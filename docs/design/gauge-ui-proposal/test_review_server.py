"""HTTP checks use temporary feedback storage, never the owner's review."""
import json
from pathlib import Path
import tempfile
from threading import Thread
import unittest
from urllib.error import HTTPError
from urllib.request import Request,urlopen
from review_server import build_server

class ReviewHttpTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.storage=Path(self.temp.name)/'feedback.json'
        self.server=build_server(0,self.storage)
        self.worker=Thread(target=self.server.serve_forever,daemon=True);self.worker.start()
        self.url=f'http://127.0.0.1:{self.server.server_address[1]}/api/gauge-review'
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.worker.join();self.temp.cleanup()
    def call(self,payload=None,origin=None):
        headers={'Content-Type':'application/json'}
        if origin:headers['Origin']=origin
        request=Request(self.url,data=json.dumps(payload).encode() if payload is not None else None,headers=headers)
        with urlopen(request) as response:return json.load(response)
    def test_individual_save_reload_and_resubmit(self):
        self.assertEqual(self.call()['revision'],0)
        saved=self.call({'action':'save','screen':{'id':'dual','status':'changes_requested','feedback':'Make RPM larger'}})
        self.assertEqual(saved['screens']['dual']['feedback'],'Make RPM larger')
        self.assertEqual(self.call()['screens'],saved['screens'])
        saved=self.call({'action':'save','screen':{'id':'dual','status':'approved','feedback':''}})
        self.assertEqual(saved['revision'],2)
        self.assertEqual(self.storage.stat().st_mode & 0o777,0o600)
    def test_partial_review_preserves_unreviewed_and_submission_snapshot(self):
        entries=[{'id':key,'status':'approved' if key=='dual' else 'unreviewed','feedback':''} for key in self.server.store.ids]
        submitted=self.call({'action':'submit','screens':entries})
        self.assertIsNotNone(submitted['submittedAt'])
        self.assertEqual(len(submitted['submittedScreens']),44)
        changed=self.call({'action':'save','screen':{'id':'dual','status':'changes_requested','feedback':'Different font'}})
        self.assertEqual(changed['submittedScreens']['dual']['status'],'approved')
        self.assertEqual(changed['screens']['dual']['status'],'changes_requested')
    def test_bad_ids_and_cross_origin_do_not_write(self):
        for payload,origin,code in [({'action':'save','screen':{'id':'unknown','status':'approved'}},None,400),
                                   ({'action':'save','screen':{'id':'dual','status':'approved'}},'https://example.com',403)]:
            with self.assertRaises(HTTPError) as error:self.call(payload,origin)
            self.assertEqual(error.exception.code,code)
            error.exception.close()
        self.assertFalse(self.storage.exists())
    def test_changed_manifest_preserves_feedback(self):
        self.call({'action':'save','screen':{'id':'dual','status':'approved','feedback':'Keep'}})
        before=self.storage.read_bytes();self.server.store.manifest_hash='changed'
        with self.assertRaises(HTTPError) as error:self.call()
        self.assertEqual(error.exception.code,409)
        error.exception.close()
        self.assertEqual(self.storage.read_bytes(),before)

if __name__=='__main__':unittest.main()

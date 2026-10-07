import json
from pathlib import Path
import subprocess
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_bench_simulator import BenchReplies
from obd_capture import parser_binary

class BenchTest(unittest.TestCase):
    def decode(self, model, command, ecu=None):
        response=model.reply(command.encode())
        args=[str(parser_binary()),command[:2],command[2:] or '00',response.hex()]
        if ecu: args.append(ecu)
        return json.loads(subprocess.check_output(args,text=True))
    def test_real_sanitized_vectors_pass_production_parser(self):
        for port in ['engine','transmission']:
            model=BenchReplies(port)
            model.reply(b'ATE0'); model.reply(b'ATH1')
            for case in model.fixture['cases']:
                with self.subTest(port=port,command=case['command']):
                    self.assertEqual(1,self.decode(model,case['command'],model.fixture['ecu'])['status'])
    def test_two_ecus_are_rejected_without_a_route_and_select_only_requested_ecu(self):
        model=BenchReplies(scenario='multiple'); model.reply(b'ATE0')
        self.assertEqual(6,self.decode(model,'010C')['status'])
        self.assertEqual('0b3c',self.decode(model,'010C','7E8')['payload'])
    def test_transmission_port_cannot_supply_engine_route_readings(self):
        model=BenchReplies('transmission'); model.reply(b'ATE0'); model.reply(b'ATH1')
        for command in ['0100','010C','0105','010D']:
            with self.subTest(command=command):
                self.assertEqual(4,self.decode(model,command,'7E8')['status'])
                self.assertEqual(1,self.decode(model,command,'7E9')['status'])
    def test_errors_and_absent_replies_stay_unavailable(self):
        model=BenchReplies(scenario='no-data'); model.reply(b'ATE0')
        self.assertEqual(2,self.decode(model,'010C')['status'])
        model=BenchReplies(scenario='malformed'); model.reply(b'ATE0')
        self.assertEqual(4,self.decode(model,'010C')['status'])
        self.assertIsNone(BenchReplies(scenario='missing').reply(b'010C'))
        self.assertIn(b'?',model.reply(b'04'))
    def test_fixture_identity_is_separate_from_vehicle_evidence(self):
        model=BenchReplies(); model.reply(b'ATE0')
        self.assertIn(b'SIMULATED',model.reply(b'ATI'))
        self.assertEqual('captured',model.fixture['origin'])
        self.assertNotIn('address',model.fixture)
        model.reply(b'ATH0')
        self.assertEqual(4,self.decode(model,'010C','7E8')['status'])

if __name__ == '__main__': unittest.main()

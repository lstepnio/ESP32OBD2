import asyncio
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_capture import AdapterSession
from obd_capture_core import allowed_command, support_replies, supports
from obd_explore import exploration_policy, monitor, setup


class PolicyTests(unittest.TestCase):
    def test_identity_policy_is_opt_in_and_no_vehicle_write_or_vin(self):
        for command in ('0900', '0904', '0906', '090A'):
            self.assertTrue(exploration_policy(command))
            self.assertFalse(allowed_command(command))
        for command in ('0902', '04', '220000', 'ATSH7E1', 'ATCSM0', 'ATMA', 'ATTP8', '0904\r04'):
            self.assertFalse(exploration_policy(command))
        self.assertTrue(exploration_policy('ATCSM1'))
        self.assertTrue(exploration_policy('ATTP7'))

    def test_identity_support_is_not_confused_with_mode01(self):
        raw=b'7E906490014400000\r>'
        maps={0: support_replies(raw, 0, service=9)}
        self.assertTrue(supports(maps, 4))
        self.assertTrue(supports(maps, 6))
        self.assertTrue(supports(maps, 10))
        self.assertEqual({}, support_replies(raw, 0))


class Recorder:
    def __init__(self): self.events=[]
    def event(self, *args, **kwargs): self.events.append((args,kwargs))


class MonitorTests(unittest.IsolatedAsyncioTestCase):
    async def test_adapter_buffer_full_is_a_partial_capture_even_with_clean_prompt(self):
        session = AdapterSession(Recorder(), policy=exploration_policy)
        class Client:
            async def write_gatt_char(self, _, raw, **kwargs):
                session.rx.put_nowait(b'1230102030405060708\rBUFFER FULL\r>')
        session.client = Client()
        result = await monitor(session, seconds=.1)
        self.assertTrue(result['adapter_buffer_full'])
        self.assertTrue(result['capture_limited'])
        self.assertTrue(result['prompt_recovered'])

    async def test_byte_limit_stops_monitor_and_waits_for_prompt(self):
        session=AdapterSession(Recorder(), policy=exploration_policy)
        writes=[]
        class Client:
            async def write_gatt_char(self, _, raw, **kwargs):
                writes.append(raw)
                if raw==b'ATMA\r':
                    session.rx.put_nowait(b'1230102030405060708\r')
                elif raw==b'\r':
                    session.rx.put_nowait(b'STOPPED\r>')
        session.client=Client()
        result=await monitor(session, seconds=.01, byte_limit=5)
        self.assertEqual([b'ATMA\r', b'\r'],writes)
        self.assertTrue(result['capture_limited'])
        self.assertTrue(result['prompt_recovered'])
        self.assertFalse(session.waiting_prompt)

    async def test_monitor_refuses_an_unfinished_diagnostic(self):
        session=AdapterSession(Recorder())
        session.waiting_prompt=True
        with self.assertRaisesRegex(ValueError,'Unfinished'):
            await monitor(session)

    async def test_silent_monitor_setting_rejection_stops_before_monitor(self):
        session=AdapterSession(Recorder(), policy=exploration_policy)
        class Client:
            async def write_gatt_char(self, _, raw, **kwargs):
                session.notification(None, bytearray(b'?\r>'))
        session.client=Client()
        with self.assertRaisesRegex(ValueError,'did not confirm ATCSM1'):
            await setup(session, ('ATCSM1',))


if __name__ == '__main__': unittest.main()

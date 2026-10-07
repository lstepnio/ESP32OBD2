import contextlib
import asyncio
import io
import json
from pathlib import Path
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_explore import main, tcm_values_policy, explore
from obd_tcm_values import BASELINE, BoundedSession, decode, payload, probe, summarize, verify_identity
from obd_capture import AdapterSession

# Exact identity and fault replies from the owner's TCM capture. No private IDs.
IDENTITY = {
    '0904': b'7E91013490401363832\r7E92137343836374145\r7E922000000000000AA\r>',
    '0906': b'7E9074906018240DAE8\r>',
    '090A': b'7E91017490A0154434D\r7E921002D5472616E73\r7E9226D69734374726C\r7E923000000AAAAAAAA\r>',
}
REPLIES = {
    '2204FE': b'7E9066204FE555455\r>',
    '225503': b'7E9046255030D\r>', '225504': b'7E9046255040D\r>',
    '225034': b'7E905625034004D\r>',
    '03': b'7E9100A4304C121C140\r7E9211DCA1DF3AAAAAA\r>',
    '07': b'7E910084703C121C140\r7E9211DCAAAAAAAAAAA\r>',
    '0A': b'7E910084A03C121C140\r7E9211DCAAAAAAAAAAA\r>',
    '0101': b'7E906410184000000\r>', '010C': b'7E904410C0FA0\r>',
    '010D': b'7E903410D00\r>', '0142': b'7E904414236B0\r>',
}
MAPS = {0: {'7E9': 0x98180001}, 32: {'7E9': 0x80018001}, 64: {'7E9': 0x40800000}}


class DecodeTests(unittest.TestCase):
    def test_actual_identity_is_required_including_cvn(self):
        identity = {k: v.hex() for k, v in IDENTITY.items()}
        self.assertEqual(BASELINE, verify_identity(identity))
        for key in IDENTITY:
            broken = dict(identity); broken.pop(key)
            with self.assertRaises(ValueError): verify_identity(broken)
        broken = dict(identity); broken['0906'] = b'7E9074906018240DAE9\r>'.hex()
        with self.assertRaisesRegex(ValueError, 'differs'): verify_identity(broken)

    def test_actual_temperature_gears_and_full_fault_lists(self):
        self.assertEqual(45, decode(REPLIES['2204FE'], '2204FE')['celsius'])
        self.assertEqual([84, 85], decode(REPLIES['2204FE'], '2204FE')['uninterpreted_bytes'])
        for raw_byte, expected in [('0D', 'P'), ('0B', 'R'), ('00', 'N'), ('01', '1')]:
            for command in ('225503', '225504'):
                self.assertEqual(expected, decode(f'7E90462{command[2:]}{raw_byte}\r>'.encode(), command)['published_gear'])
        self.assertEqual(['U0121', 'U0140', 'P1DCA', 'P1DF3'], decode(REPLIES['03'], '03')['codes'])
        for command in ('07', '0A'):
            self.assertEqual(['U0121', 'U0140', 'P1DCA'], decode(REPLIES[command], command)['codes'])
        self.assertEqual([], decode(b'7E9024300AAAAAAAAAA\r>', '03')['codes'])

    def test_pressure_fixtures_remain_raw_without_invented_units(self):
        for raw, expected in [('0000', 0), ('004D', 77), ('004F', 79), ('026C', 620)]:
            result = decode(f'7E905625034{raw}\r>'.encode(), '225034')
            self.assertEqual(expected, result['raw_unsigned'])
            self.assertFalse(result['qualified'])
            self.assertNotIn('bar', result)
            self.assertNotIn('pressure', result)

    def test_standard_zero_mil_count_and_units(self):
        result = decode(REPLIES['0101'], '0101')
        self.assertTrue(result['mil_reported']); self.assertEqual(4, result['reported_dtc_count'])
        self.assertEqual(1000, decode(REPLIES['010C'], '010C')['engine_rpm'])
        self.assertEqual(0, decode(REPLIES['010D'], '010D')['vehicle_speed_kph'])
        self.assertEqual(14, decode(REPLIES['0142'], '0142')['ecu_voltage'])

    def test_wrong_route_prefix_lengths_count_and_transport_fail_closed(self):
        for raw, command in [(REPLIES['03'].replace(b'7E9', b'7E8'), '03'),
                (REPLIES['03'].replace(b'4304', b'4303'), '03'),
                (REPLIES['225503'], '225504'), (b'7E90462503400\r>', '225034'),
                (b'7E906410C0FA00000\r>', '010C'),
                (b'7E910084703C121C140\r>', '07'),
                (REPLIES['03'].replace(b'7E921', b'7E922'), '03'),
                (REPLIES['225034'] + REPLIES['225034'], '225034'),
                (b'BUFFER FULL\r>', '225034'), (b'\xff\r>', '225034')]:
            with self.subTest(raw=raw), self.assertRaises(ValueError): decode(raw, command)
        self.assertEqual('invalid_value', decode(b'7E904625503AD\r>', '225503')['status'])
        self.assertEqual('negative_response', decode(b'7E9037F2231AAAAAA\r>', '225034')['status'])
        self.assertEqual('no_data', decode(b'NO DATA\r>', '225034')['status'])
        with self.assertRaises(ValueError): decode(REPLIES['03'], '04')


class PolicyTests(unittest.TestCase):
    def test_only_documented_reads_and_setup_are_available(self):
        for command in (*REPLIES, 'ATSH7E1', 'ATSH7DF'):
            self.assertTrue(tcm_values_policy(command))
        for command in ('04', '1003', '1902FF', '225505', '2E50340000', 'ATSH7E0', '0101\r04', 'ATRV'):
            self.assertFalse(tcm_values_policy(command))

    def test_cli_rejects_missing_state_wrong_source_and_unbounded_mixed_runs(self):
        for extra in [[], ['--tcm-state', 'idle-P', '--monitor'],
                      ['--tcm-state', 'idle-P', '--duration', 'nan'],
                      ['--tcm-state', 'idle-P', '--duration', '121'],
                      ['--tcm-state', 'idle-P', '--tcm-faults']]:
            with patch.object(sys, 'argv', ['obd_explore', '--adapter', 'unused', '--source',
                         'transmission', '--tcm-values', *extra]), patch('obd_explore.asyncio.run') as run, \
                         contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit): main()
                run.assert_not_called()


class Clock:
    now = 0
    def __call__(self): return self.now
    async def sleep(self, seconds): self.now += seconds


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_stalled_ble_write_is_bounded_and_leaves_request_unfinished(self):
        recorder = SimpleNamespace(event=lambda *args, **kwargs: None)
        session = BoundedSession(recorder, policy=tcm_values_policy)
        session.deadline = time.monotonic() + .01
        async def stalled_write(*args, **kwargs):
            await asyncio.sleep(10)
        session.client = SimpleNamespace(write_gatt_char=stalled_write)
        with self.assertRaisesRegex(ValueError, 'budget'):
            await session.request('225503')
        self.assertTrue(session.waiting_prompt)

    async def test_deadline_stops_before_send_and_request_timeouts_are_capped(self):
        session = BoundedSession(None)
        session.deadline = -1
        with patch.object(AdapterSession, 'request', new=AsyncMock()) as request:
            with self.assertRaisesRegex(ValueError, 'budget exhausted'):
                await session.request('225503')
            request.assert_not_called()
            session.deadline = None
            request.return_value = REPLIES['225503']
            await session.request('225503', timeout=15)
            self.assertEqual(5, request.call_args.kwargs['timeout'])

    async def test_supported_reads_rate_cap_raw_pressure_twice_and_summary(self):
        clock = Clock(); session = AsyncMock()
        session.request.side_effect = lambda command, **kwargs: REPLIES[command]
        result = {'owner_reported_state': 'idle-P', 'verified_identity': BASELINE, 'samples': []}
        await probe(session, result, MAPS, 12, clock=clock, sleep=clock.sleep)
        samples = result['samples']
        self.assertEqual(2, sum(s['request'] == '225034' for s in samples))
        self.assertEqual(1, sum(s['request'] == '03' for s in samples))
        self.assertTrue(all(b['t_ms'] - a['t_ms'] >= 500 for a, b in zip(samples, samples[1:])))
        self.assertTrue(all(c.kwargs['timeout'] <= 5 for c in session.request.call_args_list))
        report = summarize(result)
        self.assertEqual('idle-P', report['owner_reported_state'])
        self.assertEqual(0, report['requests']['010D']['values'][0]['vehicle_speed_kph'])

    async def test_unsupported_reads_skipped_and_negative_not_retried(self):
        clock = Clock(); session = AsyncMock()
        session.request.side_effect = lambda command, **kwargs: b'NO DATA\r>' if command == '225034' else REPLIES[command]
        result = {'owner_reported_state': 'off-P', 'verified_identity': BASELINE, 'samples': []}
        await probe(session, result, {0: {'7E9': 0}}, 10, clock=clock, sleep=clock.sleep)
        commands = [c.args[0] for c in session.request.call_args_list]
        self.assertFalse(any(c.startswith('01') for c in commands))
        self.assertEqual(1, commands.count('225034'))
        self.assertEqual('no_data', result['skipped']['225034'])

    async def test_transport_loss_preserves_partial_and_stops(self):
        clock = Clock(); session = AsyncMock()
        session.request.side_effect = [REPLIES['2204FE'], ValueError('No prompt')]
        result = {'owner_reported_state': 'idle-P', 'verified_identity': BASELINE, 'samples': []}
        await probe(session, result, MAPS, 30, clock=clock, sleep=clock.sleep)
        self.assertEqual(2, len(result['samples']))
        self.assertEqual('transport_error', result['samples'][-1]['status'])
        self.assertEqual('No prompt', summarize(result)['stop_reason'])

    async def test_drive_does_not_probe_new_pressure_or_faults(self):
        clock = Clock(); session = AsyncMock()
        session.request.side_effect = lambda command, **kwargs: REPLIES[command]
        result = {'owner_reported_state': 'drive', 'verified_identity': BASELINE, 'samples': []}
        await probe(session, result, MAPS, 10, clock=clock, sleep=clock.sleep)
        commands = [c.args[0] for c in session.request.call_args_list]
        self.assertFalse(set(commands) & {'225034', '03', '07', '0A', '0101'})


class IntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def run_capture(self, *, wrong_identity=False, restore_failure=False, no_prompt=False):
        writes = []
        characteristic = SimpleNamespace(properties=['notify', 'write'])
        service = SimpleNamespace(uuid='test', characteristics=[], get_characteristic=lambda _: characteristic)
        class Services(list):
            def get_service(self, _): return service
        class Client:
            services = Services([service])
            def __init__(self, *args, **kwargs): pass
            async def __aenter__(self): return self
            async def __aexit__(self, *args): pass
            async def start_notify(self, _, callback): self.callback = callback
            async def stop_notify(self, _): pass
            async def write_gatt_char(self, _, raw, **kwargs):
                command = raw.decode().strip(); writes.append(command)
                responses = {'0100': b'7E906410098180001\r>',
                             '0120': b'7E906412080018001\r>',
                             '0140': b'7E906414040800000\r>',
                             '0900': b'7E906490014400000\r>',
                             'ATDP': b'ISO 15765-4 CAN\r>', 'ATDPN': b'A6\r>',
                             **IDENTITY, **REPLIES}
                reply = responses.get(command, b'OK\r>')
                if wrong_identity and command == '0906': reply = b'7E9074906018240DAE9\r>'
                if restore_failure and command == 'ATSH7DF': reply = b'?\r>'
                if no_prompt and command == '225503': raise OSError('Link lost')
                # Exercise the real notification queue and fragmented prompt assembly.
                self.callback(None, reply[:7]); self.callback(None, reply[7:])
        # Use the same injected clock for all probe timing.
        async def quick_probe(session, result, maps, duration):
            clock = Clock()
            await probe(session, result, maps, duration, clock=clock, sleep=clock.sleep)
        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(adapter='test', source='transmission', output=Path(directory),
                tcm_values=True, tcm_state='idle-P', duration=8, tcm_gear_position=None,
                tcm_temperature=False, tcm_temperature_v2=False, tcm_temperature_v3=False,
                tcm_faults=False, hemi_temperature=False, monitor=False)
            with patch('obd_explore.choose_adapter', new=AsyncMock(return_value=SimpleNamespace(name='test', address='test'))), \
                    patch('bleak.BleakClient', Client), patch('obd_explore.tcm_values.probe', quick_probe), \
                    contextlib.redirect_stdout(io.StringIO()):
                if wrong_identity:
                    with self.assertRaisesRegex(ValueError, 'differs'): await explore(args)
                else:
                    await explore(args)
            record = json.loads(next(Path(directory).glob('*/exploration.json')).read_text())
            summaries = list(Path(directory).glob('*/tcm-values-summary.json'))
            summary = json.loads(summaries[0].read_text()) if summaries else None
        return writes, record, summary

    async def test_identity_gated_capture_and_restoration_report(self):
        writes, record, summary = await self.run_capture()
        self.assertTrue(record['adapter_restored']); self.assertTrue(summary['adapter_restored'])
        self.assertEqual(['ATSH7DF', 'ATCAF1', 'ATTP0'], writes[-3:])
        self.assertLess(writes.index('0906'), writes.index('225034'))
        self.assertEqual(['U0121', 'U0140', 'P1DCA', 'P1DF3'], summary['requests']['03']['values'][0]['codes'])

    async def test_mismatched_identity_never_sends_values_but_restores(self):
        writes, record, summary = await self.run_capture(wrong_identity=True)
        self.assertFalse(any(command in REPLIES for command in writes))
        self.assertTrue(record['adapter_restored']); self.assertIsNone(summary)

    async def test_failed_restoration_is_not_reported_as_success(self):
        _, record, summary = await self.run_capture(restore_failure=True)
        self.assertFalse(summary['adapter_restored'])
        self.assertIn('restore_error', record)

    async def test_transport_loss_saves_partial_and_does_not_send_restore_into_unfinished_reply(self):
        writes, record, summary = await self.run_capture(no_prompt=True)
        self.assertEqual('225503', writes[-1])
        self.assertFalse(record['adapter_restored'])
        self.assertEqual(2, len(record['tcm_values']['samples']))
        self.assertEqual('Link lost', summary['stop_reason'])


if __name__ == '__main__': unittest.main()

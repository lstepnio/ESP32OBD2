import contextlib
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_tcm_temperature_candidate import decode_candidate
from obd_explore import tcm_temperature_policy, tcm_temperature_v2_policy, tcm_temperature_v3_policy, tcm_temperature_probe, main


class TcmCandidateTests(unittest.TestCase):
    def test_published_scaling_is_unqualified(self):
        for value in (0, 40, 65, 100, 255):
            result = decode_candidate(f'2208DF\r7E9 04 62 08 DF {value:02X} AA AA AA\r>'.encode())
            self.assertEqual(value - 40, result['celsius'])
            self.assertAlmostEqual((value - 40) * 1.8 + 32, result['fahrenheit'])
            self.assertFalse(result['qualified'])

    def test_wrong_source_length_prefix_and_ambiguity_fail(self):
        for raw in (b'7E8046208DF64\r>', b'6208DF64\r>', b'7E90462810264\r>',
                    b'7E9056208DF6400\r>', b'7E9046208DF\r>', b'7E910086208DF64\r>',
                    b'7E9046208DF64\r7E9046208DF64\r>', b'7E9046208DF64\r',
                    b'NO DATA\r7E9046208DF64\r>', b'\xff\r>'):
            with self.subTest(raw=raw), self.assertRaises(ValueError): decode_candidate(raw)

    def test_negative_and_no_data_have_no_temperature(self):
        for raw in (b'7E9037F2231AAAAAAAA\r>', b'NO DATA\r>'):
            result = decode_candidate(raw)
            self.assertNotIn('celsius', result)
            self.assertFalse(result['qualified'])

    def test_challenger_v2_scaling_and_request_isolation(self):
        # Matches the OBDb authors' 2021 Challenger fixtures, attributed in the goal document.
        for frame, expected in ((b'7E90462504341\r>', 25), (b'7E90462504386\r>', 94)):
            self.assertEqual(expected, decode_candidate(frame, '225043')['celsius'])
            with self.assertRaises(ValueError): decode_candidate(frame)
        with self.assertRaises(ValueError): decode_candidate(b'7E9046208DF64\r>', '225043')
        with self.assertRaises(ValueError): decode_candidate(b'7E90462504341\r>', '228102')
        self.assertTrue(tcm_temperature_v2_policy('225043'))
        self.assertFalse(tcm_temperature_v2_policy('2208DF'))
        self.assertFalse(tcm_temperature_policy('225043'))

    def test_challenger_three_byte_variant_retains_uninterpreted_bytes(self):
        result = decode_candidate(b'7E9066204FE393837\r>', '2204FE')
        self.assertEqual(17, result['celsius'])
        self.assertAlmostEqual(62.6, result['fahrenheit'])
        self.assertEqual([0x38, 0x37], result['uninterpreted_bytes'])
        self.assertFalse(result['qualified'])
        for frame in (b'7E9046204FE39\r>', b'7E8066204FE393837\r>'):
            with self.assertRaises(ValueError): decode_candidate(frame, '2204FE')
        self.assertTrue(tcm_temperature_v3_policy('2204FE'))
        self.assertFalse(tcm_temperature_v3_policy('225043'))

    def test_policy_is_one_read_only_route(self):
        for command in ('2208DF', 'ATSH7E1', 'ATSH7DF'):
            self.assertTrue(tcm_temperature_policy(command))
        for command in ('229110', '228102', 'ATSH7E0', '04', '1003', '3E00', '2208DF\r04'):
            self.assertFalse(tcm_temperature_policy(command))

    def test_invalid_cli_combinations_never_connect(self):
        for extra in (['--source', 'engine'], ['--source', 'transmission', '--monitor'],
                      ['--source', 'transmission', '--tcm-faults']):
            with patch.object(sys, 'argv', ['obd_explore', '--adapter', 'unused', '--tcm-temperature', *extra]), \
                    patch('obd_explore.asyncio.run') as run, contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit): main()
                run.assert_not_called()

    def test_new_variants_cannot_combine_or_use_engine_source(self):
        for flag in ('--tcm-temperature-v2', '--tcm-temperature-v3'):
            for extra in (['--source', 'engine'], ['--source', 'transmission', '--monitor'],
                          ['--source', 'transmission', '--tcm-temperature'],
                          ['--source', 'transmission', '--tcm-faults']):
                with patch.object(sys, 'argv', ['obd_explore', '--adapter', 'unused', flag, *extra]), \
                        patch('obd_explore.asyncio.run') as run, contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit): main()
                    run.assert_not_called()
        with patch.object(sys, 'argv', ['obd_explore', '--adapter', 'unused', '--source',
                                       'transmission', '--tcm-temperature-v2', '--tcm-temperature-v3']), \
                patch('obd_explore.asyncio.run') as run, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit): main()
            run.assert_not_called()


class TcmProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_v2_probe_is_fixed_and_restores_header(self):
        session = AsyncMock()
        session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', *([b'7E9037F2231\r>'] * 3), b'OK\r>']
        result = {'samples': []}
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()):
            await tcm_temperature_probe(session, result, '225043')
        self.assertEqual(['ATSH7E1', '225043', '225043', '225043', 'ATSH7DF'],
                         [c.args[0] for c in session.request.call_args_list])
        self.assertTrue(all(s['status'] == 'negative_response' for s in result['samples']))

    async def test_fixed_probe_restores_route(self):
        session = AsyncMock()
        session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', *([b'7E9046208DF64\r>'] * 3), b'OK\r>']
        result = {'samples': []}
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()):
            await tcm_temperature_probe(session, result)
        self.assertEqual(['ATSH7E1', '2208DF', '2208DF', '2208DF', 'ATSH7DF'],
                         [c.args[0] for c in session.request.call_args_list])
        self.assertEqual(3, len(result['samples']))

    async def test_timeout_preserves_evidence_and_stops(self):
        session = AsyncMock()
        session.waiting_prompt = False
        count = 0
        async def request(command, **kwargs):
            nonlocal count
            if command == '2208DF':
                count += 1
                if count == 2:
                    session.waiting_prompt = True
                    raise ValueError('timeout')
                return b'NO DATA\r>'
            return b'OK\r>'
        session.request.side_effect = request
        result = {'samples': []}
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()), self.assertRaises(ValueError):
            await tcm_temperature_probe(session, result)
        self.assertEqual(1, len(result['samples']))
        self.assertEqual(['ATSH7E1', '2208DF', '2208DF'], [c.args[0] for c in session.request.call_args_list])

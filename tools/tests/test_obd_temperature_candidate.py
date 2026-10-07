import contextlib
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_explore import exploration_policy, candidate_policy, temperature_probe, main
from obd_temperature_candidate import decode_candidate


class CandidateTests(unittest.TestCase):
    def test_synthetic_scaling_and_padding_remains_unqualified(self):
        for hex_value, expected in [('1180', 70), ('2540', 149), ('3500', 212)]:
            raw = f'229110\r7E8 05 62 91 10 {hex_value} AA AA\r>'.encode()
            result = decode_candidate(raw)
            self.assertEqual(expected, result['fahrenheit'])
            self.assertAlmostEqual((expected - 32) * 5 / 9, result['celsius'])
            self.assertFalse(result['qualified'])

    def test_wrong_route_prefix_length_ambiguity_and_bounds(self):
        for raw in (b'7E9056291102540\r>', b'6291102540\r>',
                    b'7E8056291112540\r>', b'7E80562911025\r>',
                    b'7E806629110254000\r>', b'7E810096291102540\r>',
                    b'7E805629110FFFF\r>', b'7E8056291102540\r',
                    b'7E8056291102540\r7E8056291102540\r>',
                    b'NO DATA\r7E8056291102540\r>', b'\xff\r>'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                decode_candidate(raw)

    def test_no_data_and_negative_do_not_create_reading(self):
        self.assertEqual('no_data', decode_candidate(b'NO DATA\r>')['status'])
        result = decode_candidate(b'7E8037F2231AAAAAAAA\r>')
        self.assertEqual('31', result['nrc'])
        self.assertNotIn('celsius', result)

    def test_opt_in_cannot_expand_to_arbitrary_requests_or_tcm_route(self):
        for cmd in ('229110', 'ATSH7E0', 'ATSH7DF'):
            self.assertFalse(exploration_policy(cmd))
            self.assertTrue(candidate_policy(cmd))
        for cmd in ('223C22', 'ATSH7E1', '04', '1003', '3E01', '229110\r04'):
            self.assertFalse(candidate_policy(cmd))

    def test_cli_rejects_transmission_label_before_connecting(self):
        with patch.object(sys, 'argv', ['obd_explore', '--source', 'transmission',
                                       '--adapter', 'unused', '--hemi-temperature']), \
                patch('obd_explore.asyncio.run') as run, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                main()
            self.assertEqual(2, error.exception.code)
            run.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_fixed_reads_and_header_restore(self):
        session = AsyncMock()
        session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', *([b'NO DATA\r>'] * 3), b'OK\r>']
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()):
            result = await temperature_probe(session)
        self.assertEqual(['ATSH7E0', '229110', '229110', '229110', 'ATSH7DF'],
                         [call.args[0] for call in session.request.call_args_list])
        self.assertFalse(result['qualified'])

    async def test_clean_request_failure_restores_header(self):
        session = AsyncMock()
        session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', ValueError('failure'), b'OK\r>']
        with self.assertRaises(ValueError):
            await temperature_probe(session)
        self.assertEqual('ATSH7DF', session.request.call_args_list[-1].args[0])

    async def test_missing_prompt_prevents_another_command(self):
        session = AsyncMock()
        session.waiting_prompt = False
        async def request(cmd, **kwargs):
            if cmd == '229110':
                session.waiting_prompt = True
                raise ValueError('timeout')
            return b'OK\r>'
        session.request.side_effect = request
        with self.assertRaises(ValueError):
            await temperature_probe(session)
        self.assertEqual(['ATSH7E0', '229110'],
                         [call.args[0] for call in session.request.call_args_list])


if __name__ == '__main__':
    unittest.main()

import contextlib
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_tcm_gear_candidate import COMMANDS, decode_candidate
from obd_explore import main, tcm_gear_policy, tcm_gear_probe


class GearTests(unittest.TestCase):
    def test_published_fixtures_remain_unqualified_and_kind_is_separate(self):
        for command in ('225503', '225504'):
            for value, gear in ((0, 'N'), (1, '1'), (3, '3'), (11, 'R'), (13, 'P')):
                sample = decode_candidate(f'7E90462{command[2:]}{value:02X}\r>'.encode(), command)
                self.assertEqual(gear, sample['published_gear'])
                self.assertEqual('current' if command == '225503' else 'target', sample['published_kind'])
                self.assertFalse(sample['qualified'])
        self.assertEqual(3, decode_candidate(b'7E904623C221E\r>', '223C22')['published_scalar'])
        self.assertEqual('UNKNOWN', decode_candidate(b'7E9046255030F\r>', '225503')['published_gear'])
        sample = decode_candidate(b'7E904625503AD\r>', '225503')
        self.assertEqual('P', sample['published_gear'])
        self.assertEqual(10, sample['uninterpreted_high_nibble'])

    def test_errors_do_not_create_gears(self):
        for raw in (b'NO DATA\r>', b'7E9037F2231AAAAAAAA\r>'):
            sample = decode_candidate(raw, '225503')
            self.assertNotIn('published_gear', sample)
        for raw in (b'7E8046255030D\r>', b'7E9046255040D\r>', b'6255030D\r>',
                    b'7E9056255030D00\r>', b'7E904625503\r>', b'7E910086255030D\r>',
                    b'7E9046255030D\r7E9046255030D\r>', b'7E9046255030D\r',
                    b'NO DATA\r7E9046255030D\r>', b'BUFFER FULL\r>', b'\xff\r>', b'x' * 4096 + b'>'):
            with self.subTest(raw=raw[:40]), self.assertRaises(ValueError):
                decode_candidate(raw, '225503')

    def test_policy_and_cli_keep_capture_bounded(self):
        for command in (*COMMANDS, 'ATSH7E1', 'ATSH7DF'):
            self.assertTrue(tcm_gear_policy(command))
        for command in ('04', '1003', '2E550300', '225503\r04', '22550400', 'ATSH7E0', '2204FE'):
            self.assertFalse(tcm_gear_policy(command))
        for extra in (['--source', 'engine'], ['--source', 'transmission', '--monitor'],
                      ['--source', 'transmission', '--tcm-temperature-v3'],
                      ['--source', 'transmission', '--tcm-faults']):
            with patch.object(sys, 'argv', ['obd_explore', '--adapter', 'unused', '--tcm-gear-position', 'P', *extra]), \
                    patch('obd_explore.asyncio.run') as run, contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit): main()
                run.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_exact_requests_and_restoration_with_owner_label(self):
        session = AsyncMock(); session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', *([b'7E9037F2231\r>'] * 6), b'OK\r>']
        result = {'owner_reported_position': 'P', 'samples': []}
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()):
            await tcm_gear_probe(session, result)
        self.assertEqual(['ATSH7E1', *COMMANDS, *COMMANDS, 'ATSH7DF'],
                         [c.args[0] for c in session.request.call_args_list])
        self.assertEqual(6, len(result['samples']))
        self.assertTrue(all(s['status'] == 'negative_response' for s in result['samples']))

    async def test_timeout_retains_partial_evidence_without_followup_on_unfinished_request(self):
        session = AsyncMock(); session.waiting_prompt = False
        async def request(command, **kwargs):
            if command == 'ATSH7E1': return b'OK\r>'
            if command == '223C22': return b'7E904623C2200\r>'
            session.waiting_prompt = True
            raise TimeoutError('No prompt')
        session.request.side_effect = request
        result = {'owner_reported_position': 'P', 'samples': []}
        with patch('obd_explore.asyncio.sleep', new=AsyncMock()), self.assertRaises(TimeoutError):
            await tcm_gear_probe(session, result)
        self.assertEqual(1, len(result['samples']))
        self.assertEqual(['ATSH7E1', '223C22', '225503'], [c.args[0] for c in session.request.call_args_list])

    async def test_missing_label_never_sends(self):
        session = AsyncMock()
        with self.assertRaises(ValueError): await tcm_gear_probe(session, {'samples': []})
        session.request.assert_not_called()


if __name__ == '__main__': unittest.main()

import contextlib
import io
from pathlib import Path
import sys
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_explore import exploration_policy, fault_policy, fault_probe, main


class PolicyTests(unittest.TestCase):
    def test_fault_reads_are_opt_in_and_never_clear(self):
        for command in ('03', '07', '0A', 'ATSH7E1', 'ATSH7DF'):
            self.assertTrue(fault_policy(command))
            self.assertFalse(exploration_policy(command))
        for command in ('04', '14FFFFFF', '1902FF', 'ATSH7E0', '223C22', '03\r04'):
            self.assertFalse(fault_policy(command))

    def test_engine_label_rejected_before_connecting(self):
        with patch.object(sys, 'argv', ['obd_explore', '--source', 'engine', '--adapter', 'unused',
                                       '--tcm-faults']), patch('obd_explore.asyncio.run') as run, \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit): main()
            run.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_fixed_tcm_categories_and_header_restore(self):
        session = AsyncMock()
        session.waiting_prompt = False
        session.request.side_effect = [b'OK\r>', b'03reply>', b'07reply>', b'0Areply>', b'OK\r>']
        result = {'samples': []}
        await fault_probe(session, result)
        self.assertEqual(['ATSH7E1', '03', '07', '0A', 'ATSH7DF'],
                         [call.args[0] for call in session.request.call_args_list])
        self.assertEqual(['stored', 'pending', 'permanent'], [s['category'] for s in result['samples']])

    async def test_timeout_keeps_saved_samples_and_stops_commands(self):
        session = AsyncMock()
        session.waiting_prompt = False
        async def request(command, **kwargs):
            if command == '07':
                session.waiting_prompt = True
                raise ValueError('timeout')
            return b'OK\r>'
        session.request.side_effect = request
        result = {'samples': []}
        with self.assertRaises(ValueError): await fault_probe(session, result)
        self.assertEqual(1, len(result['samples']))
        self.assertEqual(['ATSH7E1', '03', '07'], [c.args[0] for c in session.request.call_args_list])


if __name__ == '__main__': unittest.main()

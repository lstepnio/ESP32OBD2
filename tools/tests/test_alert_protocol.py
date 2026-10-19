"""Production C parity against the Android event snapshot fixture, without devices."""
import pathlib
import subprocess
import tempfile
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
class AlertProtocolTest(unittest.TestCase):
    def test_production_event_and_clear_policy(self):
        with tempfile.TemporaryDirectory() as tmp:
            binary = str(pathlib.Path(tmp) / 'events')
            subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror', '-fsanitize=address,undefined',
                '-Ifirmware/gauge/tests/stubs', '-Ifirmware/gauge/main/inc', 'firmware/gauge/tests/alert_events_test.c',
                'firmware/gauge/main/src/alert_events.c', 'firmware/gauge/main/src/alert_engine.c', 'firmware/gauge/main/src/clear_policy.c', '-o', binary], cwd=ROOT, check=True)
            subprocess.run([binary], check=True, capture_output=True)
            actual = subprocess.check_output([binary, '--vector'], text=True).strip()
            expected = (ROOT/'android/app/src/test/resources/alert-events-v16.hex').read_text().strip()
            self.assertEqual(actual, expected)

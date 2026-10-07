"""Exercise production firmware diagnostics state and its Android wire fixture."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]

class FirmwareDiagnosticsTests(unittest.TestCase):
    def test_production_state_and_shared_android_packet(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / 'diagnostics'
            vector = Path(folder) / 'packet.hex'
            subprocess.run([os.environ.get('CC', 'cc'), '-std=c11', '-Wall', '-Wextra', '-Werror',
                '-fsanitize=address,undefined',
                '-I', str(ROOT / 'firmware/gauge/tests/diagnostics_stubs'),
                '-I', str(ROOT / 'firmware/gauge/tests/stubs'),
                '-I', str(ROOT / 'firmware/gauge/main/inc'),
                str(ROOT / 'firmware/gauge/tests/diagnostics_state_test.c'),
                str(ROOT / 'firmware/gauge/main/src/diagnostics_state.c'),
                '-o', str(binary)], check=True, capture_output=True, timeout=30)
            subprocess.run([str(binary), str(vector)], check=True, capture_output=True, timeout=10)
            self.assertEqual((ROOT / 'android/app/src/test/resources/diagnostics-tcm-v15.hex').read_text(), vector.read_text())

"""Independent production adapter snapshots and stale-generation rejection."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]
class AdapterSourceTests(unittest.TestCase):
    def test_independent_disconnect_pause_and_late_callbacks(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / 'sources'
            subprocess.run([os.environ.get('CC', 'cc'), '-std=c11', '-Wall', '-Wextra', '-Werror',
                '-fsanitize=address,undefined',
                '-I', str(ROOT / 'firmware/gauge/tests/dual_stubs'),
                '-I', str(ROOT / 'firmware/gauge/tests/diagnostics_stubs'),
                '-I', str(ROOT / 'firmware/gauge/tests/stubs'),
                '-I', str(ROOT / 'firmware/gauge/main/inc'),
                str(ROOT / 'firmware/gauge/tests/adapter_source_test.c'),
                str(ROOT / 'firmware/gauge/main/src/adapter_status.c'),
                '-o', str(binary)], check=True, capture_output=True, timeout=30)
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)

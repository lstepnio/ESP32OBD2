"""Real socket deadline/cancellation fixtures against production firmware IO."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]

class WifiBulkIoTests(unittest.TestCase):
    def test_silence_trickle_backpressure_close_and_session_loss(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "wifi-io"
            subprocess.run([os.environ.get("CC", "cc"), "-std=c11", "-Wall", "-Wextra", "-Werror",
                "-pthread", "-fsanitize=address,undefined", "-I", str(ROOT / "firmware/gauge/main/inc"),
                str(ROOT / "firmware/gauge/tests/wifi_bulk_io_test.c"),
                str(ROOT / "firmware/gauge/main/src/wifi_bulk_io.c"), "-o", str(binary)],
                check=True, capture_output=True, timeout=30)
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)

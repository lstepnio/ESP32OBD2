"""Fault injection against production display settings without vehicle hardware."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]

class DisplaySettingsTests(unittest.TestCase):
    def test_readers_do_not_wait_on_flash_and_failed_writes_preserve_saved_state(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "display"
            subprocess.run([os.environ.get("CC", "cc"), "-std=c11", "-Wall", "-Wextra", "-Werror",
                "-fsanitize=address,undefined",
                "-I", str(ROOT / "firmware/gauge/tests/display_stubs"),
                "-I", str(ROOT / "firmware/gauge/tests/diagnostics_stubs"),
                "-I", str(ROOT / "firmware/gauge/main/inc"),
                str(ROOT / "firmware/gauge/tests/display_settings_test.c"),
                str(ROOT / "firmware/gauge/main/src/display_settings.c"),
                "-o", str(binary)], check=True, capture_output=True, timeout=30)
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)

"""Fault injection against production display settings without vehicle hardware."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DisplaySettingsTests(unittest.TestCase):
    def test_readers_do_not_wait_on_flash_and_failed_writes_preserve_saved_state(self):
        self._run(smart=1)

    def test_restricted_display_preserves_saved_smart_preference(self):
        self._run(smart=0)

    def _run(self, smart):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "display"
            subprocess.run(
                [
                    os.environ.get("CC", "cc"),
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    f"-DEGAUGE_FEATURE_SMART_CYCLE={smart}",
                    "-fsanitize=address,undefined",
                    "-I",
                    str(ROOT / "firmware/gauge/tests/display_stubs"),
                    "-I",
                    str(ROOT / "firmware/gauge/tests/diagnostics_stubs"),
                    "-I",
                    str(ROOT / "firmware/gauge/main/inc"),
                    str(ROOT / "firmware/gauge/tests/display_settings_test.c"),
                    str(ROOT / "firmware/gauge/main/src/display_settings.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)

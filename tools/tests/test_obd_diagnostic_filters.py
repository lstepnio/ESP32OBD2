"""Production filter policy under sanitizer instrumentation."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DiagnosticFilterTest(unittest.TestCase):
    def test_filter_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            out = str(Path(directory) / "filter")
            subprocess.run(
                [
                    os.environ.get("CC", "cc"),
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/diagnostic_filter_test.c",
                    "firmware/gauge/main/src/diagnostic_filter.c",
                    "-o",
                    out,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [out, str(ROOT / "android/app/src/test/resources/diagnostics-tcm-v15.hex")],
                check=True,
            )

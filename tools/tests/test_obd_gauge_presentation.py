"""Production circular presentation policy and temporary showcase lifecycle."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class GaugePresentationTest(unittest.TestCase):
    def test_production_policy_with_sanitizers(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = str(Path(directory) / "gauge-presentation")
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/gauge_presentation_test.c",
                    "firmware/gauge/main/src/gauge_layout.c",
                    "firmware/gauge/main/src/display_showcase.c",
                    "-lm",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True)

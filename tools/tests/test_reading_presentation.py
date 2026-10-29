"""Deterministic causal display-only easing, independent of LVGL/drivers."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReadingPresentationTest(unittest.TestCase):
    def test_bounded_causal_easing(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = str(Path(directory) / "presentation")
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/reading_presentation_test.c",
                    "firmware/gauge/main/src/reading_presentation.c",
                    "-lm",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True)

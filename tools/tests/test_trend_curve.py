"""Bounded production trend geometry and antialias coverage, independent of display drivers."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class TrendCurveTest(unittest.TestCase):
    def test_geometry_and_rasterizer(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = str(Path(directory) / "trend")
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/trend_curve_test.c",
                    "firmware/gauge/main/src/trend_curve.c",
                    "-lm",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True)

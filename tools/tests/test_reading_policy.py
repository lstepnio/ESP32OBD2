"""Native display-only rate policy with explicit custom/alert limits."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReadingPolicyTest(unittest.TestCase):
    def test_native_policy(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = str(Path(folder) / "reading-policy")
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/reading_policy_test.c",
                    "firmware/gauge/main/src/reading_policy.c",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True)

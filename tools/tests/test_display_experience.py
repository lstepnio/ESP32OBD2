"""Production idle/orientation policies without a physical display or sensor."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DisplayExperienceTests(unittest.TestCase):
    def native(self, name, sources, includes=()):
        with tempfile.TemporaryDirectory() as folder:
            binary = str(Path(folder) / name)
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    *["-I" + p for p in includes],
                    *["firmware/gauge/" + p for p in sources],
                    "-lm",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True, timeout=20)

    def test_logo_waiting_policy(self):
        self.native("idle", ["tests/display_idle_policy_test.c", "main/src/display_idle_policy.c"])

    def test_orientation_confidence_and_hysteresis(self):
        self.native(
            "orientation", ["tests/orientation_policy_test.c", "main/src/orientation_policy.c"]
        )

    def test_orientation_transport_failure_and_recovery(self):
        self.native(
            "sensor",
            [
                "tests/orientation_runtime_test.c",
                "main/src/orientation_policy.c",
                "main/src/orientation_runtime.c",
            ],
            [
                "firmware/gauge/tests/board_stubs",
                "firmware/gauge/tests/display_stubs",
                "firmware/gauge/tests/diagnostics_stubs",
            ],
        )

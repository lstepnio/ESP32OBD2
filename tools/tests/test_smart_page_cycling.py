"""Replay the production selector without a phone, vehicle, or ESP32."""

from pathlib import Path
import subprocess
import tempfile
import unittest
from tools.generate_reading_catalog import smart_interest

ROOT = Path(__file__).resolve().parents[2]


class SmartPageCyclingTests(unittest.TestCase):
    def test_production_replays(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "smart-pages"
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/smart_page_policy_test.c",
                    "firmware/gauge/main/src/smart_page_policy.c",
                    "firmware/gauge/main/src/smart_metric_catalog.c",
                    "firmware/gauge/main/src/alert_events.c",
                    "-lm",
                    "-o",
                    str(binary),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([str(binary)], check=True, timeout=30)

    def test_interest_metadata_rejects_guesses_and_invalid_numbers(self):
        self.assertIsNone(smart_interest(None))
        self.assertEqual(smart_interest({"kind": "rpm"}), {"kind": "rpm"})
        valid = {"kind": "thermal", "approach": 5, "riseRate": 0.2, "riseDelta": 3}
        self.assertEqual(smart_interest(valid), valid)
        for value in (float("nan"), float("inf"), -1, 0, True, "5", 100):
            with self.subTest(value=value), self.assertRaises(AssertionError):
                smart_interest({**valid, "approach": value})
        for invalid in (
            {"kind": "label_guess"},
            {"kind": "thermal"},
            {"kind": "rpm", "approach": 1},
        ):
            with self.subTest(invalid=invalid), self.assertRaises(AssertionError):
                smart_interest(invalid)

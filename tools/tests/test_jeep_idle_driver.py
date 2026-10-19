"""Replay the private-trace-derived, sanitized cases through the native candidate.

No Bluetooth imports or hardware access. The post-Return inactive report is a
synthetic sequence using a captured pre-Start report, not vehicle qualification.
"""

import json
from pathlib import Path
import tempfile
import unittest

from tools import dev

ROOT = Path(__file__).resolve().parents[2]


class JeepIdleDriverTests(unittest.TestCase):
    def test_native_driver_uses_captured_replies(self):
        fixture = json.loads(
            (ROOT / "firmware/gauge/tests/fixtures/jeep-high-idle.json").read_text()
        )
        self.assertEqual("captured", fixture["origin"])
        self.assertEqual("7E8", fixture["ecu"])
        self.assertEqual(12, len(fixture["cases"]))
        with tempfile.TemporaryDirectory(prefix="egauge-idle-") as directory:
            header = Path(directory) / "jeep_idle_fixture.h"
            entries = []
            for case in fixture["cases"]:
                response = bytes.fromhex(case["responseHex"]).decode("ascii")
                self.assertTrue(response.endswith("\r\r>"))
                entries.append("{" + json.dumps(case["command"]) + "," + json.dumps(response) + "}")
            header.write_text(
                "/* Generated from the sanitized capture for this test run. */\n"
                "static const struct { const char *command, *response; } capture[] = {\n"
                + ",\n".join(entries)
                + "\n};\n"
            )
            dev.native(
                "jeep_idle_driver",
                [
                    "tests/jeep_idle_driver_test.c",
                    "main/src/jeep_idle_driver.c",
                    "main/src/elm_response.c",
                    "main/src/temporary_idle.c",
                ],
                includes=[f"-I{directory}"],
            )


if __name__ == "__main__":
    unittest.main()

import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class CompanionGattReadTest(unittest.TestCase):
    def test_production_callback_contract_reconstructs_long_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            binary = str(pathlib.Path(tmp) / "gatt-read")
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/tests/stubs",
                    "-Ifirmware/gauge/main/inc",
                    "firmware/gauge/tests/companion_gatt_read_test.c",
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True, capture_output=True)
        source = (ROOT / "firmware/gauge/main/src/ble_companion.c").read_text()
        self.assertNotIn("+ ctxt->offset", source)
        self.assertNotIn("+ctxt->offset", source)
        # Guard all read paths without coupling the test to the number of features.
        reads = re.findall(r"return\s+companion_gatt_read_value\((.*?)\);", source, re.S)
        self.assertTrue(reads)
        self.assertNotRegex(source, r"\bos_mbuf_(?:append|extend|prepend)\s*\(")
        for arguments in reads:
            self.assertRegex(arguments, r"^ctxt->om\s*,")
            self.assertRegex(arguments, r",\s*ctxt->offset\s*$")

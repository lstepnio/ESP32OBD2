"""Compile the production pairing gate against real and unsafe build settings."""

import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
HEADER = ROOT / "firmware/gauge/main/inc/ble_security_policy.h"


class BleSecurityPolicyTest(unittest.TestCase):
    def compile_policy(self, definitions):
        compiler = shutil.which("cc")
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory() as directory:
            source = pathlib.Path(directory) / "policy.c"
            source.write_text(
                "".join(f"#define {key} {value}\n" for key, value in definitions.items())
                + f'#include "{HEADER}"\nint main(void) {{ return 0; }}\n'
            )
            return subprocess.run(
                [compiler, "-std=c11", "-Wall", "-Wextra", "-Werror", "-fsyntax-only", str(source)],
                capture_output=True,
                text=True,
            )

    def test_checked_in_configuration_supports_authenticated_fresh_pairing(self):
        definitions = {}
        for line in (ROOT / "firmware/gauge/sdkconfig").read_text().splitlines():
            if line.startswith("CONFIG_") and "=" in line:
                key, value = line.split("=", 1)
                if key in {
                    "CONFIG_BT_NIMBLE_SECURITY_ENABLE",
                    "CONFIG_BT_NIMBLE_SM_SC",
                    "CONFIG_BT_NIMBLE_SM_LVL",
                    "CONFIG_BT_NIMBLE_SM_LEGACY",
                }:
                    definitions[key] = "1" if value == "y" else value
        result = self.compile_policy(definitions)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_conflicting_levels_and_missing_security_cannot_build(self):
        secure = {
            "CONFIG_BT_NIMBLE_SECURITY_ENABLE": "1",
            "CONFIG_BT_NIMBLE_SM_SC": "1",
            "CONFIG_BT_NIMBLE_SM_LVL": "0",
        }
        unsafe = [{**secure, "CONFIG_BT_NIMBLE_SM_LVL": str(level)} for level in range(1, 5)]
        unsafe += [
            {key: value for key, value in secure.items() if key != missing}
            for missing in ("CONFIG_BT_NIMBLE_SECURITY_ENABLE", "CONFIG_BT_NIMBLE_SM_SC")
        ]
        unsafe += [
            {**secure, disabled: "0"}
            for disabled in ("CONFIG_BT_NIMBLE_SECURITY_ENABLE", "CONFIG_BT_NIMBLE_SM_SC")
        ]
        unsafe += [{**secure, "CONFIG_BT_NIMBLE_SM_LEGACY": "1"}]
        for definitions in unsafe:
            with self.subTest(definitions=definitions):
                result = self.compile_policy(definitions)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("eGauge requires neutral global level 0", result.stderr)


if __name__ == "__main__":
    unittest.main()

"""Generator failures must not destroy the reviewed font set."""

import csv
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "firmware/gauge"


class FontGenerationTest(unittest.TestCase):
    def test_inventory_matches_generated_and_used_fonts(self):
        rows = list(csv.reader((PROJECT / "assets/fonts/fonts.csv").read_text().splitlines()))
        expected = {f"{prefix}_{size}" for _, prefix, *sizes in rows for size in sizes}
        actual = {p.stem for p in (PROJECT / "main/fonts").glob("*.c")}
        used = set(
            re.findall(
                r"extern\s+const\s+lv_font_t\s+(\w+)\s*;",
                (PROJECT / "main/src/gauge_presentation_reference.c").read_text(),
            )
        )
        self.assertEqual(expected, actual)
        self.assertEqual(expected, used)

    def test_failed_conversion_preserves_original_and_success_is_checkable(self):
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory)
            (project / "scripts").mkdir()
            (project / "assets/fonts").mkdir(parents=True)
            (project / "main/fonts").mkdir(parents=True)
            shutil.copy(PROJECT / "scripts/mkfonts.sh", project / "scripts/mkfonts.sh")
            shutil.copy(PROJECT / "assets/fonts/fonts.csv", project / "assets/fonts/fonts.csv")
            original = project / "main/fonts/reviewed.c"
            original.write_text("keep the reviewed font")
            converter = project / "converter"
            converter.write_text(
                f"#!{sys.executable}\n"
                + """import os, pathlib, sys
if '--version' in sys.argv:
    print('1.5.3'); sys.exit(0)
output = pathlib.Path(sys.argv[sys.argv.index('-o') + 1])
if os.environ.get('FAIL_FONT') in output.name:
    sys.exit(1)
output.write_text('font ' + output.stem)
"""
            )
            converter.chmod(0o700)
            env = dict(os.environ, FONT_CONVERTER=str(converter), FAIL_FONT="semibold_40")
            command = ["bash", str(project / "scripts/mkfonts.sh")]
            self.assertNotEqual(0, subprocess.run(command, env=env, capture_output=True).returncode)
            self.assertEqual("keep the reviewed font", original.read_text())
            self.assertFalse(list(project.glob(".fonts.*")))
            env["FAIL_FONT"] = "never-fail"
            subprocess.run(command, env=env, check=True, capture_output=True)
            self.assertFalse(original.exists())
            subprocess.run([*command, "--check"], env=env, check=True, capture_output=True)
            generated = next((project / "main/fonts").glob("*.c"))
            generated.write_text("unexpected change")
            self.assertNotEqual(
                0, subprocess.run([*command, "--check"], env=env, capture_output=True).returncode
            )
            self.assertEqual("unexpected change", generated.read_text())

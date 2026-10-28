"""The repository venv must not shadow the SDK's Python interpreter."""

import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import dev


class ToolchainEnvironmentTest(unittest.TestCase):
    def test_parked_candidate_never_modifies_the_ordinary_config_or_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            firmware = root / "firmware"
            firmware.mkdir()
            ordinary = "# CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST is not set\nCONFIG_OTHER=y\n"
            (firmware / "sdkconfig").write_text(ordinary)
            with (
                patch.object(dev, "FIRMWARE", firmware),
                patch.object(dev, "OUTPUT", root / "output"),
                patch.object(dev, "idf_command", return_value=["python", "idf.py"]),
                patch.object(dev, "run") as run,
            ):
                dev.firmware("build", parked_idle_test=True)
                command = run.call_args.args[0]
                self.assertIn(firmware / "build-parked-idle", command)
                candidate = (root / "output/parked-idle.sdkconfig").read_text()
                self.assertIn("CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=y", candidate)
                self.assertIn("CONFIG_OTHER=y", candidate)
                self.assertNotIn("is not set", candidate)
                self.assertEqual((firmware / "sdkconfig").read_text(), ordinary)
                run.reset_mock()
                dev.firmware("build")
                self.assertNotIn("-B", run.call_args.args[0])

    def test_firmware_uses_sdk_python_despite_an_active_repository_venv(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk = root / "sdk"
            environment = root / "idf-python"
            script = sdk / "tools/idf.py"
            python = environment / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
            for file in (script, python):
                file.parent.mkdir(parents=True, exist_ok=True)
                file.touch()
            with (
                patch.dict(os.environ, IDF_PATH=str(sdk), IDF_PYTHON_ENV_PATH=str(environment)),
                patch.object(sys, "executable", str(root / "repo-venv/bin/python")),
                patch.object(dev, "run") as run,
            ):
                dev.firmware("build")
                self.assertEqual(run.call_args.args[0][:2], [python, script])
            with (
                patch.dict(os.environ, {}, clear=True),
                self.assertRaisesRegex(SystemExit, "export.sh"),
            ):
                dev.idf_command()

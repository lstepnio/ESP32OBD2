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

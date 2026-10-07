"""Deadline expiry and tick rollover against the production OBD wait policy."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[2]

class ObdWaitPolicyTests(unittest.TestCase):
    def test_expired_deadline_never_wraps_into_an_unbounded_wait(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder) / "deadline"
            subprocess.run([os.environ.get("CC", "cc"), "-std=c11", "-Wall", "-Wextra", "-Werror",
                "-fsanitize=address,undefined", "-I", str(ROOT / "firmware/gauge/main/inc"),
                str(ROOT / "firmware/gauge/tests/obd_wait_policy_test.c"), "-o", str(binary)],
                check=True, capture_output=True, timeout=30)
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)

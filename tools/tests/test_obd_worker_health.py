"""Exercise production bounded health counters, including timestamp wrap and sibling stalls."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class WorkerHealthTest(unittest.TestCase):
    def test_production_health_counters(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "worker-health"
            subprocess.run(["cc", "-std=c11", "-Wall", "-Wextra", "-Werror",
                "-fsanitize=address,undefined", "-I", str(ROOT / "firmware/gauge/main/inc"),
                str(ROOT / "firmware/gauge/main/src/worker_health.c"),
                str(ROOT / "firmware/gauge/tests/worker_health_test.c"), "-o", str(binary)], check=True)
            subprocess.run([str(binary)], check=True)

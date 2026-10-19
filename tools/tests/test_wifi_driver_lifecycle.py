"""Run production Wi-Fi lifecycle code against allocation/registration failure stubs."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class WifiDriverLifecycleTests(unittest.TestCase):
    def test_failed_initialization_releases_resources_and_retry_can_succeed(self):
        compiler = shutil.which("cc")
        self.assertIsNotNone(compiler, "A C compiler is required for this production fixture")
        source = (ROOT / "firmware/gauge/main/src/wifi_bulk.c").read_text()
        start = source.index("static void release_wifi_driver(void)")
        end = source.index("static bool start_maintenance_network(void)", start)
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work / "wifi_driver_lifecycle.inc").write_text(source[start:end])
            binary = work / "wifi_driver_lifecycle"
            subprocess.run(
                [
                    compiler,
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I" + str(work),
                    str(ROOT / "firmware/gauge/tests/wifi_driver_lifecycle_test.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            result = subprocess.run([str(binary)], check=True, capture_output=True, text=True)
            self.assertIn("idempotent close passed", result.stdout)


if __name__ == "__main__":
    unittest.main()

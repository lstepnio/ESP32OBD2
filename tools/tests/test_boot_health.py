"""Exercise actual trial confirmation with stopped UI/BLE dependencies, without hardware."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BootHealthTests(unittest.TestCase):
    def test_integrated_trial_never_confirms_without_independent_ui_and_ble(self):
        source = (ROOT / "firmware/gauge/main/main.c").read_text()
        start = source.index("static void boot_health_task(void *arg)")
        function = source[start : source.index("\n}\n", start) + 3]
        harness = (ROOT / "firmware/gauge/tests/boot_health_harness.c").read_text()
        harness += (
            function
            + """
int main(int argc, char **argv) {
    assert(argc == 2);
    mode = (unsigned)atoi(argv[1]);
    g_runtime = &mode;
    int outcome = setjmp(finished);
    if (!outcome) boot_health_task(NULL);
    bool healthy = mode == 1;
    assert(ota_confirmed == healthy);
    assert(config_confirmed == healthy);
    assert(outcome == (healthy ? 1 : 2));
    puts("Production trial acceptance matches independent UI/BLE health");
}
"""
        )
        with tempfile.TemporaryDirectory() as directory:
            source_file, binary = Path(directory) / "trial.c", Path(directory) / "trial"
            source_file.write_text(harness)
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I",
                    str(ROOT / "firmware/gauge/main/inc"),
                    str(source_file),
                    "-o",
                    str(binary),
                ],
                check=True,
            )
            for scenario in range(4):
                with self.subTest(scenario=scenario):
                    subprocess.run([str(binary), str(scenario)], check=True)

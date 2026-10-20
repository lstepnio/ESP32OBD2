"""Reference bootstrap/delegation and surface compatibility without hardware."""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class GaugePlatformTest(unittest.TestCase):
    def build_and_run(self, name, sources, includes=()):
        with tempfile.TemporaryDirectory() as folder:
            binary = str(Path(folder) / name)
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    *["-I" + include for include in includes],
                    *["firmware/gauge/" + source for source in sources],
                    "-o",
                    binary,
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([binary], check=True)

    def test_reference_adapter(self):
        self.build_and_run(
            "board",
            [
                "tests/gauge_board_test.c",
                "main/src/gauge_board_reference.c",
                "main/src/gauge_surface.c",
            ],
            ["firmware/gauge/tests/board_stubs", "firmware/gauge/tests/display_stubs"],
        )

    def test_presentation_profile(self):
        self.build_and_run(
            "presentation",
            [
                "tests/gauge_profile_test.c",
                "main/src/gauge_presentation.c",
                "main/src/gauge_surface.c",
            ],
            ["firmware/gauge/tests/board_stubs"],
        )

    def test_reference_parity_and_other_surfaces(self):
        self.build_and_run("surface", ["tests/gauge_surface_test.c", "main/src/gauge_surface.c"])


if __name__ == "__main__":
    unittest.main()

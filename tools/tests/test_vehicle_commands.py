"""Offline shared-command worker replay. No hardware or Bluetooth access."""

import unittest
from tools import dev


class VehicleCommandsTests(unittest.TestCase):
    def test_shared_worker_replays_three_actions_and_negative_outcomes(self):
        dev.native(
            "vehicle_commands",
            [
                "tests/vehicle_commands_test.c",
                "main/src/vehicle_commands.c",
                "main/src/vehicle_idle_coolant.c",
                "main/src/jeep_idle_driver.c",
                "main/src/jeep_abs_tcs_driver.c",
                "main/src/elm_response.c",
                "main/src/transfer_gate.c",
            ],
            arguments=["contracts/examples/vehicle-command-idle-start.hex"],
        )


if __name__ == "__main__":
    unittest.main()

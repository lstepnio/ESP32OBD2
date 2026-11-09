"""Temporary parked-idle evidence policy and strict identity parser, without devices."""

import unittest
from tools import dev


class ParkedIdleTests(unittest.TestCase):
    def test_actual_primary_worker_with_brake_owner_mode(self):
        self._primary_worker(True)

    def test_actual_primary_worker_with_fixed_fake_adapter(self):
        self._primary_worker(False)

    def test_restricted_build_blocks_start_and_preserves_recovery_conflict(self):
        self._primary_worker(False, actions=False)

    def _primary_worker(self, brake_owner, actions=True):
        dev.native(
            f"parked_idle_runtime_{int(brake_owner)}_{int(actions)}",
            [
                "tests/parked_idle_runtime_test.c",
                "main/src/vehicle_commands_runtime.c",
                "main/src/jeep_idle_parked_test.c",
                "main/src/vehicle_commands.c",
                "main/src/vehicle_idle_coolant.c",
                "main/src/jeep_idle_driver.c",
                "main/src/jeep_abs_tcs_driver.c",
                "main/src/elm_response.c",
                "main/src/transfer_gate.c",
                "main/src/worker_health.c",
            ],
            includes=[
                "-Ifirmware/gauge/tests/runtime_stubs",
                "-include",
                "firmware/gauge/tests/runtime_stubs/environment.h",
                f"-DEGAUGE_FEATURE_VEHICLE_ACTIONS={int(actions)}",
                "-DCONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=1",
                f"-DCONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST={int(brake_owner)}",
            ],
        )

    def test_scoped_identity_guard_and_restoration_policy(self):
        dev.native(
            "jeep_idle_parked",
            [
                "tests/jeep_idle_parked_test.c",
                "main/src/jeep_idle_parked_test.c",
                "main/src/elm_response.c",
            ],
        )


if __name__ == "__main__":
    unittest.main()

"""Candidate build isolation keeps normal defaults and qualification claims intact."""

import unittest
from tools.dev import firmware_variant_config


class CandidateOptionsTests(unittest.TestCase):
    def test_candidate_overrides_qualified_but_preserves_unrelated_settings(self):
        original = "CONFIG_OTHER=123\nCONFIG_EGAUGE_AUTO_ORIENTATION_CANDIDATE_INTERVAL=99\nCONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED=y\n# CONFIG_EGAUGE_AUTO_ORIENTATION_PROBE_ENABLED is not set\n"
        result = firmware_variant_config(
            original, parked_idle_test=True, orientation_candidate=True
        )
        self.assertIn("CONFIG_OTHER=123\n", result)
        self.assertIn("CONFIG_EGAUGE_AUTO_ORIENTATION_CANDIDATE_INTERVAL=99\n", result)
        self.assertEqual(result.count("CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED"), 1)
        self.assertIn("# CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED is not set", result)
        self.assertIn("CONFIG_EGAUGE_AUTO_ORIENTATION_CANDIDATE=y", result)
        self.assertIn("CONFIG_EGAUGE_AUTO_ORIENTATION_PROBE_ENABLED=y", result)
        self.assertIn("CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=y", result)

    def test_parked_only_does_not_turn_on_sensor_candidate(self):
        result = firmware_variant_config("CONFIG_OTHER=123\n", parked_idle_test=True)
        self.assertNotIn("ORIENTATION", result)
        self.assertIn("CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=y", result)

    def test_brake_candidate_is_independent_and_deduplicates_existing_setting(self):
        original = "# CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST is not set\nCONFIG_OTHER=y\n"
        result = firmware_variant_config(original, brake_actions_test=True)
        self.assertEqual(result.count("CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST"), 1)
        self.assertIn("CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST=y", result)
        self.assertIn("CONFIG_OTHER=y", result)
        self.assertNotIn("IDLE_PARKED", result)
        self.assertNotIn("ORIENTATION", result)
        self.assertEqual(firmware_variant_config(original), original)

    def test_combined_candidate_retains_all_requested_flags(self):
        result = firmware_variant_config(
            "", parked_idle_test=True, brake_actions_test=True, orientation_candidate=True
        )
        self.assertIn("CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST=y", result)
        self.assertIn("CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST=y", result)
        self.assertIn("CONFIG_EGAUGE_AUTO_ORIENTATION_CANDIDATE=y", result)
        self.assertIn("# CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED is not set", result)

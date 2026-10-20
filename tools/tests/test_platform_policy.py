"""Publication policy must fail closed for added boards, channels and repositories."""

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from platform_policy import load_policy, require_public_firmware


class PlatformPolicyTest(unittest.TestCase):
    def test_reference_development_publication(self):
        policy = load_policy()
        require_public_firmware(
            policy, policy["referenceBoard"], "development", policy["publicRepository"]
        )

    def test_new_hardware_is_private_by_default(self):
        policy = load_policy()
        for board in (
            "waveshare-esp32-s3-touch-amoled-1.75",
            "waveshare-esp32-s3-touch-lcd-2.8c",
            "android-virtual",
            "unknown",
            "",
        ):
            with self.subTest(board=board), self.assertRaises(ValueError):
                require_public_firmware(policy, board, "development", policy["publicRepository"])

    def test_new_channel_or_repository_cannot_use_public_flow(self):
        policy = load_policy()
        for channel, repository in (
            ("stable", policy["publicRepository"]),
            ("rc", policy["publicRepository"]),
            ("development", "lstepnio/ESP32OBD2-platforms"),
        ):
            with self.subTest(channel=channel, repository=repository), self.assertRaises(ValueError):
                require_public_firmware(policy, policy["referenceBoard"], channel, repository)

    def test_invalid_or_expanded_policy_is_rejected(self):
        original = load_policy()
        changes = (
            {"schemaVersion": True},
            {"schemaVersion": 2},
            {"referenceBoard": ""},
            {"publicRepository": None},
            {"publicFirmwareBoards": [original["referenceBoard"], "other"]},
            {"publicFirmwareChannels": ["development", "stable"]},
            {"otherFirmwareAudience": "public"},
            {"unexpected": True},
        )
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as folder:
                policy = copy.deepcopy(original)
                policy.update(change)
                path = Path(folder) / "policy.json"
                path.write_text(json.dumps(policy))
                with self.assertRaises(ValueError):
                    load_policy(path)


if __name__ == "__main__":
    unittest.main()

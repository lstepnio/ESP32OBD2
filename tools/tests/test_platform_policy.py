"""Publication policy must fail closed for added boards, channels and repositories."""

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from platform_policy import load_policy, require_public_build, require_public_firmware


class PlatformPolicyTest(unittest.TestCase):
    def test_reference_development_publication(self):
        policy = load_policy()
        require_public_firmware(
            policy, policy["referenceBoard"], "development", policy["publicRepository"]
        )

    def test_public_build_requires_explicit_off_definitions(self):
        disabled = [
            f"-DEGAUGE_FEATURE_{name}=0"
            for name in ("DUAL_ADAPTER", "SMART_CYCLE", "VEHICLE_ACTIONS")
        ]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "compile_commands.json"
            entry = {"file": "/src/main/src/ble_companion.c", "arguments": ["cc", *disabled]}
            path.write_text(json.dumps([entry]))
            require_public_build(folder)
            for definition in disabled:
                for bad in ([], [definition[:-1] + "1"], [definition, definition[:-1] + "1"]):
                    with self.subTest(definition=definition, bad=bad):
                        entry["arguments"] = ["cc", *(d for d in disabled if d != definition), *bad]
                        path.write_text(json.dumps([entry]))
                        with self.assertRaises(ValueError):
                            require_public_build(folder)

    def test_public_build_excludes_private_oled_entitlements(self):
        disabled = [
            f"-DEGAUGE_FEATURE_{name}=0"
            for name in ("DUAL_ADAPTER", "SMART_CYCLE", "VEHICLE_ACTIONS")
        ]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "compile_commands.json"
            companion = {"file": "/src/main/src/ble_companion.c", "arguments": ["cc", *disabled]}
            flagged = {
                **companion,
                "arguments": [*companion["arguments"], "-DEGAUGE_OLED_LICENSE_REQUIRED=1"],
            }
            for commands in (
                [flagged],
                [companion, {"file": "/src/main/src/oled_license.c", "arguments": ["cc"]}],
            ):
                with self.subTest(commands=commands), self.assertRaisesRegex(ValueError, "OLED"):
                    path.write_text(json.dumps(commands))
                    require_public_build(folder)

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
            with (
                self.subTest(channel=channel, repository=repository),
                self.assertRaises(ValueError),
            ):
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

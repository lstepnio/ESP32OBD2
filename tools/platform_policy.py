#!/usr/bin/env python3
"""Validate the owner-approved public firmware distribution boundary.

This is a release-process guard, not image attestation or protection against
third parties building already published source. New boards default to private.
"""

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "contracts/platform-policy.json"


def load_policy(path=POLICY):
    policy = json.loads(Path(path).read_text())
    expected = {
        "schemaVersion",
        "referenceBoard",
        "publicRepository",
        "publicFirmwareBoards",
        "publicFirmwareChannels",
        "otherFirmwareAudience",
    }
    if not isinstance(policy, dict) or set(policy) != expected:
        raise ValueError("Unexpected platform policy fields")
    if type(policy["schemaVersion"]) is not int or policy["schemaVersion"] != 1:
        raise ValueError("Unsupported platform policy version")
    for field in ("referenceBoard", "publicRepository"):
        if not isinstance(policy[field], str) or not policy[field].strip():
            raise ValueError(f"Missing {field}")
    if policy["publicFirmwareBoards"] != [policy["referenceBoard"]]:
        raise ValueError("Only the reference board may receive public firmware")
    if policy["publicFirmwareChannels"] != ["development"]:
        raise ValueError("Additional public channels require release-flow review")
    if policy["otherFirmwareAudience"] != "private":
        raise ValueError("Additional hardware must default to private")
    return policy


def require_public_firmware(policy, board, channel, repository):
    if repository != policy["publicRepository"]:
        raise ValueError("Public firmware repository does not match policy")
    if board not in policy["publicFirmwareBoards"]:
        raise ValueError(f"Public firmware is not approved for board: {board}")
    if channel not in policy["publicFirmwareChannels"]:
        raise ValueError(f"Public firmware channel is not approved: {channel}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--board")
    parser.add_argument("--channel", default="development")
    parser.add_argument("--repository", default="lstepnio/ESP32OBD2")
    args = parser.parse_args()
    try:
        policy = load_policy()
        if args.board:
            require_public_firmware(policy, args.board, args.channel, args.repository)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Platform policy: {error}\n")
    print("PASS: public firmware remains limited to the reference board")


if __name__ == "__main__":
    main()

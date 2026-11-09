#!/usr/bin/env python3
"""Issue or verify an offline OLED license with a separate P-256 signing key."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

MAGIC = b"EGOLIC1\x00"
FEATURES = {"dual-adapter": 1, "smart-cycle": 2, "vehicle-actions": 4}
FEATURE_MASK = sum(FEATURES.values())
PAYLOAD_SIZE = len(MAGIC) + 32 + 1
MAX_SIZE = PAYLOAD_SIZE + 72
P256_SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d03010703420004")


def unit_id(factory_mac):
    if not re.fullmatch(r"(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}", factory_mac):
        raise ValueError("Expected the six-byte factory eFuse MAC")
    mac = bytes.fromhex(factory_mac.replace(":", ""))
    if not any(mac) or mac[0] & 3:
        raise ValueError("Expected a globally administered unicast factory MAC")
    return hashlib.sha256(b"egauge/esp32s3/factory-mac/v1\x00" + mac).digest()


def openssl(*args):
    return subprocess.run(["openssl", *args], check=True, capture_output=True, timeout=10).stdout


def require_p256(key, public):
    args = ["pkey", "-in", key, "-pubout", "-outform", "DER"]
    if public:
        args.append("-pubin")
    encoded = openssl(*args)
    if len(encoded) != len(P256_SPKI_PREFIX) + 64 or not encoded.startswith(P256_SPKI_PREFIX):
        raise ValueError("Use a P-256 key with an uncompressed public point")


def issue(factory_mac, feature_mask, signing_key):
    if not 0 <= feature_mask <= FEATURE_MASK:
        raise ValueError("Unsupported entitlement flags")
    if signing_key.stat().st_mode & 0o077:
        raise ValueError("Restrict the signing key to its owner")
    require_p256(signing_key, public=False)
    payload = MAGIC + unit_id(factory_mac) + bytes([feature_mask])
    with tempfile.TemporaryDirectory() as folder:
        file = Path(folder) / "claims"
        file.write_bytes(payload)
        signature = openssl("dgst", "-sha256", "-sign", signing_key, file)
        if not 8 <= len(signature) <= 72:
            raise ValueError("Unexpected P-256 signature size")
    return payload + signature


def verify(license_bytes, factory_mac, verification_key):
    if not PAYLOAD_SIZE + 8 <= len(license_bytes) <= MAX_SIZE:
        raise ValueError("Invalid license length")
    payload, signature = license_bytes[:PAYLOAD_SIZE], license_bytes[PAYLOAD_SIZE:]
    if payload[:-1] != MAGIC + unit_id(factory_mac) or payload[-1] & ~FEATURE_MASK:
        raise ValueError("License target, board identity, or feature set does not match")
    require_p256(verification_key, public=True)
    with tempfile.TemporaryDirectory() as folder:
        claims, proof = Path(folder) / "claims", Path(folder) / "signature"
        claims.write_bytes(payload)
        proof.write_bytes(signature)
        openssl("dgst", "-sha256", "-verify", verification_key, "-signature", proof, claims)
    return [name for name, bit in FEATURES.items() if payload[-1] & bit]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    issue_command = commands.add_parser("issue")
    issue_command.add_argument("--signing-key", required=True, type=Path)
    issue_command.add_argument("--feature", action="append", choices=FEATURES, default=[])
    verify_command = commands.add_parser("verify")
    verify_command.add_argument("--verification-key", required=True, type=Path)
    for command in (issue_command, verify_command):
        command.add_argument("license", type=Path)
    args = parser.parse_args()
    try:
        # Keep the factory address out of command lines and routine logs.
        mac = sys.stdin.read(32).strip()
        if args.action == "issue":
            flags = sum(FEATURES[name] for name in set(args.feature))
            data = issue(mac, flags, args.signing_key)
            fd = os.open(args.license, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as output:
                output.write(data)
            print("OLED license created")
        else:
            with args.license.open("rb") as source:
                features = verify(source.read(MAX_SIZE + 1), mac, args.verification_key)
            print("OLED license verified" + (", " + ", ".join(features) if features else ""))
    except (OSError, ValueError, subprocess.SubprocessError):
        parser.exit(1, "License operation failed. Check the input, key, board and license.\n")


if __name__ == "__main__":
    main()

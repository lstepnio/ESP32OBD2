#!/usr/bin/env python3
"""Create the development signature metadata consumed by OTA opcode 0x28.

The private key stays outside the repository. This is a development artifact,
not a production release manifest or release sequence policy.
"""

import argparse
import hashlib
import json
import os
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path

BOARD = "ESP32-S3-Touch-LCD-1.28"
BOARD_TAG = 0x31534745


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--key", type=Path, help="signing key (required for an alternate target)")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--board", help="trusted target label (requires --board-tag)")
    parser.add_argument(
        "--board-tag",
        type=lambda value: int(value, 0),
        help="nonzero u32 target tag, decimal or hex",
    )
    parser.add_argument(
        "--bundle", type=Path, help="also write a bounded .egauge-dev-update ZIP for Android import"
    )
    parser.add_argument(
        "--expected-version",
        help="require the ESP app descriptor version to match the release version",
    )
    args = parser.parse_args()

    if (args.board is None) != (args.board_tag is None):
        parser.error("--board and --board-tag must be supplied together")
    board = BOARD if args.board is None else args.board
    board_tag = BOARD_TAG if args.board_tag is None else args.board_tag
    if not board.strip() or not 1 <= board_tag <= 0xFFFFFFFF:
        parser.error("board label and nonzero u32 board tag are required")
    if (board == BOARD) != (board_tag == BOARD_TAG):
        parser.error("the reference board label and tag are reserved for each other")
    if board != BOARD and args.key is None:
        parser.error("an alternate target requires an explicit --key")
    key = args.key or Path.home() / ".config/egauge/dev-update-key.pem"

    image = args.image.read_bytes()
    if not 1024 <= len(image) <= 0x300000:
        parser.error("image must fit the inactive 3 MiB OTA slot")
    if not key.is_file():
        parser.error(f"signing key is missing: {key}")
    if len(image) < 208 or image[0] != 0xE9 or image[32:36] != bytes.fromhex("3254cdab"):
        parser.error("image has no supported ESP app descriptor")
    app_version = image[48:80].split(b"\0", 1)[0].decode("ascii", errors="strict")
    if args.expected_version is not None and app_version != args.expected_version:
        parser.error(
            f"image version {app_version!r} does not match release version {args.expected_version!r}"
        )
    digest = hashlib.sha256(image).digest()
    signed_bytes = struct.pack("<II", board_tag, len(image)) + digest
    with tempfile.NamedTemporaryFile(mode="wb", delete=False) as message:
        message.write(signed_bytes)
        message_path = Path(message.name)
    try:
        signature = subprocess.check_output(
            ["openssl", "dgst", "-sha256", "-sign", str(key), str(message_path)]
        )
    finally:
        message_path.unlink(missing_ok=True)
    if not 64 <= len(signature) <= 72:
        parser.error("unexpected P-256 DER signature length")
    manifest = {
        "board": board,
        "boardTag": board_tag,
        "imageLength": len(image),
        "sha256": digest.hex(),
        "signatureDerHex": signature.hex(),
        "signaturePartBytes": 8,
        "transferProtocol": 0,
    }
    bundle_metadata = json.dumps(manifest, separators=(",", ":")) + "\n"
    if len(bundle_metadata.encode("utf-8")) > 4096:
        parser.error("update metadata exceeds the Android import limit")
    output = args.output or args.image.with_suffix(args.image.suffix + ".dev-update.json")
    if output.resolve() == args.image.resolve():
        parser.error("metadata path must differ from the image path")
    output.write_text(json.dumps(manifest, indent=2) + "\n")
    os.chmod(output, 0o600)
    print(output)
    if args.bundle:
        if (
            args.bundle.resolve() == output.resolve()
            or args.bundle.resolve() == args.image.resolve()
        ):
            parser.error("bundle path must differ from the image and metadata paths")
        with zipfile.ZipFile(
            args.bundle,
            "w",
            compression=zipfile.ZIP_DEFLATED,
            compresslevel=6,
            strict_timestamps=True,
        ) as archive:
            archive.writestr("metadata.json", bundle_metadata)
            archive.write(args.image, "firmware.bin")
        os.chmod(args.bundle, 0o600)
        print(args.bundle)


if __name__ == "__main__":
    main()

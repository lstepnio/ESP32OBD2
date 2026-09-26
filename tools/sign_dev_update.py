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

BOARD_TAG = 0x31534745


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--key", type=Path,
                        default=Path.home() / ".config/egauge/dev-update-key.pem")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--bundle", type=Path,
                        help="also write a bounded .egauge-dev-update ZIP for Android import")
    args = parser.parse_args()

    image = args.image.read_bytes()
    if not 1024 <= len(image) <= 0x300000:
        parser.error("image must fit the inactive 3 MiB OTA slot")
    if not args.key.is_file():
        parser.error(f"signing key is missing: {args.key}")
    digest = hashlib.sha256(image).digest()
    signed_bytes = struct.pack("<II", BOARD_TAG, len(image)) + digest
    with tempfile.NamedTemporaryFile(mode="wb", delete=False) as message:
        message.write(signed_bytes)
        message_path = Path(message.name)
    try:
        signature = subprocess.check_output(
            ["openssl", "dgst", "-sha256", "-sign", str(args.key), str(message_path)]
        )
    finally:
        message_path.unlink(missing_ok=True)
    if not 64 <= len(signature) <= 72:
        parser.error("unexpected P-256 DER signature length")
    manifest = {
        "board": "ESP32-S3-Touch-LCD-1.28",
        "boardTag": BOARD_TAG,
        "imageLength": len(image),
        "sha256": digest.hex(),
        "signatureDerHex": signature.hex(),
        "signaturePartBytes": 8,
        "transferProtocol": 0,
    }
    output = args.output or args.image.with_suffix(args.image.suffix + ".dev-update.json")
    if output.resolve() == args.image.resolve():
        parser.error("metadata path must differ from the image path")
    output.write_text(json.dumps(manifest, indent=2) + "\n")
    os.chmod(output, 0o600)
    print(output)
    if args.bundle:
        if args.bundle.resolve() == output.resolve() or args.bundle.resolve() == args.image.resolve():
            parser.error("bundle path must differ from the image and metadata paths")
        with zipfile.ZipFile(args.bundle, "w", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=6, strict_timestamps=True) as archive:
            archive.writestr("metadata.json", json.dumps(manifest, separators=(",", ":")) + "\n")
            archive.write(args.image, "firmware.bin")
        os.chmod(args.bundle, 0o600)
        print(args.bundle)


if __name__ == "__main__":
    main()

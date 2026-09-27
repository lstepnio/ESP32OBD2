#!/usr/bin/env python3
"""Create and sign the development GitHub firmware catalog.

This development catalog uses the existing development update key. Stable and
beta channels require a separately reviewed production trust and release flow.
"""

import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--sequence", required=True, type=int)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--notes", default="Development firmware for authenticated integration testing.")
    parser.add_argument("--key", type=Path,
                        default=Path.home() / ".config/egauge/dev-update-key.pem")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--signature", type=Path, required=True)
    args = parser.parse_args()

    if not args.version or len(args.version) > 40:
        parser.error("invalid version")
    allowed = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_")
    if not args.tag or len(args.tag) > 80 or any(c not in allowed for c in args.tag):
        parser.error("invalid release tag")
    if not 1 <= args.sequence <= 0xFFFFFFFF:
        parser.error("sequence must fit uint32 and be positive")
    if not args.bundle.is_file() or not args.key.is_file():
        parser.error("bundle or signing key is missing")
    bundle = args.bundle.read_bytes()
    if not 1024 <= len(bundle) <= 0x340000:
        parser.error("bundle is outside catalog bounds")
    now = datetime.now(timezone.utc).replace(microsecond=0)
    catalog = {
        "schemaVersion": 1,
        "repository": "lstepnio/ESP32OBD2",
        "generation": args.sequence,
        "generatedAt": now.isoformat().replace("+00:00", "Z"),
        "expiresAt": (now + timedelta(days=180)).isoformat().replace("+00:00", "Z"),
        "releases": [{
            "version": args.version,
            "releaseSequence": args.sequence,
            "channel": "development",
            "boardId": "waveshare-esp32-s3-touch-lcd-1.28",
            "boardRevisions": ["all"],
            "partitionLayout": "egauge-16m-ab-v1",
            "transferProtocol": 0,
            "bundleBytes": len(bundle),
            "bundleSha256": hashlib.sha256(bundle).hexdigest(),
            "bundleUrl": ("https://github.com/lstepnio/ESP32OBD2/releases/download/" +
                          args.tag + "/" + args.bundle.name),
            "releaseNotes": args.notes[:2000],
            "sourceTag": args.tag,
        }],
    }
    raw = (json.dumps(catalog, sort_keys=True, separators=(",", ":")) + "\n").encode()
    args.output.write_bytes(raw)
    subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(args.key),
                    "-out", str(args.signature), str(args.output)], check=True)
    print(args.output)
    print(args.signature)


if __name__ == "__main__":
    main()

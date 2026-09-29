#!/usr/bin/env python3
"""Package a local catalog into small, versioned JSONL/Zstandard shards."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sqlite3
import subprocess
from pathlib import Path


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            value.update(chunk)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path,
                        default=Path("references/vehicle-catalog/catalog.sqlite"))
    parser.add_argument("--output", type=Path,
                        default=Path("data/vehicle-definitions"))
    parser.add_argument("--chunk-mib", type=int, default=64)
    parser.add_argument("--provenance", required=True,
                        help="Exact source package and version for the manifest")
    parser.add_argument("--redistribution-rights", default="unconfirmed",
                        help="Human-reviewed rights status for the manifest")
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        parser.error(f"Output directory is not empty: {args.output}")
    if not args.database.is_file():
        parser.error(f"Catalog does not exist: {args.database}")
    if not shutil.which("zstd"):
        parser.error("zstd is required")
    if args.chunk_mib < 1:
        parser.error("--chunk-mib must be positive")

    args.output.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(f"file:{args.database}?mode=ro", uri=True)
    shards = []
    current = None
    current_path = None
    current_category = None
    part_number = 0
    raw_bytes = 0
    resource_count = 0
    item_count = 0

    def finish() -> None:
        nonlocal current, current_path, raw_bytes, resource_count, item_count
        if current is None:
            return
        current.stdin.close()
        if current.wait() != 0:
            raise RuntimeError(f"Compression failed for {current_path}")
        shards.append({
            "file": current_path.name,
            "category": current_category,
            "sha256": digest(current_path),
            "bytes": current_path.stat().st_size,
            "uncompressed_bytes": raw_bytes,
            "resources": resource_count,
            "top_level_items": item_count,
        })
        print(f"Wrote {current_path.name}: {resource_count} resources", flush=True)
        current = None
        current_path = None
        raw_bytes = resource_count = item_count = 0

    def start(category: str) -> None:
        nonlocal current, current_path, current_category, part_number
        current_category = category
        part_number += 1
        current_path = args.output / f"{category.lower()}-{part_number:03d}.jsonl.zst"
        current = subprocess.Popen(
            ["zstd", "-q", "-10", "-o", str(current_path)],
            stdin=subprocess.PIPE,
        )

    try:
        for name, category, module, count, payload in db.execute(
            "SELECT name, category, module, item_count, payload_json "
            "FROM resources ORDER BY category, name"
        ):
            line = (json.dumps({"resource": name, "category": category,
                                "module": module}, ensure_ascii=False,
                               separators=(",", ":"))[:-1]
                    + ',"definitions":' + payload + "}\n").encode("utf-8")
            if current is None or current_category != category or (
                raw_bytes + len(line) > args.chunk_mib * 1024 * 1024 and resource_count
            ):
                finish()
                start(category)
            current.stdin.write(line)
            raw_bytes += len(line)
            resource_count += 1
            item_count += count
        finish()
        inputs = [dict(filename=row[0], sha256=row[1], resources=row[2]) for row in db.execute(
            "SELECT filename, sha256, resource_count FROM inputs ORDER BY id"
        )]
        manifest = {
            "format": "diagnostic-resource-jsonl-zstd-v1",
            "status": "extracted research definitions; not vehicle verified or enabled",
            "redistribution_rights": args.redistribution_rights,
            "provenance": args.provenance,
            "inputs": inputs,
            "resources": sum(shard["resources"] for shard in shards),
            "top_level_items": sum(shard["top_level_items"] for shard in shards),
            "shards": shards,
        }
        (args.output / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Packaged {manifest['resources']} resources in {len(shards)} shards")
    finally:
        db.close()


if __name__ == "__main__":
    main()

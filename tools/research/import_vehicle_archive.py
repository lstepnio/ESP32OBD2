#!/usr/bin/env python3
"""Rebuild the searchable local SQLite catalog from versioned JSONL/Zstandard shards."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sqlite3
import subprocess
from pathlib import Path

from export_vehicle_catalog import SCHEMA, index_rows


def sha256(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            value.update(chunk)
    return value.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=Path("data/vehicle-definitions"))
    parser.add_argument("--output", type=Path,
                        default=Path("references/vehicle-catalog/catalog.sqlite"))
    args = parser.parse_args()
    if args.output.exists():
        parser.error(f"Output already exists: {args.output}")
    if not shutil.which("zstd"):
        parser.error("zstd is required")
    manifest = json.loads((args.archive / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != "diagnostic-resource-jsonl-zstd-v1":
        parser.error("Unsupported archive format")
    for shard in manifest["shards"]:
        path = args.archive / shard["file"]
        if not path.is_file() or path.stat().st_size != shard["bytes"] or sha256(path) != shard["sha256"]:
            parser.error(f"Missing or invalid shard: {path}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    partial = args.output.with_suffix(args.output.suffix + ".partial")
    if partial.exists():
        parser.error(f"Partial catalog already exists: {partial}")
    database = sqlite3.connect(partial)
    total = 0
    item_total = 0
    try:
        database.executescript(SCHEMA)
        input_ids = {}
        for entry in manifest["inputs"]:
            input_ids[entry["filename"][:-4]] = database.execute(
                "INSERT INTO inputs(filename, sha256, resource_count) VALUES (?, ?, ?)",
                (entry["filename"], entry["sha256"], entry["resources"]),
            ).lastrowid
        database.commit()
        database.execute("BEGIN")
        for shard in manifest["shards"]:
            path = args.archive / shard["file"]
            process = subprocess.Popen(["zstd", "-dc", str(path)], stdout=subprocess.PIPE)
            shard_count = 0
            shard_items = 0
            try:
                for line in process.stdout:
                    record = json.loads(line)
                    name = record["resource"]
                    source_name = next((key for key in input_ids if name.startswith(key + ".")), None)
                    if source_name is None:
                        raise ValueError(f"Resource has no matching input: {name}")
                    payload = record["definitions"]
                    count = len(payload) if isinstance(payload, list) else 1
                    resource_id = database.execute(
                        """INSERT INTO resources
                        (input_id, name, category, module, encoding, item_count, payload_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                        (input_ids[source_name], name, record["category"], record["module"],
                         "archive-json", count,
                         json.dumps(payload, ensure_ascii=False, separators=(",", ":"))),
                    ).lastrowid
                    database.executemany(
                        """INSERT INTO entries
                        (resource_id, item_index, name, mode, pid, unit, dtc,
                         request_header, response_header, command, stop_command)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        index_rows(resource_id, payload),
                    )
                    shard_count += 1
                    shard_items += count
                    total += 1
                    item_total += count
                    if total % 5000 == 0:
                        database.commit()
                        database.execute("BEGIN")
            finally:
                process.stdout.close()
                if process.wait() != 0:
                    raise RuntimeError(f"Cannot decompress {path}")
            if shard_count != shard["resources"] or shard_items != shard["top_level_items"]:
                raise ValueError(f"Archive counts do not match for {path}")
            database.commit()
            database.execute("BEGIN")
            print(f"Imported {path.name}: {shard_count} resources", flush=True)
        database.commit()
        if total != manifest["resources"] or item_total != manifest["top_level_items"]:
            raise ValueError("Archive total counts do not match manifest")
        database.execute("PRAGMA optimize")
        database.close()
        partial.replace(args.output)
        print(f"Rebuilt {args.output}: {total} resources, {item_total} top-level records")
    except BaseException:
        database.close()
        raise


if __name__ == "__main__":
    main()

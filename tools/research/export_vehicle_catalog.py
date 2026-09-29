#!/usr/bin/env python3
"""Index embedded JSON diagnostic resources in a local, Git-ignored SQLite file.

This is a research extractor, not a source of executable vehicle profiles.
It accepts .NET assemblies that contain embedded JSON or Base64/raw-DEFLATE JSON
resources. The input files and output database remain local to the operator.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import sqlite3
import sys
import zlib
from collections import Counter
from pathlib import Path

SCHEMA = """
CREATE TABLE inputs (
    id INTEGER PRIMARY KEY,
    filename TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    resource_count INTEGER NOT NULL
);
CREATE TABLE resources (
    id INTEGER PRIMARY KEY,
    input_id INTEGER NOT NULL REFERENCES inputs(id),
    name TEXT NOT NULL UNIQUE,
    category TEXT NOT NULL,
    module TEXT,
    encoding TEXT NOT NULL,
    item_count INTEGER NOT NULL,
    payload_json TEXT NOT NULL
);
CREATE TABLE entries (
    resource_id INTEGER NOT NULL REFERENCES resources(id),
    item_index INTEGER NOT NULL,
    name TEXT,
    mode TEXT,
    pid TEXT,
    unit TEXT,
    dtc TEXT,
    request_header TEXT,
    response_header TEXT,
    command TEXT,
    stop_command TEXT,
    PRIMARY KEY (resource_id, item_index)
);
CREATE TABLE errors (
    input_id INTEGER NOT NULL REFERENCES inputs(id),
    resource_name TEXT NOT NULL,
    error TEXT NOT NULL
);
CREATE INDEX entries_pid_idx ON entries(mode, pid);
CREATE INDEX entries_dtc_idx ON entries(dtc);
CREATE INDEX entries_name_idx ON entries(name);
CREATE INDEX resources_category_module_idx ON resources(category, module);
"""


def decode(raw: bytes) -> tuple[object, str]:
    try:
        source = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError("Resource is not UTF-8 text") from exc
    if source.lstrip().startswith(("[", "{")):
        try:
            return json.loads(source), "json"
        except ValueError:
            try:
                import json5
                return json5.loads(source), "json5"
            except ImportError as exc:
                raise SystemExit("Install research dependencies: python3 -m pip install dnfile json5") from exc
            except ValueError as exc:
                raise ValueError("Resource contains malformed JSON") from exc
    try:
        decoded = zlib.decompress(base64.b64decode(source, validate=True), -15)
        return json.loads(decoded), "base64+raw-deflate+json"
    except (ValueError, zlib.error, UnicodeDecodeError) as exc:
        raise ValueError("Could not decode JSON or Base64/raw-DEFLATE JSON") from exc


def location(resource_name: str) -> tuple[str, str | None]:
    # The logical path after Assets describes content without depending on
    # assembly filenames or namespaces.
    tail = resource_name.split(".Assets.", 1)[-1]
    parts = tail.split(".")
    if parts[0] == "Modules" and len(parts) > 2:
        category = parts[1]
        module = parts[2] if category in {"LiveData", "DTC", "Activation", "Write"} else None
        if module is None:
            category = "ModuleIndex" if category.endswith("Modules") else category
        return category, module
    return parts[0], parts[1] if len(parts) > 2 else None


def scalar(item: dict, *keys: str) -> str | None:
    for key in keys:
        value = item.get(key)
        if value is not None and not isinstance(value, (dict, list)):
            return str(value)
    return None


def index_rows(resource_id: int, payload: object):
    items = payload if isinstance(payload, list) else [payload]
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        yield (
            resource_id,
            index,
            scalar(item, "N", "Name", "TranslationId", "Id", "Desc"),
            scalar(item, "Mode", "Service"),
            scalar(item, "Pid", "PID", "Identifier"),
            scalar(item, "Unit"),
            scalar(item, "DTC", "Code"),
            scalar(item, "RequestHeaderId"),
            scalar(item, "RespondeHeaderId", "ResponseHeaderId"),
            scalar(item, "Cmd", "RCommand", "SCommand", "Command"),
            scalar(item, "StopCommand", "ECommand"),
        )


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def export(paths: list[Path], output: Path) -> None:
    try:
        import dnfile
    except ImportError as exc:
        raise SystemExit("Install research dependencies: python3 -m pip install dnfile json5") from exc
    output.parent.mkdir(parents=True, exist_ok=True)
    partial = output.with_suffix(output.suffix + ".partial")
    if partial.exists():
        partial.unlink()
    database = sqlite3.connect(partial)
    counts: Counter[str] = Counter()
    failures = 0
    total = 0
    try:
        database.executescript(SCHEMA)
        database.execute("BEGIN")
        for path in paths:
            assembly = dnfile.dnPE(str(path))
            if not assembly.net or not assembly.net.resources:
                raise ValueError(f"No managed resources found in {path}")
            resources = assembly.net.resources
            input_id = database.execute(
                "INSERT INTO inputs(filename, sha256, resource_count) VALUES (?, ?, ?)",
                (path.name, file_hash(path), len(resources)),
            ).lastrowid
            for resource in resources:
                name = str(resource.name)
                total += 1
                try:
                    payload, encoding = decode(resource.data)
                    category, module = location(name)
                    item_count = len(payload) if isinstance(payload, list) else 1
                    resource_id = database.execute(
                        """INSERT INTO resources
                        (input_id, name, category, module, encoding, item_count, payload_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                        (input_id, name, category, module, encoding, item_count,
                         json.dumps(payload, ensure_ascii=False, separators=(",", ":"))),
                    ).lastrowid
                    database.executemany(
                        """INSERT INTO entries
                        (resource_id, item_index, name, mode, pid, unit, dtc,
                         request_header, response_header, command, stop_command)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        index_rows(resource_id, payload),
                    )
                    counts[category] += 1
                except Exception as exc:  # Retain every unreadable resource for audit.
                    database.execute(
                        "INSERT INTO errors(input_id, resource_name, error) VALUES (?, ?, ?)",
                        (input_id, name, str(exc)),
                    )
                    failures += 1
                if total % 5000 == 0:
                    database.commit()
                    database.execute("BEGIN")
                    print(f"Indexed {total} resources...", flush=True)
            database.commit()
            database.execute("BEGIN")
        database.commit()
        database.execute("PRAGMA optimize")
        database.close()
        partial.replace(output)
    except BaseException:
        database.close()
        raise
    print(f"Database: {output}")
    print(f"Resources: {total}; unreadable: {failures}")
    print("Categories: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    if failures:
        print("Inspect the errors table before using this catalog.", file=sys.stderr)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assemblies", nargs="+", type=Path, help="Managed assemblies to inspect")
    parser.add_argument("--output", type=Path,
                        default=Path("references/vehicle-catalog/catalog.sqlite"))
    arguments = parser.parse_args()
    for path in arguments.assemblies:
        if not path.is_file():
            parser.error(f"Input does not exist: {path}")
    if arguments.output.exists():
        parser.error(f"Output already exists: {arguments.output}")
    export(arguments.assemblies, arguments.output)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Search a local diagnostic-resource catalog or assemble a vehicle inventory."""

from __future__ import annotations

import argparse
import json
import sqlite3
from pathlib import Path


DEFAULT_DATABASE = Path("references/vehicle-catalog/catalog.sqlite")


def connect(path: Path) -> sqlite3.Connection:
    if not path.is_file():
        raise SystemExit(f"Catalog does not exist: {path}")
    database = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    database.row_factory = sqlite3.Row
    return database


def find_resource(database: sqlite3.Connection, suffix: str) -> dict:
    rows = database.execute(
        "SELECT name, category, module, payload_json FROM resources WHERE name LIKE ?",
        ("%." + suffix,),
    ).fetchall()
    if len(rows) != 1:
        raise ValueError(f"Expected one resource ending in {suffix}; found {len(rows)}")
    row = dict(rows[0])
    row["payload"] = json.loads(row.pop("payload_json"))
    return row


def vehicle_report(database: sqlite3.Connection, key: str) -> dict:
    vehicles = find_resource(database, "Vehicles.Vehicle.json")["payload"]
    matches = [item for item in vehicles if item.get("ShortName", "").casefold() == key.casefold()
               or item.get("Name", "").casefold() == key.casefold()]
    if len(matches) != 1:
        raise ValueError(f"Expected one vehicle matching {key!r}; found {len(matches)}")
    vehicle = matches[0]
    index = find_resource(database, "Modules." + vehicle["ModulesFilePath"] + ".json")
    module_names = set(vehicle.get("ModuleList", []))
    modules = [item for item in index["payload"] if item.get("Folder") in module_names]
    adaptation_names = [name for name in vehicle.get("AdaptationsFilePath", "").split(";") if name]
    adaptations = {}
    for name in adaptation_names:
        resource = find_resource(database, "VehicleAdaptations." + name + ".json")
        adaptations[name] = resource["payload"]
    return {
        "status": "static candidate inventory; no vehicle validation",
        "vehicle": vehicle,
        "module_index": index["name"],
        "modules": modules,
        "adaptations": adaptations,
    }


def search(database: sqlite3.Connection, args: argparse.Namespace) -> None:
    conditions = []
    parameters: list[object] = []
    if args.category:
        conditions.append("r.category = ?")
        parameters.append(args.category)
    if args.module:
        conditions.append("UPPER(r.module) = UPPER(?)")
        parameters.append(args.module)
    if args.mode:
        conditions.append("UPPER(e.mode) = UPPER(?)")
        parameters.append(args.mode)
    if args.pid:
        conditions.append("UPPER(e.pid) = UPPER(?)")
        parameters.append(args.pid)
    if args.dtc:
        conditions.append("UPPER(e.dtc) = UPPER(?)")
        parameters.append(args.dtc)
    if args.name:
        conditions.append("e.name LIKE ?")
        parameters.append("%" + args.name + "%")
    if not conditions:
        raise SystemExit("Set at least one search filter")
    query = """SELECT r.id AS resource_id, r.name AS resource_name, r.category,
               r.module, e.item_index, e.name, e.mode, e.pid, e.unit, e.dtc,
               e.request_header, e.response_header, e.command, e.stop_command
               FROM entries e JOIN resources r ON r.id = e.resource_id WHERE """
    query += " AND ".join(conditions) + " ORDER BY r.name, e.item_index LIMIT ? OFFSET ?"
    parameters.extend([args.limit, args.offset])
    cached_payloads = {}
    for row in database.execute(query, parameters):
        item = dict(row)
        if args.raw:
            resource_id = item["resource_id"]
            if resource_id not in cached_payloads:
                payload_text = database.execute(
                    "SELECT payload_json FROM resources WHERE id = ?", (resource_id,)
                ).fetchone()[0]
                cached_payloads[resource_id] = json.loads(payload_text)
            payload = cached_payloads[resource_id]
            item["raw"] = payload[item["item_index"]] if isinstance(payload, list) else payload
        print(json.dumps(item, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE)
    commands = parser.add_subparsers(dest="operation", required=True)
    vehicle = commands.add_parser("vehicle", help="Show the candidate module and adaptation inventory")
    vehicle.add_argument("key", help="Vehicle short name or full name")
    vehicle.add_argument("--output", type=Path, help="Write JSON report to a local file")
    find = commands.add_parser("search", help="Search indexed records, one JSON object per line")
    for field in ("category", "module", "mode", "pid", "dtc", "name"):
        find.add_argument("--" + field)
    find.add_argument("--limit", type=int, default=25)
    find.add_argument("--offset", type=int, default=0)
    find.add_argument("--raw", action="store_true", help="Include the complete decoded record")
    args = parser.parse_args()
    database = connect(args.database)
    try:
        if args.operation == "vehicle":
            report = vehicle_report(database, args.key)
            rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
            if args.output:
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(rendered, encoding="utf-8")
                print(args.output)
            else:
                print(rendered, end="")
        else:
            search(database, args)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    finally:
        database.close()


if __name__ == "__main__":
    main()

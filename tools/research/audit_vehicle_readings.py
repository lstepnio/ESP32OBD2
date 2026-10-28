#!/usr/bin/env python3
"""Compare one responder's captured Mode 01 support with the executable catalog.

No adapter access or probing. Bitmap advertisement is not a validated measurement.
Output omits vehicle identifiers, adapter addresses and original capture paths.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
NON_SCALAR = {
    0x01: (
        "diagnostic",
        "MIL and monitor status",
        "Existing shared MIL/fault path; not a scalar gauge",
    ),
    0x03: (
        "named_state_gap",
        "Fuel-system loop status",
        "Separate status bytes need named states, not a whole-word number",
    ),
    0x12: (
        "named_state_gap",
        "Secondary air status",
        "Decode the status bits and reject reserved values",
    ),
    0x13: (
        "sensor_inventory",
        "Oxygen sensor layout",
        "Discover fitted sensors before offering their measurements",
    ),
    0x1C: (
        "diagnostic_identity",
        "OBD compliance standard",
        "Useful diagnostic context, not a dashboard measurement",
    ),
    0x1D: (
        "sensor_inventory",
        "Alternate oxygen sensor layout",
        "Discover fitted sensors before offering their measurements",
    ),
    0x1E: (
        "named_state_gap",
        "Power take-off status",
        "Bit state; advertisement does not prove this swap has a PTO",
    ),
    0x41: (
        "diagnostic_gap",
        "Monitor status this drive cycle",
        "Requires monitor bit decoding and diagnostic context",
    ),
}
for _pid in range(0x14, 0x1C):
    NON_SCALAR[_pid] = (
        "component_gap",
        "Narrowband oxygen sensor voltage / trim",
        "One reply has two signals; component decoding and sensor-layout attribution are required",
    )
for _pid in range(0x24, 0x2C):
    NON_SCALAR[_pid] = (
        "component_gap",
        "Wideband oxygen equivalence / voltage",
        "Two independently scaled signals share the reply",
    )
for _pid in range(0x34, 0x3C):
    NON_SCALAR[_pid] = (
        "component_gap",
        "Wideband oxygen equivalence / current",
        "Two independently scaled signals share the reply",
    )


def audit(discovery: dict, catalog: dict, responder: str = "7E8") -> dict:
    if not re.fullmatch(r"[0-7][0-9A-F]{2}", responder):
        raise ValueError("Use one explicit 11-bit responder")
    maps = discovery.get("standard_support")
    if not isinstance(maps, dict) or not 1 <= len(maps) <= 8:
        raise ValueError("Missing or invalid captured standard support")
    by_pid = {}
    for row in catalog["readings"]:
        request = row["definition"]["request"]
        if request["service"] == "01" and request.get("responseId") == responder:
            by_pid.setdefault(int(request["identifier"], 16), []).append(row)
    observed = {}
    for base_text, replies in maps.items():
        if not isinstance(base_text, str) or not re.fullmatch(r"0|[1-9][0-9]{0,2}", base_text):
            raise ValueError("Support bases must be canonical decimal multiples of 32")
        base = int(base_text)
        if base % 32 or base > 224 or not isinstance(replies, dict):
            raise ValueError("Invalid support range")
        bits = replies.get(responder)
        if bits is None:
            continue
        if type(bits) is not int or not 0 <= bits <= 0xFFFFFFFF:
            raise ValueError("Invalid support bitmap")
        observed[base] = bits
    if 0 not in observed:
        raise ValueError("No base-zero support reply for the selected responder")
    rows = []
    pending = []
    for base, bits in sorted(observed.items()):
        if base and not (observed.get(base - 32, 0) & 1):
            raise ValueError("Unlinked support range; do not combine unrelated discovery sessions")
        for offset in range(1, 33):
            if not bits & (1 << (32 - offset)):
                continue
            pid = base + offset
            if pid > 255:
                raise ValueError("Invalid continuation beyond the supported PID space")
            found = by_pid.get(pid, [])
            if offset == 32:
                kind, name, why = (
                    "support_map",
                    "PID range continuation",
                    "Discovery metadata, not a reading",
                )
                if pid not in observed:
                    pending.append(f"{pid:02X}")
            elif found:
                kind, name, why = (
                    "selectable",
                    found[0]["name"],
                    "Catalog definition exists; measurement validation is separate",
                )
            else:
                kind, name, why = NON_SCALAR.get(
                    pid,
                    (
                        "catalog_gap",
                        "Uncatalogued advertised PID",
                        "Find a documented decoder before adding it",
                    ),
                )
            rows.append(
                {
                    "pid": f"{pid:02X}",
                    "name": name,
                    "coverage": kind,
                    "readingIds": [r["id"] for r in found],
                    "note": why,
                }
            )
    counts = {
        kind: sum(r["coverage"] == kind for r in rows)
        for kind in sorted({r["coverage"] for r in rows})
    }
    return {
        "schema": 1,
        "responder": responder,
        "scope": "Captured Mode 01 advertisement only; enhanced-module inventory is separate",
        "maps": {f"{base:02X}": f"{bits:08X}" for base, bits in sorted(observed.items())},
        "counts": counts,
        "pendingSupportRanges": pending,
        "readings": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("discovery", type=Path)
    parser.add_argument("--catalog", type=Path, default=ROOT / "contracts/reading-catalog.json")
    parser.add_argument("--responder", default="7E8")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(
        json.loads(args.discovery.read_text()), json.loads(args.catalog.read_text()), args.responder
    )
    text = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text)
    else:
        print(text, end="")


if __name__ == "__main__":
    main()

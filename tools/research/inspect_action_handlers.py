#!/usr/bin/env python3
"""Record diagnostic action handler calls and literals from a managed assembly.

The report is a static inventory. It is not a complete control-flow analysis or
an executable vehicle procedure.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

try:
    import dnfile
    from dncil.cil.body.reader import read_method_body_from_bytes
except ImportError as exc:
    raise SystemExit("Install research dependencies: python3 -m pip install dnfile dncil") from exc


JK_ACTION_TYPES = (
    "ABSTCKillAdaptationViewModel",
    "ABSTCKillTIPMAdaptationViewModel",
    "Rpm28CRDAdaptationViewModel",
    "RpmPbKWPAdaptationViewModel",
    "ZTestStepsRoutineViewModel",
)


def token_name(pe, value: object) -> str:
    raw = str(value)
    if not raw.startswith("token("):
        return raw
    token = int(raw[6:-1], 16)
    table = token >> 24
    index = (token & 0xFFFFFF) - 1
    try:
        if table == 10:
            return str(pe.net.mdtables.MemberRef.rows[index].Name)
        if table == 6:
            return str(pe.net.mdtables.MethodDef.rows[index].Name)
        if table == 43:
            return str(pe.net.mdtables.MethodSpec.rows[index].Method.row.Name)
    except (AttributeError, IndexError):
        pass
    return raw


def method_report(pe, method) -> dict | None:
    if not method.row.Rva:
        return None
    body = read_method_body_from_bytes(pe.get_data(method.row.Rva, 65536))
    calls = []
    strings = []
    for instruction in body.instructions:
        opcode = str(instruction.opcode)
        operand = str(instruction.operand)
        if opcode == "ldstr" and operand.startswith("string token("):
            token = int(operand[13:-1], 16)
            strings.append(str(pe.net.user_strings.get(token & 0xFFFFFF)))
        if opcode.startswith(("call", "newobj")):
            calls.append(token_name(pe, instruction.operand))
    return {
        "name": str(method.row.Name),
        "instruction_count": len(body.instructions),
        "calls": list(dict.fromkeys(calls)),
        "string_literals": strings,
    }


def inspect(path: Path, selected_types: list[str]) -> dict:
    pe = dnfile.dnPE(str(path))
    if not pe.net:
        raise ValueError("Input is not a managed assembly")
    outer = {str(row.TypeName): row for row in pe.net.mdtables.TypeDef.rows}
    nested = {}
    for row in pe.net.mdtables.NestedClass.rows:
        nested.setdefault(str(row.EnclosingClass.row.TypeName), []).append(row.NestedClass.row)
    report = {
        "status": "static handler inventory; commands and vehicle behavior unverified",
        "assembly_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "types": [],
    }
    for name in selected_types:
        if name not in outer:
            raise ValueError(f"Type not found: {name}")
        item = {"name": name, "methods": [], "nested_types": []}
        for method in outer[name].MethodList:
            if found := method_report(pe, method):
                item["methods"].append(found)
        for subtype in nested.get(name, []):
            child = {"name": str(subtype.TypeName), "methods": []}
            for method in subtype.MethodList:
                if found := method_report(pe, method):
                    child["methods"].append(found)
            item["nested_types"].append(child)
        report["types"].append(item)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assembly", type=Path)
    parser.add_argument("--type", dest="types", action="append",
                        help="Managed type to inspect; repeat for multiple types")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.assembly.is_file():
        parser.error(f"Assembly does not exist: {args.assembly}")
    result = inspect(args.assembly, args.types or list(JK_ACTION_TYPES))
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
        print(args.output)
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Reject a linked gauge image that puts its fixed LVGL pool in internal RAM."""
import argparse
import json
from pathlib import Path

MAX_INTERNAL_STATIC_BYTES = 192 * 1024


def validate_internal_budget(sections):
    used = sum(sections.get(name, 0) for name in (
        ".dram0.data", ".dram0.bss", ".iram0.text", ".iram0.vectors"))
    if used > MAX_INTERNAL_STATIC_BYTES:
        raise ValueError(f"Internal static RAM {used} exceeds reviewed {MAX_INTERNAL_STATIC_BYTES}-byte budget")
    return used


def validate_arena(arenas):
    if len(arenas) != 1:
        raise ValueError("Expected exactly one fixed LVGL arena")
    arena = arenas[0]
    if arena["bytes"] != 64 * 1024:
        raise ValueError("Review the memory budget before changing the 64 KiB LVGL arena")
    if not arena["section"].startswith(".ext_ram.bss") or not (
        0x3C000000 <= arena["address"] < arena["address"] + arena["bytes"] <= 0x3E000000
    ):
        raise ValueError("LVGL arena must be linked into ESP32-S3 external BSS")


def inspect_image(path):
    from elftools.elf.elffile import ELFFile
    with path.open("rb") as source:
        elf = ELFFile(source)
        symbols = elf.get_section_by_name(".symtab")
        if symbols is None:
            raise ValueError("Unstripped firmware ELF is required")
        arenas = []
        for symbol in symbols.iter_symbols():
            if symbol.name.startswith("work_mem_int") and symbol["st_size"]:
                arenas.append({"address": symbol["st_value"], "bytes": symbol["st_size"],
                               "section": elf.get_section(symbol["st_shndx"]).name})
        validate_arena(arenas)
        sections = {s.name: s["sh_size"] for s in elf.iter_sections()
                    if s.name in (".dram0.data", ".dram0.bss", ".iram0.text", ".iram0.vectors", ".ext_ram.bss")}
        used = validate_internal_budget(sections)
        hook = symbols.get_symbol_by_name("memory_health_allocation_failed")
        if not hook or elf.get_section(hook[0]["st_shndx"]).name != ".iram0.text":
            raise ValueError("Allocation failure hook must remain in IRAM")
        return {"lvgl_arena": arenas[0], "section_bytes": sections,
                "internal_static_bytes": used, "internal_static_limit": MAX_INTERNAL_STATIC_BYTES}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("elf", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(inspect_image(args.elf), sort_keys=True))
    except ValueError as failure:
        parser.exit(1, f"Memory placement check failed: {failure}\n")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generate SwiftUI color constants from the shared eGauge design tokens."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design/tokens.json"
TARGET = ROOT / "ios/EGaugeApp/DesignTokens.generated.swift"


def render() -> str:
    colors = json.loads(SOURCE.read_text())["color"]
    lines = ["// Generated from design/tokens.json. Run tools/generate_ios_tokens.py.",
             "import SwiftUI", "", "enum DesignTokenColor {"]
    for name, value in colors.items():
        rgb = [int(value[index:index + 2], 16) for index in (1, 3, 5)]
        lines.append(f"    static let {name} = Color(red: {rgb[0]}.0 / 255, "
                     f"green: {rgb[1]}.0 / 255, blue: {rgb[2]}.0 / 255)")
    return "\n".join(lines + ["}", ""])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = render()
    if args.check:
        if not TARGET.exists() or TARGET.read_text() != expected:
            raise SystemExit("iOS design tokens are stale; run tools/generate_ios_tokens.py")
        print("iOS design colors match design/tokens.json")
    else:
        TARGET.write_text(expected)
        print(TARGET)

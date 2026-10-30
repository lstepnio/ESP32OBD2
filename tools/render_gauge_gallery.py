#!/usr/bin/env python3
"""Convert production LVGL host frames to the README gallery; no mock UI or dependencies."""

import argparse
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
FRAMES = {"numeric": 1, "arc": 2, "bar": 3, "trend": 4, "dual": 5, "dual-arc": 6, "alerts": 20}


def png(rgb):
    width = height = 240
    if len(rgb) != width * height * 3:
        raise ValueError("Expected a 240x240 RGB888 production frame")

    def chunk(kind, data):
        return (
            struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
        )

    # Physical reference panel is circular; preserve every visible RGB pixel and
    # make its nonexistent corners transparent for both GitHub and the website.
    rows = b""
    for y in range(height):
        row = bytearray(b"\0")
        for x in range(width):
            i = (y * width + x) * 3
            alpha = 255 if (x - 119.5) ** 2 + (y - 119.5) ** 2 <= 120**2 else 0
            row.extend(rgb[i : i + 3])
            row.append(alpha)
        rows += row
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=Path, default=ROOT / "artifacts/checks/ui-renders")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    output = ROOT / "docs/assets/gauge"
    sources = {name: f"demo-{frame:02d}" for name, frame in FRAMES.items()}
    sources.update(
        {
            "action-ready": "actions-ready",
            "action-active": "actions-active",
            "action-recovery": "actions-unknown",
        }
    )
    for name, frame in sources.items():
        rendered = png((args.frames / f"{frame}.rgb").read_bytes())
        target = output / f"{name}.png"
        if args.check:
            if not target.exists() or target.read_bytes() != rendered:
                raise SystemExit(f"Gallery needs regeneration: {target.relative_to(ROOT)}")
        else:
            output.mkdir(parents=True, exist_ok=True)
            target.write_bytes(rendered)
    print(
        f"PASS: {len(sources)} production gauge screens " + ("match" if args.check else "written")
    )


if __name__ == "__main__":
    main()

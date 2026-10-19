#!/usr/bin/env python3
"""Convert production LVGL host frames to the README gallery; no mock UI or dependencies."""

import argparse
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
FRAMES = {"numeric": 1, "arc": 2, "dual": 5, "alerts": 16}


def png(rgb):
    width = height = 240
    if len(rgb) != width * height * 3:
        raise ValueError("Expected a 240x240 RGB888 production frame")

    def chunk(kind, data):
        return (
            struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
        )

    rows = b"".join(b"\0" + rgb[i : i + width * 3] for i in range(0, len(rgb), width * 3))
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(rows, 9))
        + chunk(b"IEND", b"")
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=Path, default=ROOT / "artifacts/checks/ui-renders")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    output = ROOT / "docs/assets/gauge"
    for name, frame in FRAMES.items():
        rendered = png((args.frames / f"demo-{frame:02d}.rgb").read_bytes())
        target = output / f"{name}.png"
        if args.check:
            if not target.exists() or target.read_bytes() != rendered:
                raise SystemExit(f"Gallery needs regeneration: {target.relative_to(ROOT)}")
        else:
            output.mkdir(parents=True, exist_ok=True)
            target.write_bytes(rendered)
    print("PASS: four production gauge screens " + ("match" if args.check else "written"))


if __name__ == "__main__":
    main()

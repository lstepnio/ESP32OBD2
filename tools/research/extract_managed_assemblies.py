#!/usr/bin/env python3
"""Extract managed assemblies from an Android assembly-store split package."""

from __future__ import annotations

import argparse
import struct
import zipfile
from pathlib import Path


def decompress_lz4(source: bytes, target_size: int) -> bytes:
    output = bytearray()
    index = 0
    while index < len(source):
        token = source[index]
        index += 1
        literal_size = token >> 4
        if literal_size == 15:
            while True:
                extension = source[index]
                index += 1
                literal_size += extension
                if extension != 255:
                    break
        output.extend(source[index:index + literal_size])
        index += literal_size
        if index >= len(source):
            break
        offset = source[index] | (source[index + 1] << 8)
        index += 2
        if not offset or offset > len(output):
            raise ValueError("Invalid LZ4 match offset")
        match_size = (token & 15) + 4
        if token & 15 == 15:
            while True:
                extension = source[index]
                index += 1
                match_size += extension
                if extension != 255:
                    break
        for _ in range(match_size):
            output.append(output[-offset])
    if len(output) != target_size:
        raise ValueError(f"Unexpected LZ4 size: {len(output)} != {target_size}")
    return bytes(output)


def extract(apk: Path, output: Path, prefix: str) -> int:
    with zipfile.ZipFile(apk) as archive:
        data = archive.read("lib/arm64-v8a/libassembly-store.so")
    offset = data.find(b"XABA")
    if offset < 0:
        raise ValueError("No assembly store found")
    _, _, count, _, index_size = struct.unpack_from("<5I", data, offset)
    start = offset + 20 + index_size
    name_position = start + count * 28
    names = []
    for _ in range(count):
        size = struct.unpack_from("<I", data, name_position)[0]
        name_position += 4
        names.append(data[name_position:name_position + size].decode("utf-8"))
        name_position += size
    output.mkdir(parents=True, exist_ok=True)
    extracted = 0
    for index, name in enumerate(names):
        if not name.startswith(prefix):
            continue
        _, blob_offset, blob_size = struct.unpack_from("<3I", data, start + index * 28)
        blob = data[offset + blob_offset:offset + blob_offset + blob_size]
        if blob.startswith(b"XALZ"):
            original_size = struct.unpack_from("<I", blob, 8)[0]
            assembly = decompress_lz4(blob[12:], original_size)
        else:
            assembly = blob
        if not assembly.startswith(b"MZ"):
            raise ValueError(f"Extracted resource is not a managed DLL: {name}")
        target = output / name
        if target.exists():
            raise FileExistsError(f"Refusing to replace existing assembly: {target}")
        target.write_bytes(assembly)
        print(f"{name}: {len(assembly)} bytes")
        extracted += 1
    return extracted


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="APK split containing libassembly-store.so")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--prefix", default="", help="Only extract assemblies with this name prefix")
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    print(f"Extracted {extract(args.apk, args.output, args.prefix)} assemblies")


if __name__ == "__main__":
    main()

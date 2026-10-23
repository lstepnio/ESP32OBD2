#!/usr/bin/env python3
"""Retain reference-build provenance and recovery/debug outputs outside the OTA ZIP."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import subprocess
import uuid
import zipfile

import yaml

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "esp32-idf-project.bin",
    "esp32-idf-project.elf",
    "esp32-idf-project.map",
    "bootloader/bootloader.bin",
    "partition_table/partition-table.bin",
    "ota_data_initial.bin",
)


def collect(root, build, source_sha):
    root, build = Path(root), Path(build)
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("Expected full source SHA")
    lock = yaml.safe_load((root / "firmware/gauge/dependencies.lock").read_text())
    packages, licenses = [], {}
    for name, record in sorted(lock["dependencies"].items()):
        identifier = "SPDXRef-" + re.sub(r"[^A-Za-z0-9.-]", "-", name)
        package = {
            "name": name,
            "SPDXID": identifier,
            "versionInfo": str(record["version"]),
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": "NOASSERTION",
            "licenseDeclared": "NOASSERTION",
            "copyrightText": "NOASSERTION",
        }
        if digest := record.get("component_hash"):
            package["checksums"] = [{"algorithm": "SHA256", "checksumValue": digest}]
        packages.append(package)
        if name == "idf":
            component = Path(
                json.loads((build / "project_description.json").read_text())["idf_path"]
            )
            candidates = [component / "LICENSE"]
        else:
            local = record["source"].get("type") == "local"
            component = root / (
                "firmware/gauge/bsp/waveshare_lcd_128_round_touch"
                if local
                else "firmware/gauge/managed_components/" + name.replace("/", "__")
            )
            candidates = [
                p
                for p in component.rglob("*")
                if p.is_file()
                and p.name.upper().startswith(("LICENSE", "LICENCE", "COPYING", "NOTICE"))
            ]
        if not candidates and name == "janos/waveshare_lcd_128_round_touch":
            component = root / "firmware/gauge"
            candidates = [component / "LICENSE"]
        candidates = [p for p in candidates if p.is_file()]
        if not candidates:
            raise ValueError(f"Missing dependency license inventory: {name}")
        for license_file in candidates:
            relative = license_file.relative_to(component).as_posix()
            licenses["licenses/" + identifier + "/" + relative] = license_file.read_bytes()
    payload = {"build/" + name: (build / name).read_bytes() for name in REQUIRED}
    for name in ("sdkconfig", "partitions.csv"):
        payload["source/" + name] = (root / "firmware/gauge" / name).read_bytes()
    # Strip machine-local paths; preserve all version/hash/source-type facts.
    resolved = {
        name: {k: v for k, v in item.items() if k != "source"}
        | {"sourceType": item["source"]["type"]}
        for name, item in lock["dependencies"].items()
    }
    payload["dependencies.json"] = json.dumps(resolved, sort_keys=True, indent=2).encode()
    payload.update(licenses)
    for name in (
        "THIRD_PARTY_NOTICES.md",
        "third_party/NotoSans-OFL.txt",
        "firmware/gauge/LICENSE",
    ):
        payload["notices/" + name] = (root / name).read_bytes()
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    spdx = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "eGauge reference firmware resolved dependencies",
        "documentNamespace": "https://egauge.majjix.com/sbom/" + str(uuid.uuid4()),
        "creationInfo": {"creators": ["Tool: egauge-release-evidence"], "created": now},
        "packages": packages,
        "relationships": [
            {
                "spdxElementId": "SPDXRef-DOCUMENT",
                "relationshipType": "DESCRIBES",
                "relatedSpdxElement": p["SPDXID"],
            }
            for p in packages
        ],
    }
    payload["sbom.spdx.json"] = json.dumps(spdx, indent=2).encode()
    provenance = {
        "sourceCommit": source_sha,
        "board": "waveshare-esp32-s3-touch-lcd-1.28",
        "created": now,
        "toolchain": "ESP-IDF 5.4.1",
        "qualifiedContainer": "espressif/idf:v5.4.1@sha256:6b4adab7b282e9261a154d7130fb4945a3c28bd35c2e914357c2f522643cb92a",
        "execution": {
            "platform": platform.platform(),
            "githubRunId": os.environ.get("GITHUB_RUN_ID"),
        },
        "scope": "Resolved dependency inventory and retained build outputs; license conclusions are not asserted",
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in sorted(payload.items())},
    }
    payload["provenance.json"] = json.dumps(provenance, indent=2).encode()
    return payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    subprocess.run(
        ["git", "diff", "--exit-code", "HEAD", "--"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.DEVNULL,
    )
    payload = collect(ROOT, ROOT / "firmware/gauge/build", sha)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(payload.items()):
            archive.writestr(name, data)
    print("Retained reference build evidence: " + args.output.name)


if __name__ == "__main__":
    main()

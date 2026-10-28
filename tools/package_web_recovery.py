#!/usr/bin/env python3
"""Stage the published development application and matching USB support regions.

This is recovery engineering, not a firmware release/signing path. No private key
is read. Supporting boot files are explicitly recorded as rebuilt, not original
GitHub release artifacts. Output stays outside Git; its public manifest is reviewed.
"""

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.2.0-dev.60"
SOURCE = "860dc3f9f9ffa7e28a388a7100fb6e61ff616a39"
BUNDLE_SHA = "a23086270ad84194db2c7c145f008100d78202bfaa21bb72e4eb4efdd2b4590e"
CATALOG_SHA = "a12317df65cb842c9694cc38a2f6b1e08b296f06cdee9a95d8b16e64a7ef446e"
BOARD = "waveshare-esp32-s3-touch-lcd-1.28"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify_signature(message, signature):
    with tempfile.TemporaryDirectory() as directory:
        message_path = Path(directory) / "message"
        signature_path = Path(directory) / "signature"
        message_path.write_bytes(message)
        signature_path.write_bytes(signature)
        subprocess.run(
            [
                "openssl",
                "dgst",
                "-sha256",
                "-verify",
                str(ROOT / "firmware/gauge/main/certs/dev-update-public.pem"),
                "-signature",
                str(signature_path),
                str(message_path),
            ],
            check=True,
            capture_output=True,
        )


def published_application(inputs):
    catalog_data = (inputs / "egauge-release-catalog.json").read_bytes()
    if sha(catalog_data) != CATALOG_SHA:
        raise ValueError("Catalog differs from pinned release")
    verify_signature(catalog_data, (inputs / "egauge-release-catalog.sig").read_bytes())
    release = json.loads(catalog_data)["releases"][0]
    bundle = inputs / release["bundleUrl"].rsplit("/", 1)[1]
    if sha(bundle.read_bytes()) != BUNDLE_SHA or release["bundleSha256"] != BUNDLE_SHA:
        raise ValueError("Bundle differs from pinned release")
    with zipfile.ZipFile(bundle) as archive:
        if set(archive.namelist()) != {"metadata.json", "firmware.bin"}:
            raise ValueError("Unexpected bundle entries")
        image = archive.read("firmware.bin")
        metadata = json.loads(archive.read("metadata.json"))
    if (
        not 1024 <= len(image) <= 0x300000
        or metadata["boardTag"] != 0x31534745
        or metadata["imageLength"] != len(image)
        or metadata["sha256"] != sha(image)
    ):
        raise ValueError("Application metadata mismatch")
    verify_signature(
        struct.pack("<II", 0x31534745, len(image)) + hashlib.sha256(image).digest(),
        bytes.fromhex(metadata["signatureDerHex"]),
    )
    if (
        image[0] != 0xE9
        or image[32:36] != bytes.fromhex("3254cdab")
        or image[48:80].split(b"\0")[0].decode() != VERSION
    ):
        raise ValueError("Application descriptor mismatch")
    return image


def image_header(data):
    if (
        len(data) < 24
        or data[0] != 0xE9
        or data[2:4] != bytes([2, 0x4F])
        or data[12:14] != b"\x09\x00"
    ):
        raise ValueError("Expected ESP32-S3 / DIO / 80 MHz / 16 MB image")


def verify_apk(apk, certificate, expected_version, tools=None, expected_code=None):
    """Derive artifact identity from Android's verifiers, never a ZIP filename."""
    tools = (
        Path(tools) if tools else Path(os.environ.get("ANDROID_HOME", "")) / "build-tools/36.0.0"
    )
    signer, inspector = tools / "apksigner", tools / "aapt2"
    if not signer.is_file() or not inspector.is_file():
        raise ValueError("APK verification requires Android build-tools 36.0.0; set ANDROID_HOME")
    verified = subprocess.check_output(
        [str(signer), "verify", "--print-certs", str(apk)], text=True, timeout=60
    )
    signers = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", verified, re.MULTILINE
    )
    if len(signers) != 1 or signers[0].lower() != certificate:
        raise ValueError("APK signer does not match the reviewed fingerprint")
    badging = subprocess.check_output(
        [str(inspector), "dump", "badging", str(apk)], text=True, timeout=60
    )
    identity = re.search(
        r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    if (
        not identity
        or identity[1] != "com.lstepnio.egauge"
        or identity[3] != expected_version
        or (expected_code is not None and int(identity[2]) != expected_code)
    ):
        raise ValueError("APK package/version does not match the reviewed source")
    return {
        "version": identity[3],
        "versionCode": int(identity[2]),
        "packageName": identity[1],
        "variant": "debug" if "application-debuggable" in badging else "release",
        "signingCertificateSha256": signers[0].lower(),
    }


def package(inputs, source, apk, app_commit, certificate, output):
    # The export must contain the release's exact board configuration and layout.
    for name in [
        "firmware/gauge/sdkconfig",
        "firmware/gauge/partitions.csv",
        "firmware/gauge/CMakeLists.txt",
    ]:
        expected = subprocess.check_output(["git", "show", f"{SOURCE}:{name}"], cwd=ROOT)
        if (source / name).read_bytes() != expected:
            raise ValueError(f"Release source mismatch: {name}")
    build = source / "firmware/gauge/build"
    description = json.loads((build / "project_description.json").read_text())
    idf_version = subprocess.check_output(
        ["git", "describe", "--tags", "--dirty", "--always"], cwd=description["idf_path"], text=True
    ).strip()
    if idf_version != "v5.4.1" or description["target"] != "esp32s3":
        raise ValueError("Support build must use ESP-IDF v5.4.1 / ESP32-S3")
    application = published_application(inputs)
    boot = (build / "bootloader/bootloader.bin").read_bytes()
    table = (build / "partition_table/partition-table.bin").read_bytes()
    image_header(application)
    image_header(boot)
    if not 0 < len(boot) <= 0x8000 or len(table) != 3072:
        raise ValueError("Support region bounds mismatch")
    # Compare every partition descriptor to the reviewed A/B layout, not just size.
    expected = [
        ("nvs", 0x9000, 0x6000),
        ("phy_init", 0xF000, 0x1000),
        ("otadata", 0x10000, 0x2000),
        ("config_a", 0x12000, 0x20000),
        ("config_b", 0x32000, 0x20000),
        ("ota_0", 0x60000, 0x300000),
        ("ota_1", 0x360000, 0x300000),
    ]
    actual = []
    for offset in range(0, len(table), 32):
        magic, _, _, start, length, label, _ = struct.unpack(
            "<HBBII16sI", table[offset : offset + 32]
        )
        if magic != 0x50AA:
            break
        actual.append((label.split(b"\0")[0].decode(), start, length))
    if actual != expected:
        raise ValueError("Partition layout mismatch")
    if len(certificate) != 64 or any(c not in "0123456789abcdef" for c in certificate):
        raise ValueError("APK signing-certificate SHA-256 required")
    subprocess.run(["git", "cat-file", "-e", app_commit + "^{commit}"], cwd=ROOT, check=True)
    app_source = subprocess.check_output(
        ["git", "show", app_commit + ":android/app/build.gradle.kts"], cwd=ROOT, text=True
    )
    expected = re.search(r'versionName = "([^"]+)"', app_source)
    if expected is None:
        raise ValueError("Reviewed App source has no explicit version")
    expected_code = re.search(r"versionCode = ([0-9]+)", app_source)
    if expected_code is None:
        raise ValueError("Reviewed App source has no explicit version code")
    app_identity = verify_apk(apk, certificate, expected[1], expected_code=int(expected_code[1]))
    files = [
        ("bootloader.bin", 0, boot),
        ("partition-table.bin", 0x8000, table),
        ("ota-selector.bin", 0x10000, b"\xff" * 8192),
        ("application.bin", 0x60000, application),
    ]
    directory = "dev60-" + sha(b"".join(data for _, _, data in files))[:12]
    records = {}

    def save(name, data):
        path = output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        records[name] = {"sha256": sha(data), "size": len(data)}
        return {"url": "/downloads/" + name, **records[name]}

    parts = [
        {"address": address, **save(directory + "/" + name, data)} for name, address, data in files
    ]
    recovery = {
        "board": BOARD,
        "chip": "ESP32-S3",
        "flashSize": "16MB",
        "version": VERSION,
        "sourceCommit": SOURCE,
        "sourceRelease": "https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v" + VERSION,
        "applicationProvenance": "Exact signed published application; catalog and application signatures verified",
        "supportProvenance": "Bootloader and partition table rebuilt from release source with ESP-IDF v5.4.1; blank OTA selector selects slot zero",
        "bundleSha256": BUNDLE_SHA,
        "parts": parts,
    }
    recovery_record = save(
        directory + "/recovery.json", (json.dumps(recovery, indent=2) + "\n").encode()
    )
    apk_data = apk.read_bytes()
    if not zipfile.is_zipfile(apk):
        raise ValueError("Not an APK archive")
    app_record = save("egauge-android-" + sha(apk_data)[:16] + ".apk", apk_data)
    manifest = {
        "schema": 1,
        "firmware": {"version": VERSION, "release": recovery["sourceRelease"], **recovery_record},
        "app": {
            **app_identity,
            "sourceCommit": app_commit,
            "sourceProvenance": "Operator-supplied build revision; APK signature, package and version independently verified",
            **app_record,
        },
        "files": records,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["input", "source", "apk", "output"]:
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--app-commit", required=True)
    parser.add_argument("--apk-certificate-sha256", required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        parser.error("Use an empty output directory")
    package(
        args.input, args.source, args.apk, args.app_commit, args.apk_certificate_sha256, args.output
    )
    print("Verified signed application and packaged USB recovery; no firmware release created")


if __name__ == "__main__":
    main()

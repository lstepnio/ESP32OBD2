"""USB recovery package trust and deployable-site boundaries."""

import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / (name + ".py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


package = module("package_web_recovery")
site = module("build_website")


class RecoveryTests(unittest.TestCase):
    def test_only_pinned_board_flash_headers(self):
        header = bytearray(24)
        header[0], header[2], header[3], header[12] = 0xE9, 2, 0x4F, 9
        package.image_header(header)
        for index, value in [(0, 0), (2, 0), (3, 0x5F), (12, 0)]:
            changed = bytearray(header)
            changed[index] = value
            with self.assertRaises(ValueError):
                package.image_header(changed)

    def test_signature_verifier_rejects_modified_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            public = root / "firmware/gauge/main/certs/dev-update-public.pem"
            public.parent.mkdir(parents=True)
            key, message, signature = [root / name for name in ("key", "message", "sig")]
            message.write_bytes(b"signed public recovery image")
            subprocess.run(
                [
                    "openssl",
                    "ecparam",
                    "-name",
                    "prime256v1",
                    "-genkey",
                    "-noout",
                    "-out",
                    str(key),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["openssl", "pkey", "-in", str(key), "-pubout", "-out", str(public)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "openssl",
                    "dgst",
                    "-sha256",
                    "-sign",
                    str(key),
                    "-out",
                    str(signature),
                    str(message),
                ],
                check=True,
                capture_output=True,
            )
            with patch.object(package, "ROOT", root):
                package.verify_signature(message.read_bytes(), signature.read_bytes())
                with self.assertRaises(subprocess.CalledProcessError):
                    package.verify_signature(b"altered image", signature.read_bytes())

    def test_pinned_manifest_and_recovery_inventory_agree(self):
        inventory = json.loads((ROOT / "website/downloads/manifest.json").read_text())
        raw = (ROOT / "website/recovery/package.json").read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), inventory["firmware"]["sha256"])
        recovery = json.loads(raw)
        for part in recovery["parts"]:
            record = inventory["files"][part["url"].removeprefix("/downloads/")]
            self.assertEqual(record, {"sha256": part["sha256"], "size": part["size"]})

    def test_deployable_build_requires_real_downloads(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "--downloads"):
                site.build(Path(directory))

    def test_changed_download_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs, output = root / "inputs", root / "output"
            output.mkdir()
            inventory = json.loads((ROOT / "website/downloads/manifest.json").read_text())
            first = inputs / next(iter(inventory["files"]))
            first.parent.mkdir(parents=True)
            first.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "Download verification failed"):
                site.build(output, inputs)


if __name__ == "__main__":
    unittest.main()


class ApkIdentityTests(unittest.TestCase):
    def test_verifier_derives_identity_and_rejects_wrong_signer_package_or_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for tool in ("apksigner", "aapt2"):
                (root / tool).touch()
            signer = "a" * 64
            signed = "Signer #1 certificate SHA-256 digest: " + signer
            badging = "package: name='com.lstepnio.egauge' versionCode='51' versionName='0.2.0-dev.51'\napplication-debuggable"
            with patch.object(package.subprocess, "check_output", side_effect=[signed, badging]):
                result = package.verify_apk(root / "app.apk", signer, "0.2.0-dev.51", root)
                self.assertEqual(result["versionCode"], 51)
                self.assertEqual(result["variant"], "debug")
            with patch.object(package.subprocess, "check_output", side_effect=[signed, badging]):
                with self.assertRaises(ValueError):
                    package.verify_apk(
                        root / "app.apk", signer, "0.2.0-dev.51", root, expected_code=52
                    )
            for wrong, version, outputs in [
                ("b" * 64, "0.2.0-dev.51", [signed]),
                (signer, "different", [signed, badging]),
                (
                    signer,
                    "0.2.0-dev.51",
                    [signed, badging.replace("com.lstepnio.egauge", "other.package")],
                ),
            ]:
                with patch.object(package.subprocess, "check_output", side_effect=outputs):
                    with self.assertRaises(ValueError):
                        package.verify_apk(root / "app.apk", wrong, version, root)
            with patch.object(
                package.subprocess,
                "check_output",
                side_effect=subprocess.CalledProcessError(1, "apksigner"),
            ):
                with self.assertRaises(subprocess.CalledProcessError):
                    package.verify_apk(root / "generic.zip", signer, "0.2.0-dev.51", root)

    def test_absent_verification_tools_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "build-tools"):
                package.verify_apk(Path(directory) / "app.apk", "a" * 64, "0.2.0-dev.51", directory)

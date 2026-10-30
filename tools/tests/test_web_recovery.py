"""USB recovery package trust and deployable-site boundaries."""

import hashlib
import copy
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
            with (
                patch.object(
                    site,
                    "review_images",
                    return_value={
                        "publicationReady": True,
                        "changedImageInputs": [],
                        "pendingCaptures": [],
                    },
                ),
                self.assertRaisesRegex(ValueError, "--downloads"),
            ):
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
            with (
                patch.object(
                    site,
                    "review_images",
                    return_value={
                        "publicationReady": True,
                        "changedImageInputs": [],
                        "pendingCaptures": [],
                    },
                ),
                self.assertRaisesRegex(ValueError, "Download verification failed"),
            ):
                site.build(output, inputs)


if __name__ == "__main__":
    unittest.main()


class ApkIdentityTests(unittest.TestCase):
    def test_app_refresh_preserves_firmware_and_rejects_corruption_before_writing(self):
        inventory = {
            "firmware": {"version": "reviewed-fallback"},
            "app": {"url": "/downloads/old.apk", "signingCertificateSha256": "a" * 64},
            "files": {
                "fallback/application.bin": {"sha256": package.sha(b"firmware"), "size": 8},
                "old.apk": {"sha256": package.sha(b"old"), "size": 3},
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "website/downloads/manifest.json"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps(inventory))
            downloads, output = root / "downloads", root / "output"
            (downloads / "fallback").mkdir(parents=True)
            (downloads / "fallback/application.bin").write_bytes(b"firmware")
            output.mkdir()
            apk = root / "new.apk"
            apk.write_bytes(b"verified-apk")
            with (
                patch.object(package, "ROOT", root),
                patch.object(
                    package, "source_apk_identity", return_value={"versionCode": 53}
                ) as verify,
                patch.object(package.zipfile, "is_zipfile", return_value=True),
            ):
                result = package.refresh_apk(downloads, apk, "source", output)
                verify.assert_called_once_with(apk, "source", "a" * 64)
                self.assertEqual(result["firmware"], inventory["firmware"])
                self.assertEqual((output / "fallback/application.bin").read_bytes(), b"firmware")
                self.assertNotIn("old.apk", result["files"])
                self.assertEqual(result["app"]["versionCode"], 53)
                (downloads / "fallback/application.bin").write_bytes(b"tampered")
                empty = root / "empty"
                empty.mkdir()
                with self.assertRaisesRegex(ValueError, "verification failed"):
                    package.refresh_apk(downloads, apk, "source", empty)
                self.assertEqual(list(empty.iterdir()), [])

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


class WebsiteMetadataTests(unittest.TestCase):
    def test_gallery_is_shared_escaped_and_source_traced(self):
        content = json.loads((ROOT / "website/content.json").read_text())
        changed = copy.deepcopy(content)
        changed["screens"][0]["label"] = '<img src="untrusted">'
        choices, payload = site.gallery(changed)
        self.assertIn("&lt;img", choices)
        self.assertNotIn('<img src="untrusted">', choices)
        self.assertIn("dual-arc", json.loads(payload))
        changed["screens"][1]["key"] = changed["screens"][0]["key"]
        with self.assertRaisesRegex(ValueError, "duplicate"):
            site.gallery(changed)

    def test_generated_site_has_gallery_and_keeps_indexing_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            manifest = site.build(output, metadata_only=True)
            html = (output / "index.html").read_text()
            script = (output / manifest["assetPrefix"].lstrip("/") / "app.js").read_text()
            self.assertIn('data-screen="dual-arc"', html)
            self.assertIn('"dual-arc":', script)
            self.assertNotIn("EGAUGE_SCREEN_DATA", script)
            self.assertIn('content="noindex, nofollow"', html)
            self.assertIn('property="og:image"', html)

    def test_missing_claim_scope_or_evidence_is_rejected(self):
        content = json.loads((ROOT / "website/content.json").read_text())
        html = (ROOT / "website/index.html").read_text()
        for field in ("scope", "evidence"):
            changed = copy.deepcopy(content)
            del changed["claims"]["CORE"][field]
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "Missing claim"):
                site.validate_claims(changed, html)
        changed = copy.deepcopy(content)
        del changed["claims"]["CORE"]
        with self.assertRaisesRegex(ValueError, "claim provenance"):
            site.validate_claims(changed, html)

    def test_missing_image_provenance_is_rejected(self):
        content = json.loads((ROOT / "website/content.json").read_text())
        content["imageProvenance"] = []
        with self.assertRaisesRegex(ValueError, "Missing image provenance"):
            site.review_images(content, (ROOT / "website/index.html").read_text())

    def test_changed_inputs_need_exact_documented_equivalence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            asset = root / "docs/assets/gauge/numeric.png"
            asset.parent.mkdir(parents=True)
            asset.write_bytes(b"capture")
            source = root / "renderer.c"
            source.write_text("baseline")
            content = {"screens": [{"key": "numeric"}], "imageProvenance": ["capture.json"]}
            baseline = hashlib.sha256(source.read_bytes()).hexdigest()
            metadata = {
                "inputProvenance": {
                    "revision": "capture-revision",
                    "paths": ["renderer.c"],
                    "sha256": {"renderer.c": baseline},
                },
                "entries": [
                    {
                        "path": "docs/assets/gauge/numeric.png",
                        "sha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
                    }
                ],
            }
            provenance = root / "capture.json"

            def save():
                provenance.write_text(json.dumps(metadata))

            save()
            with patch.object(site, "ROOT", root):
                self.assertTrue(site.review_images(content, "")["publicationReady"])
                saved = metadata.pop("inputProvenance")
                save()
                with self.assertRaisesRegex(ValueError, "Missing image input provenance"):
                    site.review_images(content, "")
                metadata["inputProvenance"] = saved
                save()
                source.write_text("changed")
                report = site.review_images(content, "")
                self.assertFalse(report["publicationReady"])
                self.assertEqual(report["changedImageInputs"][0]["changedInputs"], ["renderer.c"])
                (root / "review.md").write_text("Review found an unchanged represented state")
                metadata["equivalenceReview"] = {
                    "reviewedOn": "2026-10-29",
                    "reason": "Unchanged represented state",
                    "evidence": ["review.md"],
                    "sha256": {"renderer.c": hashlib.sha256(source.read_bytes()).hexdigest()},
                }
                save()
                self.assertTrue(site.review_images(content, "")["publicationReady"])
                source.write_text("changed again")
                self.assertFalse(site.review_images(content, "")["publicationReady"])
                asset.write_bytes(b"replaced without provenance")
                with self.assertRaisesRegex(ValueError, "capture hash mismatch"):
                    site.review_images(content, "")

    def test_deployable_build_rejects_unreviewed_visual_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(site, "review_images", return_value={"publicationReady": False}):
                with self.assertRaisesRegex(ValueError, "semantic review/capture pending"):
                    site.build(Path(directory))

    def test_responsive_image_sources_are_link_checked(self):
        parsed = site.SiteHTML()
        parsed.feed('<source srcset="/small.webp 1x, /large.webp 2x">')
        self.assertEqual(parsed.links, ["/small.webp", "/large.webp"])

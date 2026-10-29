"""Retained outputs must be complete, hashed, path-sanitized and license-bearing."""

import hashlib
import importlib.util
import json
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "release_evidence", ROOT / "tools/release_evidence.py"
)
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceTests(unittest.TestCase):
    def test_missing_outputs_fail_and_inventory_covers_every_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            gauge = root / "firmware/gauge"
            gauge.mkdir(parents=True)
            build = gauge / "build"
            build.mkdir()
            sdk = root / "sdk"
            sdk.mkdir()
            (sdk / "LICENSE").write_text("fixture license")
            (build / "project_description.json").write_text(
                json.dumps({"idf_path": str(sdk), "config_file": str(gauge / "sdkconfig")})
            )
            (gauge / "dependencies.lock").write_text(
                "dependencies:\n  idf:\n    version: 5.4.1\n    source:\n      type: idf\n      path: /private/machine\n"
            )
            (gauge / "sdkconfig").write_text("fixture")
            (gauge / "partitions.csv").write_text("fixture")
            with self.assertRaises(FileNotFoundError):
                evidence.collect(root, build, "a" * 40)
            for name in evidence.REQUIRED:
                path = build / name
                path.parent.mkdir(exist_ok=True, parents=True)
                path.write_bytes(name.encode())
            for name in (
                "THIRD_PARTY_NOTICES.md",
                "third_party/NotoSans-OFL.txt",
                "firmware/gauge/LICENSE",
            ):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture notice")
            result = evidence.collect(root, build, "a" * 40)
            provenance = json.loads(result["provenance.json"])
            self.assertEqual(provenance["sourceCommit"], "a" * 40)
            self.assertNotIn(b"/private/machine", result["dependencies.json"])
            self.assertEqual(set(provenance["files"]), set(result) - {"provenance.json"})
            for name, digest in provenance["files"].items():
                self.assertEqual(hashlib.sha256(result[name]).hexdigest(), digest)
            self.assertEqual(json.loads(result["sbom.spdx.json"])["spdxVersion"], "SPDX-2.3")
            (sdk / "LICENSE").unlink()
            with self.assertRaisesRegex(ValueError, "license"):
                evidence.collect(root, build, "a" * 40)

    def test_candidate_evidence_uses_selected_image_and_recorded_configuration(self):
        for variant in ("parked-idle", "orientation-candidate", "brake-actions-test"):
            with self.subTest(variant=variant), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                gauge = root / "firmware/gauge"
                build = gauge / ("build-" + variant)
                build.mkdir(parents=True)
                sdk = root / "sdk"
                sdk.mkdir()
                (sdk / "LICENSE").write_text("fixture license")
                config = root / "artifacts/checks" / (variant + ".sdkconfig")
                config.parent.mkdir(parents=True)
                config.write_text("candidate config " + variant)
                (gauge / "sdkconfig").write_text("normal config")
                (build / "project_description.json").write_text(
                    json.dumps({"idf_path": str(sdk), "config_file": str(config)})
                )
                (gauge / "dependencies.lock").write_text(
                    "dependencies:\n  idf:\n    version: 5.4.1\n    source:\n      type: idf\n"
                )
                (gauge / "partitions.csv").write_text("fixture partitions")
                for name in evidence.REQUIRED:
                    path = build / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes((variant + ":" + name).encode())
                for name in (
                    "THIRD_PARTY_NOTICES.md",
                    "third_party/NotoSans-OFL.txt",
                    "firmware/gauge/LICENSE",
                ):
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text("fixture notice")
                result = evidence.collect(root, build, "b" * 40)
                self.assertEqual(result["source/sdkconfig"], config.read_bytes())
                self.assertEqual(
                    result["build/esp32-idf-project.bin"],
                    (build / "esp32-idf-project.bin").read_bytes(),
                )
                provenance = json.loads(result["provenance.json"])
                self.assertEqual(provenance["sourceCommit"], "b" * 40)
                for name, digest in provenance["files"].items():
                    self.assertEqual(hashlib.sha256(result[name]).hexdigest(), digest)
                config.unlink()
                with self.assertRaises(FileNotFoundError):
                    evidence.collect(root, build, "b" * 40)
                with self.assertRaisesRegex(ValueError, "source SHA"):
                    evidence.collect(root, build, "short")

    def test_exact_checkout_trust_handles_container_owner_without_accepting_dirty_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "source.txt").write_text("committed")
            subprocess.run(["git", "add", "source.txt"], cwd=root, check=True)
            subprocess.run(
                [
                    "git",
                    "-c",
                    "user.name=Fixture",
                    "-c",
                    "user.email=fixture@example.invalid",
                    "commit",
                    "-qm",
                    "fixture",
                ],
                cwd=root,
                check=True,
            )
            env = {
                "GIT_TEST_ASSUME_DIFFERENT_OWNER": "1",
                "GIT_CONFIG_NOSYSTEM": "1",
                "GIT_CONFIG_GLOBAL": os.devnull,
            }
            with mock.patch.dict(os.environ, env):
                denied = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True)
                self.assertNotEqual(denied.returncode, 0)
                self.assertIn(b"dubious ownership", denied.stderr)
                self.assertRegex(evidence.checked_source(root), r"^[0-9a-f]{40}$")
                # Command-scoped trust must not persist to a later untrusted call.
                self.assertNotEqual(
                    subprocess.run(
                        ["git", "rev-parse", "HEAD"],
                        cwd=root,
                        capture_output=True,
                    ).returncode,
                    0,
                )
                (root / "source.txt").write_text("changed")
                with self.assertRaises(subprocess.CalledProcessError):
                    evidence.checked_source(root)

    def test_cli_selects_build_and_rejects_dirty_sources_before_archiving(self):
        for variant in (
            None,
            "build-parked-idle",
            "build-orientation-candidate",
            "build-brake-actions-test",
        ):
            with self.subTest(variant=variant), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "evidence.zip"
                arguments = ["release_evidence.py", "--output", str(output)]
                build = ROOT / "firmware/gauge" / (variant or "build")
                if variant:
                    arguments += ["--build-dir", str(build)]
                with (
                    mock.patch("sys.argv", arguments),
                    mock.patch.object(evidence.subprocess, "check_output", return_value="a" * 40),
                    mock.patch.object(evidence.subprocess, "run") as clean_check,
                    mock.patch.object(evidence, "collect", return_value={}) as collect,
                ):
                    evidence.main()
                    collect.assert_called_once_with(ROOT, build, "a" * 40)
                    self.assertEqual(
                        clean_check.call_args.args[0],
                        [
                            "git",
                            "-c",
                            f"safe.directory={ROOT}",
                            "diff",
                            "--exit-code",
                            "HEAD",
                            "--",
                        ],
                    )
                    self.assertTrue(clean_check.call_args.kwargs["check"])
                    output.unlink()
                    clean_check.side_effect = evidence.subprocess.CalledProcessError(1, "git")
                    collect.reset_mock()
                    with self.assertRaises(evidence.subprocess.CalledProcessError):
                        evidence.main()
                    collect.assert_not_called()
                    self.assertFalse(output.exists())

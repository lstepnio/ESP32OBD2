"""Retained outputs must be complete, hashed, path-sanitized and license-bearing."""

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

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
            (build / "project_description.json").write_text(json.dumps({"idf_path": str(sdk)}))
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

"""Exercise the actual development signer with disposable P-256 keys."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SIGNER = Path(__file__).resolve().parents[1] / "sign_dev_update.py"


class SignDevUpdateTest(unittest.TestCase):
    def test_default_and_explicit_target_sign_exact_protocol_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            key = root / "key.pem"
            public_key = root / "public.pem"
            subprocess.run(
                ["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", key],
                check=True,
            )
            subprocess.run(
                ["openssl", "pkey", "-in", key, "-pubout", "-out", public_key], check=True
            )
            image = bytearray(1024)
            image[0] = 0xE9
            image[32:36] = bytes.fromhex("3254cdab")
            image[48:53] = b"1.2.3"
            image_path = root / "firmware.bin"
            image_path.write_bytes(image)
            bundle = root / "update.zip"
            signature = root / "signature.der"
            message = root / "message.bin"
            digest = hashlib.sha256(image).digest()
            cases = (
                ([], "ESP32-S3-Touch-LCD-1.28", 0x31534745, b"EGS1"),
                (
                    ["--board", "Synthetic-Test-Board", "--board-tag", "0xf2434755"],
                    "Synthetic-Test-Board",
                    0xF2434755,
                    bytes.fromhex("554743f2"),
                ),
            )
            for options, board, tag, tag_bytes in cases:
                with self.subTest(board=board):
                    subprocess.run(
                        [
                            sys.executable,
                            SIGNER,
                            image_path,
                            "--key",
                            key,
                            "--bundle",
                            bundle,
                            "--expected-version",
                            "1.2.3",
                            *options,
                        ],
                        capture_output=True,
                        check=True,
                    )
                    with zipfile.ZipFile(bundle) as archive:
                        self.assertEqual(archive.namelist(), ["metadata.json", "firmware.bin"])
                        self.assertEqual(archive.read("firmware.bin"), image)
                        metadata = json.loads(archive.read("metadata.json"))
                    self.assertEqual(metadata["board"], board)
                    self.assertEqual(metadata["boardTag"], tag)
                    self.assertEqual(metadata["imageLength"], len(image))
                    self.assertEqual(metadata["sha256"], digest.hex())
                    self.assertEqual(metadata["transferProtocol"], 0)
                    self.assertEqual(metadata["signaturePartBytes"], 8)
                    signature.write_bytes(bytes.fromhex(metadata["signatureDerHex"]))
                    message.write_bytes(tag_bytes + b"\x00\x04\x00\x00" + digest)
                    subprocess.run(
                        [
                            "openssl",
                            "dgst",
                            "-sha256",
                            "-verify",
                            public_key,
                            "-signature",
                            signature,
                            message,
                        ],
                        capture_output=True,
                        check=True,
                    )

    def test_incomplete_or_reserved_target_is_rejected_before_signing(self):
        for options, error in (
            (["--board", "Synthetic-Test-Board"], b"must be supplied together"),
            (["--board", "Synthetic-Test-Board", "--board-tag", "0"], b"nonzero u32"),
            (["--board", "Synthetic-Test-Board", "--board-tag", "0x31534745"], b"reserved"),
            (["--board", "Synthetic-Test-Board", "--board-tag", "0xf2434755"], b"explicit --key"),
        ):
            with self.subTest(options=options):
                result = subprocess.run(
                    [sys.executable, SIGNER, "unused.bin", *options], capture_output=True
                )
                self.assertEqual(result.returncode, 2)
                self.assertIn(error, result.stderr)

"""Exercise the actual OpenSSL signature and board binding with disposable keys."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from board_license import PAYLOAD_SIZE, issue, openssl, unit_id, verify


class BoardLicenseTest(unittest.TestCase):
    def test_signature_unit_product_and_flags(self):
        mac = "00:11:22:33:44:55"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key, public = root / "private.pem", root / "public.pem"
            key.touch(mode=0o600)
            key.write_bytes(openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout"))
            public.write_bytes(openssl("pkey", "-in", key, "-pubout"))
            license_bytes = issue(mac, 3, key)
            self.assertEqual(verify(license_bytes, mac, public), ["dual-adapter", "smart-cycle"])
            self.assertEqual(unit_id(mac.upper()), unit_id(mac.lower()))
            with self.assertRaises(ValueError):
                verify(license_bytes, "00:11:22:33:44:56", public)
            with self.assertRaises(ValueError):
                verify(b"X" + license_bytes[1:], mac, public)
            with self.assertRaises(ValueError):
                verify(
                    license_bytes[: PAYLOAD_SIZE - 1] + b"\x80" + license_bytes[PAYLOAD_SIZE:],
                    mac,
                    public,
                )
            with self.assertRaises(subprocess.CalledProcessError):
                verify(
                    license_bytes[: PAYLOAD_SIZE - 1] + b"\x07" + license_bytes[PAYLOAD_SIZE:],
                    mac,
                    public,
                )
            with self.assertRaises(ValueError):
                verify(license_bytes[:PAYLOAD_SIZE], mac, public)
            key.write_bytes(openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout"))
            public.write_bytes(openssl("pkey", "-in", key, "-pubout"))
            with self.assertRaises(subprocess.CalledProcessError):
                verify(license_bytes, mac, public)

    def test_reject_invalid_factory_ids(self):
        for invalid in ("", "11:22:33:44:55", "00:00:00:00:00:00", "02:11:22:33:44:55"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                unit_id(invalid)

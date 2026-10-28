"""Synthetic-only intake failure/restore tests; never contact deployed services."""

import contextlib
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import http.client
import importlib.util
import io
import ipaddress
from pathlib import Path
import sqlite3
import tempfile
from threading import Thread
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = importlib.util.spec_from_file_location(
        name, ROOT / "website/interest-api" / (name + ".py")
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


server, backup = load("server"), load("backup")


class IntakeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        server.DB_PATH = str(self.root / "intake.sqlite3")
        server.last_attempt.clear()
        server.initialize()
        self.http = server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.worker = Thread(target=self.http.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.worker.join()
        self.temp.cleanup()

    def post(self, body, content_type="application/x-www-form-urlencoded"):
        server.last_attempt.clear()
        client = http.client.HTTPConnection("127.0.0.1", self.http.server_port, timeout=3)
        client.request(
            "POST", "/interest", body, {"Origin": server.SITE_ORIGIN, "Content-Type": content_type}
        )
        response = client.getresponse()
        result = response.status, response.read()
        client.close()
        return result

    def test_current_consent_and_historical_scope_survive_repeat_signup(self):
        self.assertEqual(self.post(b"email=person%40example.invalid")[0], 200)
        self.assertEqual(
            self.post(b"email=person%40example.invalid&consent_version=project-updates-v1")[0], 200
        )
        with server.database() as db:
            self.assertEqual(
                db.execute("SELECT consent_text FROM interest").fetchone()[0],
                server.CONSENTS["legacy-preorder-v1"],
            )
            self.assertEqual(
                db.execute("SELECT version FROM consent_event ORDER BY id").fetchall(),
                [("legacy-preorder-v1",), ("project-updates-v1",)],
            )
        self.assertEqual(
            self.post(b"email=new%40example.invalid&consent_version=project-updates-v1")[0], 200
        )
        with server.database() as db:
            self.assertEqual(
                db.execute(
                    "SELECT consent_text FROM interest WHERE email=?", ("new@example.invalid",)
                ).fetchone()[0],
                server.CONSENTS["project-updates-v1"],
            )
        self.assertEqual(self.post(b"email=a%40example.invalid&consent_version=unreviewed")[0], 400)

    def test_form_and_server_share_the_reviewed_consent_contract(self):
        html = (ROOT / "website/index.html").read_text()
        self.assertIn(server.CONSENTS["project-updates-v1"], html)
        self.assertIn('name="consent_version" value="project-updates-v1"', html)

    def test_additive_migration_preserves_original_consent(self):
        Path(server.DB_PATH).unlink()
        with server.database(create=True) as db:
            db.execute(
                "CREATE TABLE interest(email TEXT PRIMARY KEY, created_at TEXT NOT NULL, consent_text TEXT NOT NULL)"
            )
            db.execute(
                "INSERT INTO interest VALUES ('fixture@example.invalid','2000-01-01','original narrow purpose')"
            )
        server.initialize()
        with server.database() as db:
            self.assertEqual(
                db.execute("SELECT created_at, consent_text FROM interest").fetchone(),
                ("2000-01-01", "original narrow purpose"),
            )
            self.assertEqual(db.execute("SELECT count(*) FROM consent_event").fetchone()[0], 0)
        server.ready()

    def test_malformed_requests_are_controlled_and_followed_by_success(self):
        for data, kind in [
            (b"email=\xff", "application/x-www-form-urlencoded"),
            (b"garbage", "multipart/form-data; boundary=absent"),
            (b"email=a&email=b", "application/x-www-form-urlencoded"),
        ]:
            with self.subTest(data=data), contextlib.redirect_stdout(io.StringIO()) as log:
                self.assertEqual(self.post(data, kind)[0], 400)
                self.assertNotIn("Traceback", log.getvalue())
                self.assertNotIn("email=", log.getvalue())
        self.assertEqual(self.post(b"email=a%40example.invalid")[0], 200)

    def test_unavailable_store_fails_readiness_and_submission_without_personal_logs(self):
        Path(server.DB_PATH).unlink()
        with contextlib.redirect_stdout(io.StringIO()) as log:
            client = http.client.HTTPConnection("127.0.0.1", self.http.server_port, timeout=3)
            client.request("GET", "/health")
            reply = client.getresponse()
            self.assertEqual(reply.status, 503)
            reply.read()
            client.close()
            self.assertEqual(self.post(b"email=private%40example.invalid")[0], 503)
            self.assertIn("SQLITE_CANTOPEN", log.getvalue())
            self.assertNotIn("private", log.getvalue())
        self.assertFalse(Path(server.DB_PATH).exists())

    def test_trusted_proxy_walk_ignores_spoofed_left_values(self):
        previous = server.TRUSTED_PROXIES
        try:
            server.TRUSTED_PROXIES = (
                ipaddress.ip_network("10.2.0.1/32"),
                ipaddress.ip_network("10.2.0.2/32"),
            )
            self.assertEqual(
                server.effective_address("10.2.0.1", "1.1.1.1, 198.51.100.7, 10.2.0.2"),
                "198.51.100.7",
            )
            self.assertEqual(
                server.effective_address("10.2.0.1", "2.2.2.2, 198.51.100.7, 10.2.0.2"),
                "198.51.100.7",
            )
            self.assertEqual(server.effective_address("198.51.100.7", "1.1.1.1"), "198.51.100.7")
            address = server.effective_address("10.2.0.1", "1.1.1.1, 198.51.100.7, 10.2.0.2")
            self.assertTrue(server.admitted(address, 10))
            self.assertFalse(server.admitted(address, 11))
            self.assertTrue(server.admitted(address, 12))
        finally:
            server.TRUSTED_PROXIES = previous

    def test_actual_http_throttle_cannot_be_reset_with_an_untrusted_header(self):
        for index, expected in enumerate((200, 429)):
            client = http.client.HTTPConnection("127.0.0.1", self.http.server_port, timeout=3)
            client.request(
                "POST",
                "/interest",
                b"email=throttle%40example.invalid",
                {
                    "Origin": server.SITE_ORIGIN,
                    "Content-Type": "application/x-www-form-urlencoded",
                    "X-Forwarded-For": f"198.51.100.{index + 1}",
                },
            )
            response = client.getresponse()
            self.assertEqual(response.status, expected)
            response.read()
            client.close()

    def test_multipart_file_parts_and_invalid_boundaries_fail(self):
        body = b'--x\r\nContent-Disposition: form-data; name="email"; filename="payload"\r\n\r\nfixture@example.invalid\r\n--x--\r\n'
        self.assertEqual(self.post(body, "multipart/form-data; boundary=x")[0], 400)
        self.assertEqual(self.post(body, "multipart/form-data")[0], 400)
        encoded = (
            b'--x\r\nContent-Disposition: form-data; name="email"\r\n'
            b"Content-Transfer-Encoding: base64\r\n\r\n"
            + base64.b64encode(b"encoded@example.invalid")
            + b"!\r\n--x--\r\n"
        )
        self.assertEqual(self.post(encoded, "multipart/form-data; boundary=x")[0], 400)

    def test_write_lock_is_diagnosed_without_leaking_input(self):
        with contextlib.closing(sqlite3.connect(server.DB_PATH, timeout=1)) as blocker:
            blocker.execute("BEGIN IMMEDIATE")
            with contextlib.redirect_stdout(io.StringIO()) as log:
                self.assertEqual(self.post(b"email=secret%40example.invalid")[0], 503)
                self.assertIn("SQLITE_BUSY", log.getvalue())
                self.assertNotIn("secret", log.getvalue())
            blocker.rollback()

    def test_failed_snapshot_validation_leaves_no_partial_and_does_not_prune(self):
        targets = self.root / "backups"
        targets.mkdir()
        old = targets / "interest-old.sqlite3"
        old.write_bytes(b"retain")
        with patch.object(backup, "validate", side_effect=[None, ValueError("snapshot invalid")]):
            with self.assertRaises(ValueError):
                backup.backup(
                    server.DB_PATH, targets, datetime.now(timezone.utc) + timedelta(days=31)
                )
        self.assertEqual(list(targets.iterdir()), [old])
        self.assertEqual(old.read_bytes(), b"retain")

    def test_backup_missing_corrupt_or_wrong_schema_never_prunes(self):
        targets = self.root / "backups"
        targets.mkdir()
        sentinel = targets / "interest-old.sqlite3"
        sentinel.write_bytes(b"keep")
        before = hashlib.sha256(sentinel.read_bytes()).hexdigest()
        for source in (
            self.root / "missing.sqlite3",
            self.root / "corrupt.sqlite3",
            self.root / "empty.sqlite3",
        ):
            if "corrupt" in source.name:
                source.write_bytes(b"not sqlite")
            if "empty" in source.name:
                sqlite3.connect(source).close()
            with self.assertRaises((sqlite3.Error, ValueError)):
                backup.backup(source, targets)
            self.assertEqual(hashlib.sha256(sentinel.read_bytes()).hexdigest(), before)
            self.assertEqual(list(targets.iterdir()), [sentinel])
        self.assertFalse((self.root / "missing.sqlite3").exists())

    def test_backup_restores_all_records_and_prunes_only_after_validation(self):
        self.post(b"email=fixture%40example.invalid&consent_version=project-updates-v1")
        with server.database() as db:
            db.execute(
                "INSERT INTO custom_request(email, details) VALUES (?, ?)",
                ("fixture@example.invalid", "synthetic project request"),
            )
        targets = self.root / "backups"
        targets.mkdir()
        old = targets / "interest-old.sqlite3"
        old.write_bytes(b"old")
        now = datetime.now(timezone.utc) + timedelta(days=31)
        result = backup.backup(server.DB_PATH, targets, now)
        self.assertFalse(old.exists())
        self.assertEqual(result.stat().st_mode & 0o777, 0o600)
        with (
            contextlib.closing(sqlite3.connect(result)) as restored,
            sqlite3.connect(server.DB_PATH) as original,
        ):
            for table in ("interest", "custom_request", "consent_event"):
                self.assertEqual(
                    restored.execute(f"SELECT * FROM {table}").fetchall(),
                    original.execute(f"SELECT * FROM {table}").fetchall(),
                )
        self.assertEqual(list(targets.iterdir()), [result])

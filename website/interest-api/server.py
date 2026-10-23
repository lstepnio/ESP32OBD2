"""Bounded same-origin intake. Log outcomes, never contact details or request bodies."""

from contextlib import contextmanager
import ipaddress
import json
import os
import re
import sqlite3
import time
from email.parser import BytesParser
from email.policy import default
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock
from urllib.parse import parse_qs

DB_PATH = os.environ.get("INTEREST_DB", "/data/interest.sqlite3")
SITE_ORIGIN = os.environ.get("SITE_ORIGIN", "https://egauge.majjix.com")
TRUSTED_PROXIES = tuple(
    ipaddress.ip_network(x.strip())
    for x in os.environ.get("TRUSTED_PROXY_CIDRS", "").split(",")
    if x.strip()
)
EMAIL = re.compile(r"^[^\s@<>]{1,64}@[^\s@<>]{1,189}$")
CONSENTS = {
    "legacy-preorder-v1": "Email me about eGauge preorder availability and selected options. No payment or reservation is taken.",
    "project-updates-v1": "We’ll email you about eGauge development and new vehicle profiles. You can request removal at any time. Signing up creates no order or reservation.",
}
last_attempt = {}
lock = Lock()


def event(name, **fields):
    print(json.dumps({"event": name, **fields}, sort_keys=True), flush=True)


@contextmanager
def database(create=False):
    uri = Path(DB_PATH).resolve().as_uri() + ("?mode=rwc" if create else "?mode=rw")
    connection = sqlite3.connect(uri, uri=True, timeout=2)
    try:
        with connection:
            yield connection
    finally:
        connection.close()


def initialize():
    with database(create=True) as db:
        db.execute(
            "CREATE TABLE IF NOT EXISTS interest (email TEXT PRIMARY KEY COLLATE NOCASE, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, consent_text TEXT NOT NULL, deposit_interest INTEGER NOT NULL DEFAULT 0, vip_interest INTEGER NOT NULL DEFAULT 0, roadmap_idea TEXT NOT NULL DEFAULT '')"
        )
        columns = {row[1] for row in db.execute("PRAGMA table_info(interest)")}
        for column in ("deposit_interest", "vip_interest"):
            if column not in columns:
                db.execute(f"ALTER TABLE interest ADD COLUMN {column} INTEGER NOT NULL DEFAULT 0")
        if "roadmap_idea" not in columns:
            db.execute("ALTER TABLE interest ADD COLUMN roadmap_idea TEXT NOT NULL DEFAULT ''")
        db.execute(
            "CREATE TABLE IF NOT EXISTS custom_request (id INTEGER PRIMARY KEY, email TEXT NOT NULL, details TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, status TEXT NOT NULL DEFAULT 'new')"
        )
        db.execute(
            "CREATE TABLE IF NOT EXISTS consent_event (id INTEGER PRIMARY KEY, email TEXT NOT NULL, version TEXT NOT NULL, consent_text TEXT NOT NULL, accepted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        )


def effective_address(peer, forwarded):
    """Walk from the socket peer inward. Untrusted left-hand header values never win."""
    address = ipaddress.ip_address(peer)
    chain = forwarded.split(",") if forwarded else []
    if len(chain) > 16:
        return str(address)
    for candidate in reversed(chain):
        if not any(address in network for network in TRUSTED_PROXIES):
            break
        try:
            address = ipaddress.ip_address(candidate.strip())
        except ValueError:
            break
    return str(address)


def admitted(address, now):
    with lock:
        for key in list(last_attempt):
            if now - last_attempt[key] >= 2:
                del last_attempt[key]
        if address in last_attempt or len(last_attempt) >= 5000:
            return False
        last_attempt[address] = now
        return True


def parse_fields(content_type, body):
    if content_type.startswith("application/x-www-form-urlencoded"):
        parsed = parse_qs(
            body.decode("utf-8"), keep_blank_values=True, max_num_fields=16, errors="strict"
        )
        if any(len(values) != 1 for values in parsed.values()):
            raise ValueError("Duplicate form fields")
        return {key: values[0] for key, values in parsed.items()}
    message = BytesParser(policy=default).parsebytes(
        b"Content-Type: " + content_type.encode("ascii") + b"\r\nMIME-Version: 1.0\r\n\r\n" + body
    )
    if not message.is_multipart() or message.defects:
        raise ValueError("Malformed multipart form")
    fields = {}
    for part in message.iter_parts():
        name = part.get_param("name", header="content-disposition")
        if (
            not name
            or name in fields
            or len(fields) >= 16
            or part.get_filename() is not None
            or part.is_multipart()
            or part.defects
        ):
            raise ValueError("Invalid form field")
        fields[name] = part.get_payload(decode=True).decode("utf-8")
    return fields


def ready():
    # Acquire a write reservation and validate schema without changing any row.
    with database() as db:
        db.execute("BEGIN IMMEDIATE")
        db.execute("UPDATE interest SET email=email WHERE 0")
        db.execute("SELECT details FROM custom_request LIMIT 0")
        db.execute("SELECT version, consent_text FROM consent_event LIMIT 0")
        db.rollback()


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    def log_message(self, *_):
        pass

    def respond(self, code, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path not in ("/health", "/ready"):
            return self.respond(404, {"error": "Not found"})
        try:
            ready()
        except sqlite3.Error as error:
            event(
                "intake_readiness",
                outcome="unavailable",
                reason=getattr(error, "sqlite_errorname", "SQLITE_ERROR"),
            )
            return self.respond(503, {"ok": False})
        self.respond(200, {"ok": True})

    def do_POST(self):
        if self.path not in ("/interest", "/custom"):
            return self.respond(404, {"error": "Not found"})
        if self.headers.get("Origin") != SITE_ORIGIN:
            return self.respond(403, {"error": "Open the eGauge website to sign up."})
        kind = self.headers.get_content_type()
        if kind not in ("application/x-www-form-urlencoded", "multipart/form-data"):
            return self.respond(415, {"error": "Unsupported form format."})
        length = self.headers.get("Content-Length", "")
        if not length.isdigit() or not 0 < int(length) <= 16384:
            return self.respond(413, {"error": "Form is too large."})
        address = effective_address(self.client_address[0], self.headers.get("X-Forwarded-For", ""))
        if not admitted(address, time.monotonic()):
            return self.respond(429, {"error": "Please wait a moment and try again."})
        try:
            body = self.rfile.read(int(length))
            if len(body) != int(length):
                raise ValueError("Incomplete form")
            fields = parse_fields(self.headers.get("Content-Type", ""), body)
        except (ValueError, UnicodeError, TimeoutError):
            event("intake_rejected", reason="malformed_form")
            return self.respond(
                400, {"error": "The form could not be read. Please reload and try again."}
            )
        if fields.get("website"):
            return self.respond(200, {"ok": True})
        email = fields.get("email", "").strip().lower()
        if len(email) > 254 or not EMAIL.fullmatch(email) or ".." in email:
            return self.respond(400, {"error": "Enter a valid email address."})
        version = fields.get("consent_version", "legacy-preorder-v1")
        if version not in CONSENTS:
            return self.respond(400, {"error": "Reload the form to review the signup details."})
        idea, details = fields.get("roadmap_idea", "").strip(), fields.get("details", "").strip()
        if self.path == "/interest" and len(idea) > 500:
            return self.respond(
                400, {"error": "Please keep the roadmap idea under 500 characters."}
            )
        if self.path == "/custom" and not 20 <= len(details) <= 3000:
            return self.respond(
                400, {"error": "Please describe your project in 20 to 3000 characters."}
            )
        started = time.monotonic()
        try:
            with database() as db:
                if self.path == "/interest":
                    db.execute(
                        "INSERT INTO interest(email, consent_text, deposit_interest, vip_interest, roadmap_idea) VALUES (?, ?, ?, ?, ?) ON CONFLICT(email) DO UPDATE SET deposit_interest=max(deposit_interest,excluded.deposit_interest), vip_interest=max(vip_interest,excluded.vip_interest), roadmap_idea=CASE WHEN excluded.roadmap_idea!='' THEN excluded.roadmap_idea ELSE interest.roadmap_idea END",
                        (
                            email,
                            CONSENTS[version],
                            int(fields.get("deposit_interest") == "yes"),
                            int(fields.get("vip_interest") == "yes"),
                            idea,
                        ),
                    )
                    db.execute(
                        "INSERT INTO consent_event(email, version, consent_text) VALUES (?, ?, ?)",
                        (email, version, CONSENTS[version]),
                    )
                else:
                    db.execute(
                        "INSERT INTO custom_request(email, details) VALUES (?, ?)", (email, details)
                    )
        except sqlite3.Error as error:
            event(
                "intake_write",
                operation=self.path[1:],
                outcome="failed",
                reason=getattr(error, "sqlite_errorname", "SQLITE_ERROR"),
            )
            return self.respond(
                503, {"error": "Unable to confirm your submission. Please try again later."}
            )
        event(
            "intake_write",
            operation=self.path[1:],
            outcome="saved",
            duration_ms=round((time.monotonic() - started) * 1000),
        )
        self.respond(200, {"ok": True})


if __name__ == "__main__":
    initialize()
    ThreadingHTTPServer(("0.0.0.0", 8081), Handler).serve_forever()

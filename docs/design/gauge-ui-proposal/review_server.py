#!/usr/bin/env python3
"""Local-only review gallery and durable owner feedback. No device operations."""

import argparse
from datetime import datetime, timezone
from hashlib import sha256
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import tempfile
from threading import RLock
from urllib.parse import unquote, urlsplit

UI_DIR = Path(__file__).resolve().parent
ROOT = UI_DIR.parents[2]
REVIEW_ID = "gauge-ui-2026-10-14"
STATUSES = {"unreviewed", "approved", "changes_requested"}


class ReviewConflict(ValueError):
    pass


class ReviewStore:
    def __init__(self, storage=None):
        manifest_bytes = (UI_DIR / "mock-manifest.json").read_bytes()
        self.manifest = json.loads(manifest_bytes)
        self.review_id = self.manifest.get("reviewId", REVIEW_ID)
        round_number = self.manifest.get("reviewRound", 1)
        suffix = f"-v{round_number}" if round_number > 1 else ""
        self.storage = (
            Path(storage)
            if storage
            else ROOT / f"artifacts/gauge-ui-review/2026-10-14-review{suffix}.json"
        )
        image_hashes = b"".join(
            sha256((UI_DIR / (s["id"] + ".png")).read_bytes()).digest()
            for s in self.manifest["screens"]
        )
        self.manifest_hash = sha256(manifest_bytes + image_hashes).hexdigest()
        self.ids = {s["id"] for s in self.manifest["screens"]}
        self.lock = RLock()

    def load(self):
        if not self.storage.exists():
            return {
                "reviewId": self.review_id,
                "manifestSha256": self.manifest_hash,
                "revision": 0,
                "screens": {},
                "submittedAt": None,
            }
        data = json.loads(self.storage.read_text())
        if data["reviewId"] != self.review_id or data["manifestSha256"] != self.manifest_hash:
            raise ValueError(
                "The examples changed. Existing feedback is preserved; a new review needs a separate file."
            )
        return data

    def entry(self, raw):
        if not isinstance(raw, dict):
            raise ValueError("Screen feedback must be an object.")
        key = raw.get("id")
        status = raw.get("status")
        note = raw.get("feedback", "")
        if (
            key not in self.ids
            or status not in STATUSES
            or not isinstance(note, str)
            or len(note) > 4000
        ):
            raise ValueError("Invalid screen, decision or feedback length.")
        return key, {"status": status, "feedback": note}

    def save(self, payload):
        if (
            payload.get("reviewId") != self.review_id
            or payload.get("manifestSha256") != self.manifest_hash
        ):
            raise ReviewConflict(
                "The examples changed. Refresh the gallery; your previous review is preserved."
            )
        action = payload.get("action")
        if action == "save":
            entries = [payload.get("screen", {})]
        elif action == "submit":
            entries = payload.get("screens")
            if not isinstance(entries, list) or len(entries) != len(self.ids):
                raise ValueError("Submit must include each example once; unreviewed is allowed.")
        else:
            raise ValueError("Unknown review action.")
        validated = dict(self.entry(raw) for raw in entries)
        if len(validated) != len(entries):
            raise ValueError("Duplicate screen decision.")
        with self.lock:
            current = self.load()
            current["screens"].update(validated)
            now = datetime.now(timezone.utc).isoformat()
            current["updatedAt"] = now
            current["revision"] += 1
            if action == "submit":
                current["submittedAt"] = now
                current["submittedRevision"] = current["revision"]
                # A retained snapshot distinguishes submitted decisions from later edits.
                current["submittedScreens"] = dict(current["screens"])
            self.write(current)
            return current

    def write(self, current):
        self.storage.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        fd, name = tempfile.mkstemp(dir=self.storage.parent, prefix=".review-", suffix=".json")
        try:
            with os.fdopen(fd, "w") as out:
                json.dump(current, out, indent=2, ensure_ascii=False)
                out.write("\n")
                out.flush()
                os.fsync(out.fileno())
            os.replace(name, self.storage)
        finally:
            if Path(name).exists():
                Path(name).unlink()

    def seed_from(self, previous_file, previous_assets):
        """Carry only human-approved, byte-identical images into a new review."""
        with self.lock:
            if self.storage.exists():
                raise ReviewConflict("New review already exists; preserve it.")
            old = json.loads(Path(previous_file).read_text())
            assets = Path(previous_assets)
            raw = (assets / "mock-manifest.json").read_bytes()
            old_manifest = json.loads(raw)
            old_hash = sha256(
                raw
                + b"".join(
                    sha256((assets / (s["id"] + ".png")).read_bytes()).digest()
                    for s in old_manifest["screens"]
                )
            ).hexdigest()
            if old_hash != old.get("manifestSha256"):
                raise ReviewConflict("The prior review does not match its original images.")
            # A new round may start before Submit review. Saved per-screen decisions
            # remain authoritative; this never turns unreviewed images into approvals.
            prior = old.get("screens", old.get("submittedScreens", {}))
            if not isinstance(prior, dict):
                raise ReviewConflict("The prior decisions are invalid.")
            for key, entry in prior.items():
                self.entry({"id": key, **entry})
            current = self.load()
            current["priorReview"] = {
                key: old[key]
                for key in (
                    "reviewId",
                    "revision",
                    "submittedRevision",
                    "submittedAt",
                    "manifestSha256",
                )
                if key in old
            }
            current["priorScreens"] = prior
            current["earlierFeedback"] = old.get("earlierFeedback", old.get("priorScreens", {}))
            current["carriedApprovals"] = []
            for screen in self.manifest["screens"]:
                key = screen["id"]
                decision = current["priorScreens"].get(key, {})
                identical = (assets / (key + ".png")).exists() and (
                    assets / (key + ".png")
                ).read_bytes() == (UI_DIR / (key + ".png")).read_bytes()
                carried = identical and decision.get("status") == "approved"
                current["screens"][key] = {
                    "status": "approved" if carried else "unreviewed",
                    "feedback": "",
                }
                if carried:
                    current["carriedApprovals"].append(key)
            self.write(current)
            return current


class ReviewHandler(SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        # Do not log feedback contents or personal data.
        pass

    def allowed_request(self):
        port = self.server.server_address[1]
        allowed = {f"127.0.0.1:{port}", f"localhost:{port}"}
        if self.headers.get("Host") not in allowed:
            return False
        origin = self.headers.get("Origin")
        return origin is None or origin in {f"http://{host}" for host in allowed}

    def respond_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

    def do_GET(self):
        if not self.allowed_request():
            return self.respond_json({"error": "Local review origin required"}, 403)
        if urlsplit(self.path).path == "/api/gauge-review":
            try:
                with self.server.store.lock:
                    data = self.server.store.load()
                return self.respond_json(data)
            except (ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
                return self.respond_json({"error": str(error)}, 409)
        path = self.translate_path(self.path)
        if path is None:
            return self.send_error(404)
        super().do_GET()

    def translate_path(self, path):
        path = unquote(urlsplit(path).path)
        if path == "/docs/design/gauge-ui-proposal.md":
            return str(UI_DIR.parent / "gauge-ui-proposal.md")
        prefix = "/docs/design/gauge-ui-proposal/"
        if not path.startswith(prefix):
            return None
        tail = path[len(prefix) :] or "index.html"
        candidate = (UI_DIR / tail).resolve()
        if not candidate.is_relative_to(UI_DIR) or candidate.suffix not in {
            ".html",
            ".png",
            ".json",
            ".js",
        }:
            return None
        return str(candidate)

    def do_POST(self):
        if not self.allowed_request():
            return self.respond_json({"error": "Local review origin required"}, 403)
        if urlsplit(self.path).path != "/api/gauge-review":
            return self.respond_json({"error": "Unknown route"}, 404)
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
            return self.respond_json({"error": "JSON required"}, 415)
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 256 * 1024:
                raise ValueError("Review payload too large or empty.")
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict):
                raise ValueError("Review object required.")
            data = self.server.store.save(payload)
            self.respond_json(data)
        except ReviewConflict as error:
            self.respond_json({"error": str(error)}, 409)
        except (ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
            self.respond_json({"error": str(error)}, 400)
        except OSError:
            self.respond_json(
                {"error": "Could not save on this Mac. Your browser draft is retained."}, 500
            )


def build_server(port=8769, storage=None):
    server = ThreadingHTTPServer(("127.0.0.1", port), ReviewHandler)
    server.store = ReviewStore(storage)
    return server


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8769)
    args = parser.parse_args()
    server = build_server(args.port)
    print(
        f"Local gauge review: http://127.0.0.1:{args.port}/docs/design/gauge-ui-proposal/index.html",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

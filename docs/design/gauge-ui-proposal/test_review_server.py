"""HTTP checks use temporary feedback storage, never the owner's review."""

import json
from pathlib import Path
import tempfile
from threading import Thread
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from review_server import build_server, UI_DIR


class ReviewHttpTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.storage = Path(self.temp.name) / "feedback.json"
        self.server = build_server(0, self.storage)
        self.worker = Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}/api/gauge-review"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()
        self.temp.cleanup()

    def call(self, payload=None, origin=None):
        headers = {"Content-Type": "application/json"}
        if payload is not None:
            payload.setdefault("reviewId", self.server.store.review_id)
            payload.setdefault("manifestSha256", self.server.store.manifest_hash)
        if origin:
            headers["Origin"] = origin
        request = Request(
            self.url,
            data=json.dumps(payload).encode() if payload is not None else None,
            headers=headers,
        )
        with urlopen(request) as response:
            return json.load(response)

    def test_individual_save_reload_and_resubmit(self):
        self.assertEqual(self.call()["revision"], 0)
        saved = self.call(
            {
                "action": "save",
                "screen": {
                    "id": "dual",
                    "status": "changes_requested",
                    "feedback": "Make RPM larger",
                },
            }
        )
        self.assertEqual(saved["screens"]["dual"]["feedback"], "Make RPM larger")
        self.assertEqual(self.call()["screens"], saved["screens"])
        saved = self.call(
            {"action": "save", "screen": {"id": "dual", "status": "approved", "feedback": ""}}
        )
        self.assertEqual(saved["revision"], 2)
        self.assertEqual(self.storage.stat().st_mode & 0o777, 0o600)

    def test_partial_review_preserves_unreviewed_and_submission_snapshot(self):
        entries = [
            {"id": key, "status": "approved" if key == "dual" else "unreviewed", "feedback": ""}
            for key in self.server.store.ids
        ]
        submitted = self.call({"action": "submit", "screens": entries})
        self.assertIsNotNone(submitted["submittedAt"])
        self.assertEqual(len(submitted["submittedScreens"]), len(self.server.store.ids))
        changed = self.call(
            {
                "action": "save",
                "screen": {
                    "id": "dual",
                    "status": "changes_requested",
                    "feedback": "Different font",
                },
            }
        )
        self.assertEqual(changed["submittedScreens"]["dual"]["status"], "approved")
        self.assertEqual(changed["screens"]["dual"]["status"], "changes_requested")

    def test_bad_ids_and_cross_origin_do_not_write(self):
        for payload, origin, code in [
            ({"action": "save", "screen": {"id": "unknown", "status": "approved"}}, None, 400),
            (
                {"action": "save", "screen": {"id": "dual", "status": "approved"}},
                "https://example.com",
                403,
            ),
        ]:
            with self.assertRaises(HTTPError) as error:
                self.call(payload, origin)
            self.assertEqual(error.exception.code, code)
            error.exception.close()
        self.assertFalse(self.storage.exists())

    def test_old_page_cannot_approve_new_images(self):
        with self.assertRaises(HTTPError) as error:
            self.call(
                {
                    "action": "save",
                    "reviewId": "old-review",
                    "screen": {"id": "dual", "status": "approved", "feedback": ""},
                }
            )
        self.assertEqual(error.exception.code, 409)
        error.exception.close()
        self.assertFalse(self.storage.exists())

    def test_seed_retains_only_identical_approved_images(self):
        from hashlib import sha256

        assets = UI_DIR / "v2"
        raw = (assets / "mock-manifest.json").read_bytes()
        manifest = json.loads(raw)
        digest = sha256(
            raw
            + b"".join(
                sha256((assets / (s["id"] + ".png")).read_bytes()).digest()
                for s in manifest["screens"]
            )
        ).hexdigest()
        prior = {
            "reviewId": "previous-review",
            "manifestSha256": digest,
            "submittedAt": "2026-10-14T00:00:00+00:00",
            "submittedRevision": 3,
            "submittedScreens": {
                s["id"]: {"status": "approved", "feedback": ""} for s in manifest["screens"]
            },
        }
        original = Path(self.temp.name) / "original.json"
        original.write_text(json.dumps(prior))
        before = original.read_bytes()
        current = self.server.store.seed_from(original, assets)
        self.assertEqual(current["screens"]["numeric"]["status"], "approved")
        self.assertEqual(current["screens"]["threshold-mil"]["status"], "unreviewed")
        self.assertEqual(current["screens"]["numeric-alert-mil"]["status"], "unreviewed")
        self.assertIsNone(current["submittedAt"])
        self.assertEqual(current["priorScreens"]["dual"]["status"], "approved")
        self.assertEqual(before, original.read_bytes())
        with self.assertRaises(ValueError):
            self.server.store.seed_from(original, assets)

    def test_saved_unsubmitted_round_preserves_comments_and_unreviewed(self):
        from hashlib import sha256

        assets = UI_DIR / "v2"
        raw = (assets / "mock-manifest.json").read_bytes()
        manifest = json.loads(raw)
        digest = sha256(
            raw
            + b"".join(
                sha256((assets / (s["id"] + ".png")).read_bytes()).digest()
                for s in manifest["screens"]
            )
        ).hexdigest()
        prior = {
            "reviewId": "previous-review",
            "manifestSha256": digest,
            "submittedAt": None,
            "revision": 2,
            "screens": {
                "numeric": {"status": "approved", "feedback": ""},
                "dual": {"status": "unreviewed", "feedback": "Keep comparing"},
            },
            "priorScreens": {
                "threshold-mil": {"status": "changes_requested", "feedback": "Earlier note"}
            },
        }
        original = Path(self.temp.name) / "saved.json"
        original.write_text(json.dumps(prior))
        before = original.read_bytes()
        current = self.server.store.seed_from(original, assets)
        self.assertEqual(current["screens"]["numeric"]["status"], "approved")
        self.assertEqual(current["screens"]["dual"]["status"], "unreviewed")
        self.assertIsNone(current["submittedAt"])
        self.assertIsNone(current["priorReview"]["submittedAt"])
        self.assertEqual(current["priorScreens"]["dual"]["feedback"], "Keep comparing")
        self.assertEqual(current["earlierFeedback"]["threshold-mil"]["feedback"], "Earlier note")
        self.assertEqual(before, original.read_bytes())

    def test_changed_manifest_preserves_feedback(self):
        self.call(
            {"action": "save", "screen": {"id": "dual", "status": "approved", "feedback": "Keep"}}
        )
        before = self.storage.read_bytes()
        self.server.store.manifest_hash = "changed"
        with self.assertRaises(HTTPError) as error:
            self.call()
        self.assertEqual(error.exception.code, 409)
        error.exception.close()
        self.assertEqual(self.storage.read_bytes(), before)


class ActionReviewTest(unittest.TestCase):
    def test_independent_namespace_and_bound_interaction_assets(self):
        from review_server import ReviewStore, ReviewConflict
        import shutil

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            assets = root / "action-pages"
            shutil.copytree(UI_DIR / "action-pages", assets)
            shutil.copyfile(UI_DIR / "gallery.js", root / "gallery.js")
            old = ReviewStore(root / "old.json")
            old.save(
                {
                    "reviewId": old.review_id,
                    "manifestSha256": old.manifest_hash,
                    "action": "save",
                    "screen": {"id": "dual", "status": "approved"},
                }
            )
            before = old.storage.read_bytes()
            new = ReviewStore(root / "new.json", assets)
            self.assertNotEqual(old.review_id, new.review_id)
            payload = {
                "reviewId": new.review_id,
                "manifestSha256": new.manifest_hash,
                "action": "save",
                "screen": {
                    "id": "air-ready",
                    "status": "changes_requested",
                    "feedback": "Make Stop easier to reach",
                },
            }
            new.save(payload)
            self.assertEqual(
                new.load()["screens"]["air-ready"]["feedback"], "Make Stop easier to reach"
            )
            self.assertEqual(old.storage.read_bytes(), before)
            (assets / "prototype.js").write_text("// revised flow\n")
            with self.assertRaises(ReviewConflict):
                new.save(payload)
            revised = ReviewStore(root / "new.json", assets)
            with self.assertRaises(ReviewConflict):
                revised.save(payload)
            with self.assertRaises(ValueError):
                revised.load()

    def test_selected_gallery_routes_and_submission(self):
        with tempfile.TemporaryDirectory() as folder:
            server = build_server(0, Path(folder) / "review.json", UI_DIR / "action-pages")
            worker = Thread(target=server.serve_forever, daemon=True)
            worker.start()
            base = f"http://127.0.0.1:{server.server_address[1]}"
            try:
                for path in [
                    "action-pages/index.html",
                    "action-pages/active.png",
                    "action-pages/proposal.html",
                    "gallery.js",
                ]:
                    with urlopen(base + "/docs/design/gauge-ui-proposal/" + path) as response:
                        self.assertEqual(response.status, 200)
                for path in ["index.html", "../AGENTS.md", "action-pages/generate.py"]:
                    with self.assertRaises(HTTPError) as error:
                        urlopen(base + "/docs/design/gauge-ui-proposal/" + path)
                    error.exception.close()
                store = server.store
                data = store.save(
                    {
                        "reviewId": store.review_id,
                        "manifestSha256": store.manifest_hash,
                        "action": "submit",
                        "screens": [
                            {"id": key, "status": "unreviewed", "feedback": ""} for key in store.ids
                        ],
                    }
                )
                self.assertEqual(len(data["submittedScreens"]), len(store.ids))
                self.assertTrue(
                    all(v["status"] == "unreviewed" for v in data["submittedScreens"].values())
                )
            finally:
                server.shutdown()
                server.server_close()
                worker.join()


class ActionCarryTest(unittest.TestCase):
    def test_bound_assets_removed_examples_and_revised_notes(self):
        from review_server import ReviewStore, ReviewConflict
        import shutil

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old_assets, new_assets = root / "old", root / "new"
            for folder in (old_assets, new_assets):
                folder.mkdir()
                (folder / "flow.js").write_text("// original interaction\n")
                for key in ("air-ready", "abs-ready", "retired"):
                    shutil.copyfile(UI_DIR / "action-pages/air-ready.png", folder / (key + ".png"))
            common = {"reviewId": "test", "reviewAssets": ["flow.js"]}
            (old_assets / "mock-manifest.json").write_text(
                json.dumps(
                    {
                        **common,
                        "screens": [{"id": x} for x in ("air-ready", "abs-ready", "retired")],
                    }
                )
            )
            (new_assets / "mock-manifest.json").write_text(
                json.dumps(
                    {
                        **common,
                        "screens": [
                            {"id": "air-ready", "revised": False},
                            {"id": "abs-ready", "revised": True},
                        ],
                    }
                )
            )
            old = ReviewStore(root / "old.json", old_assets)
            old.save(
                {
                    "reviewId": old.review_id,
                    "manifestSha256": old.manifest_hash,
                    "action": "submit",
                    "screens": [
                        {"id": key, "status": "approved", "feedback": "Preserve my words"}
                        for key in old.ids
                    ],
                }
            )
            before = old.storage.read_bytes()
            new = ReviewStore(root / "new.json", new_assets)
            carried = new.seed_from(old.storage, old_assets)
            self.assertEqual(carried["carriedApprovals"], ["air-ready"])
            self.assertEqual(carried["screens"]["abs-ready"]["status"], "unreviewed")
            self.assertEqual(carried["priorScreens"]["retired"]["feedback"], "Preserve my words")
            self.assertEqual(old.storage.read_bytes(), before)
            (old_assets / "flow.js").write_text("// changed after approval\n")
            unseeded = ReviewStore(root / "unseeded.json", new_assets)
            with self.assertRaises(ReviewConflict):
                unseeded.seed_from(old.storage, old_assets)
            self.assertFalse(unseeded.storage.exists())

    def test_frozen_session_survives_source_edits(self):
        import review_server
        import shutil
        from unittest.mock import patch

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ui = root / "ui"
            ui.mkdir()
            shutil.copytree(UI_DIR / "action-pages", ui / "action-pages")
            shutil.copyfile(UI_DIR / "gallery.js", ui / "gallery.js")
            with (
                patch.object(review_server, "ROOT", root),
                patch.object(review_server, "UI_DIR", ui),
            ):
                frozen = review_server.freeze_action_review()
                store = review_server.ReviewStore(root / "review.json", frozen)
                original_hash = store.manifest_hash
                source = ui / "action-pages/prototype.js"
                source.write_text(source.read_text() + "\n// next design draft\n")
                self.assertEqual(store.asset_digest(), original_hash)
                next_snapshot = review_server.freeze_action_review()
                self.assertNotEqual(frozen, next_snapshot)
                self.assertEqual(store.asset_digest(), original_hash)


if __name__ == "__main__":
    unittest.main()

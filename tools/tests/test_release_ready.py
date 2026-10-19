"""Release gating must not confuse green runs on another SHA or an older attempt."""

import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_release_ready import require_success


class ReleaseReadyTest(unittest.TestCase):
    def run_record(self, **changes):
        return dict(
            head_sha="a" * 40,
            run_number=1,
            run_attempt=1,
            status="completed",
            conclusion="success",
            html_url="https://example.invalid/run",
            **changes,
        )

    def test_exact_sha_required(self):
        with self.assertRaises(ValueError):
            require_success([self.run_record()], "b" * 40)

    def test_latest_attempt_must_complete_successfully(self):
        good = self.run_record()
        for status, conclusion in (
            ("in_progress", None),
            ("completed", "failure"),
            ("completed", "cancelled"),
        ):
            retry = good | dict(run_attempt=2, status=status, conclusion=conclusion)
            with self.assertRaises(ValueError):
                require_success([good, retry], "a" * 40)
        self.assertEqual(require_success([good], "a" * 40), good["html_url"])

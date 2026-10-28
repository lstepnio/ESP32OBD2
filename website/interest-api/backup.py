"""Validate a private SQLite snapshot before publication or retention cleanup."""

from contextlib import closing
from datetime import datetime, timedelta, timezone
from pathlib import Path
import os
import sqlite3
import tempfile


def validate(db):
    if db.execute("PRAGMA quick_check").fetchone() != ("ok",):
        raise ValueError("SQLite integrity check failed")
    for table, column in (("interest", "consent_text"), ("custom_request", "details")):
        db.execute(f"SELECT {column} FROM {table} LIMIT 0")


def backup(source, target_dir, now=None):
    now = now or datetime.now(timezone.utc)
    source, target_dir = Path(source).resolve(), Path(target_dir)
    # mode=ro refuses missing sources and cannot create a misleading empty database.
    with closing(sqlite3.connect(source.as_uri() + "?mode=ro", uri=True, timeout=5)) as original:
        validate(original)
        target_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(target_dir, 0o700)
        target = target_dir / ("interest-" + now.strftime("%Y%m%dT%H%M%S%fZ") + ".sqlite3")
        fd, name = tempfile.mkstemp(prefix=".partial-", dir=target_dir)
        os.close(fd)
        temporary = Path(name)
        try:
            with closing(sqlite3.connect(temporary)) as snapshot:
                original.backup(snapshot)
                validate(snapshot)
            with temporary.open("rb") as stream:
                os.fsync(stream.fileno())
            temporary.replace(target)
            directory = os.open(target_dir, os.O_RDONLY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        finally:
            temporary.unlink(missing_ok=True)
    cutoff = now - timedelta(days=30)
    for old in target_dir.glob("interest-*.sqlite3"):
        if old != target and datetime.fromtimestamp(old.stat().st_mtime, timezone.utc) < cutoff:
            old.unlink()
    return target


if __name__ == "__main__":
    try:
        backup(
            os.environ.get("INTEREST_DB", "/docker/appdata/egauge/private/interest.sqlite3"),
            os.environ.get("INTEREST_BACKUPS", "/docker/backups/egauge-interest"),
        )
    except (sqlite3.Error, OSError, ValueError):
        print("intake_backup outcome=failed", flush=True)
        raise SystemExit(1)
    print("intake_backup outcome=verified", flush=True)

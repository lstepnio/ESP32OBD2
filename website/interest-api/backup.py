"""Consistent private SQLite backup for eGauge interest and project intake."""
from datetime import datetime, timedelta, timezone
from pathlib import Path
import os
import sqlite3

source = Path('/docker/appdata/egauge/private/interest.sqlite3')
target_dir = Path('/docker/backups/egauge-interest')
target_dir.mkdir(mode=0o700, exist_ok=True)
os.chmod(target_dir, 0o700)
stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
target = target_dir / f'interest-{stamp}.sqlite3'
with sqlite3.connect(source) as original, sqlite3.connect(target) as backup:
    original.backup(backup)
os.chmod(target, 0o600)
cutoff = datetime.now(timezone.utc) - timedelta(days=30)
for old in target_dir.glob('interest-*.sqlite3'):
    if datetime.fromtimestamp(old.stat().st_mtime, timezone.utc) < cutoff:
        old.unlink()

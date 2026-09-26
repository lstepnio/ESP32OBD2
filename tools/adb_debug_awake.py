#!/usr/bin/env python3
"""Keep a development phone awake for a session, then restore its settings."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


SETTINGS = (
    ("system", "screen_off_timeout", "1800000"),
    ("global", "stay_on_while_plugged_in", "7"),
)


def adb_path():
    explicit = os.environ.get("ADB")
    candidates = [explicit, shutil.which("adb")]
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if root:
            candidates.append(str(Path(root) / "platform-tools" / "adb"))
    candidates.append(str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
    return next((path for path in candidates if path and Path(path).is_file()), None)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("start", "stop", "status"))
    parser.add_argument("--serial", help="ADB device serial when more than one is connected")
    args = parser.parse_args()
    adb = adb_path()
    if not adb:
        parser.error("adb was not found; set ADB or ANDROID_HOME")
    command = [adb] + (["-s", args.serial] if args.serial else [])

    def run(*parts):
        return subprocess.check_output(command + list(parts), text=True).strip()

    serial = run("get-serialno")
    if serial == "unknown":
        parser.error("no authorized ADB device")
    state_dir = Path.home() / ".cache" / "egauge"
    key = hashlib.sha256(serial.encode()).hexdigest()[:16]
    state_file = state_dir / f"debug-awake-{key}.json"

    def read_settings():
        return {f"{scope}.{name}": run("shell", "settings", "get", scope, name)
                for scope, name, _ in SETTINGS}

    if args.action == "status":
        print(json.dumps({"device": serial, "active": state_file.exists(),
                          "settings": read_settings()}, indent=2))
        return

    if args.action == "start":
        state_dir.mkdir(parents=True, exist_ok=True)
        if not state_file.exists():
            state_file.write_text(json.dumps(read_settings()))
            state_file.chmod(0o600)
        for scope, name, value in SETTINGS:
            run("shell", "settings", "put", scope, name, value)
        print(f"Debug wake settings active for {serial}. Run stop to restore them.")
        return

    if not state_file.exists():
        parser.error("no saved settings for this device; nothing to restore")
    original = json.loads(state_file.read_text())
    for scope, name, _ in SETTINGS:
        value = original[f"{scope}.{name}"]
        if value == "null":
            run("shell", "settings", "delete", scope, name)
        else:
            run("shell", "settings", "put", scope, name, value)
    state_file.unlink()
    print(f"Restored original wake settings for {serial}.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f"adb command failed: {error}")

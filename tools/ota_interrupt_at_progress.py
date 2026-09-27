#!/usr/bin/env python3
"""Reset a USB-connected development gauge at an observed Android OTA percentage.

The operator starts a signed GitHub update in the companion app. This tool only
observes the visible percentage and resets the gauge through esptool when the
requested threshold is reached. It never selects or transfers a firmware file.
"""

import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


PERCENT = re.compile(r"([0-9]{1,3})%")


def run(command: list[str], timeout: float = 20, capture: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, timeout=timeout,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None)


def visible_progress(adb: str) -> int | None:
    run([adb, "shell", "uiautomator", "dump", "/sdcard/egauge-recovery-window.xml"])
    output = run([adb, "exec-out", "cat", "/sdcard/egauge-recovery-window.xml"]).stdout
    root = ET.fromstring(output)
    values = []
    for node in root.iter("node"):
        match = PERCENT.fullmatch(node.attrib.get("text", "").strip())
        if match:
            values.append(int(match.group(1)))
    return max(values) if values else None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
    parser.add_argument("--serial-port", required=True)
    parser.add_argument("--target-percent", type=int, required=True)
    parser.add_argument("--esptool-python", required=True,
                        help="Python interpreter whose environment contains esptool")
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--poll-seconds", type=float, default=1.0)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.target_percent not in range(1, 101):
        parser.error("--target-percent must be between 1 and 100")

    run([args.adb, "get-state"])
    started = time.monotonic()
    observations: list[dict[str, float | int]] = []
    reset_at = None
    while time.monotonic() - started < args.timeout_seconds:
        progress = visible_progress(args.adb)
        elapsed = round(time.monotonic() - started, 3)
        if progress is not None:
            observations.append({"elapsedSeconds": elapsed, "percent": progress})
            print(f"{elapsed:7.3f}s {progress:3d}%", flush=True)
            if progress >= 100 and args.target_percent < 100:
                raise SystemExit("Update reached 100% before the requested reset threshold")
            if progress >= args.target_percent:
                reset_at = progress
                break
        time.sleep(args.poll_seconds)
    if reset_at is None:
        raise SystemExit("Timed out before observing the requested update threshold")

    reset_started = time.monotonic()
    result = run([
        args.esptool_python, "-m", "esptool", "--chip", "esp32s3",
        "--port", args.serial_port, "--before", "default_reset",
        "--after", "hard_reset", "run",
    ], timeout=30)
    evidence = {
        "schemaVersion": 1,
        "targetPercent": args.target_percent,
        "observedResetPercent": reset_at,
        "resetElapsedSeconds": round(reset_started - started, 3),
        "observations": observations,
        "esptoolOutput": result.stdout.decode(errors="replace").strip(),
    }
    encoded = json.dumps(evidence, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(encoded)
    print(encoded, end="")


if __name__ == "__main__":
    main()

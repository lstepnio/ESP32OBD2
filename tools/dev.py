#!/usr/bin/env python3
"""Shared local/CI checks. Hardware access and release publication are deliberately absent."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "artifacts/checks"
FIRMWARE = ROOT / "firmware/gauge"
NATIVE_TESTS = {
    "obd_trace": ["tests/obd_trace_test.c", "main/src/obd_trace_format.c"],
    "page_action": ["tests/page_action_test.c", "main/src/page_action.c", "main/src/page_save_policy.c"],
    "elm_response": ["tests/elm_response_test.c", "main/src/elm_response.c"],
    "core_logic": ["tests/core_logic_test.c", *["main/src/" + name + ".c" for name in (
        "alert_engine", "config_trial_policy", "display_units", "json_guard",
        "pid_decoder", "poll_scheduler", "page_save_policy", "wifi_bulk_policy")]],
}


def run(command, *, cwd=ROOT):
    command = [str(part) for part in command]
    print("+ " + shlex.join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True, timeout=1800)


def require(program):
    if not shutil.which(program):
        raise SystemExit(f"Missing {program}. See README.md prerequisites.")


def native(name, sources, *, includes=(), arguments=(), execute=True):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    binary = OUTPUT / name
    run([*shlex.split(os.environ.get("CC", "cc")), "-std=c11", "-Wall", "-Wextra", "-Werror",
         "-fsanitize=address,undefined", "-Ifirmware/gauge/tests/stubs", "-Ifirmware/gauge/main/inc",
         *includes, *[FIRMWARE / source for source in sources], "-lm", "-o", binary])
    if execute:
        run([binary, *arguments])


def host(action):
    if action == "lint":
        run([sys.executable, "tools/validate.py"])
        for source in tracked_files((".js",)):
            run(["node", "--check", source])
        run(["git", "diff", "--check"])
        return
    if action == "test":
        # Whole discovery prevents a new suite disappearing behind filename-specific CI globs.
        run([sys.executable, "-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py"])
        run([sys.executable, "-m", "unittest", "discover", "-s", "docs/design/gauge-ui-proposal", "-p", "test_*.py"])
        run([sys.executable, "tools/obd_capture.py", "self-test"])
    for name, sources in NATIVE_TESTS.items():
        native(name, sources, execute=action == "test")


def firmware(action):
    require("idf.py")
    if action == "build":
        run(["idf.py", "-C", FIRMWARE, "build"])
    elif action == "test":
        idf = os.environ.get("IDF_PATH")
        if not idf:
            raise SystemExit("Source ESP-IDF 5.4.1 export.sh before firmware checks.")
        require("cmake")
        build = OUTPUT / "ui-host"
        run(["cmake", "-S", FIRMWARE / "tests/ui_host", "-B", build])
        run(["cmake", "--build", build, "-j", "2"])
        run([build / "gauge_ui_render", OUTPUT / "ui-renders"])
        json_dir = Path(idf) / "components/json/cJSON"
        native("config_binding", ["tests/config_binding_test.c", *["main/src/" + name + ".c" for name in (
            "config_document", "config_runtime", "diagnostic_filter", "json_guard", "obd_adapter_profile", "pid_decoder")], json_dir / "cJSON.c"],
            includes=["-I" + str(json_dir)], arguments=["contracts/examples/config-bound-adapter.json", "android/app/src/main/assets/numeric_config_template.json"])
    elif action == "lint":
        # Compiler warnings are errors; the linked-image memory gate is part of the build.
        run(["idf.py", "-C", FIRMWARE, "build"])


def android(action):
    tasks = {"build": [":app:assembleDebug"], "test": [":app:testDebugUnitTest", ":app:compileDebugAndroidTestKotlin"], "lint": [":app:lintDebug"]}
    run([ROOT / "android/gradlew", *tasks[action], "--no-daemon"], cwd=ROOT / "android")


def tracked_files(suffixes):
    paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    return [p for p in paths if p.endswith(suffixes)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("lint", "test", "build"))
    parser.add_argument("--target", choices=("host", "firmware", "android", "all"), default="all")
    args = parser.parse_args()
    targets = {"host": host, "firmware": firmware, "android": android}
    for target, check in targets.items():
        if args.target in (target, "all"):
            check(args.action)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as failure:
        sys.exit(failure.returncode)
    except (FileNotFoundError, subprocess.TimeoutExpired) as failure:
        sys.exit(str(failure))

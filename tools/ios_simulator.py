#!/usr/bin/env python3
"""Create named eGauge simulators and build/run a development-only setup probe.

Uses Xcode's bundled tools, Python stdlib and Apple's runtime download service.
Does not touch signing accounts, real devices, Bluetooth bonds or global defaults.
"""
from __future__ import annotations

import argparse
import json
import platform
import plistlib
import shutil
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/ios-setup"
BUNDLE_ID = "com.lstepnio.egauge.simulatorprobe"


def run(*args: str, capture: bool = False) -> str:
    result = subprocess.run(args, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""


def simjson(*args: str) -> dict:
    return json.loads(run("xcrun", "simctl", *args, "--json", capture=True))


def build() -> Path:
    if platform.system() != "Darwin":
        raise SystemExit("This setup tool needs macOS and full Xcode.")
    run("xcodebuild", "-checkFirstLaunchStatus")
    app = OUT / "EGaugeSetup.app"
    app.mkdir(parents=True, exist_ok=True)
    sdk = run("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path", capture=True)
    arch = "arm64" if platform.machine() == "arm64" else "x86_64"
    run("xcrun", "--sdk", "iphonesimulator", "swiftc", "-parse-as-library",
        "-swift-version", "6", "-target", f"{arch}-apple-ios18.0-simulator",
        "-sdk", sdk, str(ROOT / "ios/SimulatorProbe/Probe.swift"),
        str(ROOT / "ios/EGaugeCore/Sources/EGaugeCore/RuntimeIdentity.swift"),
        "-o", str(app / "EGaugeSetup"))
    info = {
        "CFBundleIdentifier": BUNDLE_ID, "CFBundleExecutable": "EGaugeSetup",
        "CFBundleName": "eGauge Setup", "CFBundleDisplayName": "eGauge Setup",
        "CFBundlePackageType": "APPL", "CFBundleVersion": "1",
        "CFBundleShortVersionString": "0.1", "MinimumOSVersion": "18.0",
        "LSRequiresIPhoneOS": True, "UIDeviceFamily": [1, 2],
        "UILaunchScreen": {},
        "NSBluetoothAlwaysUsageDescription": "Check Bluetooth availability in this development simulator.",
        "UISupportedInterfaceOrientations": ["UIInterfaceOrientationPortrait",
            "UIInterfaceOrientationLandscapeLeft", "UIInterfaceOrientationLandscapeRight"],
    }
    (app / "Info.plist").write_bytes(plistlib.dumps(info))
    shutil.copyfile(ROOT / "contracts/parity/runtime-identity.json", app / "runtime-identity.json")
    run("codesign", "--force", "--sign", "-", str(app))
    print(f"Built simulator-only probe: {app}", flush=True)
    return app


def devices(download: bool) -> list[dict]:
    runtimes = [r for r in simjson("list", "runtimes")["runtimes"]
                if r.get("isAvailable") and r["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-")]
    if not runtimes and download:
        run("xcodebuild", "-downloadPlatform", "iOS", "-architectureVariant",
            "arm64" if platform.machine() == "arm64" else "universal")
        return devices(False)
    if not runtimes:
        raise SystemExit("No iOS runtime installed. Run with --download, or finish the existing Xcode download first.")
    runtime = max(runtimes, key=lambda r: tuple(int(x) for x in r["version"].split(".")))
    all_types = simjson("list", "devicetypes")["devicetypes"]
    available_types = {t["name"]: t["identifier"] for t in all_types}
    selected = []
    # Stable device types available in Xcode 27. No duplicate devices on rerun.
    for label, model in [("eGauge Compact", "iPhone 16e"),
                         ("eGauge Large", "iPhone 17 Pro Max")]:
        if model not in available_types:
            raise SystemExit(f"Xcode does not offer {model}; choose a supported device type in this tool.")
        existing = simjson("list", "devices")["devices"].get(runtime["identifier"], [])
        match = next((d for d in existing if d["name"] == label and
                      d.get("isAvailable") and d.get("deviceTypeIdentifier") == available_types[model]), None)
        udid = match["udid"] if match else run("xcrun", "simctl", "create", label,
                    available_types[model], runtime["identifier"], capture=True)
        selected.append({"name": label, "model": model, "udid": udid,
                         "runtime": runtime["version"], "runtimeBuild": runtime["buildversion"]})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "devices.json").write_text(json.dumps(selected, indent=2) + "\n")
    return selected


def smoke(app: Path, device: dict) -> dict:
    udid = device["udid"]
    inventory = simjson("list", "devices")["devices"]
    state = next(d["state"] for group in inventory.values() for d in group if d["udid"] == udid)
    was_booted = state == "Booted"
    if not was_booted:
        run("xcrun", "simctl", "boot", udid)
    try:
        run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "install", udid, str(app))
        container = Path(run("xcrun", "simctl", "get_app_container", udid, BUNDLE_ID, "data", capture=True))
        result_path = container / "Documents/probe-result.json"
        result_path.unlink(missing_ok=True)
        run("xcrun", "simctl", "launch", "--terminate-running-process", udid, BUNDLE_ID)
        deadline = time.monotonic() + 45
        result = None
        while time.monotonic() < deadline:
            if result_path.exists():
                result = json.loads(result_path.read_text())
                if result.get("bluetoothState") != "Checking":
                    break
            time.sleep(1)
        if not result or not result.get("fixturesPassed"):
            raise SystemExit(f"Simulator probe failed on {device['name']}: {result}")
        screenshot = OUT / f"{device['name'].lower().replace(' ', '-')}.png"
        run("xcrun", "simctl", "io", udid, "screenshot", str(screenshot))
        return {**device, **result, "screenshot": str(screenshot)}
    finally:
        if not was_booted:
            run("xcrun", "simctl", "shutdown", udid)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--download", action="store_true", help="Download iOS if no runtime is installed")
    parser.add_argument("--build-only", action="store_true", help="Compile probe without requiring a runtime")
    parser.add_argument("--open", action="store_true", help="Leave the compact simulator open after smoke checks")
    parser.add_argument("--wait-for-runtime", type=int, default=0, metavar="MINUTES",
                        help="Wait for an already-running Xcode runtime installation")
    args = parser.parse_args()
    app = build()
    if args.build_only:
        return
    if args.wait_for_runtime:
        deadline = time.monotonic() + args.wait_for_runtime * 60
        print("Waiting for the existing Xcode iOS runtime installation.", flush=True)
        while not any(r.get("isAvailable") and
                      r["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-")
                      for r in simjson("list", "runtimes")["runtimes"]):
            if time.monotonic() >= deadline:
                raise SystemExit("Runtime installation did not finish within the wait limit. Rerun setup after resolving the Xcode download.")
            time.sleep(15)
        print("iOS runtime is available; creating and checking simulators.", flush=True)
    selected = devices(args.download)
    results = [smoke(app, device) for device in selected]
    (OUT / "validation.json").write_text(json.dumps({
        "xcode": run("xcodebuild", "-version", capture=True),
        "macOS": platform.mac_ver()[0], "devices": results,
    }, indent=2) + "\n")
    if args.open:
        udid = selected[0]["udid"]
        state = next(d["state"] for group in simjson("list", "devices")["devices"].values()
                     for d in group if d["udid"] == udid)
        if state != "Booted":
            run("xcrun", "simctl", "boot", udid)
        device_hub = Path(run("xcode-select", "-p", capture=True)).parent / "Applications/DeviceHub.app"
        if device_hub.exists():
            run("open", "-a", str(device_hub))
        else:
            run("open", "-a", "Simulator", "--args", "-CurrentDeviceUDID", udid)
        run("xcrun", "simctl", "bootstatus", udid, "-b")
        run("xcrun", "simctl", "launch", "--terminate-running-process", udid, BUNDLE_ID)
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()

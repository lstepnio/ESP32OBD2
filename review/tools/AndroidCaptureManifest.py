#!/usr/bin/env python3
"""Copy the focused managed-emulator captures and record honest per-image provenance."""

import hashlib
import json
import shutil
import struct
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (
    ROOT
    / "android/app/build/outputs/managed_device_android_test_additional_output/debug/ciPhone/website-review"
)
DEST = ROOT / "review/screens/android"
HARNESS = ROOT / "android/app/src/androidTest/java/com/lstepnio/egauge/WebsiteReviewCaptureTest.kt"
SOURCES = {
    "gauge": "ui/home/HomeScreen.kt + debug ui/preview/ScreenFixtures.kt",
    "car": "ui/car/CarScreen.kt",
    "settings": "ui/settings/SettingsScreen.kt",
    "expert": "ui/expert/ExpertScreen.kt",
    "setup": "ui/setup/SetupScreen.kt + debug ui/preview/ScreenFixtures.kt",
    "customize": "ui/customize/DashboardEditorScreen.kt + debug ui/preview/ScreenFixtures.kt",
    "alerts": "ui/AlertPresentation.kt (CarAlertWorkspace or AlertWorkspaceList)",
    "alert-settings": "ui/car/CarAlertSettingsScreen.kt",
    "car-advanced": "ui/expert/ExpertScreen.kt or HiddenFaultCodesScreen.kt",
    "updates": "ui/settings/UpdatesScreen.kt + debug ui/preview/ScreenFixtures.kt",
}
CASES = [
    "gauge-default",
    "car-default",
    "settings-default",
    "expert-default",
    "setup-default",
    "customize-default",
    "alerts-default",
    "alert-settings-default",
    "car-advanced-default",
    "updates-default",
    "alerts-empty",
    "setup-loading",
    "gauge-permission-denied",
    "updates-error",
    "alerts-synthetic-current",
    "customize-review",
    "car-advanced-hidden-codes",
    "car-advanced-second-adapter",
]
expected = {
    f"{case}-{size}-{theme}.png"
    for case in CASES
    for size in ("phone", "large")
    for theme in ("light", "dark")
}
actual = {p.name for p in SOURCE.glob("*.png")}
if actual != expected:
    raise SystemExit(
        f"Capture set mismatch. Missing={expected - actual}; unexpected={actual - expected}"
    )
commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
entries = []
for name in sorted(expected):
    src = SOURCE / name
    payload = src.read_bytes()
    width, height = struct.unpack(">II", payload[16:24])
    prefix, size, theme = name[:-4].rsplit("-", 2)
    route = next(
        route for route in sorted(SOURCES, key=len, reverse=True) if prefix.startswith(route + "-")
    )
    state = prefix[len(route) + 1 :]
    expected_size = (390, 844) if size == "phone" else (1000, 800)
    if (width, height) != expected_size:
        raise SystemExit(f"Unexpected resolution for {name}: {width}x{height}")
    shutil.copy2(src, DEST / name)
    entries.append(
        {
            "path": "review/screens/android/" + name,
            "surface": "android",
            "route": route,
            "state": state,
            "theme": theme,
            "simulated": True,
            "source_commit": commit,
            "source": "android/app/src/main/java/com/lstepnio/egauge/" + SOURCES[route],
            "harness": str(HARNESS.relative_to(ROOT)),
            "harness_sha256": hashlib.sha256(HARNESS.read_bytes()).hexdigest(),
            "synthetic_data": "Fixed ScreenFixtures and WebsiteReviewCaptureTest values; model-only disposable emulator defaults for empty Alerts and notification policy. Example car/gauge/adapter identities, temperatures, RPM, faults and versions are fictional.",
            "resolution_px": [width, height],
            "layout_dp": [width, height],
            "density": 1.0,
            "font_scale": 1.0,
            "fonts": "Production EGaugeTheme Typography; Android API35 default sans-serif fallback (no downloaded fonts).",
            "renderer": "Compose captureToImage().asAndroidBitmap() PNG, focused managed emulator instrumentation; SwiftShader indirect",
            "device": "Gradle ciPhone: API35 google_apis arm64-v8a Pixel 2, configured physical display1080x1920 density420; composition overrides density1",
            "system_ui": "Excluded by composition capture; no demo mode changes",
            "caveats": [
                "Static navigation shell mirrors production bottom-bar/rail breakpoints; CompanionApp/model navigation, global operation/alert banners, window insets and full-device SystemUI are not exercised.",
                "Default Alerts and empty Alerts intentionally show the same production empty workspace. Populated synthetic-current uses production list inside a test header and tabs, not the model-owned live workspace.",
                "Alert settings combines production ScreenFixtures.customize with model-only default phone policy; saved-on-phone panel is fixture/model comparison, not proof of a durable save.",
                "Second-adapter variant captures the production Advanced summary with synthetic bindings; chooser sheet, discovery and transport compatibility are untested.",
                "No radio, controller, physical phone, gauge, installed artifact or live vehicle behavior is qualified. Offscreen content and dialogs require separate captures.",
            ],
            "sha256": hashlib.sha256(payload).hexdigest(),
        }
    )
manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    "capture_count": len(entries),
    "source_commit": commit,
    "entries": entries,
}
(DEST / "manifest.fragment.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(f"Copied and verified {len(entries)} PNGs; wrote {DEST / 'manifest.fragment.json'}")

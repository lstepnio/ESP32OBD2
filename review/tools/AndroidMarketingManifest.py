#!/usr/bin/env python3
"""Preserve the audit baseline and copy only the three focused 2x marketing captures."""

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
    / "android/app/build/outputs/managed_device_android_test_additional_output/debug/ciPhone/website-marketing"
)
DEST = ROOT / "docs/assets/app"
HARNESS = ROOT / "android/app/src/androidTest/java/com/lstepnio/egauge/WebsiteReviewCaptureTest.kt"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


expected = {f"{route}-default-phone-dark.png" for route in ("gauge", "car", "settings")}
assert {p.name for p in SOURCE.glob("*.png")} == expected
entries = []
for route in ("gauge", "car", "settings"):
    src = SOURCE / f"{route}-default-phone-dark.png"
    assert struct.unpack(">II", src.read_bytes()[16:24]) == (780, 1688)
    dest = DEST / f"{route}.png"
    shutil.copy2(src, dest)
    entries.append(
        {
            "path": str(dest.relative_to(ROOT)),
            "sha256": sha(dest),
            "route": route,
            "source_capture": str(src.relative_to(ROOT)),
            "resolution_px": [780, 1688],
            "layout_dp": [390, 844],
            "density": 2.0,
            "font_scale": 1.0,
            "theme": "dark",
            "advanced": False,
            "navigation": ["Gauge", "Car", "Settings"],
            "synthetic": True,
            "synthetic_data": "Example car; stored Engine P0300; coolant108C/limit105C; preview RPM2840; brightness80%; rotation0; cycle10s. Fixture values, not live readings.",
            "gauge_connection": "Synthetic Gauge ready and Car connected through production ConnectionStatusWidget; example readings retain PREVIEW, not live vehicle evidence.",
            "controls": "Production composables, canEditActions=false and canEditActionPages=false. Disabled rows retain production arrow styling; no execution or public-control capability claim.",
        }
    )
manifest = {
    "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    "source_commit": subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip(),
    "runtime_source_equivalent_to": "62d1e4b (git diff of main/debug/core/Gradle inputs empty)",
    "harness": str(HARNESS.relative_to(ROOT)),
    "harness_sha256": sha(HARNESS),
    "capture_method": "Compose captureToImage, managed ciPhone API35 google_apis arm64-v8a Pixel2, SwiftShader indirect; no physical device",
    "fonts": "Production EGaugeTheme typography and API35 sans-serif fallback; no downloaded fonts",
    "caveats": [
        "Composition only; static three-tab shell, no CompanionApp navigation/global banners/window insets/SystemUI.",
        "Production Gauge retains PREVIEW label; no added Demo badge.",
        "No radio, device, vehicle, durable-save or action execution qualification.",
        "72-image review/screens/android audit baseline unchanged.",
    ],
    "entries": entries,
}
(DEST / "marketing-provenance.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("Copied and verified three 780x1688 marketing PNGs")

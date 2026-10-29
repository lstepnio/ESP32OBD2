"""Package production RGB565 host frames without claiming hardware observations."""

import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "gauge_gallery", ROOT / "tools/render_gauge_gallery.py"
)
gallery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gallery)
base = ROOT / "review/screens/esp32"
frames = ROOT / "artifacts/review-ui-frames"
commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
entries = []
showcase_names = [
    "numeric-rpm",
    "arc-coolant",
    "bar-coolant",
    "trend-coolant",
    "dual-gear-rpm",
    "dual-arc-gear-rpm",
    "dual-arc-reverse-rpm",
    "dual-arc-coolant-gear",
    "dual-arc-source-unavailable",
    "dual-mixed-units",
    "numeric-fuel-pressure",
    "numeric-negative-ignition",
    "numeric-wide-rpm",
    "bar-battery",
    "dual-mil-codes",
    "numeric-threshold-warning",
    "arc-threshold-mil",
    "bar-threshold-mil",
    "trend-threshold-mil",
    "dual-threshold-mil",
    "critical-attention",
    "critical-mil",
    "dual-child-loss",
    "dual-both-sources-absent",
    "stored-codes-without-mil",
]
for source in sorted(frames.glob("*.rgb")):
    name = source.stem
    source_name = name
    if name.startswith("demo-"):
        name = "showcase-" + showcase_names[int(name.split("-")[1]) - 1]
    png = gallery.png(source.read_bytes())
    raw = base / "raw" / f"esp32_{name}_dark_240.png"
    raw.write_bytes(png)
    encoded = base64.b64encode(png).decode("ascii")
    framed = base / "framed" / f"esp32_{name}_round.svg"
    framed.write_text(
        f"""<svg xmlns="http://www.w3.org/2000/svg" width="320" height="320" viewBox="0 0 320 320"><title>eGauge {name}, software-rendered reference-board screen</title><defs><clipPath id="panel"><circle cx="160" cy="160" r="120"/></clipPath></defs><circle cx="160" cy="160" r="141" fill="#172025"/><circle cx="160" cy="160" r="131" fill="#05080a"/><image x="40" y="40" width="240" height="240" clip-path="url(#panel)" href="data:image/png;base64,{encoded}"/></svg>"""
    )
    for path, variant in [(raw, "raw"), (framed, "illustrated-frame")]:
        entries.append(
            {
                "file": path.relative_to(ROOT / "review/screens").as_posix(),
                "sourceFrame": source_name,
                "kind": "simulated",
                "data": "synthetic host fixtures",
                "source": "firmware/gauge/tests/ui_host/ui_render_test.c + production main/src/ui.c and fonts",
                "commit": commit,
                "workingTreeChanges": "capture-only RGB565 option in host fixture",
                "renderer": "LVGL 9.2.2 software, RGB565 render buffer expanded to RGB888 PNG",
                "nativePanel": {"width": 240, "height": 240, "colorDepth": 16},
                "resolution": [240, 240] if variant == "raw" else [320, 320],
                "theme": "dark",
                "variant": variant,
                "buildOptions": {"EGAUGE_HOST_RGB565": True, "EGAUGE_PARKED_IDLE_TEST": True},
                "caveats": [
                    "not physical display/touch/radio evidence",
                    "fixture-injected action states do not enable or qualify commands",
                    "frame is an illustration, not a product photograph",
                ],
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
        )
(base / "manifest-fragment.json").write_text(json.dumps(entries, indent=2) + "\n")
print(f"Packaged {len(entries) // 2} native screens and matching circular framed variants")

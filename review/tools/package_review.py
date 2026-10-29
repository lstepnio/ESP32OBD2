"""Build one provenance manifest and a local review gallery from existing captures."""

import hashlib
import html
import json
from pathlib import Path
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SCREENS = ROOT / "review/screens"
commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
previous = {}
if (SCREENS / "manifest.json").exists():
    previous = {
        e["file"]: e for e in json.loads((SCREENS / "manifest.json").read_text())["entries"]
    }
entries = json.loads((SCREENS / "esp32/manifest-fragment.json").read_text())
for entry in entries:
    entry["surface"] = "esp32"
for entry in json.loads((SCREENS / "android/manifest.fragment.json").read_text())["entries"]:
    entries.append(
        {
            "file": entry["path"].removeprefix("review/screens/"),
            "kind": "simulated",
            "surface": "android",
            "commit": entry["source_commit"],
            "resolution": entry["resolution_px"],
            **{
                k: v
                for k, v in entry.items()
                if k not in ["path", "source_commit", "resolution_px"]
            },
        }
    )
for path in sorted((SCREENS / "web").glob("*.png")):
    data = path.read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    synthetic = any(s in path.name for s in ["_success_", "_error_", "feed-error"])
    key = path.relative_to(SCREENS).as_posix()
    digest = hashlib.sha256(data).hexdigest()
    if key in previous and previous[key]["sha256"] == digest:
        entries.append(previous[key])
        continue
    after = "_after" in path.name
    entries.append(
        {
            "file": key,
            "kind": "simulated" if synthetic else "real",
            "surface": "web",
            "source": "website/index.html or website/recovery/index.html via tools/build_website.py; pinned public downloads verified",
            "commit": commit if after else "083b0013363a697ab832179ee1af71b4456ca181",
            "phase": "after" if after else "before",
            "device": "Playwright Chromium 156 desktop browser with CSS viewport emulation",
            "resolution": [width, height],
            "theme": "light (only authored website theme)",
            "environment": "local preview at 127.0.0.1:"
            + ("8776" if after else "8772")
            + ", not a deployed revision",
            "data": "synthetic intercepted local API response"
            if synthetic
            else "authored site with real release metadata snapshot and software-rendered embedded product examples",
            "sha256": hashlib.sha256(data).hexdigest(),
            "caveats": [
                "browser screenshot of site source, not physical-gauge or live-vehicle evidence",
                "form tests never submit to the public API",
                "embedded App/gauge images retain software/example provenance",
            ],
        }
    )
for entry in entries:
    path = SCREENS / entry["file"]
    assert path.is_file(), path
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"], path
manifest = {
    "schemaVersion": 1,
    "sourceCommit": commit,
    "scope": "Phase2/3 review only; no website or device publication",
    "entries": entries,
}
(SCREENS / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
cards = []
for entry in entries:
    # Raw and framed gauge are both retained; the default gallery shows the circular version.
    if entry["surface"] == "esp32" and entry.get("variant") == "raw":
        continue
    name = entry["file"].split("/")[-1]
    cards.append(
        f'<article data-surface="{entry["surface"]}"><a href="screens/{html.escape(entry["file"])}"><img loading="lazy" src="screens/{html.escape(entry["file"])}" alt="{html.escape(name)}"></a><h3>{html.escape(name)}</h3><p>{entry["kind"]} · {entry["resolution"][0]} × {entry["resolution"][1]}</p></article>'
    )
page = (
    """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>eGauge review evidence</title><meta name="robots" content="noindex,nofollow"><link rel="icon" href="data:,"><style>body{background:#0c1114;color:#f2f5ef;font:16px system-ui;margin:32px}a{color:#00ff70}header{max-width:1000px}select{font:inherit;padding:10px;margin:16px 0}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px}article{background:#172025;padding:16px;min-width:0;border-radius:16px}img{max-width:100%;height:340px;object-fit:contain;display:block;margin:auto}h3{font-size:14px;overflow-wrap:anywhere}p{font-size:14px;color:#a9babd}</style><header><h1>eGauge review evidence</h1><p>Production UI with synthetic data, software gauge frames and local website captures. These images do not establish physical vehicle behavior. Raw captures and detailed provenance remain in <a href="screens/manifest.json">the manifest</a>.</p><p><a href="REPORT.md">Findings and proposed fixes</a> · <a href="context.md">Context</a> · <a href="feature-matrix.md">Feature matrix</a></p><label for="surface">Surface </label><select id="surface"><option value="all">All</option><option value="android">Android</option><option value="esp32">Round gauge</option><option value="web">Website before</option></select></header><main>"""
    + "".join(cards)
    + """</main><script>document.querySelector('#surface').addEventListener('change',e=>document.querySelectorAll('article').forEach(a=>a.hidden=e.target.value!=='all'&&a.dataset.surface!==e.target.value));</script></html>"""
)
(ROOT / "review/index.html").write_text(page)
print(f"Verified and indexed {len(entries)} capture files")

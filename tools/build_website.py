#!/usr/bin/env python3
"""Build the self-hosted public site from authored copy and canonical project screens.

Assets use a content-addressed directory so deployment can upload them before
atomically replacing index.html inside the existing Nginx bind mount.
"""

from __future__ import annotations

import argparse
from datetime import date
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


class SiteHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.links = []
        self.images = 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if identifier := attrs.get("id"):
            if identifier in self.ids:
                raise ValueError(f"Duplicate website id: {identifier}")
            self.ids.add(identifier)
        for key in ("href", "src", "action"):
            if attrs.get(key):
                self.links.append(attrs[key])
        if tag == "img":
            if not attrs.get("alt"):
                raise ValueError("Website images need descriptive alt text")
            self.images += 1


def build(output: Path, downloads: Path | None = None, metadata_only=False):
    source = ROOT / "website"
    content = json.loads((source / "content.json").read_text())
    for document in content["sourceDocuments"]:
        if not (ROOT / document).is_file():
            raise ValueError(f"Missing website source authority: {document}")
    reviewed = date.fromisoformat(content["reviewedOn"])
    readings = json.loads((ROOT / "contracts/reading-catalog.json").read_text())["readings"]
    files = {name: (source / name).read_bytes() for name in ("styles.css", "app.js", "favicon.svg")}
    for image in sorted((ROOT / "docs/assets").rglob("*.png")):
        files[image.relative_to(ROOT / "docs/assets").as_posix()] = image.read_bytes()
    for image in sorted((source / "future").glob("*.png")):
        files["future/" + image.name] = image.read_bytes()
    for asset in sorted((source / "recovery").iterdir()):
        if asset.suffix != ".html" and asset.name != "package.json":
            files["recovery/" + asset.name] = asset.read_bytes()
    package = json.loads((source / "downloads/manifest.json").read_text())
    virtual = set()
    for name, record in package["files"].items():
        if Path(name).is_absolute() or ".." in Path(name).parts:
            raise ValueError("Invalid download path")
        target = output / "downloads" / name
        if name.endswith("/recovery.json"):
            data = (source / "recovery/package.json").read_bytes()
        elif downloads is not None:
            data = (downloads / name).read_bytes()
        elif metadata_only:
            virtual.add("/downloads/" + name)
            continue
        else:
            raise ValueError("Pass --downloads with verified package files for a deployable build")
        if len(data) != record["size"] or hashlib.sha256(data).hexdigest() != record["sha256"]:
            raise ValueError(f"Download verification failed: {name}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    (output / "downloads/manifest.json").write_bytes(
        (source / "downloads/manifest.json").read_bytes()
    )
    digest = hashlib.sha256()
    for name, payload in sorted(files.items()):
        digest.update(name.encode() + b"\0" + payload + b"\0")
    prefix = "/assets/" + digest.hexdigest()[:16]
    for name, payload in files.items():
        path = output / prefix.lstrip("/") / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
    replacements = {
        "ASSET_PREFIX": prefix,
        "RECOVERY_MANIFEST": package["firmware"]["url"],
        "RECOVERY_MANIFEST_SHA256": package["firmware"]["sha256"],
        "FIRMWARE_VERSION": package["firmware"]["version"],
        "FIRMWARE_RELEASE": package["firmware"]["release"],
        "APK_URL": package["app"]["url"],
        "APP_VERSION": package["app"]["version"],
        "READING_COUNT": str(len(readings)),
        "UPDATED_DATE": reviewed.isoformat(),
        "UPDATED_LABEL": f"{reviewed.day} {reviewed.strftime('%B %Y')}",
    }
    image_count = 0
    for name in ("index.html", "recovery/index.html"):
        html = (source / name).read_text()
        for key, value in replacements.items():
            html = html.replace("{{" + key + "}}", value)
        if re.search(r"{{[^}]+}}", html):
            raise ValueError("Unresolved website placeholder")
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(html)
    (output / "robots.txt").write_bytes((source / "robots.txt").read_bytes())
    (output / "favicon.svg").write_bytes(files["favicon.svg"])
    for name in ("index.html", "recovery/index.html"):
        document = SiteHTML()
        document.feed((output / name).read_text())
        image_count += document.images
        for link in document.links:
            url = urlsplit(link)
            if url.scheme or url.netloc or url.path.startswith("/api/"):
                continue
            path = unquote(url.path)
            if path.startswith("/"):
                asset = output / path.lstrip("/")
            else:
                asset = (output / name).parent / path
            if asset.is_dir():
                asset = asset / "index.html"
            if path and not asset.is_file() and path not in virtual:
                raise ValueError(f"Missing website asset: {link}")
            if url.fragment and not path and url.fragment not in document.ids:
                raise ValueError(f"Missing website anchor: {link}")
    source_commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    manifest = {
        "sourceCommit": source_commit,
        "reviewedOn": reviewed.isoformat(),
        "assetPrefix": prefix,
        "sourceDocuments": content["sourceDocuments"],
        "files": {
            p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(output.rglob("*"))
            if p.is_file()
        },
    }
    (output / "site-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(
        f"PASS: website links, {image_count} image placements, {len(files)} assets; catalog {len(readings)}"
    )
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/website-release")
    parser.add_argument("--check", action="store_true", help="Validate in a temporary directory")
    parser.add_argument(
        "--downloads", type=Path, help="Verified package output; required for deployment"
    )
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix="egauge-site-") as directory:
            build(Path(directory), args.downloads, metadata_only=args.downloads is None)
    else:
        output = args.output.resolve()
        # Never overwrite the source tree, repo root, or an arbitrary existing directory.
        if not output.is_relative_to(ROOT / "artifacts"):
            raise SystemExit(
                "Build output must be inside this checkout's ignored artifacts directory"
            )
        output.mkdir(parents=True, exist_ok=True)
        if any(output.iterdir()):
            raise SystemExit(
                "Use an empty output directory so the manifest contains only this release"
            )
        build(output, args.downloads)
        print(f"Built {output}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build the self-hosted public site from authored copy and canonical project screens.

Assets use a content-addressed directory so deployment can upload them before
atomically replacing index.html inside the existing Nginx bind mount.
"""

from __future__ import annotations

import argparse
from datetime import date
import hashlib
from html import escape
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
        for candidate in attrs.get("srcset", "").split(","):
            if candidate.strip():
                self.links.append(candidate.strip().split()[0])
        if tag == "img":
            if not attrs.get("alt"):
                raise ValueError("Website images need descriptive alt text")
            self.images += 1


def gallery(content):
    screens = content["screens"]
    choices, data, names = [], {}, set()
    for screen in screens:
        key = screen["key"]
        if not re.fullmatch(r"[a-z]+(?:-[a-z]+)*", key) or key in names:
            raise ValueError("Invalid or duplicate gallery key")
        names.add(key)
        if not (ROOT / screen["source"]).is_file():
            raise ValueError("Missing gallery source authority")
        if not (ROOT / "docs/assets/gauge" / (key + ".png")).is_file():
            raise ValueError("Missing gallery screen")
        label, caption, alt = (screen[k] for k in ("label", "caption", "alt"))
        if not all(isinstance(v, str) and v.strip() for v in (label, caption, alt)):
            raise ValueError("Gallery labels must be nonempty text")
        pressed = "true" if len(choices) == 0 else "false"
        choices.append(
            f'<button type="button" data-screen="{key}" aria-pressed="{pressed}">{escape(label)}</button>'
        )
        data[key] = [caption, alt]
    if not screens or screens[0]["key"] != "numeric":
        raise ValueError("Gallery must start with Numeric")
    return "".join(choices), json.dumps(data, ensure_ascii=False)


def repository_file(name):
    path = ROOT / name
    if Path(name).is_absolute() or ".." in Path(name).parts or not path.is_file():
        raise ValueError(f"Missing or invalid provenance reference: {name}")
    return path


def validate_claims(content, html):
    """Authored scope/evidence is reviewable metadata, not automatic truth proof."""
    claims = content.get("claims", {})
    tokens = set(re.findall(r"{{CLAIM_([A-Z_]+)}}", html))
    if not tokens or tokens != set(claims):
        raise ValueError("Every material claim needs a used claim provenance record")
    scopes = content.get("claimScopes", {})
    replacements = {}
    for key, claim in {**claims, **content["actionAvailability"]}.items():
        scope = scopes.get(claim.get("scope"), {})
        if not scope.get("audience") or scope.get("availability") not in {
            "available-development",
            "observed-limited",
            "owner-only-candidate",
            "planned",
            "exploring",
            "paused",
        }:
            raise ValueError(f"Missing claim audience/availability: {key}")
        if not claim.get("text") or not claim.get("evidence"):
            raise ValueError(f"Missing claim text/evidence: {key}")
        sources = claim.get("sources", [claim["source"]] if "source" in claim else [])
        if not sources:
            raise ValueError(f"Missing claim source: {key}")
        for name in [*sources, *claim["evidence"]]:
            repository_file(name)
        if key in claims:
            replacements["CLAIM_" + key] = escape(claim["text"])
    return replacements


def claim_inputs(content):
    """One shared input inventory covers material claims and their evidence."""
    references = set(content.get("sourceDocuments", []))
    for claim in [
        *content.get("claims", {}).values(),
        *content.get("actionAvailability", {}).values(),
    ]:
        references.update(claim.get("sources", [claim["source"]] if "source" in claim else []))
        references.update(claim.get("evidence", []))
    return references


def review_claim_inputs(content):
    baseline = content.get("claimInputProvenance", {})
    hashes = baseline.get("sha256", {})
    if not hashes or not baseline.get("reviewedOn") or not baseline.get("reason"):
        raise ValueError("Missing claim input provenance or semantic review metadata")
    date.fromisoformat(baseline["reviewedOn"])
    current = {
        name: hashlib.sha256(repository_file(name).read_bytes()).hexdigest()
        for name in sorted(claim_inputs(content))
    }
    differences = sorted(
        name for name in hashes.keys() | current.keys() if hashes.get(name) != current.get(name)
    )
    if not differences:
        return []
    affected = []
    for key, claim in {
        **content.get("claims", {}),
        **content.get("actionAvailability", {}),
    }.items():
        references = set(claim.get("sources", [claim["source"]] if "source" in claim else []))
        references.update(claim.get("evidence", []))
        if references.intersection(differences):
            affected.append(key)
    return [
        {
            "changedInputs": differences,
            "affectedClaims": sorted(affected),
            "currentSha256": current,
            "requiredAction": "Review affected claim scope, availability, copy and evidence against these exact sources; update claimInputProvenance with reviewedOn, specific reason and this exact currentSha256 map only after semantic review.",
        }
    ]


def review_images(content, html):
    """Keep capture baselines immutable; only explicit equivalence reviews accept drift."""
    records = {}
    pending = list(content.get("pendingCaptures", []))
    changed = []
    for name in content.get("imageProvenance", []):
        metadata_path = repository_file(name)
        metadata = json.loads(metadata_path.read_text())
        inputs = metadata.get("inputProvenance", {})
        hashes, paths = inputs.get("sha256", {}), inputs.get("paths", [])
        if not hashes or not paths or not inputs.get("revision"):
            raise ValueError(f"Missing image input provenance: {name}")
        current_names = set()
        for source in paths:
            path = ROOT / source
            if Path(source).is_absolute() or ".." in Path(source).parts:
                raise ValueError("Invalid image input path")
            if path.is_file():
                current_names.add(source)
            elif path.is_dir():
                current_names.update(
                    p.relative_to(ROOT).as_posix() for p in path.rglob("*") if p.is_file()
                )
        current = {
            path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
            for path in sorted(current_names)
        }
        differences = sorted(
            path for path in hashes.keys() | current.keys() if hashes.get(path) != current.get(path)
        )
        review = metadata.get("equivalenceReview", {})
        accepted = bool(
            differences
            and review.get("reason")
            and review.get("evidence")
            and review.get("reviewedOn")
            and review.get("sha256") == current
        )
        if accepted:
            date.fromisoformat(review["reviewedOn"])
            for evidence in review["evidence"]:
                repository_file(evidence)
        if differences and not accepted:
            changed.append(
                {
                    "provenance": name,
                    "changedInputs": differences,
                    "currentSha256": current,
                    "requiredAction": "Recapture affected screens or document a semantic equivalenceReview with reason, evidence, reviewedOn and exact current sha256 map.",
                }
            )
        for entry in metadata.get("entries", []):
            asset = (
                entry.get("path")
                or (metadata_path.parent / entry["file"]).relative_to(ROOT).as_posix()
            )
            if asset in records:
                raise ValueError(f"Duplicate image provenance: {asset}")
            if hashlib.sha256(repository_file(asset).read_bytes()).hexdigest() != entry.get(
                "sha256"
            ):
                raise ValueError(f"Image capture hash mismatch: {asset}")
            records[asset] = name
    used = set(re.findall(r"{{ASSET_PREFIX}}/((?:app|gauge)/[^\" ]+\.png)", html))
    used.update("gauge/" + screen["key"] + ".png" for screen in content["screens"])
    for asset in used:
        if "docs/assets/" + asset not in records:
            raise ValueError(f"Missing image provenance: {asset}")
    return {
        "changedImageInputs": changed,
        "pendingCaptures": pending,
        "publicationReady": not changed and not pending,
    }


def build(output: Path, downloads: Path | None = None, metadata_only=False):
    source = ROOT / "website"
    content = json.loads((source / "content.json").read_text())
    for document in content["sourceDocuments"]:
        if not (ROOT / document).is_file():
            raise ValueError(f"Missing website source authority: {document}")
    reviewed = date.fromisoformat(content["reviewedOn"])
    template = (source / "index.html").read_text()
    claim_replacements = validate_claims(content, template)
    review = review_images(content, template)
    review["changedClaimInputs"] = review_claim_inputs(content)
    review["publicationReady"] = review["publicationReady"] and not review["changedClaimInputs"]
    if not metadata_only and not review["publicationReady"]:
        raise ValueError("Website semantic review/capture pending; run --check for review items")
    (output / "site-review.json").write_text(json.dumps(review, indent=2) + "\n")
    if not review["publicationReady"]:
        print(
            f"REVIEW REQUIRED: {len(review['changedClaimInputs'])} changed claim input sets, "
            f"{len(review['changedImageInputs'])} changed image input sets, "
            f"{len(review['pendingCaptures'])} pending captures (use --review-report)"
        )
    choices, screen_data = gallery(content)
    readings = json.loads((ROOT / "contracts/reading-catalog.json").read_text())["readings"]
    files = {name: (source / name).read_bytes() for name in ("styles.css", "app.js", "favicon.svg")}
    marker = b"/* EGAUGE_SCREEN_DATA */ {}"
    if marker not in files["app.js"]:
        raise ValueError("Missing build-time gallery data marker")
    files["app.js"] = files["app.js"].replace(marker, screen_data.encode())
    used_images = set(re.findall(r"{{ASSET_PREFIX}}/((?:app|gauge)/[^\" ]+\.png)", template))
    used_images.update("gauge/" + screen["key"] + ".png" for screen in content["screens"])
    for name in sorted(used_images):
        files[name] = (ROOT / "docs/assets" / name).read_bytes()
    for image in sorted((source / "future").glob("*")):
        if image.suffix not in {".png", ".webp"}:
            continue
        files["future/" + image.name] = image.read_bytes()
    files["social-preview.png"] = (source / "social-preview.png").read_bytes()
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
    source_commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    replacements = {
        "SOURCE_COMMIT": source_commit,
        "ASSET_PREFIX": prefix,
        "RECOVERY_MANIFEST": package["firmware"]["url"],
        "RECOVERY_MANIFEST_SHA256": package["firmware"]["sha256"],
        "FIRMWARE_VERSION": package["firmware"]["version"],
        "FIRMWARE_RELEASE": package["firmware"]["release"],
        "APK_URL": package["app"]["url"],
        "APP_VERSION": package["app"]["version"],
        "APP_VERSION_CODE": str(package["app"].get("versionCode", "not recorded")),
        "READING_COUNT": str(len(readings)),
        "UPDATED_DATE": reviewed.isoformat(),
        "UPDATED_LABEL": f"{reviewed.day} {reviewed.strftime('%B %Y')}",
        "SCREEN_CHOICES": choices,
        "SOCIAL_IMAGE": "https://egauge.majjix.com" + prefix + "/social-preview.png",
    }
    replacements.update(claim_replacements)
    for key in ("airUp", "winch", "absTcs"):
        item = content["actionAvailability"][key]
        if not (ROOT / item["source"]).is_file():
            raise ValueError("Missing action availability authority")
        token = {"airUp": "AIR_UP_STATUS", "winch": "WINCH_STATUS", "absTcs": "ABS_TCS_STATUS"}[key]
        replacements[token] = escape(item["text"])
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
    documents = {}
    for name in ("index.html", "recovery/index.html"):
        document = SiteHTML()
        document.feed((output / name).read_text())
        documents[name] = document
        image_count += document.images
    for name, document in documents.items():
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
            if url.fragment and asset.is_file() and asset.suffix == ".html":
                target_name = asset.relative_to(output).as_posix()
                if target_name in documents and url.fragment not in documents[target_name].ids:
                    raise ValueError(f"Missing website anchor: {link}")
            elif url.fragment and not path and url.fragment not in document.ids:
                raise ValueError(f"Missing website anchor: {link}")
    manifest = {
        "sourceCommit": source_commit,
        "reviewedOn": reviewed.isoformat(),
        "assetPrefix": prefix,
        "sourceDocuments": content["sourceDocuments"],
        "claims": {
            "scopes": content["claimScopes"],
            "items": content["claims"],
            "actions": content["actionAvailability"],
            "inputProvenance": content["claimInputProvenance"],
        },
        "visualReview": review,
        "imageProvenance": {
            name: json.loads(repository_file(name).read_text())
            for name in content["imageProvenance"]
        },
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
    parser.add_argument(
        "--review-report", type=Path, help="Save actionable source/image review JSON during --check"
    )
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix="egauge-site-") as directory:
            manifest = build(Path(directory), args.downloads, metadata_only=args.downloads is None)
            if args.review_report:
                args.review_report.parent.mkdir(parents=True, exist_ok=True)
                args.review_report.write_text(json.dumps(manifest["visualReview"], indent=2) + "\n")
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

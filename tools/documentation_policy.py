"""Offline checks for authored documentation ownership, local links and context paths."""
from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path
from urllib.parse import unquote

TASK_ID = r'(?:QUAL|CODE|RELEASE|FEATURE|PLATFORM)-\d{2}'


def authored_documents(root: Path) -> list[Path]:
    return sorted([root / name for name in (
        'README.md', 'AGENTS.md', 'CONTRIBUTING.md', 'THIRD_PARTY_NOTICES.md',
        'android/README.md', 'firmware/gauge/README.md',
    )] + list((root / 'docs').rglob('*.md')))


def prose(text: str) -> str:
    """Remove fenced code so examples cannot create headings, links or task IDs."""
    result = []
    fence = None
    for line in text.splitlines():
        match = re.match(r'^\s*(`{3,}|~{3,})', line)
        if match:
            marker = match[1]
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
            continue
        if fence is None:
            result.append(line)
    return '\n'.join(result)


def heading_anchors(text: str) -> set[str]:
    text = prose(text)
    anchors = set(re.findall(r'<a\s+(?:id|name)=[\"\']([^\"\']+)', text))
    counts: dict[str, int] = {}
    for line in text.splitlines():
        match = re.match(r'^ {0,3}#{1,6}\s+(.+?)(?:\s+#+\s*)?$', line)
        if not match:
            continue
        title = re.sub(r'<[^>]*>', '', match[1]).lower()
        slug = ''.join(c for c in title if c in ' -_' or unicodedata.category(c)[0] in 'LN')
        slug = slug.replace(' ', '-')
        ordinal = counts.get(slug, 0)
        anchor = slug if ordinal == 0 else f'{slug}-{ordinal}'
        # A repeated heading can collide with a naturally numbered heading.
        while anchor in anchors:
            ordinal += 1
            anchor = f'{slug}-{ordinal}'
        counts[slug] = ordinal + 1
        anchors.add(anchor)
    return anchors


def check_links(root: Path, documents: list[Path]) -> None:
    cache = {}
    for document in documents:
        content = prose(document.read_text())
        destinations = re.findall(r'\]\(([^)]+)\)', content)
        destinations += re.findall(r'<img\b[^>]*\bsrc=[\"\']([^\"\']+)', content)
        for destination in destinations:
            dest = destination.strip()
            if '://' in dest or dest.startswith(('mailto:', 'data:')):
                continue
            # Markdown destinations may use angle brackets and an optional title.
            dest = dest[1:dest.index('>')] if dest.startswith('<') else dest.split(' "', 1)[0]
            path, _, fragment = dest.partition('#')
            target = (document.parent / unquote(path)).resolve() if path else document.resolve()
            label = f'{document.relative_to(root)}: {destination}'
            if not target.exists():
                raise ValueError(f'Broken documentation link: {label}')
            if fragment and target.suffix == '.md':
                if target not in cache:
                    cache[target] = heading_anchors(target.read_text())
                anchors = cache[target]
                if unquote(fragment) not in anchors:
                    raise ValueError(f'Broken documentation heading: {label}')


def check_map(root: Path, documents: list[Path]) -> None:
    manifest = json.loads((root / 'docs/documentation-map.json').read_text())
    if manifest.get('schemaVersion') != 1:
        raise ValueError('Unsupported documentation map version')
    classified = []
    for role, paths in manifest['documents'].items():
        if not paths or not isinstance(paths, list):
            raise ValueError(f'Empty documentation role: {role}')
        classified.extend(paths)
    if len(classified) != len(set(classified)):
        raise ValueError('Document classified more than once')
    maintained = {str(p.relative_to(root)) for p in documents
                  if not p.is_relative_to(root / 'docs/evidence')}
    if set(classified) != maintained:
        raise ValueError(f'Documentation map coverage mismatch: {sorted(set(classified) ^ maintained)}')
    for name, path in manifest['entrypoints'].items():
        if path not in classified:
            raise ValueError(f'Unclassified documentation entrypoint: {name}')
    for area, paths in manifest['sourceAreas'].items():
        for path in paths:
            if not (root / path).exists():
                raise ValueError(f'Stale AI context path: {area}: {path}')
    for document in documents:
        if document.is_relative_to(root / 'docs/evidence') and document.name != 'README.md':
            content = document.read_text()
            if '> Historical evidence.' not in content or '../current-state.md' not in content or '../backlog.md' not in content:
                raise ValueError(f'Missing historical scope banner: {document.relative_to(root)}')
    backlog = prose((root / manifest['entrypoints']['backlog']).read_text())
    ids = re.findall(r'^\| (' + TASK_ID + r') \|', backlog, re.MULTILINE)
    if not ids or len(ids) != len(set(ids)):
        raise ValueError('Missing or duplicate backlog task IDs')
    roadmap = prose((root / manifest['entrypoints']['roadmap']).read_text())
    unknown = set(re.findall(TASK_ID, roadmap)) - set(ids)
    if unknown:
        raise ValueError(f'Roadmap references undefined backlog IDs: {sorted(unknown)}')


def check_documentation(root: Path) -> int:
    documents = authored_documents(root)
    check_links(root, documents)
    check_map(root, documents)
    return len(documents)

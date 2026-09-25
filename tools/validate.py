"""Validate draft contract shapes, semantic examples, failure cases and local docs.

This is design tooling, not the firmware validator or a production decoder.
"""
from __future__ import annotations
import copy
import json
import math
import re
from pathlib import Path
from urllib.parse import unquote
from jsonschema import Draft202012Validator, ValidationError
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
schemas = {p.name: json.loads(p.read_text()) for p in (ROOT / 'contracts').glob('*.schema.json')}
registry = Registry().with_resources((x['$id'], Resource.from_contents(x)) for x in schemas.values())
for schema in schemas.values():
    Draft202012Validator.check_schema(schema)

def shape(name, doc):
    Draft202012Validator(schemas[name], registry=registry).validate(doc)

def check(ok, message):
    if not ok:
        raise ValueError(message)

def decode(definition, payload):
    d = definition['decoder']
    raw = bytes.fromhex(payload)
    end = d['byteOffset'] + d['byteLength']
    check(len(raw) >= definition['response']['minPayloadBytes'] and end <= len(raw), 'Short payload')
    value = int.from_bytes(raw[d['byteOffset']:end], d['endian'], signed=d['signed'])
    value = value * d['numerator'] / d['denominator'] + d['offset']
    check(math.isfinite(value), 'Nonfinite decoded value')
    check(definition['range']['min'] <= value <= definition['range']['max'], 'Decoded value out of range')
    return value

def pid(doc):
    shape('pid-definition.schema.json', doc)
    service = doc['request']['service']
    identifier = doc['request']['identifier']
    check(len(identifier) == (2 if service == '01' else 4), 'Identifier width')
    check(doc['response']['prefix'] == ('41' if service == '01' else '62') + identifier, 'Response prefix mismatch')
    check(doc['range']['min'] < doc['range']['max'], 'PID range order')
    check(doc['decoder']['byteOffset'] + doc['decoder']['byteLength'] <= doc['response']['minPayloadBytes'], 'Extraction exceeds declared minimum')
    check(doc['staleAfterMs'] >= doc['pollIntervalMs'], 'Stale interval below poll interval')
    check((doc['request']['route'] == 'can11_physical') == ('requestId' in doc['request']), 'Physical route needs request ID; functional route must not override header')
    for vector in doc['vectors']:
        check(math.isclose(decode(doc, vector['payloadHex']), vector['expected'], rel_tol=1e-9, abs_tol=1e-9), 'Decode vector mismatch')

def unique(items, key):
    check(len({x[key] for x in items}) == len(items), 'Duplicate ' + key)

def config(doc):
    shape('config.schema.json', doc)
    check(len(json.dumps(doc, separators=(',', ':')).encode()) <= 65536, 'Config exceeds 64 KiB')
    unique(doc['sources'], 'id'); unique(doc['definitions'], 'id'); unique(doc['pages'], 'id'); unique(doc['alerts'], 'id')
    sources = {x['id'] for x in doc['sources']}
    defs = {x['id']: x for x in doc['definitions']}
    physical_queries = set()
    for d in defs.values():
        pid(d)
        check(d['sourceId'] in sources, 'Unknown adapter source')
        q = (d['sourceId'], d['request']['responseId'], d['request']['service'], d['request']['identifier'])
        check(q not in physical_queries, 'Duplicate physical query definition')
        physical_queries.add(q)
    for page in doc['pages']:
        check(len(page['pidIds']) == (2 if page['renderer'] == 'dual' else 1), 'Renderer channel count')
        check(all(x in defs for x in page['pidIds']), 'Page references unknown PID')
    for alert in doc['alerts']:
        check(alert['pidId'] in defs, 'Alert references unknown PID')
        d = defs[alert['pidId']]
        levels = [alert[k] for k in ('warning', 'critical') if k in alert]
        check(all(d['range']['min'] <= v <= d['range']['max'] for v in levels), 'Alert threshold outside PID range')
        if len(levels) == 2:
            check((levels[0] < levels[1]) if alert['direction'] == 'above' else (levels[0] > levels[1]), 'Alert severity order')
        for level in levels:
            release = level - alert['hysteresis'] if alert['direction'] == 'above' else level + alert['hysteresis']
            check(d['range']['min'] <= release <= d['range']['max'], 'Alert release boundary outside PID range')

def release(doc):
    shape('release-manifest.schema.json', doc)
    check(doc['configSchemaMin'] <= doc['configSchemaMax'], 'Config compatibility order')

count = 0
for p in (ROOT / 'contracts/examples').glob('*.json'):
    d = json.loads(p.read_text())
    (pid if p.name.startswith('pid-') else config if p.name.startswith('config') else release)(d)
    count += 1

base = json.loads((ROOT / 'contracts/examples/config-dual-source.json').read_text())
negative_cases = [
    ('undefined source', lambda d: d['definitions'][0].update(sourceId='missing')),
    ('duplicate source', lambda d: d['sources'].append(copy.deepcopy(d['sources'][0]))),
    ('bad page reference', lambda d: d['pages'][0].update(pidIds=['missing.pid'])),
    ('single view with two values', lambda d: d['pages'][0].update(pidIds=['engine.rpm', 'vehicle.speed'])),
    ('invalid alert order', lambda d: d['alerts'][0].update(warning=120, critical=100)),
    ('alert release range', lambda d: d['alerts'][0].update(hysteresis=1000)),
    ('zero divisor', lambda d: d['definitions'][0]['decoder'].update(denominator=0)),
    ('short payload', lambda d: d['definitions'][0]['vectors'][0].update(payloadHex='1A')),
    ('wrong decode expectation', lambda d: d['definitions'][0]['vectors'][0].update(expected=5)),
    ('bad extraction', lambda d: d['definitions'][0]['decoder'].update(byteOffset=4095)),
    ('write service injected as PID', lambda d: d['definitions'][0]['request'].update(service='04')),
    ('incorrect positive prefix', lambda d: d['definitions'][0]['response'].update(prefix='62000C')),
    ('physical route lacks header', lambda d: d['definitions'][0]['request'].update(route='can11_physical')),
    ('unbounded formula field', lambda d: d['definitions'][0]['decoder'].update(expression='eval(input)')),
    ('duplicate query same source', lambda d: d['definitions'][-1].update(sourceId='ecm')),
]
for label, mutate in negative_cases:
    candidate = copy.deepcopy(base); mutate(candidate)
    try:
        config(candidate)
    except (ValueError, ValidationError):
        pass
    else:
        raise AssertionError(f'Invalid case accepted: {label}')

# Independent signed/little-endian decoder vector.
signed = copy.deepcopy(base['definitions'][0])
signed['decoder'].update(endian='little', signed=True, denominator=1)
signed['range'] = {'min': -32768, 'max': 32767}
check(decode(signed, 'FEFF') == -2, 'Signed little-endian decoding')

# Verify authored Markdown targets. External URLs are intentionally not network-tested.
mds = [ROOT / 'README.md', ROOT / 'CONTRIBUTING.md', ROOT / 'THIRD_PARTY_NOTICES.md']
mds += list((ROOT / 'docs').rglob('*.md')) + [ROOT / 'android/README.md', ROOT / 'firmware/gauge/README.md']
for p in mds:
    for dest in re.findall(r'\]\(([^)]+)\)', p.read_text()):
        if '://' in dest or dest.startswith(('#', 'mailto:')):
            continue
        target = unquote(dest.split('#')[0])
        check((p.parent / target).exists(), f'Broken link in {p.relative_to(ROOT)}: {dest}')

# Prototype colors are a checked consumer of the design tokens until codegen exists.
tokens = json.loads((ROOT / 'design/tokens.json').read_text())
css = (ROOT / 'design/prototype/styles.css').read_text()
for name, color in tokens['color'].items():
    match = re.search(r'--' + re.escape(name) + r'\s*:\s*(#[0-9a-fA-F]{6})', css)
    check(match and match.group(1).lower() == color.lower(), f'Prototype token mismatch: {name}')
print(f'PASS: {len(schemas)} schemas, {count} examples, {len(negative_cases)} rejection cases, signed decode vector, {len(mds)} document link sets, color token parity.')

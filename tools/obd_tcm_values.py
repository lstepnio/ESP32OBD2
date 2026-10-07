"""Fixed read-only TCM capture and offline report. No inferred pressure units.

Baseline comes from the owner's captures, not a donor-model assumption.
225034 is an OBDb Challenger candidate at d2d9fda, not qualified pressure.
"""
import argparse
import asyncio
import json
from pathlib import Path
import re
import subprocess
import time

from obd_capture import AdapterSession, parser_binary
from obd_capture_core import supports
from obd_tcm_gear_candidate import decode_candidate as decode_gear
from obd_tcm_temperature_candidate import decode_candidate as decode_temperature

STATES = ('off-P', 'idle-P', 'idle-R', 'idle-N', 'idle-D', 'drive')
STANDARD = ('0101', '010C', '010D', '0142')
FIXED = ('2204FE', '225503', '225504', '225034', '03', '07', '0A')
BASELINE = {'calibration': '68274867AE', 'cvn': '8240DAE8',
            'name': 'TCM - TransmisCtrl'}


class BoundedSession(AdapterSession):
    """Bound discovery plus polling, leaving restoration to the caller's finally."""
    deadline = None

    async def request(self, command, timeout=5):
        remaining = self.deadline - time.monotonic() if self.deadline else 5
        if remaining <= 0:
            raise ValueError('TCM capture time budget exhausted')
        limit = min(timeout, 5, remaining)
        try:
            return await asyncio.wait_for(super().request(command, timeout=limit), limit)
        except asyncio.TimeoutError as error:
            if self.recording:
                self.recording.event('timeout')
            raise ValueError(f'{command} exceeded the TCM request budget; stopped') from error


def payload(raw, command):
    """One bounded headered 7E9 ISO-TP message, including Mode 09 identity."""
    if len(raw) > 4096 or not raw.endswith(b'>') or raw.count(b'>') != 1:
        raise ValueError('Incomplete or oversized TCM reply')
    lines = [re.sub(r'[ \t]', '', line).upper() for line in
             re.split(r'[\r\n>]', raw.decode('ascii'))]
    lines = [line for line in lines if line and line not in (command, 'SEARCHING...')]
    if lines == ['NODATA']:
        return None
    frames = []
    for line in lines:
        if not re.fullmatch(r'7E9(?:[0-9A-F]{2}){2,8}', line):
            raise ValueError('Wrong ECU or malformed TCM reply')
        frames.append(bytes.fromhex(line[3:]))
    if not frames:
        raise ValueError('Empty TCM reply')
    first = frames[0]
    if first[0] >> 4 == 0:
        length = first[0]
        if not 1 <= length <= 7 or len(first) < length + 1 or len(frames) != 1:
            raise ValueError('Invalid or duplicate single frame')
        return first[1:length + 1]
    if first[0] >> 4 != 1 or len(first) != 8:
        raise ValueError('Invalid ISO-TP first frame')
    length = ((first[0] & 15) << 8) | first[1]
    if not 8 <= length <= 64:
        raise ValueError('ISO-TP payload outside bounds')
    data = bytearray(first[2:])
    for index, frame in enumerate(frames[1:], 1):
        if len(data) >= length or frame[0] != 0x20 | (index & 15):
            raise ValueError('Duplicate or misordered continuation')
        needed = min(7, length - len(data))
        if len(frame) < needed + 1:
            raise ValueError('Short ISO-TP continuation')
        data.extend(frame[1:needed + 1])
    if len(data) != length:
        raise ValueError('Incomplete ISO-TP message')
    return bytes(data)


def verify_identity(identity):
    values = {}
    for command, key, length in (('0904', 'calibration', 16),
                                 ('0906', 'cvn', 4), ('090A', 'name', 20)):
        body = payload(bytes.fromhex(identity.get(command, '')), command)
        prefix = bytes.fromhex('49' + command[2:] + '01')
        if body is None or len(body) != 3 + length or not body.startswith(prefix):
            raise ValueError('Missing or malformed TCM identity: ' + command)
        values[key] = (body[3:].hex().upper() if key == 'cvn' else
                       body[3:].decode('ascii').replace('\x00', ' ').strip())
        if key == 'name':
            values[key] = re.sub(r'\s*-\s*', ' - ', values[key])
    if values != BASELINE:
        raise ValueError('TCM identity differs from the captured baseline; stopped')
    return values


def decode(raw, command):
    if command not in (*STANDARD, *FIXED):
        raise ValueError('Unlisted TCM value request')
    body = payload(raw, command)
    if body is None:
        return {'status': 'no_data'}
    service = int(command[:2], 16)
    if len(body) == 3 and body[:2] == bytes((0x7F, service)):
        return {'status': 'negative_response', 'nrc': f'{body[2]:02X}'}
    if command in ('225503', '225504'):
        result = decode_gear(raw, command)
        value = result['raw_value']
        if value not in (0, *range(1, 9), 11, 13):
            return {'status': 'invalid_value', 'raw_value': value}
        return result
    if command == '2204FE':
        return decode_temperature(raw, command)
    if command == '225034':
        if len(body) != 5 or body[:3] != b'\x62\x50\x34':
            raise ValueError('Unexpected pressure candidate prefix/length')
        return {'status': 'candidate_only', 'qualified': False,
                'payload_hex': body[3:].hex().upper(),
                'raw_unsigned': int.from_bytes(body[3:], 'big'),
                'meaning': 'Unlabelled 5034 candidate; pressure scaling unverified'}
    if command in ('03', '07', '0A'):
        # Reuse the firmware's count-aware, source-isolated DTC assembler.
        parsed = json.loads(subprocess.run(
            [str(parser_binary()), command, '00', raw.hex(), '7E9'],
            text=True, capture_output=True, check=True, timeout=5).stdout)
        if parsed['status'] != 1:
            raise ValueError('Production DTC parser rejected response')
        pairs = bytes.fromhex(parsed['payload'])
        codes = []
        for i in range(0, len(pairs), 2):
            a, b = pairs[i:i + 2]
            codes.append(f'{"PCBU"[a >> 6]}{(a >> 4) & 3}{a & 15:X}{b:02X}')
        return {'status': 'accepted', 'codes': codes,
                'category': {'03': 'stored', '07': 'pending', '0A': 'permanent'}[command]}
    length = {'0101': 4, '010C': 2, '010D': 1, '0142': 2}[command]
    if len(body) != length + 2 or body[:2] != bytes.fromhex('41' + command[2:]):
        raise ValueError('Unexpected standard value prefix/length')
    data = body[2:]
    fields = ({'mil_reported': bool(data[0] & 128), 'reported_dtc_count': data[0] & 127,
               'uninterpreted_readiness_hex': data[1:].hex().upper()} if command == '0101' else
              {'engine_rpm': int.from_bytes(data, 'big') / 4} if command == '010C' else
              {'vehicle_speed_kph': data[0]} if command == '010D' else
              {'ecu_voltage': int.from_bytes(data, 'big') / 1000})
    return {'status': 'accepted', **fields}


def summarize(result):
    grouped = {}
    for sample in result['samples']:
        group = grouped.setdefault(sample['request'], {'samples': 0, 'statuses': {},
                                                      'latencies_ms': [], 'values': []})
        group['samples'] += 1
        status = sample['status']
        group['statuses'][status] = group['statuses'].get(status, 0) + 1
        group['latencies_ms'].append(sample['latency_ms'])
        value = {k: v for k, v in sample.items() if k not in
                 ('raw_hex', 'request', 't_ms', 'latency_ms', 'status')}
        if value not in group['values']:
            group['values'].append(value)
    for group in grouped.values():
        times = group.pop('latencies_ms')
        group['latency_ms'] = {'min': min(times), 'max': max(times),
                              'mean': round(sum(times) / len(times), 2)}
    return {'evidence': 'derived_from_recording', 'owner_reported_state': result['owner_reported_state'],
            'identity': result['verified_identity'], 'requests': grouped,
            'skipped': result['skipped'], 'stop_reason': result.get('stop_reason'),
            'limit': 'Support/state correlation only; new sensor meanings remain unqualified.'}


async def probe(session, result, maps, duration, *, clock=None, sleep=None):
    """One state per invocation; <=2 reads/s overall and no pressure retry after rejection."""
    if result.get('owner_reported_state') not in STATES or not 3 <= duration <= 120:
        raise ValueError('TCM capture requires a valid state and bounded duration')
    clock = clock or time.monotonic
    sleep = sleep or asyncio.sleep
    start = clock()
    deadline = start + duration
    advertised = [command for command in STANDARD if supports(maps, int(command[2:], 16))]
    result['skipped'] = {command: 'not_advertised_by_7E9' for command in STANDARD if command not in advertised}
    initial = [*FIXED, *advertised, '225034']
    if result['owner_reported_state'] == 'drive':
        parked_only = ('225034', '03', '07', '0A', '0101')
        initial = [command for command in initial if command not in parked_only]
        advertised = [command for command in advertised if command not in parked_only]
        result['skipped'].update({command: 'parked_test_only' for command in parked_only})
    cycle = ['225503', '225504', '2204FE', *advertised]
    retired = set()
    next_send = start
    index = 0
    while True:
        if index < len(initial):
            command = initial[index]
        else:
            command = cycle[(index - len(initial)) % len(cycle)]
        index += 1
        if command in retired:
            if clock() >= deadline or all(c in retired for c in cycle):
                break
            continue
        await sleep(max(0, next_send - clock()))
        remaining = deadline - clock()
        if remaining <= 0:
            break
        sent = clock()
        next_send = sent + .5
        sample = {'request': command, 't_ms': round((sent - start) * 1000, 2)}
        try:
            raw = await session.request(command, timeout=min(5, remaining))
            sample['raw_hex'] = raw.hex()
            sample['reply_bytes'] = len(raw)
            try:
                body = payload(raw, command)
                sample['payload_bytes'] = len(body) if body is not None else 0
                sample.update(decode(raw, command))
            except (ValueError, UnicodeError) as error:
                sample.update(status='rejected', reason=str(error))
            if sample['status'] in ('negative_response', 'no_data', 'rejected', 'invalid_value'):
                retired.add(command)
                result['skipped'][command] = sample['status']
        except (ValueError, OSError) as error:
            sample.update(status='transport_error', reason=str(error))
            result['stop_reason'] = str(error)
        sample['latency_ms'] = round((clock() - sent) * 1000, 2)
        result['samples'].append(sample)
        if 'stop_reason' in result:
            break


def main():
    parser = argparse.ArgumentParser(description='Offline summary of a combined TCM exploration.json')
    parser.add_argument('recording', type=Path, nargs='+')
    args = parser.parse_args()
    reports = []
    for path in args.recording:
        record = json.loads(path.read_text())
        report = summarize(record['tcm_values'])
        report['adapter_restored'] = record.get('adapter_restored', False)
        reports.append(report)
    print(json.dumps(reports[0] if len(reports) == 1 else {'sessions': reports}, indent=2))


if __name__ == '__main__':
    main()

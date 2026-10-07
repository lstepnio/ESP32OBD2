"""Bounded FCA gear candidates. Published meanings are unqualified for JSS.

OBDb/Dodge-Challenger, revision df9d74e070b7bb38dda080d20780027331bc6c86,
signalsets/v3/default.json and 2019/2021 225503/225504 response fixtures.
These describe current/target gear, not a validated selector-position request.
"""
import re

COMMANDS = ('223C22', '225503', '225504')
POSITIONS = ('P', 'R', 'N', 'D')
GEARS = {0: 'N', **{i: str(i) for i in range(1, 11)}, 11: 'R', 13: 'P'}


def decode_candidate(raw, command):
    if command not in COMMANDS:
        raise ValueError('Unlisted gear candidate')
    if len(raw) > 4096 or not raw.endswith(b'>') or raw.count(b'>') != 1:
        raise ValueError('Incomplete or oversized gear transaction')
    try:
        lines = [re.sub(r'[ \t]', '', line).upper() for line in
                 re.split(r'[\r\n>]', raw.decode('ascii'))]
    except UnicodeDecodeError as error:
        raise ValueError('Non-ASCII gear response') from error
    lines = [line for line in lines if line and line not in (command, 'SEARCHING...')]
    if lines == ['NODATA']:
        return {'status': 'no_data', 'qualified': False}
    if len(lines) != 1 or not re.fullmatch(r'7E9(?:[0-9A-F]{2}){2,8}', lines[0]):
        raise ValueError('Wrong source, ambiguous or malformed gear response')
    frame = bytes.fromhex(lines[0][3:])
    length = frame[0]
    if not 1 <= length <= 7 or len(frame) < length + 1:
        raise ValueError('Truncated or unsupported ISO-TP gear frame')
    payload = frame[1:length + 1]
    if len(payload) == 3 and payload[:2] == b'\x7f\x22':
        return {'status': 'negative_response', 'nrc': f'{payload[2]:02X}', 'qualified': False}
    if len(payload) != 4 or payload[:3] != b'\x62' + bytes.fromhex(command[2:]):
        raise ValueError('Unexpected gear prefix or payload length')
    value = payload[3]
    result = {'status': 'candidate_only', 'qualified': False, 'responder': '7E9',
              'raw_value': value, 'payload_hex': payload[3:].hex().upper()}
    if command == '223C22':
        result['published_scalar'] = value / 10
    else:
        # OBDb bix=4,len=4 identifies the low nibble. Preserve the high bits.
        result.update(published_gear=GEARS.get(value & 15, 'UNKNOWN'),
                      uninterpreted_high_nibble=value >> 4,
                      published_kind='current' if command == '225503' else 'target')
    return result

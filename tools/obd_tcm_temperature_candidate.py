"""Fixed published TCM temperature candidates, not qualified for JSS firmware.

Source: https://www.scangauge.com/xgauge/3-0l-ecodiesel-transmission-temperature/
TXD 07E12208DF, RXD 3008, MTH 00090005FFD8: A*9/5-40 F, A-40 C.
The documented application is EcoDiesel, not this Hemi swap.

225043: OBDb/Dodge-Challenger definition and 2021 response fixtures at
revision d2d9fda9119e1c61a806e32c657415e8e3ffc0bb, same A-40 C scaling.
"""
import re

COMMAND = '2208DF'
REQUEST_ID = '7E1'
RESPONSE_ID = '7E9'
COMMANDS = frozenset(('2208DF', '225043', '2204FE'))


def decode_candidate(raw, command=COMMAND):
    if command not in COMMANDS:
        raise ValueError('Unlisted temperature candidate')
    if len(raw) > 4096 or not raw.endswith(b'>') or raw.count(b'>') != 1:
        raise ValueError('Incomplete or oversized candidate transaction')
    try:
        lines = [re.sub(r'[ \t]', '', line).upper() for line in
                 re.split(r'[\r\n>]', raw.decode('ascii'))]
    except UnicodeDecodeError as error:
        raise ValueError('Non-ASCII candidate response') from error
    lines = [line for line in lines if line and line not in (command, 'SEARCHING...')]
    if lines == ['NODATA']:
        return {'status': 'no_data', 'qualified': False}
    if len(lines) != 1 or not re.fullmatch(r'[0-9A-F]{3}(?:[0-9A-F]{2}){2,8}', lines[0]):
        raise ValueError('Ambiguous or malformed candidate response')
    line = lines[0]
    if line[:3] != RESPONSE_ID:
        raise ValueError('Candidate replied from a different controller')
    frame = bytes.fromhex(line[3:])
    length = frame[0]
    if not 1 <= length <= 7 or len(frame) < length + 1:
        raise ValueError('Truncated or unsupported ISO-TP frame')
    payload = frame[1:length + 1]
    if len(payload) == 3 and payload[:2] == b'\x7f\x22':
        return {'status': 'negative_response', 'nrc': f'{payload[2]:02X}', 'qualified': False}
    expected_length = 6 if command == '2204FE' else 4
    if len(payload) != expected_length or payload[:3] != b'\x62' + bytes.fromhex(command[2:]):
        raise ValueError('Unexpected candidate prefix or payload length')
    value = payload[3]
    result = {'status': 'candidate_only', 'qualified': False, 'responder': RESPONSE_ID,
            'raw_value': value, 'celsius': value - 40, 'fahrenheit': value * 9 / 5 - 40}
    if command == '2204FE':
        # OBDb's 2019 fixtures contain three bytes but qualify only the first.
        # Preserve the other bytes without assigning sensor names or semantics.
        result['uninterpreted_bytes'] = list(payload[4:])
    return result

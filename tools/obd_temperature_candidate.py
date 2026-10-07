"""Host-only interpretation of one documented, unqualified Hemi temperature read.

Source: https://www.scangauge.com/xgauge/transmission-fluid-temperature-f-4/
This is not a production PID definition or proof of the installed sensor's meaning.
"""
import re

COMMAND = '229110'
REQUEST_ID = '7E0'
RESPONSE_ID = '7E8'


def decode_candidate(raw):
    """Require one complete headered CAN single-frame response from the published route."""
    if len(raw) > 4096 or not raw.endswith(b'>') or raw.count(b'>') != 1:
        raise ValueError('Incomplete or oversized candidate transaction')
    try:
        lines = [re.sub(r'[ \t]', '', line).upper() for line in
                 re.split(r'[\r\n>]', raw.decode('ascii'))]
    except UnicodeDecodeError as error:
        raise ValueError('Non-ASCII candidate response') from error
    lines = [line for line in lines if line and line not in (COMMAND, 'SEARCHING...')]
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
    if len(payload) != 5 or payload[:3] != b'\x62\x91\x10':
        raise ValueError('Unexpected candidate prefix or payload length')
    value = int.from_bytes(payload[3:], 'big')
    fahrenheit = value / 64
    celsius = (fahrenheit - 32) * 5 / 9
    # Engineering sanity gate only; vendor sensor invalid-value codes are unknown.
    if not -40 <= celsius <= 215:
        raise ValueError('Candidate outside engineering temperature bounds')
    return {'status': 'candidate_only', 'qualified': False, 'responder': RESPONSE_ID,
            'raw_value': value, 'fahrenheit': fahrenheit, 'celsius': celsius}

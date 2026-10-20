"""Fixed primary-ECM input capture for parked command qualification.

Raw candidates are not runtime guards. This module never sends actuator commands,
diagnostic session entry, DTC clear, security access or tester-present traffic.
"""

import time

from obd_capture_core import controller_payload

STATES = ("ignition-P", "idle-P-brake", "idle-P-released", "idle-N-brake", "idle-D-brake")
BASELINE = {"calibration": "68059434AD", "cvn": "CA36378C"}
# PCM_08_117_TD: candidate only. 0x75 -> 117 is a hypothesis to verify via 1A87.
# Do not decode Pos/F100 into guard truth without labelled vehicle observations.
CANDIDATES = ("2210AC", "2210AE", "2210B2", "2210B4")
STANDARD = ("010C", "010D", "0105")
SETUP = ("ATE0", "ATL0", "ATS0", "ATH1", "ATCAF1", "ATSP0")
ROUTE = ("ATSH7E0", "ATFCSH7E0", "ATCRA7E8")
RESTORE = ("ATCRA", "ATFCSM0", "ATSH7DF", "ATCAF1", "ATTP0")
COMMANDS = frozenset(
    (
        *SETUP,
        *ROUTE,
        *RESTORE,
        *CANDIDATES,
        *STANDARD,
        "ATI",
        "ATDP",
        "ATDPN",
        "0900",
        "0904",
        "0906",
        "090A",
        "1A87",
    )
    + tuple(f"01{base:02X}" for base in range(0, 0xE1, 0x20))
)


def policy(command):
    return command in COMMANDS


def verify_route(protocol_hex, maps):
    selected = bytes.fromhex(protocol_hex).strip(b"\r\n >")
    if selected not in (b"6", b"A6") or not maps or any(set(v) != {"7E8"} for v in maps.values()):
        raise ValueError("Parked action capture requires only primary 7E8 on 11-bit 500 kbit/s CAN")


def verify_identity(identity):
    values = {}
    for command, name, length in (("0904", "calibration", 16), ("0906", "cvn", 4)):
        body = controller_payload(bytes.fromhex(identity.get(command, "")), command, "7E8")
        prefix = bytes.fromhex("49" + command[2:] + "01")
        if body is None or len(body) != 3 + length or not body.startswith(prefix):
            raise ValueError("Missing or malformed primary ECM " + name)
        values[name] = (
            body[3:].hex().upper() if name == "cvn" else body[3:].decode("ascii").rstrip("\x00 ")
        )
    if values != BASELINE:
        raise ValueError("Primary ECM differs from the captured calibration/CVN; stopped")
    return values


def decode(raw, command):
    if command not in (*STANDARD, *CANDIDATES, "1A87"):
        raise ValueError("Unlisted parked input request")
    body = controller_payload(raw, command, "7E8")
    if body is None:
        return {"status": "no_data", "qualified": False}
    service = int(command[:2], 16)
    if len(body) == 3 and body[:2] == bytes((0x7F, service)):
        return {"status": "negative_response", "nrc": f"{body[2]:02X}", "qualified": False}
    prefix = bytes((service + 0x40,)) + bytes.fromhex(command[2:])
    if not body.startswith(prefix) or len(body) <= len(prefix):
        raise ValueError("Wrong parked input service/identifier or empty payload")
    data = body[len(prefix) :]
    if command in STANDARD:
        if len(data) != (2 if command == "010C" else 1):
            raise ValueError("Wrong standard input length")
        name, value = (
            ("rpm", int.from_bytes(data, "big") / 4)
            if command == "010C"
            else ("speed_kph", data[0])
            if command == "010D"
            else ("coolant_c", data[0] - 40)
        )
        return {"status": "accepted", name: value, "qualified": False}
    return {"status": "candidate_only", "payload_hex": data.hex().upper(), "qualified": False}


async def probe(session, result):
    """At most 22 requests; keep time, labels and raw replies independent of meaning."""
    if result.get("owner_reported_state") not in STATES:
        raise ValueError("A parked owner-reported state is required")
    begun = time.monotonic()
    unsupported = set()
    for command in ("1A87", *(*STANDARD, *CANDIDATES) * 3):
        if command in unsupported:
            continue
        started = time.monotonic()
        raw = await session.request(command, timeout=3)
        sample = {
            "request": command,
            "qualified": False,
            "t_ms": round((started - begun) * 1000, 3),
            "raw_hex": raw.hex(),
            "latency_ms": round((time.monotonic() - started) * 1000, 3),
        }
        result["samples"].append(sample)
        sample.update(decode(raw, command))
        if sample["status"] in ("no_data", "negative_response"):
            unsupported.add(command)
        if "speed_kph" in sample and sample["speed_kph"] != 0:
            raise ValueError("Nonzero speed observed; parked capture stopped")

"""One read-only TIPM candidate, not a qualified transfer-case decoder.

The archive has conflicting 2111 state enums at Pos=24. Preserve all bytes and
owner-labelled positions; never infer 2HI/4HI from a TCM High-range indication.
"""

import asyncio
import re

from obd_capture_core import controller_payload

POSITIONS = ("2HI", "4HI", "4LOW", "N")
COMMAND = "2111"
ROUTE = ("620", "504")
ADAPTER_COMMANDS = ("ATSH620", "ATCRA504", "ATCRA", "ATSH7DF")


def decode_candidate(raw):
    body = controller_payload(raw, COMMAND, ROUTE[1])
    if body is None:
        return {"status": "no_data", "qualified": False}
    if len(body) == 3 and body[:2] == b"\x7f\x21":
        return {"status": "negative_response", "nrc": f"{body[2]:02X}", "qualified": False}
    if len(body) < 3 or body[:2] != b"\x61\x11":
        raise ValueError("Unexpected TIPM read response")
    return {
        "status": "candidate_only",
        "qualified": False,
        "responder": ROUTE[1],
        "payload_hex": body[2:].hex().upper(),
        "limit": "No enum or position offset is selected until module/variant and physical states match",
    }


async def _setup(session, commands):
    for command in commands:
        raw = await session.request(command, timeout=2)
        if re.sub(rb"[\r\n >]", b"", raw) != b"OK":
            raise ValueError("Adapter did not confirm " + command)


async def probe(session, result):
    if (
        result.get("owner_reported_position") not in POSITIONS
        or not isinstance(result.get("samples"), list)
        or result["samples"]
    ):
        raise ValueError("A parked, owner-labelled transfer-case position is required")
    try:
        async with asyncio.timeout(20):
            await _setup(session, ADAPTER_COMMANDS[:2])
            for _ in range(3):
                raw = await session.request(COMMAND, timeout=3)
                sample = {"request": COMMAND, "raw_hex": raw.hex(), "qualified": False}
                result["samples"].append(sample)
                sample.update(decode_candidate(raw))
                if sample["status"] != "candidate_only":
                    break
                await asyncio.sleep(0.3)
    finally:
        # An unfinished stream is disconnected by the existing session owner.
        # Do not consume a late reply as an acknowledgement for a cleanup command.
        if not session.waiting_prompt:
            async with asyncio.timeout(4):
                await _setup(session, ADAPTER_COMMANDS[2:])

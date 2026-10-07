"""Pure capture policy and replay framing. No hardware imports or vehicle writes."""
from __future__ import annotations

import re

SCHEMA = 1
STANDARD_PIDS = (0x0C, 0x05, 0x0D, 0x04, 0x0F, 0x11, 0x42, 0x2F)
ADAPTER_COMMANDS = frozenset(("ATI", "ATE0", "ATL0", "ATS0", "ATH0", "ATH1",
                              "ATSP0", "ATDP", "ATDPN"))


def allowed_command(command: str) -> bool:
    """Allow adapter setup/identity plus bounded standard Mode 01 reads only."""
    if command in ADAPTER_COMMANDS:
        return True
    if not re.fullmatch(r"01[0-9A-F]{2}", command):
        return False
    pid = int(command[2:], 16)
    return pid in STANDARD_PIDS or pid in range(0, 0xE1, 0x20)


def support_replies(raw: bytes, base: int, service: int = 1) -> dict[str, int]:
    """Recognize complete headerless or CAN single-frame support replies.

    Other header/length/multiframe formats remain raw evidence. Duplicate
    anonymous responders are not collapsed into one ECU's capability.
    """
    replies: dict[str, int] = {}
    prefix = f"{service + 0x40:02X}{base:02X}"
    for line in re.split(rb"[\r\n>]", raw):
        text = re.sub(r"[ \t]", "", line.decode("ascii", "replace")).upper()
        if not text or not re.fullmatch(r"[0-9A-F]+", text):
            continue
        responder = "headerless"
        body = text
        if not body.startswith(prefix):
            # A CAN identifier has 3 or 8 hex digits. Require a recognized
            # positive payload after it, not a search anywhere in the line.
            width = 3 if len(text) % 2 else 8
            if len(text) <= width:
                continue
            address, body = text[:width], text[width:]
            if int(address, 16) > (0x7FF if width == 3 else 0x1FFFFFFF):
                continue
            responder = address
        if body.startswith("06" + prefix):
            body = body[2:]
        if not body.startswith(prefix) or len(body) != 12:
            continue
        if responder in replies:
            raise ValueError("Ambiguous support reply: duplicate ECU identity")
        replies[responder] = int(body[4:], 16)
    return replies


def supports(bitmaps: dict[int, dict[str, int]], pid: int) -> bool:
    base = ((pid - 1) // 32) * 32
    bit = 1 << (32 - (pid - base))
    return any(bitmap & bit for bitmap in bitmaps.get(base, {}).values())


def validate_record(record: dict) -> None:
    if record.get("schema") != SCHEMA:
        raise ValueError("Unsupported capture schema")
    if record.get("event") == "manifest":
        return
    for key in ("seq", "t_us", "source", "generation", "offset", "total", "status"):
        if type(record.get(key)) is not int:
            raise ValueError(f"Invalid capture field {key}")
    if record["source"] not in (0, 1) or min(record["seq"], record["t_us"],
                                            record["generation"], record["offset"], record["total"]) < 0:
        raise ValueError("Invalid capture identity or length")
    if not re.fullmatch(r"[a-z_]{1,19}", record.get("event", "")):
        raise ValueError("Invalid capture event")
    text = record.get("hex", "")
    if not isinstance(text, str) or len(text) % 2 or not re.fullmatch(r"[0-9a-fA-F]*", text):
        raise ValueError("Invalid capture hex")
    length = len(text) // 2
    if record["total"] > 4096 or record["offset"] + length > record["total"]:
        raise ValueError("Capture fragment outside bounds")


def ordered_records(records: list[dict]) -> list[dict]:
    """Order asynchronously printed firmware records within explicit boot epochs."""
    epochs: list[list[dict]] = [[]]
    for record in records:
        validate_record(record)
        if record["event"] == "manifest":
            continue
        if record["event"] == "trace_loss":
            raise ValueError("Capture lost trace events; repeat the recording")
        if record["event"] == "rx_overflow":
            raise ValueError("Capture RX queue overflow; repeat the recording")
        if record["event"] == "trace_start" and epochs[-1]:
            epochs.append([])
        epochs[-1].append(record)
    ordered = []
    for epoch in epochs:
        epoch.sort(key=lambda item: item["seq"])
        for previous, current in zip(epoch, epoch[1:]):
            if current["seq"] != previous["seq"] + 1:
                raise ValueError("Capture sequence gap or duplicate; replay cannot be trusted")
        ordered.extend(epoch)
    return ordered


def transactions(records: list[dict]) -> list[dict]:
    """Keep each source/generation separate; never assign orphan RX to a query."""
    active: dict[tuple[int, int], dict] = {}
    fragments: dict[tuple, bytearray] = {}
    completed = []
    for record in ordered_records(records):
        event = record["event"]
        source, generation = record["source"], record["generation"]
        key = (source, generation)
        if event == "trace_start":
            active.clear()
            fragments.clear()
        if event in ("disconnected", "tx_failed", "rx_overflow"):
            for old_key in list(active):
                if old_key[0] == source:
                    active.pop(old_key)
        if event not in ("tx", "rx"):
            continue
        fragment_key = (source, generation, event, record["t_us"], record["total"])
        if record["offset"] == 0:
            fragments[fragment_key] = bytearray()
        data = fragments.get(fragment_key)
        if data is None or len(data) != record["offset"]:
            raise ValueError("Capture fragment offset gap")
        data.extend(bytes.fromhex(record["hex"]))
        if len(data) != record["total"]:
            continue
        block = bytes(fragments.pop(fragment_key))
        if event == "tx":
            # A clean generation starts a new session. Old partial replies
            # remain historical and can never become the new command's value.
            for old_key in list(active):
                if old_key[0] == source and old_key != key:
                    active.pop(old_key)
            if key in active:
                raise ValueError("New request before previous prompt")
            active[key] = {"source": source, "generation": generation,
                           "command": block.decode("ascii", "replace").strip(),
                           "t_us": record["t_us"], "raw": bytearray()}
        elif key in active:
            query = active[key]
            query["raw"].extend(block)
            if len(query["raw"]) > 4096:
                raise ValueError("Oversized response")
            if b">" in block:
                # Preserve the first transaction exactly through its prompt;
                # trailing unsolicited bytes cannot feed a later query.
                query["raw"] = bytes(query["raw"]).split(b">", 1)[0] + b">"
                query["latency_us"] = max(0, record["t_us"] - query["t_us"])
                completed.append(query)
                active.pop(key)
    if fragments:
        raise ValueError("Recording ended inside a trace fragment")
    return completed

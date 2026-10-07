#!/usr/bin/env python3
"""Private, read-only Jeep capture from USB gauge or directly from a BLE adapter.

No arbitrary command option, vehicle writes, DTC clear, VIN query, or Mode 22
probe. Raw files stay under ignored artifacts/vehicle-captures by default.
"""
from __future__ import annotations

import argparse
import asyncio
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

from obd_capture_core import (STANDARD_PIDS, allowed_command,
                              support_replies, supports, transactions, validate_record)

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "artifacts" / "vehicle-captures"
PREFIX = "EGAUGE_OBD "
BLE_PROFILES = (
    ("000018f0-0000-1000-8000-00805f9b34fb", "00002af1-0000-1000-8000-00805f9b34fb", "00002af0-0000-1000-8000-00805f9b34fb"),
    ("0000fff0-0000-1000-8000-00805f9b34fb", "0000fff2-0000-1000-8000-00805f9b34fb", "0000fff1-0000-1000-8000-00805f9b34fb"),
)


class Recording:
    def __init__(self, output: Path, source: str, origin: str):
        output.mkdir(parents=True, exist_ok=True, mode=0o700)
        stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
        self.directory = output / f"{stamp}-{source}-{origin}"
        self.directory.mkdir(mode=0o700)
        self.file = self.directory / "capture.jsonl"
        fd = os.open(self.file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        self.stream = os.fdopen(fd, "w", encoding="utf-8")
        self.start = time.monotonic_ns()
        self.sequence = 0
        self.write({"schema": 1, "event": "manifest", "origin": origin,
                    "evidence": "simulated" if origin == "simulated" else "hardware_capture",
                    "environment": "simulated" if origin == "simulated" else "bench" if source == "bench" else "vehicle",
                    "source_label": source, "utc": stamp,
                    "vehicle": None if source == "bench" else "2010 Wrangler / owner-reported 5.7L Hemi + ZFHP70 swap",
                    "adapter": None if source == "bench" else "owner-reported Vgate iCar Pro BLE4.0 / B06XGB4873"})

    def write(self, record: dict):
        if self.stream.closed:
            return  # A late OS disconnect callback cannot reopen a finished capture.
        self.stream.write(json.dumps(record, separators=(",", ":")) + "\n")
        self.stream.flush()

    def event(self, event: str, data: bytes = b"", status: int = 0, generation: int = 1):
        self.write({"schema": 1, "seq": self.sequence,
                    "t_us": (time.monotonic_ns() - self.start) // 1000,
                    "source": 0, "generation": generation, "event": event,
                    "status": status, "offset": 0, "total": len(data), "hex": data.hex()})
        self.sequence += 1

    def close(self):
        self.stream.close()


def save_private(path: Path, value: dict):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def parser_binary() -> Path:
    output = ROOT / "artifacts" / "capture-tools"
    output.mkdir(parents=True, exist_ok=True)
    binary = output / "elm_capture_replay"
    inputs = [ROOT / "firmware/gauge/tests/elm_capture_replay.c",
              ROOT / "firmware/gauge/main/src/elm_response.c",
              ROOT / "firmware/gauge/main/inc/elm_response.h"]
    if not binary.exists() or any(p.stat().st_mtime > binary.stat().st_mtime for p in inputs):
        subprocess.run(["cc", "-std=c11", "-Wall", "-Wextra", "-Werror",
                        "-I" + str(ROOT / "firmware/gauge/main/inc"),
                        str(inputs[0]), str(inputs[1]), "-o", str(binary)], check=True)
    return binary


def replay(path: Path, write_summary: bool = True) -> dict:
    with path.open(encoding="utf-8") as stream:
        records = [json.loads(line) for line in stream if line.strip()]
    frames = transactions(records)
    binary = parser_binary()
    results = []
    for frame in frames:
        command = frame["command"]
        item = {key: value for key, value in frame.items() if key != "raw"}
        item["raw_hex"] = frame["raw"].hex()
        if re.fullmatch(r"01[0-9A-Fa-f]{2}", command) or command.upper() in ("03", "07", "0A"):
            response = subprocess.run([str(binary), command[:2], command[2:] or "00", item["raw_hex"]],
                                      text=True, capture_output=True, check=True)
            item["production_parser"] = json.loads(response.stdout)
        results.append(item)
    simulated = any(r.get("event") == "source_simulated" or
                    r.get("event") == "manifest" and r.get("evidence") == "simulated" for r in records)
    summary = {"evidence": "simulated" if simulated else "hardware_capture",
               "complete_transactions": len(results), "records": len(records),
               "mode01_accepted": sum(r["command"].startswith("01") and r.get("production_parser", {}).get("status") == 1 for r in results),
               "service_reads_accepted": sum(r["command"] in ("03", "07", "0A") and r.get("production_parser", {}).get("status") == 1 for r in results),
               "timeouts": sum(r.get("event") == "timeout" for r in records),
               "failed_connections": sum(r.get("event") == "connect_failed" for r in records),
               "incomplete_or_aborted_queries": sum(r.get("event") == "tx" and r.get("offset") == 0 for r in records) - len(results),
               "transactions": results,
               "limit": "Production parser accepts headerless or CAN single-frame Mode 01 and bounded emissions replies; multiple responders and ISO-TP multi-frame replies fail closed."}
    if write_summary:
        target = path.parent / "replay-summary.json"
        # Re-running replay deliberately replaces only its derived summary.
        target.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        target.chmod(0o600)
    return summary


def print_summary(path: Path):
    summary = replay(path)
    print(f"Saved {summary['complete_transactions']} complete transactions; "
          f"{summary['mode01_accepted']} accepted by the firmware parser.")
    print(f"Private recording: {path}")


def record_serial(args):
    import serial
    recording = Recording(args.output, args.source, "gauge_usb")
    fd = os.open(recording.directory / "serial.log", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    count = 0
    malformed = 0
    try:
        # Opening a USB serial device may reset some boards. A fresh boot is
        # acceptable, but no bootloader command or flash operation is sent.
        device = serial.Serial(port=None, baudrate=115200, timeout=0.25)
        device.dtr = False
        device.rts = False
        device.port = args.port
        with os.fdopen(fd, "wb") as log, device:
            deadline = time.monotonic() + args.duration
            print(f"Recording gauge USB for {args.duration:g} seconds. Ctrl-C stops and saves.")
            while time.monotonic() < deadline:
                raw = device.readline(4096)
                if not raw:
                    continue
                log.write(raw)
                log.flush()
                line = raw.decode("utf-8", "replace")
                if PREFIX not in line:
                    continue
                try:
                    event = json.loads(line.split(PREFIX, 1)[1])
                    validate_record(event)
                except (ValueError, TypeError):
                    malformed += 1
                    continue
                event["host_t_us"] = (time.monotonic_ns() - recording.start) // 1000
                recording.write(event)
                count += 1
    except KeyboardInterrupt:
        print("Capture stopped; saving.")
    finally:
        recording.close()
    if malformed:
        raise ValueError(f"{malformed} damaged trace lines; repeat capture. Raw log: {recording.directory}")
    if not count:
        raise ValueError(f"No trace events received. Check capture firmware/USB. Log: {recording.directory}")
    print_summary(recording.file)


async def scan_adapters() -> list:
    from bleak import BleakScanner
    found = await BleakScanner.discover(timeout=8, return_adv=True)
    result = []
    for device, adv in found.values():
        name = adv.local_name or device.name or ""
        services = {x.lower() for x in adv.service_uuids}
        if any(word in name.lower() for word in ("vgate", "vlink", "icar", "obd")) or any(p[0] in services for p in BLE_PROFILES):
            result.append((device, adv))
    return result


async def choose_adapter(address: str | None):
    from bleak import BleakScanner
    if address:
        device = await BleakScanner.find_device_by_address(address, timeout=8)
        if device is None:
            raise ValueError("Selected adapter is not advertising. Check its power and close other OBD apps.")
        return device
    candidates = await scan_adapters()
    if len(candidates) != 1:
        for index, (device, adv) in enumerate(candidates, 1):
            print(f"{index}: {adv.local_name or device.name} ({device.address}), RSSI {adv.rssi}")
        raise ValueError("Expected one adapter; use scan, then pass its observed identifier with --adapter.")
    return candidates[0][0]


class AdapterSession:
    def __init__(self, recording: Recording, policy=allowed_command):
        self.recording = recording
        self.policy = policy
        self.rx = asyncio.Queue(maxsize=128)
        self.rx_lost = False
        self.waiting_prompt = False
        self.client = None
        self.tx = None
        self.response = False

    def notification(self, _characteristic, value: bytearray):
        raw = bytes(value)
        self.recording.event("rx", raw)
        try:
            self.rx.put_nowait(raw)
        except asyncio.QueueFull:
            self.rx_lost = True
            self.recording.event("rx_overflow", status=1)

    async def request(self, command: str, timeout: float = 8) -> bytes:
        if not self.policy(command):
            raise ValueError("Request rejected by the read-only capture policy")
        if self.waiting_prompt:
            raise ValueError("Previous request has no prompt; reconnect before another request")
        # No request may consume unsolicited bytes queued before it was sent.
        while not self.rx.empty():
            self.rx.get_nowait()
        raw = command.encode("ascii") + b"\r"
        self.recording.event("tx", raw)
        self.waiting_prompt = True
        try:
            await self.client.write_gatt_char(self.tx, raw, response=self.response)
        except Exception:
            self.recording.event("tx_failed", status=1)
            raise
        frame = bytearray()
        deadline = time.monotonic() + timeout
        try:
            while time.monotonic() < deadline:
                data = await asyncio.wait_for(self.rx.get(), max(0.001, deadline - time.monotonic()))
                frame.extend(data)
                if len(frame) > 4096 or self.rx_lost:
                    raise ValueError("Adapter response overflow; capture stopped")
                if b">" in data:
                    self.waiting_prompt = False
                    self.recording.event("prompt")
                    return bytes(frame).split(b">", 1)[0] + b">"
        except asyncio.TimeoutError:
            pass
        self.recording.event("timeout")
        raise ValueError(f"{command} timed out; capture saved. Reconnect before retrying.")


async def capture_ble(args):
    from bleak import BleakClient
    device = await choose_adapter(args.adapter)
    recording = Recording(args.output, args.source, "simulated" if getattr(args, "simulated", False) else "mac_ble")
    session = AdapterSession(recording)
    try:
        async with BleakClient(device, timeout=15,
                               disconnected_callback=lambda _: recording.event("disconnected", generation=2)) as client:
            session.client = client
            gatt = {"name": device.name, "identifier": device.address, "services": [
                {"uuid": service.uuid, "handle": service.handle,
                 "characteristics": [{"uuid": char.uuid, "handle": char.handle,
                                       "properties": char.properties,
                                       "descriptors": [{"uuid": d.uuid, "handle": d.handle} for d in char.descriptors]}
                                      for char in service.characteristics]} for service in client.services]}
            save_private(recording.directory / "gatt.json", gatt)
            profile = None
            for service_uuid, tx_uuid, rx_uuid in BLE_PROFILES:
                service = client.services.get_service(service_uuid)
                if service:
                    tx = service.get_characteristic(tx_uuid)
                    rx = service.get_characteristic(rx_uuid)
                    if tx and rx and "notify" in rx.properties and any(p in tx.properties for p in ("write", "write-without-response")):
                        profile = (tx, rx)
                        break
            if not profile:
                raise ValueError("GATT map saved, but no implemented UART driver matches. Add a driver from this evidence before sending commands.")
            session.tx, rx = profile
            session.response = "write" in session.tx.properties
            await client.start_notify(rx, session.notification)
            recording.event("link_ready")
            print("Connected to the adapter; recording read-only requests.")
            for command in ("ATI", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0"):
                reply = await session.request(command)
                if any(error in reply.upper() for error in (b"?", b"ERROR", b"UNABLE TO CONNECT")):
                    raise ValueError(f"Adapter rejected {command}; saved its response and stopped.")
            bitmaps = {}
            for base in range(0, 0xE1, 0x20):
                reply = await session.request(f"01{base:02X}", timeout=12)
                evidence = support_replies(reply, base)
                bitmaps[base] = evidence
                if not any(bits & 1 for bits in evidence.values()):
                    break
            for command in ("ATDP", "ATDPN"):
                await session.request(command)
            save_private(recording.directory / "discovery.json", {
                "source": args.source, "bitmaps": bitmaps,
                "limit": "Only recognized support replies. Missing or unrecognized replies do not establish unsupported readings."})
            pids = [pid for pid in STANDARD_PIDS if supports(bitmaps, pid)]
            if args.source == "engine" and pids:
                end = time.monotonic() + args.duration
                while time.monotonic() < end:
                    for pid in pids:
                        if time.monotonic() >= end:
                            break
                        await session.request(f"01{pid:02X}", timeout=5)
                        await asyncio.sleep(0.25)
                # Capture a small headerless baseline for the current production
                # parser after preserving the richer responder evidence above.
                await session.request("ATH0")
                for pid in (0x0C, 0x05):
                    if pid in pids:
                        await session.request(f"01{pid:02X}")
            elif args.source == "transmission":
                print("Transmission port identity and standard support captured. Enhanced reads await a documented controller definition.")
            await client.stop_notify(rx)
    finally:
        recording.close()
        print(f"Private capture retained: {recording.file}")
    print_summary(recording.file)


def self_test(args):
    recording = Recording(args.output, "engine", "simulated")
    recording.event("trace_start", generation=0)
    for command, blocks in ((b"010C\r", (b"010C\r41", b"0C1A", b"F8\r", b">")),
                            (b"0105\r", (b"41057B\r>",)),
                            (b"010D\r", (b"NO DATA\r>",)),
                            (b"03\r", (b"4301330000\r>",))):
        recording.event("tx", command)
        for block in blocks:
            recording.event("rx", block)
        recording.event("prompt")
    recording.close()
    summary = replay(recording.file)
    results = [r["production_parser"] for r in summary["transactions"]]
    if results != [{"status": 1, "payload": "1af8"}, {"status": 1, "payload": "7b"}, {"status": 2, "payload": ""}, {"status": 1, "payload": "01330000"}]:
        raise ValueError(f"Replay mismatch: {results}")
    print("Recorder and production firmware parser replay passed for split RPM, coolant, NO DATA, and stored fault reads.")
    print(f"Simulated fixture: {recording.file}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("scan", help="List nearby candidate adapters; sends no commands")
    for name in ("serial", "ble", "self-test"):
        p = sub.add_parser(name)
        p.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
        if name != "self-test":
            p.add_argument("--source", choices=("engine", "transmission", "bench") if name == "serial" else ("engine", "transmission"), required=True)
            p.add_argument("--duration", type=float, default=60)
        if name == "serial":
            p.add_argument("--port", required=True)
        elif name == "ble":
            p.add_argument("--adapter", help="Observed macOS BLE identifier from scan")
    p = sub.add_parser("replay")
    p.add_argument("capture", type=Path)
    args = parser.parse_args()
    if hasattr(args, "duration") and not 1 <= args.duration <= 3600:
        parser.error("duration must be between 1 and 3600 seconds")
    if args.action == "scan":
        for device, adv in asyncio.run(scan_adapters()):
            print(f"{adv.local_name or device.name}: {device.address}, RSSI {adv.rssi}, services {adv.service_uuids}")
    elif args.action == "serial":
        record_serial(args)
    elif args.action == "ble":
        asyncio.run(capture_ble(args))
    elif args.action == "self-test":
        self_test(args)
    else:
        print_summary(args.capture)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("Stopped; recording files already written remain available.")
    except (ValueError, OSError) as error:
        sys.exit(str(error))

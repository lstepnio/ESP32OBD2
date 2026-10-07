#!/usr/bin/env python3
"""Mac BLE peripheral using sanitized fixtures. A separate service prevents
legacy gauges accidentally using examples as a vehicle. Trace firmware only.
"""
from __future__ import annotations
import argparse
from collections import deque
import json
from pathlib import Path
import time
from obd_capture_core import allowed_command

ROOT = Path(__file__).resolve().parents[1]
SERVICE = "6f1a1000-9e3b-4f45-a714-69c9d23b6c00"

class BenchReplies:
    """Pure deterministic ELM model; every result is simulated, even recorded bytes."""
    def __init__(self, source="engine", scenario="normal"):
        self.fixture = json.loads((ROOT / f"firmware/gauge/tests/fixtures/jeep-{source}.json").read_text())
        self.responses = {item["command"]: bytes.fromhex(item["responseHex"]) for item in self.fixture["cases"]}
        self.echo = True
        self.headers = True
        self.scenario = scenario

    def reply(self, command: bytes) -> bytes | None:
        text = command.decode("ascii", "strict").strip().upper()
        if not allowed_command(text):
            return b"?\r>"
        if text == "ATE0": self.echo = False
        if text == "ATH0": self.headers = False
        if text == "ATH1": self.headers = True
        if text.startswith("AT"):
            result = {"ATI": b"EGAUGE BENCH SIMULATED", "ATDP": b"AUTO, ISO 15765-4 (CAN 11/500)", "ATDPN": b"A6"}.get(text, b"OK") + b"\r>"
        else:
            result = self.responses.get(text, b"NO DATA\r>")
            if self.scenario == "missing": return None
            if self.scenario == "no-data": result = b"NO DATA\r>"
            if self.scenario == "malformed": result = b"7E804410CZZZZ\r>"
            if self.scenario == "multiple" and text == "010C":
                result = b"7E804410C0B3C\r7E904410C1122\r>"
            if not self.headers and result.startswith((b"7E8", b"7E9")):
                # Fixture single frame only: discard CAN ID + ISO-TP length.
                result = b"\r".join(line[5:] if line.startswith((b"7E8", b"7E9")) else line
                                      for line in result.split(b"\r"))
        return (text.encode()+b"\r" if self.echo else b"") + result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=("engine", "transmission"), default="engine")
    parser.add_argument("--scenario", choices=("normal", "missing", "no-data", "malformed", "multiple", "delayed"), default="normal")
    parser.add_argument("--chunk-size", type=int, default=7)
    parser.add_argument("--delay-ms", type=int, default=30)
    parser.add_argument("--stop-after", type=int, default=600)
    args = parser.parse_args()
    if not 1 <= args.chunk_size <= 20 or not 0 <= args.delay_ms <= 5000 or not 1 <= args.stop_after <= 3600:
        parser.error("Use chunks 1..20 bytes, delay 0..5000 ms, and duration 1..3600 seconds")
    # Imported only for the actual Mac server; pure reply tests also work on CI.
    import objc
    from Foundation import NSObject, NSDate, NSRunLoop
    from CoreBluetooth import (CBPeripheralManager, CBMutableService, CBMutableCharacteristic, CBUUID,
        CBManagerStatePoweredOn, CBCharacteristicPropertyWrite, CBCharacteristicPropertyWriteWithoutResponse,
        CBCharacteristicPropertyNotify, CBAttributePermissionsWriteable, CBATTErrorSuccess,
        CBATTErrorInvalidOffset, CBATTErrorInvalidAttributeValueLength, CBAdvertisementDataServiceUUIDsKey,
        CBAdvertisementDataLocalNameKey)

    model = BenchReplies(args.source, args.scenario)
    class Delegate(NSObject):
        def init(self):
            self = objc.super(Delegate, self).init()
            self.pending = deque()
            self.central = None
            self.buffer = bytearray()
            self.ready = False
            return self

        def peripheralManagerDidUpdateState_(self, manager):
            if manager.state() != CBManagerStatePoweredOn: return
            service = CBMutableService.alloc().initWithType_primary_(CBUUID.UUIDWithString_(SERVICE), True)
            self.tx = CBMutableCharacteristic.alloc().initWithType_properties_value_permissions_(
                CBUUID.UUIDWithString_("2AF1"), CBCharacteristicPropertyWrite | CBCharacteristicPropertyWriteWithoutResponse,
                None, CBAttributePermissionsWriteable)
            self.rx = CBMutableCharacteristic.alloc().initWithType_properties_value_permissions_(
                CBUUID.UUIDWithString_("2AF0"), CBCharacteristicPropertyNotify, None, 0)
            service.setCharacteristics_([self.rx, self.tx])
            manager.addService_(service)

        def peripheralManager_didAddService_error_(self, manager, service, error):
            if error: print(f"Simulator service failed: {error}", flush=True); return
            manager.startAdvertising_({CBAdvertisementDataServiceUUIDsKey: [CBUUID.UUIDWithString_(SERVICE)],
                                       CBAdvertisementDataLocalNameKey: "EGAUGE BENCH"})
            self.ready = True
            print("SIMULATED: select Bench simulator in eGauge using trace firmware. No vehicle is connected.", flush=True)

        def peripheralManager_central_didSubscribeToCharacteristic_(self, manager, central, characteristic):
            self.central = central
            self.buffer.clear(); self.pending.clear()
            model.echo = True; model.headers = True
            print("SIMULATED: gauge subscribed", flush=True)

        def peripheralManager_central_didUnsubscribeFromCharacteristic_(self, manager, central, characteristic):
            self.central = None
            self.buffer.clear(); self.pending.clear()
            print("SIMULATED: gauge unsubscribed", flush=True)

        def peripheralManager_didReceiveWriteRequests_(self, manager, requests):
            for request in requests:
                if request.offset(): manager.respondToRequest_withResult_(request, CBATTErrorInvalidOffset); continue
                data = bytes(request.value())
                if len(self.buffer)+len(data) > 64:
                    self.buffer.clear()
                    manager.respondToRequest_withResult_(request, CBATTErrorInvalidAttributeValueLength); continue
                self.buffer.extend(data)
                manager.respondToRequest_withResult_(request, CBATTErrorSuccess)
                while b"\r" in self.buffer:
                    command, _, trailing = self.buffer.partition(b"\r")
                    self.buffer[:] = trailing
                    try: response = model.reply(command)
                    except (UnicodeError, ValueError): response = b"?\r>"
                    print(f"SIMULATED request {command.decode('ascii', 'replace')}", flush=True)
                    if response is None: continue
                    if len(self.pending) > 128: self.pending.clear(); continue
                    delay = 2.5 if args.scenario == "delayed" and not command.upper().startswith(b"AT") else args.delay_ms/1000
                    due = time.monotonic()+delay
                    for offset in range(0,len(response),args.chunk_size):
                        self.pending.append((due, response[offset:offset+args.chunk_size]))
                        due += .01

        def peripheralManagerIsReadyToUpdateSubscribers_(self, manager):
            # The main run loop retries queued notifications with bounded storage.
            pass

    delegate = Delegate.alloc().init()
    manager = CBPeripheralManager.alloc().initWithDelegate_queue_(delegate, None)
    stop = time.monotonic()+args.stop_after
    try:
        while time.monotonic() < stop:
            NSRunLoop.currentRunLoop().runUntilDate_(NSDate.dateWithTimeIntervalSinceNow_(.02))
            if delegate.central and delegate.pending and delegate.pending[0][0] <= time.monotonic():
                block = delegate.pending[0][1]
                if manager.updateValue_forCharacteristic_onSubscribedCentrals_(block, delegate.rx, [delegate.central]):
                    delegate.pending.popleft()
                    print(f"SIMULATED notification {block.hex()}", flush=True)
    except KeyboardInterrupt:
        pass
    finally:
        manager.stopAdvertising(); manager.removeAllServices()
        print("SIMULATED: bench server stopped", flush=True)

if __name__ == "__main__": main()

import asyncio
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_capture_core import allowed_command, support_replies, supports, transactions
from obd_capture import AdapterSession, capture_ble
from types import SimpleNamespace
import tempfile
from unittest.mock import patch


def events(spec):
    return [{"schema": 1, "seq": i, "t_us": i * 1000,
             "source": source, "generation": generation, "event": kind,
             "status": 0, "offset": 0, "total": len(data), "hex": data.hex()}
            for i, (source, generation, kind, data) in enumerate(spec)]


class CaptureTests(unittest.TestCase):
    def test_read_only_policy(self):
        for command in ("ATI", "ATH1", "0100", "0120", "010C", "0105"):
            self.assertTrue(allowed_command(command))
        for command in ("04", "220010", "0902", "ATSH7E0", "010C\r04", "010c", "01FF", "ATZ", "ATWR", ""):
            self.assertFalse(allowed_command(command), command)

    def test_support_per_responder_and_formats(self):
        for frame in (b"7E8 06 41 00 BE 3F A8 13\r>", b"7E8064100BE3FA813\r>", b"7E84100BE3FA813\r>"):
            self.assertEqual(support_replies(frame, 0), {"7E8": 0xBE3FA813})
        self.assertEqual(support_replies(b"18DAF110064100BE3FA813\r>", 0), {"18DAF110": 0xBE3FA813})
        self.assertEqual(support_replies(b"4100BE3FA813\r>", 0), {"headerless": 0xBE3FA813})
        self.assertEqual(support_replies(b"NO DATA\r>", 0), {})
        self.assertEqual(support_replies(b"410C1AF8\r>", 0), {})
        self.assertEqual(support_replies(b"7E810064100BE3FA813\r>", 0), {})
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            support_replies(b"4100BE3FA813\r4100BE3FA813\r>", 0)
        bitmaps = {0: {"7E8": 0x00100000, "7E9": 0x08000000}}
        self.assertTrue(supports(bitmaps, 0x0C))
        self.assertTrue(supports(bitmaps, 0x05))
        self.assertFalse(supports(bitmaps, 0x0D))

    def test_split_prompt_and_independent_sources(self):
        capture = events([(0, 1, "tx", b"010C\r"), (1, 1, "tx", b"0105\r"),
                          (0, 1, "rx", b"410C"), (1, 1, "rx", b"41057B\r>"),
                          (0, 1, "rx", b"1AF8\r"), (0, 1, "rx", b">")])
        frames = transactions(capture)
        self.assertEqual([(f["source"], f["command"], f["raw"]) for f in frames],
                         [(1, "0105", b"41057B\r>"), (0, "010C", b"410C1AF8\r>")])

    def test_late_old_generation_never_supplies_new_query(self):
        capture = events([(0, 1, "tx", b"010C\r"), (0, 2, "disconnected", b""),
                          (0, 2, "tx", b"0105\r"), (0, 1, "rx", b"410C1AF8\r>"),
                          (0, 2, "rx", b"41057B\r>")])
        frames = transactions(capture)
        self.assertEqual(len(frames), 1)
        self.assertEqual(frames[0]["command"], "0105")

    def test_prompt_required_before_next_command(self):
        capture = events([(0, 1, "tx", b"010C\r"), (0, 1, "timeout", b""),
                          (0, 1, "tx", b"0105\r")])
        with self.assertRaisesRegex(ValueError, "before previous prompt"):
            transactions(capture)

    def test_trace_loss_or_missing_event_rejected(self):
        capture = events([(0, 1, "tx", b"010C\r"), (0, 1, "rx", b"410C1AF8\r>")])
        capture[1]["seq"] = 2
        with self.assertRaisesRegex(ValueError, "sequence"):
            transactions(capture)
        capture[1]["seq"] = 1
        capture[1]["event"] = "trace_loss"
        with self.assertRaisesRegex(ValueError, "lost trace"):
            transactions(capture)

    def test_chunk_offset_and_reboot_isolation(self):
        capture = events([(0, 1, "tx", b"010C\r"), (0, 1, "rx", b"41"),
                          (0, 1, "rx", b"0C1AF8\r>")])
        capture[1]["total"] = capture[2]["total"] = 10
        capture[2]["offset"] = 2
        capture[2]["t_us"] = capture[1]["t_us"]
        self.assertEqual(transactions(capture)[0]["raw"], b"410C1AF8\r>")
        capture[2]["offset"] = 3
        with self.assertRaises(ValueError):
            transactions(capture)
        capture = events([(0, 1, "tx", b"010C\r")]) + events([
            (0, 0, "trace_start", b""), (0, 1, "rx", b"410C1AF8\r>"),
            (0, 1, "tx", b"0105\r"), (0, 1, "rx", b"41057B\r>")])
        self.assertEqual([f["command"] for f in transactions(capture)], ["0105"])


class MemoryRecorder:
    def __init__(self): self.items = []
    def event(self, *args, **kwargs): self.items.append((args, kwargs))


class AsyncCaptureTests(unittest.IsolatedAsyncioTestCase):
    async def test_full_engine_capture_against_fake_gatt_adapter(self):
        """Exercise the actual BLE capture workflow without claiming hardware evidence."""
        tx = SimpleNamespace(uuid="00002af1-0000-1000-8000-00805f9b34fb", handle=2,
                             properties=["write"], descriptors=[])
        rx = SimpleNamespace(uuid="00002af0-0000-1000-8000-00805f9b34fb", handle=3,
                             properties=["notify"], descriptors=[])
        class Service:
            uuid = "000018f0-0000-1000-8000-00805f9b34fb"
            handle = 1
            characteristics = [tx, rx]
            def get_characteristic(self, uuid):
                return next((c for c in self.characteristics if c.uuid == uuid), None)
        class Services(list):
            def get_service(self, uuid): return self[0] if uuid == self[0].uuid else None
        writes = []
        class FakeClient:
            def __init__(self, *_args, **_kwargs):
                self.services = Services([Service()]); self.headers = True
            async def __aenter__(self): return self
            async def __aexit__(self, *_args): pass
            async def start_notify(self, _char, callback): self.callback = callback
            async def stop_notify(self, _char): pass
            async def write_gatt_char(self, _char, raw, **_kwargs):
                command = raw.decode().strip(); writes.append(command)
                if command == "ATH0": self.headers = False
                replies = {"0100": b"7E806410008100000\r>", "010C": b"410C1AF8\r>", "0105": b"41057B\r>"}
                value = replies.get(command, b"OK\r>")
                if self.headers and command in ("010C", "0105"):
                    value = (b"7E804" if command == "010C" else b"7E803") + value
                self.callback(None, bytearray(value[:3])); self.callback(None, bytearray(value[3:]))
        async def selected(_identifier): return SimpleNamespace(name="Fixture Vgate", address="SIMULATED")
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(sys.modules, {"bleak": SimpleNamespace(BleakClient=FakeClient)}), patch("obd_capture.choose_adapter", selected):
                await capture_ble(SimpleNamespace(adapter=None, output=Path(directory), source="engine", duration=.001, simulated=True))
            self.assertTrue(all(allowed_command(c) for c in writes))
            self.assertIn("0100", writes); self.assertIn("ATH0", writes)
            self.assertIn("0105", writes)
            self.assertEqual(len(list(Path(directory).glob("*/gatt.json"))), 1)

    async def test_delayed_prompt_stops_requests_after_timeout(self):
        recorder = MemoryRecorder()
        session = AdapterSession(recorder)
        class FakeClient:
            async def write_gatt_char(self, *_args, **_kwargs):
                session.notification(None, bytearray(b"410C1A"))
        session.client = FakeClient()
        with self.assertRaisesRegex(ValueError, "timed out"):
            await session.request("010C", timeout=0.005)
        with self.assertRaisesRegex(ValueError, "no prompt"):
            await session.request("0105")
        session.notification(None, bytearray(b"F8\r>"))
        self.assertTrue(session.waiting_prompt)

    async def test_split_response_and_policy_before_write(self):
        recorder = MemoryRecorder()
        session = AdapterSession(recorder)
        class FakeClient:
            async def write_gatt_char(self, *_args, **_kwargs):
                session.notification(None, bytearray(b"410C1A"))
                session.notification(None, bytearray(b"F8\r>"))
        session.client = FakeClient()
        with self.assertRaisesRegex(ValueError, "read-only"):
            await session.request("04")
        self.assertEqual(await session.request("010C"), b"410C1AF8\r>")


if __name__ == "__main__":
    unittest.main()

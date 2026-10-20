"""Synthetic offline parked-capture checks. No adapter, ECU or phone is contacted."""

import asyncio
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import jeep_action_capture as capture
from obd_capture import BoundedSession
from obd_explore import explore, main


def frame(body, responder="7E8"):
    if len(body) <= 7:
        chunks = [bytes((len(body),)) + body]
    else:
        chunks = [bytes((0x10, len(body))) + body[:6]]
        chunks += [
            bytes((0x21 + index,)) + body[offset : offset + 7]
            for index, offset in enumerate(range(6, len(body), 7))
        ]
    return (
        b"\r".join(
            responder.encode()
            + (chunk if len(body) <= 7 else chunk.ljust(8, b"\x00")).hex().upper().encode()
            for chunk in chunks
        )
        + b"\r>"
    )


IDENTITY = {
    "0904": frame(b"\x49\x04\x01" + b"68059434AD".ljust(16, b"\x00")),
    "0906": frame(bytes.fromhex("490601CA36378C")),
}
REPLIES = {
    "0100": frame(bytes.fromhex("410008180000")),
    "0900": frame(bytes.fromhex("490014000000")),
    "ATDP": b"ISO 15765-4 CAN\r>",
    "ATDPN": b"A6\r>",
    "010C": frame(bytes.fromhex("410C0AF0")),
    "010D": frame(bytes.fromhex("410D00")),
    "0105": frame(bytes.fromhex("41055A")),
    "1A87": frame(bytes.fromhex("5A870875154F140400")),
    **{
        command: frame(bytes.fromhex("62" + command[2:] + "0100")) for command in capture.CANDIDATES
    },
    **IDENTITY,
}


class PolicyTests(unittest.TestCase):
    def test_fixed_reads_only_no_actuator_or_session_control(self):
        for command in (*capture.CANDIDATES, *capture.STANDARD, *capture.RESTORE, "1A87"):
            self.assertTrue(capture.policy(command))
        for command in (
            "310506801",
            "32051",
            "33051",
            "31A4",
            "32A4",
            "10921",
            "3E011",
            "0902",
            "04",
            "27",
            "2E10AC01",
            "2210AC\r04",
            "2210AF",
        ):
            self.assertFalse(capture.policy(command), command)

    def test_primary_identity_route_and_calibration_are_mandatory(self):
        identity = {k: v.hex() for k, v in IDENTITY.items()}
        self.assertEqual(capture.BASELINE, capture.verify_identity(identity))
        for bad in (
            {},
            {**identity, "0906": frame(bytes.fromhex("490601CA36378D")).hex()},
            {k: v.replace(b"7E8", b"7E9").hex() for k, v in IDENTITY.items()},
        ):
            with self.assertRaises(ValueError):
                capture.verify_identity(bad)
        for maps in (
            {},
            {0: {}},
            {0: {"7E9": 1}},
            {0: {"7E8": 1, "7E9": 1}},
            {0: {"7E8": 1}, 32: {}},
        ):
            with self.assertRaises(ValueError):
                capture.verify_route(b"A6\r>".hex(), maps)
        capture.verify_route(b"A6\r>".hex(), {0: {"7E8": 1}})
        with self.assertRaises(ValueError):
            capture.verify_route(b"A7\r>".hex(), {0: {"7E8": 1}})

    def test_missing_negative_or_raw_candidate_never_becomes_guard_truth(self):
        self.assertEqual(700, capture.decode(REPLIES["010C"], "010C")["rpm"])
        self.assertEqual(50, capture.decode(REPLIES["0105"], "0105")["coolant_c"])
        self.assertEqual("no_data", capture.decode(b"NO DATA\r>", "2210AC")["status"])
        self.assertEqual(
            "negative_response", capture.decode(frame(bytes.fromhex("7F2231")), "2210AC")["status"]
        )
        sample = capture.decode(REPLIES["2210AC"], "2210AC")
        self.assertFalse(sample["qualified"])
        self.assertEqual("candidate_only", sample["status"])
        self.assertNotIn("park", sample)
        for raw in (
            REPLIES["2210AC"].replace(b"7E8", b"7E9"),
            REPLIES["2210AC"] * 2,
            b"7E8056210AC\r>",
            b"7E8046210AE00\r>",
            b"7E8102049040168\r7E82200\r>",
        ):
            with self.assertRaises(ValueError):
                capture.decode(raw, "2210AC")

    def test_cli_refuses_wrong_source_or_combined_probes_before_ble(self):
        for extra in (
            ["--source", "transmission"],
            ["--source", "engine", "--monitor"],
            ["--source", "engine", "--duration", "10"],
            ["--source", "engine", "--tcm-values"],
        ):
            with (
                patch.object(
                    sys,
                    "argv",
                    [
                        "obd_explore",
                        "--adapter",
                        "unused",
                        "--jeep-action-state",
                        "idle-P-brake",
                        *extra,
                    ],
                ),
                patch("obd_explore.asyncio.run") as run,
                contextlib.redirect_stderr(io.StringIO()),
            ):
                with self.assertRaises(SystemExit):
                    main()
                run.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_fixed_request_count_and_stop_on_movement_or_bad_label(self):
        session = AsyncMock()
        session.request.side_effect = lambda command, **_: REPLIES[command]
        result = {"owner_reported_state": "idle-P-brake", "samples": []}
        await capture.probe(session, result)
        self.assertEqual(22, len(result["samples"]))
        self.assertTrue(all(not sample["qualified"] for sample in result["samples"]))
        result = {"owner_reported_state": "idle-P-brake", "samples": []}
        session.request.side_effect = lambda command, **_: (
            frame(bytes.fromhex("410D01")) if command == "010D" else REPLIES[command]
        )
        with self.assertRaisesRegex(ValueError, "Nonzero speed"):
            await capture.probe(session, result)
        self.assertEqual("010D", result["samples"][-1]["request"])
        with self.assertRaisesRegex(ValueError, "owner-reported"):
            await capture.probe(session, {"owner_reported_state": "drive", "samples": []})

    async def test_unsupported_candidates_are_recorded_once_without_zero_defaults(self):
        session = AsyncMock()
        session.request.side_effect = lambda command, **_: (
            b"NO DATA\r>" if command in ("010D", "2210AC") else REPLIES[command]
        )
        result = {"owner_reported_state": "idle-P-brake", "samples": []}
        await capture.probe(session, result)
        for command in ("010D", "2210AC"):
            rows = [row for row in result["samples"] if row["request"] == command]
            self.assertEqual(1, len(rows))
            self.assertEqual("no_data", rows[0]["status"])
            self.assertNotIn("speed_kph", rows[0])
        provenance = json.loads(
            (
                Path(__file__).resolve().parents[2]
                / "data/vehicle-definitions/vehicles/jeep/command-guard-candidates.json"
            ).read_text()
        )
        self.assertEqual(
            set(capture.CANDIDATES),
            {
                item["definition"]["Mode"] + item["definition"]["Pid"]
                for item in provenance["entries"]
            },
        )

    async def test_stalled_write_has_a_whole_request_deadline(self):
        session = BoundedSession(SimpleNamespace(event=lambda *_: None), policy=capture.policy)
        session.deadline = asyncio.get_running_loop().time() + 0.01
        session.client = SimpleNamespace()

        # Use an actual awaiting write so the outer budget covers the write itself.
        async def stalled(*args, **kwargs):
            await asyncio.sleep(10)

        session.client.write_gatt_char = stalled
        with self.assertRaisesRegex(ValueError, "budget"):
            await session.request("2210AC")
        self.assertTrue(session.waiting_prompt)

    async def run_workflow(self, wrong_identity=False, transport_loss=False):
        writes = []
        characteristic = SimpleNamespace(properties=["notify", "write"])
        service = SimpleNamespace(
            uuid="fixture", characteristics=[], get_characteristic=lambda _: characteristic
        )

        class Services(list):
            def get_service(self, _):
                return service

        class Client:
            services = Services([service])

            def __init__(self, *args, **kwargs):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *args):
                pass

            async def start_notify(self, _, callback):
                self.callback = callback

            async def stop_notify(self, _):
                pass

            async def write_gatt_char(self, _, raw, **kwargs):
                command = raw.decode().strip()
                writes.append(command)
                if transport_loss and command == "2210AC":
                    raise OSError("Fixture link loss")
                reply = REPLIES.get(command, b"OK\r>")
                if wrong_identity and command == "0906":
                    reply = frame(bytes.fromhex("490601CA36378D"))
                self.callback(None, reply[:5])
                self.callback(None, reply[5:])

        with tempfile.TemporaryDirectory() as directory:
            args = SimpleNamespace(
                adapter="fixture",
                source="engine",
                output=Path(directory),
                duration=30,
                jeep_action_state="idle-P-brake",
                tcm_values=False,
                tcm_gear_position=None,
                tcm_temperature=False,
                tcm_temperature_v2=False,
                tcm_temperature_v3=False,
                tcm_faults=False,
                hemi_temperature=False,
                monitor=False,
            )
            with (
                patch(
                    "obd_explore.choose_adapter",
                    new=AsyncMock(return_value=SimpleNamespace(name="fixture", address="fixture")),
                ),
                patch.dict(sys.modules, {"bleak": SimpleNamespace(BleakClient=Client)}),
                contextlib.redirect_stdout(io.StringIO()),
            ):
                if wrong_identity or transport_loss:
                    with self.assertRaises((ValueError, OSError)):
                        await explore(args)
                else:
                    await explore(args)
            result = json.loads(next(Path(directory).glob("*/exploration.json")).read_text())
            return writes, result

    async def test_full_workflow_restores_route_and_retains_partial_failure(self):
        writes, result = await self.run_workflow()
        self.assertEqual(list(capture.RESTORE), writes[-5:])
        self.assertTrue(result["adapter_restored"])
        self.assertEqual(22, len(result["jeep_action_inputs"]["samples"]))
        writes, result = await self.run_workflow(wrong_identity=True)
        self.assertFalse(set(writes) & set(capture.CANDIDATES))
        self.assertTrue(result["adapter_restored"])
        writes, result = await self.run_workflow(transport_loss=True)
        self.assertFalse(result["adapter_restored"])
        self.assertIn("error", result)
        self.assertGreater(len(result["jeep_action_inputs"]["samples"]), 0)
        self.assertFalse(any(command.startswith(("31", "32", "33")) for command in writes))

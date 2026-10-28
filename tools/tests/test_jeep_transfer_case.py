import asyncio
import contextlib
import io
from pathlib import Path
import sys
from unittest.mock import AsyncMock, patch
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_explore import transfer_case_policy, exploration_policy, main, explore
from tools.tests.test_jeep_action_capture import frame, REPLIES
import json
import tempfile
from types import SimpleNamespace
from obd_jeep_transfer_case import decode_candidate, probe


class TransferCaseTests(unittest.TestCase):
    def test_one_candidate_and_only_adapter_setup_are_allowed(self):
        for command in ("2111", "ATSH620", "ATCRA504", "ATCRA", "ATSH7DF"):
            self.assertTrue(transfer_case_policy(command))
        for command in ("2111", "ATSH620", "ATCRA504"):
            self.assertFalse(exploration_policy(command))
        for command in (
            "2112",
            "2140",
            "31A4",
            "32A4",
            "31050680",
            "04",
            "2E1234FF",
            "0902",
            "2111\r04",
        ):
            self.assertFalse(transfer_case_policy(command))

    def test_no_variants_or_positions_are_guessed(self):
        for value in (0, 1, 2, 3, 4, 7, 255):
            result = decode_candidate(f"50404611100{value:02X}\r>".encode())
            self.assertFalse(result["qualified"])
            self.assertEqual("candidate_only", result["status"])
            self.assertNotIn("position", result)
        self.assertEqual("no_data", decode_candidate(b"NO DATA\r>")["status"])
        self.assertEqual("80", decode_candidate(b"504037F2180\r>")["nrc"])
        for raw in (
            b"7E80461110003\r>",
            b"5040462110003\r>",
            b"5040461110003\r>junk",
            b"5040461110003\r5040461110003\r>",
            b"5040461110003\r" + b" " * 4096 + b">",
            b"5040461110003\r>>",
            b"5041009611100030000\r50422000000\r>",
        ):
            with self.assertRaises(ValueError):
                decode_candidate(raw)

    def test_cli_rejects_other_port_or_probe_combinations_before_bluetooth(self):
        for extra in (
            ["--source", "transmission"],
            ["--source", "engine", "--tcm-faults"],
            ["--source", "engine", "--monitor"],
            ["--source", "engine", "--jeep-action-state", "idle-P-brake"],
            ["--source", "engine", "--tcm-state", "idle-P"],
            ["--source", "engine", "--duration", "nan"],
            ["--source", "engine", "--tcm-values"],
            ["--source", "engine", "--tcm-temperature-v3"],
        ):
            with (
                patch.object(
                    sys,
                    "argv",
                    ["obd_explore", "--adapter", "offline", "--jeep-transfer-case", "2HI", *extra],
                ),
                patch("obd_explore.explore") as connect,
                contextlib.redirect_stderr(io.StringIO()),
            ):
                with self.assertRaises(SystemExit):
                    main()
                connect.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
    async def test_bad_label_or_existing_samples_cannot_send(self):
        session = type("Session", (), {"request": AsyncMock()})()
        for result in (
            {"owner_reported_position": "drive", "samples": []},
            {"owner_reported_position": "2HI"},
            {"owner_reported_position": "2HI", "samples": [{}]},
        ):
            with self.assertRaises(ValueError):
                await probe(session, result)
        session.request.assert_not_called()

    async def test_setup_rejection_does_not_send_candidate(self):
        session = type(
            "Session",
            (),
            {
                "waiting_prompt": False,
                "request": AsyncMock(return_value=b"?\r>"),
            },
        )()
        with self.assertRaises(ValueError):
            await probe(session, {"owner_reported_position": "2HI", "samples": []})
        self.assertNotIn("2111", [c.args[0] for c in session.request.call_args_list])

    async def test_three_samples_with_shared_deadline_and_cleanup(self):
        session = type(
            "Session",
            (),
            {
                "waiting_prompt": False,
                "request": AsyncMock(
                    side_effect=lambda command, **kw: (
                        b"5040461110003\r>" if command == "2111" else b"OK\r>"
                    )
                ),
            },
        )()
        result = {"owner_reported_position": "2HI", "samples": []}
        with patch("obd_jeep_transfer_case.asyncio.sleep", new=AsyncMock()):
            await probe(session, result)
        self.assertEqual(
            ["ATSH620", "ATCRA504", "2111", "2111", "2111", "ATCRA", "ATSH7DF"],
            [c.args[0] for c in session.request.call_args_list],
        )
        self.assertEqual(3, len(result["samples"]))

    async def test_negative_not_retried_and_unfinished_reply_has_no_cleanup_write(self):
        session = type(
            "Session",
            (),
            {
                "waiting_prompt": False,
                "request": AsyncMock(
                    side_effect=lambda command, **kw: (
                        b"504037F2131\r>" if command == "2111" else b"OK\r>"
                    )
                ),
            },
        )()
        result = {"owner_reported_position": "4HI", "samples": []}
        await probe(session, result)
        self.assertEqual(1, len(result["samples"]))

        async def fail(command, **kwargs):
            if command == "2111":
                session.waiting_prompt = True
                raise ValueError("Lost prompt")
            return b"OK\r>"

        session.request = AsyncMock(side_effect=fail)
        with self.assertRaises(ValueError):
            await probe(session, {"owner_reported_position": "4LOW", "samples": []})
        self.assertEqual(
            ["ATSH620", "ATCRA504", "2111"], [c.args[0] for c in session.request.call_args_list]
        )

    async def test_cancel_is_preserved_after_bounded_adapter_cleanup(self):
        async def cancel(command, **kwargs):
            if command == "2111":
                raise asyncio.CancelledError()
            return b"OK\r>"

        session = type(
            "Session", (), {"waiting_prompt": False, "request": AsyncMock(side_effect=cancel)}
        )()
        with self.assertRaises(asyncio.CancelledError):
            await probe(session, {"owner_reported_position": "N", "samples": []})
        self.assertEqual("ATSH7DF", session.request.call_args_list[-1].args[0])


class WorkflowTests(unittest.IsolatedAsyncioTestCase):
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
                if transport_loss and command == "2111":
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
                jeep_action_state=None,
                jeep_transfer_case="2HI",
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

    async def test_route_is_explicit_and_identity_failure_sends_no_tipm_request(self):
        replies = {**REPLIES, "2111": b"5040461110003\r>"}
        with (
            patch.dict(REPLIES, replies),
            patch("obd_jeep_transfer_case.asyncio.sleep", new=AsyncMock()),
        ):
            writes, result = await self.run_workflow()
        self.assertEqual(3, writes.count("2111"))
        self.assertEqual(["ATCRA", "ATSH7DF", "ATCAF1", "ATTP0"], writes[-4:])
        self.assertTrue(result["adapter_restored"])
        self.assertFalse(result["transfer_case_candidate"]["qualified"])
        self.assertNotIn("2210AC", writes)
        writes, result = await self.run_workflow(wrong_identity=True)
        self.assertFalse(set(writes) & {"ATSH620", "ATCRA504", "2111"})
        self.assertTrue(result["adapter_restored"])
        writes, result = await self.run_workflow(transport_loss=True)
        self.assertFalse(result["adapter_restored"])
        self.assertIn("error", result)

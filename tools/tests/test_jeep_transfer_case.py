import asyncio
from pathlib import Path
import sys
from unittest.mock import AsyncMock, patch
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from obd_explore import transfer_case_policy, exploration_policy, main
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
        ):
            with self.assertRaises(ValueError):
                decode_candidate(raw)

    def test_cli_rejects_other_port_or_probe_combinations_before_bluetooth(self):
        for extra in (
            ["--source", "transmission"],
            ["--source", "engine", "--tcm-faults"],
            ["--source", "engine", "--monitor"],
        ):
            with (
                patch.object(
                    sys,
                    "argv",
                    ["obd_explore", "--adapter", "offline", "--jeep-transfer-case", "2HI", *extra],
                ),
                patch("obd_explore.explore") as connect,
            ):
                with self.assertRaises(SystemExit):
                    main()
                connect.assert_not_called()


class ProbeTests(unittest.IsolatedAsyncioTestCase):
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

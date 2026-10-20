import contextlib
import io
import json
from pathlib import Path
import sqlite3
from types import SimpleNamespace
import unittest

from tools.research.audit_vehicle_readings import audit
from tools.research.query_vehicle_catalog import search, translated_labels

ROOT = Path(__file__).resolve().parents[2]


class CoverageTests(unittest.TestCase):
    def test_actual_ecm_bitmap_distinguishes_scalar_coverage_from_status_gaps(self):
        discovery = json.loads(
            (ROOT / "firmware/gauge/tests/fixtures/jeep-ecm-support.json").read_text()
        )
        catalog = json.loads((ROOT / "contracts/reading-catalog.json").read_text())
        result = audit(discovery, catalog)
        checked_in = json.loads(
            (
                ROOT / "data/vehicle-definitions/vehicles/jeep/wrangler-jk-standard-coverage.json"
            ).read_text()
        )
        self.assertEqual(checked_in, result)
        self.assertEqual(42, len(result["readings"]))
        self.assertEqual(30, result["counts"]["selectable"])
        self.assertEqual(4, result["counts"]["component_gap"])
        self.assertEqual(2, result["counts"]["named_state_gap"])
        self.assertEqual([], result["pendingSupportRanges"])
        self.assertNotIn("identity", result)
        self.assertNotIn("transfer", " ".join(r["name"].lower() for r in result["readings"]))
        for pid in ("14", "15", "18", "19"):
            self.assertEqual(
                "component_gap", next(r for r in result["readings"] if r["pid"] == pid)["coverage"]
            )

    def test_wrong_ecu_invalid_and_unlinked_ranges_are_not_coverage(self):
        catalog = {"readings": []}
        for maps in (
            {"0": {"7E9": 1}},
            {"1": {"7E8": 1}},
            {"0": {"7E8": True}},
            {"0": {"7E8": -1}},
            {"0": {"7E8": 0}, "64": {"7E8": 1}},
            {"0": {"7E8": 0x100000000}},
        ):
            with self.assertRaises(ValueError):
                audit({"standard_support": maps}, catalog)
        result = audit({"standard_support": {"0": {"7E8": 1}}}, catalog)
        self.assertEqual(["20"], result["pendingSupportRanges"])
        self.assertEqual("support_map", result["readings"][0]["coverage"])


class TranslationSearchTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
        CREATE TABLE resources(id INTEGER PRIMARY KEY, name TEXT, category TEXT, module TEXT, payload_json TEXT);
        CREATE TABLE entries(resource_id INTEGER,item_index INTEGER,name TEXT,mode TEXT,pid TEXT,unit TEXT,dtc TEXT,request_header TEXT,response_header TEXT,command TEXT,stop_command TEXT);
        """)
        labels = [{"Id": "TIPMCGW_V2_730854", "D": "T-Case Encoder Sensor"}]
        self.db.execute(
            "INSERT INTO resources VALUES(1,?,?,?,?)",
            ("x.Labels.TIPMCGW.dat", "Descriptions", None, json.dumps(labels)),
        )
        self.db.execute(
            "INSERT INTO resources VALUES(2,?,?,?,?)",
            (
                "x.LiveData.TIPMCGW.variant.json",
                "LiveData",
                "TIPMCGW",
                '[{"N":"TIPMCGW_V2_730854","Mode":"21","Pid":"11"}]',
            ),
        )
        self.db.execute(
            "INSERT INTO entries(resource_id,item_index,name,mode,pid) VALUES(2,0,'TIPMCGW_V2_730854','21','11')"
        )

    def tearDown(self):
        self.db.close()

    def test_human_label_search_retains_id_raw_variant_and_route_fields(self):
        args = SimpleNamespace(
            category="LiveData",
            module="TIPMCGW",
            mode=None,
            pid=None,
            dtc=None,
            name="encoder sensor",
            limit=5,
            offset=0,
            raw=True,
        )
        with contextlib.redirect_stdout(io.StringIO()) as output:
            search(self.db, args)
        row = json.loads(output.getvalue())
        self.assertEqual("T-Case Encoder Sensor", row["display_name"])
        self.assertEqual("TIPMCGW_V2_730854", row["name"])
        self.assertEqual("11", row["raw"]["Pid"])
        args.module = "TCM"
        with contextlib.redirect_stdout(io.StringIO()) as output:
            search(self.db, args)
        self.assertEqual("", output.getvalue())

    def test_ambiguous_or_excessively_broad_labels_fail_instead_of_guessing(self):
        labels = [{"Id": "same", "D": "Sensor A"}, {"Id": "same", "D": "Sensor B"}]
        self.db.execute("UPDATE resources SET payload_json=? WHERE id=1", (json.dumps(labels),))
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            translated_labels(self.db, None, "sensor")
        labels = [{"Id": str(i), "D": "Sensor"} for i in range(4001)]
        self.db.execute("UPDATE resources SET payload_json=? WHERE id=1", (json.dumps(labels),))
        with self.assertRaisesRegex(ValueError, "too broad"):
            translated_labels(self.db, None, "sensor")


if __name__ == "__main__":
    unittest.main()

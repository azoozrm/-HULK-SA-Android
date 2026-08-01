from __future__ import annotations
import json
from pathlib import Path
import unittest

CONTRACT = Path(__file__).resolve().parents[2] / "lab_fixture_app" / "fixture-contract.json"

class FixtureContractTests(unittest.TestCase):
    def test_contract_has_required_controls_and_faults(self):
        data = json.loads(CONTRACT.read_text())
        controls = set(data["controls"])
        required = {
            "toolbar-wifi", "toolbar-schedule", "toolbar-concurrent",
            "row-1-primary", "row-1-pause", "row-1-resume", "row-1-cancel", "row-1-priority", "row-1-delete",
            "row-2-primary", "row-2-pause", "row-2-resume", "row-2-cancel", "row-2-priority", "row-2-delete",
            "live-item-1", "live-item-2",
        }
        self.assertEqual(required, controls)
        self.assertEqual({"none", "server_stopped", "origin_only", "repository_only"}, set(data["faults"]))

    def test_every_declared_path_starts_from_known_initial_target(self):
        data = json.loads(CONTRACT.read_text())
        self.assertEqual("toolbar-wifi", data["initial_target"])
        self.assertGreaterEqual(data["stability_reads"], 2)
        for target, path in data["paths"].items():
            with self.subTest(target):
                self.assertIn(target, data["controls"])
                self.assertTrue(path)
                self.assertTrue(set(path) <= {"UP", "DOWN", "LEFT", "RIGHT"})

if __name__ == "__main__":
    unittest.main()

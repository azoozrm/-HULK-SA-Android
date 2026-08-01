from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "evidence_gate.py"
SPEC = importlib.util.spec_from_file_location("compatibility_v2_evidence", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class EvidenceGateTest(unittest.TestCase):
    def test_missing_required_evidence_is_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"runtime": ["screenshot.png", "logcat.txt"]}}), encoding="utf-8")
            (root / "screenshot.png").write_bytes(b"png")
            checks = MODULE.gate_evidence(spec, "runtime", root)
            self.assertEqual(["PASS", "FAIL"], [check.status for check in checks])

    def test_empty_evidence_is_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"fast": ["UNIT-TESTS.xml"]}}), encoding="utf-8")
            (root / "UNIT-TESTS.xml").touch()
            checks = MODULE.gate_evidence(spec, "fast", root)
            self.assertEqual("FAIL", checks[0].status)

    def test_complete_non_empty_evidence_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            spec = root / "spec.json"
            spec.write_text(json.dumps({"scopes": {"fast": ["a.txt", "b.xml"]}}), encoding="utf-8")
            (root / "a.txt").write_text("a", encoding="utf-8")
            (root / "b.xml").write_text("<testsuite/>", encoding="utf-8")
            checks = MODULE.gate_evidence(spec, "fast", root)
            self.assertTrue(all(check.status == "PASS" for check in checks))


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "compatibility_gate",
    ROOT / "gate.py",
)
assert SPEC is not None and SPEC.loader is not None
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class GateMarkerReconciliationTest(unittest.TestCase):
    def test_final_xml_marker_reclassifies_only_the_timing_false_positive(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            xml = root / "raw/portrait/font-100/downloads/ui.xml"
            xml.parent.mkdir(parents=True)
            xml.write_text(
                '<hierarchy><node package="sa.hulksa.player.dev" '
                'content-desc="qa-page:downloads,qa-download-transfer:bytes-positive" '
                '/></hierarchy>',
                encoding="utf-8",
            )
            summary = root / "summary.json"
            data = {
                "overall_status": "FAIL",
                "critical_count": 1,
                "warning_count": 0,
                "infrastructure_error_count": 0,
                "cases": [
                    {
                        "id": "portrait/font-100/downloads",
                        "page": "downloads",
                        "marker_found": False,
                        "status": "FAIL",
                    }
                ],
                "findings": [
                    {
                        "severity": "critical",
                        "code": "page_marker_missing",
                        "message": "initial wait timed out",
                        "case_id": "portrait/font-100/downloads",
                        "page": "downloads",
                        "evidence": {
                            "xml": "raw/portrait/font-100/downloads/ui.xml"
                        },
                    }
                ],
            }
            summary.write_text(json.dumps(data), encoding="utf-8")

            corrections = GATE.reconcile_final_page_markers(data, summary)

            self.assertEqual(len(corrections), 1)
            rewritten = json.loads(summary.read_text(encoding="utf-8"))
            self.assertEqual(rewritten["critical_count"], 0)
            self.assertEqual(rewritten["warning_count"], 1)
            self.assertEqual(rewritten["overall_status"], "WARN")
            self.assertTrue(rewritten["cases"][0]["marker_found"])
            self.assertEqual(rewritten["cases"][0]["status"], "WARN")
            self.assertEqual(
                rewritten["findings"][0]["code"],
                "page_marker_reconciled_from_final_hierarchy",
            )
            self.assertTrue((root / "GATE-CORRECTIONS.json").is_file())

    def test_missing_final_marker_remains_critical(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            xml = root / "raw/home/ui.xml"
            xml.parent.mkdir(parents=True)
            xml.write_text("<hierarchy />", encoding="utf-8")
            summary = root / "summary.json"
            data = {
                "overall_status": "FAIL",
                "critical_count": 1,
                "warning_count": 0,
                "infrastructure_error_count": 0,
                "cases": [],
                "findings": [
                    {
                        "severity": "critical",
                        "code": "page_marker_missing",
                        "message": "missing",
                        "case_id": "portrait/font-100/home",
                        "page": "home",
                        "evidence": {"xml": "raw/home/ui.xml"},
                    }
                ],
            }
            summary.write_text(json.dumps(data), encoding="utf-8")

            corrections = GATE.reconcile_final_page_markers(data, summary)

            self.assertEqual(corrections, [])
            self.assertEqual(data["critical_count"], 1)
            self.assertEqual(data["findings"][0]["severity"], "critical")


if __name__ == "__main__":
    unittest.main()

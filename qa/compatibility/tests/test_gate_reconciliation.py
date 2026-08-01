from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
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


class StaleDownloadHierarchyReconciliationTest(unittest.TestCase):
    def _fixture(self, root: Path) -> tuple[dict, Path]:
        search = root / "raw/portrait/font-100/search"
        downloads = root / "raw/portrait/font-100/downloads"
        search.mkdir(parents=True)
        downloads.mkdir(parents=True)
        stale_xml = b'<hierarchy><node content-desc="qa-page:search" /></hierarchy>'
        (search / "ui.xml").write_bytes(stale_xml)
        (downloads / "ui.xml").write_bytes(stale_xml)
        (search / "screenshot.png").write_bytes(b"search-frame")
        (downloads / "screenshot.png").write_bytes(b"downloads-frame")
        records = [
            {
                "historyKey": "QA_DOWNLOAD:1",
                "sourceCandidates": ["http://127.0.0.1:4567/fixture-1.mp4"],
                "bytesDownloaded": 4194304,
            }
        ]
        prefs = (
            '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n'
            '<map><string name="downloads">'
            + json.dumps(records)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
            + "</string></map>\n"
        )
        (downloads / "download-state.xml").write_text(prefs, encoding="utf-8")
        (downloads / "download-files.txt").write_text(
            "-rw-rw---- 1 10086 1015 4194304 2026-07-31 17:00 QA_DOWNLOAD_1.mp4.part\n",
            encoding="utf-8",
        )
        summary = {
            "overall_status": "FAIL",
            "critical_count": 2,
            "warning_count": 0,
            "infrastructure_error_count": 0,
            "cases": [
                {
                    "id": "portrait/font-100/search",
                    "page": "search",
                    "orientation": "portrait",
                    "foreground_package": "sa.hulksa.player.dev",
                    "status": "PASS",
                    "files": {
                        "xml": "raw/portrait/font-100/search/ui.xml",
                        "screenshot": "raw/portrait/font-100/search/screenshot.png",
                    },
                },
                {
                    "id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "orientation": "portrait",
                    "foreground_package": "sa.hulksa.player.dev",
                    "status": "FAIL",
                    "marker_found": False,
                    "files": {
                        "xml": "raw/portrait/font-100/downloads/ui.xml",
                        "screenshot": "raw/portrait/font-100/downloads/screenshot.png",
                        "download_state": "raw/portrait/font-100/downloads/download-state.xml",
                        "download_files": "raw/portrait/font-100/downloads/download-files.txt",
                    },
                },
            ],
            "navigation": [
                {"orientation": "portrait", "page": "downloads", "success": True}
            ],
            "findings": [
                {
                    "severity": "critical",
                    "code": "page_marker_missing",
                    "case_id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "message": "missing",
                    "evidence": {"xml": "raw/portrait/font-100/downloads/ui.xml"},
                },
                {
                    "severity": "critical",
                    "code": "download_transfer_no_byte_progress",
                    "case_id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "message": "no bytes",
                    "evidence": {"xml": "raw/portrait/font-100/downloads/ui.xml"},
                },
            ],
        }
        summary_path = root / "summary.json"
        summary_path.write_text(json.dumps(summary), encoding="utf-8")
        return summary, summary_path

    def test_reconciles_only_with_independent_transport_and_stale_xml_proof(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            summary, summary_path = self._fixture(Path(directory))
            corrections = GATE.reconcile_stale_download_hierarchies(summary, summary_path)
            self.assertEqual(1, len(corrections))
            self.assertEqual(0, summary["critical_count"])
            self.assertEqual("WARN", summary["overall_status"])
            self.assertEqual(
                {
                    "stale_ui_hierarchy_reconciled",
                    "download_transport_proven_by_repository_and_files",
                },
                {finding["code"] for finding in summary["findings"]},
            )
            self.assertEqual(4194304, corrections[0]["transport"]["max_file_bytes"])

    def test_does_not_reconcile_when_download_xml_is_not_stale_search_xml(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            summary, summary_path = self._fixture(Path(directory))
            path = Path(directory) / "raw/portrait/font-100/downloads/ui.xml"
            path.write_bytes(b'<hierarchy><node content-desc="qa-page:downloads" /></hierarchy>')
            self.assertEqual([], GATE.reconcile_stale_download_hierarchies(summary, summary_path))
            self.assertEqual(2, summary["critical_count"])

    def test_does_not_reconcile_without_positive_fixture_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            summary, summary_path = self._fixture(Path(directory))
            path = Path(directory) / "raw/portrait/font-100/downloads/download-files.txt"
            path.write_text("no fixture file evidence\n", encoding="utf-8")
            self.assertEqual([], GATE.reconcile_stale_download_hierarchies(summary, summary_path))
            self.assertEqual(2, summary["critical_count"])

    def test_downstream_findings_do_not_inflate_product_count(self) -> None:
        data = {
            "infrastructure_error_count": 0,
            "findings": [
                {"severity": "critical", "classification": "fixture", "finding_role": "primary", "root_cause_id": "root", "gate_outcome": "BLOCKED"},
                {"severity": "critical", "classification": "future_stage", "finding_role": "downstream", "root_cause_id": "root", "gate_outcome": "RECORDED", "product_strict": False},
            ],
        }
        metrics = GATE._finding_metrics(data)
        self.assertEqual(1, metrics["primary_root_cause_count"])
        self.assertEqual(1, metrics["downstream_count"])
        self.assertEqual(0, metrics["product_critical_count"])


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zlib

from qa.quality.analyzers.evidence import (
    classify_foreground,
    classify_retry,
    has_crash_or_anr,
    intentional_lazy_partial,
    launcher_or_system_contamination,
    png_geometry,
    validate_case_evidence,
    validate_download_evidence,
    validate_focus_trace,
    validate_navigation_trace,
    xml_interactive_overlaps,
)
from qa.quality.reporters.aggregate import REQUIRED_OUTPUTS, aggregate
from qa.quality.reporters.static_summary import build_summary


def png_bytes(width: int, height: int) -> bytes:
    signature = b"\x89PNG\r\n\x1a\n"
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)

    def chunk(name: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + name
            + data
            + struct.pack(">I", zlib.crc32(name + data) & 0xFFFFFFFF)
        )

    raw = b"".join(b"\x00" + b"\x00\x00\x00" * width for _ in range(height))
    return signature + chunk(b"IHDR", ihdr_data) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


class RawEvidenceTest(unittest.TestCase):
    def test_missing_screenshot_and_xml_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            errors = validate_case_evidence(Path(temp))
            self.assertTrue(any("screenshot" in item for item in errors))
            self.assertTrue(any("hierarchy" in item for item in errors))

    def test_wrong_png_geometry_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "screenshot.png").write_bytes(png_bytes(100, 50))
            (root / "hierarchy.xml").write_text("<hierarchy/>", encoding="utf-8")
            (root / "logcat.txt").write_text("", encoding="utf-8")
            (root / "window.txt").write_text(
                "mCurrentFocus=Window{0 u0 sa.hulksa.player.dev/.MainActivity}",
                encoding="utf-8",
            )
            self.assertEqual(png_geometry(root / "screenshot.png"), (100, 50))
            errors = validate_case_evidence(root, expected_width=200, expected_height=100)
            self.assertTrue(any("wrong PNG geometry" in item for item in errors))

    def test_foreground_mismatch_and_launcher_contamination(self) -> None:
        self.assertEqual(
            classify_foreground("mCurrentFocus=Window{0 u0 com.google.android.tvlauncher/.Main}"),
            "non-app",
        )
        with tempfile.TemporaryDirectory() as temp:
            xml = Path(temp) / "hierarchy.xml"
            xml.write_text(
                '<hierarchy><node package="com.google.android.tvlauncher" bounds="[0,0][100,100]"/></hierarchy>',
                encoding="utf-8",
            )
            self.assertTrue(launcher_or_system_contamination(xml))

    def test_crash_and_anr_parsing(self) -> None:
        self.assertTrue(has_crash_or_anr("FATAL EXCEPTION: main\nProcess: sa.hulksa.player"))
        self.assertTrue(has_crash_or_anr("ANR in sa.hulksa.player"))
        self.assertFalse(has_crash_or_anr("ANR in com.android.systemui"))

    def test_parent_child_overlap_is_not_a_product_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            xml = Path(temp) / "tree.xml"
            xml.write_text(
                """<hierarchy>
<node package="sa.hulksa.player.dev" clickable="true" bounds="[0,0][100,100]">
  <node package="sa.hulksa.player.dev" focusable="true" bounds="[0,0][100,100]"/>
</node></hierarchy>""",
                encoding="utf-8",
            )
            self.assertEqual(xml_interactive_overlaps(xml), [])

    def test_sibling_overlap_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            xml = Path(temp) / "tree.xml"
            xml.write_text(
                """<hierarchy>
<node package="sa.hulksa.player.dev" clickable="true" text="a" bounds="[0,0][100,100]"/>
<node package="sa.hulksa.player.dev" focusable="true" text="b" bounds="[5,5][95,95]"/>
</hierarchy>""",
                encoding="utf-8",
            )
            self.assertEqual(len(xml_interactive_overlaps(xml)), 1)

    def test_lazy_list_edge_teaser_policy_is_explicit(self) -> None:
        viewport = (0, 0, 100, 100)
        self.assertTrue(intentional_lazy_partial((-20, 0, 40, 100), viewport, axis="horizontal"))
        self.assertFalse(intentional_lazy_partial((-100, 0, -10, 100), viewport, axis="horizontal"))
        self.assertFalse(intentional_lazy_partial((-20, -5, 40, 105), viewport, axis="horizontal"))


class TraceTest(unittest.TestCase):
    def test_navigation_reachability(self) -> None:
        trace = {
            "nodes": ["home", "details", "player"],
            "start": "home",
            "expected_reachable": ["details", "player"],
            "edges": [{"from": "home", "to": "details"}],
        }
        self.assertEqual(validate_navigation_trace(trace), ["unreachable nodes: ['player']"])

    def test_focus_loss_and_two_node_loop(self) -> None:
        trace = {
            "expected_nodes": ["a", "b", "c"],
            "events": [
                {"node": "a", "visible": True},
                {"node": "b", "visible": True},
            ]
            * 4,
        }
        errors = validate_focus_trace(trace)
        self.assertTrue(any("unreachable" in item for item in errors))
        self.assertIn("two-node focus loop", errors)


class DownloadEvidenceTest(unittest.TestCase):
    def test_positive_growth_resume_and_checksum_pass(self) -> None:
        data = {
            "samples": [
                {"bytes": 0, "part_size": 0},
                {"bytes": 1024, "part_size": 1024},
                {"bytes": 2048, "part_size": 2048},
            ],
            "resumed": True,
            "resume_requested_offset": 1024,
            "resume_observed_offset": 1024,
            "part_writers": ["worker-1", "worker-1"],
            "final": {
                "expected_size": 2048,
                "actual_size": 2048,
                "expected_sha256": "abc",
                "actual_sha256": "abc",
            },
        }
        self.assertEqual(validate_download_evidence(data), [])

    def test_stalled_or_corrupt_download_fails(self) -> None:
        data = {
            "samples": [{"bytes": 0, "part_size": 0}],
            "part_writers": ["worker-1", "worker-2"],
            "final": {
                "expected_size": 10,
                "actual_size": 9,
                "expected_sha256": "a",
                "actual_sha256": "b",
            },
        }
        errors = validate_download_evidence(data)
        self.assertIn("transferred bytes never became positive", errors)
        self.assertIn("partial file never grew", errors)
        self.assertIn("final checksum mismatch", errors)
        self.assertIn("multiple workers wrote the same partial file", errors)

    def test_retry_only_overrides_infrastructure(self) -> None:
        infra = classify_retry(
            {"type": "Infrastructure", "status": "BLOCKED"},
            {"status": "PASS"},
        )
        product = classify_retry(
            {"type": "Product", "status": "FAIL"},
            {"status": "PASS"},
        )
        self.assertTrue(infra["retry_allowed"])
        self.assertEqual(infra["effective_status"], "PASS")
        self.assertFalse(product["retry_allowed"])
        self.assertEqual(product["effective_status"], "FAIL")


class AggregateReporterTest(unittest.TestCase):
    def test_reporter_direct_cli_is_loadable(self) -> None:
        script = Path(__file__).resolve().parents[1] / "reporters" / "aggregate.py"
        result = subprocess.run(
            [sys.executable, str(script), "--help"],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--expected-devices", result.stdout)

    def test_preserved_retry_attempt_is_not_mistaken_for_duplicate_final_result(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            device = root / "input" / "phone"
            attempt = device / "attempts" / "attempt-1"
            attempt.mkdir(parents=True)
            final = {
                "device": {"id": "phone", "api": 35},
                "case_count": 1,
                "cases": [{"status": "PASS", "page": "home"}],
                "findings": [],
                "infrastructure_error_count": 0,
            }
            (device / "summary.json").write_text(json.dumps(final), encoding="utf-8")
            (attempt / "summary.json").write_text(
                json.dumps(
                    {
                        "device": {"id": "phone", "api": 35},
                        "case_count": 0,
                        "cases": [],
                        "findings": [],
                        "infrastructure_error_count": 1,
                    }
                ),
                encoding="utf-8",
            )
            result = aggregate(
                root / "input",
                root / "output",
                build_sha="c" * 40,
                source_branch="test",
                workflow="unit",
                run_id="retry",
                expected_devices=["phone"],
                impact={"schema_version": 1},
            )
            self.assertEqual(result["release_recommendation"], "PASS")
            self.assertEqual(result["parse_errors"], [])

    def test_missing_expected_artifact_is_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            result = aggregate(
                root / "input",
                root / "output",
                build_sha="a" * 40,
                source_branch="test",
                workflow="unit",
                run_id="1",
                expected_devices=["tv"],
                impact={"schema_version": 1},
            )
            self.assertEqual(result["release_recommendation"], "BLOCKED")
            for name in REQUIRED_OUTPUTS + ("SHA256SUMS",):
                self.assertTrue((root / "output" / name).is_file(), name)

    def test_report_generation_preserves_arabic_and_counts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            device = root / "input" / "phone"
            device.mkdir(parents=True)
            (device / "summary.json").write_text(
                json.dumps(
                    {
                        "device": {"id": "phone", "api": 35},
                        "case_count": 1,
                        "cases": [{"status": "PASS", "page": "home"}],
                        "findings": [
                            {
                                "severity": "warning",
                                "code": "arabic_layout_review",
                                "message": "مراجعة محاذاة النص العربي",
                                "page": "home",
                            }
                        ],
                        "infrastructure_error_count": 0,
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            result = aggregate(
                root / "input",
                root / "output",
                build_sha="b" * 40,
                source_branch="test",
                workflow="unit",
                run_id="2",
                expected_devices=["phone"],
                impact={"schema_version": 1},
                pr_number="59",
                source_head_sha="c" * 40,
                base_sha="d" * 40,
                test_variant="unit-fixture",
                run_attempt="3",
            )
            self.assertEqual(result["release_recommendation"], "PASS WITH WARNINGS")
            self.assertEqual(result["pr_number"], "59")
            self.assertEqual(result["source_head_sha"], "c" * 40)
            self.assertEqual(result["test_variant"], "unit-fixture")
            manifest = json.loads(
                (root / "output" / "run-manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual(manifest["base_sha"], "d" * 40)
            self.assertEqual(manifest["run_attempt"], "3")
            self.assertIn(
                "مراجعة محاذاة",
                (root / "output" / "REPORT.md").read_text(encoding="utf-8"),
            )

    def test_expected_matrix_gaps_and_warning_cases_are_counted_truthfully(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            blocked = root / "input" / "tv"
            warning = root / "input" / "phone"
            blocked.mkdir(parents=True)
            warning.mkdir(parents=True)
            (blocked / "summary.json").write_text(
                json.dumps(
                    {
                        "device": {"id": "tv", "api": 35},
                        "overall_status": "BLOCKED",
                        "expected_case_count": 8,
                        "case_count": 0,
                        "cases": [],
                        "findings": [
                            {
                                "severity": "infrastructure",
                                "finding_type": "Infrastructure",
                                "code": "emulator_boot_failed",
                                "message": "emulator did not become usable",
                            }
                        ],
                        "infrastructure_error_count": 1,
                    }
                ),
                encoding="utf-8",
            )
            (warning / "summary.json").write_text(
                json.dumps(
                    {
                        "device": {"id": "phone", "api": 35},
                        "overall_status": "WARN",
                        "expected_case_count": 1,
                        "case_count": 1,
                        "cases": [{"status": "WARN", "page": "home"}],
                        "findings": [
                            {
                                "severity": "warning",
                                "code": "advisory_geometry",
                                "message": "manual geometry review",
                            }
                        ],
                        "infrastructure_error_count": 0,
                    }
                ),
                encoding="utf-8",
            )
            result = aggregate(
                root / "input",
                root / "output",
                build_sha="e" * 40,
                source_branch="test",
                workflow="unit",
                run_id="matrix-counts",
                expected_devices=["tv", "phone"],
                impact={"schema_version": 1},
            )
            self.assertEqual(result["release_recommendation"], "BLOCKED")
            self.assertEqual(result["planned"], 9)
            self.assertEqual(result["executed"], 1)
            self.assertEqual(result["passed"], 1)
            self.assertEqual(result["failed"], 0)
            self.assertEqual(result["skipped"], 8)
            self.assertEqual(result["infrastructure"], 1)
            suite = ET.parse(root / "output" / "junit.xml").getroot()
            self.assertEqual(suite.attrib["tests"], "9")
            self.assertEqual(suite.attrib["skipped"], "8")


class StaticSummaryTest(unittest.TestCase):
    def test_lint_and_unexecuted_vulnerability_are_not_silent_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lint = root / "lint.xml"
            lint.write_text(
                '<issues><issue id="A" severity="Warning"/>'
                '<issue id="B" severity="Information"/></issues>',
                encoding="utf-8",
            )
            vulnerability = root / "vulnerability.json"
            vulnerability.write_text(
                json.dumps({"status": "NOT_EXECUTED", "reason": "scanner absent"}),
                encoding="utf-8",
            )
            summary = build_summary(
                gradle_outcome="success",
                package_outcome="success",
                lint_path=lint,
                vulnerability_path=vulnerability,
            )
            self.assertEqual(summary["infrastructure_error_count"], 0)
            self.assertEqual(
                {item["code"] for item in summary["findings"]},
                {"android_lint_advisories", "vulnerability_scan_not_executed"},
            )
            self.assertIn("2 warning/advisory", summary["findings"][0]["message"])

    def test_missing_expected_static_evidence_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            summary = build_summary(
                gradle_outcome="success",
                package_outcome="success",
                lint_path=root / "missing-lint.xml",
                vulnerability_path=root / "missing-vulnerability.json",
            )
            self.assertEqual(summary["infrastructure_error_count"], 2)
            self.assertEqual(
                {item["finding_type"] for item in summary["findings"]},
                {"Infrastructure"},
            )


if __name__ == "__main__":
    unittest.main()

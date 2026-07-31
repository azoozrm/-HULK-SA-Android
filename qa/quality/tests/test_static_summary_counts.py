from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from qa.quality.reporters.static_summary import build_summary
from qa.quality.reporters.aggregate import aggregate


class StaticSummaryCountContractTest(unittest.TestCase):
    def test_static_summary_preserves_complete_provenance(self) -> None:
        provenance = {
            "source_head_sha": "a" * 40,
            "base_sha": "b" * 40,
            "tested_ref": "refs/pull/57/merge",
            "tested_commit_sha": "c" * 40,
            "merge_sha": "c" * 40,
            "lab_apk_sha256": "d" * 64,
            "workflow_run_id": "123",
            "workflow_run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lint = root / "lint.xml"
            lint.write_text("<issues />", encoding="utf-8")
            vulnerability = root / "vulnerability.json"
            vulnerability.write_text(
                json.dumps({"status": "PASS"}), encoding="utf-8"
            )
            summary = build_summary(
                gradle_outcome="success",
                package_outcome="success",
                lint_path=lint,
                vulnerability_path=vulnerability,
                provenance=provenance,
            )
        self.assertEqual(summary["provenance"], provenance)

    def test_explicit_skipped_case_is_not_counted_as_a_second_matrix_gap(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            lint = root / "lint.xml"
            lint.write_text("<issues />", encoding="utf-8")
            vulnerability = root / "vulnerability.json"
            vulnerability.write_text(
                json.dumps({"status": "NOT_EXECUTED", "reason": "scanner absent"}),
                encoding="utf-8",
            )
            provenance = {
                "source_head_sha": "a" * 40,
                "base_sha": "b" * 40,
                "tested_ref": "refs/pull/57/merge",
                "tested_commit_sha": "c" * 40,
                "merge_sha": "c" * 40,
                "lab_apk_sha256": "d" * 64,
                "workflow_run_id": "static-counts",
                "workflow_run_attempt": "1",
            }
            static = build_summary(
                gradle_outcome="success",
                package_outcome="success",
                lint_path=lint,
                vulnerability_path=vulnerability,
                provenance=provenance,
            )
            input_dir = root / "input/static-build"
            input_dir.mkdir(parents=True)
            (input_dir / "summary.json").write_text(
                json.dumps(static),
                encoding="utf-8",
            )

            result = aggregate(
                root / "input",
                root / "output",
                build_sha="a" * 40,
                source_branch="quality-test",
                workflow="unit",
                run_id="static-counts",
                expected_devices=["static-build"],
                impact={"schema_version": 1},
                source_head_sha=provenance["source_head_sha"],
                base_sha=provenance["base_sha"],
                tested_ref=provenance["tested_ref"],
                tested_commit_sha=provenance["tested_commit_sha"],
                merge_sha=provenance["merge_sha"],
                lab_apk_sha256=provenance["lab_apk_sha256"],
                run_attempt=provenance["workflow_run_attempt"],
            )

            self.assertEqual(result["release_recommendation"], "PASS WITH WARNINGS")
            self.assertEqual(result["infrastructure"], 0)
            self.assertEqual(result["planned"], 3)
            self.assertEqual(result["executed"], 3)
            self.assertEqual(result["passed"], 2)
            self.assertEqual(result["failed"], 0)
            self.assertEqual(result["skipped"], 1)

    def test_reports_keep_quality_lab_and_fixture_counts(self) -> None:
        source = Path("qa/quality/reporters/aggregate.py").read_text(encoding="utf-8")
        self.assertIn('"quality_lab_critical"', source)
        self.assertIn('"fixture_critical"', source)
        self.assertIn('reclassified_from', source)


if __name__ == "__main__":
    unittest.main()

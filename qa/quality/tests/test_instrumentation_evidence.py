from pathlib import Path
import tempfile
import unittest

from qa.quality.scripts.instrumentation_evidence import write_evidence


class InstrumentationEvidenceTest(unittest.TestCase):
    def test_missing_results_are_blocked_and_still_create_artifact_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            summary = write_evidence(
                output=root / "evidence",
                head_sha="a" * 40,
                run_id="123",
                run_attempt="1",
                step_outcome="failure",
                results_root=root / "missing-results",
                reports_root=root / "missing-reports",
            )

            self.assertEqual("BLOCKED", summary["overall_status"])
            self.assertEqual("infrastructure", summary["classification"])
            self.assertEqual(0, summary["executed"])
            for filename in ("SUMMARY.json", "REPORT.md", "run-manifest.json", "SHA256SUMS"):
                self.assertTrue((root / "evidence" / filename).is_file())

    def test_junit_failures_are_product_failures(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            results = root / "results"
            reports = root / "reports"
            results.mkdir()
            reports.mkdir()
            (results / "TEST-fixture.xml").write_text(
                '<testsuite tests="3" failures="1" errors="0" skipped="1" />',
                encoding="utf-8",
            )
            (reports / "index.html").write_text("<html></html>", encoding="utf-8")

            summary = write_evidence(
                output=root / "evidence",
                head_sha="b" * 40,
                run_id="456",
                run_attempt="2",
                step_outcome="failure",
                results_root=results,
                reports_root=reports,
            )

            self.assertEqual("FAIL", summary["overall_status"])
            self.assertEqual("product", summary["classification"])
            self.assertEqual(1, summary["passed"])
            self.assertEqual(1, summary["failed"])
            self.assertEqual(1, summary["skipped"])

    def test_clean_junit_and_report_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            results = root / "results"
            reports = root / "reports"
            results.mkdir()
            reports.mkdir()
            (results / "TEST-fixture.xml").write_text(
                '<testsuite tests="4" failures="0" errors="0" skipped="0" />',
                encoding="utf-8",
            )
            (reports / "index.html").write_text("<html></html>", encoding="utf-8")

            summary = write_evidence(
                output=root / "evidence",
                head_sha="c" * 40,
                run_id="789",
                run_attempt="1",
                step_outcome="success",
                results_root=results,
                reports_root=reports,
            )

            self.assertEqual("PASS", summary["overall_status"])
            self.assertEqual("none", summary["classification"])
            self.assertEqual(4, summary["executed"])
            self.assertEqual(4, summary["passed"])


if __name__ == "__main__":
    unittest.main()

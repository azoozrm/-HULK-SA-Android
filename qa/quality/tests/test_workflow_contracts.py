from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
WORKFLOWS = ROOT / ".github/workflows"


class WorkflowContractTest(unittest.TestCase):
    def source(self, name: str) -> str:
        return (WORKFLOWS / name).read_text(encoding="utf-8")

    def test_required_quality_workflows_exist_with_final_enforcement(self) -> None:
        expectations = {
            "quality-pr.yml": "Final PR quality enforcement",
            "quality-ui.yml": "Final UI evidence enforcement",
            "quality-nightly.yml": "Final nightly enforcement",
            "quality-release.yml": "Final release recommendation",
            "quality-pr-intelligence.yml": "Final impact enforcement",
        }
        for filename, final_job in expectations.items():
            source = self.source(filename)
            self.assertIn(final_job, source)
            self.assertIn("timeout-minutes:", source)
            self.assertIn("concurrency:", source)

    def test_compatibility_lab_is_reusable_and_aggregates_all_device_evidence(self) -> None:
        source = self.source("compatibility-lab.yml")
        trigger = source.split("permissions:", 1)[0]
        self.assertIn("workflow_call:", trigger)
        self.assertNotIn("pull_request:", trigger)
        self.assertNotIn("\n  push:", trigger)
        self.assertIn("attempts/attempt-1", source)
        self.assertIn("aggregate-evidence:", source)
        self.assertIn("Final fail-closed enforcement", source)
        for trace_field in (
            "source_head_sha:",
            "base_sha:",
            "pr_number:",
            "test_variant:",
            "--run-attempt",
            "--source-head-sha",
        ):
            self.assertIn(trace_field, source)

    def test_pr_workflows_do_not_receive_signing_or_production_credentials(self) -> None:
        for filename in ("quality-pr.yml", "quality-ui.yml", "quality-pr-intelligence.yml"):
            source = self.source(filename)
            self.assertNotIn("HULK_RELEASE_KEYSTORE", source)
            self.assertNotIn("HULK_RELEASE_STORE_PASSWORD", source)
            self.assertNotIn("production-signing", source)

    def test_signed_workflow_has_no_placeholder_fallback(self) -> None:
        source = self.source("signed-release-qualification.yml")
        self.assertNotIn("example.invalid", source)
        self.assertIn("http://3162356.xyz:8080", source)
        self.assertIn("verify-runtime-config.py", source)
        self.assertIn("HULK_CONFIG_URL: ''", source)

    def test_canonical_release_uses_verified_production_endpoint(self) -> None:
        source = self.source("canonical-build.yml")
        self.assertNotIn("example.invalid", source)
        self.assertIn("HULK_PORTAL_URL: http://3162356.xyz:8080", source)
        self.assertIn("HULK_CONFIG_URL: ''", source)
        self.assertIn("verify-runtime-config.py", source)
        self.assertIn("build/reports/runtime-config", source)

    def test_release_gate_requires_external_evidence_and_never_claims_it(self) -> None:
        source = self.source("quality-release.yml")
        self.assertIn("physical_evidence_run_id", source)
        self.assertIn("protected_smoke_run_id", source)
        self.assertIn('device_type == "physical"', source)
        self.assertIn("real_download_bytes > 0", source)
        self.assertNotIn("continue-on-error:", source)

    def test_report_only_is_never_used_to_skip_final_assertions(self) -> None:
        for filename in ("quality-pr.yml", "quality-ui.yml", "quality-nightly.yml"):
            source = self.source(filename)
            self.assertNotIn("if: inputs.report_only", source)
            self.assertNotIn("if: ${{ inputs.report_only", source)

    def test_full_python_self_test_jobs_install_visual_dependencies(self) -> None:
        for filename in ("quality-pr.yml", "quality-nightly.yml"):
            source = self.source(filename)
            requirements = "-r qa/compatibility/requirements.txt"
            self.assertIn(requirements, source)
            self.assertLess(
                source.index(requirements),
                source.index(
                    "python3 -m unittest discover -s qa/quality/tests"
                ),
            )

    def test_instrumentation_layer_uploads_fail_closed_evidence_before_asserting(self) -> None:
        source = self.source("quality-ui.yml")
        self.assertIn("Prepare fail-closed instrumentation evidence", source)
        self.assertIn("Finalize instrumentation evidence", source)
        self.assertIn("quality-evidence/instrumentation/", source)
        self.assertIn("steps.instrumented.outcome", source)
        self.assertIn("'.overall_status == \"PASS\"'", source)
        self.assertIn("if-no-files-found: error", source)
        self.assertIn("github.event.pull_request.head.sha || github.sha", source)
        self.assertIn("test_variant: quality-ui", source)

    def test_pr_report_records_real_impact_and_pr_traceability(self) -> None:
        source = self.source("quality-pr.yml")
        for expected in (
            "quick-impact/impact.json",
            "--pr-number",
            "--source-head-sha",
            "--base-sha",
            "--test-variant",
            "qa.quality.reporters.static_summary",
        ):
            self.assertIn(expected, source)


if __name__ == "__main__":
    unittest.main()

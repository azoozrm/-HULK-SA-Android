from __future__ import annotations

from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("compatibility_gate_diagnostics", ROOT / "gate.py")
assert SPEC is not None and SPEC.loader is not None
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class GateDiagnosticsTest(unittest.TestCase):
    def sample(self) -> dict[str, object]:
        return {
            "critical_count": 1,
            "warning_count": 2,
            "infrastructure_error_count": 0,
            "findings": [
                {
                    "severity": "critical",
                    "code": "download_transfer_no_byte_progress",
                    "case_id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "message": "origin served bytes but repository exposed zero progress",
                    "evidence": {
                        "screenshot": "raw/portrait/downloads/screenshot.png",
                        "xml": "raw/portrait/downloads/ui.xml",
                    },
                }
            ],
        }

    def test_enforced_failure_prints_actionable_finding_and_annotation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            summary = root / "summary.json"
            step_summary = root / "step-summary.md"
            summary.write_text(json.dumps(self.sample()), encoding="utf-8")
            output = io.StringIO()
            with mock.patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(step_summary)}):
                with redirect_stdout(output):
                    result = GATE.evaluate_gate(self.sample(), summary, True)

            rendered = output.getvalue()
            self.assertEqual(result, 1)
            self.assertIn("download_transfer_no_byte_progress", rendered)
            self.assertIn("portrait/font-100/downloads", rendered)
            self.assertIn("origin served bytes", rendered)
            self.assertIn("::error title=", rendered)
            markdown = step_summary.read_text(encoding="utf-8")
            self.assertIn("Critical compatibility diagnostics", markdown)
            self.assertIn("raw/portrait/downloads/ui.xml", markdown)

    def test_lab_only_mode_preserves_finding_but_returns_success(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            result = GATE.evaluate_gate(self.sample(), Path("summary.json"), False)
        self.assertEqual(result, 0)
        self.assertIn("DETECTED:", output.getvalue())
        self.assertIn("product enforcement is disabled only for this lab-only qualification", output.getvalue())

    def test_fixture_precondition_is_blocked_not_product(self) -> None:
        data = {
            "infrastructure_error_count": 0,
            "findings": [{
                "severity": "critical", "code": "download_action_start_state_not_established",
                "classification": "fixture", "finding_role": "primary", "root_cause_id": "r1",
                "gate_outcome": "BLOCKED", "product_strict": False,
            }],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "summary.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            self.assertEqual(2, GATE.evaluate_gate(data, path, True))

    def test_valid_product_callback_failure_stays_fail(self) -> None:
        data = {
            "infrastructure_error_count": 0,
            "findings": [{
                "severity": "critical", "code": "tv_download_action_not_executed",
                "classification": "product", "finding_role": "primary", "root_cause_id": "p1",
                "gate_outcome": "FAIL", "product_strict": True,
            }],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "summary.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            self.assertEqual(1, GATE.evaluate_gate(data, path, True))


if __name__ == "__main__":
    unittest.main()

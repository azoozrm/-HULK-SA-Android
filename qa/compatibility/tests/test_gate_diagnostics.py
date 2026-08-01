from __future__ import annotations

from contextlib import redirect_stdout
import importlib.util
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
SPEC = importlib.util.spec_from_file_location(
    "compatibility_gate_diagnostics",
    ROOT / "gate.py",
)
assert SPEC is not None and SPEC.loader is not None
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class GateDiagnosticsTest(unittest.TestCase):
    def sample(self) -> dict[str, object]:
        return {
            "findings": [
                {
                    "severity": "critical",
                    "code": "download_transfer_no_byte_progress",
                    "case_id": "portrait/font-100/downloads",
                    "page": "downloads",
                    "message": "origin served bytes but repository exposed zero progress",
                    "classification": "product",
                    "root_cause_id": "download-boundary:portrait/font-100/downloads",
                    "finding_role": "primary",
                    "evidence": {
                        "screenshot": "raw/portrait/downloads/screenshot.png",
                        "xml": "raw/portrait/downloads/ui.xml",
                    },
                }
            ],
            "download_actions": [],
            "provenance": {
                "source_head_sha": "a" * 40,
                "base_sha": "b" * 40,
                "tested_commit_sha": "a" * 40,
                "merge_sha": "c" * 40,
                "lab_apk_sha256": "d" * 64,
                "workflow_run_id": "1",
                "workflow_run_attempt": "1",
            },
        }

    def test_product_finding_is_actionable_and_report_only_passes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            summary = root / "summary.json"
            step_summary = root / "step-summary.md"
            output = io.StringIO()
            with mock.patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(step_summary)}):
                with redirect_stdout(output):
                    result = GATE.evaluate_gate(self.sample(), summary, False)

            rendered = output.getvalue()
            self.assertEqual(0, result)
            self.assertIn("download_transfer_no_byte_progress", rendered)
            self.assertIn("portrait/font-100/downloads", rendered)
            self.assertIn("origin served bytes", rendered)
            self.assertIn("::warning title=PRODUCT", rendered)
            self.assertIn("PASS:", rendered)
            markdown = step_summary.read_text(encoding="utf-8")
            self.assertIn("Compatibility qualification decision", markdown)
            self.assertIn("Product roots: `1`", markdown)
            self.assertIn("Lab roots: `0`", markdown)

    def test_strict_mode_fails_on_same_product_root(self):
        output = io.StringIO()
        with redirect_stdout(output):
            result = GATE.evaluate_gate(self.sample(), Path("summary.json"), True)
        self.assertEqual(1, result)
        self.assertIn("FAIL_PRODUCT:", output.getvalue())


if __name__ == "__main__":
    unittest.main()

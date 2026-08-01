from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
SPEC = importlib.util.spec_from_file_location(
    "compatibility_gate_reconciliation",
    ROOT / "gate.py",
)
assert SPEC is not None and SPEC.loader is not None
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class GateReconciliationTest(unittest.TestCase):
    def provenance(self) -> dict[str, str]:
        return {
            "source_head_sha": "a" * 40,
            "base_sha": "b" * 40,
            "tested_commit_sha": "a" * 40,
            "merge_sha": "c" * 40,
            "lab_apk_sha256": "d" * 64,
            "workflow_run_id": "1",
            "workflow_run_attempt": "1",
        }

    def test_duplicate_same_root_becomes_downstream_not_second_root(self):
        data = {
            "download_actions": [],
            "provenance": self.provenance(),
            "findings": [
                {
                    "severity": "critical",
                    "code": "download_origin_repository_boundary_mismatch",
                    "case_id": "landscape/downloads",
                    "classification": "product",
                    "root_cause_id": "download-boundary:landscape/downloads",
                    "finding_role": "primary",
                    "message": "origin has bytes, repository has zero",
                },
                {
                    "severity": "critical",
                    "code": "download_transfer_no_byte_progress",
                    "case_id": "landscape/downloads",
                    "classification": "product",
                    "root_cause_id": "download-boundary:landscape/downloads",
                    "finding_role": "primary",
                    "message": "same boundary expressed by a downstream assertion",
                },
            ],
        }
        normalized = GATE.normalize_summary(data)
        self.assertEqual(1, normalized["primary_root_cause_count"])
        self.assertEqual(1, normalized["downstream_count"])
        self.assertEqual(1, normalized["product_critical_count"])
        self.assertEqual((0, "PASS"), GATE.gate_decision(normalized, False))
        self.assertEqual((1, "FAIL_PRODUCT"), GATE.gate_decision(normalized, True))

    def test_missing_provenance_is_blocked_in_report_only_mode(self):
        data = {
            "findings": [],
            "download_actions": [],
            "provenance": {
                "source_head_sha": "a" * 40,
                "base_sha": "b" * 40,
            },
        }
        normalized = GATE.normalize_summary(data)
        self.assertEqual("BLOCKED", normalized["overall_status"])
        self.assertEqual(1, normalized["blocked_root_count"])
        self.assertEqual((2, "BLOCKED"), GATE.gate_decision(normalized, False))


if __name__ == "__main__":
    unittest.main()

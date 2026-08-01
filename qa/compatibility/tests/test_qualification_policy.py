from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from copy import deepcopy
import unittest

from qualification_policy import gate_decision, graph_contradictions, normalize_summary, retry_allowed


class QualificationPolicyTests(unittest.TestCase):
    def base(self):
        return {
            "findings": [],
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

    def test_static_graph_contradiction_is_one_lab_root(self):
        summary = self.base()
        checks = []
        for check_id, expected in (("row-1-cancel", "row-1-cancel"), ("row-1-priority", "row-1-priority"), ("row-2-cancel", "row-2-cancel")):
            checks.append({
                "id": check_id,
                "success": False,
                "source": "PRODUCT",
                "precondition_established": False,
                "reason": f"NAVIGATION_TARGET_MISMATCH: DOWN expected {expected}, observed row-1-primary",
                "key_events": [{"key": "DOWN", "focused_before": "toolbar-concurrent", "expected_target": expected, "actual_target": "row-1-primary", "success": False}],
            })
        summary["download_actions"] = [{"orientation": "landscape", "checks": checks}]
        result = normalize_summary(summary)
        self.assertEqual("BLOCKED", result["overall_status"])
        self.assertEqual(1, result["quality_lab_critical_count"])
        self.assertEqual(1, result["primary_root_cause_count"])
        self.assertEqual(2, result["downstream_count"])
        self.assertEqual(0, result["product_critical_count"])

    def test_product_callback_requires_target_and_key_proof(self):
        summary = self.base()
        summary["download_actions"] = [{"orientation": "landscape", "checks": [{
            "id": "row-1-pause",
            "success": False,
            "source": "PRODUCT",
            "precondition_established": True,
            "key_press_confirmed": True,
            "expected_action": "pause",
            "reason": "ACTION_CALLBACK_NOT_EXECUTED: pause revision did not advance",
        }]}]
        result = normalize_summary(summary)
        self.assertEqual("FAIL", result["overall_status"])
        self.assertEqual(1, result["product_critical_count"])
        self.assertEqual((0, "PASS"), gate_decision(summary, False))
        self.assertEqual((1, "FAIL_PRODUCT"), gate_decision(summary, True))

    def test_unproven_action_is_blocked_not_product(self):
        summary = self.base()
        summary["download_actions"] = [{"orientation": "landscape", "checks": [{
            "id": "row-1-pause",
            "success": False,
            "source": "PRODUCT",
            "precondition_established": False,
            "key_press_confirmed": False,
            "expected_action": "pause",
            "reason": "ACTION_CALLBACK_NOT_EXECUTED: pause revision did not advance",
        }]}]
        result = normalize_summary(summary)
        self.assertEqual("BLOCKED", result["overall_status"])
        self.assertEqual(0, result["product_critical_count"])
        self.assertEqual(1, result["blocked_root_count"])

    def test_missing_provenance_is_blocked(self):
        summary = self.base(); summary["provenance"].pop("merge_sha")
        result = normalize_summary(summary)
        self.assertEqual("BLOCKED", result["overall_status"])
        self.assertEqual(1, result["blocked_root_count"])

    def test_retry_is_not_allowed_for_assertions(self):
        self.assertTrue(retry_allowed("adb_disconnected"))
        self.assertFalse(retry_allowed("navigation_target_mismatch"))
        self.assertFalse(retry_allowed("action_callback_not_executed"))


if __name__ == "__main__":
    unittest.main()

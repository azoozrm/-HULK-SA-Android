from __future__ import annotations

import unittest

from qa.quality.reporters.aggregate.__main__ import policy_exit_code


class AggregateCliPolicyTest(unittest.TestCase):
    def test_lab_only_product_findings_are_preserved_without_cli_failure(self) -> None:
        summary = {
            "release_recommendation": "FAIL",
            "infrastructure": 0,
            "product_critical": 49,
            "test_variant": "quality-ui-lab-only",
        }
        self.assertEqual(
            0,
            policy_exit_code(1, "quality-ui-lab-only", summary),
        )

    def test_product_strict_failure_remains_fatal(self) -> None:
        summary = {
            "release_recommendation": "FAIL",
            "infrastructure": 0,
            "product_critical": 1,
            "test_variant": "quality-ui-product-strict",
        }
        self.assertEqual(
            1,
            policy_exit_code(1, "quality-ui-product-strict", summary),
        )

    def test_blocked_result_can_never_be_bypassed(self) -> None:
        summary = {
            "release_recommendation": "BLOCKED",
            "infrastructure": 1,
            "product_critical": 0,
            "test_variant": "quality-ui-lab-only",
        }
        self.assertEqual(
            2,
            policy_exit_code(2, "quality-ui-lab-only", summary),
        )

    def test_mismatched_summary_variant_fails_closed(self) -> None:
        summary = {
            "release_recommendation": "FAIL",
            "infrastructure": 0,
            "product_critical": 1,
            "test_variant": "quality-ui-product-strict",
        }
        self.assertEqual(
            1,
            policy_exit_code(1, "quality-ui-lab-only", summary),
        )


if __name__ == "__main__":
    unittest.main()

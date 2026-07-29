from __future__ import annotations

import unittest

from qa.quality.inventory.pr_inventory import classify_paths


class PullRequestInventoryTest(unittest.TestCase):
    def test_mixed_product_and_lab_change_is_high_risk(self) -> None:
        result = classify_paths(
            [
                "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt",
                "qa/compatibility/analyze.py",
            ],
        )

        self.assertTrue(result.contains_product_changes)
        self.assertTrue(result.contains_lab_changes)
        self.assertEqual("high-mixed", result.regression_risk)

    def test_quality_workflow_is_lab_change(self) -> None:
        result = classify_paths([".github/workflows/quality-pr.yml"])

        self.assertFalse(result.contains_product_changes)
        self.assertTrue(result.contains_lab_changes)
        self.assertIn("workflow", result.kinds)

    def test_android_test_is_lab_not_product(self) -> None:
        result = classify_paths(
            ["app/src/androidTest/java/sa/hulksa/player/CompatibilitySmokeTest.kt"],
        )

        self.assertTrue(result.contains_lab_changes)
        self.assertFalse(result.contains_product_changes)

    def test_documentation_only_change_is_low_risk(self) -> None:
        result = classify_paths(["docs/quality/HANDOFF.md"])

        self.assertEqual(("documentation",), result.kinds)
        self.assertEqual("low", result.regression_risk)


if __name__ == "__main__":
    unittest.main()

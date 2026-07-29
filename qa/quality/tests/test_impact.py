from pathlib import Path
import tempfile
import unittest

from qa.quality.impact.classifier import classify
from qa.quality.inventory.ui_inventory import discover


ROOT = Path(__file__).resolve().parents[3]


class ImpactClassifierTest(unittest.TestCase):
    def test_compose_change_selects_visual_focus_and_accessibility(self) -> None:
        result = classify(
            ["app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"]
        )
        for expected in ("visual", "focus", "navigation", "accessibility"):
            self.assertIn(expected, result["selected_tests"])
        self.assertFalse(result["full_matrix"])

    def test_download_repository_selects_resume_storage_and_network(self) -> None:
        result = classify(
            ["app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt"]
        )
        for expected in ("download-resume", "storage", "network-resilience"):
            self.assertIn(expected, result["selected_tests"])

    def test_unknown_change_fails_safe_to_full_matrix(self) -> None:
        result = classify(["unclassified/new-system.bin"])
        self.assertTrue(result["full_matrix"])
        self.assertIn("bundletool", result["selected_tests"])

    def test_workflow_or_analyzer_change_runs_lab_self_tests(self) -> None:
        result = classify([".github/workflows/quality-pr.yml", "qa/quality/analyzers/new.py"])
        self.assertIn("quality-self-tests", result["selected_tests"])
        self.assertIn("schema-validation", result["selected_tests"])


class UiInventoryTest(unittest.TestCase):
    def test_discovers_destinations_and_non_shell_screens(self) -> None:
        screens, actions = discover(ROOT)
        screen_ids = {item["id"] for item in screens}
        for expected in (
            "destination:home",
            "destination:downloads",
            "composable:PlayerScreen",
            "composable:MovieDetailsScreen",
            "composable:SeriesScreen",
            "composable:LoginScreen",
        ):
            self.assertIn(expected, screen_ids)
        self.assertTrue(any(item["action"] == "long-click" for item in actions))
        self.assertTrue(any(item["review_required"] for item in screens))

    def test_inventory_is_deterministic(self) -> None:
        first = discover(ROOT)
        second = discover(ROOT)
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()

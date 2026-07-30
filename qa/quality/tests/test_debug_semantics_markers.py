from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = (
    ROOT
    / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
)
QA_ACTIVITY = ROOT / "qa/compatibility/QaActivity.kt"
MARKER_INJECTOR = ROOT / "qa/compatibility/inject_quality_markers.py"


class DebugSemanticsMarkersTest(unittest.TestCase):
    def setUp(self) -> None:
        self.production_source = MAIN_SHELL.read_text(encoding="utf-8")
        self.qa_source = QA_ACTIVITY.read_text(encoding="utf-8")
        self.injector_source = MARKER_INJECTOR.read_text(encoding="utf-8")

    def test_production_shell_contains_no_quality_only_semantics(self) -> None:
        for marker in (
            "qa-page:",
            "qa-tv-page-content:",
            "qa-tv-rail",
            "qa-tv-live-actions",
            "qa-tv-download-list",
            "qa-tv-download-card:",
            ".qaTvPageContent(",
        ):
            self.assertNotIn(marker, self.production_source)

    def test_debug_harness_owns_page_and_download_progress_markers(self) -> None:
        for marker in (
            'val pageMarker = "qa-page:',
            "QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER",
            "QA_DOWNLOAD_PROGRESS_MARKER",
            ".semantics(mergeDescendants = false)",
        ):
            self.assertIn(marker, self.qa_source)

    def test_disposable_injector_owns_tv_geometry_markers(self) -> None:
        for marker in (
            "qa-tv-page-content:",
            "qa-tv-rail",
            "qa-tv-live-actions",
            "qa-tv-download-list",
            "qa-tv-download-card:",
            "BuildConfig.DEBUG",
            "expected exactly one match",
        ):
            self.assertIn(marker, self.injector_source)

    def test_debug_harness_maps_every_shell_destination(self) -> None:
        for destination in (
            "HOME",
            "LIVE",
            "MOVIES",
            "SERIES",
            "FAVORITES",
            "SEARCH",
            "DOWNLOADS",
            "SETTINGS",
        ):
            self.assertIn(
                f"MainDestination.{destination}",
                self.qa_source,
                msg=f"missing debug destination mapping for {destination}",
            )


if __name__ == "__main__":
    unittest.main()

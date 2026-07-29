from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = (
    ROOT
    / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
)


class DebugSemanticsMarkersTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = MAIN_SHELL.read_text(encoding="utf-8")

    def test_markers_are_debug_only(self) -> None:
        self.assertIn("isTv && BuildConfig.DEBUG", self.source)
        self.assertIn("enabled && BuildConfig.DEBUG", self.source)
        self.assertNotIn("testTag(", self.source)

    def test_all_shell_destinations_have_page_geometry_marker(self) -> None:
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
                self.source,
                msg=f"missing destination reference for {destination}",
            )

        self.assertEqual(
            self.source.count(".qaTvPageContent("),
            8,
            msg="expected one helper plus seven explicit calls, including the two catalog destinations",
        )

    def test_download_and_live_geometry_markers_exist(self) -> None:
        for marker in (
            "qa-tv-rail",
            "qa-tv-live-actions",
            "qa-tv-download-list",
            "qa-tv-download-card:",
        ):
            self.assertIn(marker, self.source)


if __name__ == "__main__":
    unittest.main()

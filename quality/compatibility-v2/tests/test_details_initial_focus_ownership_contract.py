from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
DETAILS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProTvPolishScreens.kt"
MOVIES = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MoviesApprovedUi.kt"
SERIES = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SeriesApprovedUi.kt"


class DetailsInitialFocusOwnershipContractTest(unittest.TestCase):
    @staticmethod
    def section(path: Path, start: str, end: str) -> str:
        text = path.read_text(encoding="utf-8")
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def movie_screen(self) -> str:
        return self.section(
            DETAILS,
            "private fun MovieDetailsProTvPolished(",
            "@Composable\nprivate fun SeriesDetailsProTvPolished(",
        )

    def series_screen(self) -> str:
        return self.section(
            DETAILS,
            "private fun SeriesDetailsProTvPolished(",
            "@Composable\nprivate fun DetailsTvEpisodeUnit(",
        )

    def test_back_focus_cancels_pending_primary_grant_on_both_detail_paths(self) -> None:
        for name, section in (("movie", self.movie_screen()), ("series", self.series_screen())):
            with self.subTest(screen=name):
                back = section.index('text = "رجوع"')
                back_window = section[back : back + 700]
                self.assertIn("onFocused = {", back_window)
                self.assertIn("initialFocusPending = false", back_window)

    def test_primary_grant_is_attachment_and_readiness_driven(self) -> None:
        for name, section in (("movie", self.movie_screen()), ("series", self.series_screen())):
            with self.subTest(screen=name):
                self.assertIn("detailsInitialFocusAction(", section)
                self.assertIn("playActionAttached = watchActionPlaced", section)
                self.assertIn("onWatchActionPlaced", section)
                grant_index = section.index("detailsInitialFocusAction(")
                grant_end = section.index(
                    "LaunchedEffect(targetEpisode?.id) {" if name == "series" else "LazyColumn(",
                    grant_index,
                )
                self.assertNotIn("delay(", section[grant_index:grant_end])

        movie_bar = self.section(
            MOVIES,
            "internal fun MovieDetailsActionsBar(",
            "private fun MovieDownloadActionControl(",
        )
        series_bar = self.section(
            SERIES,
            "internal fun SeriesDetailsActionsBar(",
            "internal fun episodeDownloadControlCaption(",
        )
        for name, bar in (("movie", movie_bar), ("series", series_bar)):
            with self.subTest(action_bar=name):
                self.assertIn(".onPlaced { onWatchActionPlaced?.invoke() }", bar)

    def test_tab_and_non_play_action_moves_still_cancel_pending_grant(self) -> None:
        for name, section in (("movie", self.movie_screen()), ("series", self.series_screen())):
            with self.subTest(screen=name):
                select = section.index("val selectTab:")
                self.assertIn("initialFocusPending = false", section[select : select + 300])
                focused = section.index("onActionFocused = {")
                self.assertIn("initialFocusPending = false", section[focused : focused + 700])


if __name__ == "__main__":
    unittest.main()

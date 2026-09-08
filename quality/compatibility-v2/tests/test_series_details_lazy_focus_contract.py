from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
DETAILS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProTvPolishScreens.kt"


class SeriesDetailsLazyFocusContractTest(unittest.TestCase):
    @staticmethod
    def source() -> str:
        return DETAILS.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        text = cls.source()
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def test_offscreen_episode_target_scrolls_before_focus_after_real_lazy_layout_signal(self) -> None:
        series = self.section(
            "private fun SeriesDetailsProTvPolished(",
            "@Composable\nprivate fun DetailsTvEpisodeUnit(",
        )
        effect_start = series.index("LaunchedEffect(pendingLazyFocus, listState)")
        effect_end = series.index("LaunchedEffect(series.id, targetEpisode?.id)", effect_start)
        effect = series[effect_start:effect_end]

        scroll = effect.index("listState.scrollToItem(sourceIndex + 1)")
        signal = effect.index("snapshotFlow", scroll)
        visibility = effect.index("it.key == request.targetItemKey", signal)
        wait = effect.index(".first { it }", visibility)
        focus = effect.index("request.targetRequester.requestFocus()", wait)

        self.assertLess(scroll, signal)
        self.assertLess(signal, visibility)
        self.assertLess(visibility, wait)
        self.assertLess(wait, focus)
        self.assertNotIn("delay(", effect)
        self.assertNotIn("while (", effect)

    def test_episode_and_related_lazy_down_targets_keep_deterministic_column_mapping(self) -> None:
        series = self.section(
            "private fun SeriesDetailsProTvPolished(",
            "@Composable\nprivate fun DetailsTvEpisodeUnit(",
        )
        self.assertIn('val sourceRowKey = "series_tv_polished_episode_row_${selectedSeason}_$rowIndex"', series)
        self.assertIn("val nextColumn = columnIndex.coerceAtMost(nextEpisodes.lastIndex)", series)
        self.assertIn('"series_tv_polished_episode_row_${selectedSeason}_${rowIndex + 1}"', series)
        self.assertIn('"series_tv_polished_related"', series)
        self.assertIn("val lazyDownRequest = if (downTarget != null && lazyDownItemKey != null) {", series)
        self.assertIn("DetailsTvLazyFocusRequest(", series)
        self.assertIn("sourceItemKey = sourceRowKey", series)
        self.assertIn("targetItemKey = lazyDownItemKey", series)
        self.assertIn("targetRequester = downTarget", series)
        self.assertIn("pendingLazyFocus = request", series)
        self.assertIn("downCard = null", series)

    def test_mini_actions_delegate_lazy_down_without_changing_touch_activation(self) -> None:
        episode = self.section(
            "private fun DetailsTvEpisodeUnit(",
            "@Composable\nprivate fun DetailsTvMiniAction(",
        )
        mini = self.section(
            "private fun DetailsTvMiniAction(",
            "@Composable\nprivate fun DetailsTvResumeStrip(",
        )

        self.assertEqual(episode.count("onMoveDown = onMoveDown"), 2)
        self.assertEqual(episode.count("onCancelPendingMove = onCancelPendingMove"), 2)
        self.assertIn("if (onMoveDown != null) {", mini)
        self.assertIn("onMoveDown()", mini)
        self.assertIn("downTarget?.let { runCatching { it.requestFocus() } }", mini)
        self.assertGreaterEqual(mini.count("onCancelPendingMove()"), 3)
        self.assertIn(".clickable(enabled = enabled, role = Role.Button, onClick = onClick)", mini)


if __name__ == "__main__":
    unittest.main()

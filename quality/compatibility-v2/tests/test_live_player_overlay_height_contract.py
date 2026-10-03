from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"


class LivePlayerOverlayHeightContractTest(unittest.TestCase):
    """R6 regressions for complete overlay height allocation.

    These fail on the R5 source: the Live error card was a non-scrollable composition and the More
    panel had no explicit fallback when the measured strip left too little height.
    """

    @staticmethod
    def read() -> str:
        return PLAYER_SCREEN.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_live_error_card_is_a_bounded_scroll_container(self) -> None:
        panel = self.section(
            self.read(),
            "private fun PlayerErrorPanel(",
            "@Composable\nprivate fun TrackSelectionPanel(",
        )
        self.assertIn("Modifier.heightIn(max = cardMaxHeight)", panel)
        self.assertIn("Modifier.verticalScroll(rememberScrollState())", panel)
        self.assertIn("Modifier.safeDrawingPadding()", panel)
        # Focused actions are explicitly brought into view for D-pad reachability.
        self.assertIn("bringIntoViewRequester(", panel)
        self.assertIn(".bringIntoView()", panel)

    def test_more_overlay_measures_the_strip_then_falls_back_when_space_is_insufficient(self) -> None:
        player = self.read()
        self.assertIn("SubcomposeLayout(", player)
        self.assertIn("liveBottomOverlayMode(", player)
        self.assertIn("LiveBottomOverlayMode.FALLBACK", player)
        self.assertIn("livePlayerMorePanelMinimumHeightDp(", player)
        # No positive panel floor, estimated strip height or measured-strip feedback state returns.
        self.assertNotIn("coerceAtLeast(160.dp)", player)
        self.assertNotIn("liveControlsStripHeightPx", player)
        self.assertNotIn("onMeasuredHeightPx", player)


if __name__ == "__main__":
    unittest.main()

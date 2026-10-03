from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
CONTROLS_POLICY = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LivePlayerControlsPolicy.kt"


class LivePlayerLayoutAllocationContractTest(unittest.TestCase):
    """R5 regressions for the strip/panel allocation and measured error actions.

    These fail on the R4 source: the More panel was an unweighted sibling bounded by an estimated
    strip height with a 160dp floor, and the error actions used fixed 40/44dp heights and a
    television/width column decision.
    """

    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_more_panel_reserves_the_strip_before_allocating_space(self) -> None:
        player = self.read(PLAYER_SCREEN)
        # The overlay subcomposes and measures the real strip before it allocates any panel space.
        self.assertIn("subcompose(LiveBottomOverlaySlot.STRIP)", player)
        self.assertIn("subcompose(LiveBottomOverlaySlot.PANEL)", player)
        # No estimated strip state, feedback measurement or panel floor remains.
        self.assertNotIn("liveControlsStripHeightPx", player)
        self.assertNotIn("onMeasuredHeightPx", player)
        self.assertNotIn("livePlayerReservedStripHeightDp", player)
        self.assertNotIn("coerceAtLeast(160.dp)", player)

    def test_error_actions_use_measured_caption_sizing(self) -> None:
        player = self.read(PLAYER_SCREEN)
        policy = self.read(CONTROLS_POLICY)
        self.assertIn("rememberTextMeasurer()", player)
        self.assertIn("liveErrorActionSizing(", player)
        self.assertIn("action.liveCaption()", player)
        # The fixed sizing helpers must be gone from both the UI and the policy.
        self.assertNotIn("playerErrorActionHeightDp", player)
        self.assertNotIn("playerErrorActionColumns", player)
        self.assertNotIn("playerErrorActionHeightDp", policy)
        self.assertNotIn("playerErrorActionColumns", policy)
        self.assertIn("internal fun liveErrorActionSizing(", policy)
        self.assertIn("requiredActionHeightDp", policy)
        self.assertIn("availableWidthDp", policy)

    def test_error_origin_picker_reserves_only_its_own_safe_remainder(self) -> None:
        player = self.read(PLAYER_SCREEN)
        self.assertIn("request.isLive && activePanel == PlayerPanel.SERVERS", player)
        # The error-origin overlay has no controls strip to reserve and no artificial floor.
        self.assertIn("coerceAtLeast(0.dp)", player)


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
HULK_THEME = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/theme/HulkTheme.kt"


class SidebarNavigationFocusHandoffContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_tv_destination_change_defers_focus_until_new_content_frame(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("LaunchedEffect(isTv, state.screen, state.destination)", app)
        self.assertIn("withFrameNanos { }", app)
        self.assertGreaterEqual(app.count("focusManager.moveFocus(FocusDirection.Left)"), 2)
        effect = app[app.index("LaunchedEffect(isTv, state.screen, state.destination)"):]
        self.assertLess(effect.index("withFrameNanos { }"), effect.index("focusManager.moveFocus(FocusDirection.Left)"))

    def test_rapid_destination_changes_cancel_stale_effects_by_destination_key(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("LaunchedEffect(isTv, state.screen, state.destination)", app)
        self.assertIn("SidebarNavigationFocusHandoffTracker", app)
        self.assertIn("focusHandoffTracker.takePrevious(state.destination)", app)

    def test_same_destination_does_not_create_navigation_churn(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("previousDestination != currentDestination", app)

    def test_home_movies_series_live_are_not_excluded_from_handoff(self) -> None:
        app = self.read(HULK_APP)
        policy = app[
            app.index("internal fun shouldAttemptTvSidebarFocusHandoff"):
            app.index("@Composable\nfun HulkApp")
        ]

        for destination in ("HOME", "MOVIES", "SERIES", "LIVE"):
            self.assertNotIn(f"currentDestination != MainDestination.{destination}", policy)

    def test_search_keeps_existing_independent_focus_owner(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("currentDestination != MainDestination.SEARCH", app)
        self.assertIn("state.destination == MainDestination.SEARCH", app)
        self.assertIn("ProfileSmartSearchLayer(", app)

    def test_rtl_handoff_moves_from_right_rail_toward_content(self) -> None:
        app = self.read(HULK_APP)
        theme = self.read(HULK_THEME)

        self.assertIn("FocusDirection.Left", app)
        self.assertIn("LocalLayoutDirection provides LayoutDirection.Rtl", theme)

    def test_rail_naturally_owns_expanded_state_and_is_not_manually_collapsed(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertIn(".onFocusChanged { railHasFocus = it.hasFocus }", shell)
        self.assertNotIn("railHasFocus = false", shell)

    def test_sidebar_reentry_focus_routing_is_unchanged(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertIn("onEnter = {\n                    selectedRequester.requestFocus()", shell)
        self.assertIn(".focusGroup()", shell)

    def test_stable_main_shell_profile_boundary_is_preserved(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("key(catalogNavigationMemory) {", app)
        self.assertNotIn("key(catalogNavigationMemory, state.destination)", app)

    def test_rail_width_animation_is_unchanged(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertIn(
            "targetValue = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp",
            shell,
        )
        self.assertIn(".width(railWidth)", shell)
        self.assertIn('label = "railWidth"', shell)


if __name__ == "__main__":
    unittest.main()

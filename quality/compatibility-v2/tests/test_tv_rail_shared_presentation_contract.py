from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
SMART_SEARCH = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileSmartSearchLayer.kt"
TV_RAIL_PRESENTATION = (
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/TvRailPresentation.kt"
)
DESTINATION_ORDER = (
    "HOME",
    "LIVE",
    "MOVIES",
    "SERIES",
    "FAVORITES",
    "SEARCH",
    "DOWNLOADS",
    "SETTINGS",
)


class TvRailSharedPresentationContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def main_rail(self) -> str:
        return self.section(
            self.read(MAIN_SHELL),
            "private fun CinematicNavigationRail(",
            "private fun DestinationContent(",
        )

    def search_rail(self) -> str:
        return self.section(
            self.read(SMART_SEARCH),
            "private fun SmartSearchRail(",
            "private suspend fun buildSmartSearchIndex(",
        )

    def test_main_and_search_rails_share_one_presentation_surface(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertEqual(1, presentation.count("internal fun TvRailSurface("))
        self.assertEqual(1, presentation.count("internal fun TvRailDestinationItem("))
        for rail in (self.main_rail(), self.search_rail()):
            self.assertIn("TvRailSurface(", rail)
            self.assertIn("TvRailDestinationItem(", rail)

    def test_tv_rail_presentation_owns_no_repository_or_navigation_state(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        for forbidden in (
            "ViewModel",
            "repository",
            "Repository",
            "requestFocus(",
            "delay(",
            "navigate(",
        ):
            self.assertNotIn(forbidden, presentation)

    def test_overlay_reserves_only_collapsed_width_and_never_scales_on_focus(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn(".width(metrics.collapsedWidthDp.dp)", presentation)
        self.assertIn(".tvRailOverlayWidth(surfaceWidth)", presentation)
        self.assertNotIn("Modifier.requiredWidth(", presentation)
        self.assertNotIn(".requiredWidth(surfaceWidth)", presentation)
        self.assertIn("Modifier.width(surfaceWidth)", presentation)
        self.assertIn("animateDpAsState(", presentation)
        self.assertIn("TV_RAIL_EXPANSION_DURATION_MILLIS", presentation)
        self.assertNotIn("focusScale", presentation)
        self.assertNotIn("graphicsLayer", presentation)
        self.assertIn(".zIndex(1f)", presentation)
        self.assertEqual(1, presentation.count(".clickable("))
        self.assertIn(".onFocusChanged { onRailFocusChanged(it.hasFocus) }", presentation)
        self.assertIn(".onFocusChanged { itemFocused = it.isFocused }", presentation)

    def test_overlay_start_edge_is_anchored_and_expands_only_toward_content(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn(
            "internal fun tvRailOverlayAnchorOffsetPx(",
            presentation,
        )
        self.assertIn("tvRailOverlayAnchorOffsetPx(", presentation)
        self.assertIn("layoutDirection == LayoutDirection.Rtl", presentation)
        self.assertIn("placeable.place(", presentation)
        self.assertIn("constraints.hasBoundedWidth", presentation)

    def test_expanded_overlay_uses_a_local_content_edge_scrim(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn("TvRailEdgeScrimWidth", presentation)
        self.assertIn("internal fun tvRailEdgeScrimAlpha(", presentation)
        self.assertIn("drawBehind { drawTvRailEdgeScrim(edgeScrimAlpha) }", presentation)
        self.assertIn("if (alpha <= 0f) return", presentation)
        self.assertIn("Brush.horizontalGradient(", presentation)
        self.assertGreaterEqual(presentation.count("if (overlayExpansion)"), 3)
        self.assertIn(
            ".background(Brush.horizontalGradient(listOf(TvRailSurfaceStart, TvRailSurfaceEnd)))",
            presentation,
        )
        self.assertEqual(1, presentation.count("drawBehind {"))

    def test_overlay_expansion_is_tv_only_and_focus_contracts_stay_with_each_controller(self) -> None:
        main_rail = self.main_rail()
        search_rail = self.search_rail()

        self.assertIn("overlayExpansion = adaptiveUi.isTelevision", main_rail)
        self.assertIn("overlayExpansion = isTv", search_rail)
        self.assertIn("val expanded = railHasFocus", main_rail)
        self.assertIn("val expanded = railHasFocus || !isTv", search_rail)
        self.assertIn("onRailFocusChanged = { railHasFocus = it }", main_rail)
        self.assertIn("onRailFocusChanged = { railHasFocus = it }", search_rail)
        self.assertIn("onRailEnter = { selectedRequester.requestFocus() }", main_rail)
        self.assertIn("selectedRequester.requestFocus()", search_rail)
        self.assertIn("returnToContent()", search_rail)
        self.assertIn(".focusProperties { left = contentReturnRequester }", search_rail)

    def test_selected_and_focused_states_stay_distinct_and_combined(self) -> None:
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn("internal fun tvRailVisualState(selected: Boolean, highlighted: Boolean)", presentation)
        self.assertIn("TvRailVisualState.SELECTED_FOCUSED", presentation)
        self.assertIn("TvRailVisualState.SELECTED ->", presentation)
        self.assertIn("TvRailVisualState.FOCUSED,", presentation)
        self.assertIn("adaptiveUi.tvPremiumPolicy.focusBorderWidthDp.dp", presentation)
        self.assertIn("val contentTint = when (visualState)", presentation)
        self.assertNotIn("TvRailSelectionMarker", presentation)

    def test_destination_order_and_profile_placement_are_unchanged(self) -> None:
        shell = self.read(MAIN_SHELL)
        search = self.read(SMART_SEARCH)
        main_rail = self.main_rail()
        search_rail = self.search_rail()

        main_entries = shell[shell.index("private val destinations = listOf(") :]
        search_entries = search[search.index("private val smartSearchDestinations = listOf(") :]
        for block in (main_entries, search_entries):
            positions = [block.index(f"MainDestination.{name}") for name in DESTINATION_ORDER]
            self.assertEqual(sorted(positions), positions)

        self.assertIn("entries.first { it.destination == MainDestination.SETTINGS }", main_rail)
        self.assertIn(
            "smartSearchDestinations.first { it.destination == MainDestination.SETTINGS }",
            search_rail,
        )
        for rail in (main_rail, search_rail):
            self.assertIn('label = "تغيير المستخدم"', rail)
            self.assertLess(rail.index('label = "تغيير المستخدم"'), rail.index("Spacer(Modifier.weight(1f))"))


if __name__ == "__main__":
    unittest.main()

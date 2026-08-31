from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
SETTINGS_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt"


class StableMainShellNavigationContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_destination_change_does_not_rekey_the_main_shell(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("key(catalogNavigationMemory) {", app)
        self.assertNotIn("key(catalogNavigationMemory, state.destination)", app)

    def test_profile_memory_remains_the_shell_reset_boundary(self) -> None:
        app = self.read(HULK_APP)
        key_start = app.index("key(catalogNavigationMemory) {")
        shell_start = app.index("MainShellScreen(", key_start)

        self.assertLess(key_start, shell_start)
        self.assertIn("catalogNavigationMemory: ProfileCatalogNavigationMemory", app)

    def test_shell_delegates_catalog_context_to_profile_owned_navigation(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertNotIn("val queryMemory = remember", shell)
        self.assertNotIn("val categoryMemory = remember", shell)
        self.assertNotIn("rememberingSelectDestination", shell)
        self.assertEqual(1, shell.count("onSelect = onSelectDestination"))
        self.assertIn("onSelect = selectTvDestination", shell)
        self.assertIn("onSelectDestination = onSelectDestination", shell)

    def test_shared_category_underlap_preserves_shell_geometry(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun NavigationItem(",
        )
        catalog = self.section(
            source,
            "private fun ReorderableCatalogCategoryBar(",
            "private fun CatalogInteractionHints(",
        )
        live = source[source.index("private fun ReorderableLiveCategoryBar(") :]

        self.assertIn("Row(Modifier.fillMaxSize())", shell)
        self.assertIn("Modifier.weight(1f).fillMaxHeight()", shell)
        self.assertIn(".zIndex(1f)", rail)
        for category_bar in (catalog, live):
            self.assertIn("rememberCategorySidebarUnderlap", category_bar)
            self.assertIn(".extendCategoryViewportTowardStart", category_bar)
            self.assertIn("start = sidebarUnderlap.startContentPaddingDp.dp", category_bar)
        self.assertEqual(
            2,
            catalog.count(".extendCategoryViewportTowardStart")
            + live.count(".extendCategoryViewportTowardStart"),
        )

    def test_sidebar_width_change_is_discrete_and_content_remains_sibling(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun NavigationItem(",
        )

        self.assertIn("Row(Modifier.fillMaxSize())", shell)
        self.assertIn("Modifier.weight(1f).fillMaxHeight()", shell)
        self.assertIn(
            "val railWidth = if (expanded) metrics.expandedWidthDp.dp else metrics.collapsedWidthDp.dp",
            rail,
        )
        self.assertIn(".width(railWidth)", rail)
        self.assertNotIn("animateDpAsState(", rail)
        self.assertNotIn('label = "railWidth"', rail)

    def test_sidebar_expansion_remains_derived_from_actual_focus(self) -> None:
        source = self.read(MAIN_SHELL)
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun NavigationItem(",
        )

        self.assertIn("val expanded = railHasFocus", rail)
        self.assertIn(".onFocusChanged { railHasFocus = it.hasFocus }", rail)
        self.assertEqual(1, rail.count("railHasFocus ="))
        self.assertNotIn("railHasFocus = false", rail)

    def test_destination_selection_hands_focus_off_after_navigation(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        selection = self.section(
            shell,
            "val selectTvDestination: (MainDestination) -> Unit = { destination ->",
            "val homeModel =",
        )
        handoff = self.section(
            shell,
            "LaunchedEffect(\n        useNavigationRail,",
            "Box(Modifier.fillMaxSize().background(colors.background))",
        )

        self.assertIn("if (destination != state.destination)", selection)
        self.assertLess(
            selection.index("onSelectDestination(destination)"),
            selection.index("pendingTvContentFocusHandoff ="),
        )
        self.assertIn("onSelect = selectTvDestination", shell)
        self.assertIn("handoff.destination != state.destination", handoff)
        self.assertIn("withFrameNanos { }", handoff)
        self.assertIn("currentTvDestinationFocusRequester.requestFocus()", handoff)
        self.assertIn("pendingTvContentFocusHandoff = null", handoff)
        self.assertNotIn("delay(", handoff)
        self.assertNotIn("debounce", handoff.lower())
        self.assertNotIn("poll", handoff.lower())

    def test_same_destination_skips_navigation_churn_but_still_requests_handoff(self) -> None:
        source = self.read(MAIN_SHELL)
        selection = self.section(
            source,
            "val selectTvDestination: (MainDestination) -> Unit = { destination ->",
            "val homeModel =",
        )

        navigation_guard_end = selection.index("}", selection.index("if (destination != state.destination)"))
        handoff_assignment = selection.index("pendingTvContentFocusHandoff =")
        self.assertLess(navigation_guard_end, handoff_assignment)
        self.assertEqual(1, selection.count("onSelectDestination(destination)"))

    def test_all_rail_destinations_share_the_content_focus_entry_contract(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        destination_content = self.section(
            source,
            "private fun DestinationContent(",
            "private fun CinemaHomeScreen(",
        )
        destination_entries = source[source.index("private val destinations = listOf(") :]

        self.assertIn(
            "destinations.associate { entry -> entry.destination to FocusRequester() }",
            shell,
        )
        self.assertIn(".focusRequester(currentTvContentFocusRequester)", shell)
        for destination in (
            "HOME",
            "LIVE",
            "MOVIES",
            "SERIES",
            "FAVORITES",
            "DOWNLOADS",
            "SETTINGS",
        ):
            marker = f"MainDestination.{destination}"
            self.assertIn(marker, destination_entries)
            self.assertIn(marker, destination_content)

    def test_destination_content_container_owns_a_restoring_focus_group(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        destination_content = self.section(
            source,
            "private fun DestinationContent(",
            "private fun CinemaHomeScreen(",
        )

        self.assertIn(".focusRequester(currentTvContentFocusRequester)", shell)
        self.assertIn(".focusRestorer()", shell)
        self.assertIn(".focusGroup()", shell)
        self.assertIn("navigationMemory = navigationMemory", destination_content)
        self.assertNotIn("key(state.destination", destination_content)
        self.assertNotIn("key( state.destination", destination_content)

    def test_empty_and_loading_destinations_keep_a_safe_focus_target(self) -> None:
        source = self.read(MAIN_SHELL)
        favorites = self.section(
            source,
            "private fun FavoritesScreen(",
            "private fun UnifiedSearchScreen(",
        )
        downloads = self.section(
            source,
            "private fun DownloadsScreen(",
            "private fun DownloadCard(",
        )
        settings = self.read(SETTINGS_SCREEN)

        self.assertIn("if (isTv && content.isEmpty())", favorites)
        self.assertIn("else if (content.isEmpty() && state.loadingTypes.isEmpty())", favorites)
        self.assertIn("loading = state.loadingTypes.isNotEmpty()", favorites)
        self.assertIn("FavoritesFocusFallback(", favorites)
        self.assertIn('FocusButton("تحديث القائمة", onRefresh', favorites)
        self.assertIn("if (downloads.isEmpty())", downloads)
        self.assertIn(".focusRequester(toolbarFocus.wifi)", downloads)
        self.assertIn("focus.refreshAccount.requestFocus()", settings)
        self.assertIn("focusRequester = focus.refreshAccount", settings)

    def test_existing_destination_focus_memory_remains_in_place(self) -> None:
        source = self.read(MAIN_SHELL)
        home = self.section(
            source,
            "private fun CinemaHomeScreen(",
            "private fun RenewalBanner(",
        )
        live = self.section(
            source,
            "private fun LiveCatalogScreen(",
            "private fun FavoritesScreen(",
        )
        favorites = self.section(
            source,
            "private fun FavoritesScreen(",
            "private fun UnifiedSearchScreen(",
        )
        downloads = self.section(
            source,
            "private fun DownloadsScreen(",
            "private fun DownloadCard(",
        )

        self.assertIn("navigationMemory.position(MainDestination.HOME)", home)
        self.assertIn("navigationMemory.position(MainDestination.LIVE)", live)
        self.assertIn("channelRequester.requestFocus()", live)
        self.assertIn("MainDestination.FAVORITES, navigationMemory", favorites)
        self.assertIn("navigationMemory.position(MainDestination.DOWNLOADS)", downloads)


if __name__ == "__main__":
    unittest.main()

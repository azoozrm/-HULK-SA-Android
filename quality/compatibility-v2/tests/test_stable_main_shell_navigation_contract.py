from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
SETTINGS_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt"
TV_RAIL_PRESENTATION = (
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/TvRailPresentation.kt"
)


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
        self.assertNotIn("onSelect = onSelectDestination", shell)
        self.assertIn("onSelect = selectTvDestination", shell)
        self.assertIn("onSelectDestination = onSelectDestination", shell)

    def test_shared_category_underlap_preserves_shell_geometry(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        presentation = self.read(TV_RAIL_PRESENTATION)
        catalog = self.section(
            source,
            "private fun CatalogCategoryBar(",
            "private fun LiveCategoryBar(",
        )
        live = source[source.index("private fun LiveCategoryBar(") :]

        self.assertIn("Row(Modifier.fillMaxSize())", shell)
        self.assertIn("Modifier.weight(1f).fillMaxHeight()", shell)
        self.assertIn(".zIndex(1f)", presentation)
        for category_bar in (catalog, live):
            self.assertIn("rememberCategorySidebarUnderlap", category_bar)
            self.assertIn(".extendCategoryViewportTowardStart", category_bar)
            self.assertIn("start = sidebarUnderlap.startContentPaddingDp.dp", category_bar)
        self.assertEqual(
            2,
            catalog.count(".extendCategoryViewportTowardStart")
            + live.count(".extendCategoryViewportTowardStart"),
        )

    def test_rail_expansion_is_an_overlay_that_never_resizes_sibling_content(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun DestinationContent(",
        )
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn("Row(Modifier.fillMaxSize())", shell)
        self.assertIn("Modifier.weight(1f).fillMaxHeight()", shell)
        self.assertIn("TvRailSurface(", rail)
        self.assertIn("overlayExpansion = adaptiveUi.isTelevision", rail)
        self.assertIn(".width(metrics.collapsedWidthDp.dp)", presentation)
        self.assertIn(".tvRailOverlayWidth(surfaceWidth)", presentation)
        self.assertNotIn("Modifier.requiredWidth(", presentation)
        self.assertNotIn(".requiredWidth(surfaceWidth)", presentation)
        self.assertIn("layoutDirection == LayoutDirection.Rtl", presentation)
        self.assertIn("animateDpAsState(", presentation)
        self.assertIn("TV_RAIL_EXPANSION_DURATION_MILLIS", presentation)
        self.assertNotIn("val railWidth = if (expanded)", source)

    def test_sidebar_expansion_remains_derived_from_actual_focus(self) -> None:
        source = self.read(MAIN_SHELL)
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun DestinationContent(",
        )
        presentation = self.read(TV_RAIL_PRESENTATION)

        self.assertIn("val expanded = railHasFocus", rail)
        self.assertIn("onRailFocusChanged = { focused ->", rail)
        self.assertIn("railHasFocus = focused", rail)
        self.assertIn("onRailFocused()", rail)
        self.assertEqual(1, rail.count("railHasFocus ="))
        self.assertNotIn("railHasFocus = false", rail)
        self.assertIn(".onFocusChanged { onRailFocusChanged(it.hasFocus) }", presentation)

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

        self.assertIn("when (tvRailSelectionIntent(state.destination, destination))", selection)
        self.assertIn("TvRailSelectionIntent.CHANGE_DESTINATION ->", selection)
        self.assertIn("onSelectDestination(destination)", selection)
        self.assertIn("onSelect = selectTvDestination", shell)
        self.assertIn("handoff.destination != state.destination", handoff)
        self.assertIn("withFrameNanos { }", handoff)
        self.assertIn("currentTvDestinationFocusRequester.requestFocus()", handoff)
        self.assertIn("pendingTvContentFocusHandoff = null", handoff)
        self.assertNotIn("delay(", handoff)
        self.assertNotIn("debounce", handoff.lower())
        self.assertNotIn("poll", handoff.lower())

    def test_same_destination_dismissal_returns_to_the_content_focus_owner(self) -> None:
        source = self.read(MAIN_SHELL)
        selection = self.section(
            source,
            "val selectTvDestination: (MainDestination) -> Unit = { destination ->",
            "val homeModel =",
        )
        policy = self.read(Path(REPO_ROOT, "app/src/main/java/sa/hulksa/player/ui/screens/TvRailSelectionPolicy.kt"))

        # Activating the already selected destination is a dismissal, never navigation.
        self.assertIn("TvRailSelectionIntent.RETURN_TO_CONTENT ->", selection)
        self.assertEqual(1, selection.count("onSelectDestination(destination)"))
        self.assertIn(
            "if (requestedDestination == selectedDestination)",
            policy,
        )
        self.assertIn("TvRailSelectionIntent.RETURN_TO_CONTENT", policy)

        # The content group's saved child is restored exactly. A plain group requestFocus() lands
        # on the group's top-left control, which was the Home "jump upward" defect.
        before_selection = source[
            source.index("val restoreTvContentFocus: () -> Boolean = {") :
            source.index("val selectTvDestination: (MainDestination) -> Unit =")
        ]
        self.assertIn("restoreFocusedChild()", before_selection)
        self.assertNotIn(".requestFocus()", before_selection)
        return_branch = selection[
            selection.index("TvRailSelectionIntent.RETURN_TO_CONTENT ->") :
        ]
        self.assertIn("pendingTvContentFocusHandoff = null", return_branch)
        self.assertIn("restoreContentFocus = restoreTvContentFocus", return_branch)
        self.assertIn("returnTvRailFocusToContent(", return_branch)
        self.assertIn("armEntryHandoff = {", return_branch)
        self.assertLess(
            return_branch.index("pendingTvContentFocusHandoff = null"),
            return_branch.index("returnTvRailFocusToContent("),
        )
        self.assertNotIn("currentTvDestinationFocusRequester", return_branch)
        self.assertNotIn("tvCatalogAllFocusRequesters", return_branch)
        self.assertNotIn("scrollToItem", return_branch)
        self.assertNotIn("delay(", return_branch)

        # Physical LEFT (RTL) closes the rail through the same saved-child owner; only a real
        # successful restore consumes the key, so a vanished child keeps the framework fallback.
        rail = self.section(source, "private fun CinematicNavigationRail(", "private fun DestinationContent(")
        self.assertIn("private fun Modifier.railContentReturn(", source)
        self.assertIn("onReturnToContent: () -> Boolean", rail)
        self.assertIn("onReturnToContent = restoreTvContentFocus", source)
        self.assertIn("tvRailContentReturnDirection(layoutDirection)", rail)
        self.assertIn(".then(contentReturnModifier)", rail)
        self.assertEqual(3, rail.count(".then(contentReturnModifier)"))
        self.assertIn("event.key == direction &&", source)
        self.assertIn("onReturnToContent()", source)

        # The fallback arms the existing entry handoff exactly once, after a real failed restore.
        helper_start = policy.index("internal inline fun returnTvRailFocusToContent(")
        helper = policy[helper_start:]
        self.assertIn("runCatching(restoreContentFocus).getOrDefault(false)", helper)
        self.assertLess(
            helper.index("if (restored) return TvRailReturnResult.CONTENT"),
            helper.index("armEntryHandoff()"),
        )
        self.assertEqual(1, helper.count("armEntryHandoff()"))
        self.assertNotIn("delay(", helper)
        self.assertNotIn("while (", helper)

        # Lazy/scrollable containers install a non-focusable focus target but no focus restorer.
        # The shared save-only helper lets the content-group restore walk the saved path. This is
        # a source contract only; the actual focus-tree recursion is a Compose runtime behavior.
        helper = self.read(
            Path(REPO_ROOT, "app/src/main/java/sa/hulksa/player/ui/screens/TvFocusSavePath.kt")
        )
        self.assertIn("internal fun Modifier.saveFocusedChildOnExit(", helper)
        self.assertIn("onExit = { requester.saveFocusedChild() }", helper)
        self.assertNotIn("focusRestorer", helper)
        self.assertNotIn("onEnter", helper)
        self.assertIn(
            "modifier = Modifier.fillMaxSize().saveFocusedChildOnExit(homeColumnSaveFocusRequester)",
            source,
        )
        self.assertEqual(3, source.count(".saveFocusedChildOnExit(rowSaveFocusRequester)"))

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

        self.assertIn('label = "جاري تجهيز المفضلة"', favorites)
        self.assertIn('FavoritesEmptyState("لم تضف اي محتوى الى المفضلة بعد")', favorites)
        self.assertIn("FavoritesTypeFilterBar(", favorites)
        self.assertIn("filterRequesters.getValue(activeFilter).requestFocus()", favorites)
        self.assertIn("if (downloads.isEmpty())", downloads)
        self.assertIn(".focusRequester(toolbarFocus.wifi)", downloads)
        # Settings entry first focuses the selected section row in the RIGHT column; the
        # refresh-account control is still the attached first SUBSCRIPTION detail action reached
        # through the eligible settingsSectionFirstRequester path after OK/LEFT.
        self.assertIn("focus.sectionRequester(selectedSection).requestFocus()", settings)
        self.assertIn("settingsSectionFirstRequester(", settings)
        self.assertIn("focusRequester = focus.refreshAccount", settings)

    def test_settings_entry_uses_the_explicit_attached_selected_section_target(self) -> None:
        shell = self.read(MAIN_SHELL)
        settings = self.read(SETTINGS_SCREEN)

        # The shell owns one explicit Settings entry target and routes the SETTINGS handoff to it
        # instead of the generic content group, so no detail action can take the first focus.
        self.assertIn("val tvSettingsEntryFocusRequester = remember { FocusRequester() }", shell)
        self.assertIn("?: if (state.destination == MainDestination.SETTINGS)", shell)
        self.assertIn("tvSettingsEntryFocusRequester = tvSettingsEntryFocusRequester", shell)
        self.assertIn("tvEntryFocusRequester = settingsEntryFocusRequester", shell)

        # Settings attaches that target only to the selected section row and first entry requests
        # exactly it; the previous generic first-entry request is gone.
        self.assertIn(
            "entryFocusRequester = entryFocusRequester?.takeIf { section == selected }",
            settings,
        )
        self.assertIn(".then(entryFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)", settings)
        self.assertIn("runCatching { tvEntryFocusRequester.requestFocus() }", settings)
        # The old separate first-entry request on the section requester is gone; the remaining
        # request on that requester is the RIGHT/Back return-to-section-list path.
        self.assertEqual(1, settings.count("focus.sectionRequester(selectedSection).requestFocus()"))

    def test_existing_destination_focus_memory_remains_in_place(self) -> None:
        source = self.read(MAIN_SHELL)
        home = self.section(
            source,
            "private fun CinemaHomeScreen(",
            "private fun HomeMessageOwnership(",
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
        self.assertIn(
            "destination = MainDestination.FAVORITES, navigationMemory = navigationMemory",
            " ".join(favorites.split()),
        )
        self.assertIn("navigationMemory.position(MainDestination.DOWNLOADS)", downloads)


if __name__ == "__main__":
    unittest.main()

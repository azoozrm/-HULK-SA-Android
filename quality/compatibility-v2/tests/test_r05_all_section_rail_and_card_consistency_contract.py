from pathlib import Path
import unittest

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
TV_SAVE_PATH = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvFocusSavePath.kt"
TV_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"
SETTINGS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt"
SEARCH = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileSmartSearchLayer.kt"
HULK_COMPONENTS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
VOICE = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/VoiceSearchFoundation.kt"


class R05AllSectionRailAndCardConsistencyContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.shell = MAIN_SHELL.read_text(encoding="utf-8")
        cls.save_path = TV_SAVE_PATH.read_text(encoding="utf-8")
        cls.grid = TV_GRID.read_text(encoding="utf-8")
        cls.settings = SETTINGS.read_text(encoding="utf-8")
        cls.search = SEARCH.read_text(encoding="utf-8")
        cls.components = HULK_COMPONENTS.read_text(encoding="utf-8")
        cls.voice = VOICE.read_text(encoding="utf-8")

    def test_save_path_helper_is_shared_and_save_only(self) -> None:
        self.assertIn("internal fun Modifier.saveFocusedChildOnExit(", self.save_path)
        self.assertIn("focusRequester(requester)", self.save_path)
        self.assertIn("onExit = { requester.saveFocusedChild() }", self.save_path)
        self.assertNotIn("focusRestorer", self.save_path)
        self.assertNotIn("onEnter", self.save_path)

    def test_every_section_has_a_scrollable_boundary_save_path(self) -> None:
        # Home keeps its working path, plus category selectors, lazy lists/grids, downloads and
        # settings scroll columns. Search uses its exact contentReturnRequester instead.
        expected = {
            "Home column": "homeColumnSaveFocusRequester",
            "Home rows": "rowSaveFocusRequester",
            "Favorites type filter": "FavoritesTypeFilterBar",
            "Movies/Series category bar": "CatalogCategoryBar",
            "Live category bar": "LiveCategoryBar",
            "Live list": "liveListSaveFocusRequester",
            "Downloads grid": "downloadGridSaveFocusRequester",
        }
        for label, marker in expected.items():
            self.assertIn(marker, self.shell, label)
        self.assertGreaterEqual(self.shell.count("saveFocusedChildOnExit("), 10)
        # The shared catalog grid covers Movies, Series and Favorites.
        self.assertIn(".saveFocusedChildOnExit(gridSaveFocusRequester)", self.grid)
        # Settings has separate section-list and detail vertical scroll columns on TV and phone.
        self.assertEqual(4, self.settings.count(".saveFocusedChildOnExit("))
        self.assertIn("sectionListSaveFocusRequester", self.settings)
        self.assertIn("detailSaveFocusRequester", self.settings)
        # No section-specific scroll reset was introduced.
        for source in (self.shell, self.grid, self.settings):
            self.assertNotIn("saveFocusedChildOnExit(.*scrollToItem", source)

    def test_search_rail_returns_from_every_entry_and_same_selected_click_dismisses(self) -> None:
        self.assertIn("val contentReturnModifier = Modifier", self.search)
        self.assertIn(".focusProperties { if (isTv) left = contentReturnRequester }", self.search)
        self.assertIn("returnToContent()", self.search)
        self.assertGreaterEqual(self.search.count(".then(contentReturnModifier)"), 3)
        self.assertIn("if (entry.destination == selected)", self.search)
        self.assertNotIn(
            "if (isTv && entry.destination == MainDestination.SEARCH) {\n"
            "                                Modifier\n"
            "                                    .focusProperties { left = contentReturnRequester }",
            self.search,
        )
        # The active Search history Delete and voice controls update the same exact return owner.
        self.assertIn("onContentFocusTargetChanged(deleteRequesters[index])", self.search)
        self.assertIn("onFocused = { onContentFocusTargetChanged(voiceSearchRequester) }", self.search)
        self.assertIn("onFocused: (() -> Unit)? = null", self.voice)

    def test_favorites_and_search_use_stable_vod_first_presentation(self) -> None:
        self.assertIn("orderMixedContentVodFirst(items) { it.type }", self.shell)
        self.assertIn("orderMixedContentVodFirst(results) { it.type }", self.search)
        self.assertIn("orderMixedContentVodFirst(visibleRecentEntries) { it.contentType }", self.search)
        # Search matching remains upstream of the presentation projection.
        self.assertIn("results = boundedSmartSearch(preparedIndex, settledQuery, selectedFilter)", self.search)
        self.assertIn("recentEntries = displayRecentEntries", self.search)
        self.assertIn("results = displayResults", self.search)

    def test_mixed_live_cards_opt_into_boxed_geometry_only_in_favorites_and_search(self) -> None:
        self.assertIn("boxedCatalogGeometry: Boolean = false", self.components)
        self.assertIn("Modifier.aspectRatio(1f)", self.components)
        self.assertIn("Modifier.aspectRatio(16f / 9f)", self.components)
        self.assertIn("ContentScale.Fit", self.components)
        self.assertIn("maxLines = if (boxedCatalogGeometry) 2 else 1", self.components)
        self.assertIn("minLines = if (boxedCatalogGeometry) 2 else 1", self.components)
        self.assertIn("onFooterHeightMeasured", self.components)
        # Favorites and both Search card branches opt in; Home/Live standalone callers keep the
        # default 16:9 card because their call sites do not pass the flag.
        self.assertGreaterEqual(self.grid.count("boxedCatalogGeometry = true"), 1)
        self.assertGreaterEqual(self.search.count("boxedCatalogGeometry = true"), 2)
        home_live_call = self.shell[
            self.shell.index("ContentType.LIVE -> LiveChannelHomeCard(") :
            self.shell.index("ContentType.LIVE -> LiveChannelHomeCard(") + 900
        ]
        self.assertNotIn("boxedCatalogGeometry = true", home_live_call)


if __name__ == "__main__":
    unittest.main()

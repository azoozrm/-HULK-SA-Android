from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
SMART_SEARCH = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileSmartSearchLayer.kt"
VOICE_FOUNDATION = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/VoiceSearchFoundation.kt"
VIEW_MODEL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"

FILTER_ORDER = ("ALL(", "MOVIES(", "SERIES(", "LIVE(")
FILTER_LABELS = ("الكل", "الافلام", "المسلسلات", "القنوات")


class SearchModel01ContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SMART_SEARCH.read_text(encoding="utf-8")
        cls.voice = VOICE_FOUNDATION.read_text(encoding="utf-8")
        cls.view_model = VIEW_MODEL.read_text(encoding="utf-8")

    def test_physical_rtl_filter_order_and_labels(self) -> None:
        enum_start = self.source.index("private enum class SmartSearchFilter(")
        enum_block = self.source[enum_start : self.source.index("fun accepts", enum_start)]
        positions = [enum_block.index(marker) for marker in FILTER_ORDER]
        self.assertEqual(sorted(positions), positions)
        for label in FILTER_LABELS:
            self.assertIn(label, enum_block)
        self.assertNotIn("الأفلام", enum_block)

    def test_singular_movie_word_is_used_in_app_authored_copy(self) -> None:
        self.assertIn('ContentType.MOVIE -> "فلم"', self.source)
        self.assertNotIn('"فيلم"', self.source)

    def test_exact_authored_search_copy(self) -> None:
        for copy in (
            '"البحث"',
            '"اكتب اسم فلم او مسلسل او قناة"',
            '"ابدا البحث"',
            '"اكتب حرفين على الاقل"',
            '"جاري البحث"',
            '"جاري تحميل المحتوى"',
            '"لا توجد نتائج مطابقة"',
            '"جرب اسما اخر او غير نوع المحتوى"',
            '"حذف"',
            '"مسح الكل"',
        ):
            self.assertIn(copy, self.source)
        self.assertNotIn('"نتائج البحث"', self.source)

    def test_page_has_no_recent_content_heading(self) -> None:
        self.assertNotIn("المحتوى الذي بحثت عنه مؤخرا", self.source)
        self.assertNotIn("المحتوى الذي تختاره يظهر هنا بدون تكرار", self.source)

    def test_toolbar_matches_the_accepted_catalog_header_policy(self) -> None:
        self.assertIn("TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP", self.source)
        self.assertIn("tvPageSafeInsets(", self.source)
        self.assertIn("fontSize = if (isTv) 27.sp else MOBILE_SECTION_TITLE_SIZE", self.source)
        self.assertIn("width(if (isTv) 185.dp else 105.dp)", self.source)
        self.assertIn("Modifier.weight(1f).widthIn(max = 630.dp)", self.source)
        self.assertNotIn("CircleShape", self.source)
        self.assertNotIn("ابحث في الافلام والمسلسلات والقنوات", self.source)

    def test_tv_toolbar_uses_the_exact_series_field_allocation(self) -> None:
        # R05: the TV field keeps the accepted CatalogHeader slot arithmetic instead of a
        # weighted fill, and the freed management/refresh action space toward the physical
        # LEFT stays unused without hidden or fake controls.
        self.assertIn("SEARCH_TITLE_SLOT_WIDTH_DP = 185", self.source)
        self.assertIn("SEARCH_HEADER_GAP_DP = 11", self.source)
        self.assertIn("rememberTextMeasurer()", self.source)
        self.assertIn('AnnotatedString("ادارة الفئات")', self.source)
        self.assertIn("homeHeaderActionTouchSizeDp().dp", self.source)
        self.assertIn("manageActionWidth", self.source)
        self.assertIn("Modifier.width(fieldWidth)", self.source)
        self.assertNotIn("SmartSearchToolbarActions", self.source)
        self.assertNotIn("ManageCategoriesButton(", self.source)

    def test_clear_all_lives_at_the_filter_toolbar_left_edge(self) -> None:
        self.assertIn("leftEdgeRequester", self.source)
        self.assertIn("leftEdgeRequester != null -> moveFocus(leftEdgeRequester)", self.source)
        self.assertIn('text = "مسح الكل"', self.source)

    def test_filters_reuse_the_accepted_category_strip_expressions(self) -> None:
        self.assertIn("rememberLiveCategoryStripMetrics()", self.source)
        self.assertIn("rememberCategorySidebarUnderlap(", self.source)
        self.assertIn("extendCategoryViewportTowardStart(", self.source)
        self.assertIn(".then(itemMetrics.compactHeightModifier)", self.source)

    def test_one_catalog_grid_serves_results_and_history(self) -> None:
        self.assertEqual(1, self.source.count("LazyVerticalGrid("))
        self.assertIn("private sealed interface SmartSearchGridRow", self.source)
        self.assertIn("SmartSearchGridRow.Result", self.source)
        self.assertIn("SmartSearchGridRow.History", self.source)
        self.assertNotIn("SmartRecentSection", self.source)
        self.assertNotIn("SmartRecentCard", self.source)
        self.assertNotIn("UniversalPosterCard", self.source)

    def test_grid_reuses_the_accepted_r10_focus_reveal_policy(self) -> None:
        self.assertIn("TvCatalogFocusMoveState", self.source)
        self.assertIn("nextTvGridFocusIndex(", self.source)
        self.assertIn("tvCatalogFocusPath(", self.source)
        self.assertIn("focusedCardScrollCorrection(", self.source)
        self.assertIn("revealFocusedGridItem(", self.source)
        self.assertIn("moviesNoBringIntoViewSpec()", self.source)
        self.assertIn("favoritesRemovalFocusIndex(", self.source)
        self.assertIn("favoritesSelectGuardDecision(", self.source)
        self.assertIn("GridCells.Fixed(columnCount)", self.source)
        self.assertIn("columnCount = movieCatalogColumnCount(", self.source)

    def test_cards_use_the_accepted_catalog_family_with_compact_delete(self) -> None:
        self.assertIn("MoviesCatalogBoxedCard(", self.source)
        self.assertIn("SeriesCatalogBoxedCard(", self.source)
        self.assertIn("LiveChannelHomeCard(", self.source)
        self.assertIn("footerAction = deleteAction", self.source)
        self.assertIn("SmartSearchHistoryDeleteAction(", self.source)
        self.assertIn("movieCompactArtworkHeightPx(", self.source)
        self.assertIn("FAVORITES_MOBILE_MIN_CELL_WIDTH", self.source)

    def test_saved_card_down_reaches_its_own_delete_without_ancestor_stealing(self) -> None:
        # R05: DOWN on a saved-content card moves to that card's own Delete; while the Delete
        # owns focus the ancestor card handler must not intercept the action's keys.
        self.assertIn("var deleteFocusedIndex by remember { mutableIntStateOf(-1) }", self.source)
        self.assertIn(
            "if (deleteFocusedIndex == index) return@onPreviewKeyEvent false",
            self.source,
        )
        self.assertIn("move == TvGridFocusMove.DOWN", self.source)
        self.assertIn("onDeleteRow != null", self.source)
        self.assertIn("row is SmartSearchGridRow.History", self.source)
        self.assertIn("deleteRequesters.getOrNull(index)", self.source)
        self.assertIn("onFocusLost", self.source)
        self.assertIn("deleteFocusedIndex = index", self.source)

    def test_one_tv_editing_owner_survives_projection_changes(self) -> None:
        # One hoisted editing owner, no competing local flags.
        self.assertEqual(1, self.source.count("var tvEditing by remember"))
        self.assertIn("editing = isTv && tvEditing", self.source)
        self.assertIn("onEditingChange = { tvEditing = it }", self.source)
        self.assertIn("readOnly = isTv && !editing", self.source)
        # Projection transitions while editing must not relocate focus.
        self.assertIn("if (!tvEditingNow.value) moveFocusTo(selectedFilterRequester)", self.source)
        self.assertIn("val isRemoval = !editingNow.value", self.source)
        self.assertIn("if (editingNow.value) return@LaunchedEffect", self.source)
        # Obsolete grid focus jobs are cancelled when editing begins.
        self.assertIn("LaunchedEffect(editing) {", self.source)
        self.assertIn("focusMoveState.job?.cancel()", self.source)
        # Platform IME/window blur is not navigation; Back has one owner with its release.
        self.assertIn("windowInfo.isWindowFocused", self.source)
        self.assertIn("event.key == Key.Back && editing", self.source)
        self.assertIn("backSequenceConsumed = true", self.source)
        # The explicit IME Search/Done transition ends editing once before handing focus over.
        input_start = self.source.index("private fun SmartSearchInput(")
        input_block = self.source[
            input_start : self.source.index("\n        InlineVoiceSearchAction(", input_start)
        ]
        self.assertIn("onSearch = {", input_block)
        self.assertIn("onEditingChange(false)", input_block)
        self.assertIn("moveDown()", input_block)
        self.assertIn("imeVisibleOnTv", input_block)

    def test_normal_history_delete_does_not_arm_the_next_select_press(self) -> None:
        # FocusButton's delete click is delivered on KeyUp. The ancestor preview already passed
        # that release, so arming a held-gesture guard here would swallow the next independent
        # SELECT press. A normal click must leave the arm clear; long-hold ownership stays in the
        # Favorites shell guard.
        delete_start = self.source.index("SmartSearchHistoryDeleteAction(")
        delete_block = self.source[
            delete_start : self.source.index("onFocused = {", delete_start)
        ]
        self.assertIn("onDelete = {", delete_block)
        self.assertIn("suppressSelectKeyUp = false", delete_block)
        self.assertNotIn("suppressSelectKeyUp = true", delete_block)

    def test_last_card_delete_left_stays_on_delete(self) -> None:
        self.assertNotIn("?: clearAllRequester", self.source)
        self.assertIn("moveFocusTo(deleteRequesters.getOrNull(nextIndex))", self.source)
        delete_start = self.source.index("private fun SmartSearchHistoryDeleteAction(")
        delete_block = self.source[
            delete_start : self.source.index("private fun smartSearchFilterFromMemoryKey(", delete_start)
        ]
        self.assertIn("onMoveLeft()", delete_block)
        self.assertIn("Key.DirectionLeft -> onMoveLeft()", delete_block)
        self.assertIn("Key.DirectionUp -> moveFocusTo(cardRequester)", delete_block)

    def test_failed_focus_transfers_are_not_swallowed(self) -> None:
        self.assertIn("private fun moveFocusTo(target: FocusRequester?): Boolean", self.source)
        self.assertIn("runCatching { target.requestFocus() }.getOrDefault(false)", self.source)

    def test_history_and_filter_location_use_the_existing_navigation_owner(self) -> None:
        self.assertIn("navigationMemory: NavigationMemoryStore", self.source)
        self.assertIn("smartSearchFilterFromMemoryKey(", self.source)
        self.assertIn("navigationMemory.save(", self.source)

    def test_voice_search_is_wired_and_owner_independent_of_notification_latch(self) -> None:
        self.assertIn("LocalVoiceSearchLauncher provides onVoiceSearch", self.voice)
        self.assertIn("launchVoiceSearch(query)", self.voice)
        self.assertIn("onTranscript(pending.request.owner, transcript)", self.voice)
        self.assertIn("searchFieldRequester.requestFocus()", self.voice)

        owner_start = self.view_model.index("internal fun currentVoiceSearchOwner(")
        owner_block = self.view_model[
            owner_start : self.view_model.index("\n    }", owner_start)
        ]
        self.assertIn("currentState.destination != MainDestination.SEARCH", owner_block)
        self.assertNotIn("notificationUiReady", owner_block)


if __name__ == "__main__":
    unittest.main()

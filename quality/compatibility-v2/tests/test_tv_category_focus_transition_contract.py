from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


class TvCategoryFocusTransitionContractTest(unittest.TestCase):
    @staticmethod
    def source() -> str:
        return MAIN_SHELL.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        text = cls.source()
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def test_movies_and_series_category_ok_request_first_content_without_same_selection_churn(self) -> None:
        poster = self.section("private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        self.assertIn("val selectCategoryAndEnterContent: (String?) -> Unit", poster)
        self.assertIn("nextCategoryContentFocusRequestId += 1L", poster)
        self.assertIn("val categoryChanged = state.selectedCategoryId != categoryId", poster)
        self.assertIn("focusFirstItem = categoryChanged", poster)
        self.assertIn("if (categoryChanged) {", poster)
        self.assertIn('navigationMemory.save(destination, itemKey = "", itemIndex = 0)', poster)
        self.assertIn("onSelectCategory(categoryId)", poster)
        self.assertIn("ReorderableCatalogCategoryBar(", poster)
        self.assertIn("selectCategoryAndEnterContent", poster)
        self.assertIn("destination = destination", poster)
        self.assertLess(
            poster.index("if (categoryChanged) {"),
            poster.index('navigationMemory.save(destination, itemKey = "", itemIndex = 0)'),
        )

    def test_new_catalog_category_waits_for_exact_model_and_nonempty_content_before_handoff(self) -> None:
        poster = self.section("private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        self.assertIn("request.categoryId == state.selectedCategoryId", poster)
        self.assertIn("keyedModel?.input == modelInput", poster)
        self.assertIn("if (showingContinue) continueWatching.isNotEmpty() else visible.isNotEmpty()", poster)
        self.assertIn("withFrameNanos { }", poster)
        self.assertIn("armedCategoryContentFocusRequestId = request.requestId", poster)
        self.assertIn("restoreFocusedCard = categoryContentFocusRequest?.let { request ->", poster)
        self.assertIn("armedCategoryContentFocusRequestId == request.requestId", poster)
        self.assertIn("} ?: state.searchQuery.isBlank()", poster)

    def test_live_category_ok_hands_focus_to_first_channel_only_after_it_is_composed(self) -> None:
        live = self.section("private fun LiveCatalogScreen(", "private fun LivePreviewStage(")
        self.assertIn("val selectCategoryAndEnterContent: (String?) -> Unit", live)
        self.assertIn("val categoryChanged = state.selectedCategoryId != categoryId", live)
        self.assertIn("focusFirstItem = categoryChanged", live)
        self.assertIn('navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)', live)
        self.assertIn("categoryRequest?.focusFirstItem == true && visible.isNotEmpty() -> 0", live)
        self.assertIn("categoryRequest != null && visible.isNotEmpty() -> rememberedIndex", live)
        self.assertIn("listState.scrollToItem(targetIndex)", live)
        self.assertIn("listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }", live)
        self.assertIn("withFrameNanos { }", live)
        self.assertIn("channelRequester.requestFocus()", live)
        self.assertNotIn("delay(180)", live)

    def test_empty_or_loading_category_never_requests_unbound_content_focus(self) -> None:
        poster = self.section("private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        live = self.section("private fun LiveCatalogScreen(", "private fun LivePreviewStage(")
        self.assertIn("continueWatching.isNotEmpty()", poster)
        self.assertIn("visible.isNotEmpty()", poster)
        self.assertIn("categoryRequest?.focusFirstItem == true && visible.isNotEmpty() -> 0", live)
        self.assertIn("else -> null", live)
        self.assertIn("if (targetIndex != null)", live)
        self.assertIn("resultCount == 0", poster)
        self.assertIn("type !in state.loadingTypes", poster)
        self.assertIn("ContentType.LIVE !in state.loadingTypes", live)

    def test_continue_category_handoff_uses_first_history_item_without_delay(self) -> None:
        history = self.section("private fun HistoryGrid(", "private fun DiagnosticsCenter(")
        poster = self.section("private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        self.assertIn("focusFirstItemRequestId: Long = 0L", history)
        self.assertIn("focusContentRequestId: Long = 0L", history)
        self.assertIn("if (focusFirstItemRequestId != 0L) {\n        0", history)
        self.assertIn("gridState.scrollToItem(targetIndex)", history)
        self.assertIn("gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }", history)
        self.assertIn("withFrameNanos { }", history)
        self.assertIn("index == 0", history)
        self.assertNotIn("delay(", history)
        self.assertIn("focusFirstItemRequestId = categoryContentFocusRequest", poster)
        self.assertIn("categoryContentFocusReady && it.focusFirstItem", poster)
        self.assertIn("focusContentRequestId = categoryContentFocusRequest", poster)

    def test_visible_category_restore_is_direct_and_offscreen_restore_scrolls_before_focus(self) -> None:
        helper = self.section("private fun restoreSelectedCategoryFocus(", "private fun launchGrowthUrl(")
        visible_check = helper.index("isVisible(directTarget.first)")
        direct_focus = helper.index("directTarget.second.requestFocus()", visible_check)
        scroll = helper.index("listState.scrollToItem(targetIndex)")
        composed = helper.index("snapshotFlow", scroll)
        frame = helper.index("withFrameNanos { }", composed)
        final_focus = helper.rindex("target.second.requestFocus()")
        self.assertLess(visible_check, direct_focus)
        self.assertLess(scroll, composed)
        self.assertLess(composed, frame)
        self.assertLess(frame, final_focus)
        self.assertNotIn("delay(", helper)
        self.assertNotIn("debounce", helper.lower())
        self.assertNotIn("poll", helper.lower())
        self.assertNotIn("while (", helper)
        self.assertIn("target = resolveTarget()", helper)
        self.assertIn("Re-resolve from the selected category ID after layout", helper)

    def test_live_movies_series_restore_selected_category_by_current_id_after_reorder(self) -> None:
        catalog = self.section("private fun ReorderableCatalogCategoryBar(", "private fun CatalogInteractionHints(")
        live = self.section("private fun ReorderableLiveCategoryBar(", "private fun LiveCategoryChip(")
        for block in (catalog, live):
            self.assertIn("selectedCategoryFocusIndex(", block)
            self.assertIn("selectedId = selectedId", block)
            self.assertIn("orderedIds = ordered.map(Category::id)", block)
            self.assertIn("focusRestoreState.resolveTarget = { selectedFocusTarget() }", block)
            self.assertIn("restoreSelectedCategoryFocus(", block)
            self.assertNotIn("selectedFocusTarget()?.second?.requestFocus()", block)

    def test_special_categories_and_selected_only_entry_gate_remain_supported(self) -> None:
        catalog = self.section("private fun ReorderableCatalogCategoryBar(", "private fun CatalogInteractionHints(")
        live = self.section("private fun ReorderableLiveCategoryBar(", "private fun LiveCategoryChip(")
        self.assertIn("listOf<String?>(null, FAVORITES_CATEGORY_ID, CONTINUE_CATEGORY_ID)", catalog)
        self.assertIn("CONTINUE_CATEGORY_ID -> continueFocusRequester", catalog)
        self.assertIn("listOf<String?>(null, FAVORITES_CATEGORY_ID)", live)
        self.assertIn("LIVE_TV_PRO_MAIN_RECENT_CATEGORY", live)
        for block in (catalog, live):
            self.assertIn("canFocus = !isTv || categoryBarHasFocus || selectedId == null", block)
            self.assertIn("canFocus = !isTv || categoryBarHasFocus || selectedId == FAVORITES_CATEGORY_ID", block)
            self.assertIn("canFocus = !isTv || categoryBarHasFocus || selectedId == category.id", block)

    def test_category_underlap_and_reorder_contracts_remain_intact(self) -> None:
        catalog = self.section("private fun ReorderableCatalogCategoryBar(", "private fun CatalogInteractionHints(")
        live = self.section("private fun ReorderableLiveCategoryBar(", "private fun LiveCategoryChip(")
        for block in (catalog, live):
            self.assertIn("rememberCategorySidebarUnderlap", block)
            self.assertIn("extendCategoryViewportTowardStart", block)
            self.assertIn("keepCategoryChipFullyVisibleOnFocus", block)
            self.assertIn("fun move(id: String, direction: Int)", block)
            self.assertIn("prefs.edit().putString", block)

    def test_new_focus_flow_adds_no_arbitrary_delay_polling_or_retry_loop(self) -> None:
        helper = self.section("private fun restoreSelectedCategoryFocus(", "private fun launchGrowthUrl(")
        poster = self.section("private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        live = self.section("private fun LiveCatalogScreen(", "private fun LivePreviewStage(")
        history = self.section("private fun HistoryGrid(", "private fun DiagnosticsCenter(")
        for block in (helper, poster, live, history):
            self.assertNotIn("delay(40", block)
            self.assertNotIn("delay(90", block)
            self.assertNotIn("delay(120", block)
            self.assertNotIn("delay(180", block)
            self.assertNotIn("delay(300", block)
            self.assertNotIn("debounce", block.lower())
            self.assertNotIn("retry", block.lower())


if __name__ == "__main__":
    unittest.main()

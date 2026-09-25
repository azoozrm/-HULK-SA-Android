from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
TV_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"


class TvRememberedContentHandoffOwnershipContractTest(unittest.TestCase):
    @classmethod
    def source(cls) -> str:
        return MAIN_SHELL.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        text = cls.source()
        start_index = text.index(start)
        return text[start_index : text.index(end, start_index)]

    def shell(self) -> str:
        return self.section(
            "fun MainShellScreen(",
            "@Composable\nprivate fun CinematicNavigationRail(",
        )

    def decision(self) -> str:
        return self.section(
            "val rememberedPosterContentOwnsTvHandoff",
            "val homeModel =",
        )

    def handoff_effect(self) -> str:
        return self.section(
            "LaunchedEffect(\n        useNavigationRail,",
            "Box(Modifier.fillMaxSize().background(colors.background))",
        )

    def test_remembered_poster_restore_and_all_handoff_have_one_exclusive_owner(self) -> None:
        decision = self.decision()
        self.assertIn(
            "val rememberedPosterContentOwnsTvHandoff: (MainDestination) -> Boolean = { destination ->",
            decision,
        )
        self.assertIn("val contentRestoreOwnsTvHandoff =", decision)
        self.assertIn(
            "destination != state.destination && rememberedPosterContentOwnsTvHandoff(destination)",
            decision,
        )
        self.assertIn("pendingTvContentFocusHandoff = if (contentRestoreOwnsTvHandoff) {", decision)
        owner_branch = decision.index("pendingTvContentFocusHandoff = if (contentRestoreOwnsTvHandoff) {")
        null_branch = decision.index("null", owner_branch)
        request_branch = decision.index("TvContentFocusHandoffRequest(", null_branch)
        self.assertLess(null_branch, request_branch)
        self.assertEqual(1, decision.count("pendingTvContentFocusHandoff ="))
        self.assertEqual(2, decision.count("rememberedPosterContentOwnsTvHandoff"))
        self.assertEqual(2, decision.count("contentRestoreOwnsTvHandoff"))

    def test_remembered_poster_eligibility_reuses_existing_navigation_memory(self) -> None:
        decision = self.decision()
        self.assertIn("MainDestination.MOVIES -> ContentType.MOVIE", decision)
        self.assertIn("MainDestination.SERIES -> ContentType.SERIES", decision)
        self.assertIn("val rememberedKey = navigationMemory.position(destination).itemKey", decision)
        self.assertIn("state.searchQuery.isBlank()", decision)
        self.assertIn("rememberedKey.isNotBlank()", decision)
        self.assertIn(
            'state.catalogs[type]?.items?.any { "${it.type}:${it.id}" == rememberedKey } == true',
            decision,
        )

    def test_all_handoff_cannot_override_a_pending_remembered_content_restore(self) -> None:
        decision = self.decision()
        handoff = self.handoff_effect()
        self.assertIn("pendingTvContentFocusHandoff = if (contentRestoreOwnsTvHandoff) {", decision)
        self.assertIn("null", decision)
        self.assertIn("pendingTvContentFocusHandoff ?: return@LaunchedEffect", handoff)
        self.assertIn("withFrameNanos { }", handoff)
        self.assertIn("currentTvDestinationFocusRequester.requestFocus()", handoff)
        self.assertIn("pendingTvContentFocusHandoff = null", handoff)
        grid = TV_GRID.read_text(encoding="utf-8")
        self.assertIn("focusRequesters[targetIndex].requestFocus()", grid)

    def test_initial_catalog_handoff_remains_available_without_remembered_content(self) -> None:
        shell = self.shell()
        decision = self.decision()
        self.assertIn("if (destination != state.destination)", decision)
        self.assertIn("onSelectDestination(destination)", decision)
        self.assertIn("TvContentFocusHandoffRequest(", decision)
        self.assertIn("rememberedKey.isNotBlank()", decision)
        self.assertIn("state.searchQuery.isBlank()", decision)
        self.assertIn(
            "tvCatalogAllFocusRequesters[state.destination] ?: currentTvContentFocusRequester",
            shell,
        )
        self.assertIn("initialAllFocusRequester = tvCatalogAllFocusRequesters[state.destination]", shell)
        self.assertIn("initialAllFocusPending = currentTvCatalogInitialFocusPending", shell)
        catalog_bar = self.section(
            "private fun ReorderableCatalogCategoryBar(",
            "private fun CatalogInteractionHints(",
        )
        live_bar = self.section(
            "private fun ReorderableLiveCategoryBar(",
            "private fun LiveCategoryChip(",
        )
        for block in (catalog_bar, live_bar):
            self.assertIn("allowInitialEntry = initialAllFocusPending", block)

    def test_category_content_handoff_remains_intact(self) -> None:
        poster = self.section(
            "private fun PosterCatalogScreen(",
            "internal fun resolveLivePreview(",
        )
        self.assertIn("val categoryChanged = state.selectedCategoryId != categoryId", poster)
        self.assertIn("focusFirstItem = categoryChanged", poster)
        self.assertIn("withFrameNanos { }", poster)
        self.assertIn("restoreFocusedCard = categoryContentFocusRequest?.let { request ->", poster)
        self.assertIn("armedCategoryContentFocusRequestId == request.requestId", poster)

    def test_offscreen_remembered_grid_target_keeps_identity_visibility_then_focus_order(self) -> None:
        grid = TV_GRID.read_text(encoding="utf-8")
        self.assertIn("val rememberedKeyIndex = contentKeyIndex[remembered.itemKey] ?: -1", grid)
        self.assertIn(
            "val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)",
            grid,
        )
        restore_start = grid.index(
            "LaunchedEffect(contentKeys, remembered.itemKey, destination, restoreFocusedCard)"
        )
        restore = grid[restore_start : grid.index("BoxWithConstraints", restore_start)]
        scroll = restore.index("gridState.scrollToItem(targetIndex)")
        visible = restore.index(
            "snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }",
            scroll,
        )
        placed = restore.index("ensureIndexFullyVisible(targetIndex)", visible)
        focus = restore.index("focusRequesters[targetIndex].requestFocus()", placed)
        self.assertLess(scroll, visible)
        self.assertLess(visible, placed)
        self.assertLess(placed, focus)
        self.assertNotIn("delay(", restore)

    def test_remembered_content_handoff_decision_adds_no_arbitrary_timing_or_retry(self) -> None:
        for block in (self.decision(), self.handoff_effect()):
            lowered = block.lower()
            self.assertNotIn("delay(", block)
            self.assertNotIn("debounce", lowered)
            self.assertNotIn("poll", lowered)
            self.assertNotIn("retry", lowered)
            self.assertNotIn("while (", block)

    def test_grid_restoration_and_live_s3_memory_ownership_remain_untouched(self) -> None:
        grid = TV_GRID.read_text(encoding="utf-8")
        self.assertIn("navigationMemory.save(destination, contentKeys[nextIndex], nextIndex)", grid)
        self.assertIn("navigationMemory.save(destination, key, index)", grid)
        self.assertNotIn("firstVisibleItemIndex", grid)
        live = self.section(
            "private fun LiveCatalogScreen(",
            "private fun LivePreviewStage(",
        )
        self.assertIn("LaunchedEffect(listState, visible, isTv) {", live)
        self.assertIn("if (isTv) return@LaunchedEffect", live)
        self.assertEqual(1, live.count("listState.firstVisibleItemIndex"))
        self.assertNotIn("rememberedPosterContentOwnsTvHandoff", live)

    def test_mobile_touch_selection_path_is_not_rerouted_through_the_tv_handoff(self) -> None:
        shell = self.shell()
        self.assertIn("onSelect = selectTvDestination", shell)
        self.assertIn("useNavigationRail", shell)
        mobile = self.section(
            "} else {\n            Column(Modifier.fillMaxSize()) {",
            "private fun CinematicNavigationRail(",
        )
        self.assertIn("onSelectDestination", mobile)
        self.assertNotIn("selectTvDestination", mobile)


if __name__ == "__main__":
    unittest.main()

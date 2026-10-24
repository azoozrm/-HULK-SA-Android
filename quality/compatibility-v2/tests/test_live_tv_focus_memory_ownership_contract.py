from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
TV_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"


class LiveTvFocusMemoryOwnershipContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @classmethod
    def live(cls) -> str:
        source = cls.read(MAIN_SHELL)
        start = source.index("private fun LiveCatalogScreen(")
        return source[start : source.index("private fun LivePreviewStage(", start)]

    def test_live_logical_focus_memory_is_written_by_actual_channel_focus(self) -> None:
        live = self.live()
        self.assertIn('val key = liveChannelKey(channel)', live)
        self.assertIn("onFocused = {", live)
        self.assertIn("focusedChannelIndex[0] = index", live)
        self.assertIn("navigationMemory.save(MainDestination.LIVE, key, index)", live)

    def test_live_viewport_scroll_cannot_overwrite_the_focused_item_memory(self) -> None:
        live = self.live()
        self.assertIn("LaunchedEffect(listState, visible, isTv) {", live)
        self.assertIn("if (isTv) return@LaunchedEffect", live)
        self.assertEqual(1, live.count("listState.firstVisibleItemIndex"))
        snapshot = live.index("snapshotFlow { listState.firstVisibleItemIndex }")
        guard = live.index("if (isTv) return@LaunchedEffect")
        self.assertLess(guard, snapshot)

    def test_leaving_live_restores_the_remembered_focused_channel(self) -> None:
        live = self.live()
        self.assertIn("val remembered = navigationMemory.position(MainDestination.LIVE)", live)
        self.assertIn(
            "val rememberedIndex = remembered.itemIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))",
            live,
        )
        self.assertIn("rememberLazyListState(initialFirstVisibleItemIndex = rememberedIndex)", live)
        self.assertIn(
            "val restore = key == liveFocusKey ||",
            live,
        )
        self.assertIn("liveFocusKey.isBlank() && index == rememberedIndex", live)
        self.assertIn(
            "state.searchQuery.isBlank() && remembered.itemKey.isNotBlank() && visible.isNotEmpty() -> rememberedIndex",
            live,
        )
        self.assertIn("listState.scrollToItem(targetIndex)", live)
        self.assertIn("channelRequester.requestFocus()", live)

    def test_live_category_entry_and_first_item_handoff_remain_intact(self) -> None:
        live = self.live()
        self.assertIn("val categoryChanged = state.selectedCategoryId != categoryId", live)
        self.assertIn("focusFirstItem = categoryChanged", live)
        self.assertIn(
            'navigationMemory.save(MainDestination.LIVE, itemKey = "", itemIndex = 0)',
            live,
        )
        self.assertIn("categoryRequest?.focusFirstItem == true && visible.isNotEmpty() -> 0", live)
        self.assertIn("categoryRequest != null && visible.isNotEmpty() -> rememberedIndex", live)

    def test_movies_and_series_grid_keep_their_single_focused_item_writer(self) -> None:
        grid = self.read(TV_GRID)
        self.assertNotIn("firstVisibleItemIndex", grid)
        normalized_grid = " ".join(grid.split())
        self.assertIn(
            "navigationMemory.save( destination, contentKeys[nextIndex], nextIndex, rowKey = memoryRowKey, )",
            normalized_grid,
        )
        self.assertIn(
            "navigationMemory.save(destination, key, index, rowKey = memoryRowKey)",
            normalized_grid,
        )

    def test_favorite_recomposition_cannot_rekey_navigation_restore(self) -> None:
        live = self.live()
        start = live.index("LaunchedEffect(\n        visible,")
        keys = live[start : live.index(") {", start)]
        # A focus save is navigation state, not a restoration trigger. A favorite/notice
        # recomposition must not replay entry restoration after focus saved a newer key.
        self.assertNotIn("remembered.itemKey", keys)
        self.assertIn("state.selectedCategoryId", keys)
        self.assertIn("state.searchQuery", keys)
        self.assertIn("categoryContentFocusRequest", keys)

    def test_live_favorites_row_removal_wires_shell_guard_and_handoff(self) -> None:
        live = self.live()
        self.assertIn("onRemoteLongClick = {", live)
        self.assertIn("beginFavoritesRemovalLongPress(", live)
        self.assertIn("onFavoritesRemovalStarted", live)
        self.assertIn("liveRemovalHandoff(visible, key)", live)
        self.assertIn("previewRemovalInProgress", live)


    def test_live_heart_reads_the_explicit_favorite_snapshot(self) -> None:
        source = self.read(MAIN_SHELL)
        live = self.live()
        stage_start = source.index("private fun LivePreviewStage(")
        stage = source[stage_start : source.index("private fun LiveStage(", stage_start)]
        self.assertIn("favorites: CatalogFavoriteSnapshot", live)
        self.assertIn("favorites::contains", live)
        self.assertIn("favorites.contains(channel)", live)
        self.assertIn("preview?.let(favorites::contains)", stage)
        self.assertNotIn("isFavorite(channel)", live)



if __name__ == "__main__":
    unittest.main()

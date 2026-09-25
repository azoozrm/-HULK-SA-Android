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
        self.assertIn('val key = "${channel.type}:${channel.id}"', live)
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
            "val restore = key == remembered.itemKey || (remembered.itemKey.isBlank() && index == rememberedIndex)",
            live,
        )
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
        self.assertIn("navigationMemory.save(destination, contentKeys[nextIndex], nextIndex)", grid)
        self.assertIn("navigationMemory.save(destination, key, index)", grid)


if __name__ == "__main__":
    unittest.main()

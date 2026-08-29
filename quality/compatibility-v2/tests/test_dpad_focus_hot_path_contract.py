from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
CATALOG_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"


class DpadFocusHotPathContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_fully_visible_grid_target_has_no_coroutine_or_scroll(self) -> None:
        source = self.read(CATALOG_GRID)
        handler = self.section(
            source,
            ".onPreviewKeyEvent { event ->",
            "val onFocusedCard = {",
        )
        direct = self.section(
            handler,
            "val focusedDirectly = if (focusPath == TvCatalogFocusPath.DIRECT)",
            "if (focusedDirectly)",
        )

        self.assertIn("requester.requestFocus()", direct)
        self.assertNotIn("focusScope.launch", direct)
        self.assertNotIn("scrollToItem", direct)
        self.assertNotIn("scrollBy", direct)
        self.assertNotIn("ensureIndexFullyVisible", direct)

    def test_edge_targets_keep_scroll_assisted_path_and_pending_guard(self) -> None:
        source = self.read(CATALOG_GRID)
        handler = self.section(
            source,
            ".onPreviewKeyEvent { event ->",
            "val onFocusedCard = {",
        )

        self.assertIn("focusMoveState.job = focusScope.launch", handler)
        self.assertIn("focusIndex(", handler)
        self.assertIn("ensureFullyVisible = true", handler)
        self.assertIn("focusMoveState.complete(nextIndex)", handler)
        self.assertIn("focusRequesters.requesterOrNull(nextIndex)", handler)
        self.assertNotIn("mutableStateOf<Job?>", source)
        self.assertNotIn("mutableStateOf<Int?>", source)

    def test_live_preview_read_is_isolated_from_list_items(self) -> None:
        source = self.read(MAIN_SHELL)
        live = self.section(
            source,
            "private fun LiveCatalogScreen(",
            "private fun LivePreviewStage(",
        )

        self.assertIn("val previewState = remember(", live)
        self.assertIn("derivedStateOf { isLivePreviewSelected(previewState.value, channel) }", live)
        self.assertIn("selected = selected", live)
        self.assertIn("LivePreviewStage(", live)
        self.assertNotIn("selected = preview?.id", live)
        self.assertNotIn("item = previewState.value", live)

    def test_only_stage_host_reads_preview_for_stage_rendering(self) -> None:
        source = self.read(MAIN_SHELL)
        host = self.section(
            source,
            "private fun LivePreviewStage(",
            "private fun LiveStage(",
        )

        self.assertIn("val preview = previewState.value", host)
        self.assertIn("item = preview", host)
        self.assertIn("previewState.value?.let(onOpen)", host)
        self.assertIn("previewState.value?.let(onToggleFavorite)", host)

    def test_focus_requesters_are_lazy_and_shared_across_focus_paths(self) -> None:
        source = self.read(CATALOG_GRID)
        focus_index = self.section(
            source,
            "suspend fun focusIndex(",
            "LaunchedEffect(contentKeys,",
        )

        self.assertIn("remember(contentKeys) { TvCatalogFocusRequesterStore(contentKeys) }", source)
        self.assertIn(".focusRequester(focusRequesters.requester(index))", source)
        self.assertIn("focusRequesters.requesterOrNull(index)", focus_index)
        self.assertIn("requesters.getOrPut(index, factory)", source)
        self.assertNotIn("List(contentKeys.size) { FocusRequester() }", source)


if __name__ == "__main__":
    unittest.main()

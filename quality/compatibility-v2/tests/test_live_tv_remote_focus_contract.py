from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
PLAYER_PRO = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerProEpisodeNavigation.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
LIVE_TV_BROWSER = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/LiveTvProChannelBrowser.kt"


class LiveTvRemoteFocusQualificationTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_picture_size_panel_owns_focus_without_second_ok(self) -> None:
        text = self.read(PLAYER_SCREEN)
        self.assertIn("val optionFocus = remember { FocusRequester() }", text)
        self.assertIn("runCatching { optionFocus.requestFocus() }", text)
        self.assertIn("browserVisible || activePanel != null -> null", text)
        self.assertIn(
            "event.type != KeyEventType.KeyDown || browserVisible || activePanel != null ||",
            text,
        )

    def test_parent_ok_cycle_cannot_open_channel_browser_over_child_panel(self) -> None:
        text = self.read(PLAYER_PRO)
        select_contract = re.compile(
            r"KEYCODE_DPAD_CENTER,\s*"
            r"AndroidKeyEvent\.KEYCODE_ENTER,\s*"
            r"AndroidKeyEvent\.KEYCODE_NUMPAD_ENTER,\s*"
            r"->\s*\{\s*"
            r"if \(childControlSelectionPending\) \{.*?"
            r"return@onPreviewKeyEvent false\s*"
            r"\}\s*"
            r"if \(!liveControlsLikelyVisible\) \{.*?"
            r"liveBrowserVisible = true",
            re.DOTALL,
        )
        self.assertRegex(text, select_contract)
        self.assertIn("childControlSelectionPending = true", text)

    def test_hardware_channel_keys_keep_live_zapping_contract(self) -> None:
        text = self.read(PLAYER_PRO)
        next_contract = re.compile(
            r"KEYCODE_CHANNEL_UP,\s*AndroidKeyEvent\.KEYCODE_MEDIA_NEXT,\s*->\s*\{.*?"
            r"queueLiveRelative\(1\)",
            re.DOTALL,
        )
        previous_contract = re.compile(
            r"KEYCODE_CHANNEL_DOWN,\s*AndroidKeyEvent\.KEYCODE_MEDIA_PREVIOUS,\s*->\s*\{.*?"
            r"queueLiveRelative\(-1\)",
            re.DOTALL,
        )
        self.assertRegex(text, next_contract)
        self.assertRegex(text, previous_contract)
        self.assertIn("ANDROID_KEYCODE_LAST_CHANNEL", text)

    def test_browser_and_option_layers_isolate_player_surface_keys(self) -> None:
        player_text = self.read(PLAYER_SCREEN)
        pro_text = self.read(PLAYER_PRO)
        self.assertIn("browserVisible || activePanel != null", player_text)
        self.assertIn("if (liveBrowserVisible) return@onPreviewKeyEvent false", pro_text)
        self.assertIn("cancelPendingLiveZap()", pro_text)
        self.assertIn("dismissLiveZapIndicator()", pro_text)

    def test_live_category_strip_routes_entry_directly_to_selected_focus(self) -> None:
        text = self.read(MAIN_SHELL)
        start = text.index("private fun ReorderableLiveCategoryBar(")
        end = text.index("private fun LiveCategoryChip(", start)
        block = text[start:end]
        self.assertIn("selectedCategoryFocusIndex(", block)
        self.assertIn("fun selectedFocusTarget(): CategoryFocusTarget?", block)
        self.assertIn("onEnter = {", block)
        self.assertIn("restoreSelectedCategoryFocus(", block)
        self.assertIn("focusRestoreController.resolveTarget = { selectedFocusTarget() }", block)
        self.assertIn("cancelDefaultEntry = { cancelFocusChange() }", block)
        self.assertIn("var categoryBarHasFocus by remember", block)
        self.assertIn(".categoryChipFocus(", block)
        self.assertIn("controller.pendingRequest != null", text)
        self.assertIn(
            "val focusedChannelIndex = remember(visible) { intArrayOf(rememberedIndex) }",
            text,
        )
        self.assertNotIn("var focusedChannelIndex by remember", text)
        self.assertIn("focusedChannelIndex[0] == 0", text)
        self.assertIn("categoryFocusRestoreController.requestFromSource()", text)
        self.assertIn("restoreSelectedCategoryFocus(", block)

    def test_player_browser_visible_selected_category_skips_reveal_scroll(self) -> None:
        text = self.read(LIVE_TV_BROWSER)
        start = text.index("fun returnFocusToSelectedCategory()")
        end = text.index("LaunchedEffect(visible, selectedCategory, normalizedQuery)", start)
        block = text[start:end]
        self.assertIn("val targetCategoryId = selectedCategory", block)
        self.assertIn("val initialTarget = resolveCategoryReturnTarget(targetCategoryId)", block)
        self.assertIn("liveTvProCategoryReturnNeedsReveal(initialTarget.first, visibleIndices)", block)
        self.assertEqual(1, block.count("categoryListState.scrollToItem"))
        self.assertGreater(
            block.index("categoryListState.scrollToItem(initialTarget.first)"),
            block.index("if (liveTvProCategoryReturnNeedsReveal"),
        )

    def test_player_browser_offscreen_return_waits_for_exact_target_before_focus(self) -> None:
        text = self.read(LIVE_TV_BROWSER)
        start = text.index("fun returnFocusToSelectedCategory()")
        end = text.index("LaunchedEffect(visible, selectedCategory, normalizedQuery)", start)
        block = text[start:end]
        scroll = block.index("categoryListState.scrollToItem(initialTarget.first)")
        confirmation = block.index("snapshotFlow", scroll)
        re_resolve = block.index("resolveCategoryReturnTarget(targetCategoryId)", confirmation)
        focus = block.index("resolvedTarget.second.requestFocus()", re_resolve)
        self.assertLess(scroll, confirmation)
        self.assertLess(confirmation, re_resolve)
        self.assertLess(re_resolve, focus)
        self.assertIn(".first { it }", block)
        self.assertNotIn("delay(", block)
        self.assertNotIn("repeat(", block)
        self.assertNotIn("while (", block)

    def test_player_browser_return_never_focuses_interim_or_fallback_category(self) -> None:
        text = self.read(LIVE_TV_BROWSER)
        start = text.index("fun returnFocusToSelectedCategory()")
        end = text.index("LaunchedEffect(visible, selectedCategory, normalizedQuery)", start)
        block = text[start:end]
        self.assertEqual(4, block.count("targetCategoryId"))
        self.assertNotIn("allCategoryFocus.requestFocus", block)
        self.assertNotIn("favoritesCategoryFocus.requestFocus", block)
        self.assertNotIn("recentCategoryFocus.requestFocus", block)
        self.assertIn("resolvedTarget.second.requestFocus()", block)

    def test_player_browser_dpad_left_repeat_is_one_shot(self) -> None:
        text = self.read(LIVE_TV_BROWSER)
        row_start = text.index("fun BrowserChannelRow(")
        row_end = text.index("fun ReorderableCategoryRow(", row_start)
        row = text[row_start:row_end]
        self.assertIn("event.nativeKeyEvent.repeatCount == 0", row)
        self.assertIn("onReturnToCategory()", row)
        self.assertIn("if (!categoryReturnGate.tryStart()) return", text)
        self.assertIn("categoryReturnGate.finish()", text)


if __name__ == "__main__":
    unittest.main()

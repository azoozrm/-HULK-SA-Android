from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
PLAYER_PRO = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerProEpisodeNavigation.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


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

    def test_live_category_strip_restores_focus_context(self) -> None:
        text = self.read(MAIN_SHELL)
        start = text.index("private fun ReorderableLiveCategoryBar(")
        end = text.index("private fun LiveCategoryChip(", start)
        block = text[start:end]
        self.assertIn("LaunchedEffect(selectedId)", block)
        self.assertIn("Modifier.focusRestorer().focusGroup()", block)
        self.assertIn("listState.scrollToItem(anchorIndex)", block)


if __name__ == "__main__":
    unittest.main()

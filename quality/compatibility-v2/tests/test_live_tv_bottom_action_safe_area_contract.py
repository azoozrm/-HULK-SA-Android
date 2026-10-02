from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


class LiveTvBottomActionSafeAreaContractTest(unittest.TestCase):
    @staticmethod
    def live_catalog() -> str:
        text = MAIN_SHELL.read_text(encoding="utf-8")
        start = text.index("private fun LiveCatalogScreen(")
        end = text.index("@Composable\nprivate fun LivePreviewStage(", start)
        return text[start:end]

    @staticmethod
    def live_stage() -> str:
        text = MAIN_SHELL.read_text(encoding="utf-8")
        start = text.index("private fun LiveStage(")
        end = text.index("@Composable\nprivate fun FavoritesScreen(", start)
        return text[start:end]

    @staticmethod
    def tv_row(catalog: str) -> str:
        start = catalog.index("} else if (isTv) {")
        end = catalog.index("} else {\n            LazyColumn(", start)
        return catalog[start:end]

    def test_live_tv_pane_insets_inherit_the_screen_safe_clearance(self) -> None:
        row = self.tv_row(self.live_catalog())
        self.assertRegex(row, re.compile(r"start\s*=\s*TV_PAGE_GUTTER\b"))
        self.assertRegex(row, re.compile(r"end\s*=\s*TV_CATEGORY_PARENT_HORIZONTAL_INSET_DP\.dp\b"))
        self.assertRegex(
            row,
            re.compile(r"Arrangement\.spacedBy\(20\.dp\s*-\s*TV_PAGE_GUTTER\)"),
        )
        self.assertNotIn("horizontal = 8.dp", row)
        self.assertNotIn("TCL", row)

    def test_live_stage_actions_inherit_horizontal_clearance_and_keep_bottom_inset(self) -> None:
        block = self.live_stage()
        # Horizontal clearance for the actions is inherited from the TV content Row plus the
        # metadata column inset; the stage must not re-add the old horizontal action padding.
        self.assertEqual(1, block.count("padding(horizontal = 4.dp)"))
        self.assertNotIn("horizontal = TV_PAGE_GUTTER", block)
        self.assertIn("bottom = TV_LIVE_ACTION_INSET", block)
        self.assertNotIn("TCL", block)

    def test_live_bottom_buttons_remain_siblings_with_focus_graph_unchanged(self) -> None:
        block = self.live_stage()
        actions_start = block.index(".padding(bottom = TV_LIVE_ACTION_INSET)")
        actions = block[actions_start:]
        self.assertEqual(actions.count("FocusButton("), 2)
        self.assertIn("horizontalArrangement = Arrangement.spacedBy(12.dp)", actions)
        self.assertRegex(
            actions,
            re.compile(
                r'FocusButton\(\s*"تشغيل القناة".*?'
                r'left = favoriteRequester; right = channelRequester.*?'
                r'FocusButton\(\s*if \(isFavorite\).*?'
                r'left = channelRequester; right = playRequester',
                re.DOTALL,
            ),
        )

    def test_live_tv_channel_list_returns_focus_to_the_play_action(self) -> None:
        row = self.tv_row(self.live_catalog())
        self.assertRegex(
            row,
            re.compile(r"ChannelListItem\(.*?left\s*=\s*playRequester", re.DOTALL),
        )

    def test_live_preview_stage_remains_tv_only_and_mobile_list_path_is_unchanged(self) -> None:
        text = MAIN_SHELL.read_text(encoding="utf-8")
        tv_branch = text.index("} else if (isTv) {", text.index("private fun LiveCatalogScreen("))
        preview = text.index("LivePreviewStage(", tv_branch)
        mobile_branch = text.index("} else {\n            LazyColumn(", preview)
        self.assertLess(tv_branch, preview)
        self.assertLess(preview, mobile_branch)


if __name__ == "__main__":
    unittest.main()

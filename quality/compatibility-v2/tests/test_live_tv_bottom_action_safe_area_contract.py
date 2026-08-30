from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


class LiveTvBottomActionSafeAreaContractTest(unittest.TestCase):
    @staticmethod
    def live_stage() -> str:
        text = MAIN_SHELL.read_text(encoding="utf-8")
        start = text.index("private fun LiveStage(")
        end = text.index("@Composable\nprivate fun FavoritesScreen(", start)
        return text[start:end]

    def test_live_bottom_actions_keep_tv_horizontal_and_bottom_safe_insets(self) -> None:
        block = self.live_stage()
        self.assertIn("horizontal = TV_PAGE_GUTTER", block)
        self.assertIn("bottom = TV_LIVE_ACTION_INSET", block)
        self.assertNotIn("horizontal = 8.dp", block)
        self.assertNotIn("TCL", block)

    def test_live_bottom_buttons_remain_siblings_with_focus_graph_unchanged(self) -> None:
        block = self.live_stage()
        actions_start = block.index("horizontal = TV_PAGE_GUTTER")
        actions = block[actions_start:]
        self.assertEqual(actions.count("FocusButton("), 2)
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
        self.assertIn("horizontalArrangement = Arrangement.spacedBy(12.dp)", actions)

    def test_live_preview_stage_remains_tv_only_and_mobile_list_path_is_unchanged(self) -> None:
        text = MAIN_SHELL.read_text(encoding="utf-8")
        tv_branch = text.index("} else if (isTv) {", text.index("private fun LiveCatalogScreen("))
        preview = text.index("LivePreviewStage(", tv_branch)
        mobile_branch = text.index("} else {\n            LazyColumn(", preview)
        self.assertLess(tv_branch, preview)
        self.assertLess(preview, mobile_branch)


if __name__ == "__main__":
    unittest.main()

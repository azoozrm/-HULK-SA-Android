from pathlib import Path
import unittest

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


class R06CategoryStripSameOriginReturnContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.shell = MAIN_SHELL.read_text(encoding="utf-8")

    def section(self, start: str, end: str) -> str:
        begin = self.shell.index(start)
        return self.shell[begin:self.shell.index(end, begin)]

    def test_both_category_strips_save_both_focus_boundaries(self) -> None:
        catalog = self.section("private fun CatalogCategoryBar(", "private fun LiveCategoryBar(")
        live = self.section("private fun LiveCategoryBar(", "private fun LiveCategoryChip(")
        for label, block in (("catalog", catalog), ("live", live)):
            self.assertIn("val barSaveFocusRequester", block, label)
            self.assertIn("val barScrollSaveFocusRequester", block, label)
            self.assertIn(".focusRequester(barSaveFocusRequester)", block, label)
            group = block.index(".focusRequester(barSaveFocusRequester)")
            group_end = block.index(".focusGroup()", group)
            scroll = block.index(
                ".saveFocusedChildOnExit(barScrollSaveFocusRequester)",
                group_end,
            )
            self.assertLess(group, group_end, label)
            self.assertLess(group_end, scroll, label)
            self.assertNotIn(".saveFocusedChildOnExit(barSaveFocusRequester)", block, label)
            self.assertIn("barSaveFocusRequester.saveFocusedChild()", block, label)
            self.assertIn("applyCategoryGroupExit(", block, label)
            self.assertIn("clearRetainedOrigin()", block, label)
            self.assertNotIn("delay(", block, label)
            self.assertNotIn("scrollToItem(target", block, label)
        self.assertIn("if (focusState.isFocused) controller.clearRetainedOrigin()", self.shell)
        self.assertEqual(2, self.shell.count("requestOrdinaryFocus {"))

    def test_exact_origin_gate_is_additive_and_legacy_selected_entry_remains(self) -> None:
        gate = self.section(
            "internal fun canCategoryChipReceiveFocus(",
            "@Composable\ninternal fun Modifier.categoryFocusTarget(",
        )
        self.assertIn("retainedOriginId: String? = null", gate)
        self.assertIn("retainedOriginActive: Boolean = false", gate)
        self.assertIn("selectedId == chipId", gate)
        self.assertIn("retainedOriginActive && retainedOriginId == chipId", gate)
        self.assertIn("!restorePending", gate)
        self.assertIn("categoryBarHasFocus", gate)
        self.assertIn("lastFocusedCategoryId = categoryId", self.shell)
        self.assertIn("fun retainLastFocusedOrigin()", self.shell)
        self.assertIn("retainedOriginCategoryId = lastFocusedCategoryId", self.shell)
        self.assertIn("requestedFocusDirection == FocusDirection.Up", self.shell)
        self.assertIn("requestedFocusDirection == FocusDirection.Down", self.shell)
        self.assertIn("fun requestOrdinaryFocus(", self.shell)
        self.assertIn("fun consumeOrdinaryExit(", self.shell)
        self.assertIn("internal fun applyCategoryGroupExit(", self.shell)
        self.assertIn("saveFocusedChild()", self.shell)
        self.assertIn("if (ordinaryExit) clearRetainedOrigin() else retainLastFocusedOrigin()", self.shell)

    def test_selected_entry_and_manager_entry_owners_are_unchanged(self) -> None:
        self.assertIn("restoreSelectedCategoryFocus(", self.shell)
        self.assertIn("cancelDefaultEntry = { cancelFocusChange() }", self.shell)
        self.assertIn("selectedCategoryFocusIndex(", self.shell)
        self.assertIn("focusRestoreController.resolveTarget = { selectedFocusTarget() }", self.shell)


if __name__ == "__main__":
    unittest.main()

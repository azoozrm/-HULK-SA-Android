from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
BOTTOM_NAV = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/StableMobileBottomNavigation.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
SMART_SEARCH = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileSmartSearchLayer.kt"

OUTER_OWNER_GUARD = re.compile(
    r"state\.screen == HulkScreen\.MAIN\s*&&\s*!isTv\s*&&\s*"
    r"adaptiveUi\.navigationType != HulkNavigationType\.RAIL",
)
RESERVED_SPACER = re.compile(
    r"Spacer\(\s*Modifier\s*\.navigationBarsPadding\(\)\s*"
    r"\.height\(MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT\),?\s*\)",
)


class SingleMobileNavigationOwnerContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_outer_stable_mobile_bar_is_the_only_composed_mobile_owner(self) -> None:
        app = self.read(HULK_APP)

        self.assertEqual(1, app.count("StableMobileBottomNavigation("))
        self.assertRegex(app, OUTER_OWNER_GUARD)

    def test_main_shell_composes_no_competing_mobile_navigation_owner(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertNotIn("MobileNavigation(", shell)
        self.assertNotIn("private fun MobileNavigation(", shell)
        self.assertNotIn("mobileNavigationPosition", shell)
        self.assertIn("CinematicNavigationRail(", shell)
        self.assertIn("onSelect = selectTvDestination", shell)

    def test_search_composes_no_competing_smart_search_navigation_owner(self) -> None:
        search = self.read(SMART_SEARCH)

        self.assertNotIn("SmartSearchBottomNavigation", search)
        self.assertIn("SmartSearchRail(", search)
        self.assertIn("if (useRail) {", search)

    def test_bottom_clearance_is_reserved_without_a_second_navigation_control(self) -> None:
        nav = self.read(BOTTOM_NAV)
        shell = self.read(MAIN_SHELL)
        search = self.read(SMART_SEARCH)

        self.assertIn("internal val MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT =", nav)
        self.assertRegex(
            nav,
            r"MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT =\s*\n\s*"
            r"MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT \+ "
            r"MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING \* 2",
        )
        self.assertIn(".height(MOBILE_BOTTOM_NAVIGATION_ITEM_HEIGHT)", nav)
        self.assertIn(".padding(vertical = MOBILE_BOTTOM_NAVIGATION_VERTICAL_PADDING)", nav)
        self.assertRegex(shell, RESERVED_SPACER)
        self.assertRegex(search, RESERVED_SPACER)
        self.assertEqual(2, shell.count("MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT"))
        self.assertEqual(1, search.count("MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT"))

    def test_ime_visibility_contract_remains_intentional_on_both_paths(self) -> None:
        nav = self.read(BOTTOM_NAV)
        search = self.read(SMART_SEARCH)

        self.assertIn("if (WindowInsets.isImeVisible) return", nav)
        self.assertIn("val imeVisible = !isTv && WindowInsets.isImeVisible", search)
        self.assertIn(".imePadding()", search)
        self.assertRegex(search, r"if \(!imeVisible\) \{\s*Spacer\(")

    def test_tv_and_expanded_rail_ownership_is_untouched(self) -> None:
        shell = self.read(MAIN_SHELL)
        search = self.read(SMART_SEARCH)

        rail = shell.index("CinematicNavigationRail(")
        shell_spacer = shell.index("height(MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT)")
        self.assertLess(rail, shell_spacer)
        self.assertIn("if (useNavigationRail) {", shell)
        self.assertIn("initialAllFocusRequester = tvCatalogAllFocusRequesters[state.destination]", shell)
        self.assertIn(".focusRequester(currentTvContentFocusRequester)", shell)

        use_rail = search.index("if (useRail) {")
        smart_rail = search.index("SmartSearchRail(")
        search_spacer = search.index("height(MOBILE_BOTTOM_NAVIGATION_RESERVED_HEIGHT)")
        self.assertLess(use_rail, smart_rail)
        self.assertLess(smart_rail, search_spacer)


if __name__ == "__main__":
    unittest.main()

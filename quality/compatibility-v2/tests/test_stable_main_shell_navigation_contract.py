from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"


class StableMainShellNavigationContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_destination_change_does_not_rekey_the_main_shell(self) -> None:
        app = self.read(HULK_APP)

        self.assertIn("key(catalogNavigationMemory) {", app)
        self.assertNotIn("key(catalogNavigationMemory, state.destination)", app)

    def test_profile_memory_remains_the_shell_reset_boundary(self) -> None:
        app = self.read(HULK_APP)
        key_start = app.index("key(catalogNavigationMemory) {")
        shell_start = app.index("MainShellScreen(", key_start)

        self.assertLess(key_start, shell_start)
        self.assertIn("catalogNavigationMemory: ProfileCatalogNavigationMemory", app)

    def test_shell_delegates_catalog_context_to_profile_owned_navigation(self) -> None:
        shell = self.read(MAIN_SHELL)

        self.assertNotIn("val queryMemory = remember", shell)
        self.assertNotIn("val categoryMemory = remember", shell)
        self.assertNotIn("rememberingSelectDestination", shell)
        self.assertGreaterEqual(shell.count("onSelect = onSelectDestination"), 2)
        self.assertIn("onSelectDestination = onSelectDestination", shell)

    def test_shared_category_underlap_preserves_shell_geometry(self) -> None:
        source = self.read(MAIN_SHELL)
        shell = self.section(
            source,
            "fun MainShellScreen(",
            "private fun CinematicNavigationRail(",
        )
        rail = self.section(
            source,
            "private fun CinematicNavigationRail(",
            "private fun NavigationItem(",
        )
        catalog = self.section(
            source,
            "private fun ReorderableCatalogCategoryBar(",
            "private fun CatalogInteractionHints(",
        )
        live = source[source.index("private fun ReorderableLiveCategoryBar(") :]

        self.assertIn("Row(Modifier.fillMaxSize())", shell)
        self.assertIn("Modifier.weight(1f).fillMaxHeight()", shell)
        self.assertIn(".zIndex(1f)", rail)
        for category_bar in (catalog, live):
            self.assertIn("rememberCategorySidebarUnderlap", category_bar)
            self.assertIn(".extendCategoryViewportTowardStart", category_bar)
            self.assertIn("start = sidebarUnderlap.startContentPaddingDp.dp", category_bar)
        self.assertEqual(
            2,
            catalog.count(".extendCategoryViewportTowardStart")
            + live.count(".extendCategoryViewportTowardStart"),
        )


if __name__ == "__main__":
    unittest.main()

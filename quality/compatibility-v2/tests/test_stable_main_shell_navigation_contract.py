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


if __name__ == "__main__":
    unittest.main()

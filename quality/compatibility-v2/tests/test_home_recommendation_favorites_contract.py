from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
PROFILE_AWARE_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileAwareHulkApp.kt"


class HomeRecommendationFavoritesContractTest(unittest.TestCase):
    def test_hulk_app_passes_current_favorites_to_home(self) -> None:
        source = HULK_APP.read_text(encoding="utf-8")

        self.assertNotIn("homeRecommendationFavorites", source)
        self.assertNotIn("MainDestination.HOME -> state.copy(favorites =", source)

    def test_home_recommendation_input_and_identity_contracts_remain_explicit(self) -> None:
        source = MAIN_SHELL.read_text(encoding="utf-8")

        self.assertIn("favorites = state.favorites", source)
        self.assertIn('itemsIndexed(content, key = { _, item -> "${item.type}:${item.id}" })', source)

    def test_profile_navigation_memory_remains_profile_owned(self) -> None:
        source = PROFILE_AWARE_APP.read_text(encoding="utf-8")

        self.assertIn(
            "navigationMemoryByProfile.getOrPut(activeProfileId) { NavigationMemoryStore() }",
            source,
        )
        self.assertIn("navigationMemoryByProfile.clear()", source)


if __name__ == "__main__":
    unittest.main()

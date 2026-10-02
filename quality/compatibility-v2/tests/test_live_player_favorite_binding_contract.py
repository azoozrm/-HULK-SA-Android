from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_APP = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt"
PLAYER_PRO = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerProEpisodeNavigation.kt"
PLAYER_SCREEN = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"


class LivePlayerFavoriteBindingContractTest(unittest.TestCase):
    """The Live HUD favorite state must react to the observed favorites snapshot.

    `HulkViewModel.isFavorite` reads `MutableStateFlow.value`, which is not a Compose snapshot
    read, and the bound reference passed down from the recomposing caller can keep the player
    subtree skipped. The observed `state.favorites` snapshot must therefore be an explicit input
    through HulkApp -> PlayerProScreen -> PlayerScreen, where it keys the derived HUD control.
    """

    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def test_hulk_app_passes_the_observed_favorites_snapshot(self) -> None:
        app = self.read(HULK_APP)
        self.assertIn("PlayerProScreen(", app)
        self.assertIn("favoriteKeys = state.favorites", app)

    def test_player_pro_forwards_the_snapshot_to_the_player(self) -> None:
        pro = self.read(PLAYER_PRO)
        self.assertIn("favoriteKeys: Set<String>,", pro)
        self.assertIn("favoriteKeys = favoriteKeys,", pro)

    def test_player_screen_keys_the_live_hud_favorite_on_the_snapshot(self) -> None:
        player = self.read(PLAYER_SCREEN)
        self.assertIn("favoriteKeys: Set<String> = emptySet(),", player)
        self.assertIn("remember(liveFavoriteChannel, favoriteKeys)", player)
        self.assertIn("livePlayerFavoriteControl(", player)

    def test_no_second_favorite_shadow_store_or_polling_is_introduced(self) -> None:
        for path in (HULK_APP, PLAYER_PRO, PLAYER_SCREEN):
            text = self.read(path)
            self.assertNotIn("favoriteOverrides", text)


if __name__ == "__main__":
    unittest.main()

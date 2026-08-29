from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
HULK_VIEW_MODEL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/HulkViewModel.kt"


class LoginLazyTvPlatformContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = HULK_VIEW_MODEL.read_text(encoding="utf-8")

    @classmethod
    def section(cls, start: str, end: str) -> str:
        start_index = cls.source.index(start)
        return cls.source[start_index : cls.source.index(end, start_index)]

    def test_view_model_construction_defers_tv_platform_integration(self) -> None:
        declaration = self.section(
            "private val tvPlatformIntegration = TvPlatformIntegrationProvider {",
            "private val initialCachedOperations",
        )

        self.assertIn("TvPlatformIntegration(application)", declaration)
        self.assertNotIn(
            "private val tvPlatformIntegration = TvPlatformIntegration(application)",
            self.source,
        )
        self.assertEqual(1, self.source.count("TvPlatformIntegration(application)"))

    def test_unauthenticated_login_clear_path_does_not_force_initialization(self) -> None:
        clear_path = self.section(
            "private fun clearTvPlatformPrograms(",
            "private fun tvLandscapeArtworkSnapshot(",
        )

        self.assertIn("tvPlatformIntegration.getIfInitialized()", clear_path)
        self.assertIn(
            "?: if (session != null) tvPlatformIntegration.get() else return",
            clear_path,
        )

    def test_authenticated_platform_entry_points_use_the_stable_provider(self) -> None:
        profile_ready = self.section(
            "fun setTvPlatformProfileReady(ready: Boolean)",
            "fun beginTvPlatformProfileSwitch()",
        )
        sync = self.section(
            "private fun scheduleTvPlatformSync(immediate: Boolean)",
            "private fun beginTvPlatformProfileTransition(",
        )

        self.assertIn("tvPlatformIntegration.get().activeProfileScope()", profile_ready)
        self.assertIn("tvPlatformIntegration.get().activeProfileScope()", sync)
        self.assertIn("tvPlatformIntegration.get().syncActiveProfile(", sync)


if __name__ == "__main__":
    unittest.main()

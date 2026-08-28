from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
CATALOG_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"
DERIVED_MODELS = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/CatalogScreenEntryModels.kt"


class CatalogScreenEntryPerformanceContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_poster_catalog_uses_profile_owned_derived_model(self) -> None:
        source = self.read(MAIN_SHELL)
        poster = self.section(
            source,
            "private fun PosterCatalogScreen(",
            "private fun LiveCatalogScreen(",
        )

        self.assertIn("CatalogScreenModelInput(", poster)
        self.assertIn("navigationMemory.catalogModel(modelInput)", poster)
        self.assertIn("produceState(", poster)
        self.assertNotIn("remember(catalog) { newest(", poster)
        self.assertNotIn("ordered.filter", poster)

        content_grid = self.section(
            source,
            "private fun ContentGrid(",
            "private fun HistoryGrid(",
        )
        self.assertIn("preparedContentKeys", content_grid)
        self.assertIn("contentKeyIndex[remembered.itemKey]", content_grid)
        self.assertNotIn("LaunchedEffect(content.map", content_grid)

    def test_home_recommendations_are_not_built_in_composition(self) -> None:
        source = self.read(MAIN_SHELL)
        home = self.section(
            source,
            "private fun CinemaHomeScreen(",
            "private fun RenewalBanner(",
        )

        self.assertIn("HomeContentModelInput(", home)
        self.assertIn("navigationMemory.homeModel(homeInput)", home)
        self.assertIn("produceState(", home)
        self.assertNotIn("buildSmartHomeRecommendations(", home)
        self.assertNotIn("navigationMemory.homeContent(state)", home)

    def test_catalog_category_bar_does_not_build_unused_artwork_index(self) -> None:
        source = self.read(MAIN_SHELL)
        category_bar = self.section(
            source,
            "private fun ReorderableCatalogCategoryBar(",
            "private fun CatalogInteractionHints(",
        )

        self.assertNotIn("artworkByCategory", category_bar)
        self.assertNotIn("groupBy(ContentItem::categoryId)", category_bar)

    def test_tv_grid_consumes_precomputed_keys_without_focus_map(self) -> None:
        source = self.read(CATALOG_GRID)

        self.assertIn("contentKeys: List<String>", source)
        self.assertIn("contentKeyIndex[remembered.itemKey]", source)
        self.assertIn("List(contentKeys.size) { FocusRequester() }", source)
        self.assertNotIn("content.map {", source)
        self.assertNotIn("contentKeys.indexOf(", source)
        self.assertNotIn("contentKeys.associateWith", source)

    def test_heavy_models_are_built_on_worker_dispatcher(self) -> None:
        source = self.read(DERIVED_MODELS)

        self.assertIn("Dispatchers.Default", source)
        self.assertGreaterEqual(source.count("withContext(dispatcher)"), 2)
        self.assertIn("deriveCatalogScreenModel", source)
        self.assertIn("deriveHomeContentModel", source)


if __name__ == "__main__":
    unittest.main()

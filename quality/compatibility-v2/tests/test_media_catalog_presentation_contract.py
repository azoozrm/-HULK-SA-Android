from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
MAIN_SHELL = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
TV_GRID = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/TvCatalogGrid.kt"
MEDIA_PRESENTATION = (
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/components/MediaCatalogPresentation.kt"
)
OUT_OF_SCOPE_CONSUMERS = (
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/HomeScreen.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProScreens.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/DetailsProTvPolishScreens.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/SeriesDetailsScreenV2.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/screens/KidsMobileDetailsScreens.kt",
    REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui/ProfileSmartSearchLayer.kt",
)


class MediaCatalogPresentationContractTest(unittest.TestCase):
    @staticmethod
    def read(path: Path) -> str:
        return path.read_text(encoding="utf-8")

    @staticmethod
    def section(source: str, start: str, end: str) -> str:
        start_index = source.index(start)
        return source[start_index : source.index(end, start_index)]

    def test_titles_live_below_artwork_without_overlay_scrim(self) -> None:
        media = self.read(MEDIA_PRESENTATION)

        self.assertIn("internal fun MediaCatalogCard(", media)
        self.assertIn("text = item.name", media)
        self.assertIn("maxLines = 2", media)
        self.assertIn("minLines = 2", media)
        self.assertIn("TextOverflow.Ellipsis", media)
        self.assertNotIn("Brush.verticalGradient", media)
        self.assertNotIn("align(Alignment.BottomStart)", media)
        self.assertNotIn("align(Alignment.BottomEnd)", media)
        artwork_route = media.index("MediaArtworkKind.POSTER -> MediaPosterArtwork(")
        title_route = media.index("text = item.name")
        self.assertLess(artwork_route, title_route)

    def test_movie_metadata_is_movie_specific(self) -> None:
        media = self.read(MEDIA_PRESENTATION)

        self.assertIn(
            "ContentType.MOVIE -> listOfNotNull(\n"
            "            formatMediaRating(item.rating),\n"
            "            formatMediaDuration(metadata.durationMs),\n"
            "        )",
            media,
        )
        self.assertIn("internal fun formatMediaDuration(", media)

    def test_series_metadata_is_series_specific(self) -> None:
        media = self.read(MEDIA_PRESENTATION)

        self.assertIn(
            "ContentType.SERIES -> listOfNotNull(\n"
            "            formatMediaRating(item.rating),\n"
            "            formatMediaSeasonCount(metadata.seasonCount),\n"
            "        )",
            media,
        )
        self.assertIn("internal fun formatMediaSeasonCount(", media)
        series_branch = self.section(
            media,
            "ContentType.SERIES -> listOfNotNull(",
            "ContentType.LIVE -> listOfNotNull(",
        )
        self.assertNotIn("formatMediaDuration", series_branch)

    def test_live_artwork_uses_a_contained_landscape_stage(self) -> None:
        media = self.read(MEDIA_PRESENTATION)

        self.assertIn("MediaArtworkKind.LIVE_LOGO", media)
        self.assertIn("aspectRatio(16f / 9f)", media)
        self.assertIn("contentScale = ContentScale.Fit", media)
        self.assertIn("contentScale = ContentScale.Crop", media)

    def test_focus_uses_a_static_gold_outline_without_scaling(self) -> None:
        media = self.read(MEDIA_PRESENTATION)

        self.assertIn("internal const val MEDIA_CARD_FOCUS_BORDER_WIDTH_DP = 3f", media)
        self.assertIn(
            "borderWidthDp = if (showFocused) MEDIA_CARD_FOCUS_BORDER_WIDTH_DP else 0f",
            media,
        )
        self.assertIn("scale = 1f", media)
        self.assertIn("focusStyle.borderWidthDp.dp", media)
        self.assertIn("colors.goldBright", media)
        self.assertNotIn("animateFloatAsState", media)
        self.assertNotIn("graphicsLayer", media)

    def test_media_cards_are_not_pushed_into_out_of_scope_screens(self) -> None:
        for path in OUT_OF_SCOPE_CONSUMERS:
            self.assertNotIn("MediaCatalogCard", self.read(path), str(path))
            self.assertNotIn("rememberMediaCardMetadata", self.read(path), str(path))

    def test_tv_and_my_list_share_the_media_family(self) -> None:
        grid = self.read(TV_GRID)
        shell = self.read(MAIN_SHELL)

        self.assertIn("MediaCatalogCard(", grid)
        self.assertIn("rememberMediaCardMetadata(item)", grid)
        self.assertNotIn("SeriesPosterCard(", grid)
        self.assertNotIn("CompactPosterCard(", grid)

        content_grid = self.section(shell, "private fun ContentGrid(", "private fun HistoryGrid(")
        self.assertIn("val mediaCatalog = destination != MainDestination.SEARCH", content_grid)
        self.assertIn("MediaCatalogCard(", content_grid)
        self.assertIn("rememberMediaCardMetadata(item)", content_grid)
        self.assertIn("UniversalPosterCard(", content_grid)
        self.assertIn("contentKeyIndex[remembered.itemKey]", content_grid)

    def test_catalog_density_stays_adaptive_with_readable_cards(self) -> None:
        grid = self.read(TV_GRID)
        shell = self.read(MAIN_SHELL)
        content_grid = self.section(shell, "private fun ContentGrid(", "private fun HistoryGrid(")

        for text in (grid, content_grid):
            self.assertIn("tvCatalogColumnCellWidth(", text)
            self.assertIn("tvCatalogTargetColumns(", text)
        self.assertIn("GridCells.Adaptive(minCellWidth)", content_grid)
        self.assertIn("isTv -> 132.dp", content_grid)
        self.assertIn("else -> 105.dp", content_grid)
        self.assertNotIn("1920.dp", grid)
        self.assertNotIn("1080.dp", grid)

    def test_movies_series_my_list_behavior_owners_remain_intact(self) -> None:
        shell = self.read(MAIN_SHELL)
        poster = self.section(shell, "private fun PosterCatalogScreen(", "internal fun resolveLivePreview(")
        favorites = self.section(shell, "private fun FavoritesScreen(", "private fun UnifiedSearchScreen(")
        content_grid = self.section(shell, "private fun ContentGrid(", "private fun HistoryGrid(")

        self.assertIn("TvCatalogGrid(", poster)
        self.assertIn("ReorderableCatalogCategoryBar(", poster)
        self.assertIn("selectCategoryAndEnterContent", poster)
        self.assertIn(
            "ContentGrid(content, isTv, MainDestination.FAVORITES, navigationMemory",
            favorites,
        )
        self.assertIn("if (isTv && content.isEmpty())", favorites)
        self.assertIn("navigationMemory.save(destination, targetKey, targetIndex)", content_grid)
        self.assertIn("preparedContentKeys", content_grid)

    def test_catalog_filters_stay_distinct_from_content_focus(self) -> None:
        shell = self.read(MAIN_SHELL)
        catalog_bar = self.section(
            shell,
            "private fun ReorderableCatalogCategoryBar(",
            "private fun CatalogInteractionHints(",
        )
        chip = self.section(shell, "private fun LiveCategoryChip(", "private fun LiveInteractionHints(")

        self.assertIn("mediaFilter = true", catalog_bar)
        self.assertIn("focused -> colors.goldBright", chip)
        self.assertIn("selected -> colors.gold", chip)
        self.assertIn("focused || selected", chip)


if __name__ == "__main__":
    unittest.main()

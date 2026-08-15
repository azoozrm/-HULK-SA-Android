package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode

class DetailsProPolicyTest {
    @Test
    fun tvMetricsStayAdaptiveAcross720To4kLogicalWidths() {
        val compact = detailsProMetrics(screenWidthDp = 960, screenHeightDp = 540, isTv = true)
        val large = detailsProMetrics(screenWidthDp = 1920, screenHeightDp = 1080, isTv = true)

        assertTrue(compact.heroHeightDp in 340..620)
        assertTrue(large.heroHeightDp in 340..620)
        assertTrue(compact.horizontalPaddingDp < large.horizontalPaddingDp)
        assertEquals(3, compact.episodeColumns)
        assertEquals(5, large.episodeColumns)
    }

    @Test
    fun phoneAndTabletEpisodeColumnsScaleWithoutSingleDeviceAssumptions() {
        assertEquals(2, detailsProEpisodeColumns(360, isTv = false))
        assertEquals(3, detailsProEpisodeColumns(800, isTv = false))
        assertEquals(4, detailsProEpisodeColumns(1200, isTv = false))
    }

    @Test
    fun relatedRankingPrefersSharedGenreAndCategory() {
        val source = item(1, "Source", category = "9", genre = "Action, Crime", rating = "7.0")
        val sameGenreAndCategory = item(2, "Best", category = "9", genre = "Crime / Action", rating = "6.0")
        val categoryOnly = item(3, "Category", category = "9", genre = "Drama", rating = "9.0")
        val unrelated = item(4, "Other", category = "3", genre = "Comedy", rating = "10.0")

        val ranked = detailsProRelatedItems(
            source = source,
            candidates = listOf(unrelated, categoryOnly, sameGenreAndCategory, source),
        )

        assertEquals(listOf(2, 3, 4), ranked.map(ContentItem::id))
    }

    @Test
    fun adjacentEpisodeUsesCurrentOrderedEpisodeWithoutCrossingEdges() {
        val episodes = listOf(
            episode(10, season = 1, number = 1),
            episode(11, season = 1, number = 2),
            episode(20, season = 2, number = 1),
        )

        assertEquals(10, detailsProAdjacentEpisode(episodes, 11, -1)?.id)
        assertEquals(20, detailsProAdjacentEpisode(episodes, 11, 1)?.id)
        assertNull(detailsProAdjacentEpisode(episodes, 10, -1))
        assertNull(detailsProAdjacentEpisode(episodes, 20, 1))
    }

    private fun item(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = "2024",
        containerExtension = "mp4",
        genre = genre,
    )

    private fun episode(id: Int, season: Int, number: Int) = Episode(
        id = id,
        title = "Episode $number",
        season = season,
        episodeNumber = number,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )
}

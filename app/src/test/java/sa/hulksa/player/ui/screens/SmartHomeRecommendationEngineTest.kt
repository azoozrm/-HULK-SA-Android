package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class SmartHomeRecommendationEngineTest {
    @Test
    fun recentWatchSignalsRankRelatedUnwatchedContent() {
        val watched = movie(1, "Watched Action", "action", "Action, Thriller", "7.2", 400L)
        val related = movie(2, "Related Action", "action", "Action", "8.4", 300L)
        val other = movie(3, "Unrelated Drama", "drama", "Drama", "9.8", 500L)

        val result = buildSmartHomeRecommendations(
            movies = listOf(other, watched, related),
            series = emptyList(),
            live = emptyList(),
            history = listOf(historyForMovie(watched, updatedAt = 10_000L)),
            favorites = emptySet(),
        )

        assertFalse(result.becauseYouWatched.any { it.id == watched.id })
        assertEquals(related.id, result.becauseYouWatched.first().id)
    }

    @Test
    fun continueWatchingKeepsOnlyLatestEpisodePerSeries() {
        val older = seriesHistory(
            key = "SERIES:101",
            episodeStreamId = 101,
            parentSeriesId = 50,
            episodeNumber = 1,
            updatedAt = 1_000L,
        )
        val newer = seriesHistory(
            key = "SERIES:102",
            episodeStreamId = 102,
            parentSeriesId = 50,
            episodeNumber = 2,
            updatedAt = 2_000L,
        )

        val result = buildSmartHomeRecommendations(
            movies = emptyList(),
            series = listOf(series(50, "Series A", "drama", "Drama", "8.0", 100L)),
            live = emptyList(),
            history = listOf(older, newer),
            favorites = emptySet(),
        )

        assertEquals(1, result.continueWatching.size)
        assertEquals(newer.key, result.continueWatching.single().key)
    }

    @Test
    fun parentSeriesIdSeedsSeriesRecommendationsReliably() {
        val watchedSeries = series(50, "Series A", "crime", "Crime, Drama", "8.0", 500L)
        val relatedSeries = series(51, "Series B", "crime", "Crime", "8.5", 400L)
        val unrelatedSeries = series(52, "Series C", "comedy", "Comedy", "9.5", 600L)
        val history = seriesHistory(
            key = "SERIES:900",
            episodeStreamId = 900,
            parentSeriesId = watchedSeries.id,
            episodeNumber = 7,
            updatedAt = 9_000L,
        )

        val result = buildSmartHomeRecommendations(
            movies = emptyList(),
            series = listOf(unrelatedSeries, watchedSeries, relatedSeries),
            live = emptyList(),
            history = listOf(history),
            favorites = emptySet(),
        )

        assertEquals(relatedSeries.id, result.becauseYouWatched.first().id)
        assertFalse(result.becauseYouWatched.any { it.id == watchedSeries.id })
    }

    @Test
    fun coldStartFallsBackToStrongCatalogSuggestionsAndHeroCandidates() {
        val movies = listOf(
            movie(1, "Movie A", "action", "Action", "9.2", 500L, artwork = true),
            movie(2, "Movie B", "drama", "Drama", "8.9", 400L, artwork = true),
        )
        val series = listOf(
            series(3, "Series A", "crime", "Crime", "9.1", 600L, artwork = true),
            series(4, "Series B", "comedy", "Comedy", "8.8", 300L, artwork = true),
        )

        val result = buildSmartHomeRecommendations(
            movies = movies,
            series = series,
            live = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertTrue(result.becauseYouWatched.isEmpty())
        assertTrue(result.suggested.isNotEmpty())
        assertTrue(result.suggested.map(ContentItem::type).toSet().size >= 2)
        assertTrue(result.featuredCandidates.isNotEmpty())
    }

    @Test
    fun favoriteSignalsRemainDeterministicAndInvalidateTowardMatchingCategory() {
        val content = listOf(
            movie(1, "Action Favorite", "action", "Action", "7.0", 300L),
            movie(2, "Action Match", "action", "Action", "9.0", 200L),
            movie(3, "Drama", "drama", "Drama", "10.0", 100L),
        )
        val favorites = setOf("MOVIE:1")

        val first = buildSmartHomeRecommendations(content, emptyList(), emptyList(), emptyList(), favorites)
        val second = buildSmartHomeRecommendations(content, emptyList(), emptyList(), emptyList(), favorites)

        assertEquals(first, second)
        assertEquals(setOf(1, 2), first.becauseYouWatched.map(ContentItem::id).toSet())
    }

    @Test
    fun refreshRotationChangesSuggestedContentWithoutLosingDeterminismPerSeed() {
        val content = (1..80).map { id ->
            movie(
                id = id,
                name = "Movie $id",
                category = "category-${id % 8}",
                genre = "Genre ${id % 5}",
                rating = String.format(java.util.Locale.US, "%.1f", 6.0 + (id % 30) / 10.0),
                added = (1_000 - id).toLong(),
            )
        }

        val first = buildSmartHomeRecommendations(content, emptyList(), emptyList(), emptyList(), emptySet(), rotationSeed = 0)
        val refreshed = buildSmartHomeRecommendations(content, emptyList(), emptyList(), emptyList(), emptySet(), rotationSeed = 1)
        val refreshedAgain = buildSmartHomeRecommendations(content, emptyList(), emptyList(), emptyList(), emptySet(), rotationSeed = 1)

        assertNotEquals(first.suggested.map(ContentItem::id), refreshed.suggested.map(ContentItem::id))
        assertEquals(refreshed.suggested, refreshedAgain.suggested)
    }

    @Test
    fun becauseYouWatchedAvoidsSingleCategorySaturationWhenAlternativesExist() {
        val favorite = movie(1, "Sports Favorite", "sports", "Sports", "8.0", 1_000L)
        val sports = (2..12).map { id -> movie(id, "Sports $id", "sports", "Sports", "9.0", (1_000 - id).toLong()) }
        val alternatives = (20..27).map { id ->
            movie(id, "Alternative $id", "alt-$id", "Drama", "8.5", (900 - id).toLong())
        }

        val result = buildSmartHomeRecommendations(
            movies = listOf(favorite) + sports + alternatives,
            series = emptyList(),
            live = emptyList(),
            history = emptyList(),
            favorites = setOf("MOVIE:1"),
        )

        assertTrue(result.becauseYouWatched.take(10).count { it.categoryId == "sports" } <= 4)
    }

    @Test
    fun heroPrefersCinematicMetadataOverBareHighRatedArtwork() {
        val bare = movie(
            id = 1,
            name = "Bare Event",
            category = "event",
            genre = "",
            rating = "10.0",
            added = 1_000L,
            artwork = true,
            plot = null,
        )
        val cinematic = movie(
            id = 2,
            name = "Cinematic Movie",
            category = "drama",
            genre = "Drama",
            rating = "8.0",
            added = 900L,
            artwork = true,
            plot = "A complete cinematic story with useful metadata.",
        )

        val result = buildSmartHomeRecommendations(
            movies = listOf(bare, cinematic),
            series = emptyList(),
            live = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertEquals(cinematic.id, result.featuredCandidates.first().id)
    }

    private fun movie(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
        artwork: Boolean = false,
        plot: String? = null,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.MOVIE,
        posterUrl = if (artwork) "https://example.com/$id.jpg" else null,
        rating = rating,
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
        plot = plot,
        genre = genre,
        backdropUrl = if (artwork) "https://example.com/$id-backdrop.jpg" else null,
    )

    private fun series(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
        artwork: Boolean = false,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.SERIES,
        posterUrl = if (artwork) "https://example.com/$id.jpg" else null,
        rating = rating,
        year = "2026",
        containerExtension = null,
        addedAtEpochSeconds = added,
        genre = genre,
        backdropUrl = if (artwork) "https://example.com/$id-backdrop.jpg" else null,
    )

    private fun historyForMovie(item: ContentItem, updatedAt: Long): HistoryEntry = HistoryEntry(
        key = "MOVIE:${item.id}",
        title = item.name,
        posterUrl = item.posterUrl,
        streamKind = "movie",
        streamId = item.id,
        extension = "mp4",
        isLive = false,
        positionMs = 60_000L,
        durationMs = 600_000L,
        updatedAtEpochMs = updatedAt,
    )

    private fun seriesHistory(
        key: String,
        episodeStreamId: Int,
        parentSeriesId: Int,
        episodeNumber: Int,
        updatedAt: Long,
    ): HistoryEntry = HistoryEntry(
        key = key,
        title = "Series A · S1 E$episodeNumber",
        posterUrl = null,
        streamKind = "series",
        streamId = episodeStreamId,
        extension = "mp4",
        isLive = false,
        positionMs = 90_000L,
        durationMs = 600_000L,
        updatedAtEpochMs = updatedAt,
        seriesTitle = "Series A",
        season = 1,
        episodeNumber = episodeNumber,
        parentContentId = parentSeriesId,
    )
}

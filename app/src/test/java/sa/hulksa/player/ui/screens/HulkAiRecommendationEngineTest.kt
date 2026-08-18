package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class HulkAiRecommendationEngineTest {
    @Test
    fun recentHistoryBoostsRelatedUnwatchedContent() {
        val watched = movie(1, "Watched Action", "action", "Action, Thriller", "7.0", 100L)
        val related = movie(2, "Related Action", "action-2", "Action", "7.5", 90L)
        val unrelated = movie(3, "Rated Drama", "drama", "Drama", "9.9", 120L)

        val result = buildHulkAiSuggestions(
            movies = listOf(unrelated, watched, related),
            series = emptyList(),
            history = listOf(movieHistory(watched, 10_000L)),
            favorites = emptySet(),
            limit = 3,
        )

        assertEquals(related.id, result.suggestions.first().item.id)
        assertFalse(result.suggestions.any { it.item.id == watched.id })
        assertTrue(result.hasProfileSignals)
        assertTrue(result.suggestions.first().evidence.any { it.type == HulkAiEvidenceType.RECENT_GENRE })
    }

    @Test
    fun favoritesAreSignalsButNotRepeatedInDiscovery() {
        val favorite = series(10, "Favorite Crime", "crime", "Crime, Drama", "8.0", 100L)
        val related = series(11, "Related Crime", "crime-2", "Crime", "8.1", 90L)
        val unrelated = series(12, "Comedy", "comedy", "Comedy", "9.8", 120L)

        val result = buildHulkAiSuggestions(
            movies = emptyList(),
            series = listOf(unrelated, favorite, related),
            history = emptyList(),
            favorites = setOf("SERIES:${favorite.id}"),
            limit = 3,
        )

        assertEquals(related.id, result.suggestions.first().item.id)
        assertFalse(result.suggestions.any { it.item.id == favorite.id })
        assertTrue(result.suggestions.first().evidence.any { it.type == HulkAiEvidenceType.FAVORITE_GENRE })
    }

    @Test
    fun seriesEpisodeHistoryUsesParentContentId() {
        val watchedSeries = series(20, "Series A", "mystery", "Mystery", "7.2", 80L)
        val related = series(21, "Series B", "mystery-2", "Mystery", "7.7", 70L)
        val other = series(22, "Series C", "family", "Family", "9.9", 100L)

        val result = buildHulkAiSuggestions(
            movies = emptyList(),
            series = listOf(other, watchedSeries, related),
            history = listOf(seriesHistory(parentSeriesId = watchedSeries.id, seriesTitle = watchedSeries.name)),
            favorites = emptySet(),
            limit = 3,
        )

        assertEquals(related.id, result.suggestions.first().item.id)
        assertFalse(result.suggestions.any { it.item.id == watchedSeries.id })
    }

    @Test
    fun liveHistoryDoesNotCreateVodPreferenceSignals() {
        val action = movie(30, "Action", "action", "Action", "7.0", 10L)
        val drama = movie(31, "Drama", "drama", "Drama", "9.5", 20L)
        val liveHistory = HistoryEntry(
            key = "LIVE:999",
            title = "Action Live",
            posterUrl = null,
            streamKind = "live",
            streamId = 999,
            extension = "ts",
            isLive = true,
            positionMs = 0L,
            durationMs = 0L,
            updatedAtEpochMs = 99_000L,
        )

        val result = buildHulkAiSuggestions(
            movies = listOf(action, drama),
            series = emptyList(),
            history = listOf(liveHistory),
            favorites = emptySet(),
            limit = 2,
        )

        assertFalse(result.hasProfileSignals)
        assertEquals(drama.id, result.suggestions.first().item.id)
    }

    @Test
    fun preferredGenresComeOnlyFromRealCatalogMetadata() {
        val favorite = movie(40, "Real Genre", "real", "Science Fiction, Adventure", "8.2", 50L)
        val candidate = movie(41, "Candidate", "other", "Science Fiction", "8.0", 40L)

        val result = buildHulkAiSuggestions(
            movies = listOf(favorite, candidate),
            series = emptyList(),
            history = emptyList(),
            favorites = setOf("MOVIE:${favorite.id}"),
            limit = 2,
        )

        assertTrue("science fiction" in result.preferredGenres)
        assertTrue("adventure" in result.preferredGenres)
        assertFalse(result.preferredGenres.any { it !in setOf("science fiction", "adventure") })
    }

    @Test
    fun coldStartIsDeterministicAndUsesRealRatingFreshness() {
        val olderHigh = movie(50, "Older High", "a", "Drama", "9.4", 100L)
        val newerMid = movie(51, "Newer Mid", "b", "Comedy", "8.0", 500L)
        val lower = movie(52, "Lower", "c", "Action", "6.0", 400L)

        val first = buildHulkAiSuggestions(
            movies = listOf(lower, newerMid, olderHigh),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
            limit = 3,
        )
        val second = buildHulkAiSuggestions(
            movies = listOf(lower, newerMid, olderHigh),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
            limit = 3,
        )

        assertEquals(first.suggestions.map { it.item.id }, second.suggestions.map { it.item.id })
        assertFalse(first.hasProfileSignals)
        assertTrue(first.suggestions.first().evidence.any {
            it.type == HulkAiEvidenceType.HIGH_RATING || it.type == HulkAiEvidenceType.FRESH_CONTENT
        })
    }

    @Test
    fun movieAndSeriesAreBothRepresentedWhenCatalogSupportsThem() {
        val movies = (1..8).map { id -> movie(100 + id, "Movie $id", "movie-$id", "Action", "9.5", (1000 - id).toLong()) }
        val series = (1..4).map { id -> series(200 + id, "Series $id", "series-$id", "Drama", "7.5", (500 - id).toLong()) }

        val result = buildHulkAiSuggestions(
            movies = movies,
            series = series,
            history = emptyList(),
            favorites = emptySet(),
            limit = 8,
        )

        assertTrue(result.suggestions.count { it.item.type == ContentType.MOVIE } >= 2)
        assertTrue(result.suggestions.count { it.item.type == ContentType.SERIES } >= 2)
    }

    private fun movie(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
    ) = item(id, name, category, genre, rating, added, ContentType.MOVIE)

    private fun series(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
    ) = item(id, name, category, genre, rating, added, ContentType.SERIES)

    private fun item(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
        type: ContentType,
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = type,
        posterUrl = "https://example.com/$id.jpg",
        rating = rating,
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
        plot = "Real provider plot for $name",
        genre = genre,
        backdropUrl = "https://example.com/$id-backdrop.jpg",
    )

    private fun movieHistory(item: ContentItem, updatedAt: Long) = HistoryEntry(
        key = "MOVIE:${item.id}",
        title = item.name,
        posterUrl = item.posterUrl,
        streamKind = "movie",
        streamId = item.id,
        extension = "mp4",
        isLive = false,
        positionMs = 1_000L,
        durationMs = 10_000L,
        updatedAtEpochMs = updatedAt,
    )

    private fun seriesHistory(parentSeriesId: Int, seriesTitle: String) = HistoryEntry(
        key = "SERIES_EPISODE:900",
        title = "$seriesTitle Episode 4",
        posterUrl = null,
        streamKind = "series",
        streamId = 900,
        extension = "mp4",
        isLive = false,
        positionMs = 1_000L,
        durationMs = 10_000L,
        updatedAtEpochMs = 50_000L,
        seriesTitle = seriesTitle,
        season = 1,
        episodeNumber = 4,
        parentContentId = parentSeriesId,
    )
}

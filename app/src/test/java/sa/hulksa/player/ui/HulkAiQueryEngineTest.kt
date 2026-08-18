package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class HulkAiQueryEngineTest {
    @Test
    fun arabicRecommendationTriggersOpenHulkAi() {
        assertTrue(isHulkAiRequest("رشح لي فيلم اكشن"))
        assertTrue(isHulkAiRequest("ابغى مسلسل جريمة"))
        assertTrue(isHulkAiRequest("HULK AI movie"))
        assertFalse(isHulkAiRequest("the matrix"))
    }

    @Test
    fun explicitMovieActionUsesOnlyRealMatchingMoviesWhenAvailable() {
        val actionMovie = movie(1, "Action One", "Action", "8.2", "2025", 300L)
        val dramaMovie = movie(2, "Drama One", "Drama", "9.7", "2025", 400L)
        val actionSeries = series(3, "Action Series", "Action", "9.9", "2025", 500L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "رشح لي فيلم اكشن",
            movies = listOf(dramaMovie, actionMovie),
            series = listOf(actionSeries),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertTrue(result.exactConstraintMatch)
        assertTrue(result.suggestions.isNotEmpty())
        assertTrue(result.suggestions.all { it.item.type == ContentType.MOVIE })
        assertTrue(result.suggestions.all { it.item.genre?.contains("Action") == true })
    }

    @Test
    fun seriesRequestKeepsSeriesOnly() {
        val movie = movie(1, "Crime Movie", "Crime", "9.8", "2025", 300L)
        val series = series(2, "Crime Series", "Crime", "7.8", "2024", 200L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "اقترح لي مسلسل جريمة",
            movies = listOf(movie),
            series = listOf(series),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertEquals(1, result.suggestions.size)
        assertEquals(ContentType.SERIES, result.suggestions.single().item.type)
    }

    @Test
    fun highRatingIntentUsesRealRating() {
        val high = movie(1, "High", "Drama", "9.2", "2023", 100L)
        val low = movie(2, "Low", "Drama", "6.1", "2025", 400L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "ابي فيلم دراما عالي التقييم",
            movies = listOf(low, high),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertEquals(high.id, result.suggestions.first().item.id)
        assertTrue(
            result.suggestions.first().signals.any {
                it.type == HulkAiQuerySignalType.HIGH_RATING && it.label.contains("9.2")
            },
        )
    }

    @Test
    fun recentIntentUsesRealAddedTimestamp() {
        val old = movie(1, "Old", "Action", "9.5", "2021", 100L)
        val fresh = movie(2, "Fresh", "Action", "7.5", "2025", 1_000L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "رشح لي فيلم اكشن جديد",
            movies = listOf(old, fresh),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertEquals(fresh.id, result.suggestions.first().item.id)
        assertTrue(result.suggestions.first().signals.any { it.type == HulkAiQuerySignalType.RECENT })
    }

    @Test
    fun yearConstraintUsesActualCatalogYear() {
        val y2024 = movie(1, "A", "Drama", "8.0", "2024", 200L)
        val y2025 = movie(2, "B", "Drama", "7.0", "2025", 100L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "ابي فيلم دراما 2025",
            movies = listOf(y2024, y2025),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertTrue(result.exactConstraintMatch)
        assertTrue(result.suggestions.all { it.item.year == "2025" })
    }

    @Test
    fun missingConstraintReportsApproximateFallbackInsteadOfInventingMatch() {
        val drama = movie(1, "Drama", "Drama", "8.0", "2024", 100L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "رشح لي فيلم رعب",
            movies = listOf(drama),
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertFalse(result.exactConstraintMatch)
        assertEquals(drama.id, result.suggestions.single().item.id)
        assertFalse(result.suggestions.single().signals.any { it.type == HulkAiQuerySignalType.GENRE })
    }

    @Test
    fun profileSignalsBreakTieAndWatchedItemIsNotRepeatedWhenAlternativeExists() {
        val watched = movie(1, "Watched Crime", "Crime", "8.0", "2024", 100L)
        val related = movie(2, "Related Crime", "Crime", "8.0", "2024", 100L)
        val other = movie(3, "Other Drama", "Drama", "8.0", "2024", 100L)

        val result = buildHulkAiQuerySuggestions(
            rawQuery = "رشح لي فيلم",
            movies = listOf(watched, other, related),
            series = emptyList(),
            history = listOf(historyFor(watched)),
            favorites = emptySet(),
        )

        assertFalse(result.suggestions.any { it.item.id == watched.id })
        assertEquals(related.id, result.suggestions.first().item.id)
        assertTrue(result.hasProfileSignals)
        assertTrue(result.suggestions.first().signals.any { it.type == HulkAiQuerySignalType.PROFILE })
    }

    private fun movie(
        id: Int,
        name: String,
        genre: String,
        rating: String,
        year: String,
        added: Long,
    ): ContentItem = content(id, name, ContentType.MOVIE, genre, rating, year, added)

    private fun series(
        id: Int,
        name: String,
        genre: String,
        rating: String,
        year: String,
        added: Long,
    ): ContentItem = content(id, name, ContentType.SERIES, genre, rating, year, added)

    private fun content(
        id: Int,
        name: String,
        type: ContentType,
        genre: String,
        rating: String,
        year: String,
        added: Long,
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = genre.lowercase(),
        type = type,
        posterUrl = "https://example.invalid/$id.jpg",
        rating = rating,
        year = year,
        containerExtension = if (type == ContentType.MOVIE) "mp4" else null,
        addedAtEpochSeconds = added,
        plot = "$name $genre",
        genre = genre,
        backdropUrl = null,
    )

    private fun historyFor(item: ContentItem) = HistoryEntry(
        key = "MOVIE:${item.id}",
        title = item.name,
        posterUrl = item.posterUrl,
        streamKind = "movie",
        streamId = item.id,
        extension = "mp4",
        isLive = false,
        positionMs = 15_000L,
        durationMs = 100_000L,
        updatedAtEpochMs = 10_000L,
    )
}

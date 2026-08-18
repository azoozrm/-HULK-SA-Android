package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class HulkAiResponsiveEngineTest {
    @Test
    fun largeCatalogStillReturnsRealRecommendations() {
        val movies = (1..2_000).map { id ->
            item(
                id = id,
                type = ContentType.MOVIE,
                name = "Movie $id",
                genre = if (id % 2 == 0) "Action" else "Drama",
                rating = if (id % 7 == 0) "9.0" else "7.0",
                added = id.toLong(),
            )
        }
        val series = (2_001..4_000).map { id ->
            item(
                id = id,
                type = ContentType.SERIES,
                name = "Series $id",
                genre = if (id % 2 == 0) "Crime" else "Drama",
                rating = "8.0",
                added = id.toLong(),
            )
        }

        val result = buildResponsiveHulkAiQuerySuggestions(
            rawQuery = "رشح",
            movies = movies,
            series = series,
            history = emptyList(),
            favorites = emptySet(),
            limit = 12,
        )

        assertEquals(12, result.suggestions.size)
        assertTrue(result.suggestions.all { it.item in movies || it.item in series })
    }

    @Test
    fun explicitMovieActionIntentSurvivesBounding() {
        val movies = (1..2_000).map { id ->
            item(
                id = id,
                type = ContentType.MOVIE,
                name = "Movie $id",
                genre = if (id > 1_900) "Action" else "Drama",
                rating = "7.5",
                added = id.toLong(),
            )
        }

        val result = buildResponsiveHulkAiQuerySuggestions(
            rawQuery = "رشح لي فيلم اكشن جديد",
            movies = movies,
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
            limit = 10,
        )

        assertTrue(result.suggestions.isNotEmpty())
        assertTrue(result.suggestions.all { it.item.type == ContentType.MOVIE })
        assertTrue(result.suggestions.all { it.item.genre == "Action" })
    }

    private fun item(
        id: Int,
        type: ContentType,
        name: String,
        genre: String,
        rating: String,
        added: Long,
    ) = ContentItem(
        id = id,
        type = type,
        name = name,
        categoryId = "cat-${id % 10}",
        posterUrl = "https://example.com/$id.jpg",
        backdropUrl = null,
        rating = rating,
        year = "2025",
        genre = genre,
        plot = "Real provider metadata",
        addedAtEpochSeconds = added,
    )
}
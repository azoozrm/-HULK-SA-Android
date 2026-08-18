package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class CinemaHeroTypeBalanceRegressionTest {
    @Test
    fun posterOnlyMoviesRemainEligibleWhenSeriesOwnBackdropArtwork() {
        val movies = (1..8).map { id ->
            ContentItem(
                id = id,
                name = "Movie $id",
                categoryId = "movie-$id",
                type = ContentType.MOVIE,
                posterUrl = "https://example.com/movie-$id.jpg",
                rating = "8.0",
                year = "2026",
                containerExtension = "mp4",
                addedAtEpochSeconds = (2_000 - id).toLong(),
                plot = "Movie $id has real provider metadata but only poster artwork.",
                genre = "Action",
                backdropUrl = null,
            )
        }
        val series = (101..108).map { id ->
            ContentItem(
                id = id,
                name = "Series $id",
                categoryId = "series-$id",
                type = ContentType.SERIES,
                posterUrl = "https://example.com/series-$id.jpg",
                rating = "8.5",
                year = "2026",
                containerExtension = null,
                addedAtEpochSeconds = (3_000 - id).toLong(),
                plot = "Series $id has real provider metadata and cinematic backdrop artwork.",
                genre = "Drama",
                backdropUrl = "https://example.com/series-$id-backdrop.jpg",
            )
        }

        val result = buildSmartHomeRecommendations(
            movies = movies,
            series = series,
            live = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
            rotationSeed = 0,
        )

        assertEquals(8, result.featuredCandidates.size)
        assertEquals(4, result.featuredCandidates.count { it.type == ContentType.MOVIE })
        assertEquals(4, result.featuredCandidates.count { it.type == ContentType.SERIES })
    }
}

package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class SmartCollectionsEngineTest {
    @Test
    fun recentHistoryRanksRelatedUnwatchedMovieFirst() {
        val watched = movie(1, "Watched Action", "action", "Action", "7.0", 100L)
        val related = movie(2, "Related Action", "action", "Action", "8.0", 90L)
        val unrelated = movie(3, "Unrelated Drama", "drama", "Drama", "10.0", 200L)

        val collections = buildSmartCollections(
            movies = listOf(unrelated, watched, related),
            series = emptyList(),
            history = listOf(movieHistory(watched, 5_000L)),
            favorites = emptySet(),
        )

        val smartMovies = collections.first { it.key == "smart-movies" }
        assertEquals(related.id, smartMovies.items.first().id)
        assertFalse(smartMovies.items.any { it.id == watched.id })
        assertEquals(SmartCollectionSource.PROFILE, smartMovies.source)
    }

    @Test
    fun favoriteSignalPersonalizesSeriesWithoutRepeatingFavorite() {
        val favorite = series(10, "Crime Favorite", "crime", "Crime, Drama", "7.0", 100L)
        val related = series(11, "Crime Match", "crime", "Crime", "8.1", 90L)
        val unrelated = series(12, "Comedy", "comedy", "Comedy", "9.9", 200L)

        val collections = buildSmartCollections(
            movies = emptyList(),
            series = listOf(unrelated, favorite, related),
            history = emptyList(),
            favorites = setOf("SERIES:10"),
        )

        val smartSeries = collections.first { it.key == "smart-series" }
        assertEquals(related.id, smartSeries.items.first().id)
        assertFalse(smartSeries.items.any { it.id == favorite.id })
    }

    @Test
    fun genreCollectionsUseOnlyGenresPresentInCatalog() {
        val movies = (1..5).map { id ->
            movie(id, "Action $id", "action-$id", "Action", "8.$id", id.toLong())
        } + (10..14).map { id ->
            movie(id, "Drama $id", "drama-$id", "Drama", "7.5", id.toLong())
        }

        val collections = buildSmartCollections(
            movies = movies,
            series = emptyList(),
            history = emptyList(),
            favorites = emptySet(),
            maxGenreCollections = 2,
        )

        val genreCollections = collections.filter { it.source == SmartCollectionSource.GENRE }
        assertTrue(genreCollections.isNotEmpty())
        assertTrue(genreCollections.all { collection ->
            collection.items.all { item ->
                val titleGenre = collection.title.removePrefix("مختارات ").lowercase()
                item.genre.orEmpty().lowercase().contains(titleGenre)
            }
        })
        assertFalse(genreCollections.any { it.title.contains("Horror", ignoreCase = true) })
    }

    @Test
    fun coldStartProducesCuratedMovieAndSeriesCollections() {
        val collections = buildSmartCollections(
            movies = listOf(movie(1, "Movie", "m", "Action", "8.8", 20L)),
            series = listOf(series(2, "Series", "s", "Drama", "9.0", 30L)),
            history = emptyList(),
            favorites = emptySet(),
        )

        assertEquals(SmartCollectionSource.CURATED, collections.first { it.key == "smart-movies" }.source)
        assertEquals(SmartCollectionSource.CURATED, collections.first { it.key == "smart-series" }.source)
    }

    @Test
    fun liveHistoryAndLiveItemsNeverEnterVodCollections() {
        val movie = movie(1, "Movie", "m", "Action", "8.0", 10L)
        val liveHistory = HistoryEntry(
            key = "LIVE:77",
            title = "Live",
            posterUrl = null,
            streamKind = "live",
            streamId = 77,
            extension = "ts",
            isLive = true,
            positionMs = 0L,
            durationMs = 0L,
            updatedAtEpochMs = 99L,
        )

        val collections = buildSmartCollections(
            movies = listOf(movie),
            series = emptyList(),
            history = listOf(liveHistory),
            favorites = setOf("LIVE:77"),
        )

        assertTrue(collections.flatMap(SmartCollection::items).all { it.type != ContentType.LIVE })
    }

    @Test
    fun seriesEpisodeHistoryUsesParentSeriesAsProfileSignal() {
        val watchedSeries = series(50, "Series A", "crime", "Crime", "7.5", 100L)
        val relatedSeries = series(51, "Series B", "crime", "Crime", "8.0", 90L)
        val unrelatedSeries = series(52, "Series C", "comedy", "Comedy", "9.8", 200L)
        val history = HistoryEntry(
            key = "SERIES:900",
            title = "Series A · S1 E7",
            posterUrl = null,
            streamKind = "series",
            streamId = 900,
            extension = "mp4",
            isLive = false,
            positionMs = 90_000L,
            durationMs = 600_000L,
            updatedAtEpochMs = 10_000L,
            seriesTitle = "Series A",
            season = 1,
            episodeNumber = 7,
            parentContentId = watchedSeries.id,
        )

        val collections = buildSmartCollections(
            movies = emptyList(),
            series = listOf(unrelatedSeries, watchedSeries, relatedSeries),
            history = listOf(history),
            favorites = emptySet(),
        )

        val smartSeries = collections.first { it.key == "smart-series" }
        assertEquals(relatedSeries.id, smartSeries.items.first().id)
        assertFalse(smartSeries.items.any { it.id == watchedSeries.id })
    }

    @Test
    fun outputIsDeterministicForSameProfileSignals() {
        val content = (1..20).map { id ->
            movie(
                id = id,
                name = "Movie $id",
                category = "category-${id % 4}",
                genre = if (id % 2 == 0) "Action" else "Drama",
                rating = "8.${id % 10}",
                added = id.toLong(),
            )
        }
        val favorites = setOf("MOVIE:2")

        val first = buildSmartCollections(content, emptyList(), emptyList(), favorites)
        val second = buildSmartCollections(content, emptyList(), emptyList(), favorites)

        assertEquals(first, second)
    }

    private fun movie(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
        genre = genre,
    )

    private fun series(
        id: Int,
        name: String,
        category: String,
        genre: String,
        rating: String,
        added: Long,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.SERIES,
        posterUrl = null,
        rating = rating,
        year = "2026",
        containerExtension = null,
        addedAtEpochSeconds = added,
        genre = genre,
    )

    private fun movieHistory(item: ContentItem, updatedAt: Long): HistoryEntry = HistoryEntry(
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
}

package sa.hulksa.player.ui.screens

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination
import sa.hulksa.player.catalogPublicationAllowed
import sa.hulksa.player.catalogRequestShouldStart
import sa.hulksa.player.catalogTypesForDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.projectCatalogsForDestination

class HomeLiveCatalogLoadingTest {
    @Test
    fun `cold authenticated Home requests Live with Movie and Series`() {
        assertEquals(
            setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            catalogTypesForDestination(MainDestination.HOME),
        )
    }

    @Test
    fun `Home projects the loaded Live catalog while keeping compact Movie and Series`() {
        val loadedMovie = Catalog(emptyList(), listOf(movie(1), movie(2)))
        val loadedSeries = Catalog(emptyList(), listOf(series(3)))
        val loadedLive = Catalog(emptyList(), listOf(live(4), live(5)))
        val compactMovie = Catalog(emptyList(), listOf(movie(2)))

        val projected = projectCatalogsForDestination(
            destination = MainDestination.HOME,
            current = emptyMap(),
            loaded = mapOf(
                ContentType.MOVIE to loadedMovie,
                ContentType.SERIES to loadedSeries,
                ContentType.LIVE to loadedLive,
            ),
            home = mapOf(ContentType.MOVIE to compactMovie),
        )

        assertSame(compactMovie, projected[ContentType.MOVIE])
        assertSame(loadedSeries, projected[ContentType.SERIES])
        assertSame(loadedLive, projected[ContentType.LIVE])
    }

    @Test
    fun `live-only failure keeps usable Movie and Series Home content`() {
        val loadedMovie = Catalog(emptyList(), listOf(movie(1)))
        val loadedSeries = Catalog(emptyList(), listOf(series(2)))

        val projected = projectCatalogsForDestination(
            destination = MainDestination.HOME,
            current = emptyMap(),
            loaded = mapOf(
                ContentType.MOVIE to loadedMovie,
                ContentType.SERIES to loadedSeries,
            ),
            home = emptyMap(),
        )

        assertSame(loadedMovie, projected[ContentType.MOVIE])
        assertSame(loadedSeries, projected[ContentType.SERIES])
        assertFalse(ContentType.LIVE in projected)
    }

    @Test
    fun `first frame Home carries already loaded Live without waiting for exact derivation`() {
        val liveCatalog = Catalog(emptyList(), listOf(live(7), live(8)))
        val input = HomeContentModelInput(
            movieCatalog = null,
            seriesCatalog = null,
            liveCatalog = liveCatalog,
            history = emptyList(),
            favorites = emptySet(),
        )

        val firstFrame = initialHomePresentation(input)

        assertEquals(listOf(7, 8), firstFrame.live.map(ContentItem::id))
    }

    @Test
    fun `late Live arrival presents channels and keeps the current hero identity`() = runBlocking {
        val movieCatalog = Catalog(emptyList(), listOf(movie(10), movie(11), movie(12)))
        val seriesCatalog = Catalog(emptyList(), listOf(series(20), series(21), series(22)))
        val liveCatalog = Catalog(emptyList(), listOf(live(30), live(31)))
        val history = listOf(
            HistoryEntry(
                key = "LIVE:30",
                title = "Channel 30",
                posterUrl = null,
                streamKind = "live",
                streamId = 30,
                extension = "m3u8",
                isLive = true,
                positionMs = 0L,
                durationMs = 0L,
                updatedAtEpochMs = 5_000L,
            ),
        )
        val store = CatalogScreenEntryModelStore()

        val before = store.home(
            HomeContentModelInput(
                movieCatalog = movieCatalog,
                seriesCatalog = seriesCatalog,
                liveCatalog = null,
                history = history,
                favorites = emptySet(),
            ),
        ).model
        val heroBefore = before.featuredCandidates.firstOrNull()
        val after = store.home(
            HomeContentModelInput(
                movieCatalog = movieCatalog,
                seriesCatalog = seriesCatalog,
                liveCatalog = liveCatalog,
                history = history,
                favorites = emptySet(),
            ),
        ).model

        assertEquals(listOf(30, 31), after.live.map(ContentItem::id))
        assertEquals(listOf(30, 31), after.personalizedLive.map(ContentItem::id))
        assertEquals("LIVE:30", after.lastLive?.key)
        assertTrue(heroBefore != null)
        assertEquals(
            heroBefore?.let(::homeHeroIdentity),
            resolvePresentedHomeHero(
                currentHeroIdentity = heroBefore?.let(::homeHeroIdentity),
                featuredCandidates = after.featuredCandidates,
                movies = after.movies,
                series = after.series,
            )?.let(::homeHeroIdentity),
        )
    }

    @Test
    fun `overlapping startup Home and refresh requests cannot duplicate an in-flight load`() {
        assertTrue(catalogRequestShouldStart(hasLoadedCatalog = false, force = false, inFlight = false))
        assertFalse(catalogRequestShouldStart(hasLoadedCatalog = true, force = false, inFlight = false))
        assertTrue(catalogRequestShouldStart(hasLoadedCatalog = true, force = true, inFlight = false))
        assertFalse(catalogRequestShouldStart(hasLoadedCatalog = false, force = false, inFlight = true))
        assertFalse(catalogRequestShouldStart(hasLoadedCatalog = true, force = true, inFlight = true))
    }

    @Test
    fun `late catalog result cannot publish after session or generation invalidation`() {
        assertTrue(
            catalogPublicationAllowed(
                requestGeneration = 7L,
                currentGeneration = 7L,
                sessionCurrent = true,
            ),
        )
        assertFalse(
            catalogPublicationAllowed(
                requestGeneration = 7L,
                currentGeneration = 8L,
                sessionCurrent = true,
            ),
        )
        assertFalse(
            catalogPublicationAllowed(
                requestGeneration = 7L,
                currentGeneration = 7L,
                sessionCurrent = false,
            ),
        )
    }

    private fun movie(id: Int): ContentItem = item(id, ContentType.MOVIE, "Movie $id")

    private fun series(id: Int): ContentItem = item(id, ContentType.SERIES, "Series $id")

    private fun live(id: Int): ContentItem = item(id, ContentType.LIVE, "Channel $id")

    private fun item(id: Int, type: ContentType, name: String): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = "category-$id",
        type = type,
        posterUrl = "https://artwork.example.test/$type-$id.jpg",
        rating = "8.0",
        year = null,
        containerExtension = if (type == ContentType.LIVE) "m3u8" else "mp4",
        genre = "Drama",
        addedAtEpochSeconds = 1_000L + id,
    )
}

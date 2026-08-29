package sa.hulksa.player.ui.screens

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class ProfileHomeFirstFramePresentationTest {
    @Test
    fun `fresh profile exposes cheap home presentation before exact derivation`() {
        val movieCatalog = Catalog(
            categories = emptyList(),
            items = listOf(movie(1, "Account Movie")),
        )
        val input = HomeContentModelInput(
            movieCatalog = movieCatalog,
            seriesCatalog = null,
            liveCatalog = null,
            history = listOf(history(91, "Old Profile History")),
            favorites = setOf("MOVIE:91"),
        )
        val store = CatalogScreenEntryModelStore(
            homeBuilder = { error("exact Home derivation must not run for first-frame presentation") },
        )

        assertNull(store.cachedHome(input))
        val fallback = checkNotNull(store.lastGoodHome())

        assertSame(input, fallback.input)
        assertSame(movieCatalog.items, fallback.model.movies)
        assertTrue(fallback.model.series.isEmpty())
        assertTrue(fallback.model.live.isEmpty())
        assertTrue(fallback.model.continueWatching.isEmpty())
        assertNull(fallback.model.lastLive)
        assertTrue(fallback.model.becauseYouWatched.isEmpty())
        assertTrue(fallback.model.suggested.isEmpty())
        assertTrue(fallback.model.personalizedLive.isEmpty())
        assertTrue(fallback.model.popularMovies.isEmpty())
        assertTrue(fallback.model.popularSeries.isEmpty())
        assertTrue(fallback.model.featuredCandidates.isEmpty())
    }

    @Test
    fun `exact home model replaces first-frame presentation`() = runBlocking {
        val input = HomeContentModelInput(
            movieCatalog = Catalog(emptyList(), listOf(movie(2, "Exact Movie"))),
            seriesCatalog = null,
            liveCatalog = null,
            history = emptyList(),
            favorites = emptySet(),
        )
        val store = CatalogScreenEntryModelStore()

        assertNull(store.cachedHome(input))
        val fallback = checkNotNull(store.lastGoodHome())
        val exact = store.home(input)

        assertNotSame(fallback, exact)
        assertSame(exact, store.cachedHome(input))
        assertSame(exact, store.lastGoodHome())
        assertEquals(listOf(2), exact.model.movies.map(ContentItem::id))
    }

    @Test
    fun `fresh profile stores never share first-frame home presentation`() {
        val profileAInput = HomeContentModelInput(
            movieCatalog = Catalog(emptyList(), listOf(movie(10, "Profile A Catalog"))),
            seriesCatalog = null,
            liveCatalog = null,
            history = listOf(history(10, "Profile A History")),
            favorites = setOf("MOVIE:10"),
        )
        val profileBInput = HomeContentModelInput(
            movieCatalog = Catalog(emptyList(), listOf(movie(20, "Profile B Catalog"))),
            seriesCatalog = null,
            liveCatalog = null,
            history = listOf(history(20, "Profile B History")),
            favorites = setOf("MOVIE:20"),
        )
        val profileAStore = CatalogScreenEntryModelStore()
        val profileBStore = CatalogScreenEntryModelStore()

        profileAStore.cachedHome(profileAInput)
        profileBStore.cachedHome(profileBInput)
        val profileA = checkNotNull(profileAStore.lastGoodHome())
        val profileB = checkNotNull(profileBStore.lastGoodHome())

        assertNotSame(profileA, profileB)
        assertSame(profileAInput, profileA.input)
        assertSame(profileBInput, profileB.input)
        assertEquals(listOf(10), profileA.model.movies.map(ContentItem::id))
        assertEquals(listOf(20), profileB.model.movies.map(ContentItem::id))
        assertTrue(profileA.model.continueWatching.isEmpty())
        assertTrue(profileB.model.continueWatching.isEmpty())
    }

    private fun movie(id: Int, name: String): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = "all",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = "8.0",
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = id.toLong(),
        plot = "Plot",
        genre = "Drama",
    )

    private fun history(id: Int, title: String): HistoryEntry = HistoryEntry(
        key = "movie:$id",
        title = title,
        posterUrl = null,
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
        isLive = false,
        positionMs = 10L,
        durationMs = 100L,
        updatedAtEpochMs = 1_000L,
    )
}

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
    fun `fresh profile exposes non-null cheap empty home presentation`() {
        val input = homeInput()
        val store = CatalogScreenEntryModelStore(
            homeBuilder = { error("exact Home derivation must not run for first-frame presentation") },
        )

        assertNull(store.cachedHome(input))
        val fallback = checkNotNull(store.lastGoodHome())

        assertSame(input, fallback.input)
        assertTrue(fallback.model.movies.isEmpty())
        assertTrue(fallback.model.series.isEmpty())
        assertTrue(fallback.model.live.isEmpty())
        assertNull(heroId(fallback.model))
        assertPersonalizedSectionsEmpty(fallback.model)
    }

    @Test
    fun `movie catalog arrival replaces empty fallback immediately before exact derivation`() = runBlocking {
        val emptyInput = homeInput()
        val movieCatalog = Catalog(emptyList(), listOf(movie(2, "Movie Hero")))
        val movieInput = homeInput(movieCatalog = movieCatalog)
        val store = CatalogScreenEntryModelStore()

        assertNull(store.cachedHome(emptyInput))
        val emptyFallback = checkNotNull(store.lastGoodHome())
        assertNull(heroId(emptyFallback.model))

        assertNull(store.cachedHome(movieInput))
        val movieFallback = checkNotNull(store.lastGoodHome())

        assertNotSame(emptyFallback, movieFallback)
        assertSame(movieInput, movieFallback.input)
        assertSame(movieCatalog.items, movieFallback.model.movies)
        assertEquals(2, heroId(movieFallback.model))
        assertPersonalizedSectionsEmpty(movieFallback.model)

        val exact = store.home(movieInput)
        assertNotSame(movieFallback, exact)
        assertSame(exact, store.cachedHome(movieInput))
        assertSame(exact, store.lastGoodHome())
        assertEquals(2, heroId(exact.model))
    }

    @Test
    fun `series catalog arrival exposes hero source without manual refresh`() {
        val emptyInput = homeInput()
        val seriesCatalog = Catalog(emptyList(), listOf(series(3, "Series Hero")))
        val seriesInput = homeInput(seriesCatalog = seriesCatalog)
        val store = CatalogScreenEntryModelStore(
            homeBuilder = { error("exact Home derivation must not be required for Hero availability") },
        )

        store.cachedHome(emptyInput)
        val emptyFallback = checkNotNull(store.lastGoodHome())
        assertNull(heroId(emptyFallback.model))

        store.cachedHome(seriesInput)
        val seriesFallback = checkNotNull(store.lastGoodHome())

        assertNotSame(emptyFallback, seriesFallback)
        assertSame(seriesInput, seriesFallback.input)
        assertSame(seriesCatalog.items, seriesFallback.model.series)
        assertEquals(3, heroId(seriesFallback.model))
        assertPersonalizedSectionsEmpty(seriesFallback.model)
    }

    @Test
    fun `stale exact model from previous input never masks current catalog fallback`() = runBlocking {
        val emptyInput = homeInput()
        val exactEmpty = CatalogScreenEntryModelStore().run {
            cachedHome(emptyInput)
            home(emptyInput)
        }
        val movieCatalog = Catalog(emptyList(), listOf(movie(4, "Current Movie")))
        val movieInput = homeInput(movieCatalog = movieCatalog)
        val store = CatalogScreenEntryModelStore()

        store.cachedHome(emptyInput)
        val storedEmpty = store.home(emptyInput)
        assertEquals(exactEmpty.model, storedEmpty.model)

        assertNull(store.cachedHome(movieInput))
        val current = checkNotNull(store.lastGoodHome())

        assertNotSame(storedEmpty, current)
        assertSame(movieInput, current.input)
        assertEquals(4, heroId(current.model))
    }

    @Test
    fun `stale exact completion cannot replace newer current input presentation`() = runBlocking {
        val inputA = homeInput()
        val movieCatalog = Catalog(emptyList(), listOf(movie(5, "Input B Movie")))
        val inputB = homeInput(movieCatalog = movieCatalog)
        lateinit var store: CatalogScreenEntryModelStore
        store = CatalogScreenEntryModelStore(
            homeBuilder = { input ->
                if (input == inputA) {
                    store.cachedHome(inputB)
                }
                initialHomePresentation(input)
            },
        )

        val staleA = store.home(inputA)
        val currentB = checkNotNull(store.lastGoodHome())

        assertSame(inputA, staleA.input)
        assertSame(inputB, currentB.input)
        assertEquals(5, heroId(currentB.model))
        assertNull(store.cachedHome(inputB))

        val exactB = store.home(inputB)
        assertSame(inputB, exactB.input)
        assertSame(exactB, store.cachedHome(inputB))
        assertSame(exactB, store.lastGoodHome())
        assertEquals(5, heroId(exactB.model))
    }

    @Test
    fun `fresh profile stores never share catalog or personalized presentation`() = runBlocking {
        val profileAInput = homeInput(
            movieCatalog = Catalog(emptyList(), listOf(movie(10, "Profile A Catalog"))),
            history = listOf(history(10, "Profile A History")),
            favorites = setOf("MOVIE:10"),
        )
        val profileBInput = homeInput(
            movieCatalog = Catalog(emptyList(), listOf(movie(20, "Profile B Catalog"))),
            history = listOf(history(20, "Profile B History")),
            favorites = setOf("MOVIE:20"),
        )
        val profileAStore = CatalogScreenEntryModelStore()
        val profileBStore = CatalogScreenEntryModelStore()

        profileAStore.cachedHome(profileAInput)
        val profileAExact = profileAStore.home(profileAInput)
        profileBStore.cachedHome(profileBInput)
        val profileBFallback = checkNotNull(profileBStore.lastGoodHome())

        assertNotSame(profileAExact, profileBFallback)
        assertSame(profileAInput, profileAExact.input)
        assertSame(profileBInput, profileBFallback.input)
        assertEquals(10, heroId(profileAExact.model))
        assertEquals(20, heroId(profileBFallback.model))
        assertTrue(profileBFallback.model.movies.none { it.id == 10 })
        assertPersonalizedSectionsEmpty(profileBFallback.model)
    }

    private fun homeInput(
        movieCatalog: Catalog? = null,
        seriesCatalog: Catalog? = null,
        history: List<HistoryEntry> = emptyList(),
        favorites: Set<String> = emptySet(),
    ): HomeContentModelInput = HomeContentModelInput(
        movieCatalog = movieCatalog,
        seriesCatalog = seriesCatalog,
        liveCatalog = null,
        history = history,
        favorites = favorites,
    )

    private fun heroId(model: HomeContentSnapshot): Int? =
        model.featuredCandidates.firstOrNull()?.id
            ?: model.movies.firstOrNull()?.id
            ?: model.series.firstOrNull()?.id

    private fun assertPersonalizedSectionsEmpty(model: HomeContentSnapshot) {
        assertTrue(model.continueWatching.isEmpty())
        assertNull(model.lastLive)
        assertTrue(model.becauseYouWatched.isEmpty())
        assertTrue(model.suggested.isEmpty())
        assertTrue(model.personalizedLive.isEmpty())
        assertTrue(model.popularMovies.isEmpty())
        assertTrue(model.popularSeries.isEmpty())
        assertTrue(model.featuredCandidates.isEmpty())
    }

    private fun movie(id: Int, name: String): ContentItem = content(id, name, ContentType.MOVIE)

    private fun series(id: Int, name: String): ContentItem = content(id, name, ContentType.SERIES)

    private fun content(id: Int, name: String, type: ContentType): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = "all",
        type = type,
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

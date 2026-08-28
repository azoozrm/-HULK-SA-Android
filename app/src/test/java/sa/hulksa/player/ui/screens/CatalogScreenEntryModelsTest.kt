package sa.hulksa.player.ui.screens

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

class CatalogScreenEntryModelsTest {
    @Test
    fun `derived catalog preserves newest category query and continue behavior`() {
        val oldestMatch = movie(1, "Alpha Old", "action", added = 100L)
        val wrongCategory = movie(2, "Alpha Drama", "drama", added = 400L)
        val newestMatch = movie(3, "Alpha New", "action", added = 300L)
        val input = movieInput(
            catalog = Catalog(emptyList(), listOf(oldestMatch, wrongCategory, newestMatch)),
            history = listOf(
                history("movie:1", "Alpha Resume", "movie", positionMs = 10L),
                history("movie:2", "Other Resume", "movie", positionMs = 10L),
                history("series:3", "Alpha Series", "series", positionMs = 10L),
                history("movie:4", "Alpha Finished", "movie", positionMs = 95L, durationMs = 100L),
            ),
            categoryId = "action",
            query = " alpha ",
        )

        val result = deriveCatalogScreenModel(input)

        assertEquals(listOf(3, 1), result.visible.map(ContentItem::id))
        assertEquals(listOf("MOVIE:3", "MOVIE:1"), result.contentKeys)
        assertEquals(mapOf("MOVIE:3" to 0, "MOVIE:1" to 1), result.contentKeyIndex)
        assertEquals(listOf("movie:1"), result.continueWatching.map(HistoryEntry::key))
    }

    @Test
    fun `newest ordering remains stable for equal timestamps`() {
        val first = movie(1, "First", "all", added = 200L)
        val second = movie(2, "Second", "all", added = 200L)
        val newest = movie(3, "Newest", "all", added = 300L)

        val result = deriveCatalogScreenModel(
            movieInput(Catalog(emptyList(), listOf(first, second, newest))),
        )

        assertEquals(listOf(3, 1, 2), result.visible.map(ContentItem::id))
    }

    @Test
    fun `series model keeps series keys filtering and resume history`() {
        val older = movie(10, "Older Series", "drama", 100L).copy(type = ContentType.SERIES)
        val newer = movie(11, "Newer Series", "drama", 200L).copy(type = ContentType.SERIES)
        val input = CatalogScreenModelInput(
            catalog = Catalog(emptyList(), listOf(older, newer)),
            history = listOf(history("series:11", "Newer Series", "series", positionMs = 20L)),
            favorites = CatalogFavoriteSnapshot(emptySet()),
            type = ContentType.SERIES,
            destination = MainDestination.SERIES,
            categoryId = "drama",
            query = "Newer",
        )

        val result = deriveCatalogScreenModel(input)

        assertEquals(listOf(11), result.visible.map(ContentItem::id))
        assertEquals(listOf("SERIES:11"), result.contentKeys)
        assertEquals(listOf("series:11"), result.continueWatching.map(HistoryEntry::key))
    }

    @Test
    fun `favorite category uses persisted state plus optimistic override`() {
        val first = movie(1, "First", "all", added = 100L)
        val second = movie(2, "Second", "all", added = 200L)
        val favorites = CatalogFavoriteSnapshot(
            persisted = setOf("MOVIE:1"),
            optimistic = mapOf("MOVIE:1" to false, "MOVIE:2" to true),
        )

        val result = deriveCatalogScreenModel(
            movieInput(
                catalog = Catalog(emptyList(), listOf(first, second)),
                favorites = favorites,
                categoryId = "__hulk_favorites__",
            ),
        )

        assertEquals(listOf(2), result.visible.map(ContentItem::id))
    }

    @Test
    fun `same input reuses derived instance without recalculation`() = runBlocking {
        val calculations = AtomicInteger(0)
        val store = CatalogScreenEntryModelStore(
            catalogBuilder = { input ->
                calculations.incrementAndGet()
                deriveCatalogScreenModel(input)
            },
        )
        val input = movieInput(Catalog(emptyList(), listOf(movie(1, "One", "all", 1L))))

        val first = store.catalog(input)
        val equivalentInput = movieInput(
            catalog = input.catalog,
            history = input.history,
            favorites = CatalogFavoriteSnapshot(input.favorites.persisted),
        )
        val second = store.catalog(equivalentInput)

        assertSame(first, second)
        assertEquals(1, calculations.get())
    }

    @Test
    fun `catalog identity change recalculates without stale items`() = runBlocking {
        val calculations = AtomicInteger(0)
        val store = CatalogScreenEntryModelStore(
            catalogBuilder = { input ->
                calculations.incrementAndGet()
                deriveCatalogScreenModel(input)
            },
        )
        val original = Catalog(emptyList(), listOf(movie(1, "Old", "all", 100L)))
        val refreshed = Catalog(emptyList(), listOf(movie(2, "New", "all", 200L)))

        val first = store.catalog(movieInput(original))
        val second = store.catalog(movieInput(refreshed))

        assertNotSame(first, second)
        assertEquals(listOf(2), second.model.visible.map(ContentItem::id))
        assertEquals(2, calculations.get())
    }

    @Test
    fun `category and query changes invalidate only the requested destination`() = runBlocking {
        val calculations = AtomicInteger(0)
        val store = CatalogScreenEntryModelStore(
            catalogBuilder = { input ->
                calculations.incrementAndGet()
                deriveCatalogScreenModel(input)
            },
        )
        val catalog = Catalog(
            emptyList(),
            listOf(
                movie(1, "Alpha", "action", 100L),
                movie(2, "Beta", "drama", 200L),
            ),
        )
        val action = movieInput(catalog, categoryId = "action")
        val dramaQuery = movieInput(catalog, categoryId = "drama", query = "Beta")

        assertEquals(listOf(1), store.catalog(action).model.visible.map(ContentItem::id))
        val changed = store.catalog(dramaQuery)
        assertEquals(listOf(2), changed.model.visible.map(ContentItem::id))
        assertSame(changed, store.catalog(dramaQuery))
        assertEquals(2, calculations.get())
    }

    @Test
    fun `profile stores and kids scoped catalogs remain isolated`() = runBlocking {
        val kidsCatalog = Catalog(emptyList(), listOf(movie(1, "Kids", "kids", 100L)))
        val adultCatalog = Catalog(emptyList(), listOf(movie(9, "Adult", "adult", 900L)))
        val profileAStore = CatalogScreenEntryModelStore()
        val profileBStore = CatalogScreenEntryModelStore()

        val kids = profileAStore.catalog(movieInput(kidsCatalog)).model
        val adult = profileBStore.catalog(movieInput(adultCatalog)).model

        assertEquals(listOf(1), kids.visible.map(ContentItem::id))
        assertEquals(listOf(9), adult.visible.map(ContentItem::id))
        assertTrue(kids.visible.none { it.id == 9 })
    }

    @Test
    fun `empty and nullable metadata catalogs do not crash`() {
        val empty = deriveCatalogScreenModel(movieInput(null, query = "anything"))
        val malformed = movie(
            id = 1,
            name = "Untitled",
            category = "unknown",
            added = null,
        ).copy(rating = null, year = null, plot = null, genre = null, nowPlaying = null)
        val nullable = deriveCatalogScreenModel(
            movieInput(Catalog(emptyList(), listOf(malformed)), query = "missing"),
        )

        assertTrue(empty.visible.isEmpty())
        assertTrue(empty.continueWatching.isEmpty())
        assertTrue(nullable.visible.isEmpty())
    }

    @Test
    fun `home model caches same inputs and invalidates on catalog revision`() = runBlocking {
        val calculations = AtomicInteger(0)
        val store = CatalogScreenEntryModelStore(
            homeBuilder = { input ->
                calculations.incrementAndGet()
                deriveHomeContentModel(input)
            },
        )
        val history = emptyList<HistoryEntry>()
        val favorites = emptySet<String>()
        val originalCatalog = Catalog(
            emptyList(),
            listOf(movie(1, "Older", "all", 100L), movie(2, "Newest", "all", 200L)),
        )
        val firstInput = HomeContentModelInput(originalCatalog, null, null, history, favorites)

        val first = store.home(firstInput)
        val same = store.home(HomeContentModelInput(originalCatalog, null, null, history, favorites))
        val refreshedCatalog = Catalog(emptyList(), listOf(movie(3, "Refreshed", "all", 300L)))
        val refreshed = store.home(HomeContentModelInput(refreshedCatalog, null, null, history, favorites))
        val legacyRecommendations = buildSmartHomeRecommendations(
            movies = newest(originalCatalog.items),
            series = emptyList(),
            live = emptyList(),
            history = history,
            favorites = favorites,
        )

        assertSame(first, same)
        assertEquals(listOf(2, 1), first.model.movies.map(ContentItem::id))
        assertEquals(legacyRecommendations.featuredCandidates, first.model.featuredCandidates)
        assertEquals(legacyRecommendations.suggested, first.model.suggested)
        assertEquals(listOf(3), refreshed.model.movies.map(ContentItem::id))
        assertEquals(2, calculations.get())
    }

    @Test
    fun `derivation runs on configured worker dispatcher`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "catalog-model-worker")
        }
        executor.asCoroutineDispatcher().use { dispatcher ->
            var calculationThread = ""
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                catalogBuilder = { input ->
                    calculationThread = Thread.currentThread().name
                    deriveCatalogScreenModel(input)
                },
            )

            runBlocking {
                store.catalog(movieInput(Catalog(emptyList(), listOf(movie(1, "One", "all", 1L)))))
            }

            assertEquals("catalog-model-worker", calculationThread)
        }
    }

    private fun movieInput(
        catalog: Catalog?,
        history: List<HistoryEntry> = emptyList(),
        favorites: CatalogFavoriteSnapshot = CatalogFavoriteSnapshot(emptySet()),
        categoryId: String? = null,
        query: String = "",
    ): CatalogScreenModelInput = CatalogScreenModelInput(
        catalog = catalog,
        history = history,
        favorites = favorites,
        type = ContentType.MOVIE,
        destination = MainDestination.MOVIES,
        categoryId = categoryId,
        query = query,
    )

    private fun movie(
        id: Int,
        name: String,
        category: String,
        added: Long?,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = "8.0",
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
        plot = "Plot for $name",
        genre = category,
    )

    private fun history(
        key: String,
        title: String,
        kind: String,
        positionMs: Long,
        durationMs: Long = 100L,
    ): HistoryEntry = HistoryEntry(
        key = key,
        title = title,
        posterUrl = null,
        streamKind = kind,
        streamId = key.substringAfter(':').toInt(),
        extension = "mp4",
        isLive = false,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = 1_000L,
    )
}

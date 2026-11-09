package sa.hulksa.player.ui.screens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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

        assertNull(store.lastGoodHome())
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
        assertSame(refreshed, store.lastGoodHome())
        assertSame(refreshed, store.cachedHome(HomeContentModelInput(refreshedCatalog, null, null, history, favorites)))
        assertEquals(listOf(2, 1), first.model.movies.map(ContentItem::id))
        assertEquals(legacyRecommendations.featuredCandidates, first.model.featuredCandidates)
        assertEquals(legacyRecommendations.suggested, first.model.suggested)
        assertEquals(listOf(3), refreshed.model.movies.map(ContentItem::id))
        assertEquals(2, calculations.get())
    }

    @Test
    fun `current home fallback replaces stale exact model until revalidation completes`() {
        val refreshedCatalog = Catalog(emptyList(), listOf(movie(2, "Refreshed", "all", 200L)))
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                homeBuilder = { input ->
                    if (input.movieCatalog === refreshedCatalog) {
                        refreshStarted.countDown()
                        check(releaseRefresh.await(5, TimeUnit.SECONDS))
                    }
                    deriveHomeContentModel(input)
                },
            )
            runBlocking {
                val firstInput = homeInput(Catalog(emptyList(), listOf(movie(1, "Initial", "all", 100L))))
                val first = store.home(firstInput)
                val refreshedInput = HomeContentModelInput(
                    movieCatalog = refreshedCatalog,
                    seriesCatalog = null,
                    liveCatalog = null,
                    history = listOf(history("movie:2", "Refreshed", "movie", positionMs = 20L)),
                    favorites = setOf("MOVIE:2"),
                )
                val refresh = async(dispatcher) { store.home(refreshedInput) }

                val startedInTime = refreshStarted.await(5, TimeUnit.SECONDS)
                try {
                    assertTrue(startedInTime)
                    val fallback = store.lastGoodHome()
                    assertNotSame(first, fallback)
                    assertSame(refreshedInput, fallback?.input)
                    assertNull(store.cachedHome(refreshedInput))
                    assertEquals(listOf(2), fallback?.model?.movies?.map(ContentItem::id))
                    assertTrue(fallback?.model?.series?.isEmpty() == true)
                    assertTrue(fallback?.model?.continueWatching?.isEmpty() == true)
                    assertNull(fallback?.model?.lastLive)
                    assertTrue(fallback?.model?.becauseYouWatched?.isEmpty() == true)
                    assertTrue(fallback?.model?.suggested?.isEmpty() == true)
                    assertTrue(fallback?.model?.personalizedLive?.isEmpty() == true)
                    assertTrue(fallback?.model?.popularMovies?.isEmpty() == true)
                    assertTrue(fallback?.model?.popularSeries?.isEmpty() == true)
                    assertTrue(fallback?.model?.featuredCandidates?.isEmpty() == true)
                } finally {
                    releaseRefresh.countDown()
                }

                val exact = refresh.await()
                assertSame(exact, store.lastGoodHome())
                assertSame(exact, store.cachedHome(refreshedInput))
                assertEquals(listOf(2), exact.model.movies.map(ContentItem::id))

                val seriesOnlyCatalog = Catalog(
                    emptyList(),
                    listOf(movie(7, "Current Series", "all", 700L).copy(type = ContentType.SERIES)),
                )
                val seriesOnlyInput = HomeContentModelInput(
                    movieCatalog = null,
                    seriesCatalog = seriesOnlyCatalog,
                    liveCatalog = null,
                    history = listOf(history("series:7", "Current Series", "series", positionMs = 20L)),
                    favorites = setOf("SERIES:7"),
                )
                assertNull(store.cachedHome(seriesOnlyInput))
                val seriesFallback = store.lastGoodHome()
                assertSame(seriesOnlyInput, seriesFallback?.input)
                assertTrue(seriesFallback?.model?.movies?.isEmpty() == true)
                assertEquals(listOf(7), seriesFallback?.model?.series?.map(ContentItem::id))
                assertTrue(seriesFallback?.model?.continueWatching?.isEmpty() == true)
                assertTrue(seriesFallback?.model?.featuredCandidates?.isEmpty() == true)
            }
        }
    }

    @Test
    fun `stale home result cannot replace a newer completed input`() {
        val staleCatalog = Catalog(emptyList(), listOf(movie(1, "Stale", "all", 100L)))
        val currentCatalog = Catalog(emptyList(), listOf(movie(2, "Current", "all", 200L)))
        val staleStarted = CountDownLatch(1)
        val releaseStale = CountDownLatch(1)
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                homeBuilder = { input ->
                    if (input.movieCatalog === staleCatalog) {
                        staleStarted.countDown()
                        check(releaseStale.await(5, TimeUnit.SECONDS))
                    }
                    deriveHomeContentModel(input)
                },
            )
            runBlocking {
                val stale = async(dispatcher) { store.home(homeInput(staleCatalog)) }
                val startedInTime = staleStarted.await(5, TimeUnit.SECONDS)
                assertTrue(startedInTime)

                val currentInput = homeInput(currentCatalog)
                val current = store.home(currentInput)
                releaseStale.countDown()
                stale.await()

                assertSame(current, store.lastGoodHome())
                assertSame(current, store.cachedHome(currentInput))
                assertEquals(listOf(2), store.lastGoodHome()?.model?.movies?.map(ContentItem::id))
            }
        }
    }

    @Test
    fun `profile account and kids stores never share last good home`() = runBlocking {
        val accountStore = CatalogScreenEntryModelStore()
        val profileStore = CatalogScreenEntryModelStore()
        val kidsStore = CatalogScreenEntryModelStore()
        val adult = accountStore.home(
            homeInput(Catalog(emptyList(), listOf(movie(9, "Adult", "adult", 900L)))),
        )

        assertSame(adult, accountStore.lastGoodHome())
        assertNull(profileStore.lastGoodHome())
        assertNull(kidsStore.lastGoodHome())

        val kids = kidsStore.home(
            homeInput(Catalog(emptyList(), listOf(movie(1, "Kids", "kids", 100L)))),
        )
        assertEquals(listOf(1), kids.model.movies.map(ContentItem::id))
        assertEquals(listOf(9), adult.model.movies.map(ContentItem::id))
        assertNotSame(adult, kidsStore.lastGoodHome())
        assertNull(profileStore.lastGoodHome())
    }

    @Test
    fun `movies and series keep independent last good models during revalidation`() {
        val refreshedMovies = Catalog(emptyList(), listOf(movie(2, "New Movie", "all", 200L)))
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                catalogBuilder = { input ->
                    if (input.catalog === refreshedMovies) {
                        refreshStarted.countDown()
                        check(releaseRefresh.await(5, TimeUnit.SECONDS))
                    }
                    deriveCatalogScreenModel(input)
                },
            )
            runBlocking {
                val firstMovies = store.catalog(
                    movieInput(Catalog(emptyList(), listOf(movie(1, "Old Movie", "all", 100L)))),
                )
                val seriesInput = seriesInput(
                    Catalog(
                        emptyList(),
                        listOf(movie(7, "Series", "all", 700L).copy(type = ContentType.SERIES)),
                    ),
                )
                val series = store.catalog(seriesInput)
                val refreshedInput = movieInput(refreshedMovies)
                val refresh = async(dispatcher) { store.catalog(refreshedInput) }

                val startedInTime = refreshStarted.await(5, TimeUnit.SECONDS)
                try {
                    assertTrue(startedInTime)
                    assertSame(firstMovies, store.lastGoodCatalog(MainDestination.MOVIES))
                    assertSame(series, store.lastGoodCatalog(MainDestination.SERIES))
                } finally {
                    releaseRefresh.countDown()
                }

                val exact = refresh.await()
                assertSame(exact, store.lastGoodCatalog(MainDestination.MOVIES))
                assertSame(series, store.lastGoodCatalog(MainDestination.SERIES))
                assertEquals(listOf(2), exact.model.visible.map(ContentItem::id))
            }
        }
    }

    @Test
    fun `catalog and first home derivation run on configured worker dispatcher`() {
        val workerThread = AtomicReference<Thread>()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "catalog-model-worker").also(workerThread::set)
        }
        executor.asCoroutineDispatcher().use { dispatcher ->
            val catalogThread = AtomicReference<Thread>()
            val homeThread = AtomicReference<Thread>()
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                catalogBuilder = { input ->
                    catalogThread.set(Thread.currentThread())
                    deriveCatalogScreenModel(input)
                },
                homeBuilder = { input ->
                    homeThread.set(Thread.currentThread())
                    deriveHomeContentModel(input)
                },
            )

            runBlocking {
                store.catalog(movieInput(Catalog(emptyList(), listOf(movie(1, "One", "all", 1L)))))
                store.home(homeInput(Catalog(emptyList(), listOf(movie(2, "Home", "all", 2L)))))
            }

            assertSame(workerThread.get(), catalogThread.get())
            assertSame(workerThread.get(), homeThread.get())
        }
    }

    @Test
    fun `favorite projection preserves catalog order and reuses an unchanged input`() = runBlocking {
        val movieCatalog = Catalog(
            emptyList(),
            listOf(movie(2, "Favorite Movie", "all", 200L), movie(1, "Other", "all", 100L)),
        )
        val seriesCatalog = Catalog(
            emptyList(),
            listOf(movie(3, "Favorite Series", "all", 300L).copy(type = ContentType.SERIES)),
        )
        val catalogs = mapOf(
            ContentType.MOVIE to movieCatalog,
            ContentType.SERIES to seriesCatalog,
        )
        val store = CatalogScreenEntryModelStore()
        val input = FavoriteCatalogProjectionInput(
            catalogs = catalogs,
            favorites = setOf("MOVIE:2", "SERIES:3"),
        )

        val first = store.favoriteProjection(input)
        val same = store.favoriteProjection(
            FavoriteCatalogProjectionInput(catalogs, setOf("SERIES:3", "MOVIE:2")),
        )
        val changed = store.favoriteProjection(
            FavoriteCatalogProjectionInput(catalogs, setOf("MOVIE:2")),
        )

        assertSame(first, same)
        assertEquals(listOf("MOVIE:2", "SERIES:3"), first.map { "${it.type}:${it.id}" })
        assertEquals(listOf("MOVIE:2"), changed.map { "${it.type}:${it.id}" })
    }

    @Test
    fun `live projection keeps category query and favorite semantics off the composition path`() = runBlocking {
        val liveCatalog = Catalog(
            emptyList(),
            listOf(
                live(1, "Alpha News", "news"),
                live(2, "Alpha Sport", "sport"),
                live(3, "Beta News", "news"),
            ),
        )
        val favorites = CatalogFavoriteSnapshot(persisted = setOf("LIVE:3"))
        val store = CatalogScreenEntryModelStore()

        val all = store.liveProjection(
            LiveCatalogProjectionInput(liveCatalog, categoryId = null, query = "", favorites = favorites),
        )
        val news = store.liveProjection(
            LiveCatalogProjectionInput(liveCatalog, categoryId = "news", query = "", favorites = favorites),
        )
        val cached = store.liveProjection(
            LiveCatalogProjectionInput(liveCatalog, categoryId = "news", query = "", favorites = favorites),
        )
        val searched = store.liveProjection(
            LiveCatalogProjectionInput(liveCatalog, categoryId = null, query = " alpha ", favorites = favorites),
        )
        val favoriteCategory = store.liveProjection(
            LiveCatalogProjectionInput(
                liveCatalog,
                categoryId = FAVORITES_CATEGORY_ID,
                query = "",
                favorites = favorites,
            ),
        )

        assertSame(liveCatalog.items, all.items)
        assertEquals(listOf(1, 3), news.items.map(ContentItem::id))
        assertEquals(listOf(1, 2), searched.items.map(ContentItem::id))
        assertEquals(listOf(3), favoriteCategory.items.map(ContentItem::id))
        assertSame(news.items, cached.items)
        assertSame(news.input, cached.input)
    }

    @Test
    fun `delayed live projection cannot satisfy a newer category or query input`() = runBlocking {
        val liveCatalog = Catalog(
            emptyList(),
            listOf(
                live(1, "Alpha News", "news"),
                live(2, "Alpha Sport", "sport"),
                live(3, "Beta News", "news"),
            ),
        )
        val favorites = CatalogFavoriteSnapshot(persisted = emptySet())
        val oldInput = LiveCatalogProjectionInput(
            liveCatalog,
            categoryId = "news",
            query = "",
            favorites = favorites,
        )
        val newInput = LiveCatalogProjectionInput(
            liveCatalog,
            categoryId = null,
            query = " alpha ",
            favorites = favorites,
        )
        val oldStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                liveBuilder = { input ->
                    if (input == oldInput) {
                        oldStarted.countDown()
                        check(releaseOld.await(5, TimeUnit.SECONDS))
                    }
                    deriveLiveCatalogProjection(input)
                },
            )

            val oldResult = async(dispatcher) { store.liveProjection(oldInput) }
            assertTrue(oldStarted.await(5, TimeUnit.SECONDS))
            assertTrue(!oldResult.isCompleted)

            val newResult = store.liveProjection(newInput)
            assertEquals(listOf(1, 2), newResult.items.map(ContentItem::id))
            assertSame(newResult.items, store.cachedLiveProjection(newInput)?.items)

            releaseOld.countDown()
            val stale = oldResult.await()
            assertNull(stale.exactItemsFor(newInput))
            assertSame(newInput, newResult.input)
            assertSame(newResult.items, store.cachedLiveProjection(newInput)?.items)
        }
    }

    @Test
    fun `exact empty live projection is distinguishable from a stale non-empty input`() = runBlocking {
        val liveCatalog = Catalog(
            emptyList(),
            listOf(live(1, "Alpha News", "news")),
        )
        val favorites = CatalogFavoriteSnapshot(persisted = emptySet())
        val emptyInput = LiveCatalogProjectionInput(
            liveCatalog,
            categoryId = "missing",
            query = "",
            favorites = favorites,
        )
        val nonEmptyInput = LiveCatalogProjectionInput(
            liveCatalog,
            categoryId = "news",
            query = "",
            favorites = favorites,
        )
        val store = CatalogScreenEntryModelStore()

        val empty = store.liveProjection(emptyInput)

        assertTrue(empty.items.isEmpty())
        assertSame(empty.items, empty.exactItemsFor(emptyInput))
        assertNull(empty.exactItemsFor(nonEmptyInput))
    }

    private fun live(id: Int, name: String, category: String): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = category,
        type = ContentType.LIVE,
        posterUrl = null,
        rating = "8.0",
        year = null,
        containerExtension = "m3u8",
        addedAtEpochSeconds = 1_000L + id,
        genre = category,
    )

    private fun homeInput(catalog: Catalog?): HomeContentModelInput = HomeContentModelInput(
        movieCatalog = catalog,
        seriesCatalog = null,
        liveCatalog = null,
        history = emptyList(),
        favorites = emptySet(),
    )

    private fun seriesInput(catalog: Catalog?): CatalogScreenModelInput = CatalogScreenModelInput(
        catalog = catalog,
        history = emptyList(),
        favorites = CatalogFavoriteSnapshot(emptySet()),
        type = ContentType.SERIES,
        destination = MainDestination.SERIES,
        categoryId = null,
        query = "",
    )

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

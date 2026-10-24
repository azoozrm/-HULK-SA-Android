package sa.hulksa.player.ui.screens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

/**
 * R05: a favorites-only commit updates the explicit heart snapshot immediately while the Home
 * presentation keeps its accepted recommendation rows. A new Home visit/source change still uses
 * the committed favorites to build truthful personalization.
 */
class HomeFavoritePresentationTest {
    @Test
    fun `favorites-only commit keeps accepted presentation rows and updates the heart snapshot`() {
        val catalog = movieCatalog()
        val history = emptyList<HistoryEntry>()
        val accepted = HomeContentModelInput(
            movieCatalog = catalog,
            seriesCatalog = null,
            liveCatalog = null,
            history = history,
            favorites = emptySet(),
        )
        val committed = HomeContentModelInput(
            movieCatalog = catalog,
            seriesCatalog = null,
            liveCatalog = null,
            history = history,
            favorites = setOf("MOVIE:1"),
        )

        val presentationInput = stableHomePresentationInput(accepted, committed)
        assertSame(accepted, presentationInput)

        val acceptedModel = deriveHomeContentModel(accepted)
        val presentedModel = deriveHomeContentModel(presentationInput)
        assertEquals(acceptedModel.becauseYouWatched.map(ContentItem::id), presentedModel.becauseYouWatched.map(ContentItem::id))
        assertEquals(acceptedModel.suggested.map(ContentItem::id), presentedModel.suggested.map(ContentItem::id))
        assertTrue(presentedModel.becauseYouWatched.isEmpty())

        // The committed snapshot is an explicit observed value, independent of the row builder.
        val before = CatalogFavoriteSnapshot(persisted = accepted.favorites)
        val after = CatalogFavoriteSnapshot(persisted = committed.favorites)
        assertFalse(before.contains(movie(1)))
        assertTrue(after.contains(movie(1)))
        assertFalse(after.contains(movie(2)))

        // The next genuine calculation uses the committed favorites and changes the rows.
        val nextModel = deriveHomeContentModel(committed)
        assertNotSame(acceptedModel, nextModel)
        assertEquals(setOf(1, 2), nextModel.becauseYouWatched.map(ContentItem::id).toSet())
    }

    @Test
    fun `blocked recommendation builder does not gate the committed heart or shuffle rows`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                homeBuilder = { input ->
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "home builder barrier timed out" }
                    deriveHomeContentModel(input)
                },
            )
            val catalog = movieCatalog()
            val accepted = HomeContentModelInput(catalog, null, null, emptyList(), emptySet())
            val build = async(dispatcher) { store.home(accepted) }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            val committed = HomeContentModelInput(
                movieCatalog = catalog,
                seriesCatalog = null,
                liveCatalog = null,
                history = emptyList(),
                favorites = setOf("MOVIE:1"),
            )
            val presentationInput = stableHomePresentationInput(accepted, committed)
            assertSame(accepted, presentationInput)
            assertTrue(CatalogFavoriteSnapshot(committed.favorites).contains(movie(1)))

            release.countDown()
            val built = build.await()
            val expectedRows = deriveHomeContentModel(accepted)
            assertEquals(
                expectedRows.becauseYouWatched.map(ContentItem::id),
                built.model.becauseYouWatched.map(ContentItem::id),
            )
            assertTrue(built.model.becauseYouWatched.isEmpty())

            val next = store.home(committed)
            assertEquals(setOf(1, 2), next.model.becauseYouWatched.map(ContentItem::id).toSet())
        }
    }

    @Test
    fun `catalog history and Live source changes still define a new presentation revision`() {
        val catalog = movieCatalog()
        val nextCatalog = movieCatalog(extraId = 9)
        val accepted = HomeContentModelInput(catalog, null, null, emptyList(), emptySet())

        val catalogChange = HomeContentModelInput(nextCatalog, null, null, emptyList(), emptySet())
        assertSame(catalogChange, stableHomePresentationInput(accepted, catalogChange))

        val historyChange = HomeContentModelInput(
            catalog,
            null,
            null,
            listOf(historyEntry()),
            emptySet(),
        )
        assertSame(historyChange, stableHomePresentationInput(accepted, historyChange))

        val liveCatalog = Catalog(emptyList(), listOf(movie(7).copy(type = ContentType.LIVE)))
        val liveChange = HomeContentModelInput(catalog, null, liveCatalog, emptyList(), emptySet())
        assertSame(liveChange, stableHomePresentationInput(accepted, liveChange))
    }

    @Test
    fun `explicit favorites snapshot covers Movie Series and Live hearts`() {
        val snapshot = CatalogFavoriteSnapshot(
            persisted = setOf("MOVIE:1", "SERIES:2", "LIVE:3"),
        )
        assertTrue(snapshot.contains(movie(1)))
        assertTrue(snapshot.contains(movie(2).copy(type = ContentType.SERIES)))
        assertTrue(snapshot.contains(movie(3).copy(type = ContentType.LIVE)))
        assertFalse(snapshot.contains(movie(4)))
    }

    private fun movieCatalog(extraId: Int? = null): Catalog = Catalog(
        categories = emptyList(),
        items = buildList {
            add(movie(1, category = "action", rating = "7.0", added = 300L))
            add(movie(2, category = "action", rating = "9.0", added = 200L))
            add(movie(3, category = "drama", rating = "10.0", added = 100L))
            extraId?.let { add(movie(it, category = "drama", rating = "8.0", added = 400L)) }
        },
    )

    private fun movie(
        id: Int,
        category: String = "action",
        rating: String = "8.0",
        added: Long = 0L,
    ): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = category,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = null,
        containerExtension = "mp4",
        genre = if (category == "action") "Action" else "Drama",
        addedAtEpochSeconds = added,
    )

    private fun historyEntry(): HistoryEntry = HistoryEntry(
        key = "movie:1",
        title = "Movie 1",
        posterUrl = null,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
        isLive = false,
        positionMs = 30_000L,
        durationMs = 100_000L,
        updatedAtEpochMs = 1_000L,
    )
}

package sa.hulksa.player.ui.screens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class CatalogRevisionIndexTest {
    private fun item(
        id: Int,
        added: Long,
        categoryId: String = "a",
        name: String = "item-$id",
    ) = ContentItem(
        id = id,
        name = name,
        categoryId = categoryId,
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = "7.$id",
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
    )

    private fun catalog(items: List<ContentItem> = listOf(
        item(1, 10, "a", "alpha"),
        item(2, 30, "b", "beta"),
        item(3, 20, "a", "gamma"),
        item(4, 40, "b", "delta"),
    )) = Catalog(
        categories = listOf(
            Category("a", "A", ContentType.MOVIE),
            Category("b", "B", ContentType.MOVIE),
        ),
        items = items,
    )

    private fun input(
        catalog: Catalog?,
        categoryId: String? = null,
        query: String = "",
        favorites: Set<String> = emptySet(),
    ) = CatalogScreenModelInput(
        catalog = catalog,
        history = emptyList(),
        favorites = CatalogFavoriteSnapshot(favorites),
        type = ContentType.MOVIE,
        destination = MainDestination.MOVIES,
        categoryId = categoryId,
        query = query,
    )

    @Test
    fun `index reuses newest order and preserves exact category query and favorite results`() {
        val source = catalog()
        val index = CatalogRevisionIndex.build(source)
        val expectedOrder = newest(source.items).map(ContentItem::id)
        assertEquals(expectedOrder, index.newestItems.map(ContentItem::id))
        assertEquals(source.items.size, index.itemByKey.size)
        assertSame(source.items.first { it.id == 2 }, index.itemByKey["MOVIE:2"])

        val cases = listOf(
            input(source),
            input(source, categoryId = "a"),
            input(source, categoryId = "b"),
            input(source, query = "a"),
            input(source, categoryId = FAVORITES_CATEGORY_ID, favorites = setOf("MOVIE:2", "MOVIE:4")),
        )
        cases.forEach { value ->
            val indexed = deriveCatalogScreenModel(value, index)
            val plain = deriveCatalogScreenModel(value)
            assertEquals(plain.visible.map(ContentItem::id), indexed.visible.map(ContentItem::id))
            assertEquals(plain.contentKeys, indexed.contentKeys)
            assertEquals(plain.contentKeyIndex, indexed.contentKeyIndex)
            assertEquals(plain.continueWatching, indexed.continueWatching)
        }
    }

    @Test
    fun `late revision build cannot repopulate after clear or evict a newer revision`() = runBlocking {
        val oldCatalog = catalog()
        val newCatalog = catalog(
            listOf(
                item(10, 100, "a", "new-alpha"),
                item(11, 90, "b", "new-beta"),
            ),
        )
        val oldBuildStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val latestIndex = AtomicReference<CatalogRevisionIndex>()
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val store = CatalogScreenEntryModelStore(
                dispatcher = dispatcher,
                indexedCatalogBuilder = { value, index ->
                    latestIndex.set(index)
                    deriveCatalogScreenModel(value, index)
                },
                revisionIndexBuilder = { catalog ->
                    if (catalog === oldCatalog) {
                        oldBuildStarted.countDown()
                        check(releaseOld.await(5, TimeUnit.SECONDS))
                    }
                    CatalogRevisionIndex.build(catalog)
                },
            )
            val old = async(dispatcher) { store.catalog(input(oldCatalog)) }
            assertTrue(oldBuildStarted.await(5, TimeUnit.SECONDS))

            store.clear()
            val current = store.catalog(input(newCatalog))
            val currentIndex = latestIndex.get()

            releaseOld.countDown()
            old.await()
            // A category change forces a model rebuild but must reuse the cached current index.
            val repeated = store.catalog(input(newCatalog, categoryId = "a"))

            assertSame(currentIndex, latestIndex.get())
            assertEquals(
                current.model.visible.filter { it.categoryId == "a" }.map(ContentItem::id),
                repeated.model.visible.map(ContentItem::id),
            )
        }
    }

    @Test
    fun `store reuses the revision index across category and query changes and invalidates a new catalog`() = runBlocking {
        val source = catalog()
        val seen = mutableListOf<CatalogRevisionIndex>()
        val store = CatalogScreenEntryModelStore(
            indexedCatalogBuilder = { value, index ->
                seen += index
                deriveCatalogScreenModel(value, index)
            },
        )

        store.catalog(input(source))
        store.catalog(input(source, categoryId = "a"))
        store.catalog(input(source, query = "beta"))
        assertSame(seen[0], seen[1])
        assertSame(seen[0], seen[2])

        val replacement = source.copy(items = source.items.reversed())
        store.catalog(input(replacement))
        assertNotSame(seen[0], seen[3])
        assertEquals(newest(replacement.items).map(ContentItem::id), seen[3].newestItems.map(ContentItem::id))
    }
}

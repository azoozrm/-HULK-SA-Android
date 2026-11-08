package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class CatalogWriteGateTest {
    @Test
    fun `newer catalog and home revisions invalidate older work`() {
        val gate = CatalogWriteGate()
        val movie = gate.nextCatalog(ContentType.MOVIE)
        val series = gate.nextCatalog(ContentType.SERIES)
        assertTrue(gate.isCatalogCurrent(ContentType.MOVIE, movie))
        assertTrue(gate.isCatalogCurrent(ContentType.SERIES, series))

        val newerMovie = gate.nextCatalog(ContentType.MOVIE)
        assertFalse(gate.isCatalogCurrent(ContentType.MOVIE, movie))
        assertTrue(gate.isCatalogCurrent(ContentType.MOVIE, newerMovie))
        assertTrue(gate.isCatalogCurrent(ContentType.SERIES, series))

        gate.invalidateCatalogs()
        assertFalse(gate.isCatalogCurrent(ContentType.MOVIE, newerMovie))
        assertFalse(gate.isCatalogCurrent(ContentType.SERIES, series))

        val home = gate.nextHome()
        assertTrue(gate.isHomeCurrent(home))
        gate.invalidateHome()
        assertFalse(gate.isHomeCurrent(home))
    }

    @Test
    fun `identical revalidation canonicalizes the baseline and a failed write releases it`() {
        val baselines = CatalogPersistBaselines()
        val disk = catalog(1)
        val network = disk.copy()
        baselines.onRestored(ContentType.MOVIE, disk)
        baselines.onIdentical(ContentType.MOVIE, network)

        assertSame(network, baselines.get(ContentType.MOVIE))
        baselines.onWriteFailed(ContentType.MOVIE)
        assertNull(baselines.get(ContentType.MOVIE))
    }

    @Test
    fun `delayed old write cannot overwrite a newer committed generation`() = runBlocking {
        val gate = CatalogWriteGate()
        val store = HeldWriteStore()
        val key = CatalogSnapshotKey("account", "provider", ContentType.MOVIE)
        val oldCatalog = catalog(1)
        val newCatalog = catalog(2)
        val oldRevision = gate.nextCatalog(ContentType.MOVIE)

        val oldWrite = async(Dispatchers.Default) {
            store.save(key, oldCatalog) {
                gate.isCatalogCurrent(ContentType.MOVIE, oldRevision)
            }
        }
        store.oldStarted.await()

        // A newer request (new session/revision) saves and commits first.
        val newRevision = gate.nextCatalog(ContentType.MOVIE)
        val newWrite = store.save(key, newCatalog) {
            gate.isCatalogCurrent(ContentType.MOVIE, newRevision)
        }
        assertEquals(newCatalog, store.committed.get()?.catalog)

        // The delayed old write resumes; the transaction-boundary guard must reject it.
        store.releaseOld.complete(Unit)
        assertNull(oldWrite.await())
        assertTrue(newWrite != null)
        assertEquals(newCatalog, store.committed.get()?.catalog)
    }

    @Test
    fun `delayed old home write cannot overwrite newer profile home`() = runBlocking {
        val gate = CatalogWriteGate()
        val store = HeldHomeWriteStore()
        val key = HomeBootstrapKey("account", "provider", "profile")
        val oldHome = home("old")
        val newHome = home("new")
        val oldRevision = gate.nextHome()

        val oldWrite = async(Dispatchers.Default) {
            store.saveHomeBootstrap(key, oldHome) {
                gate.isHomeCurrent(oldRevision)
            }
        }
        store.oldStarted.await()

        val newRevision = gate.nextHome()
        assertTrue(
            store.saveHomeBootstrap(key, newHome) {
                gate.isHomeCurrent(newRevision)
            },
        )
        assertEquals("new", store.committed.get()?.personalizationKey)

        store.releaseOld.complete(Unit)
        assertFalse(oldWrite.await())
        assertEquals("new", store.committed.get()?.personalizationKey)
    }

    private class HeldWriteStore : CatalogSnapshotStore {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val committed = AtomicReference<CatalogSnapshot?>()

        override suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot? = committed.get()

        override suspend fun save(
            key: CatalogSnapshotKey,
            catalog: Catalog,
            isCurrent: () -> Boolean,
        ): CatalogSnapshot? {
            if (catalog.items.single().id == 1) {
                oldStarted.complete(Unit)
                releaseOld.await()
            }
            if (!isCurrent()) return null
            return CatalogSnapshot(catalog, 1L, System.currentTimeMillis())
                .also(committed::set)
        }

        override suspend fun loadHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? = null

        override suspend fun saveHomeBootstrap(
            key: HomeBootstrapKey,
            snapshot: HomeBootstrapSnapshot,
            isCurrent: () -> Boolean,
        ): Boolean = false

        override suspend fun prune() = Unit
    }

    private class HeldHomeWriteStore : CatalogSnapshotStore {
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val committed = AtomicReference<HomeBootstrapSnapshot?>()

        override suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot? = null

        override suspend fun save(
            key: CatalogSnapshotKey,
            catalog: Catalog,
            isCurrent: () -> Boolean,
        ): CatalogSnapshot? = null

        override suspend fun loadHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? =
            committed.get()

        override suspend fun saveHomeBootstrap(
            key: HomeBootstrapKey,
            snapshot: HomeBootstrapSnapshot,
            isCurrent: () -> Boolean,
        ): Boolean {
            if (snapshot.personalizationKey == "old") {
                oldStarted.complete(Unit)
                releaseOld.await()
            }
            if (!isCurrent()) return false
            committed.set(snapshot)
            return true
        }

        override suspend fun prune() = Unit
    }

    private fun catalog(id: Int) = Catalog(
        categories = emptyList(),
        items = listOf(
            ContentItem(
                id = id,
                name = "item-$id",
                categoryId = "category",
                type = ContentType.MOVIE,
                posterUrl = null,
                rating = null,
                year = null,
                containerExtension = "mp4",
            ),
        ),
    )

    private fun home(key: String) = HomeBootstrapSnapshot(
        catalogs = mapOf(ContentType.MOVIE to catalog(if (key == "old") 1 else 2)),
        sourceGenerations = mapOf(ContentType.MOVIE to 1L),
        personalizationKey = key,
        completedAtEpochMs = System.currentTimeMillis(),
    )
}

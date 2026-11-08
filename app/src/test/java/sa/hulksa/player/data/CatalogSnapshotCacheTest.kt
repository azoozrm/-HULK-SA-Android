package sa.hulksa.player.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.canUseGeneralCatalogCache
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.ui.screens.HOME_CATALOG_LIMIT
import sa.hulksa.player.ui.screens.selectHomeBootstrapSeed

class CatalogSnapshotCacheTest {
    private class FakeStore : CatalogSnapshotStore {
        val snapshots = mutableMapOf<CatalogSnapshotKey, CatalogSnapshot>()
        val homes = mutableMapOf<HomeBootstrapKey, HomeBootstrapSnapshot>()
        var failSave = false
        var corruptLoad: Catalog? = null
        var corruptHome: HomeBootstrapSnapshot? = null
        var touchCount = 0

        override suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot? {
            corruptLoad?.let { raw ->
                return raw.takeIf { validateCatalogSnapshot(key.contentType, it) }
                    ?.let { CatalogSnapshot(it, 99L, System.currentTimeMillis()) }
            }
            return snapshots[key]
        }

        override suspend fun save(
            key: CatalogSnapshotKey,
            catalog: Catalog,
            isCurrent: () -> Boolean,
        ): CatalogSnapshot? {
            if (failSave) error("simulated write failure")
            if (!validateCatalogSnapshot(key.contentType, catalog)) return null
            if (!isCurrent()) return null
            val generation = (snapshots[key]?.generation ?: 0L) + 1L
            return CatalogSnapshot(catalog, generation, System.currentTimeMillis())
                .also { snapshots[key] = it }
        }

        override suspend fun touch(
            key: CatalogSnapshotKey,
            isCurrent: () -> Boolean,
        ): Boolean {
            if (!isCurrent()) return false
            val existing = snapshots[key] ?: return false
            snapshots[key] = existing.copy(completedAtEpochMs = System.currentTimeMillis())
            touchCount += 1
            return true
        }

        override suspend fun loadHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? =
            corruptHome ?: homes[key]?.takeIf(::validateHomeBootstrap)

        override suspend fun saveHomeBootstrap(
            key: HomeBootstrapKey,
            snapshot: HomeBootstrapSnapshot,
            isCurrent: () -> Boolean,
        ): Boolean {
            if (failSave) error("simulated write failure")
            if (!validateHomeBootstrap(snapshot)) return false
            if (!isCurrent()) return false
            homes[key] = snapshot
            return true
        }

        override suspend fun deleteHomeBootstrap(
            key: HomeBootstrapKey,
            isCurrent: () -> Boolean,
        ): Boolean {
            if (!isCurrent()) return false
            return homes.remove(key) != null
        }

        override suspend fun prune() = Unit
    }

    private fun item(
        id: Int,
        type: ContentType,
        categoryId: String = "cat-a",
        added: Long = id.toLong(),
    ) = ContentItem(
        id = id,
        name = "item-$id",
        categoryId = categoryId,
        type = type,
        posterUrl = null,
        rating = "7.$id",
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = added,
    )

    private fun catalog(type: ContentType, count: Int = 3) = Catalog(
        categories = listOf(
            Category("cat-a", "A", type),
            Category("cat-b", "B", type),
        ),
        items = (1..count).map { item(it, type, if (it % 2 == 0) "cat-b" else "cat-a") },
    )

    private fun homeSnapshot(
        vararg catalogs: Pair<ContentType, Catalog>,
        personalizationKey: String = "personalization",
        completedAtEpochMs: Long = System.currentTimeMillis(),
    ) = HomeBootstrapSnapshot(
        catalogs = mapOf(*catalogs),
        sourceGenerations = catalogs.associate { (type, _) -> type to 1L },
        personalizationKey = personalizationKey,
        completedAtEpochMs = completedAtEpochMs,
    )

    @Test
    fun `complete snapshot round trips with order and counts`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.MOVIE)
        val source = catalog(ContentType.MOVIE)

        val saved = cache.persist(key, source)
        val restored = cache.restore(key)

        assertNotNull(saved)
        assertNotNull(restored)
        assertEquals(source.categories, restored!!.catalog.categories)
        assertEquals(source.items.map(ContentItem::id), restored.catalog.items.map(ContentItem::id))
        assertEquals(source.items.size, restored.catalog.items.size)
        assertEquals(saved!!.generation, restored.generation)
    }

    @Test
    fun `failed write retains the previous generation`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.SERIES)
        val first = catalog(ContentType.SERIES, count = 2)
        val second = catalog(ContentType.SERIES, count = 4)

        assertNotNull(cache.persist(key, first))
        store.failSave = true
        assertNull(cache.persist(key, second))

        val restored = cache.restore(key)
        assertNotNull(restored)
        assertEquals(first.items.map(ContentItem::id), restored!!.catalog.items.map(ContentItem::id))
        assertEquals(1L, restored.generation)
    }

    @Test
    fun `corruption is a safe cache miss`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.LIVE)
        store.corruptLoad = Catalog(
            categories = emptyList(),
            items = listOf(item(1, ContentType.MOVIE)),
        )

        assertNull(cache.restore(key))
    }

    @Test
    fun `account and provider scopes are isolated`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val a = CatalogSnapshotKey("account-a", "provider-a", ContentType.MOVIE)
        val b = CatalogSnapshotKey("account-b", "provider-a", ContentType.MOVIE)
        val c = CatalogSnapshotKey("account-a", "provider-b", ContentType.MOVIE)

        assertNotNull(cache.persist(a, catalog(ContentType.MOVIE)))
        assertNull(cache.restore(b))
        assertNull(cache.restore(c))
    }

    @Test
    fun `home bootstrap round trips and validates`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = HomeBootstrapKey("account", "provider", "profile")
        val snapshot = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
            ContentType.SERIES to Catalog(emptyList(), listOf(item(2, ContentType.SERIES))),
            ContentType.LIVE to Catalog(emptyList(), listOf(item(3, ContentType.LIVE))),
        )

        assertTrue(cache.persistHomeBootstrap(key, snapshot))
        val restored = cache.restoreHomeBootstrap(key)
        assertNotNull(restored)
        assertEquals(
            setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            restored!!.catalogs.keys,
        )
        assertEquals(snapshot.sourceGenerations, restored.sourceGenerations)
        assertEquals(snapshot.personalizationKey, restored.personalizationKey)

        val invalid = snapshot.copy(
            catalogs = mapOf(
                ContentType.MOVIE to Catalog(
                    emptyList(),
                    listOf(item(1, ContentType.MOVIE), item(1, ContentType.MOVIE)),
                ),
            ),
            sourceGenerations = mapOf(ContentType.MOVIE to 1L),
        )
        assertFalse(cache.persistHomeBootstrap(key, invalid))
    }

    @Test
    fun `home bootstrap is profile isolated`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val profileA = HomeBootstrapKey("account", "provider", "profile-a")
        val profileB = HomeBootstrapKey("account", "provider", "profile-b")
        val snapshot = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        )
        assertTrue(cache.persistHomeBootstrap(profileA, snapshot))
        assertNotNull(cache.restoreHomeBootstrap(profileA))
        assertNull(cache.restoreHomeBootstrap(profileB))
    }

    @Test
    fun `touch extends retention without rewriting the snapshot`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.MOVIE)
        val oldCompletedAt = System.currentTimeMillis() - CATALOG_CACHE_RETENTION_MS + 60_000L
        store.snapshots[key] = CatalogSnapshot(
            catalog(ContentType.MOVIE),
            generation = 2L,
            completedAtEpochMs = oldCompletedAt,
        )

        assertTrue(cache.touch(key))
        assertTrue(store.snapshots.getValue(key).completedAtEpochMs > oldCompletedAt)
        assertEquals(2L, store.snapshots.getValue(key).generation)
        assertEquals(1, store.touchCount)
        assertFalse(cache.touch(key) { false })
    }

    @Test
    fun `complete empty full catalog round trips with zero counts`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.SERIES)
        val empty = Catalog(
            categories = listOf(Category("empty-category", "Empty", ContentType.SERIES)),
            items = emptyList(),
        )

        assertNotNull(cache.persist(key, empty))
        val restored = cache.restore(key)

        assertNotNull(restored)
        assertEquals(0, restored!!.catalog.items.size)
        assertEquals(1, restored.catalog.categories.size)
        assertEquals("empty-category", restored.catalog.categories.single().id)
    }

    @Test
    fun `home seed rejects an empty type while full empty catalogs stay complete`() {
        val emptyHome = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), emptyList()),
        )
        assertFalse(validateHomeBootstrap(emptyHome))
        assertTrue(validateCatalogSnapshot(ContentType.MOVIE, Catalog(emptyList(), emptyList())))
    }

    @Test
    fun `delete home bootstrap removes only the current seed`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = HomeBootstrapKey("account", "provider", "profile")
        store.homes[key] = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        )

        assertFalse(cache.deleteHomeBootstrap(key) { false })
        assertNotNull(cache.restoreHomeBootstrap(key))
        assertTrue(cache.deleteHomeBootstrap(key))
        assertNull(cache.restoreHomeBootstrap(key))
    }

    @Test
    fun `mixed empty and healthy Home seed persists only the healthy type metadata`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = HomeBootstrapKey("account", "provider", "profile")
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
                ContentType.SERIES to Catalog(
                    emptyList(),
                    listOf(item(1, ContentType.SERIES)),
                ),
            ),
            sourceGenerations = mapOf(
                ContentType.MOVIE to 1L,
                ContentType.SERIES to 2L,
            ),
        )
        val snapshot = HomeBootstrapSnapshot(
            catalogs = selection.catalogs,
            sourceGenerations = selection.sourceGenerations,
            personalizationKey = "personalization",
            completedAtEpochMs = System.currentTimeMillis(),
        )

        assertTrue(cache.persistHomeBootstrap(key, snapshot))
        val restored = cache.restoreHomeBootstrap(key)

        assertNotNull(restored)
        assertEquals(setOf(ContentType.SERIES), restored!!.catalogs.keys)
        assertEquals(setOf(ContentType.SERIES), restored.sourceGenerations.keys)
        assertEquals(1, restored.catalogs.getValue(ContentType.SERIES).items.size)
        assertEquals(2L, restored.sourceGenerations.getValue(ContentType.SERIES))
    }

    @Test
    fun `authoritative all-empty selection deletes the previous nonempty Home seed`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val key = HomeBootstrapKey("account", "provider", "profile")
        store.homes[key] = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        )
        val selection = selectHomeBootstrapSeed(
            seeds = mapOf(
                ContentType.MOVIE to Catalog(emptyList(), emptyList()),
                ContentType.SERIES to Catalog(emptyList(), emptyList()),
                ContentType.LIVE to Catalog(emptyList(), emptyList()),
            ),
            sourceGenerations = mapOf(
                ContentType.MOVIE to 1L,
                ContentType.SERIES to 2L,
                ContentType.LIVE to 3L,
            ),
        )

        assertTrue(selection.catalogs.isEmpty())
        assertTrue(selection.confirmedEmptyTypes.size == 3)
        assertTrue(selection.missingTypes.isEmpty())
        assertTrue(selection.unboundTypes.isEmpty())
        assertTrue(cache.deleteHomeBootstrap(key))
        assertNull(cache.restoreHomeBootstrap(key))
    }

    @Test
    fun `expired snapshots are rejected before any write`() = runBlocking {
        val store = FakeStore()
        val cache = CatalogSnapshotCache(store)
        val catalogKey = CatalogSnapshotKey("account", "provider", ContentType.MOVIE)
        val homeKey = HomeBootstrapKey("account", "provider", "profile")
        val expiredAt = System.currentTimeMillis() - CATALOG_CACHE_RETENTION_MS - 1L
        store.snapshots[catalogKey] = CatalogSnapshot(
            catalog(ContentType.MOVIE),
            generation = 3L,
            completedAtEpochMs = expiredAt,
        )
        store.homes[homeKey] = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
            completedAtEpochMs = expiredAt,
        )

        assertNull(cache.restore(catalogKey))
        assertNull(cache.restoreHomeBootstrap(homeKey))
    }

    @Test
    fun `home bootstrap rejects over limit and incomplete identity`() {
        val overLimit = homeSnapshot(
            ContentType.MOVIE to Catalog(
                emptyList(),
                (1..HOME_CATALOG_LIMIT + 1).map { item(it, ContentType.MOVIE) },
            ),
        )
        val missingSource = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        ).copy(sourceGenerations = emptyMap())
        val blankPersonalization = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
            personalizationKey = " ",
        )
        val valid = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        )

        assertFalse(validateHomeBootstrap(overLimit))
        assertFalse(validateHomeBootstrap(missingSource))
        assertFalse(validateHomeBootstrap(blankPersonalization))
        assertTrue(validateHomeBootstrap(valid))
    }

    @Test
    fun `home type metadata detects a removed item`() {
        val twoItems = Catalog(
            emptyList(),
            listOf(item(1, ContentType.MOVIE), item(2, ContentType.MOVIE)),
        )
        val oneItem = Catalog(emptyList(), listOf(item(1, ContentType.MOVIE)))
        val generations = mapOf(ContentType.MOVIE to 4L)

        assertTrue(
            validateHomeBootstrapTypeMetadata(
                expectedItemCounts = mapOf(ContentType.MOVIE to 2),
                sourceGenerations = generations,
                catalogs = mapOf(ContentType.MOVIE to twoItems),
            ),
        )
        assertFalse(
            validateHomeBootstrapTypeMetadata(
                expectedItemCounts = mapOf(ContentType.MOVIE to 2),
                sourceGenerations = generations,
                catalogs = mapOf(ContentType.MOVIE to oneItem),
            ),
        )
        assertFalse(
            validateHomeBootstrapTypeMetadata(
                expectedItemCounts = mapOf(ContentType.MOVIE to 1),
                sourceGenerations = emptyMap(),
                catalogs = mapOf(ContentType.MOVIE to oneItem),
            ),
        )
    }

    @Test
    fun `storage failure is a safe miss and never escapes`() = runBlocking {
        val store = object : CatalogSnapshotStore {
            override suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot? =
                error("simulated corrupt read")

            override suspend fun save(
                key: CatalogSnapshotKey,
                catalog: Catalog,
                isCurrent: () -> Boolean,
            ): CatalogSnapshot? = error("simulated failed write")

            override suspend fun loadHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? =
                error("simulated corrupt read")

            override suspend fun saveHomeBootstrap(
                key: HomeBootstrapKey,
                snapshot: HomeBootstrapSnapshot,
                isCurrent: () -> Boolean,
            ): Boolean = error("simulated failed write")

            override suspend fun prune() = error("simulated maintenance failure")
        }
        val cache = CatalogSnapshotCache(store)
        val key = CatalogSnapshotKey("account", "provider", ContentType.MOVIE)
        val home = HomeBootstrapKey("account", "provider", "profile")
        val snapshot = homeSnapshot(
            ContentType.MOVIE to Catalog(emptyList(), listOf(item(1, ContentType.MOVIE))),
        )

        assertNull(cache.restore(key))
        assertNull(cache.persist(key, catalog(ContentType.MOVIE)))
        assertNull(cache.restoreHomeBootstrap(home))
        assertFalse(cache.persistHomeBootstrap(home, snapshot))
        cache.prune()
    }

    @Test
    fun `partial mixed and oversized snapshots are rejected before persistence`() {
        val duplicate = Catalog(
            categories = listOf(Category("a", "A", ContentType.MOVIE)),
            items = listOf(item(1, ContentType.MOVIE), item(1, ContentType.MOVIE)),
        )
        val mixed = Catalog(
            categories = listOf(Category("a", "A", ContentType.MOVIE)),
            items = listOf(item(1, ContentType.SERIES)),
        )
        val wrongCategoryType = Catalog(
            categories = listOf(Category("a", "A", ContentType.LIVE)),
            items = listOf(item(1, ContentType.MOVIE)),
        )
        val oversized = Catalog(
            categories = emptyList(),
            items = (1..CATALOG_CACHE_MAX_ITEMS + 1).map { item(it, ContentType.MOVIE) },
        )

        assertFalse(validateCatalogSnapshot(ContentType.MOVIE, duplicate))
        assertFalse(validateCatalogSnapshot(ContentType.MOVIE, mixed))
        assertFalse(validateCatalogSnapshot(ContentType.MOVIE, wrongCategoryType))
        assertFalse(validateCatalogSnapshot(ContentType.MOVIE, oversized))
        assertTrue(validateCatalogSnapshot(ContentType.MOVIE, catalog(ContentType.MOVIE)))
    }

    @Test
    fun `production cache delegates validation to the store IO boundary`() {
        val source = File("src/main/java/sa/hulksa/player/data/CatalogSnapshotStore.kt").readText()
        val cache = source
            .substringAfter("internal class CatalogSnapshotCache(")
            .substringBefore("internal class SqliteCatalogSnapshotStore(")
        assertFalse("cache must not scan full catalogs on its caller", cache.contains("validateCatalogSnapshot"))
        assertFalse(cache.contains("withContext(Dispatchers.Default)"))

        val save = source
            .substringAfter("override suspend fun save(")
            .substringBefore("override suspend fun touch(")
        assertTrue(save.contains("withContext(Dispatchers.IO)"))
        assertTrue(save.contains("validateCatalogSnapshot"))
        assertTrue(
            "the commit guard must precede setTransactionSuccessful",
            save.lastIndexOf("if (!isCurrent())") < save.indexOf("db.setTransactionSuccessful()"),
        )

        val homeSave = source
            .substringAfter("override suspend fun saveHomeBootstrap(")
            .substringBefore("override suspend fun prune(")
        assertTrue(homeSave.contains("withContext(Dispatchers.IO)"))
        assertTrue(homeSave.contains("validateHomeBootstrap"))
        assertTrue(
            "the Home commit guard must precede setTransactionSuccessful",
            homeSave.lastIndexOf("if (!isCurrent())") < homeSave.indexOf("db.setTransactionSuccessful()"),
        )
    }

    @Test
    fun `general cache is disabled before library readiness and for kids`() {
        assertTrue(canUseGeneralCatalogCache(ProfileKind.STANDARD, profileLibraryReady = true))
        assertFalse(canUseGeneralCatalogCache(ProfileKind.STANDARD, profileLibraryReady = false))
        assertFalse(canUseGeneralCatalogCache(ProfileKind.KIDS, profileLibraryReady = true))
        assertFalse(canUseGeneralCatalogCache(ProfileKind.KIDS, profileLibraryReady = false))
    }
}

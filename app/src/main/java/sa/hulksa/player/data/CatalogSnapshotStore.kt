package sa.hulksa.player.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.screens.HOME_CATALOG_LIMIT

internal const val CATALOG_CACHE_SCHEMA_VERSION = 2
internal const val CATALOG_CACHE_MAX_ITEMS = 100_000
internal const val CATALOG_CACHE_MAX_SCOPES = 8
internal const val CATALOG_CACHE_RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L

internal data class CatalogSnapshotKey(
    val accountId: String,
    val providerId: String,
    val contentType: ContentType,
)

internal data class CatalogSnapshot(
    val catalog: Catalog,
    val generation: Long,
    val completedAtEpochMs: Long,
)

internal data class HomeBootstrapKey(
    val accountId: String,
    val providerId: String,
    val profileId: String,
)

/**
 * Bounded profile-owned Home first frame. Every type is bound to the complete source revision and
 * item count that produced it; the personalization key binds the seed to the favorites/history
 * identities used for its ranking.
 */
internal data class HomeBootstrapSnapshot(
    val catalogs: Map<ContentType, Catalog>,
    val sourceGenerations: Map<ContentType, Long>,
    val personalizationKey: String,
    val completedAtEpochMs: Long,
)

/**
 * Durable complete-catalog storage. Implementations validate and write only on their own background
 * boundary, atomically replace one account/provider/type generation, and never expose a partially
 * written catalog. `isCurrent` is checked immediately before commit; a false result must roll back
 * the whole transaction.
 */
internal interface CatalogSnapshotStore {
    suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot?

    suspend fun save(
        key: CatalogSnapshotKey,
        catalog: Catalog,
        isCurrent: () -> Boolean = { true },
    ): CatalogSnapshot?

    /**
     * Extends retention for an already complete snapshot that a revalidation proved identical.
     * This is a metadata-only update; it never rewrites the catalog rows.
     */
    suspend fun touch(
        key: CatalogSnapshotKey,
        isCurrent: () -> Boolean = { true },
    ): Boolean = false

    suspend fun loadHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot?

    /**
     * Removes an obsolete Home seed after an authoritative all-empty result. The guard is checked
     * immediately before the transaction commits, so a newer profile/session seed is never removed.
     */
    suspend fun deleteHomeBootstrap(
        key: HomeBootstrapKey,
        isCurrent: () -> Boolean = { true },
    ): Boolean = false

    suspend fun saveHomeBootstrap(
        key: HomeBootstrapKey,
        snapshot: HomeBootstrapSnapshot,
        isCurrent: () -> Boolean = { true },
    ): Boolean

    suspend fun prune()
}

/** Validates only complete normalized catalogs. A failed validation is a cache miss. */
internal fun validateCatalogSnapshot(type: ContentType, catalog: Catalog): Boolean {
    if (catalog.categories.any { it.type != type }) return false
    if (catalog.items.any { it.type != type }) return false
    if (catalog.items.size > CATALOG_CACHE_MAX_ITEMS) return false
    if (catalog.categories.mapTo(HashSet(), Category::id).size != catalog.categories.size) return false
    if (catalog.items.mapTo(HashSet(), ContentItem::id).size != catalog.items.size) return false
    return true
}

internal fun validateHomeBootstrap(snapshot: HomeBootstrapSnapshot): Boolean {
    if (snapshot.catalogs.isEmpty()) return false
    if (snapshot.personalizationKey.isBlank()) return false
    if (snapshot.sourceGenerations.keys != snapshot.catalogs.keys) return false
    if (snapshot.sourceGenerations.values.any { it <= 0L }) return false
    return snapshot.catalogs.all { (type, catalog) ->
        catalog.items.isNotEmpty() &&
            catalog.items.size <= HOME_CATALOG_LIMIT &&
            validateCatalogSnapshot(type, catalog)
    }
}

/**
 * Cross-checks persisted per-type metadata against the rows that actually survived. A removed item
 * therefore invalidates the Home seed instead of silently shrinking it.
 */
internal fun validateHomeBootstrapTypeMetadata(
    expectedItemCounts: Map<ContentType, Int>,
    sourceGenerations: Map<ContentType, Long>,
    catalogs: Map<ContentType, Catalog>,
): Boolean {
    if (expectedItemCounts.keys != catalogs.keys) return false
    if (sourceGenerations.keys != catalogs.keys) return false
    return catalogs.all { (type, catalog) ->
        expectedItemCounts[type] == catalog.items.size &&
            catalog.items.size in 1..HOME_CATALOG_LIMIT &&
            (sourceGenerations[type] ?: 0L) > 0L
    }
}

internal fun isCatalogCacheExpired(
    completedAtEpochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean = completedAtEpochMs <= 0L ||
    nowEpochMs - completedAtEpochMs > CATALOG_CACHE_RETENTION_MS

/**
 * Cache policy around a storage backend. Validation and allocation belong to the backend's
 * background boundary; the cache only applies the cheap read-time age policy and turns every
 * storage failure into a safe miss. A cache write can never fail a live catalog load.
 */
internal class CatalogSnapshotCache(
    private val store: CatalogSnapshotStore,
) {
    suspend fun restore(key: CatalogSnapshotKey): CatalogSnapshot? = try {
        store.load(key)?.takeIf { !isCatalogCacheExpired(it.completedAtEpochMs) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    suspend fun persist(
        key: CatalogSnapshotKey,
        catalog: Catalog,
        isCurrent: () -> Boolean = { true },
    ): CatalogSnapshot? = try {
        store.save(key, catalog, isCurrent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    suspend fun touch(
        key: CatalogSnapshotKey,
        isCurrent: () -> Boolean = { true },
    ): Boolean = try {
        store.touch(key, isCurrent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    suspend fun restoreHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? = try {
        store.loadHomeBootstrap(key)?.takeIf { !isCatalogCacheExpired(it.completedAtEpochMs) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    suspend fun deleteHomeBootstrap(
        key: HomeBootstrapKey,
        isCurrent: () -> Boolean = { true },
    ): Boolean = try {
        store.deleteHomeBootstrap(key, isCurrent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    suspend fun persistHomeBootstrap(
        key: HomeBootstrapKey,
        snapshot: HomeBootstrapSnapshot,
        isCurrent: () -> Boolean = { true },
    ): Boolean = try {
        store.saveHomeBootstrap(key, snapshot, isCurrent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    suspend fun prune() {
        try {
            store.prune()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Cache maintenance must never affect the live path.
        }
    }
}

/**
 * App-private disposable SQLite implementation. The manifest disables backup for the whole app,
 * so this cache is never part of account/user-data migration or cloud backup. One row per
 * account/provider/type is retained; each successful save atomically replaces the previous
 * generation, and corruption discards only this cache.
 */
internal class SqliteCatalogSnapshotStore(
    context: Context,
) : CatalogSnapshotStore {
    private val appContext = context.applicationContext
    private val helper = CatalogCacheDatabase(appContext)
    private val mutex = Mutex()

    override suspend fun load(key: CatalogSnapshotKey): CatalogSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { readSnapshot(key) }.getOrElse { error ->
                if (!recoverFromCorruption(error)) runCatching { deleteSnapshot(key) }
                null
            }
        }
    }

    override suspend fun save(
        key: CatalogSnapshotKey,
        catalog: Catalog,
        isCurrent: () -> Boolean,
    ): CatalogSnapshot? {
        val saved = withContext(Dispatchers.IO) {
            // Full validation/allocation is intentionally inside the IO boundary, before the
            // transaction. The production caller is a Main-scope ViewModel job.
            if (!validateCatalogSnapshot(key.contentType, catalog)) return@withContext null
            mutex.withLock {
                runCatching {
                    if (!isCurrent()) return@runCatching null
                    val db = helper.writableDatabase
                    val now = System.currentTimeMillis()
                    db.beginTransaction()
                    try {
                        val generation = nextGeneration(db, key)
                        deleteSnapshotRows(db, key)
                        insertSnapshot(db, key, catalog, generation, now)
                        if (!isCurrent()) {
                            // Leave the transaction unsuccessful: the previous generation stays.
                            null
                        } else {
                            db.setTransactionSuccessful()
                            CatalogSnapshot(catalog, generation, now)
                        }
                    } finally {
                        db.endTransaction()
                    }
                }.getOrElse { error ->
                    recoverFromCorruption(error)
                    null
                }
            }
        }
        if (saved != null) prune()
        return saved
    }

    override suspend fun touch(
        key: CatalogSnapshotKey,
        isCurrent: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!isCurrent()) return@runCatching false
                val db = helper.writableDatabase
                val updated = db.update(
                    "catalog_snapshot",
                    ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
                    "account_id = ? AND provider_id = ? AND content_type = ? " +
                        "AND schema_version = ? AND completed = 1",
                    arrayOf(
                        key.accountId,
                        key.providerId,
                        key.contentType.name,
                        CATALOG_CACHE_SCHEMA_VERSION.toString(),
                    ),
                )
                updated == 1
            }.getOrDefault(false)
        }
    }

    override suspend fun loadHomeBootstrap(
        key: HomeBootstrapKey,
    ): HomeBootstrapSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { readHomeBootstrap(key) }.getOrElse { error ->
                if (!recoverFromCorruption(error)) runCatching { deleteHomeBootstrap(key) }
                null
            }
        }
    }

    override suspend fun deleteHomeBootstrap(
        key: HomeBootstrapKey,
        isCurrent: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!isCurrent()) return@runCatching false
                val db = helper.writableDatabase
                db.beginTransaction()
                try {
                    deleteHomeBootstrapRows(db, key)
                    if (!isCurrent()) {
                        false
                    } else {
                        db.setTransactionSuccessful()
                        true
                    }
                } finally {
                    db.endTransaction()
                }
            }.getOrElse { error ->
                recoverFromCorruption(error)
                false
            }
        }
    }

    override suspend fun saveHomeBootstrap(
        key: HomeBootstrapKey,
        snapshot: HomeBootstrapSnapshot,
        isCurrent: () -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!validateHomeBootstrap(snapshot)) return@withContext false
        mutex.withLock {
            runCatching {
                if (!isCurrent()) return@runCatching false
                val db = helper.writableDatabase
                db.beginTransaction()
                try {
                    deleteHomeBootstrapRows(db, key)
                    val completedAt = snapshot.completedAtEpochMs
                        .takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    db.insertOrThrow(
                        "home_bootstrap",
                        null,
                        ContentValues().apply {
                            put("account_id", key.accountId)
                            put("provider_id", key.providerId)
                            put("profile_id", key.profileId)
                            put("schema_version", CATALOG_CACHE_SCHEMA_VERSION)
                            put("completed", 1)
                            put("expected_type_count", snapshot.catalogs.size)
                            put("personalization_key", snapshot.personalizationKey)
                            put("updated_at", completedAt)
                        },
                    )
                    snapshot.catalogs.forEach { (type, catalog) ->
                        db.insertOrThrow(
                            "home_bootstrap_type",
                            null,
                            ContentValues().apply {
                                put("account_id", key.accountId)
                                put("provider_id", key.providerId)
                                put("profile_id", key.profileId)
                                put("content_type", type.name)
                                put("source_generation", snapshot.sourceGenerations[type] ?: 0L)
                                put("item_count", catalog.items.size)
                                put("completed", 1)
                            },
                        )
                        catalog.items.forEachIndexed { position, item ->
                            db.insertOrThrow(
                                "home_bootstrap_item",
                                null,
                                itemValues(key, type, position, item),
                            )
                        }
                    }
                    if (!isCurrent()) {
                        false
                    } else {
                        db.setTransactionSuccessful()
                        true
                    }
                } finally {
                    db.endTransaction()
                }
            }.getOrElse { error ->
                recoverFromCorruption(error)
                false
            }
        }
    }

    override suspend fun prune() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val db = helper.writableDatabase
                    val cutoff = (System.currentTimeMillis() - CATALOG_CACHE_RETENTION_MS).toString()
                    // Delete child rows before their metadata so expiry cannot leak catalog items.
                    db.delete(
                        "catalog_item",
                        "EXISTS (SELECT 1 FROM catalog_snapshot s " +
                            "WHERE s.account_id = catalog_item.account_id " +
                            "AND s.provider_id = catalog_item.provider_id " +
                            "AND s.content_type = catalog_item.content_type " +
                            "AND s.updated_at < ?)",
                        arrayOf(cutoff),
                    )
                    db.delete(
                        "catalog_category",
                        "EXISTS (SELECT 1 FROM catalog_snapshot s " +
                            "WHERE s.account_id = catalog_category.account_id " +
                            "AND s.provider_id = catalog_category.provider_id " +
                            "AND s.content_type = catalog_category.content_type " +
                            "AND s.updated_at < ?)",
                        arrayOf(cutoff),
                    )
                    db.delete("catalog_snapshot", "updated_at < ?", arrayOf(cutoff))
                    db.delete(
                        "home_bootstrap_item",
                        "EXISTS (SELECT 1 FROM home_bootstrap h " +
                            "WHERE h.account_id = home_bootstrap_item.account_id " +
                            "AND h.provider_id = home_bootstrap_item.provider_id " +
                            "AND h.profile_id = home_bootstrap_item.profile_id " +
                            "AND h.updated_at < ?)",
                        arrayOf(cutoff),
                    )
                    db.delete(
                        "home_bootstrap_type",
                        "EXISTS (SELECT 1 FROM home_bootstrap h " +
                            "WHERE h.account_id = home_bootstrap_type.account_id " +
                            "AND h.provider_id = home_bootstrap_type.provider_id " +
                            "AND h.profile_id = home_bootstrap_type.profile_id " +
                            "AND h.updated_at < ?)",
                        arrayOf(cutoff),
                    )
                    db.delete("home_bootstrap", "updated_at < ?", arrayOf(cutoff))
                    pruneScopes(db)
                }
            }
        }
    }

    /**
     * A corrupt cache is disposable. Close the open connection and delete the database so the next
     * access starts from a clean cache; the live path already treats the failure as a miss.
     */
    private fun recoverFromCorruption(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is SQLiteDatabaseCorruptException) {
                runCatching { helper.close() }
                runCatching { appContext.deleteDatabase(CATALOG_CACHE_DB_NAME) }
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun readSnapshot(key: CatalogSnapshotKey): CatalogSnapshot? {
        val db = helper.readableDatabase
        val stored = db.query(
            "catalog_snapshot",
            arrayOf("generation", "schema_version", "completed", "item_count", "category_count", "updated_at"),
            "account_id = ? AND provider_id = ? AND content_type = ?",
            arrayOf(key.accountId, key.providerId, key.contentType.name),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            if (cursor.getInt(1) != CATALOG_CACHE_SCHEMA_VERSION || cursor.getInt(2) != 1) {
                null
            } else {
                val generation = cursor.getLong(0)
                val itemCount = cursor.getInt(3)
                val categoryCount = cursor.getInt(4)
                val completedAt = cursor.getLong(5)
                StoredSnapshot(generation, itemCount, categoryCount, completedAt)
            }
        }
        val snapshot = stored ?: run {
            deleteSnapshot(key)
            return null
        }
        if (isCatalogCacheExpired(snapshot.completedAtEpochMs)) {
            deleteSnapshot(key)
            return null
        }
        val categories = readCategories(db, key, snapshot.generation)
        val items = readItems(db, key, snapshot.generation)
        if (categories.size != snapshot.categoryCount || items.size != snapshot.itemCount) {
            deleteSnapshot(key)
            return null
        }
        val catalog = Catalog(categories = categories, items = items)
        if (!validateCatalogSnapshot(key.contentType, catalog)) {
            deleteSnapshot(key)
            return null
        }
        return CatalogSnapshot(catalog, snapshot.generation, snapshot.completedAtEpochMs)
    }

    private fun readHomeBootstrap(key: HomeBootstrapKey): HomeBootstrapSnapshot? {
        val db = helper.readableDatabase
        val stored = db.query(
            "home_bootstrap",
            arrayOf("schema_version", "completed", "expected_type_count", "personalization_key", "updated_at"),
            "account_id = ? AND provider_id = ? AND profile_id = ?",
            arrayOf(key.accountId, key.providerId, key.profileId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            if (cursor.getInt(0) != CATALOG_CACHE_SCHEMA_VERSION || cursor.getInt(1) != 1) {
                null
            } else {
                HomeMeta(
                    expectedTypeCount = cursor.getInt(2),
                    personalizationKey = cursor.getString(3).orEmpty(),
                    completedAtEpochMs = cursor.getLong(4),
                )
            }
        }
        val meta = stored ?: run {
            deleteHomeBootstrap(key)
            return null
        }
        if (isCatalogCacheExpired(meta.completedAtEpochMs) || meta.personalizationKey.isBlank()) {
            deleteHomeBootstrap(key)
            return null
        }
        val typeRows = readHomeBootstrapTypes(db, key)
        if (typeRows.size != meta.expectedTypeCount) {
            deleteHomeBootstrap(key)
            return null
        }
        val catalogs = LinkedHashMap<ContentType, Catalog>()
        val sourceGenerations = LinkedHashMap<ContentType, Long>()
        typeRows.forEach { (type, typeMeta) ->
            val items = readHomeItems(db, key, type)
            val catalog = Catalog(categories = emptyList(), items = items)
            if (
                items.isEmpty() ||
                items.size != typeMeta.itemCount ||
                items.size > HOME_CATALOG_LIMIT ||
                !validateCatalogSnapshot(type, catalog)
            ) {
                deleteHomeBootstrap(key)
                return null
            }
            catalogs[type] = catalog
            sourceGenerations[type] = typeMeta.sourceGeneration
        }
        if (
            !validateHomeBootstrapTypeMetadata(
                expectedItemCounts = typeRows.mapValues { it.value.itemCount },
                sourceGenerations = sourceGenerations,
                catalogs = catalogs,
            )
        ) {
            deleteHomeBootstrap(key)
            return null
        }
        return HomeBootstrapSnapshot(
            catalogs = catalogs,
            sourceGenerations = sourceGenerations,
            personalizationKey = meta.personalizationKey,
            completedAtEpochMs = meta.completedAtEpochMs,
        )
    }

    private fun readHomeBootstrapTypes(
        db: SQLiteDatabase,
        key: HomeBootstrapKey,
    ): Map<ContentType, HomeTypeMeta> {
        val result = LinkedHashMap<ContentType, HomeTypeMeta>()
        db.query(
            "home_bootstrap_type",
            arrayOf("content_type", "source_generation", "item_count", "completed"),
            "account_id = ? AND provider_id = ? AND profile_id = ?",
            arrayOf(key.accountId, key.providerId, key.profileId),
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = runCatching { ContentType.valueOf(cursor.getString(0)) }.getOrNull()
                    ?: continue
                if (cursor.getInt(3) != 1) continue
                result[type] = HomeTypeMeta(
                    sourceGeneration = cursor.getLong(1),
                    itemCount = cursor.getInt(2),
                )
            }
        }
        return result
    }

    private fun readCategories(db: SQLiteDatabase, key: CatalogSnapshotKey, generation: Long): List<Category> {
        val result = ArrayList<Category>()
        db.query(
            "catalog_category",
            arrayOf("id", "name", "type"),
            "account_id = ? AND provider_id = ? AND content_type = ? AND generation = ?",
            arrayOf(key.accountId, key.providerId, key.contentType.name, generation.toString()),
            null,
            null,
            "position ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Category(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    type = ContentType.valueOf(cursor.getString(2)),
                )
            }
        }
        return result
    }

    private fun readItems(db: SQLiteDatabase, key: CatalogSnapshotKey, generation: Long): List<ContentItem> {
        val result = ArrayList<ContentItem>()
        db.query(
            "catalog_item",
            ITEM_COLUMNS,
            "account_id = ? AND provider_id = ? AND content_type = ? AND generation = ?",
            arrayOf(key.accountId, key.providerId, key.contentType.name, generation.toString()),
            null,
            null,
            "position ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += readItem(cursor)
        }
        return result
    }

    private fun readHomeItems(db: SQLiteDatabase, key: HomeBootstrapKey, type: ContentType): List<ContentItem> {
        val result = ArrayList<ContentItem>()
        db.query(
            "home_bootstrap_item",
            ITEM_COLUMNS,
            "account_id = ? AND provider_id = ? AND profile_id = ? AND content_type = ?",
            arrayOf(key.accountId, key.providerId, key.profileId, type.name),
            null,
            null,
            "position ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result += readItem(cursor)
        }
        return result
    }

    private fun readItem(cursor: Cursor): ContentItem = ContentItem(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("item_id")),
        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
        categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
        type = ContentType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
        posterUrl = cursor.getNullableString("poster_url"),
        rating = cursor.getNullableString("rating"),
        year = cursor.getNullableString("year"),
        containerExtension = cursor.getNullableString("container_extension"),
        nowPlaying = cursor.getNullableString("now_playing"),
        addedAtEpochSeconds = cursor.getNullableLong("added_at"),
        plot = cursor.getNullableString("plot"),
        genre = cursor.getNullableString("genre"),
        backdropUrl = cursor.getNullableString("backdrop_url"),
    )

    private fun insertSnapshot(
        db: SQLiteDatabase,
        key: CatalogSnapshotKey,
        catalog: Catalog,
        generation: Long,
        now: Long,
    ) {
        db.insertOrThrow(
            "catalog_snapshot",
            null,
            ContentValues().apply {
                put("account_id", key.accountId)
                put("provider_id", key.providerId)
                put("content_type", key.contentType.name)
                put("generation", generation)
                put("schema_version", CATALOG_CACHE_SCHEMA_VERSION)
                put("completed", 1)
                put("item_count", catalog.items.size)
                put("category_count", catalog.categories.size)
                put("updated_at", now)
            },
        )
        catalog.categories.forEachIndexed { position, category ->
            db.insertOrThrow(
                "catalog_category",
                null,
                ContentValues().apply {
                    put("account_id", key.accountId)
                    put("provider_id", key.providerId)
                    put("content_type", key.contentType.name)
                    put("generation", generation)
                    put("position", position)
                    put("id", category.id)
                    put("name", category.name)
                    put("type", category.type.name)
                },
            )
        }
        catalog.items.forEachIndexed { position, item ->
            db.insertOrThrow(
                "catalog_item",
                null,
                itemValues(key, key.contentType, position, item).apply {
                    put("generation", generation)
                },
            )
        }
    }

    private fun itemValues(
        key: CatalogSnapshotKey,
        type: ContentType,
        position: Int,
        item: ContentItem,
    ): ContentValues = ContentValues().apply {
        put("account_id", key.accountId)
        put("provider_id", key.providerId)
        put("content_type", type.name)
        put("position", position)
        put("item_id", item.id)
        put("name", item.name)
        put("category_id", item.categoryId)
        put("type", item.type.name)
        put("poster_url", item.posterUrl)
        put("rating", item.rating)
        put("year", item.year)
        put("container_extension", item.containerExtension)
        put("now_playing", item.nowPlaying)
        put("added_at", item.addedAtEpochSeconds)
        put("plot", item.plot)
        put("genre", item.genre)
        put("backdrop_url", item.backdropUrl)
    }

    private fun itemValues(
        key: HomeBootstrapKey,
        type: ContentType,
        position: Int,
        item: ContentItem,
    ): ContentValues = ContentValues().apply {
        put("account_id", key.accountId)
        put("provider_id", key.providerId)
        put("profile_id", key.profileId)
        put("content_type", type.name)
        put("position", position)
        put("item_id", item.id)
        put("name", item.name)
        put("category_id", item.categoryId)
        put("type", item.type.name)
        put("poster_url", item.posterUrl)
        put("rating", item.rating)
        put("year", item.year)
        put("container_extension", item.containerExtension)
        put("now_playing", item.nowPlaying)
        put("added_at", item.addedAtEpochSeconds)
        put("plot", item.plot)
        put("genre", item.genre)
        put("backdrop_url", item.backdropUrl)
    }

    private fun nextGeneration(db: SQLiteDatabase, key: CatalogSnapshotKey): Long =
        db.query(
            "catalog_snapshot",
            arrayOf("generation"),
            "account_id = ? AND provider_id = ? AND content_type = ?",
            arrayOf(key.accountId, key.providerId, key.contentType.name),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) + 1L else 1L }

    private fun deleteSnapshotRows(db: SQLiteDatabase, key: CatalogSnapshotKey) {
        val selection = "account_id = ? AND provider_id = ? AND content_type = ?"
        val args = arrayOf(key.accountId, key.providerId, key.contentType.name)
        db.delete("catalog_item", selection, args)
        db.delete("catalog_category", selection, args)
        db.delete("catalog_snapshot", selection, args)
    }

    private fun deleteSnapshot(key: CatalogSnapshotKey) {
        val db = helper.writableDatabase
        deleteSnapshotRows(db, key)
    }

    private fun deleteHomeBootstrapRows(db: SQLiteDatabase, key: HomeBootstrapKey) {
        val selection = "account_id = ? AND provider_id = ? AND profile_id = ?"
        val args = arrayOf(key.accountId, key.providerId, key.profileId)
        db.delete("home_bootstrap_item", selection, args)
        db.delete("home_bootstrap_type", selection, args)
        db.delete("home_bootstrap", selection, args)
    }

    private fun deleteHomeBootstrap(key: HomeBootstrapKey) {
        val db = helper.writableDatabase
        deleteHomeBootstrapRows(db, key)
    }

    private fun pruneScopes(db: SQLiteDatabase) {
        val scopes = ArrayList<Pair<String, String>>()
        db.rawQuery(
            "SELECT account_id, provider_id, MAX(updated_at) AS newest FROM catalog_snapshot " +
                "GROUP BY account_id, provider_id ORDER BY newest DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) scopes += cursor.getString(0) to cursor.getString(1)
        }
        scopes.drop(CATALOG_CACHE_MAX_SCOPES).forEach { (accountId, providerId) ->
            val args = arrayOf(accountId, providerId)
            db.delete("catalog_item", "account_id = ? AND provider_id = ?", args)
            db.delete("catalog_category", "account_id = ? AND provider_id = ?", args)
            db.delete("catalog_snapshot", "account_id = ? AND provider_id = ?", args)
            db.delete("home_bootstrap_item", "account_id = ? AND provider_id = ?", args)
            db.delete("home_bootstrap_type", "account_id = ? AND provider_id = ?", args)
            db.delete("home_bootstrap", "account_id = ? AND provider_id = ?", args)
        }
    }

    private data class StoredSnapshot(
        val generation: Long,
        val itemCount: Int,
        val categoryCount: Int,
        val completedAtEpochMs: Long,
    )

    private data class HomeMeta(
        val expectedTypeCount: Int,
        val personalizationKey: String,
        val completedAtEpochMs: Long,
    )

    private data class HomeTypeMeta(
        val sourceGeneration: Long,
        val itemCount: Int,
    )

    private class CatalogCacheDatabase(context: Context) : SQLiteOpenHelper(
        context,
        CATALOG_CACHE_DB_NAME,
        null,
        CATALOG_CACHE_SCHEMA_VERSION,
    ) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE catalog_snapshot (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    generation INTEGER NOT NULL,
                    schema_version INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    item_count INTEGER NOT NULL,
                    category_count INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (account_id, provider_id, content_type)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE catalog_category (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    generation INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    PRIMARY KEY (account_id, provider_id, content_type, generation, position)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE catalog_item (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    generation INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    category_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    poster_url TEXT,
                    rating TEXT,
                    year TEXT,
                    container_extension TEXT,
                    now_playing TEXT,
                    added_at INTEGER,
                    plot TEXT,
                    genre TEXT,
                    backdrop_url TEXT,
                    PRIMARY KEY (account_id, provider_id, content_type, generation, position)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE home_bootstrap (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    profile_id TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    expected_type_count INTEGER NOT NULL,
                    personalization_key TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (account_id, provider_id, profile_id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE home_bootstrap_type (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    profile_id TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    source_generation INTEGER NOT NULL,
                    item_count INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    PRIMARY KEY (account_id, provider_id, profile_id, content_type)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE home_bootstrap_item (
                    account_id TEXT NOT NULL,
                    provider_id TEXT NOT NULL,
                    profile_id TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    category_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    poster_url TEXT,
                    rating TEXT,
                    year TEXT,
                    container_extension TEXT,
                    now_playing TEXT,
                    added_at INTEGER,
                    plot TEXT,
                    genre TEXT,
                    backdrop_url TEXT,
                    PRIMARY KEY (account_id, provider_id, profile_id, content_type, position)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX catalog_snapshot_updated ON catalog_snapshot(updated_at)")
            db.execSQL("CREATE INDEX home_bootstrap_updated ON home_bootstrap(updated_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // The cache is disposable. A schema change is a safe cache miss.
            db.execSQL("DROP TABLE IF EXISTS home_bootstrap_item")
            db.execSQL("DROP TABLE IF EXISTS home_bootstrap_type")
            db.execSQL("DROP TABLE IF EXISTS home_bootstrap")
            db.execSQL("DROP TABLE IF EXISTS catalog_item")
            db.execSQL("DROP TABLE IF EXISTS catalog_category")
            db.execSQL("DROP TABLE IF EXISTS catalog_snapshot")
            onCreate(db)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onUpgrade(db, oldVersion, newVersion)
        }
    }

    private companion object {
        const val CATALOG_CACHE_DB_NAME = "hulk_catalog_cache_v1.db"
        val ITEM_COLUMNS = arrayOf(
            "item_id", "name", "category_id", "type", "poster_url", "rating", "year",
            "container_extension", "now_playing", "added_at", "plot", "genre", "backdrop_url",
        )
    }
}

private fun Cursor.getNullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.getNullableLong(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

package sa.hulksa.player.ui.screens

import java.security.MessageDigest
import java.util.EnumMap
import java.util.PriorityQueue
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

internal const val HOME_RECENT_LIMIT = 240
internal const val HOME_RATED_LIMIT = 120
internal const val HOME_CATALOG_LIMIT = 320

/**
 * Keeps watched series parents in the compacted Home catalog.
 *
 * A Series history key identifies the watched episode (`SERIES:<episodeId>`), while the catalog
 * card is identified by its parent series (`SERIES:<seriesId>`). Only a valid persisted parent id
 * may bridge those identities; an episode id is never treated as a series id.
 */
internal fun compactHomeCatalogForHome(
    catalog: Catalog,
    favorites: Set<String>,
    history: List<HistoryEntry>,
): Catalog {
    if (catalog.items.size <= HOME_CATALOG_LIMIT) return catalog
    val pinned = favorites + history.asSequence().mapNotNull(HistoryEntry::catalogPinnedKey).toSet()
    // One source pass feeds the pinned prefix and both bounded rankings. The previous shape
    // scanned the complete catalog three times and parsed every rating twice.
    val pinnedItems = ArrayList<ContentItem>()
    val recent = TopCatalogItems(HOME_RECENT_LIMIT)
    val rated = TopCatalogItems(HOME_RATED_LIMIT)
    catalog.items.forEachIndexed { index, item ->
        if (catalogKey(item) in pinned) pinnedItems += item
        recent.offer(item, index, item.addedAtEpochSeconds?.toDouble() ?: 0.0)
        item.rating?.toDoubleOrNull()?.let { score -> rated.offer(item, index, score) }
    }
    val retained = buildList {
        addAll(pinnedItems)
        addAll(recent.sorted())
        addAll(rated.sorted())
    }.distinctBy(::catalogKey).take(HOME_CATALOG_LIMIT)
    return catalog.copy(items = retained)
}

/**
 * Profile-owned bounded memo for Home compaction. Each Movie/Series type keeps its own entry, so
 * alternating catalog refreshes do not evict each other. Catalog identity is the revision boundary;
 * favorites/history equality only skips a rebuild after a profile-library reload returned the same
 * content. The full scan runs outside the shared monitor, and a clear invalidates late results.
 */
internal class HomeCompactionCache(
    private val builder: (Catalog, Set<String>, List<HistoryEntry>) -> Catalog =
        ::compactHomeCatalogForHome,
) {
    private class Key(
        val type: ContentType,
        val catalog: Catalog,
        val favorites: Set<String>,
        val history: List<HistoryEntry>,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Key &&
                type == other.type &&
                catalog === other.catalog &&
                favorites == other.favorites &&
                history == other.history

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + System.identityHashCode(catalog)
            result = 31 * result + favorites.hashCode()
            return 31 * result + history.hashCode()
        }
    }

    private class Entry(val key: Key, val result: Catalog)

    private val lock = Any()
    private val entries = EnumMap<ContentType, Entry>(ContentType::class.java)
    private val latestKeys = EnumMap<ContentType, Key>(ContentType::class.java)
    private val typeGenerations = EnumMap<ContentType, Long>(ContentType::class.java)
    private var epoch = 0L

    fun compact(
        type: ContentType,
        catalog: Catalog,
        favorites: Set<String>,
        history: List<HistoryEntry>,
    ): Catalog {
        val key = Key(type, catalog, favorites, history)
        // Capture invalidation before the cache check: a clear that races this call must prevent
        // this computation from repopulating the cache even if it starts after the check.
        val startEpoch = synchronized(lock) { epoch }
        synchronized(lock) {
            entries[type]?.takeIf { it.key == key }?.let { return it.result }
        }
        val requestGeneration = synchronized(lock) {
            val generation = (typeGenerations[type] ?: 0L) + 1L
            typeGenerations[type] = generation
            latestKeys[type] = key
            generation
        }
        val result = builder(catalog, favorites, history)
        synchronized(lock) {
            if (
                epoch == startEpoch &&
                typeGenerations[type] == requestGeneration &&
                latestKeys[type] == key
            ) {
                entries[type] = Entry(key, result)
            }
        }
        return result
    }

    fun clear() {
        synchronized(lock) {
            epoch += 1L
            entries.clear()
            latestKeys.clear()
            typeGenerations.clear()
        }
    }
}

/**
 * Stable bounded identity of the profile-owned inputs that shape the Home seed: favorites and the
 * history keys that can pin a catalog card. It deliberately excludes playback positions and
 * unrelated metadata so a seed is rejected/reconciled only when its selection inputs changed.
 */
internal fun homeBootstrapPersonalizationKey(
    favorites: Set<String>,
    history: List<HistoryEntry>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
    }
    favorites.sorted().forEach(::update)
    history.asSequence()
        .mapNotNull(HistoryEntry::catalogPinnedKey)
        .distinct()
        .sorted()
        .forEach(::update)
    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

/**
 * Bounded Live seed that keeps current favorite/history channels even when they are beyond the
 * provider prefix that used to define the first 320 Home Live items.
 */
internal fun compactHomeLiveCatalogForHome(
    catalog: Catalog,
    favorites: Set<String>,
    history: List<HistoryEntry>,
): Catalog {
    val pinned = HashSet<String>()
    favorites.filterTo(pinned) { it.startsWith("${ContentType.LIVE.name}:") }
    history.asSequence()
        .filter { it.isLive }
        .mapNotNull { entry ->
            entry.catalogPinnedKey() ?: entry.streamId
                .takeIf { it > 0 }
                ?.let { "LIVE:$it" }
        }
        .forEach(pinned::add)
    val pinnedItems = catalog.items.filter { catalogKey(it) in pinned }
    val pinnedKeys = pinnedItems.mapTo(HashSet(), ::catalogKey)
    val remaining = catalog.items.filter { catalogKey(it) !in pinnedKeys }
    return catalog.copy(
        items = (pinnedItems + remaining).distinctBy(::catalogKey).take(HOME_CATALOG_LIMIT),
    )
}

/**
 * Re-applies the current profile pins to a bounded seed when its stored personalization identity
 * changed. New pins outside the bounded seed are intentionally left to the complete cached type
 * restore that follows; old pins are never presented as current.
 */
/**
 * Splits a candidate Home seed into healthy, confirmed-empty and unbound types.
 *
 * Confirmed-empty types are omitted from the Home seed and its metadata; the complete full-catalog
 * cache still keeps their real zero counts. An authoritative all-empty selection is represented by
 * empty [catalogs] plus a non-empty [confirmedEmptyTypes], allowing the caller to delete an obsolete
 * nonempty Home seed. Unbound types (no positive source revision) block that destructive deletion.
 */
internal data class HomeBootstrapSeedSelection(
    val catalogs: Map<ContentType, Catalog>,
    val sourceGenerations: Map<ContentType, Long>,
    val confirmedEmptyTypes: Set<ContentType>,
    val unboundTypes: Set<ContentType>,
    val missingTypes: Set<ContentType>,
)

internal fun selectHomeBootstrapSeed(
    seeds: Map<ContentType, Catalog>,
    sourceGenerations: Map<ContentType, Long>,
    requiredTypes: Set<ContentType> = ContentType.entries.toSet(),
): HomeBootstrapSeedSelection {
    val catalogs = LinkedHashMap<ContentType, Catalog>()
    val generations = LinkedHashMap<ContentType, Long>()
    val confirmedEmpty = linkedSetOf<ContentType>()
    val unbound = linkedSetOf<ContentType>()
    seeds.forEach { (type, catalog) ->
        val generation = sourceGenerations[type] ?: 0L
        when {
            generation <= 0L -> unbound += type
            catalog.items.isEmpty() -> confirmedEmpty += type
            else -> {
                catalogs[type] = catalog
                generations[type] = generation
            }
        }
    }
    return HomeBootstrapSeedSelection(
        catalogs = catalogs,
        sourceGenerations = generations,
        confirmedEmptyTypes = confirmedEmpty,
        unboundTypes = unbound,
        missingTypes = requiredTypes - seeds.keys,
    )
}

internal fun reconcileHomeBootstrapCatalogs(
    catalogs: Map<ContentType, Catalog>,
    favorites: Set<String>,
    history: List<HistoryEntry>,
): Map<ContentType, Catalog> = catalogs.mapValues { (type, catalog) ->
    if (type == ContentType.LIVE) {
        compactHomeLiveCatalogForHome(catalog, favorites, history)
    } else {
        val pinned = favorites + history.asSequence()
            .mapNotNull(HistoryEntry::catalogPinnedKey)
            .toSet()
        val ordered = newest(catalog.items)
        val pinnedItems = ordered.filter { catalogKey(it) in pinned }
        val remaining = ordered.filter { catalogKey(it) !in pinned }
        catalog.copy(items = (pinnedItems + remaining).take(HOME_CATALOG_LIMIT))
    }
}

internal fun HistoryEntry.catalogPinnedKey(): String? = when (streamKind) {
    "series" -> parentContentId
        ?.takeIf { it > 0 }
        ?.let { "SERIES:$it" }
    else -> key.takeIf(String::isNotBlank)
}

private fun catalogKey(item: ContentItem): String = "${item.type.name}:${item.id}"

private data class RankedCatalogItem(
    val item: ContentItem,
    val sourceIndex: Int,
    val score: Double,
)

/**
 * Stable bounded top-[limit] ranking. Equivalent to
 * `items.sortedByDescending(scoreOf).take(limit)`, but O(n log limit); equal scores keep the
 * earlier source item, matching the stable full sort that defined the accepted Home order.
 */
private class TopCatalogItems(private val limit: Int) {
    private val heap = PriorityQueue<RankedCatalogItem>(limit.coerceAtLeast(1)) { left, right ->
        val byScore = compareValues(left.score, right.score)
        if (byScore != 0) byScore else compareValues(right.sourceIndex, left.sourceIndex)
    }

    fun offer(item: ContentItem, sourceIndex: Int, score: Double) {
        if (limit <= 0) return
        if (heap.size < limit) {
            heap.add(RankedCatalogItem(item, sourceIndex, score))
            return
        }
        val worst = heap.peek() ?: return
        val byScore = compareValues(score, worst.score)
        if (byScore > 0 || (byScore == 0 && sourceIndex < worst.sourceIndex)) {
            heap.poll()
            heap.add(RankedCatalogItem(item, sourceIndex, score))
        }
    }

    fun sorted(): List<ContentItem> = heap.sortedWith(
        compareByDescending<RankedCatalogItem> { it.score }.thenBy { it.sourceIndex },
    ).map(RankedCatalogItem::item)
}

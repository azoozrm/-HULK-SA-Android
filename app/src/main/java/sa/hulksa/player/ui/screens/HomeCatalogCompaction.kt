package sa.hulksa.player.ui.screens

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

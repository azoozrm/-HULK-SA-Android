package sa.hulksa.player.ui.screens

import java.util.PriorityQueue
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
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
    val retained = buildList {
        addAll(catalog.items.filter { catalogKey(it) in pinned })
        addAll(selectTop(catalog.items, HOME_RECENT_LIMIT) { it.addedAtEpochSeconds?.toDouble() ?: 0.0 })
        addAll(selectTop(catalog.items, HOME_RATED_LIMIT) { it.rating?.toDoubleOrNull() })
    }.distinctBy(::catalogKey).take(HOME_CATALOG_LIMIT)
    return catalog.copy(items = retained)
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
 * Stable top-[limit] selection equivalent to
 * `items.sortedByDescending(scoreOf).take(limit)`, but bounded to O(n log limit) and parsing each
 * score once. Equal scores keep the earlier source item, matching the stable full sort that
 * defined the accepted Home catalog order. A null score excludes the item (used by the rated
 * selection for unparseable ratings).
 */
private fun selectTop(
    items: List<ContentItem>,
    limit: Int,
    scoreOf: (ContentItem) -> Double?,
): List<ContentItem> {
    if (limit <= 0) return emptyList()
    val heap = PriorityQueue<RankedCatalogItem>(limit) { left, right ->
        val byScore = compareValues(left.score, right.score)
        if (byScore != 0) byScore else compareValues(right.sourceIndex, left.sourceIndex)
    }
    items.forEachIndexed { index, item ->
        val score = scoreOf(item) ?: return@forEachIndexed
        val candidate = RankedCatalogItem(item, index, score)
        if (heap.size < limit) {
            heap.add(candidate)
            return@forEachIndexed
        }
        val worst = heap.peek()
        val wins = compareValues(candidate.score, worst.score) > 0 ||
            (compareValues(candidate.score, worst.score) == 0 &&
                candidate.sourceIndex < worst.sourceIndex)
        if (wins) {
            heap.poll()
            heap.add(candidate)
        }
    }
    return heap.sortedWith(
        compareByDescending<RankedCatalogItem> { it.score }.thenBy { it.sourceIndex },
    ).map(RankedCatalogItem::item)
}

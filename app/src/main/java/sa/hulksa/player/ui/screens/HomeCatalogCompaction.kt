package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.Catalog
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
        addAll(catalog.items.filter { "${it.type.name}:${it.id}" in pinned })
        addAll(
            catalog.items.asSequence()
                .sortedByDescending { it.addedAtEpochSeconds ?: 0L }
                .take(HOME_RECENT_LIMIT),
        )
        addAll(
            catalog.items.asSequence()
                .filter { it.rating?.toDoubleOrNull() != null }
                .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                .take(HOME_RATED_LIMIT),
        )
    }.distinctBy { "${it.type.name}:${it.id}" }.take(HOME_CATALOG_LIMIT)
    return catalog.copy(items = retained)
}

internal fun HistoryEntry.catalogPinnedKey(): String? = when (streamKind) {
    "series" -> parentContentId
        ?.takeIf { it > 0 }
        ?.let { "SERIES:$it" }
    else -> key.takeIf(String::isNotBlank)
}

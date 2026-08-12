package sa.hulksa.player.ui

import sa.hulksa.player.data.VerifiedKidsCatalogSnapshot
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

enum class KidsSection {
    HOME,
    LIVE,
    MOVIES,
    SERIES,
    SEARCH,
}

internal fun availableKidsSections(snapshot: VerifiedKidsCatalogSnapshot): List<KidsSection> = buildList {
    add(KidsSection.HOME)
    if (snapshot.catalog(ContentType.LIVE).items.isNotEmpty()) add(KidsSection.LIVE)
    if (snapshot.catalog(ContentType.MOVIE).items.isNotEmpty()) add(KidsSection.MOVIES)
    if (snapshot.catalog(ContentType.SERIES).items.isNotEmpty()) add(KidsSection.SERIES)
    add(KidsSection.SEARCH)
}

internal fun kidsItemsForSection(
    snapshot: VerifiedKidsCatalogSnapshot,
    section: KidsSection,
    categoryId: String? = null,
    query: String = "",
): List<ContentItem> {
    val base = when (section) {
        KidsSection.HOME -> buildList {
            addAll(snapshot.catalog(ContentType.MOVIE).items.take(18))
            addAll(snapshot.catalog(ContentType.SERIES).items.take(18))
            addAll(snapshot.catalog(ContentType.LIVE).items.take(12))
        }
        KidsSection.LIVE -> snapshot.catalog(ContentType.LIVE).items
        KidsSection.MOVIES -> snapshot.catalog(ContentType.MOVIE).items
        KidsSection.SERIES -> snapshot.catalog(ContentType.SERIES).items
        KidsSection.SEARCH -> ContentType.entries.flatMap { snapshot.catalog(it).items }
    }
    val normalizedQuery = query.trim()
    return base.asSequence()
        .filter { item -> categoryId == null || item.categoryId == categoryId }
        .filter { item ->
            normalizedQuery.isBlank() ||
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                item.genre.orEmpty().contains(normalizedQuery, ignoreCase = true)
        }
        .distinctBy { "${it.type.name}:${it.id}" }
        .toList()
}

internal fun isVerifiedKidsItem(
    snapshot: VerifiedKidsCatalogSnapshot,
    item: ContentItem,
): Boolean = snapshot.catalog(item.type).items.any { candidate ->
    candidate.id == item.id && candidate.categoryId == item.categoryId
}

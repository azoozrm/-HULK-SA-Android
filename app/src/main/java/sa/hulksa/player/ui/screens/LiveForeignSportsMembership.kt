package sa.hulksa.player.ui.screens

import sa.hulksa.player.data.VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID
import sa.hulksa.player.model.ContentItem

/**
 * Shared membership for the main Live screen and the player channel browser.
 *
 * The app-only category is a view over the original provider records: item IDs, playback identity,
 * favorites and history remain the canonical provider identity. No duplicate catalog item is
 * created.
 */
internal fun liveCategoryItems(
    items: List<ContentItem>,
    categoryId: String?,
    virtualChannelIds: List<Int>,
): List<ContentItem> = when (categoryId) {
    VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID -> {
        val byId = items.associateBy(ContentItem::id)
        virtualChannelIds.mapNotNull(byId::get)
    }
    else -> items.filter { it.categoryId == categoryId }
}

/** Category matcher used by the Live projection; virtual membership wins before categoryId. */
internal fun liveCategoryMatches(
    item: ContentItem,
    selectedId: String?,
    virtualChannelIds: List<Int>,
    isFavorite: (ContentItem) -> Boolean,
): Boolean = when (selectedId) {
    VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID -> item.id in virtualChannelIds
    else -> categoryMatches(item, selectedId, isFavorite)
}

package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.providerContentIdentity
import sa.hulksa.player.data.providerStableIdentity
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

/**
 * Decorates only the Live catalog presented by MainShellScreen.
 *
 * The canonical repository catalog stays untouched. A synthetic "استكمال اخر مشاهدة" category is
 * injected for the main Live page. While that category is selected, recent channels temporarily
 * replace their canonical copies instead of being appended as duplicate provider identities.
 */
internal fun liveTvProDecorateMainState(
    state: HulkUiState,
    context: Context,
): HulkUiState {
    if (state.destination != MainDestination.LIVE) return state
    val liveCatalog = state.catalogs[ContentType.LIVE] ?: return state

    val recentCategory = Category(
        id = LIVE_TV_PRO_MAIN_RECENT_CATEGORY,
        name = "استكمال اخر مشاهدة",
        type = ContentType.LIVE,
    )
    val categories = (listOf(recentCategory) + liveCatalog.categories)
        .distinctBy(Category::id)

    val orderPrefs = context.applicationContext
        .getSharedPreferences("live_category_order", Context.MODE_PRIVATE)
    val currentOrder = orderPrefs.getString("ids", "")
        .orEmpty()
        .split(',')
        .filter(String::isNotBlank)
    if (currentOrder.firstOrNull() != LIVE_TV_PRO_MAIN_RECENT_CATEGORY) {
        val updatedOrder = listOf(LIVE_TV_PRO_MAIN_RECENT_CATEGORY) +
            currentOrder.filterNot { it == LIVE_TV_PRO_MAIN_RECENT_CATEGORY }
        orderPrefs.edit().putString("ids", updatedOrder.joinToString(",")).apply()
    }

    val presentedItems = if (state.selectedCategoryId == LIVE_TV_PRO_MAIN_RECENT_CATEGORY) {
        liveTvProRecentOverlayItems(
            items = liveCatalog.items,
            recentChannelIds = context.liveTvProRecentChannelIds(),
        )
    } else {
        liveCatalog.items
    }

    return state.copy(
        catalogs = state.catalogs + (
            ContentType.LIVE to Catalog(
                categories = categories,
                items = presentedItems,
            )
        ),
    )
}

/**
 * Builds the synthetic Recent view without ever exposing two rows with the same provider identity.
 *
 * Canonical provider duplicates are keep-first. The synthetic Recent copy then acts as a
 * deterministic override for that identity while this one derived view is active. This preserves
 * recent-channel ordering without using list indexes as identity.
 */
internal fun liveTvProRecentOverlayItems(
    items: List<ContentItem>,
    recentChannelIds: List<Int>,
): List<ContentItem> {
    if (items.isEmpty()) return emptyList()

    val canonicalByIdentity = linkedMapOf<String, ContentItem>()
    items.forEach { item ->
        val identity = item.providerStableIdentity()
        if (identity !in canonicalByIdentity) {
            canonicalByIdentity[identity] = item
        }
    }
    if (recentChannelIds.isEmpty()) return canonicalByIdentity.values.toList()

    val recentCopies = buildList {
        val seenRecent = hashSetOf<String>()
        recentChannelIds.forEach { streamId ->
            val channel = canonicalByIdentity[
                providerContentIdentity(ContentType.LIVE, streamId)
            ] ?: return@forEach
            val recentCopy = channel.copy(categoryId = LIVE_TV_PRO_MAIN_RECENT_CATEGORY)
            if (seenRecent.add(recentCopy.providerStableIdentity())) {
                add(recentCopy)
            }
        }
    }
    if (recentCopies.isEmpty()) return canonicalByIdentity.values.toList()

    val recentIdentities = recentCopies.mapTo(hashSetOf()) { it.providerStableIdentity() }
    return buildList(canonicalByIdentity.size) {
        canonicalByIdentity.values.forEach { item ->
            if (item.providerStableIdentity() !in recentIdentities) add(item)
        }
        addAll(recentCopies)
    }
}

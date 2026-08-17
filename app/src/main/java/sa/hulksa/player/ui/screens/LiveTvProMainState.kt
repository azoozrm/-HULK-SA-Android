package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentType

/**
 * Decorates only the Live catalog presented by MainShellScreen.
 *
 * The canonical repository catalog stays untouched. A synthetic "استكمال اخر مشاهدة" category is
 * injected for the main Live page and, only while that category is selected, recent-channel copies
 * are added beside the original catalog so category artwork/order stays intact.
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
        val byId = liveCatalog.items.associateBy { it.id }
        val recentCopies = context.liveTvProRecentChannelIds()
            .mapNotNull(byId::get)
            .map { channel -> channel.copy(categoryId = LIVE_TV_PRO_MAIN_RECENT_CATEGORY) }
        liveCatalog.items + recentCopies
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

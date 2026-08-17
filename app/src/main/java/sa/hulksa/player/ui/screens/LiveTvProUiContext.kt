package sa.hulksa.player.ui.screens

import android.content.Context

internal const val LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY = "__hulk_favorites__"
internal const val LIVE_TV_PRO_MAIN_CONTINUE_CATEGORY = "__hulk_continue__"
internal const val LIVE_TV_PRO_MAIN_RECENT_CATEGORY = "__live_tv_pro_recent__"
internal const val LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY = "__player_favorites__"
internal const val LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY = "__player_continue__"

internal const val LIVE_TV_PRO_CONTEXT_ALL = "__all__"
internal const val LIVE_TV_PRO_CONTEXT_FAVORITES = "__favorites__"
internal const val LIVE_TV_PRO_CONTEXT_RECENT = "__recent__"

private const val LIVE_TV_PRO_CONTEXT_PREFS = "live_player_context"
private const val LIVE_TV_PRO_CONTEXT_CATEGORY_KEY = "category_id"
private const val LIVE_TV_PRO_HISTORY_PREFS = "live_player_history"
private const val LIVE_TV_PRO_HISTORY_IDS_KEY = "ids"

internal fun liveTvProMainCategoryToContext(selectedCategoryId: String?): String = when (selectedCategoryId) {
    null -> LIVE_TV_PRO_CONTEXT_ALL
    LIVE_TV_PRO_MAIN_FAVORITES_CATEGORY -> LIVE_TV_PRO_CONTEXT_FAVORITES
    LIVE_TV_PRO_MAIN_CONTINUE_CATEGORY,
    LIVE_TV_PRO_MAIN_RECENT_CATEGORY,
    -> LIVE_TV_PRO_CONTEXT_RECENT
    else -> selectedCategoryId
}

internal fun liveTvProBrowserCategoryToContext(selectedCategoryId: String?): String = when (selectedCategoryId) {
    null -> LIVE_TV_PRO_CONTEXT_ALL
    LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> LIVE_TV_PRO_CONTEXT_FAVORITES
    LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> LIVE_TV_PRO_CONTEXT_RECENT
    else -> selectedCategoryId
}

internal fun liveTvProInitialBrowserCategory(
    launchContext: String?,
    currentCategoryId: String?,
    currentStreamId: Int,
    favoriteIds: Set<Int>,
    recentIds: List<Int>,
): String? = when (launchContext) {
    LIVE_TV_PRO_CONTEXT_FAVORITES -> {
        if (currentStreamId in favoriteIds) LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY else currentCategoryId
    }
    LIVE_TV_PRO_CONTEXT_RECENT -> {
        if (currentStreamId in recentIds) LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY else currentCategoryId
    }
    LIVE_TV_PRO_CONTEXT_ALL -> null
    null, "" -> currentCategoryId
    else -> if (launchContext == currentCategoryId) launchContext else currentCategoryId
}

internal fun Context.liveTvProRecentChannelIds(): List<Int> =
    applicationContext
        .getSharedPreferences(LIVE_TV_PRO_HISTORY_PREFS, Context.MODE_PRIVATE)
        .getString(LIVE_TV_PRO_HISTORY_IDS_KEY, "")
        .orEmpty()
        .split(',')
        .mapNotNull(String::toIntOrNull)
        .distinct()

internal fun Context.saveLiveTvProRecentChannelIds(ids: List<Int>) {
    applicationContext
        .getSharedPreferences(LIVE_TV_PRO_HISTORY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LIVE_TV_PRO_HISTORY_IDS_KEY, ids.distinct().joinToString(","))
        .apply()
}

internal fun Context.liveTvProLaunchContext(): String =
    applicationContext
        .getSharedPreferences(LIVE_TV_PRO_CONTEXT_PREFS, Context.MODE_PRIVATE)
        .getString(LIVE_TV_PRO_CONTEXT_CATEGORY_KEY, LIVE_TV_PRO_CONTEXT_ALL)
        .orEmpty()
        .ifBlank { LIVE_TV_PRO_CONTEXT_ALL }

internal fun Context.saveLiveTvProLaunchContext(contextValue: String) {
    applicationContext
        .getSharedPreferences(LIVE_TV_PRO_CONTEXT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LIVE_TV_PRO_CONTEXT_CATEGORY_KEY, contextValue)
        .apply()
}

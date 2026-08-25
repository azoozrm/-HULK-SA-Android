package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.data.AccountProfileStateScope
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy

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

internal fun decodeLiveTvProRecentChannelIds(raw: String?): List<Int> = raw
    .orEmpty()
    .split(',')
    .mapNotNull(String::toIntOrNull)
    .distinct()

internal fun encodeLiveTvProRecentChannelIds(ids: List<Int>): String =
    ids.distinct().joinToString(",")

private class LiveTvProfileStateStore(context: Context) {
    private val recentState = AccountProfileStateStore(
        context = context,
        basePreferencesName = LIVE_TV_PRO_HISTORY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )
    private val contextState = AccountProfileStateStore(
        context = context,
        basePreferencesName = LIVE_TV_PRO_CONTEXT_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )

    fun recentChannelIds(): List<Int> =
        decodeLiveTvProRecentChannelIds(recentState.read(LIVE_TV_PRO_HISTORY_IDS_KEY))

    fun activeScope(): AccountProfileStateScope? = recentState.activeScope()

    fun saveRecentChannelIds(ids: List<Int>) {
        recentState.write(
            key = LIVE_TV_PRO_HISTORY_IDS_KEY,
            value = encodeLiveTvProRecentChannelIds(ids),
        )
    }

    fun launchContext(): String = contextState.read(LIVE_TV_PRO_CONTEXT_CATEGORY_KEY)
        .orEmpty()
        .ifBlank { LIVE_TV_PRO_CONTEXT_ALL }

    fun saveLaunchContext(contextValue: String) {
        contextState.write(
            key = LIVE_TV_PRO_CONTEXT_CATEGORY_KEY,
            value = contextValue,
        )
    }

    fun removeProfile(accountId: String, profileId: String) {
        recentState.remove(accountId, profileId, LIVE_TV_PRO_HISTORY_IDS_KEY)
        contextState.remove(accountId, profileId, LIVE_TV_PRO_CONTEXT_CATEGORY_KEY)
    }

    companion object {
        @Volatile
        private var instance: LiveTvProfileStateStore? = null

        fun get(context: Context): LiveTvProfileStateStore = instance ?: synchronized(this) {
            instance ?: LiveTvProfileStateStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun Context.liveTvProRecentChannelIds(): List<Int> =
    LiveTvProfileStateStore.get(this).recentChannelIds()

internal fun Context.liveTvProStateScope(): AccountProfileStateScope? =
    LiveTvProfileStateStore.get(this).activeScope()

internal fun Context.saveLiveTvProRecentChannelIds(ids: List<Int>) {
    LiveTvProfileStateStore.get(this).saveRecentChannelIds(ids)
}

internal fun Context.liveTvProLaunchContext(): String =
    LiveTvProfileStateStore.get(this).launchContext()

internal fun Context.saveLiveTvProLaunchContext(contextValue: String) {
    LiveTvProfileStateStore.get(this).saveLaunchContext(contextValue)
}

internal fun Context.removeLiveTvProProfileState(accountId: String, profileId: String) {
    LiveTvProfileStateStore.get(this).removeProfile(accountId, profileId)
}

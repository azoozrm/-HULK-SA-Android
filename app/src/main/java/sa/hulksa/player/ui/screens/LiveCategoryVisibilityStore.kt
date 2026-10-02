package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy

private const val LIVE_CATEGORY_VISIBILITY_PREFS = "live_category_visibility"
private const val LIVE_CATEGORY_VISIBILITY_IDS_KEY = "hidden_ids"

internal fun decodeLiveHiddenCategoryIds(raw: String?): Set<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()

internal fun encodeLiveHiddenCategoryIds(ids: Set<String>): String =
    ids.filter(String::isNotBlank).sorted().joinToString(",")

private class LiveCategoryVisibilityStore(context: Context) {
    private val state = AccountProfileStateStore(
        context = context,
        basePreferencesName = LIVE_CATEGORY_VISIBILITY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )

    fun hiddenCategoryIds(): Set<String> =
        decodeLiveHiddenCategoryIds(state.read(LIVE_CATEGORY_VISIBILITY_IDS_KEY))

    fun saveHiddenCategoryIds(ids: Set<String>) {
        state.write(
            key = LIVE_CATEGORY_VISIBILITY_IDS_KEY,
            value = encodeLiveHiddenCategoryIds(ids),
        )
    }

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(accountId, profileId, LIVE_CATEGORY_VISIBILITY_IDS_KEY)
    }

    companion object {
        @Volatile
        private var instance: LiveCategoryVisibilityStore? = null

        fun get(context: Context): LiveCategoryVisibilityStore = instance ?: synchronized(this) {
            instance ?: LiveCategoryVisibilityStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun Context.liveHiddenCategoryIds(): Set<String> =
    LiveCategoryVisibilityStore.get(this).hiddenCategoryIds()

internal fun Context.saveLiveHiddenCategoryIds(ids: Set<String>) {
    LiveCategoryVisibilityStore.get(this).saveHiddenCategoryIds(ids)
}

internal fun Context.removeLiveCategoryVisibilityProfileState(accountId: String, profileId: String) {
    LiveCategoryVisibilityStore.get(this).removeProfile(accountId, profileId)
}

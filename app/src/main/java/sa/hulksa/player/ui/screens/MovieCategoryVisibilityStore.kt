package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy

private const val MOVIE_CATEGORY_VISIBILITY_PREFS = "movie_category_visibility"
private const val MOVIE_CATEGORY_VISIBILITY_IDS_KEY = "hidden_ids"

/**
 * Movie-only category visibility persistence.
 *
 * Uses its own preferences namespace and profile-scoped state store so Movie hidden/order state
 * can never read or overwrite the Live/Series visibility settings. The default (all included)
 * keeps the manager honest when no custom state exists.
 */
internal fun decodeMovieHiddenCategoryIds(raw: String?): Set<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()

internal fun encodeMovieHiddenCategoryIds(ids: Set<String>): String =
    ids.filter(String::isNotBlank).sorted().joinToString(",")

private class MovieCategoryVisibilityStore(context: Context) {
    private val state = AccountProfileStateStore(
        context = context,
        basePreferencesName = MOVIE_CATEGORY_VISIBILITY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )

    fun hiddenCategoryIds(): Set<String> =
        decodeMovieHiddenCategoryIds(state.read(MOVIE_CATEGORY_VISIBILITY_IDS_KEY))

    fun saveHiddenCategoryIds(ids: Set<String>) {
        state.write(
            key = MOVIE_CATEGORY_VISIBILITY_IDS_KEY,
            value = encodeMovieHiddenCategoryIds(ids),
        )
    }

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(accountId, profileId, MOVIE_CATEGORY_VISIBILITY_IDS_KEY)
    }

    companion object {
        @Volatile
        private var instance: MovieCategoryVisibilityStore? = null

        fun get(context: Context): MovieCategoryVisibilityStore = instance ?: synchronized(this) {
            instance ?: MovieCategoryVisibilityStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun Context.movieHiddenCategoryIds(): Set<String> =
    MovieCategoryVisibilityStore.get(this).hiddenCategoryIds()

internal fun Context.saveMovieHiddenCategoryIds(ids: Set<String>) {
    MovieCategoryVisibilityStore.get(this).saveHiddenCategoryIds(ids)
}

internal fun Context.removeMovieCategoryVisibilityProfileState(accountId: String, profileId: String) {
    MovieCategoryVisibilityStore.get(this).removeProfile(accountId, profileId)
}

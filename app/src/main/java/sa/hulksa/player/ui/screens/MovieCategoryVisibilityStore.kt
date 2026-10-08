package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.data.AccountProfileStateScope
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy
import sa.hulksa.player.model.Category

private const val MOVIE_CATEGORY_VISIBILITY_PREFS = "movie_category_visibility"
private const val MOVIE_CATEGORY_VISIBILITY_IDS_KEY = "hidden_ids"
private const val MOVIE_CATEGORY_ORDER_IDS_KEY = "order_ids"
private const val MOVIE_CATEGORY_ORDER_ADOPTION_PREFS = "movie_category_order_adoption_v1"
private const val MOVIE_CATEGORY_ORDER_ADOPTION_KEY = "legacy_adopted"
private const val LEGACY_MOVIE_CATEGORY_ORDER_PREFS = "catalog_category_order_MOVIE"
private const val LEGACY_MOVIE_CATEGORY_ORDER_IDS_KEY = "ids"

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

internal fun decodeMovieCategoryOrderIds(raw: String?): List<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun encodeMovieCategoryOrderIds(ids: List<String>): String =
    ids.filter(String::isNotBlank).distinct().joinToString(",")

/** Fixed semantic Movie rows that are never part of the server order, hide or reorder surface. */
internal fun isMovieCategorySpecialId(categoryId: String): Boolean =
    categoryId == FAVORITES_CATEGORY_ID || categoryId == CONTINUE_CATEGORY_ID

/**
 * The legacy global Movie order may contain synthetic fixed rows; only real server categories
 * participate in the one-time scoped adoption so a fixed id can never leak into the committed
 * server order.
 */
internal fun legacyMovieServerCategoryOrderIds(raw: String?): List<String> =
    decodeMovieCategoryOrderIds(raw).filterNot(::isMovieCategorySpecialId)

/**
 * Complete ordered server categories for the manager and the selector.
 *
 * Committed order wins, unavailable ids are ignored, fixed All/Favorites/Recent ids are excluded
 * and genuinely new server categories append in catalog order without resetting customization.
 */
internal fun orderedMovieServerCategories(
    categories: List<Category>,
    committedOrderIds: List<String>,
): List<Category> {
    val serverCategories = categories
        .filterNot { isMovieCategorySpecialId(it.id) }
        .distinctBy(Category::id)
    if (serverCategories.isEmpty()) return emptyList()
    val byId = serverCategories.associateBy(Category::id)
    val ordered = committedOrderIds.distinct().mapNotNull(byId::get)
    val orderedIds = ordered.mapTo(hashSetOf(), Category::id)
    return ordered + serverCategories.filterNot { it.id in orderedIds }
}

/**
 * Decides the one-time adoption of the legacy global Movie order.
 *
 * Returns the order to persist for the first verified active scope, or null when a scoped order
 * already exists or adoption already happened once. This is what prevents seeding other
 * accounts/profiles from the legacy value.
 */
internal fun movieCategoryOrderAdoption(
    scopedOrderIds: List<String>?,
    adoptionDone: Boolean,
    legacyOrderIds: List<String>,
): List<String>? = when {
    scopedOrderIds != null -> null
    adoptionDone -> null
    else -> legacyOrderIds
}

private class MovieCategoryVisibilityStore(context: Context) {
    private val appContext = context.applicationContext
    private val state = AccountProfileStateStore(
        context = appContext,
        basePreferencesName = MOVIE_CATEGORY_VISIBILITY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )
    private val adoptionState = appContext.getSharedPreferences(
        MOVIE_CATEGORY_ORDER_ADOPTION_PREFS,
        Context.MODE_PRIVATE,
    )

    fun hiddenCategoryIds(): Set<String> =
        decodeMovieHiddenCategoryIds(state.read(MOVIE_CATEGORY_VISIBILITY_IDS_KEY))

    fun saveHiddenCategoryIds(ids: Set<String>, scope: AccountProfileStateScope?) {
        val encoded = encodeMovieHiddenCategoryIds(ids)
        if (scope == null) {
            state.write(MOVIE_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        } else {
            state.write(scope, MOVIE_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        }
    }

    /**
     * Reads the committed server order for the active scope. Before the one-time adoption this is
     * the currently displayed legacy order and no write happens during composition.
     */
    fun committedCategoryOrderIds(): List<String> {
        state.read(MOVIE_CATEGORY_ORDER_IDS_KEY)
            ?.let { return decodeMovieCategoryOrderIds(it) }
        return movieCategoryOrderAdoption(
            scopedOrderIds = null,
            adoptionDone = adoptionState.getBoolean(MOVIE_CATEGORY_ORDER_ADOPTION_KEY, false),
            legacyOrderIds = legacyServerCategoryOrderIds(),
        ).orEmpty()
    }

    /**
     * Adopts the legacy displayed server order exactly once for the first verified active scope.
     * The legacy preference itself is retained untouched and no other account or profile is seeded.
     */
    fun adoptCommittedCategoryOrderOnce() {
        if (adoptionState.getBoolean(MOVIE_CATEGORY_ORDER_ADOPTION_KEY, false)) return
        val scope = state.activeScope() ?: return
        val adopted = movieCategoryOrderAdoption(
            scopedOrderIds = state.read(MOVIE_CATEGORY_ORDER_IDS_KEY)
                ?.let(::decodeMovieCategoryOrderIds),
            adoptionDone = false,
            legacyOrderIds = legacyServerCategoryOrderIds(),
        )
        if (adopted != null) {
            state.write(
                scope,
                MOVIE_CATEGORY_ORDER_IDS_KEY,
                encodeMovieCategoryOrderIds(adopted),
            )
        }
        adoptionState.edit().putBoolean(MOVIE_CATEGORY_ORDER_ADOPTION_KEY, true).apply()
    }

    fun saveCommittedCategoryOrderIds(scope: AccountProfileStateScope?, ids: List<String>) {
        val encoded = encodeMovieCategoryOrderIds(ids)
        if (scope == null) {
            state.write(MOVIE_CATEGORY_ORDER_IDS_KEY, encoded)
        } else {
            state.write(scope, MOVIE_CATEGORY_ORDER_IDS_KEY, encoded)
        }
    }

    fun activeScope(): AccountProfileStateScope? = state.activeScope()

    private fun legacyServerCategoryOrderIds(): List<String> = legacyMovieServerCategoryOrderIds(
        appContext.getSharedPreferences(LEGACY_MOVIE_CATEGORY_ORDER_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_MOVIE_CATEGORY_ORDER_IDS_KEY, null),
    )

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(
            accountId,
            profileId,
            MOVIE_CATEGORY_VISIBILITY_IDS_KEY,
            MOVIE_CATEGORY_ORDER_IDS_KEY,
        )
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

internal fun Context.saveMovieHiddenCategoryIds(
    ids: Set<String>,
    scope: AccountProfileStateScope? = null,
) {
    MovieCategoryVisibilityStore.get(this).saveHiddenCategoryIds(ids, scope)
}

internal fun Context.movieCommittedCategoryOrderIds(): List<String> =
    MovieCategoryVisibilityStore.get(this).committedCategoryOrderIds()

internal fun Context.adoptMovieCommittedCategoryOrderOnce() {
    MovieCategoryVisibilityStore.get(this).adoptCommittedCategoryOrderOnce()
}

internal fun Context.saveMovieCommittedCategoryOrderIds(
    scope: AccountProfileStateScope?,
    ids: List<String>,
) {
    MovieCategoryVisibilityStore.get(this).saveCommittedCategoryOrderIds(scope, ids)
}

internal fun Context.movieCategoryStateScope(): AccountProfileStateScope? =
    MovieCategoryVisibilityStore.get(this).activeScope()

internal fun Context.removeMovieCategoryVisibilityProfileState(accountId: String, profileId: String) {
    MovieCategoryVisibilityStore.get(this).removeProfile(accountId, profileId)
}

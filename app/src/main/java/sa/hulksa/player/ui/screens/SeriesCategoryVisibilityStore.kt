package sa.hulksa.player.ui.screens

import android.content.Context
import sa.hulksa.player.data.AccountProfileStateScope
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy
import sa.hulksa.player.model.Category

private const val SERIES_CATEGORY_VISIBILITY_PREFS = "series_category_visibility"
private const val SERIES_CATEGORY_VISIBILITY_IDS_KEY = "hidden_ids"
private const val SERIES_CATEGORY_ORDER_IDS_KEY = "order_ids"
private const val SERIES_CATEGORY_ORDER_ADOPTION_PREFS = "series_category_order_adoption_v1"
private const val SERIES_CATEGORY_ORDER_ADOPTION_KEY = "legacy_adopted"
private const val LEGACY_SERIES_CATEGORY_ORDER_PREFS = "catalog_category_order_SERIES"
private const val LEGACY_SERIES_CATEGORY_ORDER_IDS_KEY = "ids"

/**
 * Series-only category visibility persistence.
 *
 * Uses its own preferences namespace and profile-scoped state store so Series hidden/order state
 * can never read or overwrite the Live/Movie visibility settings. The default (all included)
 * keeps the manager honest when no custom state exists.
 */
internal fun decodeSeriesHiddenCategoryIds(raw: String?): Set<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()

internal fun encodeSeriesHiddenCategoryIds(ids: Set<String>): String =
    ids.filter(String::isNotBlank).sorted().joinToString(",")

internal fun decodeSeriesCategoryOrderIds(raw: String?): List<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun encodeSeriesCategoryOrderIds(ids: List<String>): String =
    ids.filter(String::isNotBlank).distinct().joinToString(",")

/** Fixed semantic Series rows that are never part of the server order, hide or reorder surface. */
internal fun isSeriesCategorySpecialId(categoryId: String): Boolean =
    categoryId == FAVORITES_CATEGORY_ID || categoryId == CONTINUE_CATEGORY_ID

/**
 * The legacy global Series order may contain synthetic fixed rows; only real server categories
 * participate in the one-time scoped adoption so a fixed id can never leak into the committed
 * server order.
 */
internal fun legacySeriesServerCategoryOrderIds(raw: String?): List<String> =
    decodeSeriesCategoryOrderIds(raw).filterNot(::isSeriesCategorySpecialId)

/**
 * Complete ordered server categories for the manager and the selector.
 *
 * Committed order wins, unavailable ids are ignored, fixed All/Favorites/Recent ids are excluded
 * and genuinely new server categories append in catalog order without resetting customization.
 */
internal fun orderedSeriesServerCategories(
    categories: List<Category>,
    committedOrderIds: List<String>,
): List<Category> {
    val serverCategories = categories
        .filterNot { isSeriesCategorySpecialId(it.id) }
        .distinctBy(Category::id)
    if (serverCategories.isEmpty()) return emptyList()
    val byId = serverCategories.associateBy(Category::id)
    val ordered = committedOrderIds.distinct().mapNotNull(byId::get)
    val orderedIds = ordered.mapTo(hashSetOf(), Category::id)
    return ordered + serverCategories.filterNot { it.id in orderedIds }
}

/**
 * Decides the one-time adoption of the legacy global Series order.
 *
 * Returns the order to persist for the first verified active scope, or null when a scoped order
 * already exists or adoption already happened once. This is what prevents seeding other
 * accounts/profiles from the legacy value.
 */
internal fun seriesCategoryOrderAdoption(
    scopedOrderIds: List<String>?,
    adoptionDone: Boolean,
    legacyOrderIds: List<String>,
): List<String>? = when {
    scopedOrderIds != null -> null
    adoptionDone -> null
    else -> legacyOrderIds
}

private class SeriesCategoryVisibilityStore(context: Context) {
    private val appContext = context.applicationContext
    private val state = AccountProfileStateStore(
        context = appContext,
        basePreferencesName = SERIES_CATEGORY_VISIBILITY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )
    private val adoptionState = appContext.getSharedPreferences(
        SERIES_CATEGORY_ORDER_ADOPTION_PREFS,
        Context.MODE_PRIVATE,
    )

    fun hiddenCategoryIds(): Set<String> =
        decodeSeriesHiddenCategoryIds(state.read(SERIES_CATEGORY_VISIBILITY_IDS_KEY))

    fun saveHiddenCategoryIds(ids: Set<String>, scope: AccountProfileStateScope?) {
        val encoded = encodeSeriesHiddenCategoryIds(ids)
        if (scope == null) {
            state.write(SERIES_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        } else {
            state.write(scope, SERIES_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        }
    }

    /**
     * Reads the committed server order for the active scope. Before the one-time adoption this is
     * the currently displayed legacy order and no write happens during composition.
     */
    fun committedCategoryOrderIds(): List<String> {
        state.read(SERIES_CATEGORY_ORDER_IDS_KEY)
            ?.let { return decodeSeriesCategoryOrderIds(it) }
        return seriesCategoryOrderAdoption(
            scopedOrderIds = null,
            adoptionDone = adoptionState.getBoolean(SERIES_CATEGORY_ORDER_ADOPTION_KEY, false),
            legacyOrderIds = legacyServerCategoryOrderIds(),
        ).orEmpty()
    }

    /**
     * Adopts the legacy displayed server order exactly once for the first verified active scope.
     * The legacy preference itself is retained untouched and no other account or profile is seeded.
     */
    fun adoptCommittedCategoryOrderOnce() {
        if (adoptionState.getBoolean(SERIES_CATEGORY_ORDER_ADOPTION_KEY, false)) return
        val scope = state.activeScope() ?: return
        val adopted = seriesCategoryOrderAdoption(
            scopedOrderIds = state.read(SERIES_CATEGORY_ORDER_IDS_KEY)
                ?.let(::decodeSeriesCategoryOrderIds),
            adoptionDone = false,
            legacyOrderIds = legacyServerCategoryOrderIds(),
        )
        if (adopted != null) {
            state.write(
                scope,
                SERIES_CATEGORY_ORDER_IDS_KEY,
                encodeSeriesCategoryOrderIds(adopted),
            )
        }
        adoptionState.edit().putBoolean(SERIES_CATEGORY_ORDER_ADOPTION_KEY, true).apply()
    }

    fun saveCommittedCategoryOrderIds(scope: AccountProfileStateScope?, ids: List<String>) {
        val encoded = encodeSeriesCategoryOrderIds(ids)
        if (scope == null) {
            state.write(SERIES_CATEGORY_ORDER_IDS_KEY, encoded)
        } else {
            state.write(scope, SERIES_CATEGORY_ORDER_IDS_KEY, encoded)
        }
    }

    fun activeScope(): AccountProfileStateScope? = state.activeScope()

    private fun legacyServerCategoryOrderIds(): List<String> = legacySeriesServerCategoryOrderIds(
        appContext.getSharedPreferences(LEGACY_SERIES_CATEGORY_ORDER_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_SERIES_CATEGORY_ORDER_IDS_KEY, null),
    )

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(
            accountId,
            profileId,
            SERIES_CATEGORY_VISIBILITY_IDS_KEY,
            SERIES_CATEGORY_ORDER_IDS_KEY,
        )
    }

    companion object {
        @Volatile
        private var instance: SeriesCategoryVisibilityStore? = null

        fun get(context: Context): SeriesCategoryVisibilityStore = instance ?: synchronized(this) {
            instance ?: SeriesCategoryVisibilityStore(context.applicationContext).also { instance = it }
        }
    }
}

internal fun Context.seriesHiddenCategoryIds(): Set<String> =
    SeriesCategoryVisibilityStore.get(this).hiddenCategoryIds()

internal fun Context.saveSeriesHiddenCategoryIds(
    ids: Set<String>,
    scope: AccountProfileStateScope? = null,
) {
    SeriesCategoryVisibilityStore.get(this).saveHiddenCategoryIds(ids, scope)
}

internal fun Context.seriesCommittedCategoryOrderIds(): List<String> =
    SeriesCategoryVisibilityStore.get(this).committedCategoryOrderIds()

internal fun Context.adoptSeriesCommittedCategoryOrderOnce() {
    SeriesCategoryVisibilityStore.get(this).adoptCommittedCategoryOrderOnce()
}

internal fun Context.saveSeriesCommittedCategoryOrderIds(
    scope: AccountProfileStateScope?,
    ids: List<String>,
) {
    SeriesCategoryVisibilityStore.get(this).saveCommittedCategoryOrderIds(scope, ids)
}

internal fun Context.seriesCategoryStateScope(): AccountProfileStateScope? =
    SeriesCategoryVisibilityStore.get(this).activeScope()

internal fun Context.removeSeriesCategoryVisibilityProfileState(accountId: String, profileId: String) {
    SeriesCategoryVisibilityStore.get(this).removeProfile(accountId, profileId)
}

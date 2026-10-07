package sa.hulksa.player.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sa.hulksa.player.data.AccountProfileStateScope
import sa.hulksa.player.data.AccountProfileStateStore
import sa.hulksa.player.data.LegacyProfileStatePolicy
import sa.hulksa.player.model.Category

private const val LIVE_CATEGORY_VISIBILITY_PREFS = "live_category_visibility"
private const val LIVE_CATEGORY_VISIBILITY_IDS_KEY = "hidden_ids"
private const val LIVE_CATEGORY_ORDER_IDS_KEY = "order_ids"
private const val LIVE_CATEGORY_ORDER_ADOPTION_PREFS = "live_category_order_adoption_v1"
private const val LIVE_CATEGORY_ORDER_ADOPTION_KEY = "legacy_adopted"
private const val LEGACY_LIVE_CATEGORY_ORDER_PREFS = "live_category_order"
private const val LEGACY_LIVE_CATEGORY_ORDER_IDS_KEY = "ids"

internal fun decodeLiveHiddenCategoryIds(raw: String?): Set<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()

internal fun encodeLiveHiddenCategoryIds(ids: Set<String>): String =
    ids.filter(String::isNotBlank).sorted().joinToString(",")

internal fun decodeLiveCategoryOrderIds(raw: String?): List<String> = raw
    .orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun encodeLiveCategoryOrderIds(ids: List<String>): String =
    ids.filter(String::isNotBlank).distinct().joinToString(",")

/**
 * The legacy global order may contain synthetic fixed rows (Recent pinning). Only real server
 * categories participate in the one-time scoped adoption so a fixed id can never leak into the
 * committed server order.
 */
internal fun legacyLiveServerCategoryOrderIds(raw: String?): List<String> =
    decodeLiveCategoryOrderIds(raw).filterNot(::isLiveCategorySpecialId)

/**
 * Complete ordered server categories for the manager and both selectors.
 *
 * Committed order wins, unavailable ids are ignored, fixed All/Favorites/Recent ids are excluded
 * and genuinely new server categories append in catalog order without resetting customization.
 */
internal fun orderedLiveServerCategories(
    categories: List<Category>,
    committedOrderIds: List<String>,
): List<Category> {
    val serverCategories = categories
        .filterNot { isLiveCategorySpecialId(it.id) }
        .distinctBy(Category::id)
    if (serverCategories.isEmpty()) return emptyList()
    val byId = serverCategories.associateBy(Category::id)
    val ordered = committedOrderIds.distinct().mapNotNull(byId::get)
    val orderedIds = ordered.mapTo(hashSetOf(), Category::id)
    return ordered + serverCategories.filterNot { it.id in orderedIds }
}

/**
 * Decides the one-time adoption of the legacy global order.
 *
 * Returns the order to persist for the first verified active scope, or null when a scoped order
 * already exists or adoption already happened once. This is what prevents seeding other
 * accounts/profiles from the legacy value.
 */
internal fun liveCategoryOrderAdoption(
    scopedOrderIds: List<String>?,
    adoptionDone: Boolean,
    legacyOrderIds: List<String>,
): List<String>? = when {
    scopedOrderIds != null -> null
    adoptionDone -> null
    else -> legacyOrderIds
}

/** Current laid-out row geometry for drag hit testing; offsets are relative to the list viewport. */
internal data class LiveCategoryRowGeometry(
    val key: String,
    val offset: Int,
    val size: Int,
)

/**
 * The stable id of the row actually displayed under [pointerY] in the current layout, or null in a
 * spacing gap or outside the rows. Only currently laid-out rows participate, so a disposed row's
 * stale geometry can never be targeted.
 */
internal fun liveCategoryDragTargetKey(
    rows: List<LiveCategoryRowGeometry>,
    pointerY: Float,
    viewportTop: Float,
): String? = rows.firstOrNull { row ->
    val top = viewportTop + row.offset
    pointerY >= top && pointerY <= top + row.size
}?.key

/**
 * Consumption-aware decisions for the Live manager touch gesture.
 *
 * Consumption always wins over release: a change that another owner consumed (including a
 * synthetic/cancelled release) must never toggle visibility, start a drag or commit an order.
 * Only a real, unconsumed release may complete an interaction.
 */
internal enum class LiveCategoryGestureDecision {
    CONTINUE,
    TOGGLE,
    DROP,
    CANCEL,
}

internal fun liveCategoryPreLongPressDecision(
    hasChange: Boolean,
    pressed: Boolean,
    consumed: Boolean,
    movedBeyondSlop: Boolean,
): LiveCategoryGestureDecision = when {
    !hasChange -> LiveCategoryGestureDecision.CANCEL
    consumed -> LiveCategoryGestureDecision.CANCEL
    !pressed -> LiveCategoryGestureDecision.TOGGLE
    movedBeyondSlop -> LiveCategoryGestureDecision.CANCEL
    else -> LiveCategoryGestureDecision.CONTINUE
}

internal fun liveCategoryDragDecision(
    hasChange: Boolean,
    pressed: Boolean,
    consumed: Boolean,
): LiveCategoryGestureDecision = when {
    !hasChange -> LiveCategoryGestureDecision.CANCEL
    consumed -> LiveCategoryGestureDecision.CANCEL
    !pressed -> LiveCategoryGestureDecision.DROP
    else -> LiveCategoryGestureDecision.CONTINUE
}

/** Moves one stable category id; unknown ids or an out-of-range target leave the order unchanged. */
internal fun moveLiveCategory(ids: List<String>, categoryId: String, targetIndex: Int): List<String> {
    val from = ids.indexOf(categoryId)
    if (from < 0) return ids
    val to = targetIndex.coerceIn(0, ids.lastIndex)
    if (to == from) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * One interaction draft for the Live manager.
 *
 * TV confirmation and phone drop commit the draft exactly once; BACK/cancellation restores the
 * pre-interaction committed order and never mutates it. The draft is observable Compose state so
 * the list re-renders while only the committed order is ever persisted.
 */
internal class LiveCategoryOrderDraft(committedIds: List<String>) {
    var committedIds by mutableStateOf(committedIds)
        private set
    private var draftIds by mutableStateOf<List<String>?>(null)

    val isMoving: Boolean get() = draftIds != null
    val displayIds: List<String> get() = draftIds ?: committedIds

    fun reset(committedIds: List<String>) {
        this.committedIds = committedIds
        draftIds = null
    }

    fun start() {
        draftIds = committedIds
    }

    fun move(categoryId: String, targetIndex: Int) {
        val current = draftIds ?: return
        val moved = moveLiveCategory(current, categoryId, targetIndex)
        if (moved != current) draftIds = moved
    }

    fun commit(): List<String>? {
        val draft = draftIds ?: return null
        committedIds = draft
        draftIds = null
        return draft
    }

    fun cancel() {
        draftIds = null
    }
}

private class LiveCategoryVisibilityStore(context: Context) {
    private val appContext = context.applicationContext
    private val state = AccountProfileStateStore(
        context = appContext,
        basePreferencesName = LIVE_CATEGORY_VISIBILITY_PREFS,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )
    private val adoptionState = appContext.getSharedPreferences(
        LIVE_CATEGORY_ORDER_ADOPTION_PREFS,
        Context.MODE_PRIVATE,
    )

    fun hiddenCategoryIds(): Set<String> =
        decodeLiveHiddenCategoryIds(state.read(LIVE_CATEGORY_VISIBILITY_IDS_KEY))

    fun saveHiddenCategoryIds(ids: Set<String>, scope: AccountProfileStateScope?) {
        val encoded = encodeLiveHiddenCategoryIds(ids)
        if (scope == null) {
            state.write(LIVE_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        } else {
            state.write(scope, LIVE_CATEGORY_VISIBILITY_IDS_KEY, encoded)
        }
    }

    /**
     * Reads the committed server order for the active scope. Before the one-time adoption this is
     * the currently displayed filtered legacy order and no write happens during composition.
     */
    fun committedCategoryOrderIds(): List<String> {
        state.read(LIVE_CATEGORY_ORDER_IDS_KEY)
            ?.let { return decodeLiveCategoryOrderIds(it) }
        return liveCategoryOrderAdoption(
            scopedOrderIds = null,
            adoptionDone = adoptionState.getBoolean(LIVE_CATEGORY_ORDER_ADOPTION_KEY, false),
            legacyOrderIds = legacyServerCategoryOrderIds(),
        ).orEmpty()
    }

    /**
     * Adopts the legacy displayed server order exactly once for the first verified active scope.
     * The legacy preference itself is retained untouched and no other account or profile is seeded.
     */
    fun adoptCommittedCategoryOrderOnce() {
        if (adoptionState.getBoolean(LIVE_CATEGORY_ORDER_ADOPTION_KEY, false)) return
        val scope = state.activeScope() ?: return
        val adopted = liveCategoryOrderAdoption(
            scopedOrderIds = state.read(LIVE_CATEGORY_ORDER_IDS_KEY)
                ?.let(::decodeLiveCategoryOrderIds),
            adoptionDone = false,
            legacyOrderIds = legacyServerCategoryOrderIds(),
        )
        if (adopted != null) {
            state.write(
                scope,
                LIVE_CATEGORY_ORDER_IDS_KEY,
                encodeLiveCategoryOrderIds(adopted),
            )
        }
        adoptionState.edit().putBoolean(LIVE_CATEGORY_ORDER_ADOPTION_KEY, true).apply()
    }

    fun saveCommittedCategoryOrderIds(scope: AccountProfileStateScope?, ids: List<String>) {
        val encoded = encodeLiveCategoryOrderIds(ids)
        if (scope == null) {
            state.write(LIVE_CATEGORY_ORDER_IDS_KEY, encoded)
        } else {
            state.write(scope, LIVE_CATEGORY_ORDER_IDS_KEY, encoded)
        }
    }

    fun activeScope(): AccountProfileStateScope? = state.activeScope()

    private fun legacyServerCategoryOrderIds(): List<String> = legacyLiveServerCategoryOrderIds(
        appContext.getSharedPreferences(LEGACY_LIVE_CATEGORY_ORDER_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_LIVE_CATEGORY_ORDER_IDS_KEY, null),
    )

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(
            accountId,
            profileId,
            LIVE_CATEGORY_VISIBILITY_IDS_KEY,
            LIVE_CATEGORY_ORDER_IDS_KEY,
        )
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

internal fun Context.saveLiveHiddenCategoryIds(
    ids: Set<String>,
    scope: AccountProfileStateScope? = null,
) {
    LiveCategoryVisibilityStore.get(this).saveHiddenCategoryIds(ids, scope)
}

internal fun Context.liveCommittedCategoryOrderIds(): List<String> =
    LiveCategoryVisibilityStore.get(this).committedCategoryOrderIds()

internal fun Context.adoptLiveCommittedCategoryOrderOnce() {
    LiveCategoryVisibilityStore.get(this).adoptCommittedCategoryOrderOnce()
}

internal fun Context.saveLiveCommittedCategoryOrderIds(
    scope: AccountProfileStateScope?,
    ids: List<String>,
) {
    LiveCategoryVisibilityStore.get(this).saveCommittedCategoryOrderIds(scope, ids)
}

internal fun Context.liveCategoryStateScope(): AccountProfileStateScope? =
    LiveCategoryVisibilityStore.get(this).activeScope()

internal fun Context.removeLiveCategoryVisibilityProfileState(accountId: String, profileId: String) {
    LiveCategoryVisibilityStore.get(this).removeProfile(accountId, profileId)
}

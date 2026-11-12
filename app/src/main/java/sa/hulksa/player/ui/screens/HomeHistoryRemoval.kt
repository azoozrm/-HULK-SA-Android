package sa.hulksa.player.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.KeyEventType
import kotlinx.coroutines.flow.first

/**
 * The one stable-key rule for a removed Home item/row. The next surviving logical key wins;
 * when none follows, the nearest surviving preceding key wins.
 */
internal fun removalFocusTarget(
    previousKeys: List<String>,
    remainingKeys: List<String>,
    removedKey: String,
): String? {
    if (remainingKeys.isEmpty()) return null
    val removedIndex = previousKeys.indexOf(removedKey)
    if (removedIndex < 0) return null
    val remaining = remainingKeys.toSet()
    previousKeys
        .drop(removedIndex + 1)
        .firstOrNull { it in remaining }
        ?.let { return it }
    return previousKeys
        .take(removedIndex)
        .lastOrNull { it in remaining }
}

/**
 * One durable Home removal transaction. The controller survives card/row disposal long enough
 * to deliver the result and expose the stable focus target for the updated row model.
 */
internal class HomeHistoryRemovalController(
    private val removeHistory: (String, (Boolean) -> Unit) -> Unit,
    private val onSuccessfulRemoval: (wasLastCard: Boolean) -> Unit,
    private val canPublishSuccess: () -> Boolean = { true },
    private val onSuccessNotice: (String) -> Unit = {},
) {
    var pendingKey by mutableStateOf<String?>(null)
        private set
    var capturedTitle by mutableStateOf("")
        private set
    var previousKeys by mutableStateOf<List<String>>(emptyList())
        private set
    var succeeded by mutableStateOf(false)
        private set

    private var resultConsumed = false

    fun begin(key: String, keys: List<String>, displayTitle: String = key): Boolean {
        if (pendingKey != null) return false
        pendingKey = key
        previousKeys = keys
        capturedTitle = displayTitle
        succeeded = false
        resultConsumed = false
        removeHistory(key) { success ->
            if (resultConsumed) return@removeHistory
            resultConsumed = true
            if (success) {
                succeeded = true
                if (canPublishSuccess()) onSuccessNotice(capturedTitle)
                onSuccessfulRemoval(keys.size == 1)
            } else {
                pendingKey = null
                previousKeys = emptyList()
                capturedTitle = ""
            }
        }
        return true
    }

    /** The next/previous surviving stable key, only after a confirmed successful removal. */
    fun focusTarget(remainingKeys: List<String>): String? {
        if (!succeeded) return null
        val removedKey = pendingKey ?: return null
        return removalFocusTarget(previousKeys, remainingKeys, removedKey)
    }

    fun finish() {
        pendingKey = null
        previousKeys = emptyList()
        capturedTitle = ""
        succeeded = false
        resultConsumed = false
    }
}

/**
 * Shell-scoped select-gesture guard. It survives disposal of the card and of the whole Continue
 * Watching row. A completed removing gesture consumes its KeyUp at the shell boundary; the next
 * fresh press is not time-locked.
 */
internal class HomeRemovalInputGuard {
    var active: Boolean = false
        private set

    fun begin() {
        active = true
    }

    fun onSelectKey(type: KeyEventType): Boolean {
        if (!active) return false
        if (type == KeyEventType.KeyUp) active = false
        return true
    }
}

internal fun homeRemovalNotice(displayTitle: String): String =
    "تمت إزالة «$displayTitle» من متابعة المشاهدة"

internal fun recentRemovalNotice(displayTitle: String): String =
    "تمت إزالة «$displayTitle» من آخر مشاهدة"

/**
 * Arms the shell select guard before a Movies/Series Recent removal reaches the authoritative
 * mutation. The guard survives disposal of the card, grid and last item; the controller keeps the
 * captured stable key and its pre-removal order. This is the same transaction shape used by Home.
 */
internal fun beginRecentHistoryRemoval(
    remote: Boolean,
    controller: HomeHistoryRemovalController,
    capturedKey: String,
    capturedKeys: List<String>,
    capturedTitle: String,
    onRemoteRemovalStarted: () -> Unit,
): Boolean {
    if (controller.pendingKey != null) return false
    if (remote) onRemoteRemovalStarted()
    return controller.begin(capturedKey, capturedKeys, capturedTitle)
}

/**
 * Focus ownership for one settled Movies/Series Recent removal. The captured surviving stable key
 * remains owned while scroll observation and ordinary navigation restoration settle. A real user
 * move to a different card releases the lock; the removal itself never clears the key implicitly.
 */
internal class RecentRemovalFocusLock {
    var ownedKey by mutableStateOf<String?>(null)
        private set

    fun own(key: String) {
        ownedKey = key
    }

    fun clear() {
        ownedKey = null
    }

    fun onFocused(key: String) {
        if (ownedKey != null && ownedKey != key) ownedKey = null
    }

    fun yieldsScrollObservation(): Boolean = ownedKey != null
}

/** A single pending handoff of focus after the Continue Watching row empties. */
internal data class HomeEmptyRowFocusRequest(val id: Long)

/**
 * Resolves the remembered Home card against the current row data. A surviving stable key wins
 * even after reorder; a genuinely removed key falls back to the nearest remembered position only
 * after the Home model has settled. During first-frame loading the caller passes
 * [allowIndexFallback] = false so a partial catalog cannot focus the wrong card before the exact
 * target is known.
 */
internal fun resolveHomeRowRestoreIndex(
    remembered: NavigationPosition,
    rowKey: String,
    itemKeys: List<String>,
    allowIndexFallback: Boolean,
): Int? {
    if (remembered.rowKey != rowKey || itemKeys.isEmpty()) return null
    val keyIndex = itemKeys.indexOf(remembered.itemKey)
    if (keyIndex >= 0) return keyIndex
    if (!allowIndexFallback) return null
    return remembered.itemIndex.coerceIn(0, itemKeys.lastIndex)
}

/**
 * Reveals the Home hero at the top of the vertical list, waits until the item is actually placed,
 * verifies ownership was not taken over by a deliberate user move, then requests focus. This is
 * the deterministic fallback for a vanished remembered row, including when the hero starts offscreen.
 */
internal suspend fun revealAndFocusHomeHero(
    listState: LazyListState,
    isFocusOwnershipLost: () -> Boolean,
    requestFocus: () -> Boolean,
): Boolean {
    if (isFocusOwnershipLost()) return false
    listState.scrollToItem(0)
    snapshotFlow {
        listState.layoutInfo.visibleItemsInfo.any { it.key == "hero" }
    }.first { it }
    withFrameNanos { }
    if (isFocusOwnershipLost()) return false
    return requestFocus()
}

/**
 * Reveals a remembered card inside one Home row, waits for its actual placement, rechecks that
 * restoration still owns focus, then requests it. A deliberate user move updates navigation
 * memory and cancels this handoff instead of being overwritten.
 */
internal suspend fun focusHomeRowCard(
    listState: LazyListState,
    targetIndex: Int,
    targetKey: String,
    isFocusOwnershipLost: () -> Boolean,
    requestFocus: () -> Boolean,
): Boolean {
    if (isFocusOwnershipLost()) return false
    if (listState.layoutInfo.visibleItemsInfo.none {
            it.key == targetKey || it.index == targetIndex
        }
    ) {
        listState.scrollToItem(targetIndex)
    }
    snapshotFlow {
        listState.layoutInfo.visibleItemsInfo.any {
            it.key == targetKey || it.index == targetIndex
        }
    }.first { it }
    withFrameNanos { }
    if (isFocusOwnershipLost()) return false
    return requestFocus()
}

/** Live ownership check for one Home restoration handoff. */
internal fun homeFocusOwnershipLost(
    expected: NavigationPosition,
    current: NavigationPosition,
    railFocusBaseline: Long,
    currentRailFocusRevision: Long,
): Boolean = current != expected || currentRailFocusRevision != railFocusBaseline

/**
 * One-shot logical return intent for Home's notification bell. It survives the notification
 * center round-trip because it lives above Home composition, but it is consumed only by an actual
 * Home focus event (the requested bell or a deliberate move elsewhere).
 */
internal class HomeNotificationReturnOwner {
    private var pending = false

    fun request() {
        pending = true
    }

    fun cancel() {
        pending = false
    }

    fun isPending(): Boolean = pending
}

/**
 * One Home entry/return focus intent plus the real focus-region events that can cancel it.
 *
 * Only a fresh Home composition with a remembered non-blank row arms the entry intent. Catalog
 * or model publication, metadata arrival, loading changes and hero rotation are not entry events
 * and therefore never re-arm it. A successful handoff consumes it. A real header/rail focus
 * cancels it. A real content focus retires it once actual focus has landed on a concrete control,
 * even when that control shares the remembered hero/item position (for example Favorite versus a
 * pending primary action). While the user is on chrome, row restoration is also blocked even if
 * the entry intent was already consumed, so a later model publication cannot pull focus back into
 * the page.
 *
 * [initialEntryIntent] lets the notification-return path keep ordinary content/hero restoration
 * disarmed while the bell owns the return. [onFocusEvent] lets that scoped owner cancel its
 * pending return when a real focus event lands anywhere else.
 */
internal class HomeFocusRestoreOwner(
    remembered: NavigationPosition,
    initialEntryIntent: Boolean = remembered.rowKey.isNotBlank(),
    private val onFocusEvent: (() -> Unit)? = null,
) {
    private val expected = remembered
    private var entryIntent = initialEntryIntent
    private var competingRestoreSuppressed = !initialEntryIntent
    private var chromeFocused = false
    private var contentFocusOutsideNavigation = false

    fun onHeaderFocused() {
        onFocusEvent?.invoke()
        competingRestoreSuppressed = false
        chromeFocused = true
        entryIntent = false
    }

    fun onRailFocused() {
        onFocusEvent?.invoke()
        competingRestoreSuppressed = false
        chromeFocused = true
        entryIntent = false
    }

    fun onContentFocused(position: NavigationPosition?) {
        onFocusEvent?.invoke()
        competingRestoreSuppressed = false
        chromeFocused = false
        contentFocusOutsideNavigation = position == null
        // Actual focus has landed on a concrete content control. Retire the pending entry intent
        // even when the position equals the remembered target: primary and Favorite share the
        // same hero position, so position equality alone cannot distinguish the sibling control.
        entryIntent = false
    }

    /** Entry/return restoration: hero fallback and the first row handoff. */
    fun canRestoreEntry(
        current: NavigationPosition,
        railFocusBaseline: Long,
        currentRailFocusRevision: Long,
    ): Boolean = entryIntent &&
        !competingRestoreSuppressed &&
        !chromeFocused &&
        !contentFocusOutsideNavigation &&
        !homeFocusOwnershipLost(
            expected = expected,
            current = current,
            railFocusBaseline = railFocusBaseline,
            currentRailFocusRevision = currentRailFocusRevision,
        )

    /**
     * Row-level restoration/reveal. A consumed entry intent does not block ordinary in-page
     * content updates, but chrome or a non-navigation content control does.
     */
    fun canRestoreRow(
        remembered: NavigationPosition,
        current: NavigationPosition,
        railFocusBaseline: Long,
        currentRailFocusRevision: Long,
    ): Boolean = remembered.rowKey.isNotBlank() &&
        !competingRestoreSuppressed &&
        !chromeFocused &&
        !contentFocusOutsideNavigation &&
        !homeFocusOwnershipLost(
            expected = remembered,
            current = current,
            railFocusBaseline = railFocusBaseline,
            currentRailFocusRevision = currentRailFocusRevision,
        )

    fun onRestored() {
        entryIntent = false
    }
}

/**
 * One Home restoration focus request. Only an actual true result consumes the owner's entry
 * intent. A non-throwing false or a thrown request is not a successful handoff and must leave the
 * intent available for a later legitimate attempt.
 */
internal fun requestHomeRestoreFocus(
    requestFocus: () -> Boolean,
    onRestored: () -> Unit,
): Boolean {
    val focused = runCatching { requestFocus() }.getOrDefault(false)
    if (focused) onRestored()
    return focused
}

/** True when a row's first logical card is outside the row's current composed viewport. */
internal fun homeRowFirstCardNeedsReveal(visibleIndices: Iterable<Int>): Boolean =
    0 !in visibleIndices

/**
 * Actual row-level first-card handoff action. It reveals a horizontally scrolled-away first
 * card, waits for real composition, requests focus, and then waits for the card's own focus
 * callback before reporting success.
 */
internal suspend fun focusHomeRowFirstCard(
    firstCardVisible: () -> Boolean,
    revealFirstCard: suspend () -> Unit,
    awaitFirstCard: suspend () -> Unit,
    requestFocus: () -> Boolean,
    awaitFocused: suspend () -> Unit,
): Boolean {
    if (!firstCardVisible()) revealFirstCard()
    awaitFirstCard()
    if (!requestFocus()) return false
    awaitFocused()
    return true
}

/**
 * One real Home row's focus owner. Rows attach a suspending first-card focus action; the
 * parent handoff cannot complete until that action has revealed/awaited the actual card and
 * verified focus.
 */
internal class HomeRowFocusTarget {
    private var focusFirstCardAction: (suspend () -> Boolean)? = null

    fun attach(action: suspend () -> Boolean) {
        focusFirstCardAction = action
    }

    fun detach() {
        focusFirstCardAction = null
    }

    suspend fun focusFirstCard(): Boolean = focusFirstCardAction?.invoke() ?: false
}

/**
 * Production empty-row handoff: vertical row availability, then the row's real first-card
 * focus action, otherwise the Home navigation entry. It returns true only after the chosen
 * target reports verified focus.
 */
internal class HomeEmptyRowFocusHandoff(
    private val ensureRowAvailable: suspend (rowKey: String) -> Boolean,
    private val focusRowFirstCard: suspend (rowKey: String) -> Boolean,
    private val focusHomeEntry: suspend () -> Boolean,
) {
    suspend fun run(targetRow: String?): Boolean {
        if (targetRow == null) return focusHomeEntry()
        if (!ensureRowAvailable(targetRow)) return false
        return focusRowFirstCard(targetRow)
    }
}

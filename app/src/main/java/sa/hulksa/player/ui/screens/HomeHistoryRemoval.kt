package sa.hulksa.player.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEventType

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
    private val onSuccessNotice: () -> Unit = {},
) {
    var pendingKey by mutableStateOf<String?>(null)
        private set
    var previousKeys by mutableStateOf<List<String>>(emptyList())
        private set
    var succeeded by mutableStateOf(false)
        private set

    private var resultConsumed = false

    fun begin(key: String, keys: List<String>): Boolean {
        if (pendingKey != null) return false
        pendingKey = key
        previousKeys = keys
        succeeded = false
        resultConsumed = false
        removeHistory(key) { success ->
            if (resultConsumed) return@removeHistory
            resultConsumed = true
            if (success) {
                succeeded = true
                if (canPublishSuccess()) onSuccessNotice()
                onSuccessfulRemoval(keys.size == 1)
            } else {
                pendingKey = null
                previousKeys = emptyList()
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
    onRemoteRemovalStarted: () -> Unit,
): Boolean {
    if (controller.pendingKey != null) return false
    if (remote) onRemoteRemovalStarted()
    return controller.begin(capturedKey, capturedKeys)
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

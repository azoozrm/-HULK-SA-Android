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

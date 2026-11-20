package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single owner for an asynchronous Home football-rail focus transition.
 *
 * A new key event is refused while one transition is still running, so rapid presses or key
 * repeats cannot start parallel scroll/focus jobs. Cancellation is explicit and safe: a completed
 * or cancelled job can never clear a newer owner.
 */
internal class FootballRailFocusOwner {
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    /** Returns false when a transition already owns the rail; true when this block was started. */
    fun start(scope: CoroutineScope, block: suspend () -> Unit): Boolean {
        if (isActive) return false
        val started = scope.launch(start = CoroutineStart.LAZY) { block() }
        job = started
        started.invokeOnCompletion {
            if (job === started) job = null
        }
        return started.start()
    }

    fun cancel() {
        val current = job ?: return
        job = null
        current.cancel()
    }

    /**
     * Per-emission collector gate. A collector that subscribed before the transition started must
     * not write while the transition owns the rail; checking once before `collect` is insufficient.
     */
    fun collectorScrollDelta(requestedDelta: Int): Int = if (isActive) 0 else requestedDelta
}

/** A null scroll delta means the keyed card is not laid out at its current index yet. */
internal data class FootballRailPlacement(val index: Int, val scrollDeltaPx: Int?)

/**
 * Reveal and focus the same card even when data changes during a scroll or a layout frame.
 * Only a measured, fully visible card may receive a request. The next frame confirms both
 * actual focus and visibility before releasing the rail's scroll owner.
 */
internal suspend fun focusFootballRailCard(
    targetKey: () -> String?,
    locate: (String) -> FootballRailPlacement?,
    reveal: suspend (FootballRailPlacement) -> Unit,
    awaitFrame: suspend () -> Unit,
    isFocused: (String) -> Boolean,
    requestFocus: (String) -> Unit,
    canMoveFocus: () -> Boolean,
): Boolean = withTimeoutOrNull(500L) {
    while (isActive) {
        awaitFrame()
        val key = targetKey() ?: return@withTimeoutOrNull false
        val placement = locate(key) ?: return@withTimeoutOrNull false
        val ready = placement.scrollDeltaPx == 0
        if (ready && isFocused(key)) return@withTimeoutOrNull true
        if (!canMoveFocus()) return@withTimeoutOrNull false
        if (ready) requestFocus(key) else reveal(placement)
    }
    false
} ?: false

package sa.hulksa.player.ui.screens

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Explicit cancellation checkpoint used after profile PIN background work returns.
 *
 * PBKDF2 and SharedPreferences.commit() are blocking primitives, so cancellation cannot interrupt
 * those calls mid-execution. This checkpoint prevents a cancelled PIN job from accepting a result
 * or updating UI state after the blocking work returns.
 */
internal suspend fun ensureActive() {
    coroutineContext.ensureActive()
}

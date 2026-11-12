package sa.hulksa.player.data

import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import sa.hulksa.player.model.AuthenticatedSession

/**
 * R32 startup dispatch coordination for authenticated provider API requests.
 *
 * While the current owner's startup catalog round is active, catalog calls (priority) own a single
 * per-provider/account permit; metadata/scan calls wait. When the round ends, queued work drains
 * through the same permit one at a time, so the first startup requests do not fan out. Outside a
 * round, and once queued work drains, dispatch is unchanged.
 *
 * Ownership is cancellable: waiters check the session owner before running, and invalidation
 * cancels queued waiters. Capacity is released in `finally` for success, failure and cancellation.
 */
class ProviderDispatchGate internal constructor() {
    private class Waiter(val priority: Boolean) {
        val ready = CompletableDeferred<Unit>()
        var grantGeneration: Long = 0L
    }

    private class State {
        var generation: Long = 0L
        var roundActive: Boolean = false
        var active: Boolean = false
        var activeGeneration: Long = 0L
        val priority = ArrayDeque<Waiter>()
        val normal = ArrayDeque<Waiter>()
    }

    private val lock = Any()
    private val states = LinkedHashMap<ProviderAdmissionKey, State>()

    /** Starts a startup round. Queued work from a previous round is cancelled. */
    fun beginRound(session: AuthenticatedSession) {
        val key = providerAdmissionKey(session)
        val cancelled = mutableListOf<Waiter>()
        synchronized(lock) {
            val state = stateLocked(key)
            state.generation += 1L
            state.roundActive = true
            cancelled += state.priority
            cancelled += state.normal
            state.priority.clear()
            state.normal.clear()
        }
        cancelled.forEach {
            it.ready.cancel(CancellationException("Provider dispatch round replaced"))
        }
    }

    /** Ends the round; an already active call finishes and queued work drains sequentially. */
    fun endRound(session: AuthenticatedSession) {
        val key = providerAdmissionKey(session)
        var grant: Waiter? = null
        synchronized(lock) {
            val state = states[key] ?: return
            state.roundActive = false
            if (!state.active) grant = grantNextLocked(state)
        }
        grant?.ready?.complete(Unit)
    }

    /** Session/logout invalidation: cancel every waiter and stop gating. */
    fun invalidate() {
        val cancelled = mutableListOf<Waiter>()
        synchronized(lock) {
            states.values.forEach { state ->
                state.generation += 1L
                state.roundActive = false
                state.active = false
                cancelled += state.priority
                cancelled += state.normal
                state.priority.clear()
                state.normal.clear()
            }
            states.clear()
        }
        cancelled.forEach {
            it.ready.cancel(CancellationException("Provider dispatch owner replaced"))
        }
    }

    internal suspend fun <T> withPermit(
        session: AuthenticatedSession,
        owner: AuthenticatedSessionOwner?,
        priority: Boolean,
        block: suspend () -> T,
    ): T {
        ensureOwner(owner)
        val key = providerAdmissionKey(session)
        var fastPath = false
        var directGrantGeneration: Long? = null
        var waiter: Waiter? = null

        synchronized(lock) {
            val state = states[key]
            if (
                state == null ||
                (!state.roundActive && !state.active &&
                    state.priority.isEmpty() && state.normal.isEmpty())
            ) {
                fastPath = true
            } else if (priority) {
                if (!state.active) {
                    state.active = true
                    state.activeGeneration = state.generation
                    directGrantGeneration = state.generation
                } else {
                    waiter = Waiter(priority = true)
                    state.priority.addLast(waiter)
                }
            } else {
                waiter = Waiter(priority = false)
                state.normal.addLast(waiter)
            }
        }

        if (fastPath) {
            ensureOwner(owner)
            return block()
        }

        val pending = waiter
        if (pending != null) {
            try {
                pending.ready.await()
            } catch (cancelled: CancellationException) {
                removeWaiter(key, pending)
                throw cancelled
            }
        }

        val grantedGeneration = directGrantGeneration ?: pending?.grantGeneration ?: 0L
        try {
            ensureOwner(owner)
            return block()
        } finally {
            release(key, grantedGeneration)
        }
    }

    private fun removeWaiter(key: ProviderAdmissionKey, waiter: Waiter) {
        synchronized(lock) {
            states[key]?.let { state ->
                state.priority.remove(waiter)
                state.normal.remove(waiter)
            }
        }
    }

    private fun release(key: ProviderAdmissionKey, generation: Long) {
        var grant: Waiter? = null
        synchronized(lock) {
            val state = states[key] ?: return
            if (!state.active || state.activeGeneration != generation) return
            grant = grantNextLocked(state)
        }
        grant?.ready?.complete(Unit)
    }

    private fun grantNextLocked(state: State): Waiter? {
        val next = if (state.priority.isNotEmpty()) {
            state.priority.removeFirst()
        } else if (state.normal.isNotEmpty()) {
            state.normal.removeFirst()
        } else {
            null
        }
        if (next == null) {
            state.active = false
            return null
        }
        state.active = true
        state.activeGeneration = state.generation
        next.grantGeneration = state.generation
        return next
    }

    private fun stateLocked(key: ProviderAdmissionKey): State {
        states[key]?.let { return it }
        if (states.size >= MAX_STATES) {
            val victim = states.entries.firstOrNull { entry ->
                !entry.value.active &&
                    entry.value.priority.isEmpty() &&
                    entry.value.normal.isEmpty()
            } ?: states.entries.first()
            states.remove(victim.key)
        }
        return State().also { states[key] = it }
    }

    private fun ensureOwner(owner: AuthenticatedSessionOwner?) {
        if (owner != null && !AuthenticatedSessionRegistry.isCurrent(owner)) {
            throw CancellationException("Provider dispatch owner replaced")
        }
    }

    internal companion object {
        private const val MAX_STATES = 16
        val shared: ProviderDispatchGate by lazy { ProviderDispatchGate() }
    }
}

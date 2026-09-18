package sa.hulksa.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single process owner of interactive Presence. It deliberately keeps the bearer token only
 * in memory; process death cancels this scope and the server TTL expires the session.
 */
internal class PresenceLifecycleOwner(
    private val scope: CoroutineScope,
    private val transport: PresenceTransport,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val heartbeatDelay: suspend (Long) -> Unit = { delay(it) },
) {
    private var discovery: OperationsPresenceConfig? = null
    private var desiredSession: PresenceSessionSnapshot? = null
    private var activeSession: ActivePresence? = null
    private var startJob: Job? = null
    private var heartbeatJob: Job? = null
    private var generation: Long = 0L

    fun updateDiscovery(next: OperationsPresenceConfig?) {
        if (discovery == next) return
        discovery = next
        generation += 1L
        startJob?.cancel()
        startJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSession = null
        launchStartIfEligible(generation)
    }

    fun onAuthenticated(snapshot: PresenceSessionSnapshot) {
        val current = desiredSession
        if (current?.sessionId == snapshot.sessionId && current.installationId == snapshot.installationId) {
            if (activeSession == null && startJob?.isActive != true) {
                launchStartIfEligible(generation)
            }
            return
        }

        val previous = activeSession
        val previousConfig = discovery
        generation += 1L
        startJob?.cancel()
        startJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSession = null
        desiredSession = snapshot

        if (previous != null && previousConfig != null) {
            launchBestEffortEnd(previousConfig, previous, PresenceEndReason.ACCOUNT_REPLACED)
        }
        launchStartIfEligible(generation)
    }

    fun logout() {
        val previous = activeSession
        val previousConfig = discovery
        generation += 1L
        desiredSession = null
        startJob?.cancel()
        startJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSession = null
        if (previous != null && previousConfig != null) {
            launchBestEffortEnd(previousConfig, previous, PresenceEndReason.LOGOUT)
        }
    }

    private fun launchStartIfEligible(ownerGeneration: Long) {
        val config = discovery ?: return
        val snapshot = desiredSession ?: return
        if (startJob?.isActive == true || activeSession?.sessionId == snapshot.sessionId) return

        startJob = scope.launch {
            var result = safeStart(config, snapshot)
            if (result == PresenceStartResult.TransientFailure) {
                retryDelay(START_RETRY_DELAY_MS)
                if (!isCurrent(ownerGeneration, config, snapshot)) return@launch
                result = safeStart(config, snapshot)
            }
            if (!isCurrent(ownerGeneration, config, snapshot)) return@launch
            startJob = null
            val accepted = (result as? PresenceStartResult.Accepted)?.value ?: return@launch
            if (accepted.sessionId != snapshot.sessionId) return@launch

            val active = ActivePresence(
                sessionId = snapshot.sessionId,
                installationId = snapshot.installationId,
                token = accepted.token,
                heartbeatSeconds = accepted.heartbeatSeconds,
            )
            activeSession = active
            heartbeatJob = launchHeartbeat(ownerGeneration, config, active)
        }
    }

    private fun launchHeartbeat(
        ownerGeneration: Long,
        config: OperationsPresenceConfig,
        active: ActivePresence,
    ): Job = scope.launch {
        while (isActive && isCurrent(ownerGeneration, config, active)) {
            heartbeatDelay(active.heartbeatSeconds * 1_000L)
            if (!isCurrent(ownerGeneration, config, active)) return@launch
            when (safeHeartbeat(config, active)) {
                PresenceCommandResult.Accepted,
                PresenceCommandResult.TransientFailure,
                -> Unit
                PresenceCommandResult.TerminalFailure -> {
                    if (isCurrent(ownerGeneration, config, active)) {
                        activeSession = null
                        heartbeatJob = null
                    }
                    return@launch
                }
            }
        }
    }

    private fun launchBestEffortEnd(
        config: OperationsPresenceConfig,
        active: ActivePresence,
        reason: PresenceEndReason,
    ) {
        scope.launch {
            try {
                transport.end(config, active.sessionId, active.token, reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Presence never owns IPTV logout or replacement success.
            }
        }
    }

    private suspend fun safeStart(
        config: OperationsPresenceConfig,
        snapshot: PresenceSessionSnapshot,
    ): PresenceStartResult = try {
        transport.start(config, snapshot)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        PresenceStartResult.TransientFailure
    }

    private suspend fun safeHeartbeat(
        config: OperationsPresenceConfig,
        active: ActivePresence,
    ): PresenceCommandResult = try {
        transport.heartbeat(config, active.sessionId, active.token)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        PresenceCommandResult.TransientFailure
    }

    private fun isCurrent(
        ownerGeneration: Long,
        config: OperationsPresenceConfig,
        snapshot: PresenceSessionSnapshot,
    ): Boolean = generation == ownerGeneration &&
        discovery == config &&
        desiredSession?.sessionId == snapshot.sessionId &&
        desiredSession?.installationId == snapshot.installationId

    private fun isCurrent(
        ownerGeneration: Long,
        config: OperationsPresenceConfig,
        active: ActivePresence,
    ): Boolean = generation == ownerGeneration &&
        discovery == config &&
        activeSession === active &&
        desiredSession?.sessionId == active.sessionId &&
        desiredSession?.installationId == active.installationId

    private data class ActivePresence(
        val sessionId: String,
        val installationId: String,
        val token: String,
        val heartbeatSeconds: Int,
    )

    private companion object {
        const val START_RETRY_DELAY_MS = 1_000L
    }
}

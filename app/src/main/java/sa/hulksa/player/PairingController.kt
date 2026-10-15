package sa.hulksa.player

import android.os.SystemClock
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.data.OperationsPairingConfig
import sa.hulksa.player.data.PairingClaimOutcome
import sa.hulksa.player.data.PairingCreateInput
import sa.hulksa.player.data.PairingDeviceInfo
import sa.hulksa.player.data.PairingResult
import sa.hulksa.player.data.PairingTransport
import sa.hulksa.player.data.canonicalBase64Url
import sa.hulksa.player.data.generatePairingSecret
import sa.hulksa.player.model.Credentials

enum class PairingPhase {
    CLOSED,
    CREATING,
    WAITING,
    CLAIMED,
    CONSUMING,
    CONSUMED,
}

/**
 * Safe render projection. Contains only app-authored copy, the public activation code / URL and
 * the monotonic expiry deadline used for the displayed countdown; never a pairing token, client
 * secret, server response body or credential.
 */
data class PairingUiState(
    val available: Boolean = false,
    val visible: Boolean = false,
    val phase: PairingPhase = PairingPhase.CLOSED,
    val userCode: String? = null,
    val verificationUrl: String? = null,
    val deadlineElapsedRealtimeMs: Long? = null,
    val message: String? = null,
    val networkFailure: Boolean = false,
    val canRetry: Boolean = false,
    val canRegenerate: Boolean = false,
)

/**
 * App-authored pairing copy. Server bodies are never echoed; every failure maps to one of these
 * fixed strings.
 */
internal object PairingCopy {
    const val SERVICE_UNAVAILABLE = "تعذر الاتصال بخدمة HULK ، حاول مرة اخرى"
    const val THROTTLED = "تم تعليق المحاولات مؤقتا ، حاول مرة اخرى بعد قليل"
    const val DISABLED = "خدمة تسجيل الدخول بالجوال غير متاحة حاليا"
    const val EXPIRED = "انتهت صلاحية رمز الربط ، اطلب رمزا جديدا"
    const val DENIED = "تم رفض طلب الربط من الجوال"
    const val CANCELLED = "تم اغلاق طلب الربط"
    const val INSTALLATION_FAILED = "تعذر تجهيز معرف التثبيت ، حاول مرة اخرى"
    const val FAILED = "حدث خطا غير متوقع. حاول مرة اخرى"
}

/**
 * Authentication reservation seam. [tryReserve] must be called on the controller's serialized
 * consume boundary and must note the same explicit user-login preflight as a manual submit before
 * reserving the existing attempt gate. [launch] hands the reserved credentials to the existing
 * internal authentication ownership; it is never the public login() entry. [release] abandons a
 * reservation that cannot be consumed (for example when no receipt capacity is available) and must
 * end the attempt without creating any authentication proof.
 */
internal interface PairingAuthHandoff {
    fun tryReserve(): Long?

    fun launch(credentials: Credentials, remember: Boolean, reservation: Long)

    fun release(reservation: Long)
}

/**
 * Memory-only owner of the TV pairing attempt. All attempt secrets live in process memory only:
 * no saved state, preferences, files, database, logs, analytics or exception messages. Polling
 * runs in exactly one cancelable job; every network result is rechecked against generation, phase
 * and context before it can affect UI or authentication.
 */
internal class PairingController(
    private val transport: PairingTransport,
    private val scope: CoroutineScope,
    private val handoff: PairingAuthHandoff,
    private val installationIdProvider: suspend () -> String?,
    private val deviceInfoProvider: () -> PairingDeviceInfo,
    private val onConsumedAck: (baseUrl: String, requestId: String, claimToken: String) -> Boolean,
    private val onState: (PairingUiState) -> Unit,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val monotonicClock: () -> Long = SystemClock::elapsedRealtime,
    private val randomUuid: () -> String = { UUID.randomUUID().toString() },
    private val randomSecret: () -> String = { canonicalBase64Url(generatePairingSecret()) },
    private val jitterFraction: () -> Double = { SecureRandom().nextDouble() * 0.4 - 0.2 },
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private class PairingAttempt(
        val generation: Long,
        val baseUrl: String,
        val clientRequestId: String,
        val clientSecret: String,
        val remember: Boolean,
        var requestId: String? = null,
        var claimToken: String? = null,
        var userCode: String? = null,
        var verificationUrl: String? = null,
        var expiresAtEpochMs: Long? = null,
        var deadlineMonotonicMs: Long = 0L,
        var pollFloorMs: Long = DEFAULT_POLL_FLOOR_MS,
        var maxDeliveries: Int = 20,
        var phase: PairingPhase = PairingPhase.CREATING,
        var notice: PairingNotice? = null,
        var serverTerminal: Boolean = false,
    ) {
        fun remainingMs(monotonicNowMs: Long): Long =
            (deadlineMonotonicMs - monotonicNowMs).coerceAtLeast(0L)
    }

    private data class PairingNotice(
        val message: String,
        val networkFailure: Boolean = false,
        val canRetry: Boolean = false,
        val canRegenerate: Boolean = true,
        val serverTerminal: Boolean = false,
    )

    private val lock = Any()
    private var discovery: OperationsPairingConfig? = null
    private var generation: Long = 0L
    private var attempt: PairingAttempt? = null
    private var job: Job? = null
    private var lastRemember: Boolean = true
    private var published: PairingUiState = PairingUiState()

    /**
     * Server-directed create not-before state, owned by the controller and independent of attempt
     * regeneration: Retry, Regenerate and cancel+reopen all observe the same cooldown, so a new
     * identity cannot bypass an advertised window.
     */
    private var createCooldownUntilMonotonicMs: Long = 0L

    fun updateDiscovery(discovery: OperationsPairingConfig?) {
        val invalidated: PairingAttempt?
        synchronized(lock) {
            this.discovery = discovery
            invalidated = if (discovery == null) {
                generation += 1L
                attempt.also { attempt = null }
            } else {
                null
            }
        }
        if (invalidated != null) {
            cancelJob()
            cleanupBestEffort(invalidated)
        }
        publish()
    }

    fun start(remember: Boolean) {
        val config: OperationsPairingConfig
        val startedGeneration: Long
        synchronized(lock) {
            if (discovery == null || attempt != null) return
            lastRemember = remember
            val generation = ++this.generation
            config = checkNotNull(discovery)
            attempt = PairingAttempt(
                generation = generation,
                baseUrl = config.baseUrl,
                clientRequestId = randomUuid(),
                clientSecret = randomSecret(),
                remember = remember,
            )
            startedGeneration = generation
        }
        publish()
        beginCreate(startedGeneration, config)
    }

    /** Re-issues create for a lost response with the SAME clientRequestId and clientSecret. */
    fun retry() {
        val config: OperationsPairingConfig
        val retriedGeneration: Long
        synchronized(lock) {
            val current = attempt ?: return
            if (current.requestId != null || current.phase != PairingPhase.CLOSED || !current.canRetry()) return
            config = discovery ?: return
            current.notice = null
            current.phase = PairingPhase.CREATING
            retriedGeneration = current.generation
        }
        cancelJob()
        publish()
        beginCreate(retriedGeneration, config)
    }

    /**
     * Invalidates the current generation BEFORE cancelling network work, then creates a new
     * attempt with a fresh UUID and secret. Never allowed once credentials were consumed.
     */
    fun regenerate() {
        val config: OperationsPairingConfig
        val stale: PairingAttempt?
        val nextGeneration: Long
        synchronized(lock) {
            config = discovery ?: return
            val current = attempt
            if (current != null &&
                (current.phase == PairingPhase.CLAIMED ||
                    current.phase == PairingPhase.CONSUMING ||
                    current.phase == PairingPhase.CONSUMED)
            ) {
                return
            }
            stale = current
            val generation = ++this.generation
            attempt = PairingAttempt(
                generation = generation,
                baseUrl = config.baseUrl,
                clientRequestId = randomUuid(),
                clientSecret = randomSecret(),
                remember = lastRemember,
            )
            nextGeneration = generation
        }
        cancelJob()
        stale?.let(::cleanupBestEffort)
        publish()
        beginCreate(nextGeneration, config)
    }

    /** First Back/cancel, lifecycle stop, manual login, logout and context loss share this path. */
    fun cancel() {
        val stale: PairingAttempt?
        synchronized(lock) {
            generation += 1L
            stale = attempt
            attempt = null
        }
        cancelJob()
        stale?.let(::cleanupBestEffort)
        publish()
    }

    private fun PairingAttempt.canRetry(): Boolean = notice?.canRetry == true

    /**
     * Starts create work only when the server-directed create cooldown has elapsed. During the
     * cooldown the attempt stays present with safe throttled copy and a retry affordance; no
     * request bytes are produced.
     */
    private fun beginCreate(generation: Long, config: OperationsPairingConfig) {
        val cooldownUntil = synchronized(lock) { createCooldownUntilMonotonicMs }
        if (monotonicClock() < cooldownUntil) {
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.THROTTLED,
                    canRetry = true,
                    canRegenerate = true,
                ),
            )
            return
        }
        cancelJob()
        job = scope.launch { runCreate(generation, config) }
    }

    private fun cancelJob() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
    }

    private suspend fun runCreate(generation: Long, config: OperationsPairingConfig) {
        val snapshot = synchronized(lock) { currentAttempt(generation) } ?: return
        val installationId = try {
            installationIdProvider()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (!isCurrent(generation)) return
        if (installationId == null) {
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.INSTALLATION_FAILED,
                    canRetry = true,
                    canRegenerate = true,
                ),
            )
            return
        }

        val input = PairingCreateInput(
            clientRequestId = snapshot.clientRequestId,
            clientSecret = snapshot.clientSecret,
            installationId = installationId,
            requestedAtEpochMs = wallClock(),
            device = deviceInfoProvider(),
        )
        val result = transport.create(config.baseUrl, input)
        if (!isCurrent(generation)) {
            if (result is PairingResult.Success) {
                cleanupBestEffortValues(
                    config.baseUrl,
                    result.value.requestId,
                    result.value.claimToken,
                )
            }
            return
        }

        when (result) {
            is PairingResult.Success -> {
                val created = result.value
                val activated = synchronized(lock) {
                    val current = currentAttempt(generation) ?: return
                    current.requestId = created.requestId
                    current.claimToken = created.claimToken
                    current.userCode = created.userCode
                    current.verificationUrl = created.verificationUrl
                    current.expiresAtEpochMs = created.expiresAtEpochMs
                    current.pollFloorMs = created.pollIntervalSeconds * 1_000L
                    current.maxDeliveries = created.maxDeliveries
                    current.deadlineMonotonicMs = monotonicClock() +
                        pairingBoundedRemainingMs(created.expiresAtEpochMs, wallClock())
                    current.phase = PairingPhase.WAITING
                    current.notice = null
                    true
                }
                if (!activated) return
                publish()
                runClaimLoop(generation, config)
            }

            is PairingResult.HttpFailure -> handleCreateFailure(generation, result)
            PairingResult.TransportFailure -> finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.SERVICE_UNAVAILABLE,
                    networkFailure = true,
                    canRetry = true,
                    canRegenerate = true,
                ),
            )
            PairingResult.DiscoveryInvalid -> finishDisabled(generation)
            PairingResult.InvalidResponse -> finishWithNotice(
                generation = generation,
                notice = PairingNotice(message = PairingCopy.FAILED),
            )
        }
    }

    private fun handleCreateFailure(generation: Long, failure: PairingResult.HttpFailure) {
        if (failure.code == "PAIRING_DISABLED") {
            finishDisabled(generation)
            return
        }
        if (failure.code == "CONTRACT_VERSION_UNSUPPORTED") {
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.DISABLED,
                    canRegenerate = false,
                    serverTerminal = true,
                ),
            )
            return
        }
        if (failure.retryAfterSeconds != null || failure.httpStatus == 429) {
            val cooldownMs = failure.retryAfterSeconds?.toLong()?.times(1_000L)
                ?: DEFAULT_CREATE_COOLDOWN_MS
            synchronized(lock) {
                createCooldownUntilMonotonicMs = maxOf(
                    createCooldownUntilMonotonicMs,
                    monotonicClock() + cooldownMs,
                )
            }
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.THROTTLED,
                    canRetry = true,
                    canRegenerate = true,
                ),
            )
            return
        }
        if (
            failure.httpStatus == 408 ||
            failure.httpStatus == 425 ||
            failure.httpStatus >= 500
        ) {
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(
                    message = PairingCopy.SERVICE_UNAVAILABLE,
                    networkFailure = failure.httpStatus >= 500,
                    canRetry = true,
                    canRegenerate = true,
                ),
            )
            return
        }
        finishWithNotice(
            generation = generation,
            notice = PairingNotice(message = PairingCopy.FAILED),
        )
    }

    private suspend fun runClaimLoop(generation: Long, config: OperationsPairingConfig) {
        var failureBackoffMs = synchronized(lock) { currentAttempt(generation)?.pollFloorMs }
            ?: return
        var pendingFloorMs = failureBackoffMs
        var serverDirectedMs: Long? = null

        while (true) {
            val remaining = synchronized(lock) { currentAttempt(generation)?.remainingMs(monotonicClock()) }
                ?: return
            if (remaining <= 0L) {
                finishExpired(generation)
                return
            }
            val plannedMs = serverDirectedMs ?: pairingJitteredDelayMs(
                baseMs = failureBackoffMs,
                floorMs = pendingFloorMs,
                jitterFraction = jitterFraction(),
            )
            delayMillis(minOf(plannedMs, remaining))
            if (!isCurrent(generation)) return
            val snapshot = synchronized(lock) { currentAttempt(generation) } ?: return
            if (snapshot.remainingMs(monotonicClock()) <= 0L) {
                finishExpired(generation)
                return
            }
            val requestId = snapshot.requestId ?: return
            val claimToken = snapshot.claimToken ?: return

            when (val result = transport.claim(config.baseUrl, requestId, claimToken)) {
                is PairingResult.Success -> when (val outcome = result.value) {
                    is PairingClaimOutcome.Pending -> {
                        serverDirectedMs = null
                        failureBackoffMs = snapshot.pollFloorMs
                        pendingFloorMs = maxOf(snapshot.pollFloorMs, outcome.retryAfterMs)
                        synchronized(lock) {
                            currentAttempt(generation)?.let { current ->
                                applyServerExpiryLocked(current, outcome.expiresAtEpochMs)
                            }
                        }
                    }
                    is PairingClaimOutcome.Approved -> {
                        consumeApproved(generation, outcome)
                        return
                    }
                    is PairingClaimOutcome.Terminal -> {
                        finishServerTerminal(generation, outcome.state)
                        return
                    }
                }
                is PairingResult.HttpFailure -> when {
                    result.code == "PAIRING_DISABLED" -> {
                        finishDisabled(generation)
                        return
                    }
                    result.retryAfterSeconds != null -> {
                        // A server-directed wait never shortens the negotiated poll floor.
                        serverDirectedMs = maxOf(
                            result.retryAfterSeconds.toLong() * 1_000L,
                            snapshot.pollFloorMs,
                        )
                    }
                    result.httpStatus == 408 ||
                        result.httpStatus == 425 ||
                        result.httpStatus == 429 ||
                        result.httpStatus >= 500 ||
                        result.code == "PAIRING_UNAVAILABLE" -> {
                        serverDirectedMs = null
                        failureBackoffMs = minOf(failureBackoffMs * 2L, MAX_FAILURE_BACKOFF_MS)
                    }
                    else -> {
                        finishWithNotice(
                            generation = generation,
                            notice = PairingNotice(message = PairingCopy.FAILED),
                        )
                        return
                    }
                }
                PairingResult.TransportFailure -> {
                    serverDirectedMs = null
                    failureBackoffMs = minOf(failureBackoffMs * 2L, MAX_FAILURE_BACKOFF_MS)
                }
                PairingResult.DiscoveryInvalid -> {
                    finishDisabled(generation)
                    return
                }
                PairingResult.InvalidResponse -> {
                    finishWithNotice(
                        generation = generation,
                        notice = PairingNotice(message = PairingCopy.FAILED),
                    )
                    return
                }
            }
            if (!isCurrent(generation)) return
        }
    }

    /**
     * One serialized consume boundary. Generation, context, phase, monotonic deadline and the
     * negotiated delivery bound are rechecked after claim IO and before reserving the existing auth
     * gate; there is no suspend gap between acceptance, phase change and reservation. Receipt
     * admission is guaranteed before authentication starts: without an admitted receipt the
     * reservation is released and the delivery is closed without authenticating. Duplicate/stale
     * deliveries observe a non-WAITING phase and are dropped.
     */
    private fun consumeApproved(
        generation: Long,
        approved: PairingClaimOutcome.Approved,
    ) {
        var reservation: Long? = null
        var reservedAttempt: PairingAttempt? = null
        var busyAttempt: PairingAttempt? = null
        var expired = false
        var malformed = false
        synchronized(lock) {
            val current = currentAttempt(generation)
            if (current == null || current.phase != PairingPhase.WAITING) return
            applyServerExpiryLocked(current, approved.expiresAtEpochMs)
            when {
                current.remainingMs(monotonicClock()) <= 0L -> expired = true
                approved.deliveryCount > current.maxDeliveries -> malformed = true
                else -> {
                    current.phase = PairingPhase.CLAIMED
                    val gateReservation = handoff.tryReserve()
                    if (gateReservation == null) {
                        current.phase = PairingPhase.CLOSED
                        busyAttempt = current
                        attempt = null
                    } else {
                        current.phase = PairingPhase.CONSUMING
                        reservation = gateReservation
                        reservedAttempt = current
                    }
                }
            }
        }
        if (expired) {
            finishExpired(generation)
            return
        }
        if (malformed) {
            finishWithNotice(
                generation = generation,
                notice = PairingNotice(message = PairingCopy.FAILED),
            )
            return
        }
        val busy = busyAttempt
        if (busy != null) {
            publish()
            cleanupBestEffort(busy)
            return
        }
        val consumed = reservedAttempt ?: return
        val reserved = reservation ?: return
        val requestId = consumed.requestId
        val claimToken = consumed.claimToken
        val admitted = requestId != null &&
            claimToken != null &&
            onConsumedAck(consumed.baseUrl, requestId, claimToken)
        if (!admitted) {
            // No receipt capacity: fail closed, release the reservation and never authenticate.
            handoff.release(reserved)
            synchronized(lock) {
                if (attempt === consumed) attempt = null
            }
            publish()
            cleanupBestEffort(consumed)
            return
        }
        synchronized(lock) {
            if (attempt === consumed) attempt = null
        }
        publish()
        handoff.launch(approved.credentials, consumed.remember, reserved)
    }

    /** Tightens the monotonic deadline from a server expiry; never extends it. */
    private fun applyServerExpiryLocked(attempt: PairingAttempt, expiresAtEpochMs: Long) {
        val candidateDeadline = monotonicClock() +
            pairingBoundedRemainingMs(expiresAtEpochMs, wallClock())
        if (candidateDeadline < attempt.deadlineMonotonicMs) {
            attempt.deadlineMonotonicMs = candidateDeadline
        }
    }

    private fun finishExpired(generation: Long) {
        finishWithNotice(
            generation = generation,
            notice = PairingNotice(message = PairingCopy.EXPIRED),
        )
    }

    private fun finishServerTerminal(generation: Long, state: String) {
        val message = when (state) {
            "DENIED" -> PairingCopy.DENIED
            else -> PairingCopy.CANCELLED
        }
        finishWithNotice(
            generation = generation,
            notice = PairingNotice(message = message, serverTerminal = true),
        )
    }

    private fun finishDisabled(generation: Long) {
        finishWithNotice(
            generation = generation,
            notice = PairingNotice(
                message = PairingCopy.DISABLED,
                canRegenerate = false,
                serverTerminal = true,
            ),
        )
    }

    private fun finishWithNotice(generation: Long, notice: PairingNotice) {
        synchronized(lock) {
            val current = currentAttempt(generation) ?: return
            current.notice = notice
            current.serverTerminal = current.serverTerminal || notice.serverTerminal
            if (current.phase != PairingPhase.CLOSED) current.phase = PairingPhase.CLOSED
        }
        publish()
    }

    private fun isCurrent(generation: Long): Boolean =
        synchronized(lock) { currentAttempt(generation) != null }

    private fun currentAttempt(generation: Long): PairingAttempt? =
        attempt?.takeIf { it.generation == generation }

    private fun cleanupBestEffort(stale: PairingAttempt) {
        if (stale.serverTerminal) return
        val requestId = stale.requestId ?: return
        val claimToken = stale.claimToken ?: return
        cleanupBestEffortValues(stale.baseUrl, requestId, claimToken)
    }

    private fun cleanupBestEffortValues(
        baseUrl: String,
        requestId: String,
        claimToken: String,
    ) {
        scope.launch {
            runCatching { transport.cancel(baseUrl, requestId, claimToken) }
        }
    }

    private fun publish() {
        val snapshot = synchronized(lock) {
            val current = attempt
            PairingUiState(
                available = discovery != null,
                visible = current != null,
                phase = current?.phase ?: PairingPhase.CLOSED,
                userCode = current?.userCode,
                verificationUrl = current?.verificationUrl,
                deadlineElapsedRealtimeMs = current?.deadlineMonotonicMs?.takeIf { it > 0L },
                message = current?.notice?.message,
                networkFailure = current?.notice?.networkFailure ?: false,
                canRetry = current?.notice?.canRetry ?: false,
                canRegenerate = current?.notice?.canRegenerate ?: false,
            )
        }
        if (snapshot == published) return
        published = snapshot
        onState(snapshot)
    }

    private companion object {
        const val MAX_FAILURE_BACKOFF_MS = 15_000L
        const val DEFAULT_POLL_FLOOR_MS = 3_000L
        const val DEFAULT_CREATE_COOLDOWN_MS = 60_000L
    }
}

/**
 * Conservative bounded client expiry basis: the wire has no server-time field, so the client
 * derives a monotonic deadline from the server expiry and the local wall clock at response time,
 * capped to the maximum supported TTL (900 s). The cap bounds a long remaining value but does not
 * bound clock skew: without server time the client cannot prove its deadline equals the server's
 * remaining validity, so a local deadline may end a valid attempt early, and a backwards-skewed
 * wall clock can make the local deadline longer than the server's remaining validity. The server
 * remains the sole expiry and authorization authority; the client never claims otherwise.
 */
internal fun pairingBoundedRemainingMs(
    expiresAtEpochMs: Long,
    wallNowMs: Long,
    maxTtlMs: Long = 900_000L,
): Long = (expiresAtEpochMs - wallNowMs).coerceIn(0L, maxTtlMs)

/** Bounded polling backoff: x2 growth capped at 15s, jitter ±20%, never below the server floor. */
internal fun pairingJitteredDelayMs(
    baseMs: Long,
    floorMs: Long,
    jitterFraction: Double,
): Long {
    val clamped = jitterFraction.coerceIn(-0.2, 0.2)
    val jittered = (baseMs * (1.0 + clamped)).toLong()
    return maxOf(jittered, floorMs).coerceAtLeast(1L)
}

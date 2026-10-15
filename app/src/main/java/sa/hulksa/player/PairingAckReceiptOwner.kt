package sa.hulksa.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.data.PairingResult
import sa.hulksa.player.data.PairingTransport

/**
 * Dedicated consumed-ACK receipt owner. A receipt (base URL + requestId + claim token only) is
 * admitted synchronously when a delivery is consumed and retried on the owner's own scope with a
 * finite budget of [MAX_ACK_ATTEMPTS] attempts, exponential backoff from 1s to 15s and a hard
 * [MAX_RECEIPT_AGE_MS] maximum age measured from acceptance including queue time. A required wait
 * that cannot fit inside the age budget retires the receipt honestly instead of stalling the queue
 * or retrying earlier; later receipts keep progressing. Retained work is bounded by
 * [MAX_PENDING_RECEIPTS]; admission is explicit so the controller can fail closed instead of
 * silently losing a consumed delivery. The owner is independent of provider failure,
 * account-decision outcome, dialog closure, pairing generation changes and logout; process/scope
 * end may cancel it. It never triggers reauthentication and is not a login-success signal.
 * Receipt state is memory-only; the server expiry remains the fallback destruction path.
 */
internal class PairingAckReceiptOwner(
    scope: CoroutineScope,
    private val transport: PairingTransport,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val monotonicClock: () -> Long = SystemClock::elapsedRealtime,
    private val maxAckAttempts: Int = MAX_ACK_ATTEMPTS,
    private val maxReceiptAgeMs: Long = MAX_RECEIPT_AGE_MS,
    private val maxPendingReceipts: Int = MAX_PENDING_RECEIPTS,
) {
    private data class Receipt(
        val baseUrl: String,
        val requestId: String,
        val claimToken: String,
        val acceptedAtMonotonicMs: Long,
    )

    private val lock = Any()
    private val pending = ArrayDeque<Receipt>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in signal) {
                drain()
            }
        }
    }

    /**
     * Synchronously admits one consumed receipt. Returns false when the bounded queue is full; the
     * caller must then fail closed and release the authentication reservation instead of
     * authenticating without a retained receipt.
     */
    fun tryEnqueue(baseUrl: String, requestId: String, claimToken: String): Boolean {
        val admitted = synchronized(lock) {
            if (pending.size >= maxPendingReceipts) {
                false
            } else {
                pending.addLast(
                    Receipt(
                        baseUrl = baseUrl,
                        requestId = requestId,
                        claimToken = claimToken,
                        acceptedAtMonotonicMs = monotonicClock(),
                    ),
                )
                true
            }
        }
        if (admitted) signal.trySend(Unit)
        return admitted
    }

    private suspend fun drain() {
        while (true) {
            val receipt = synchronized(lock) { pending.firstOrNull() } ?: return
            ackWithBudget(receipt)
            synchronized(lock) {
                if (pending.firstOrNull() === receipt) pending.removeFirst()
            }
        }
    }

    private suspend fun ackWithBudget(receipt: Receipt) {
        var backoffMs = INITIAL_ACK_BACKOFF_MS
        for (attempt in 0 until maxAckAttempts) {
            if (receiptAgeExceeded(receipt)) return
            val lastAttempt = attempt == maxAckAttempts - 1
            when (val result = transport.ack(receipt.baseUrl, receipt.requestId, receipt.claimToken)) {
                is PairingResult.Success -> return
                is PairingResult.HttpFailure -> {
                    val waitMs = when {
                        result.retryAfterSeconds != null ->
                            result.retryAfterSeconds.toLong() * 1_000L
                        result.httpStatus == 408 ||
                            result.httpStatus == 425 ||
                            result.httpStatus == 429 ||
                            result.httpStatus >= 500 ->
                            backoffMs.also { backoffMs = minOf(backoffMs * 2L, MAX_ACK_BACKOFF_MS) }
                        else -> return
                    }
                    if (lastAttempt) return
                    if (!canFitWait(receipt, waitMs)) return
                    delayMillis(waitMs)
                }
                PairingResult.TransportFailure -> {
                    if (lastAttempt) return
                    val waitMs = backoffMs
                    backoffMs = minOf(backoffMs * 2L, MAX_ACK_BACKOFF_MS)
                    if (!canFitWait(receipt, waitMs)) return
                    delayMillis(waitMs)
                }
                PairingResult.DiscoveryInvalid,
                PairingResult.InvalidResponse,
                -> return
            }
        }
    }

    private fun receiptAgeExceeded(receipt: Receipt): Boolean =
        monotonicClock() - receipt.acceptedAtMonotonicMs >= maxReceiptAgeMs

    private fun canFitWait(receipt: Receipt, waitMs: Long): Boolean =
        monotonicClock() + waitMs < receipt.acceptedAtMonotonicMs + maxReceiptAgeMs

    private companion object {
        const val MAX_ACK_ATTEMPTS = 6
        const val INITIAL_ACK_BACKOFF_MS = 1_000L
        const val MAX_ACK_BACKOFF_MS = 15_000L
        const val MAX_RECEIPT_AGE_MS = 120_000L
        const val MAX_PENDING_RECEIPTS = 8
    }
}

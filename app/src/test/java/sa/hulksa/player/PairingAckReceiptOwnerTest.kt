package sa.hulksa.player

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.PairingResult
import sa.hulksa.player.data.PairingTransport

class PairingAckReceiptOwnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val delay = ManualDelay()
    private var monotonic = 0L

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `ack retries a transport failure then completes`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.TransportFailure,
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))

        assertEquals(1, transport.ackRequests.size)
        assertEquals(listOf(1_000L), delay.requested)
        delay.release()
        assertEquals(2, transport.ackRequests.size)
        assertEquals(REQUEST_ID to CLAIM_TOKEN, transport.ackRequests.last())
    }

    @Test
    fun `terminal http failure stops without retrying`() {
        val transport = FakeAckTransport(
            listOf(PairingResult.HttpFailure("UNAUTHORIZED", 401, null)),
        )
        val owner = owner(transport)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))

        assertEquals(1, transport.ackRequests.size)
        assertEquals(emptyList<Long>(), delay.requested)
    }

    @Test
    fun `transient server failure retries and honors retry after`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 4),
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))

        assertEquals(listOf(4_000L), delay.requested)
        delay.release()
        assertEquals(2, transport.ackRequests.size)
    }

    @Test
    fun `finite attempt budget bounds the overall receipt work`() {
        val transport = FakeAckTransport(List(10) { PairingResult.TransportFailure })
        val owner = owner(transport, maxAttempts = 3)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))

        assertEquals(1, transport.ackRequests.size)
        delay.release()
        assertEquals(2, transport.ackRequests.size)
        delay.release()
        assertEquals(3, transport.ackRequests.size)
        assertEquals(listOf(1_000L, 2_000L), delay.requested)
    }

    @Test
    fun `receipts are processed in order without overwriting`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.Success("ACKED"),
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))
        assertTrue(owner.tryEnqueue(BASE_URL, SECOND_REQUEST_ID, SECOND_CLAIM_TOKEN))

        assertEquals(
            listOf(REQUEST_ID to CLAIM_TOKEN, SECOND_REQUEST_ID to SECOND_CLAIM_TOKEN),
            transport.ackRequests,
        )
    }

    @Test
    fun `huge retry after retires the first receipt and later receipts proceed`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 3_600),
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))
        assertTrue(owner.tryEnqueue(BASE_URL, SECOND_REQUEST_ID, SECOND_CLAIM_TOKEN))

        assertEquals(
            listOf(REQUEST_ID to CLAIM_TOKEN, SECOND_REQUEST_ID to SECOND_CLAIM_TOKEN),
            transport.ackRequests,
        )
        assertEquals(emptyList<Long>(), delay.requested)
    }

    @Test
    fun `full receipt queue rejects admission instead of dropping silently`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 60),
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport, capacity = 2)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))
        assertTrue(owner.tryEnqueue(BASE_URL, SECOND_REQUEST_ID, SECOND_CLAIM_TOKEN))
        assertFalse(owner.tryEnqueue(BASE_URL, THIRD_REQUEST_ID, THIRD_CLAIM_TOKEN))

        assertEquals(listOf(REQUEST_ID to CLAIM_TOKEN), transport.ackRequests)
        assertEquals(listOf(60_000L), delay.requested)
    }

    @Test
    fun `receipt age includes queue time and retires before any ack`() {
        val transport = FakeAckTransport(
            listOf(
                PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 60),
                PairingResult.Success("ACKED"),
            ),
        )
        val owner = owner(transport, maxAgeMs = 120_000L)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))
        assertTrue(owner.tryEnqueue(BASE_URL, SECOND_REQUEST_ID, SECOND_CLAIM_TOKEN))
        monotonic += 200_000L
        delay.release()

        assertEquals(listOf(REQUEST_ID to CLAIM_TOKEN), transport.ackRequests)
        assertEquals(listOf(60_000L), delay.requested)
    }

    @Test
    fun `wait that cannot fit the age budget retires without retrying earlier`() {
        val transport = FakeAckTransport(List(10) { PairingResult.TransportFailure })
        val owner = owner(transport, maxAgeMs = 500L)

        assertTrue(owner.tryEnqueue(BASE_URL, REQUEST_ID, CLAIM_TOKEN))

        assertEquals(1, transport.ackRequests.size)
        assertEquals(emptyList<Long>(), delay.requested)
    }

    private fun owner(
        transport: PairingTransport,
        maxAttempts: Int = 6,
        maxAgeMs: Long = 120_000L,
        capacity: Int = 8,
    ): PairingAckReceiptOwner = PairingAckReceiptOwner(
        scope = scope,
        transport = transport,
        delayMillis = { ms ->
            delay.delay(ms) {
                monotonic += ms
            }
        },
        monotonicClock = { monotonic },
        maxAckAttempts = maxAttempts,
        maxReceiptAgeMs = maxAgeMs,
        maxPendingReceipts = capacity,
    )

    private class FakeAckTransport(
        private val responses: List<PairingResult<String>>,
    ) : PairingTransport {
        val ackRequests = CopyOnWriteArrayList<Pair<String, String>>()
        private var index = 0

        override suspend fun create(
            baseUrl: String,
            input: sa.hulksa.player.data.PairingCreateInput,
        ): PairingResult<sa.hulksa.player.data.PairingCreated> =
            PairingResult.TransportFailure

        override suspend fun claim(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<sa.hulksa.player.data.PairingClaimOutcome> =
            PairingResult.TransportFailure

        override suspend fun ack(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<String> {
            ackRequests += requestId to claimToken
            return responses[index.coerceAtMost(responses.lastIndex)].also {
                if (index < responses.size - 1) index++
            }
        }

        override suspend fun cancel(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<String> = PairingResult.Success("CANCELLED")
    }

    private class ManualDelay {
        private val channel = Channel<Unit>(Channel.UNLIMITED)
        val requested = CopyOnWriteArrayList<Long>()

        suspend fun delay(ms: Long, onCompleted: () -> Unit) {
            requested += ms
            channel.receive()
            onCompleted()
        }

        fun release() {
            channel.trySend(Unit)
        }
    }

    private companion object {
        const val BASE_URL = "https://hulksa.com/control-center/api/app/v1/pairing/"
        const val REQUEST_ID = "11111111-2222-4333-8444-555555555555"
        const val SECOND_REQUEST_ID = "22222222-3333-4444-8555-666666666666"
        const val THIRD_REQUEST_ID = "33333333-4444-4555-8666-777777777777"
        val CLAIM_TOKEN: String = sa.hulksa.player.data.canonicalBase64Url(ByteArray(32) { 2 })
        val SECOND_CLAIM_TOKEN: String = sa.hulksa.player.data.canonicalBase64Url(ByteArray(32) { 3 })
        val THIRD_CLAIM_TOKEN: String = sa.hulksa.player.data.canonicalBase64Url(ByteArray(32) { 4 })
    }
}

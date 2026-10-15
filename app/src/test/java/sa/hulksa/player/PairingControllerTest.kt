package sa.hulksa.player

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.OperationsPairingConfig
import sa.hulksa.player.data.PairingClaimOutcome
import sa.hulksa.player.data.PairingCreateInput
import sa.hulksa.player.data.PairingCreated
import sa.hulksa.player.data.PairingDeviceInfo
import sa.hulksa.player.data.PairingResult
import sa.hulksa.player.data.PairingTransport
import sa.hulksa.player.data.canonicalBase64Url
import sa.hulksa.player.model.Credentials

class PairingControllerTest {
    private val harness = Harness()

    @After
    fun tearDown() {
        harness.scope.cancel()
    }

    @Test
    fun `hidden phone state issues zero pairing calls`() {
        harness.controller.start(remember = true)

        assertTrue(harness.transport.createInputs.isEmpty())
        assertTrue(harness.transport.claimRequests.isEmpty())
        assertTrue(harness.states.isEmpty())
    }

    @Test
    fun `create uses one generated identity and enters waiting with public fields only`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)

        val input = harness.transport.createInputs.single()
        assertTrue(input.clientRequestId.length == 36)
        assertEquals(43, input.clientSecret.length)
        assertEquals(INSTALLATION_ID, input.installationId)
        assertEquals("Example", input.device.manufacturer)
        val state = harness.states.last()
        assertEquals(PairingPhase.WAITING, state.phase)
        assertTrue(state.visible)
        assertEquals(USER_CODE, state.userCode)
        assertEquals(VERIFICATION_URL, state.verificationUrl)
        assertEquals(600_000L, state.deadlineElapsedRealtimeMs)
        assertTrue(harness.states.none { it.toString().contains(input.clientSecret) })
        assertTrue(harness.states.none { it.toString().contains(CLAIM_TOKEN) })
    }

    @Test
    fun `lost create retry reuses the same request identity and regenerate replaces it`() {
        harness.enableDiscovery()
        harness.transport.createResponse = { PairingResult.TransportFailure }
        harness.controller.start(remember = true)
        val first = harness.transport.createInputs[0]
        assertEquals(PairingCopy.SERVICE_UNAVAILABLE, harness.states.last().message)
        assertTrue(harness.states.last().canRetry)

        harness.controller.retry()
        val retried = harness.transport.createInputs[1]
        assertEquals(first.clientRequestId, retried.clientRequestId)
        assertEquals(first.clientSecret, retried.clientSecret)

        harness.controller.regenerate()
        val regenerated = harness.transport.createInputs[2]
        assertNotEquals(first.clientRequestId, regenerated.clientRequestId)
        assertNotEquals(first.clientSecret, regenerated.clientSecret)
    }

    @Test
    fun `first poll waits the server floor and claims never overlap`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.Success(PairingClaimOutcome.Pending(harness.createdExpiresAt, 3_000L))
        }

        assertEquals(listOf(3_000L), harness.delay.requested)
        harness.delay.release()

        assertEquals(1, harness.transport.claimRequests.size)
        assertEquals(1, harness.transport.maxConcurrentClaims.get())
        assertEquals(3_000L, harness.delay.requested[1])
    }

    @Test
    fun `transient failures back off x2 to the 15s cap and pending follows the server floor`() {
        harness.enableDiscovery()
        harness.transport.claimResponse = { PairingResult.TransportFailure }
        harness.controller.start(remember = true)

        harness.delay.release()
        harness.delay.release()
        harness.delay.release()
        harness.transport.claimResponse = {
            PairingResult.Success(PairingClaimOutcome.Pending(harness.createdExpiresAt, 5_000L))
        }
        harness.delay.release()

        assertEquals(listOf(3_000L, 6_000L, 12_000L, 15_000L, 5_000L), harness.delay.requested)
    }

    @Test
    fun `server directed retry after overrides the backoff cap`() {
        harness.enableDiscovery()
        harness.transport.claimResponse = {
            PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 30)
        }
        harness.controller.start(remember = true)

        harness.delay.release()

        assertEquals(listOf(3_000L, 30_000L), harness.delay.requested)
    }

    @Test
    fun `expiry stops polling and a backward wall shift cannot extend it`() {
        harness.enableDiscovery()
        harness.createdExpiresInMs = 5_000L
        harness.controller.start(remember = true)

        harness.delay.release()
        harness.wallNow -= 3_600_000L
        harness.delay.release()

        assertEquals(listOf(3_000L, 2_000L), harness.delay.requested)
        assertEquals(1, harness.transport.claimRequests.size)
        assertEquals(PairingCopy.EXPIRED, harness.states.last().message)
        assertTrue(harness.states.last().canRegenerate)
    }

    @Test
    fun `approved delivery consumes once with captured remember and enqueues one ack`() {
        harness.enableDiscovery()
        harness.controller.start(remember = false)
        harness.transport.claimResponse = { PairingResult.Success(harness.approved()) }

        harness.delay.release()

        val launch = harness.handoff.launches.single()
        assertEquals(CREDENTIALS, launch.first)
        assertEquals(false, launch.second)
        assertEquals(7L, launch.third)
        val ack = harness.ackEnqueues.single()
        assertEquals(REQUEST_ID, ack.second)
        assertEquals(CLAIM_TOKEN, ack.third)
        assertFalse(harness.states.last().visible)
        assertEquals(1, harness.transport.claimRequests.size)
        assertEquals(listOf(3_000L), harness.delay.requested)
    }

    @Test
    fun `busy authentication gate fails closed without a second login or ack`() {
        harness.enableDiscovery()
        harness.handoff.reserveResult = null
        harness.controller.start(remember = true)
        harness.transport.claimResponse = { PairingResult.Success(harness.approved()) }

        harness.delay.release()

        assertTrue(harness.handoff.launches.isEmpty())
        assertTrue(harness.ackEnqueues.isEmpty())
        assertFalse(harness.states.last().visible)
        assertEquals(1, harness.transport.cancelRequests.size)
    }

    @Test
    fun `manual cancel drops late approved bytes and cannot log in`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.controller.cancel()
        harness.transport.claimResponse = { PairingResult.Success(harness.approved()) }

        harness.delay.release()

        assertTrue(harness.transport.claimRequests.isEmpty())
        assertTrue(harness.handoff.launches.isEmpty())
        assertTrue(harness.ackEnqueues.isEmpty())
        assertEquals(1, harness.transport.cancelRequests.size)
    }

    @Test
    fun `stale create success after cancel is discarded with bounded cleanup`() {
        harness.enableDiscovery()
        harness.transport.createResponse = {
            harness.controller.cancel()
            PairingResult.Success(harness.created())
        }

        harness.controller.start(remember = true)

        assertTrue(harness.handoff.launches.isEmpty())
        assertTrue(harness.ackEnqueues.isEmpty())
        assertTrue(harness.states.none { it.userCode != null })
        assertEquals(1, harness.transport.cancelRequests.size)
        assertTrue(harness.transport.cancelRequests.any { it.first == REQUEST_ID })
    }

    @Test
    fun `terminal denial stops polling with safe copy and no cleanup cancel`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.Success(PairingClaimOutcome.Terminal("DENIED"))
        }

        harness.delay.release()

        assertEquals(PairingCopy.DENIED, harness.states.last().message)
        assertTrue(harness.states.last().canRegenerate)
        assertTrue(harness.transport.claimRequests.size == 1)
        assertTrue(harness.transport.cancelRequests.isEmpty())
    }

    @Test
    fun `pairing disabled stops the attempt without regenerating`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.HttpFailure("PAIRING_DISABLED", 404, retryAfterSeconds = null)
        }

        harness.delay.release()

        assertEquals(PairingCopy.DISABLED, harness.states.last().message)
        assertFalse(harness.states.last().canRegenerate)
        assertTrue(harness.transport.cancelRequests.isEmpty())
    }

    @Test
    fun `discovery loss and background lifecycle stop pending work`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)

        harness.controller.updateDiscovery(null)
        harness.delay.release()

        assertTrue(harness.transport.claimRequests.isEmpty())
        assertFalse(harness.states.last().available)
        assertFalse(harness.states.last().visible)
        assertEquals(1, harness.transport.cancelRequests.size)
    }

    @Test
    fun `installation identifier failure fails closed before create`() {
        harness.enableDiscovery()
        harness.installationId = null

        harness.controller.start(remember = true)

        assertTrue(harness.transport.createInputs.isEmpty())
        assertEquals(PairingCopy.INSTALLATION_FAILED, harness.states.last().message)
        assertTrue(harness.states.last().canRetry)
    }

    @Test
    fun `bounded countdown and jitter helpers stay within their contract`() {
        assertEquals(900_000L, pairingBoundedRemainingMs(10_000_000L, 0L))
        assertEquals(0L, pairingBoundedRemainingMs(1_000L, 5_000L))
        assertEquals(5_000L, pairingBoundedRemainingMs(5_000L, 0L))
        assertEquals(3_000L, pairingJitteredDelayMs(3_000L, 3_000L, -0.2))
        assertEquals(3_600L, pairingJitteredDelayMs(3_000L, 3_000L, 0.2))
        assertEquals(5_000L, pairingJitteredDelayMs(3_000L, 5_000L, 0.0))
        assertEquals(12_000L, pairingJitteredDelayMs(10_000L, 1_000L, 5.0))
    }

    @Test
    fun `held approved delivery crossing the local deadline never authenticates`() {
        harness.enableDiscovery()
        harness.createdExpiresInMs = 5_000L
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            harness.advance(10_000L)
            PairingResult.Success(harness.approved())
        }

        harness.delay.release()

        assertTrue(harness.handoff.launches.isEmpty())
        assertTrue(harness.ackEnqueues.isEmpty())
        assertEquals(PairingCopy.EXPIRED, harness.states.last().message)
    }

    @Test
    fun `approved delivery above the negotiated delivery bound never authenticates`() {
        harness.enableDiscovery()
        harness.createdMaxDeliveries = 1
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.Success(harness.approved(deliveryCount = 2))
        }

        harness.delay.release()

        assertTrue(harness.handoff.launches.isEmpty())
        assertTrue(harness.ackEnqueues.isEmpty())
        assertEquals(PairingCopy.FAILED, harness.states.last().message)
    }

    @Test
    fun `tighter server expiry tightens the same monotonic deadline`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.Success(
                PairingClaimOutcome.Pending(
                    expiresAtEpochMs = harness.wallNow + 1_000L,
                    retryAfterMs = 3_000L,
                ),
            )
        }

        harness.delay.release()
        assertEquals(listOf(3_000L, 1_000L), harness.delay.requested)
        harness.delay.release()

        assertEquals(1, harness.transport.claimRequests.size)
        assertEquals(PairingCopy.EXPIRED, harness.states.last().message)
    }

    @Test
    fun `create cooldown blocks retry regenerate and reopen until the server window passes`() {
        harness.enableDiscovery()
        harness.transport.createResponse = {
            PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 60)
        }
        harness.controller.start(remember = true)
        assertEquals(1, harness.transport.createInputs.size)
        assertEquals(PairingCopy.THROTTLED, harness.states.last().message)

        harness.controller.retry()
        harness.controller.regenerate()
        harness.controller.cancel()
        harness.controller.start(remember = true)
        assertEquals(1, harness.transport.createInputs.size)

        harness.advance(60_000L)
        harness.controller.retry()
        assertEquals(2, harness.transport.createInputs.size)
    }

    @Test
    fun `claim retry after below the server floor still waits the floor`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 1)
        }

        harness.delay.release()

        assertEquals(listOf(3_000L, 3_000L), harness.delay.requested)
    }

    @Test
    fun `server directed claim delay is never shortened to the local cap`() {
        harness.enableDiscovery()
        harness.controller.start(remember = true)
        harness.transport.claimResponse = {
            PairingResult.HttpFailure("RATE_LIMITED", 429, retryAfterSeconds = 30)
        }

        harness.delay.release()

        assertEquals(listOf(3_000L, 30_000L), harness.delay.requested)
    }

    @Test
    fun `receipt admission failure releases the reservation without authenticating`() {
        harness.enableDiscovery()
        harness.ackAdmission = false
        harness.controller.start(remember = true)
        harness.transport.claimResponse = { PairingResult.Success(harness.approved()) }

        harness.delay.release()

        assertTrue(harness.handoff.launches.isEmpty())
        assertEquals(listOf(7L), harness.handoff.releases)
        assertFalse(harness.states.last().visible)
        assertEquals(1, harness.transport.cancelRequests.size)
    }

    private inner class Harness {
        val transport = FakePairingTransport()
        val handoff = FakeHandoff()
        val delay = ManualDelay()
        val states = CopyOnWriteArrayList<PairingUiState>()
        val ackEnqueues = CopyOnWriteArrayList<Triple<String, String, String>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var wallNow = 1_770_000_000_000L
        var monotonic = 0L
        var installationId: String? = INSTALLATION_ID
        var createdExpiresInMs = 600_000L
        var createdMaxDeliveries = 20
        var ackAdmission = true
        private var lastCreatedExpiresAt = 0L
        val createdExpiresAt: Long get() = lastCreatedExpiresAt
        var uuidCounter = 0
        private var secretCounter = 0

        init {
            transport.createResponse = { PairingResult.Success(created()) }
        }

        val controller = PairingController(
            transport = transport,
            scope = scope,
            handoff = handoff,
            installationIdProvider = { installationId },
            deviceInfoProvider = {
                PairingDeviceInfo("Example", "ExampleTV", "0.9.3.23")
            },
            onConsumedAck = { baseUrl, requestId, claimToken ->
                ackEnqueues += Triple(baseUrl, requestId, claimToken)
                ackAdmission
            },
            onState = { states += it },
            wallClock = { wallNow },
            monotonicClock = { monotonic },
            randomUuid = { "00000000-0000-4000-8000-%012d".format(++uuidCounter) },
            randomSecret = { "%043d".format(++secretCounter).takeLast(43) },
            jitterFraction = { 0.0 },
            delayMillis = { ms ->
                delay.delay(ms) {
                    advance(ms)
                }
            },
        )

        fun advance(ms: Long) {
            monotonic += ms
            wallNow += ms
        }

        fun enableDiscovery() {
            controller.updateDiscovery(
                OperationsPairingConfig(baseUrl = "https://hulksa.com/control-center/api/app/v1/pairing/"),
            )
        }

        fun created(): PairingCreated {
            val expiry = wallNow + createdExpiresInMs
            lastCreatedExpiresAt = expiry
            return PairingCreated(
                requestId = REQUEST_ID,
                userCode = USER_CODE,
                claimToken = CLAIM_TOKEN,
                expiresAtEpochMs = expiry,
                verificationUrl = VERIFICATION_URL,
                pollIntervalSeconds = 3,
                maxDeliveries = createdMaxDeliveries,
            )
        }

        fun approved(deliveryCount: Int = 1): PairingClaimOutcome.Approved =
            PairingClaimOutcome.Approved(
                expiresAtEpochMs = createdExpiresAt,
                retryAfterMs = 3_000L,
                deliveryCount = deliveryCount,
                credentials = CREDENTIALS,
            )
    }

    private class FakePairingTransport : PairingTransport {
        val createInputs = CopyOnWriteArrayList<PairingCreateInput>()
        val claimRequests = CopyOnWriteArrayList<Pair<String, String>>()
        val cancelRequests = CopyOnWriteArrayList<Pair<String, String>>()
        val ackRequests = CopyOnWriteArrayList<Pair<String, String>>()
        val inFlightClaims = AtomicInteger()
        val maxConcurrentClaims = AtomicInteger()

        @Volatile
        var createResponse: (PairingCreateInput) -> PairingResult<PairingCreated> = {
            PairingResult.TransportFailure
        }

        @Volatile
        var claimResponse: () -> PairingResult<PairingClaimOutcome> = {
            PairingResult.TransportFailure
        }

        override suspend fun create(
            baseUrl: String,
            input: PairingCreateInput,
        ): PairingResult<PairingCreated> {
            createInputs += input
            return createResponse(input)
        }

        override suspend fun claim(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<PairingClaimOutcome> {
            val running = inFlightClaims.incrementAndGet()
            maxConcurrentClaims.updateAndGet { current -> maxOf(current, running) }
            try {
                claimRequests += requestId to claimToken
                return claimResponse()
            } finally {
                inFlightClaims.decrementAndGet()
            }
        }

        override suspend fun ack(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<String> {
            ackRequests += requestId to claimToken
            return PairingResult.Success("ACKED")
        }

        override suspend fun cancel(
            baseUrl: String,
            requestId: String,
            claimToken: String,
        ): PairingResult<String> {
            cancelRequests += requestId to claimToken
            return PairingResult.Success("CANCELLED")
        }
    }

    private class FakeHandoff : PairingAuthHandoff {
        val launches = CopyOnWriteArrayList<Triple<Credentials, Boolean, Long>>()
        val releases = CopyOnWriteArrayList<Long>()

        @Volatile
        var reserveResult: Long? = 7L

        override fun tryReserve(): Long? = reserveResult

        override fun launch(credentials: Credentials, remember: Boolean, reservation: Long) {
            launches += Triple(credentials, remember, reservation)
        }

        override fun release(reservation: Long) {
            releases += reservation
        }
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
        const val INSTALLATION_ID = "99999999-8888-4777-8666-555555555555"
        const val REQUEST_ID = "11111111-2222-4333-8444-555555555555"
        const val USER_CODE = "HULKTV-ABCDEF"
        val CLAIM_TOKEN: String = canonicalBase64Url(ByteArray(32) { 2 })
        const val VERIFICATION_URL = "https://hulksa.com/control-center/pair/#C=HULKTV-ABCDEF"
        val CREDENTIALS = Credentials(accessCode = "CODE", username = "user", password = "pass")
    }
}

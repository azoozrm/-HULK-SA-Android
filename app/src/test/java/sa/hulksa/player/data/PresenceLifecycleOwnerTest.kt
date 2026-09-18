package sa.hulksa.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceLifecycleOwnerTest {
    @Test
    fun `enabled discovery without successful authentication never starts`() = runBlocking {
        val fixture = fixture()
        try {
            fixture.owner.updateDiscovery(CONFIG)
            yield()
            assertNull(fixture.transport.starts.tryReceive().getOrNull())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `disabled discovery never starts and enabled discovery starts current session once`() = runBlocking {
        val fixture = fixture()
        try {
            fixture.owner.onAuthenticated(snapshot(SESSION_A))
            yield()
            assertNull(fixture.transport.starts.tryReceive().getOrNull())

            fixture.transport.startResults.send(accepted(SESSION_A))
            fixture.owner.updateDiscovery(CONFIG)
            assertEquals(SESSION_A, receive(fixture.transport.starts).sessionId)
            fixture.owner.onAuthenticated(snapshot(SESSION_A))
            yield()
            assertNull(fixture.transport.starts.tryReceive().getOrNull())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `transient start gets one bounded retry and failure remains isolated`() = runBlocking {
        val retryDelays = Channel<Long>(Channel.UNLIMITED)
        val fixture = fixture(retryDelay = { retryDelays.send(it) })
        try {
            fixture.transport.startResults.send(PresenceStartResult.TransientFailure)
            fixture.transport.startResults.send(PresenceStartResult.TransientFailure)
            fixture.owner.updateDiscovery(CONFIG)
            fixture.owner.onAuthenticated(snapshot(SESSION_A))

            assertEquals(SESSION_A, receive(fixture.transport.starts).sessionId)
            assertEquals(1_000L, receive(retryDelays))
            assertEquals(SESSION_A, receive(fixture.transport.starts).sessionId)
            yield()
            assertNull(fixture.transport.starts.tryReceive().getOrNull())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `replacement cancels old owner ends best effort and starts only new identity`() = runBlocking {
        val heartbeatDelays = Channel<Long>(Channel.UNLIMITED)
        val heartbeatRelease = Channel<Unit>(Channel.RENDEZVOUS)
        val fixture = fixture(
            heartbeatDelay = { millis ->
                heartbeatDelays.send(millis)
                heartbeatRelease.receive()
            },
        )
        try {
            fixture.transport.startResults.send(accepted(SESSION_A))
            fixture.transport.startResults.send(accepted(SESSION_B))
            fixture.transport.endResults.send(PresenceCommandResult.Accepted)
            fixture.owner.updateDiscovery(CONFIG)
            fixture.owner.onAuthenticated(snapshot(SESSION_A))
            assertEquals(SESSION_A, receive(fixture.transport.starts).sessionId)
            assertEquals(60_000L, receive(heartbeatDelays))

            fixture.owner.onAuthenticated(snapshot(SESSION_B))
            val ended = receive(fixture.transport.ends)
            assertEquals(SESSION_A, ended.sessionId)
            assertEquals(PresenceEndReason.ACCOUNT_REPLACED, ended.reason)
            assertEquals(SESSION_B, receive(fixture.transport.starts).sessionId)
            assertEquals(60_000L, receive(heartbeatDelays))
            assertNull(fixture.transport.heartbeats.tryReceive().getOrNull())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `terminal heartbeat stops owner while transient failure stays on scheduled cadence`() = runBlocking {
        val heartbeatDelays = Channel<Long>(Channel.UNLIMITED)
        val heartbeatRelease = Channel<Unit>(Channel.UNLIMITED)
        val fixture = fixture(
            heartbeatDelay = { millis ->
                heartbeatDelays.send(millis)
                heartbeatRelease.receive()
            },
        )
        try {
            fixture.transport.startResults.send(accepted(SESSION_A))
            fixture.transport.heartbeatResults.send(PresenceCommandResult.TransientFailure)
            fixture.transport.heartbeatResults.send(PresenceCommandResult.TerminalFailure)
            fixture.owner.updateDiscovery(CONFIG)
            fixture.owner.onAuthenticated(snapshot(SESSION_A))
            receive(fixture.transport.starts)

            assertEquals(60_000L, receive(heartbeatDelays))
            heartbeatRelease.send(Unit)
            assertEquals(SESSION_A, receive(fixture.transport.heartbeats))
            assertEquals(60_000L, receive(heartbeatDelays))
            heartbeatRelease.send(Unit)
            assertEquals(SESSION_A, receive(fixture.transport.heartbeats))
            yield()
            assertNull(heartbeatDelays.tryReceive().getOrNull())
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `logout ends captured owner and never waits on telemetry success`() = runBlocking {
        val heartbeatStarted = Channel<Long>(Channel.UNLIMITED)
        val fixture = fixture(heartbeatDelay = { heartbeatStarted.send(it); kotlinx.coroutines.awaitCancellation() })
        try {
            fixture.transport.startResults.send(accepted(SESSION_A))
            fixture.transport.throwOnEnd = true
            fixture.owner.updateDiscovery(CONFIG)
            fixture.owner.onAuthenticated(snapshot(SESSION_A))
            receive(fixture.transport.starts)
            receive(heartbeatStarted)

            fixture.owner.logout()
            val end = receive(fixture.transport.ends)
            assertEquals(SESSION_A, end.sessionId)
            assertEquals(PresenceEndReason.LOGOUT, end.reason)
            fixture.owner.onAuthenticated(snapshot(SESSION_B))
            assertEquals(SESSION_B, receive(fixture.transport.starts).sessionId)
        } finally {
            fixture.scope.cancel()
        }
    }

    @Test
    fun `stale start callback cannot overwrite replacement owner`() = runBlocking {
        val oldRelease = Channel<Unit>(Channel.RENDEZVOUS)
        val heartbeatRelease = Channel<Unit>(Channel.RENDEZVOUS)
        val transport = StaleStartTransport(oldRelease)
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val owner = PresenceLifecycleOwner(
            scope = scope,
            transport = transport,
            heartbeatDelay = { heartbeatRelease.receive() },
        )
        try {
            owner.updateDiscovery(CONFIG)
            owner.onAuthenticated(snapshot(SESSION_A))
            assertEquals(SESSION_A, receive(transport.starts))
            owner.onAuthenticated(snapshot(SESSION_B))
            assertEquals(SESSION_B, receive(transport.starts))

            oldRelease.send(Unit)
            heartbeatRelease.send(Unit)
            assertEquals(SESSION_B, receive(transport.heartbeats))
        } finally {
            scope.cancel()
        }
    }

    private fun CoroutineScope.fixture(
        retryDelay: suspend (Long) -> Unit = {},
        heartbeatDelay: suspend (Long) -> Unit = { kotlinx.coroutines.awaitCancellation() },
    ): Fixture {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val transport = FakeTransport()
        return Fixture(
            scope = scope,
            transport = transport,
            owner = PresenceLifecycleOwner(scope, transport, retryDelay, heartbeatDelay),
        )
    }

    private suspend fun <T> receive(channel: Channel<T>): T = withTimeout(5_000L) {
        channel.receive()
    }

    private fun accepted(sessionId: String) = PresenceStartResult.Accepted(
        PresenceStartAccepted(
            sessionId = sessionId,
            token = TOKEN,
            heartbeatSeconds = 60,
            onlineTtlSeconds = 180,
            serverTimeEpochSeconds = 1_770_000_000L,
        ),
    )

    private fun snapshot(sessionId: String) = PresenceSessionSnapshot(
        sessionId = sessionId,
        installationId = INSTALLATION_ID,
        accessCode = "TEST-CODE",
        iptvUsername = "synthetic-user",
        iptvPassword = "synthetic-password",
        host = "https://iptv.invalid",
        authenticatedAtEpochMs = 1_770_000_000_000L,
        device = PresenceDeviceMetadata(
            PresencePlatformClass.PHONE,
            "Synthetic",
            "Test Device",
            "15",
            35,
        ),
        app = PresenceAppMetadata("0.9.3.20", 64),
    )

    private data class Fixture(
        val scope: CoroutineScope,
        val transport: FakeTransport,
        val owner: PresenceLifecycleOwner,
    )

    private data class EndCall(val sessionId: String, val reason: PresenceEndReason)

    private class FakeTransport : PresenceTransport {
        val starts = Channel<PresenceSessionSnapshot>(Channel.UNLIMITED)
        val startResults = Channel<PresenceStartResult>(Channel.UNLIMITED)
        val heartbeats = Channel<String>(Channel.UNLIMITED)
        val heartbeatResults = Channel<PresenceCommandResult>(Channel.UNLIMITED)
        val ends = Channel<EndCall>(Channel.UNLIMITED)
        val endResults = Channel<PresenceCommandResult>(Channel.UNLIMITED)
        var throwOnEnd: Boolean = false

        override suspend fun start(
            config: OperationsPresenceConfig,
            snapshot: PresenceSessionSnapshot,
        ): PresenceStartResult {
            starts.send(snapshot)
            return startResults.receive()
        }

        override suspend fun heartbeat(
            config: OperationsPresenceConfig,
            sessionId: String,
            token: String,
        ): PresenceCommandResult {
            heartbeats.send(sessionId)
            return heartbeatResults.receive()
        }

        override suspend fun end(
            config: OperationsPresenceConfig,
            sessionId: String,
            token: String,
            reason: PresenceEndReason,
        ): PresenceCommandResult {
            ends.send(EndCall(sessionId, reason))
            if (throwOnEnd) throw IllegalStateException("synthetic end failure")
            return endResults.receive()
        }
    }

    private class StaleStartTransport(
        private val oldRelease: Channel<Unit>,
    ) : PresenceTransport {
        val starts = Channel<String>(Channel.UNLIMITED)
        val heartbeats = Channel<String>(Channel.UNLIMITED)
        private var startCount = 0

        override suspend fun start(
            config: OperationsPresenceConfig,
            snapshot: PresenceSessionSnapshot,
        ): PresenceStartResult {
            starts.send(snapshot.sessionId)
            startCount += 1
            if (startCount == 1) {
                withContext(NonCancellable) { oldRelease.receive() }
            }
            return PresenceStartResult.Accepted(
                PresenceStartAccepted(
                    sessionId = snapshot.sessionId,
                    token = TOKEN,
                    heartbeatSeconds = 60,
                    onlineTtlSeconds = 180,
                    serverTimeEpochSeconds = 1_770_000_000L,
                ),
            )
        }

        override suspend fun heartbeat(
            config: OperationsPresenceConfig,
            sessionId: String,
            token: String,
        ): PresenceCommandResult {
            heartbeats.send(sessionId)
            return PresenceCommandResult.TerminalFailure
        }

        override suspend fun end(
            config: OperationsPresenceConfig,
            sessionId: String,
            token: String,
            reason: PresenceEndReason,
        ): PresenceCommandResult = PresenceCommandResult.Accepted
    }

    private companion object {
        val CONFIG = OperationsPresenceConfig(
            baseUrl = "https://hulksa.com/control-center/api/app/v1/presence/",
            heartbeatSeconds = 60,
            onlineTtlSeconds = 180,
        )
        const val SESSION_A = "11111111-1111-4111-8111-111111111111"
        const val SESSION_B = "33333333-3333-4333-8333-333333333333"
        const val INSTALLATION_ID = "22222222-2222-4222-8222-222222222222"
        val TOKEN = "t".repeat(43)
    }
}

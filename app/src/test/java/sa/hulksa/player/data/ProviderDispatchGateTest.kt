package sa.hulksa.player.data

import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/** R32 startup dispatch coordination: one active provider request, catalog priority, safe release. */
class ProviderDispatchGateTest {
    private fun session(
        host: String = "https://provider.example:8443/live",
        user: String = "user",
    ) = AuthenticatedSession(
        portal = PortalConfig(host, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", user, "pass"),
        account = AccountInfo(user, "Active", null, 1, 1, false),
    )

    @Test
    fun `catalog holds the only startup permit while metadata waits`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val catalogStarted = CompletableDeferred<Unit>()
        val releaseCatalog = CompletableDeferred<Unit>()
        gate.beginRound(session)

        val catalog = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) {
                order += "catalog"
                catalogStarted.complete(Unit)
                releaseCatalog.await()
            }
        }
        withTimeout(2_000) { catalogStarted.await() }

        val metadata = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { order += "metadata" }
        }
        delay(100)
        assertEquals(listOf("catalog"), order.toList())

        releaseCatalog.complete(Unit)
        catalog.await()
        metadata.await()
        assertEquals(listOf("catalog", "metadata"), order.toList())
    }

    @Test
    fun `catalog jumps ahead of already queued metadata`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val catalogStarted = CompletableDeferred<Unit>()
        val releaseCatalog = CompletableDeferred<Unit>()
        gate.beginRound(session)

        val firstCatalog = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) {
                order += "catalog-1"
                catalogStarted.complete(Unit)
                releaseCatalog.await()
            }
        }
        withTimeout(2_000) { catalogStarted.await() }

        val metadata = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { order += "metadata" }
        }
        delay(50)
        val secondCatalog = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) { order += "catalog-2" }
        }
        delay(50)

        releaseCatalog.complete(Unit)
        listOf(firstCatalog, metadata, secondCatalog).awaitAll()
        assertEquals(listOf("catalog-1", "catalog-2", "metadata"), order.toList())
    }

    @Test
    fun `ending the round drains queued work one at a time`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        gate.beginRound(session)

        val first = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) {
                order += "first"
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        withTimeout(2_000) { firstStarted.await() }
        val second = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { order += "second" }
        }
        delay(50)
        gate.endRound(session)
        releaseFirst.complete(Unit)

        first.await()
        second.await()
        assertEquals(listOf("first", "second"), order.toList())
    }

    @Test
    fun `cancelling a waiter releases the queue for the next waiter`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gate.beginRound(session)

        val holder = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) {
                order += "holder"
                started.complete(Unit)
                release.await()
            }
        }
        withTimeout(2_000) { started.await() }
        val cancelled = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { order += "cancelled" }
        }
        delay(50)
        cancelled.cancel()
        val next = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { order += "next" }
        }
        delay(50)
        release.complete(Unit)

        holder.await()
        runCatching { cancelled.await() }
        next.await()
        assertEquals(listOf("holder", "next"), order.toList())
    }

    @Test
    fun `invalidation cancels queued waiters and stops gating`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gate.beginRound(session)

        val holder = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = true) {
                started.complete(Unit)
                release.await()
            }
        }
        withTimeout(2_000) { started.await() }
        val queued = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) { error("must not run") }
        }
        delay(50)
        gate.invalidate()

        val cancelled = runCatching { withTimeout(2_000) { queued.await() } }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
        release.complete(Unit)
        holder.await()

        // After invalidation the gate is inert: a new request runs without a permit.
        gate.withPermit(session, null, priority = false) { orderMarker = true }
        assertTrue(orderMarker)
    }

    private var orderMarker = false

    @Test
    fun `no startup round means ordinary concurrent dispatch`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val both = CompletableDeferred<Unit>()
        val bothStarted = CompletableDeferred<Unit>()
        var active = 0
        var maxActive = 0
        val first = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) {
                synchronized(this@ProviderDispatchGateTest) {
                    active += 1
                    maxActive = maxOf(maxActive, active)
                    if (active == 2) bothStarted.complete(Unit)
                }
                both.await()
                synchronized(this@ProviderDispatchGateTest) { active -= 1 }
            }
        }
        val second = async(Dispatchers.Default) {
            gate.withPermit(session, null, priority = false) {
                synchronized(this@ProviderDispatchGateTest) {
                    active += 1
                    maxActive = maxOf(maxActive, active)
                    if (active == 2) bothStarted.complete(Unit)
                }
                both.await()
                synchronized(this@ProviderDispatchGateTest) { active -= 1 }
            }
        }
        withTimeout(2_000) { bothStarted.await() }
        both.complete(Unit)
        listOf(first, second).awaitAll()
        assertEquals(2, maxActive)
    }

    @Test
    fun `another provider or account is not blocked by this round`() = runBlocking {
        val gate = ProviderDispatchGate()
        val first = session()
        val otherAccount = session(user = "other")
        val otherProvider = session(host = "https://provider-b.example/live")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gate.beginRound(first)

        val holder = async(Dispatchers.Default) {
            gate.withPermit(first, null, priority = true) {
                started.complete(Unit)
                release.await()
            }
        }
        withTimeout(2_000) { started.await() }
        gate.withPermit(otherAccount, null, priority = false) { orderMarker = true }
        gate.withPermit(otherProvider, null, priority = false) { orderMarker = true }
        assertTrue(orderMarker)
        release.complete(Unit)
        holder.await()
    }

    @Test
    fun `stale owner is rejected before dispatch`() = runBlocking {
        val gate = ProviderDispatchGate()
        val session = session()
        val stale = AuthenticatedSessionOwner(
            accountId = "a",
            providerId = "p",
            sessionId = "s",
            session = session,
        )
        gate.beginRound(session)
        assertThrows(CancellationException::class.java) {
            runBlocking { gate.withPermit(session, stale, priority = true) { orderMarker = true } }
        }
        assertFalse(orderMarker)
    }
}

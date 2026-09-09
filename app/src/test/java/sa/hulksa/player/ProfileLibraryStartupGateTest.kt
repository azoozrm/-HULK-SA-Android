package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.runProfileRoutingStartupOffMain
import sa.hulksa.player.data.runUserLibraryStartupOffMain
import sa.hulksa.player.data.isAllowedKidsHistoryEntry
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PortalConfig

class ProfileLibraryStartupGateTest {
    @Test
    fun `routing startup work runs off the composition caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runProfileRoutingStartupOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            "routing"
        }

        assertEquals("routing", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun `library startup migration and json work runs off the ViewModel caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runUserLibraryStartupOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            "library"
        }

        assertEquals("library", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun `startup dispatch does not turn cancellation into a successful result`() = runBlocking {
        var cancellationObserved = false

        try {
            runUserLibraryStartupOffMain<Unit> {
                throw CancellationException("profile startup cancelled")
            }
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
    }

    @Test
    fun `startup publishes only after its authoritative attempt completes`() {
        val gate = ProfileLibraryStartupGate()
        val session = session("session-a")
        val attempt = gate.start(session, accountId = "account-a", profileId = "adult")

        assertTrue(gate.isCurrent(attempt, session))
        assertTrue(gate.completeIfCurrent(attempt, session))
        assertFalse(gate.isCurrent(attempt, session))
    }

    @Test
    fun `profile or session changes suppress stale startup publication`() {
        val gate = ProfileLibraryStartupGate()
        val firstSession = session("session-a")
        val older = gate.start(firstSession, accountId = "account-a", profileId = "adult")
        val newerProfile = gate.start(firstSession, accountId = "account-a", profileId = "kids")

        assertFalse(gate.completeIfCurrent(older, firstSession))
        assertTrue(gate.completeIfCurrent(newerProfile, firstSession))

        val priorSessionAttempt = gate.start(firstSession, accountId = "account-a", profileId = "kids")
        val replacementSession = session("session-b")
        gate.start(replacementSession, accountId = "account-b", profileId = "adult")

        assertFalse(gate.completeIfCurrent(priorSessionAttempt, firstSession))
    }

    @Test
    fun `kids startup history remains fail closed without an authoritative allow list`() {
        val entry = HistoryEntry(
            key = "MOVIE:10",
            title = "Kids item",
            posterUrl = null,
            streamKind = "movie",
            streamId = 10,
            extension = "mp4",
            isLive = false,
            positionMs = 1_000L,
            durationMs = 10_000L,
            updatedAtEpochMs = 1L,
        )

        assertFalse(isAllowedKidsHistoryEntry(emptySet(), entry))
        assertTrue(isAllowedKidsHistoryEntry(setOf("MOVIE:10"), entry))
    }

    private fun session(username: String): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig("http://provider.test", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", username, "secret"),
        account = AccountInfo(username, "Active", null, 0, 1, false),
    )
}

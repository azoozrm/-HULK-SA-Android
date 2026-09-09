package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class DiagnosticsCoordinatorTest {
    @Test
    fun `cancellation is rethrown without becoming a diagnostics failure`() = runBlocking {
        val coordinator = DiagnosticsCoordinator()
        val session = session("session-a")
        val attempt = checkNotNull(coordinator.tryStart(session))

        try {
            coordinator.runCurrent(attempt, { session }) {
                throw CancellationException("logout")
            }
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow.
        }

        assertNotNull(coordinator.tryStart(session))
    }

    @Test
    fun `logout invalidation suppresses late progress and result publication`() = runBlocking {
        val coordinator = DiagnosticsCoordinator()
        val session = session("session-a")
        val attempt = checkNotNull(coordinator.tryStart(session))
        var currentSession: AuthenticatedSession? = session
        var progressPublicationCount = 0
        var resultPublicationCount = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val lateOutcome = async {
            coordinator.runCurrent(attempt, { currentSession }) {
                started.complete(Unit)
                release.await()
                if (coordinator.isCurrent(attempt, currentSession)) {
                    progressPublicationCount++
                }
                "late report"
            }
        }
        started.await()

        coordinator.invalidate()
        currentSession = null
        release.complete(Unit)
        val outcome = lateOutcome.await()
        if (outcome != null) resultPublicationCount++

        assertEquals(0, progressPublicationCount)
        assertEquals(0, resultPublicationCount)
        assertNull(outcome)
    }

    @Test
    fun `older run cannot replace a newer diagnostics attempt`() = runBlocking {
        val coordinator = DiagnosticsCoordinator()
        val session = session("session-a")
        val older = checkNotNull(coordinator.tryStart(session))
        val olderStarted = CompletableDeferred<Unit>()
        val releaseOlder = CompletableDeferred<Unit>()
        val olderOutcome = async {
            coordinator.runCurrent(older, { session }) {
                olderStarted.complete(Unit)
                releaseOlder.await()
                "old report"
            }
        }
        olderStarted.await()

        coordinator.invalidate()
        val newer = checkNotNull(coordinator.tryStart(session))

        assertFalse(coordinator.isCurrent(older, session))
        assertTrue(coordinator.isCurrent(newer, session))
        val outcome = coordinator.runCurrent(newer, { session }) { "new report" }
        releaseOlder.complete(Unit)

        assertNull(olderOutcome.await())
        assertTrue(outcome is DiagnosticsOutcome.Success)
        assertEquals("new report", (outcome as DiagnosticsOutcome.Success).value)
    }

    @Test
    fun `real failure from current session remains publishable`() = runBlocking {
        val coordinator = DiagnosticsCoordinator()
        val session = session("session-a")
        val attempt = checkNotNull(coordinator.tryStart(session))
        val failure = IllegalStateException("diagnostics unavailable")

        val outcome = coordinator.runCurrent<String>(attempt, { session }) { throw failure }

        assertTrue(outcome is DiagnosticsOutcome.Failure)
        assertSame(failure, (outcome as DiagnosticsOutcome.Failure).error)
    }

    private fun session(username: String): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig("http://provider.test", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", username, "secret"),
        account = AccountInfo(username, "Active", null, 0, 1, false),
    )
}

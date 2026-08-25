package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRefreshCoordinatorTest {
    @Test
    fun `cancellation is rethrown without delivering a failure result`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attempt = checkNotNull(coordinator.tryStart())
        var callbackCount = 0

        try {
            val outcome = coordinator.runCurrent(attempt) {
                throw CancellationException("stale refresh")
            }
            if (outcome != null) callbackCount++
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow.
        }

        assertEquals(0, callbackCount)
        assertNotNull(coordinator.tryStart())
    }

    @Test
    fun `stale refresh A cannot release current refresh B`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attemptA = checkNotNull(coordinator.tryStart())
        coordinator.invalidate()
        val attemptB = checkNotNull(coordinator.tryStart())

        val staleOutcome = coordinator.runCurrent(attemptA) { "A" }

        assertNull(staleOutcome)
        assertNull(coordinator.tryStart())

        val currentOutcome = coordinator.runCurrent(attemptB) { "B" }
        assertTrue(currentOutcome is AccountRefreshOutcome.Success)
        assertEquals("B", (currentOutcome as AccountRefreshOutcome.Success).value)
    }

    @Test
    fun `logout invalidation suppresses late result delivery`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attempt = checkNotNull(coordinator.tryStart())
        var callbackCount = 0

        coordinator.invalidate()
        val outcome = coordinator.runCurrent(attempt) { "late" }
        if (outcome != null) callbackCount++

        assertNull(outcome)
        assertEquals(0, callbackCount)
    }

    @Test
    fun `stale cancellation from A cannot release replacement refresh B`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attemptA = checkNotNull(coordinator.tryStart())
        coordinator.invalidate()
        val attemptB = checkNotNull(coordinator.tryStart())

        try {
            coordinator.runCurrent<String>(attemptA) {
                throw CancellationException("old account")
            }
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Expected control flow.
        }

        assertNull(coordinator.tryStart())
        val currentOutcome = coordinator.runCurrent(attemptB) { "replacement" }
        assertTrue(currentOutcome is AccountRefreshOutcome.Success)
    }

    @Test
    fun `normal refresh success is delivered once and releases ownership`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attempt = checkNotNull(coordinator.tryStart())
        var operationCount = 0
        var callbackCount = 0

        val outcome = coordinator.runCurrent(attempt) {
            operationCount++
            "refreshed"
        }
        if (outcome != null) callbackCount++

        assertEquals(1, operationCount)
        assertEquals(1, callbackCount)
        assertEquals("refreshed", (outcome as AccountRefreshOutcome.Success).value)
        assertNotNull(coordinator.tryStart())
    }

    @Test
    fun `current transient failure is delivered and allows a later retry`() = runBlocking {
        val coordinator = AccountRefreshCoordinator()
        val attempt = checkNotNull(coordinator.tryStart())
        val transient = IllegalStateException("temporary network failure")

        val outcome = coordinator.runCurrent<String>(attempt) { throw transient }

        assertTrue(outcome is AccountRefreshOutcome.Failure)
        assertSame(transient, (outcome as AccountRefreshOutcome.Failure).error)
        assertNotNull(coordinator.tryStart())
    }
}

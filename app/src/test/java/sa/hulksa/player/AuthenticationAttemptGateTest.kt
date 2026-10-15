package sa.hulksa.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationAttemptGateTest {
    @Test
    fun `duplicate submit is rejected while current attempt is active`() {
        val gate = AuthenticationAttemptGate()

        val first = gate.tryStart()
        val duplicate = gate.tryStart()

        assertNotNull(first)
        assertNull(duplicate)
        assertTrue(gate.isCurrent(checkNotNull(first)))
    }

    @Test
    fun `completion releases gate for a normal retry`() {
        val gate = AuthenticationAttemptGate()
        val first = checkNotNull(gate.tryStart())

        assertTrue(gate.complete(first))
        val retry = gate.tryStart()

        assertNotNull(retry)
        assertTrue(checkNotNull(retry) > first)
    }

    @Test
    fun `invalidation makes old result stale and allows a new attempt`() {
        val gate = AuthenticationAttemptGate()
        val first = checkNotNull(gate.tryStart())

        gate.invalidate()
        val second = checkNotNull(gate.tryStart())

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `stale completion cannot release a newer attempt`() {
        val gate = AuthenticationAttemptGate()
        val first = checkNotNull(gate.tryStart())
        gate.invalidate()
        val second = checkNotNull(gate.tryStart())

        assertFalse(gate.complete(first))
        assertNull(gate.tryStart())
        assertTrue(gate.isCurrent(second))
        assertTrue(gate.complete(second))
        assertNotNull(gate.tryStart())
    }

    @Test
    fun `paired handoff reserves once and carries the explicit login preflight proof`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        val reservation = gate.tryStartPairedHandoff()

        assertNotNull(reservation)
        assertNull(gate.tryStartPairedHandoff())
        assertNull(gate.tryStart())
        tracker.completeAuthenticationSuccess(accountId = "account", sessionId = "session")
        assertTrue(tracker.hasValidProof())
        assertTrue(gate.complete(checkNotNull(reservation)))
    }

    @Test
    fun `passive busy never manufactures parental proof and is side-effect free`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isPassivelyBusy())
        tracker.completeAuthenticationSuccess(accountId = "account", sessionId = "session")
        assertFalse(tracker.hasValidProof())
    }

    @Test
    fun `manual preflight still produces proof when the attempt completes`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)

        assertFalse(gate.isActive())
        val reservation = checkNotNull(gate.tryStart())
        tracker.completeAuthenticationSuccess(accountId = "account", sessionId = "session")

        assertTrue(tracker.hasValidProof())
        assertTrue(gate.complete(reservation))
    }

    @Test
    fun `paired reservation is invalidated by logout and can be reserved again`() {
        val tracker = ManualParentAuthProofTracker()
        val gate = AuthenticationAttemptGate(tracker)
        val first = checkNotNull(gate.tryStartPairedHandoff())

        gate.invalidate()
        val second = checkNotNull(gate.tryStartPairedHandoff())

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        assertNull(gate.tryStartPairedHandoff())
    }
}

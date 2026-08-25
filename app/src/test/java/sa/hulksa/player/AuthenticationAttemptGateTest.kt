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
}

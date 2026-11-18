package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgLoadGateTest {
    @Test
    fun accountSwitchInvalidatesAnOlderInFlightResult() {
        val gate = EpgLoadGate()
        val accountA = EpgScope(accountId = "account-a", providerId = "provider-a")
        val accountB = EpgScope(accountId = "account-b", providerId = "provider-b")

        val tokenA = gate.begin(accountA)
        assertTrue(gate.isCurrent(tokenA, accountA))

        val tokenB = gate.begin(accountB)
        assertFalse(gate.isCurrent(tokenA, accountB))
        assertFalse(gate.isCurrent(tokenA, accountA))
        assertTrue(gate.isCurrent(tokenB, accountB))
    }

    @Test
    fun sourceSwitchWithinTheSameAccountInvalidatesTheOlderResult() {
        val gate = EpgLoadGate()
        val sourceA = EpgScope(accountId = "account-a", providerId = "provider-a")
        val sourceB = EpgScope(accountId = "account-a", providerId = "provider-b")

        val token = gate.begin(sourceA)
        assertFalse(gate.isCurrent(token, sourceB))

        val replacement = gate.begin(sourceB)
        assertFalse(gate.isCurrent(token, sourceB))
        assertTrue(gate.isCurrent(replacement, sourceB))
    }

    @Test
    fun sameAccountAndSourceSurvivesAnOrdinaryReauthentication() {
        val gate = EpgLoadGate()
        val scope = EpgScope(accountId = "account-a", providerId = "provider-a")
        val token = gate.begin(scope)

        assertTrue(gate.isCurrent(token, scope))
    }

    @Test
    fun invalidationRejectsEveryOutstandingToken() {
        val gate = EpgLoadGate()
        val scope = EpgScope(accountId = "account-a", providerId = "provider-a")
        val token = gate.begin(scope)

        gate.invalidate()

        assertFalse(gate.isCurrent(token, scope))
    }
}

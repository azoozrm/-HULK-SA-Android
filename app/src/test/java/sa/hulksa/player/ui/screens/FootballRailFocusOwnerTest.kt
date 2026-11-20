package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballRailFocusOwnerTest {

    @Test
    fun `an immediately dispatched transition gates the old card before its first scroll`() = runBlocking {
        val owner = FootballRailFocusOwner()
        val scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined)
        var oldCardDelta = -126
        assertTrue(owner.start(scope) { oldCardDelta = owner.collectorScrollDelta(oldCardDelta) })
        assertEquals(0, oldCardDelta)
        assertFalse(owner.isActive)
    }

    @Test
    fun `a second transition is refused while the first is active`() = runBlocking {
        val owner = FootballRailFocusOwner()
        val gate = CompletableDeferred<Unit>()
        assertTrue(owner.start(this) { gate.await() })
        assertTrue(owner.isActive)
        assertFalse(owner.start(this) { error("must not start") })
        gate.complete(Unit)
        withTimeout(1_000L) { while (owner.isActive) delay(5L) }
        assertFalse(owner.isActive)
    }

    @Test
    fun `cancel clears the owner and allows a replacement transition`() = runBlocking {
        val owner = FootballRailFocusOwner()
        val gate = CompletableDeferred<Unit>()
        assertTrue(owner.start(this) { gate.await() })
        owner.cancel()
        assertFalse(owner.isActive)
        val replacement = CompletableDeferred<Unit>()
        assertTrue(owner.start(this) { replacement.complete(Unit) })
        replacement.await()
        withTimeout(1_000L) { while (owner.isActive) delay(5L) }
        assertFalse(owner.isActive)
    }
}

package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSettingsMutationGateTest {
    @Test
    fun `settings persistence runs exactly once away from caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runDownloadSettingsPersistenceOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            null
        }

        assertNull(result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun `rapid settings intents persist only the last value`() {
        val gate = DownloadSettingsMutationGate()
        val first = gate.begin()
        val second = gate.begin()
        val writes = mutableListOf<String>()

        assertNull(gate.writeIfCurrent(first) { writes += "night" })
        assertEquals(Unit, gate.writeIfCurrent(second) { writes += "now" })

        assertEquals(listOf("now"), writes)
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `stale completion cannot publish over a newer mutation`() {
        val gate = DownloadSettingsMutationGate()
        val older = gate.begin()
        val newer = gate.begin()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))

        gate.invalidate()

        assertFalse(gate.isCurrent(newer))
        assertNull(gate.writeIfCurrent(newer) { "stale" })
    }

    @Test
    fun `skipped older schedule intent cannot create an extra durable revision`() {
        val gate = DownloadSettingsMutationGate()
        val older = gate.begin()
        val latest = gate.begin()
        var lifecycleRevision = 41L

        gate.writeIfCurrent(older) { lifecycleRevision += 1L }
        gate.writeIfCurrent(latest) { lifecycleRevision += 1L }

        assertEquals(42L, lifecycleRevision)
    }
}

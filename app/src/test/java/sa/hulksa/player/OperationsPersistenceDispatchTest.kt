package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsPersistenceDispatchTest {
    @Test
    fun `operations persistence runs exactly once away from caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runOperationsPersistenceOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            "persisted"
        }

        assertEquals("persisted", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun `operations persistence propagates storage failure`() = runBlocking {
        var failureObserved = false

        try {
            runOperationsPersistenceOffMain<Unit> {
                throw IllegalStateException("storage write failed")
            }
        } catch (_: IllegalStateException) {
            failureObserved = true
        }

        assertTrue(failureObserved)
    }
}

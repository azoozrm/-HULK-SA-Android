package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAndKidsPersistenceDispatchTest {
    @Test
    fun profilePersistenceRunsExactlyOnceAwayFromCallerThread() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runProfilePersistenceOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            "saved"
        }

        assertEquals("saved", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun profilePersistencePropagatesStorageFailure() = runBlocking {
        var failureObserved = false

        try {
            runProfilePersistenceOffMain<Unit> {
                throw IllegalStateException("profile commit failed")
            }
        } catch (_: IllegalStateException) {
            failureObserved = true
        }

        assertTrue(failureObserved)
    }

    @Test
    fun kidsSnapshotBuildAndCommitRunExactlyOnceAwayFromCallerThread() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runKidsSnapshotPersistenceOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            true
        }

        assertTrue(result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun kidsSnapshotPersistencePropagatesStorageFailure() = runBlocking {
        var failureObserved = false

        try {
            runKidsSnapshotPersistenceOffMain<Unit> {
                throw IllegalStateException("Kids snapshot commit failed")
            }
        } catch (_: IllegalStateException) {
            failureObserved = true
        }

        assertTrue(failureObserved)
    }

    @Test
    fun persistenceDispatchersDoNotSwallowCancellation() = runBlocking {
        var profileCancellationObserved = false
        var kidsCancellationObserved = false

        try {
            runProfilePersistenceOffMain<Unit> {
                throw CancellationException("cancel profile persistence")
            }
        } catch (_: CancellationException) {
            profileCancellationObserved = true
        }
        try {
            runKidsSnapshotPersistenceOffMain<Unit> {
                throw CancellationException("cancel Kids persistence")
            }
        } catch (_: CancellationException) {
            kidsCancellationObserved = true
        }

        assertTrue(profileCancellationObserved)
        assertTrue(kidsCancellationObserved)
    }
}

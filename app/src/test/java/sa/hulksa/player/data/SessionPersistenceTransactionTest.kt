package sa.hulksa.player.data

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPersistenceTransactionTest {
    @Test
    fun cancellationAfterPersistenceStartsCannotSplitAtomicSteps() = runBlocking {
        val firstStepCommitted = CountDownLatch(1)
        val allowCompletion = CountDownLatch(1)
        val steps = Collections.synchronizedList(mutableListOf<String>())

        val job = launch(Dispatchers.Default) {
            runSessionPersistenceTransaction {
                steps += "session-metadata"
                firstStepCommitted.countDown()
                check(allowCompletion.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to complete the persistence transaction"
                }
                steps += "credential-vault"
                steps += "session-registry"
            }
        }

        assertTrue(firstStepCommitted.await(5, TimeUnit.SECONDS))
        job.cancel()
        allowCompletion.countDown()
        job.cancelAndJoin()

        assertEquals(
            listOf("session-metadata", "credential-vault", "session-registry"),
            steps.toList(),
        )
        assertTrue(job.isCancelled)
    }
}

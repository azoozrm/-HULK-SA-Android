package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.ProfileDownloadMutationOutcome
import sa.hulksa.player.model.DownloadSettings

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
    fun `rapid settings intents persist only the last value`() = runBlocking {
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
    fun `stale completion cannot publish over a newer mutation`() = runBlocking {
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
    fun `skipped older schedule intent cannot create an extra durable revision`() = runBlocking {
        val gate = DownloadSettingsMutationGate()
        val older = gate.begin()
        val latest = gate.begin()
        var lifecycleRevision = 41L

        gate.writeIfCurrent(older) { lifecycleRevision += 1L }
        gate.writeIfCurrent(latest) { lifecycleRevision += 1L }

        assertEquals(42L, lifecycleRevision)
    }

    @Test
    fun `begin and invalidate do not wait for durable write`() = runBlocking {
        val gate = DownloadSettingsMutationGate()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val persistenceExecutor = Executors.newSingleThreadExecutor()
        val uiExecutor = Executors.newSingleThreadExecutor()
        try {
            val attempt = gate.begin()
            persistenceExecutor.submit {
                runBlocking {
                    gate.writeIfCurrent(attempt) {
                        started.countDown()
                        release.await()
                    }
                }
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))

            val nextAttempt = uiExecutor.submit<DownloadSettingsMutationGate.Attempt> { gate.begin() }
                .get(1, TimeUnit.SECONDS)
            uiExecutor.submit { gate.invalidate() }.get(1, TimeUnit.SECONDS)

            assertFalse(gate.isCurrent(nextAttempt))
        } finally {
            release.countDown()
            persistenceExecutor.shutdownNow()
            uiExecutor.shutdownNow()
        }
    }

    @Test
    fun `newer settings write remains final after an older write is already running`() = runBlocking {
        val gate = DownloadSettingsMutationGate()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = mutableListOf<String>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val older = gate.begin()
            executor.submit {
                runBlocking {
                    gate.writeIfCurrent(older) {
                        started.countDown()
                        release.await()
                        writes += "older"
                    }
                }
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            val newer = gate.begin()
            val newerWrite = executor.submit {
                runBlocking { gate.writeIfCurrent(newer) { writes += "newer" } }
            }

            release.countDown()
            newerWrite.get(1, TimeUnit.SECONDS)

            assertEquals(listOf("older", "newer"), writes)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `resume followed by priority does not cancel resume`() = runBlocking {
        val resumeGate = DownloadSettingsMutationGate()
        val priorityQueue = DownloadPriorityMutationQueue()
        val resume = resumeGate.begin()
        val priorityAttempt = priorityQueue.begin()
        var resumed = false
        var priority = 0

        priorityQueue.write {
            priority = 1
            priorityOutcome()
        }
        resumeGate.writeIfCurrent(resume) { resumed = true }

        assertTrue(resumed)
        assertTrue(priorityQueue.isCurrent(priorityAttempt))
        assertEquals(1, priority)
    }

    @Test
    fun `priority followed by resume does not cancel priority`() = runBlocking {
        val resumeGate = DownloadSettingsMutationGate()
        val priorityQueue = DownloadPriorityMutationQueue()
        val priorityAttempt = priorityQueue.begin()
        val resume = resumeGate.begin()
        var resumed = false
        var priority = 0

        priorityQueue.write {
            priority = 1
            priorityOutcome()
        }
        resumeGate.writeIfCurrent(resume) { resumed = true }

        assertTrue(resumed)
        assertTrue(priorityQueue.isCurrent(priorityAttempt))
        assertEquals(1, priority)
    }

    @Test
    fun `rapid priority cycles remain correct when newest write reaches io first`() = runBlocking {
        val priorityQueue = DownloadPriorityMutationQueue()
        val first = priorityQueue.begin()
        val second = priorityQueue.begin()
        val third = priorityQueue.begin()
        val writes = AtomicInteger(0)
        var priority = 0

        fun cycle(): ProfileDownloadMutationOutcome {
            writes.incrementAndGet()
            priority = when (priority) {
                1 -> -1
                -1 -> 0
                else -> 1
            }
            return priorityOutcome()
        }

        val newestResult = priorityQueue.write(::cycle)
        val olderFirstResult = priorityQueue.write(::cycle)
        val olderSecondResult = priorityQueue.write(::cycle)

        assertEquals(3, writes.get())
        assertEquals(0, priority)
        assertEquals(newestResult, olderFirstResult)
        assertEquals(newestResult, olderSecondResult)
        assertFalse(priorityQueue.isCurrent(first))
        assertFalse(priorityQueue.isCurrent(second))
        assertTrue(priorityQueue.isCurrent(third))
    }

    private fun priorityOutcome(): ProfileDownloadMutationOutcome = ProfileDownloadMutationOutcome(
        settings = DownloadSettings(),
        downloads = emptyList(),
        applied = true,
    )
}

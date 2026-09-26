package sa.hulksa.player.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DurableDownloadActionReceiverTest {
    @Test
    fun `pause notification action is recognized`() {
        assertEquals(
            DurableDownloadNotificationAction.PAUSE,
            durableDownloadNotificationAction(ACTION_PAUSE_DOWNLOAD),
        )
    }

    @Test
    fun `resume notification action is recognized`() {
        assertEquals(
            DurableDownloadNotificationAction.RESUME,
            durableDownloadNotificationAction(ACTION_RESUME_DOWNLOAD),
        )
    }

    @Test
    fun `unknown notification action is ignored`() {
        assertNull(durableDownloadNotificationAction("unknown"))
        assertNull(durableDownloadNotificationAction(null))
    }

    @Test
    fun `pause and resume are dispatched through asynchronous receiver execution`() {
        assertEquals(
            DurableDownloadReceiverExecution.ASYNC_PAUSE,
            durableDownloadReceiverExecution(ACTION_PAUSE_DOWNLOAD),
        )
        assertEquals(
            DurableDownloadReceiverExecution.ASYNC_RESUME,
            durableDownloadReceiverExecution(ACTION_RESUME_DOWNLOAD),
        )
        assertNull(durableDownloadReceiverExecution("unknown"))
        assertNull(durableDownloadReceiverExecution(null))
    }

    @Test
    fun `missing or invalid download id is rejected`() {
        assertNull(durableDownloadReceiverDownloadId(-1L))
        assertNull(durableDownloadReceiverDownloadId(0L))
        assertEquals(7L, durableDownloadReceiverDownloadId(7L))
    }

    @Test
    fun `repository mutation runs off the caller thread and completes exactly once`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "durable-download-receiver-test")
        }
        try {
            val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
            val callerThread = Thread.currentThread()
            val completed = CountDownLatch(1)
            val resolves = AtomicInteger()
            val mutations = AtomicInteger()
            val completions = AtomicInteger()
            val mutationThread = AtomicReference<Thread?>(null)
            val observedTarget = AtomicReference<String?>(null)
            val order = mutableListOf<String>()

            scope.launchDurableDownloadReceiverMutation(
                resolveTarget = {
                    resolves.incrementAndGet()
                    "active-repository"
                },
                finish = {
                    completions.incrementAndGet()
                    order += "finish"
                    completed.countDown()
                },
            ) { target ->
                observedTarget.set(target)
                mutationThread.set(Thread.currentThread())
                mutations.incrementAndGet()
                order += "mutation"
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(1, resolves.get())
            assertEquals("active-repository", observedTarget.get())
            assertEquals(1, mutations.get())
            assertEquals(1, completions.get())
            assertEquals(listOf("mutation", "finish"), order)
            assertNotSame(callerThread, mutationThread.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `absent active owner completes without mutation`() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val mutations = AtomicInteger()
        val completions = AtomicInteger()

        scope.launchDurableDownloadReceiverMutation<String>(
            resolveTarget = { null },
            finish = { completions.incrementAndGet() },
            mutation = { mutations.incrementAndGet() },
        ).join()

        assertEquals(0, mutations.get())
        assertEquals(1, completions.get())
    }

    @Test
    fun `failed mutation still completes exactly once`() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            coroutineContext + SupervisorJob() +
                CoroutineExceptionHandler { _, throwable -> failures += throwable },
        )
        val completions = AtomicInteger()

        scope.launchDurableDownloadReceiverMutation<String>(
            resolveTarget = { "active-repository" },
            finish = { completions.incrementAndGet() },
            mutation = { throw IllegalStateException("synthetic receiver mutation failure") },
        ).join()

        assertEquals(1, completions.get())
        assertEquals(1, failures.size)
        assertTrue(failures.single() is IllegalStateException)
    }
}

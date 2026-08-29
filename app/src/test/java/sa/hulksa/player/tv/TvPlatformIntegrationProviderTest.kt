package sa.hulksa.player.tv

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlatformIntegrationProviderTest {
    @Test
    fun `login startup probe does not initialize tv platform integration`() {
        val creations = AtomicInteger(0)
        val provider = TvPlatformIntegrationProvider {
            creations.incrementAndGet()
            Any()
        }

        assertNull(provider.getIfInitialized())
        assertEquals(0, creations.get())
    }

    @Test
    fun `first platform operation creates one stable instance`() = runBlocking {
        val creations = AtomicInteger(0)
        val provider = TvPlatformIntegrationProvider {
            creations.incrementAndGet()
            Any()
        }

        val first = provider.withInstance { it }
        val second = provider.withInstance { it }

        assertSame(first, second)
        assertSame(first, provider.getIfInitialized())
        assertEquals(1, creations.get())
    }

    @Test
    fun `first initialization is asynchronous and remains single under concurrency`() {
        val callerThread = Thread.currentThread()
        val initializerThread = AtomicReference<Thread>()
        val creations = AtomicInteger(0)
        val initializationStarted = CountDownLatch(1)
        val releaseInitialization = CountDownLatch(1)

        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val provider = TvPlatformIntegrationProvider(
                initializer = {
                    creations.incrementAndGet()
                    initializerThread.set(Thread.currentThread())
                    initializationStarted.countDown()
                    check(releaseInitialization.await(5, TimeUnit.SECONDS))
                    Any()
                },
                dispatcher = dispatcher,
            )

            runBlocking {
                val first = async(dispatcher) { provider.withInstance { it } }
                assertTrue(initializationStarted.await(5, TimeUnit.SECONDS))
                val second = async(dispatcher) { provider.withInstance { it } }

                assertFalse(first.isCompleted)
                assertFalse(second.isCompleted)
                releaseInitialization.countDown()

                assertSame(first.await(), second.await())
            }
        }

        assertNotSame(callerThread, initializerThread.get())
        assertEquals(1, creations.get())
    }
}

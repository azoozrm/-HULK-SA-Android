package sa.hulksa.player.ui.screens

import java.net.InetAddress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F01 correction 05: long-outage monitoring must not expire, explicit retry must restart the
 * owned monitor, and a probe must stop at its actual deadline/cancellation boundary.
 */
class F01Correction05MonitoringTest {
    @Test
    fun `monitor remains active beyond five minutes and restores after reachability returns`() = runBlocking {
        var simulatedElapsedMs = 0L
        var restored = false
        val monitor = NetworkRestoreMonitor(
            probe = { simulatedElapsedMs >= 300_001L },
            delayForAttemptMs = { delayMs -> simulatedElapsedMs += delayMs },
        )

        val attempts = monitor.run { restored = true }

        assertTrue(restored)
        assertTrue(simulatedElapsedMs >= 300_000L)
        assertTrue(attempts > 20)
    }

    @Test
    fun `backoff stays bounded and controlled during a long outage`() {
        assertEquals(3_000L, networkProbeBackoffMs(1))
        assertEquals(3_000L, networkProbeBackoffMs(19))
        assertEquals(5_000L, networkProbeBackoffMs(20))
        assertEquals(5_000L, networkProbeBackoffMs(79))
        assertEquals(15_000L, networkProbeBackoffMs(80))
        assertEquals(15_000L, networkProbeBackoffMs(10_000))
    }

    @Test
    fun `cancelled monitor rejects a late successful probe result`() = runBlocking {
        var restored = false
        val job = launch {
            NetworkRestoreMonitor(
                probe = {
                    withContext(NonCancellable) { delay(80L) }
                    true
                },
            ).run { restored = true }
        }
        delay(20L)
        job.cancel()
        job.join()

        assertFalse(restored)
    }

    @Test
    fun `a restarted monitor is separate from the cancelled old owner`() = runBlocking {
        var oldOwnerRestored = false
        var newOwnerRestored = false
        val oldOwner = launch {
            NetworkRestoreMonitor(
                probe = {
                    withContext(NonCancellable) { delay(80L) }
                    true
                },
            ).run { oldOwnerRestored = true }
        }
        delay(20L)
        oldOwner.cancel()
        oldOwner.join()

        NetworkRestoreMonitor(probe = { true }).run { newOwnerRestored = true }

        assertFalse(oldOwnerRestored)
        assertTrue(newOwnerRestored)
    }

    @Test
    fun `total probe deadline stops stalled DNS before any connection`() = runBlocking {
        var connectionCalls = 0
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    delay(10_000L)
                    return listOf(InetAddress.getLoopbackAddress())
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean {
                    connectionCalls += 1
                    return true
                }
            },
        )

        val result = withTimeout(2_000L) {
            probe.isReachable(
                targets = listOf(NetworkProbeTarget("slow.example", 443)),
                perTargetTimeoutMs = 500,
                totalTimeoutMs = 100L,
            )
        }

        assertFalse(result)
        assertEquals(0, connectionCalls)
    }

    @Test
    fun `total probe deadline stops stalled connection and closes the sequence`() = runBlocking {
        var connectionCalls = 0
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return listOf(InetAddress.getLoopbackAddress())
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean {
                    connectionCalls += 1
                    delay(10_000L)
                    return true
                }
            },
        )

        val result = withTimeout(2_000L) {
            probe.isReachable(
                targets = listOf(NetworkProbeTarget("slow.example", 443)),
                perTargetTimeoutMs = 500,
                totalTimeoutMs = 100L,
            )
        }

        assertFalse(result)
        assertEquals(1, connectionCalls)
    }

    @Test
    fun `probe checks cancellation between targets`() = runBlocking {
        var secondTargetStarted = false
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    if (host == "second.example") secondTargetStarted = true
                    return listOf(InetAddress.getLoopbackAddress())
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean {
                    delay(10_000L)
                    return false
                }
            },
        )
        val job = launch {
            probe.isReachable(
                targets = listOf(
                    NetworkProbeTarget("first.example", 443),
                    NetworkProbeTarget("second.example", 443),
                ),
                perTargetTimeoutMs = 500,
                totalTimeoutMs = 10_000L,
            )
        }
        delay(20L)
        job.cancel()
        job.join()

        // The first target owns a stalled connection; cancellation must prevent reaching the
        // second target.
        assertFalse(secondTargetStarted)
    }
}

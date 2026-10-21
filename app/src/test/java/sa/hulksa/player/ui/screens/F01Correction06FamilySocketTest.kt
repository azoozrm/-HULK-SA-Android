package sa.hulksa.player.ui.screens

import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F01 correction 06: preserve both DNS families and test the production socket adapter's real
 * cancellation bridge, not only delay-based fake connectors.
 */
class F01Correction06FamilySocketTest {
    private val ipv4 = InetAddress.getByName("192.0.2.10")
    private val ipv6 = InetAddress.getByName("2001:db8::10")

    @Test
    fun `A and AAAA answers both survive family resolution`() = runBlocking {
        val queriedTypes = mutableListOf<Int>()
        val result = resolveAddressFamilies(timeoutMs = 1_000L) { type, _ ->
            queriedTypes += type
            if (type == 1) listOf(ipv4) else listOf(ipv6)
        }

        assertTrue(queriedTypes.contains(1))
        assertTrue(queriedTypes.contains(28))
        assertTrue(result.contains(ipv4))
        assertTrue(result.contains(ipv6))
    }

    @Test
    fun `slow or failed IPv4 does not suppress working IPv6`() = runBlocking {
        val result = withTimeout(2_000L) {
            resolveAddressFamilies(timeoutMs = 200L) { type, _ ->
                if (type == 1) {
                    delay(10_000L)
                    listOf(ipv4)
                } else {
                    listOf(ipv6)
                }
            }
        }

        assertEquals(listOf(ipv6), result)
    }

    @Test
    fun `IPv4-only and IPv6-only answers remain usable`() = runBlocking {
        val onlyV4 = resolveAddressFamilies(500L) { type, _ ->
            if (type == 1) listOf(ipv4) else emptyList()
        }
        val onlyV6 = resolveAddressFamilies(500L) { type, _ ->
            if (type == 28) listOf(ipv6) else emptyList()
        }

        assertEquals(listOf(ipv4), onlyV4)
        assertEquals(listOf(ipv6), onlyV6)
    }

    @Test
    fun `probe tries IPv6 after an IPv4 connection failure`() = runBlocking {
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return listOf(ipv4, ipv6)
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean = address == ipv6
            },
        )

        assertTrue(probe.isReachable(listOf(NetworkProbeTarget("dual.example", 443))))
    }

    @Test
    fun `cancellation before connection starts creates no owned socket`() = runBlocking {
        var factoryCalls = 0
        val connector = SocketAddressConnector {
            factoryCalls += 1
            ImmediateSocket()
        }
        val job = launch(start = CoroutineStart.LAZY) {
            connector.connect(ipv4, 443, timeoutMs = 30_000)
        }
        job.cancel()
        job.start()
        job.join()

        assertEquals(0, factoryCalls)
    }

    @Test
    fun `owned socket closes on cancellation during a stalled connect`() = runBlocking {
        val fake = BlockingSocket()
        val connector = SocketAddressConnector { fake }
        val job = launch {
            connector.connect(ipv4, 443, timeoutMs = 30_000)
        }

        yield()
        assertTrue(fake.started.await(1, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(2_000L) { job.join() }

        assertTrue(fake.closed.get())
    }

    @Test
    fun `late success from canceled connection is rejected and socket is closed`() = runBlocking {
        val fake = LateSuccessSocket()
        val connector = SocketAddressConnector { fake }
        val job = launch {
            connector.connect(ipv4, 443, timeoutMs = 30_000)
        }

        yield()
        assertTrue(fake.started.await(1, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(2_000L) { job.join() }

        assertTrue(fake.closed.get())
        assertTrue(job.isCancelled)
    }

    @Test
    fun `controlled successful socket still returns true`() = runBlocking {
        val fake = ImmediateSocket()
        val connector = SocketAddressConnector { fake }

        assertTrue(connector.connect(ipv4, 443, timeoutMs = 500))
        assertTrue(fake.closed.get())
    }

    private class ImmediateSocket : Socket() {
        val closed = AtomicBoolean(false)

        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit

        override fun close() {
            closed.set(true)
        }
    }

    private class BlockingSocket : Socket() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            if (closed.get()) throw SocketException("canceled")
        }

        override fun close() {
            closed.set(true)
            release.countDown()
        }
    }

    private class LateSuccessSocket : Socket() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        override fun close() {
            closed.set(true)
            release.countDown()
        }
    }
}

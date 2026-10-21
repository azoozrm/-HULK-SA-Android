package sa.hulksa.player.ui.screens

import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F01 correction 07: family-aware address scheduling must not let a long IPv4 list consume the whole
 * deadline before IPv6, and connector ownership must be released before publishing completion.
 */
class F01Correction07SchedulingOwnershipTest {
    private val ipv4 = InetAddress.getByName("192.0.2.10")
    private val ipv6 = InetAddress.getByName("2001:db8::10")

    @Test
    fun `three stalled IPv4 addresses do not starve IPv6 within the deadline`() = runBlocking {
        val stalledIpv4 = listOf(
            InetAddress.getByName("192.0.2.11"),
            InetAddress.getByName("192.0.2.12"),
            InetAddress.getByName("192.0.2.13"),
        )
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return stalledIpv4 + ipv6
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean {
                    if (address == ipv6) return true
                    delay(100L)
                    return false
                }
            },
        )

        assertTrue(
            probe.isReachable(
                targets = listOf(NetworkProbeTarget("dual.example", 443)),
                perTargetTimeoutMs = 100,
                totalTimeoutMs = 250L,
            ),
        )
    }

    @Test
    fun `three stalled IPv6 addresses do not starve IPv4 within the deadline`() = runBlocking {
        val stalledIpv6 = listOf(
            InetAddress.getByName("2001:db8::11"),
            InetAddress.getByName("2001:db8::12"),
            InetAddress.getByName("2001:db8::13"),
        )
        val probe = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return stalledIpv6 + ipv4
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(
                    address: InetAddress,
                    port: Int,
                    timeoutMs: Int,
                ): Boolean {
                    if (address == ipv4) return true
                    delay(100L)
                    return false
                }
            },
        )

        assertTrue(
            probe.isReachable(
                targets = listOf(NetworkProbeTarget("dual.example", 443)),
                perTargetTimeoutMs = 100,
                totalTimeoutMs = 250L,
            ),
        )
    }

    @Test
    fun `family interleaving preserves both families and IPv4-only IPv6-only operation`() = runBlocking {
        val ordered = interleaveAddressFamilies(
            listOf(
                InetAddress.getByName("192.0.2.21"),
                InetAddress.getByName("192.0.2.22"),
                InetAddress.getByName("192.0.2.23"),
                InetAddress.getByName("2001:db8::21"),
            ),
        )
        assertTrue(ordered.indexOf(InetAddress.getByName("2001:db8::21")) < 2)

        val onlyV4 = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return listOf(ipv4)
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(address: InetAddress, port: Int, timeoutMs: Int) = true
            },
        )
        val onlyV6 = BoundedReachabilityProbe(
            resolver = object : HostResolver {
                override suspend fun resolve(host: String, timeoutMs: Long): List<InetAddress> {
                    return listOf(ipv6)
                }
            },
            connector = object : AddressConnector {
                override suspend fun connect(address: InetAddress, port: Int, timeoutMs: Int) = true
            },
        )

        assertTrue(onlyV4.isReachable(listOf(NetworkProbeTarget("v4.example", 443))))
        assertTrue(onlyV6.isReachable(listOf(NetworkProbeTarget("v6.example", 443))))
    }

    @Test
    fun `failed connection releases ownership before caller can start next address`() = runBlocking {
        var socketIndex = 0
        val connector = SocketAddressConnector {
            if (socketIndex++ == 0) ImmediateFailSocket() else ImmediateSuccessSocket()
        }

        // Unconfined makes the caller resume inline on the connector worker. The old ordering
        // published the result before finally released inFlight, so the second call was skipped.
        withContext(Dispatchers.Unconfined) {
            assertFalse(connector.connect(ipv4, 443, timeoutMs = 500))
            assertTrue(connector.connect(ipv4, 443, timeoutMs = 500))
        }
        assertEquals(2, socketIndex)
    }

    private class ImmediateFailSocket : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            throw SocketException("controlled failure")
        }

        override fun close() = Unit
    }

    private class ImmediateSuccessSocket : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit

        override fun close() = Unit
    }
}

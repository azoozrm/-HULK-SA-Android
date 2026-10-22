package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAvailabilityPolicyTest {
    @Test
    fun `lost validation closes usable wifi even while the network stays available`() {
        val state = reduceNetworkAvailability(
            NetworkAvailabilityState(available = true, networkToken = 7),
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 7,
                hasInternet = true,
                validated = false,
            ),
        )

        assertFalse(state.available)
        assertEquals(7, state.networkToken)
    }

    @Test
    fun `a new default network waits for validated capabilities`() {
        val announced = reduceNetworkAvailability(
            NetworkAvailabilityState(available = true, networkToken = 7),
            NetworkCallbackSignal(NetworkCallbackKind.AVAILABLE, networkToken = 8),
        )
        assertFalse(announced.available)
        assertEquals(8, announced.networkToken)

        val validated = reduceNetworkAvailability(
            announced,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 8,
                hasInternet = true,
                validated = true,
            ),
        )
        assertTrue(validated.available)
        assertEquals(8, validated.networkToken)
    }

    @Test
    fun `stale callbacks from an old network cannot overwrite the current network`() {
        val current = NetworkAvailabilityState(available = true, networkToken = 8)

        val staleCapabilities = reduceNetworkAvailability(
            current,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 7,
                hasInternet = true,
                validated = true,
            ),
        )
        assertEquals(current, staleCapabilities)

        val staleLost = reduceNetworkAvailability(
            current,
            NetworkCallbackSignal(NetworkCallbackKind.LOST, networkToken = 7),
        )
        assertEquals(current, staleLost)

        val currentLost = reduceNetworkAvailability(
            current,
            NetworkCallbackSignal(NetworkCallbackKind.LOST, networkToken = 8),
        )
        assertFalse(currentLost.available)
    }

    @Test
    fun `restoration requires both internet and validation`() {
        val offline = NetworkAvailabilityState(available = false, networkToken = 8)

        val unvalidated = reduceNetworkAvailability(
            offline,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 8,
                hasInternet = true,
                validated = false,
            ),
        )
        assertFalse(unvalidated.available)

        val validated = reduceNetworkAvailability(
            unvalidated,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 8,
                hasInternet = true,
                validated = true,
            ),
        )
        assertTrue(validated.available)

        val noInternet = reduceNetworkAvailability(
            validated,
            NetworkCallbackSignal(
                kind = NetworkCallbackKind.CAPABILITIES,
                networkToken = 8,
                hasInternet = false,
                validated = true,
            ),
        )
        assertFalse(noInternet.available)
    }

    @Test
    fun `unavailable and duplicate validated callbacks are stable`() {
        val unavailable = reduceNetworkAvailability(
            NetworkAvailabilityState(available = true, networkToken = 8),
            NetworkCallbackSignal(NetworkCallbackKind.UNAVAILABLE),
        )
        assertFalse(unavailable.available)

        val validated = NetworkCallbackSignal(
            kind = NetworkCallbackKind.CAPABILITIES,
            networkToken = 8,
            hasInternet = true,
            validated = true,
        )
        val first = reduceNetworkAvailability(unavailable, validated)
        val second = reduceNetworkAvailability(first, validated)
        assertTrue(first.available)
        assertEquals(first, second)
    }
}

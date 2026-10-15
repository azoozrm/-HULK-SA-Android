package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.screens.pairingRemainingSeconds

class PairingEntryPolicyTest {
    @Test
    fun `pairing entry requires real television hardware and enabled discovery`() {
        assertTrue(pairingEntryOffered(hardwareTelevision = true, pairingAvailable = true))
        assertFalse(pairingEntryOffered(hardwareTelevision = true, pairingAvailable = false))
        assertFalse(pairingEntryOffered(hardwareTelevision = false, pairingAvailable = true))
        assertFalse(pairingEntryOffered(hardwareTelevision = false, pairingAvailable = false))
    }

    @Test
    fun `countdown floors at zero and rounds remaining monotonic time up to the second`() {
        assertEquals(0L, pairingRemainingSeconds(deadlineElapsedRealtimeMs = null, nowElapsedRealtimeMs = 1_000L))
        assertEquals(0L, pairingRemainingSeconds(deadlineElapsedRealtimeMs = 1_000L, nowElapsedRealtimeMs = 1_000L))
        assertEquals(0L, pairingRemainingSeconds(deadlineElapsedRealtimeMs = 500L, nowElapsedRealtimeMs = 1_000L))
        assertEquals(1L, pairingRemainingSeconds(deadlineElapsedRealtimeMs = 1_001L, nowElapsedRealtimeMs = 1_000L))
        assertEquals(600L, pairingRemainingSeconds(deadlineElapsedRealtimeMs = 1_600_000L, nowElapsedRealtimeMs = 1_000_000L))
    }
}

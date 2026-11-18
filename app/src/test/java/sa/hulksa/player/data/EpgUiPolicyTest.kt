package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.ui.screens.epgDurationLabel
import sa.hulksa.player.ui.screens.epgProgressFraction

class EpgUiPolicyTest {
    @Test
    fun currentProgressUsesOnlyAValidLiveRange() {
        val start = 1_000L
        val stop = 1_600L

        assertEquals(0f, epgProgressFraction(start, stop, start)!!)
        assertEquals(0.5f, epgProgressFraction(start, stop, 1_300L)!!)
        assertNull(epgProgressFraction(start, stop, start - 1L))
        assertNull(epgProgressFraction(start, stop, stop))
        assertNull(epgProgressFraction(start, start, start))
    }

    @Test
    fun durationUsesTheRealProgrammeRange() {
        assertEquals("أقل من دقيقة", epgDurationLabel(100L, 130L))
        assertEquals("45 د", epgDurationLabel(0L, 45L * 60L))
        assertEquals("1 س", epgDurationLabel(0L, 60L * 60L))
        assertEquals("1 س 30 د", epgDurationLabel(0L, 90L * 60L))
        assertEquals("2 س 5 د", epgDurationLabel(0L, 125L * 60L))
    }

    @Test
    fun enabledUnverifiedStateReservesLayoutButDoesNotEnableTheGuide() {
        val loading = LiveEpgUiState(enabled = true, loading = true)
        assertEquals(false, loading.guideEnabled)
        assertEquals(true, loading.enabled)

        val verified = loading.copy(verified = true, loading = false)
        assertEquals(true, verified.guideEnabled)
    }
}

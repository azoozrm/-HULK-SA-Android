package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.data.footballRiyadhDayStartEpochMs

class FootballHomeDayDisclosureTest {

    private val today = "2026-11-20"
    private val now = (footballRiyadhDayStartEpochMs(today) ?: 0L) + 12 * 60 * 60 * 1_000L

    @Test
    fun `exact today and missing records keep the today title`() {
        assertEquals("مباريات اليوم", footballHomeSectionTitle(today, today, now))
        assertEquals("مباريات اليوم", footballHomeSectionTitle(null, today, now))
    }

    @Test
    fun `previous-day fallback discloses the actual accepted day`() {
        assertEquals("مباريات الامس", footballHomeSectionTitle("2026-11-19", today, now))
        assertEquals("مباريات 2026-11-18", footballHomeSectionTitle("2026-11-18", today, now))
    }
}

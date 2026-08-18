package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsProStoreTest {
    @Test
    fun seekStepRejectsUnsupportedValuesAndCyclesDeterministically() {
        assertEquals(10, normalizedSeekStepSeconds(7))
        assertEquals(15, nextSeekStepSeconds(10))
        assertEquals(30, nextSeekStepSeconds(15))
        assertEquals(10, nextSeekStepSeconds(30))
    }
}

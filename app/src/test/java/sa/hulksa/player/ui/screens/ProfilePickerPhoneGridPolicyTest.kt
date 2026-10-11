package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePickerPhoneGridPolicyTest {
    @Test
    fun twoColumnsOnlyWhenTwoCompleteCardsPlusGapFit() {
        assertEquals(2, profilePickerPhoneColumnCount(usableWidthDp = 348f, gapDp = 8f))
        assertEquals(1, profilePickerPhoneColumnCount(usableWidthDp = 271f, gapDp = 8f))
        assertEquals(2, profilePickerPhoneColumnCount(usableWidthDp = 272f, gapDp = 8f))
    }

    @Test
    fun narrowWindowOrLargerFootprintReducesToOneColumn() {
        assertEquals(
            1,
            profilePickerPhoneColumnCount(usableWidthDp = 320f, gapDp = 10f, minCardFootprintDp = 160f),
        )
        assertEquals(
            2,
            profilePickerPhoneColumnCount(usableWidthDp = 330f, gapDp = 10f, minCardFootprintDp = 160f),
        )
    }
}

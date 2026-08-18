package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePickerTvPremiumTest {
    @Test
    fun compactTvKeepsExtraSafeSpaceAndSmallerCards() {
        val compact = profilePickerTvMetrics(screenWidthDp = 960, screenHeightDp = 540)
        val standard = profilePickerTvMetrics(screenWidthDp = 1280, screenHeightDp = 720)

        assertTrue(compact.horizontalPaddingDp >= 28f)
        assertTrue(compact.verticalPaddingDp >= 18f)
        assertTrue(compact.cardWidthDp < standard.cardWidthDp)
        assertTrue(compact.avatarSizeDp < standard.avatarSizeDp)
    }

    @Test
    fun largeTvUsesRoomWithoutChangingFocusGeometry() {
        val standard = profilePickerTvMetrics(screenWidthDp = 1280, screenHeightDp = 720)
        val large = profilePickerTvMetrics(screenWidthDp = 1920, screenHeightDp = 1080)

        assertTrue(large.cardWidthDp > standard.cardWidthDp)
        assertTrue(large.avatarSizeDp > standard.avatarSizeDp)
        assertTrue(large.rowPaddingDp > standard.rowPaddingDp)
        assertEquals(standard.focusBorderDp, large.focusBorderDp, 0.001f)
    }

    @Test
    fun allTvClassesKeepPremiumFocusBorder() {
        listOf(
            960 to 540,
            1280 to 720,
            1920 to 1080,
        ).forEach { (width, height) ->
            assertEquals(2f, profilePickerTvMetrics(width, height).focusBorderDp, 0.001f)
        }
    }
}

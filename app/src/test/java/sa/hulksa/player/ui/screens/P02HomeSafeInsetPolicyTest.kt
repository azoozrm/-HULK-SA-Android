package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class P02HomeSafeInsetPolicyTest {
    @Test
    fun `tv horizontal safe inset follows max 32dp 3 percent capped 64`() {
        assertEquals(32f, homeHorizontalSafeInsetDp(isTv = true, screenWidthDp = 960), 0.001f)
        assertEquals(57.6f, homeHorizontalSafeInsetDp(isTv = true, screenWidthDp = 1920), 0.001f)
        assertEquals(64f, homeHorizontalSafeInsetDp(isTv = true, screenWidthDp = 3840), 0.001f)
    }

    @Test
    fun `tv vertical safe inset follows max 24dp 4 percent capped 48`() {
        assertEquals(24f, homeVerticalSafeInsetDp(isTv = true, screenHeightDp = 540), 0.001f)
        assertEquals(43.2f, homeVerticalSafeInsetDp(isTv = true, screenHeightDp = 1080), 0.001f)
        assertEquals(48f, homeVerticalSafeInsetDp(isTv = true, screenHeightDp = 2160), 0.001f)
    }

    @Test
    fun `phone safe insets stay compact and deterministic`() {
        assertEquals(16f, homeHorizontalSafeInsetDp(isTv = false, screenWidthDp = 360), 0.001f)
        assertEquals(12f, homeVerticalSafeInsetDp(isTv = false, screenHeightDp = 640), 0.001f)
    }
}

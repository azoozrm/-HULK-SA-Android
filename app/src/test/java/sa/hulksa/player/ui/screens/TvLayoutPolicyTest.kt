package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TvLayoutPolicyTest {
    @Test
    fun `rail logo follows physical viewport instead of unreliable receiver density`() {
        assertEquals(30f, tvRailLogoSizeDp(screenWidthDp = 960), 0.001f)
        assertEquals(40f, tvRailLogoSizeDp(screenWidthDp = 1280), 0.001f)
        assertEquals(60f, tvRailLogoSizeDp(screenWidthDp = 1920), 0.001f)
    }

    @Test
    fun `rail logo remains bounded on unusually small or wide canvases`() {
        assertEquals(28f, tvRailLogoSizeDp(screenWidthDp = 640), 0.001f)
        assertEquals(60f, tvRailLogoSizeDp(screenWidthDp = 3840), 0.001f)
    }
}

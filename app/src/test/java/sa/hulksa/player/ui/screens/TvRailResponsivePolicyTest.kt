package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TvRailResponsivePolicyTest {
    @Test
    fun `compact tv viewport keeps rail usable without shrinking branding`() {
        val metrics = tvRailMetrics(screenWidthDp = 960, screenHeightDp = 540)

        assertEquals(88f, metrics.collapsedWidthDp, 0.001f)
        assertEquals(194f, metrics.expandedWidthDp, 0.001f)
        assertEquals(54f, metrics.logoSizeDp, 0.001f)
        assertEquals(46f, metrics.itemHeightDp, 0.001f)
        assertEquals(23f, metrics.iconSizeDp, 0.001f)
        assertEquals(14f, metrics.labelSizeSp, 0.001f)
    }

    @Test
    fun `standard tv viewport scales rail and logo proportionally`() {
        val metrics = tvRailMetrics(screenWidthDp = 1280, screenHeightDp = 720)

        assertEquals(91.42857f, metrics.collapsedWidthDp, 0.001f)
        assertEquals(206.45161f, metrics.expandedWidthDp, 0.001f)
        assertEquals(72f, metrics.logoSizeDp, 0.001f)
        assertEquals(49.65517f, metrics.itemHeightDp, 0.001f)
        assertEquals(24f, metrics.iconSizeDp, 0.001f)
        assertEquals(14.4f, metrics.labelSizeSp, 0.001f)
    }

    @Test
    fun `large tv viewport is bounded to prevent oversized navigation`() {
        val metrics = tvRailMetrics(screenWidthDp = 1920, screenHeightDp = 1080)

        assertEquals(102f, metrics.collapsedWidthDp, 0.001f)
        assertEquals(236f, metrics.expandedWidthDp, 0.001f)
        assertEquals(78f, metrics.logoSizeDp, 0.001f)
        assertEquals(56f, metrics.itemHeightDp, 0.001f)
        assertEquals(28f, metrics.iconSizeDp, 0.001f)
        assertEquals(17f, metrics.labelSizeSp, 0.001f)
    }
}

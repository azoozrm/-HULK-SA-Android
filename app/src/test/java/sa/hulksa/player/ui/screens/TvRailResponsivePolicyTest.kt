package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy

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

    @Test
    fun `shell rail metrics come from the shared premium policy`() {
        listOf(
            960 to 540,
            1280 to 720,
            1920 to 1080,
        ).forEach { (width, height) ->
            val policy = tvPremiumWindowPolicy(width, height)
            val metrics = tvRailMetrics(width, height)

            assertEquals(policy.railCollapsedWidthDp, metrics.collapsedWidthDp, 0.001f)
            assertEquals(policy.railExpandedWidthDp, metrics.expandedWidthDp, 0.001f)
            assertEquals(policy.railLogoSizeDp, metrics.logoSizeDp, 0.001f)
            assertEquals(policy.railItemHeightDp, metrics.itemHeightDp, 0.001f)
            assertEquals(policy.railIconSizeDp, metrics.iconSizeDp, 0.001f)
            assertEquals(policy.railLabelSizeSp, metrics.labelSizeSp, 0.001f)
            assertEquals(policy.railOuterHorizontalPaddingDp, metrics.outerHorizontalPaddingDp, 0.001f)
            assertEquals(policy.railItemHorizontalPaddingDp, metrics.itemHorizontalPaddingDp, 0.001f)
            assertEquals(policy.railIconLabelGapDp, metrics.iconLabelGapDp, 0.001f)
            assertEquals(policy.railLogoItemGapDp, metrics.logoItemGapDp, 0.001f)
            assertEquals(policy.railItemGapDp, metrics.itemGapDp, 0.001f)
            assertEquals(policy.railTopPaddingDp, metrics.topPaddingDp, 0.001f)
            assertEquals(policy.railBottomPaddingDp, metrics.bottomPaddingDp, 0.001f)
            assertEquals(policy.railCornerRadiusDp, metrics.cornerRadiusDp, 0.001f)
        }
    }
}

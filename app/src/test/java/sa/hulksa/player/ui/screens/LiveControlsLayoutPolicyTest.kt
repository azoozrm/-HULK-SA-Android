package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy

class LiveControlsLayoutPolicyTest {
    private val compactTv = 960 to 540
    private val standardTv = 1280 to 720
    private val largeTv = 1920 to 1080

    @Test
    fun televisionOverlayReusesPremiumSafeWindow() {
        listOf(compactTv, standardTv, largeTv).forEach { (width, height) ->
            val metrics = liveControlsLayoutMetrics(width, height, remoteLayout = true)
            val premium = tvPremiumWindowPolicy(width, height)

            assertTrue(metrics.outerHorizontalPaddingDp >= premium.horizontalSafeInsetDp)
            assertTrue(metrics.outerBottomPaddingDp >= premium.verticalSafeInsetDp)
        }
    }

    @Test
    fun compactTelevisionKeepsStrongOverscanProtection() {
        val compact = liveControlsLayoutMetrics(compactTv.first, compactTv.second, remoteLayout = true)

        assertEquals(24f, compact.outerHorizontalPaddingDp, 0.001f)
        assertEquals(36f, compact.outerBottomPaddingDp, 0.001f)
    }

    @Test
    fun standardAndLargeTelevisionStayBounded() {
        val standard = liveControlsLayoutMetrics(standardTv.first, standardTv.second, remoteLayout = true)
        val large = liveControlsLayoutMetrics(largeTv.first, largeTv.second, remoteLayout = true)

        assertTrue(large.outerHorizontalPaddingDp >= standard.outerHorizontalPaddingDp)
        assertTrue(large.outerBottomPaddingDp >= standard.outerBottomPaddingDp)
        assertTrue(large.outerHorizontalPaddingDp <= 40f)
        assertTrue(large.outerBottomPaddingDp <= 60f)
    }

    @Test
    fun focusEdgePaddingCoversConfiguredFocusVisualExpansion() {
        listOf(compactTv, standardTv, largeTv).forEach { (width, height) ->
            val metrics = liveControlsLayoutMetrics(width, height, remoteLayout = true)
            val premium = tvPremiumWindowPolicy(width, height)
            val focusGrowth = (premium.focusScale - 1f) / 2f

            assertTrue(metrics.rowHorizontalContentPaddingDp > 0f)
            assertTrue(metrics.rowVerticalContentPaddingDp > 0f)
            assertTrue(
                metrics.rowHorizontalContentPaddingDp >=
                    premium.focusBorderWidthDp + focusGrowth * 150f - 0.001f,
            )
            assertTrue(
                metrics.rowVerticalContentPaddingDp >=
                    premium.focusBorderWidthDp + focusGrowth * 40f - 0.001f,
            )
        }
    }

    @Test
    fun touchLayoutKeepsExistingPhoneGeometry() {
        val touch = liveControlsLayoutMetrics(411, 891, remoteLayout = false)

        assertEquals(18f, touch.outerHorizontalPaddingDp, 0.001f)
        assertEquals(13f, touch.outerTopPaddingDp, 0.001f)
        assertEquals(18f, touch.outerBottomPaddingDp, 0.001f)
        assertEquals(0f, touch.rowHorizontalContentPaddingDp, 0.001f)
        assertEquals(2f, touch.rowVerticalContentPaddingDp, 0.001f)
    }

    @Test
    fun livePlayerTransportControlsShareTheCompactUtilityFootprint() {
        listOf(compactTv, standardTv, largeTv).forEach { (width, height) ->
            val metrics = livePlayerControlsMetrics(width, height, remoteLayout = true)

            assertTrue(metrics.approvedSingleRow)
            assertEquals(metrics.utilityIconDp, metrics.transportIconDp)
            assertEquals(metrics.utilityIconDp + 22, metrics.transportContainerDp)
            assertTrue(metrics.transportIconDp in 20..30)
            assertTrue(metrics.morePanelWidthDp in 300..430)
            assertTrue(metrics.morePanelMaxHeightDp in 240..520)
        }
    }

    @Test
    fun compactTouchWindowsUseTheTwoRowControlsAndBoundedPanelHeight() {
        val compact = livePlayerControlsMetrics(411, 891, remoteLayout = false)
        val wideTouch = livePlayerControlsMetrics(800, 360, remoteLayout = false)

        assertFalse(compact.approvedSingleRow)
        assertTrue(wideTouch.approvedSingleRow)
        assertTrue(compact.morePanelWidthDp <= 430)
        assertTrue(compact.morePanelMaxHeightDp < 891)
        assertEquals(compact.utilityIconDp, compact.transportIconDp)
    }

    @Test
    fun livePlayerResizeLabelsUseTheLiveLocalSpellings() {
        assertEquals(listOf("ملايم", "تكبير", "كامل الشاشة"), LIVE_PLAYER_RESIZE_LABELS)
        assertEquals("ملايم", livePlayerResizeLabel(0))
        assertEquals("تكبير", livePlayerResizeLabel(1))
        assertEquals("كامل الشاشة", livePlayerResizeLabel(2))
        assertEquals("ملايم", livePlayerResizeLabel(99))
    }
}

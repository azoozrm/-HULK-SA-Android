package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        }
    }

    @Test
    fun compactTouchWindowsUseTheTwoRowControls() {
        val compact = livePlayerControlsMetrics(411, 891, remoteLayout = false)
        val wideTouch = livePlayerControlsMetrics(800, 360, remoteLayout = false)

        assertFalse(compact.approvedSingleRow)
        assertTrue(wideTouch.approvedSingleRow)
        assertTrue(compact.morePanelWidthDp <= 430)
        assertEquals(compact.utilityIconDp, compact.transportIconDp)
    }

    @Test
    fun livePlayerResizeLabelsKeepTheAlefOnlyCorrection() {
        assertEquals(listOf("ملائم", "تكبير", "ملء الشاشة"), LIVE_PLAYER_RESIZE_LABELS)
        assertEquals("ملائم", livePlayerResizeLabel(0))
        assertEquals("تكبير", livePlayerResizeLabel(1))
        assertEquals("ملء الشاشة", livePlayerResizeLabel(2))
        assertEquals("ملائم", livePlayerResizeLabel(99))
    }

    @Test
    fun reservedStripHeightGrowsWithTheCompactStripGeometry() {
        val compact = livePlayerControlsMetrics(411, 891, remoteLayout = false)
        val large = livePlayerControlsMetrics(1920, 1080, remoteLayout = true)
        val compactLayout = liveControlsLayoutMetrics(411, 891, remoteLayout = false)
        val largeLayout = liveControlsLayoutMetrics(1920, 1080, remoteLayout = true)

        val compactReserved = livePlayerReservedStripHeightDp(
            transportContainerDp = compact.transportContainerDp,
            captionSizeSp = compact.captionSizeSp,
            outerTopPaddingDp = compactLayout.outerTopPaddingDp,
            outerBottomPaddingDp = compactLayout.outerBottomPaddingDp,
        )
        val largeReserved = livePlayerReservedStripHeightDp(
            transportContainerDp = large.transportContainerDp,
            captionSizeSp = large.captionSizeSp,
            outerTopPaddingDp = largeLayout.outerTopPaddingDp,
            outerBottomPaddingDp = largeLayout.outerBottomPaddingDp,
        )

        assertTrue(compactReserved > compact.transportContainerDp.toFloat())
        assertTrue(largeReserved > large.transportContainerDp.toFloat())
        assertTrue(largeReserved > compactReserved)
    }

    @Test
    fun liveErrorActionsUseOneWideRowAndACenteredCompactGrid() {
        assertEquals(4, playerErrorActionColumns(television = true, screenWidthDp = 960))
        assertEquals(4, playerErrorActionColumns(television = false, screenWidthDp = 800))
        assertEquals(2, playerErrorActionColumns(television = false, screenWidthDp = 411))
        assertTrue(playerErrorActionHeightDp(television = true) >= playerErrorActionHeightDp(television = false))

        assertEquals(listOf(listOf(0, 1, 2, 3)), playerErrorActionRows(actionCount = 4, columns = 4))
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4)), playerErrorActionRows(actionCount = 5, columns = 4))
        assertEquals(listOf(listOf(0, 1), listOf(2)), playerErrorActionRows(actionCount = 3, columns = 2))
        assertTrue(playerErrorActionRows(actionCount = 0, columns = 4).isEmpty())
    }

    @Test
    fun liveErrorActionNeighborsFollowTheRtlGrid() {
        val singleRow = playerErrorActionNeighbors(index = 1, count = 4, columns = 4)
        assertEquals(2, singleRow.left)
        assertEquals(0, singleRow.right)
        assertNull(singleRow.up)
        assertNull(singleRow.down)

        val grid = playerErrorActionNeighbors(index = 1, count = 4, columns = 2)
        assertEquals(3, grid.down)
        assertNull(grid.up)
        assertEquals(0, grid.right)
        assertNull(grid.left)

        val secondRow = playerErrorActionNeighbors(index = 3, count = 4, columns = 2)
        assertEquals(1, secondRow.up)
        assertEquals(2, secondRow.right)
        assertNull(secondRow.down)
        assertNull(secondRow.left)

        val invalid = playerErrorActionNeighbors(index = 9, count = 4, columns = 2)
        assertNull(invalid.left)
        assertNull(invalid.down)
    }
}

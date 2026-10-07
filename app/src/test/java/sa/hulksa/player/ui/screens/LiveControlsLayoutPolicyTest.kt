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
    fun liveRowModeUsesTheMeasuredCompleteCaptionsInsteadOfWidthAlone() {
        assertEquals(
            LiveControlsRowMode.SINGLE_ROW,
            liveControlsRowMode(availableWidthPx = 900, requiredSingleRowWidthPx = 860),
        )
        assertEquals(
            LiveControlsRowMode.TWO_ROWS,
            liveControlsRowMode(availableWidthPx = 900, requiredSingleRowWidthPx = 960),
        )
        assertEquals(
            LiveControlsRowMode.TWO_ROWS,
            liveControlsRowMode(availableWidthPx = 0, requiredSingleRowWidthPx = 100),
        )
        assertEquals(
            LiveControlsRowMode.TWO_ROWS,
            liveControlsRowMode(availableWidthPx = 900, requiredSingleRowWidthPx = 0),
        )
    }

    @Test
    fun liveStripGradientAnchorsOnTheMeasuredToolRow() {
        assertEquals(
            0.4f,
            liveStripGradientStopFraction(stripHeightPx = 100f, toolsTopPx = 40f)!!,
            0.001f,
        )
        assertNull(liveStripGradientStopFraction(stripHeightPx = 0f, toolsTopPx = 40f))
        assertNull(liveStripGradientStopFraction(stripHeightPx = 100f, toolsTopPx = 0f))
        assertNull(liveStripGradientStopFraction(stripHeightPx = 100f, toolsTopPx = 100f))
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
    fun liveErrorActionsChooseMeasuredColumnsAndFitTheContentWidth() {
        // 960x540 TV card content width (~739dp) with normal text: four equal actions fit.
        val wideTv = liveErrorActionSizing(
            availableWidthDp = 739,
            actionCount = 4,
            maxColumns = LIVE_PLAYER_ERROR_MAX_COLUMNS,
            requiredActionWidthDp = 150,
            requiredActionHeightDp = 38,
        )
        assertEquals(4, wideTv.columns)
        assertEquals(listOf(listOf(0, 1, 2, 3)), wideTv.rows)
        assertTrue(4 * 150 + 3 * PLAYER_ERROR_ACTION_GAP_DP <= 739)

        // 600x320dp touch card content width (~516dp) with fontScale-2 text: two equal columns.
        val compactScaled = liveErrorActionSizing(
            availableWidthDp = 516,
            actionCount = 4,
            maxColumns = LIVE_PLAYER_ERROR_MAX_COLUMNS,
            requiredActionWidthDp = 240,
            requiredActionHeightDp = 48,
        )
        assertEquals(2, compactScaled.columns)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), compactScaled.rows)
        assertEquals(48, compactScaled.actionHeightDp)
        assertTrue(2 * 240 + PLAYER_ERROR_ACTION_GAP_DP <= 516)

        // 411dp portrait card content width (~342dp) with fontScale-2 text: one column.
        val narrowScaled = liveErrorActionSizing(
            availableWidthDp = 342,
            actionCount = 4,
            maxColumns = LIVE_PLAYER_ERROR_MAX_COLUMNS,
            requiredActionWidthDp = 240,
            requiredActionHeightDp = 48,
        )
        assertEquals(1, narrowScaled.columns)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3)), narrowScaled.rows)
    }

    @Test
    fun liveErrorActionHeightGrowsWithScaledTextInsteadOfClippingIt() {
        val normal = liveErrorActionSizing(739, 4, LIVE_PLAYER_ERROR_MAX_COLUMNS, 150, 36)
        val scaled = liveErrorActionSizing(739, 4, LIVE_PLAYER_ERROR_MAX_COLUMNS, 240, 48)
        val extraScaled = liveErrorActionSizing(739, 4, LIVE_PLAYER_ERROR_MAX_COLUMNS, 260, 56)

        assertTrue(scaled.actionHeightDp > normal.actionHeightDp)
        assertTrue(extraScaled.actionHeightDp > scaled.actionHeightDp)
        assertTrue(scaled.actionHeightDp >= 48)
        assertTrue(scaled.actionWidthDp >= 240)
    }

    @Test
    fun liveErrorActionsHandleHiddenActionsAndDegenerateInputs() {
        assertEquals(listOf(listOf(0, 1, 2, 3)), playerErrorActionRows(actionCount = 4, columns = 4))
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4)), playerErrorActionRows(actionCount = 5, columns = 4))
        assertEquals(listOf(listOf(0, 1), listOf(2)), playerErrorActionRows(actionCount = 3, columns = 2))
        assertTrue(playerErrorActionRows(actionCount = 0, columns = 4).isEmpty())

        val three = liveErrorActionSizing(516, 3, LIVE_PLAYER_ERROR_MAX_COLUMNS, 200, 44)
        assertEquals(2, three.columns)
        assertEquals(listOf(listOf(0, 1), listOf(2)), three.rows)

        val two = liveErrorActionSizing(739, 2, LIVE_PLAYER_ERROR_MAX_COLUMNS, 150, 38)
        assertEquals(2, two.columns)
        assertEquals(listOf(listOf(0, 1)), two.rows)

        val empty = liveErrorActionSizing(739, 0, LIVE_PLAYER_ERROR_MAX_COLUMNS, 150, 38)
        assertEquals(0, empty.columns)
        assertTrue(empty.rows.isEmpty())

        val unmeasured = liveErrorActionSizing(739, 4, LIVE_PLAYER_ERROR_MAX_COLUMNS, 0, 0)
        assertTrue(unmeasured.rows.isEmpty())
    }

    @Test
    fun morePanelFallbackTriggersOnlyWhenTheStripLeavesTooLittleHeight() {
        // 600x320dp touch at fontScale 2: the complete strip leaves roughly 72dp.
        val minimumForScaledCompact = livePlayerMorePanelMinimumHeightDp(captionSizeSp = 11, fontScale = 2f)
        assertTrue(minimumForScaledCompact > 72)
        assertTrue(minimumForScaledCompact < 320)

        val normalTv = livePlayerMorePanelMinimumHeightDp(captionSizeSp = 13, fontScale = 1f)
        assertTrue(normalTv < minimumForScaledCompact)

        assertEquals(
            LiveBottomOverlayMode.FALLBACK,
            liveBottomOverlayMode(remainingHeightPx = 72, minimumPanelHeightPx = minimumForScaledCompact),
        )
        assertEquals(
            LiveBottomOverlayMode.NORMAL,
            liveBottomOverlayMode(
                remainingHeightPx = minimumForScaledCompact,
                minimumPanelHeightPx = minimumForScaledCompact,
            ),
        )
        assertEquals(
            LiveBottomOverlayMode.NORMAL,
            liveBottomOverlayMode(remainingHeightPx = 600, minimumPanelHeightPx = minimumForScaledCompact),
        )
    }

    @Test
    fun morePanelMinimumHeightGrowsWithFontScaleAndStaysBounded() {
        val base = livePlayerMorePanelMinimumHeightDp(captionSizeSp = 11, fontScale = 1f)
        val medium = livePlayerMorePanelMinimumHeightDp(captionSizeSp = 11, fontScale = 1.3f)
        val scaled = livePlayerMorePanelMinimumHeightDp(captionSizeSp = 11, fontScale = 2f)

        assertTrue(medium > base)
        assertTrue(scaled > medium)
        assertTrue(base > 60)
        assertTrue(scaled < 320)
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

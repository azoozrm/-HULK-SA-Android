package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroCopyLayoutPolicyTest {
    private val tvTitleSlot = 84
    private val tvFactsSlot = 56
    private val tvActionsHeight = 46
    private val tvTopGap = 16
    private val tvBottomPadding = 40
    private val tvTitleFactsGap = 8
    private val tvPlotGap = 8
    private val tvPlotLine = 17

    private fun tvMetrics(
        availableHeightPx: Int,
        actionsHeightPx: Int = tvActionsHeight,
    ): HeroCopyLayoutMetrics = heroCopyLayoutMetrics(
        availableHeightPx = availableHeightPx,
        titleSlotPx = tvTitleSlot,
        factsSlotPx = tvFactsSlot,
        actionsHeightPx = actionsHeightPx,
        topGapPx = tvTopGap,
        bottomPaddingPx = tvBottomPadding,
        titleFactsGapPx = tvTitleFactsGap,
        plotGapPx = tvPlotGap,
        plotLineHeightPx = tvPlotLine,
    )

    @Test
    fun `two-line title slot and facts anchor do not depend on title line count`() {
        val metrics = tvMetrics(availableHeightPx = 345)

        assertEquals(2 * 42, tvTitleSlot)
        assertEquals(metrics.titleTopPx + tvTitleSlot + tvTitleFactsGap, metrics.factsTopPx)
        assertEquals(69, metrics.titleTopPx)
        assertEquals(161, metrics.factsTopPx)
        assertEquals(259, metrics.actionsTopPx)
    }

    @Test
    fun `facts and actions stay inside the copy region on a short tv hero`() {
        val metrics = tvMetrics(availableHeightPx = 275)

        assertEquals(1, metrics.reservedPlotLines)
        assertEquals(16, metrics.titleTopPx)
        assertEquals(108, metrics.factsTopPx)
        assertEquals(164, metrics.plotTopPx)
        assertEquals(189, metrics.actionsTopPx)
        assertTrue(metrics.fitsContent)
        assertTrue(metrics.factsTopPx + metrics.factsSlotPx <= metrics.plotTopPx)
        assertTrue(metrics.plotTopPx + metrics.plotLineHeightPx <= metrics.actionsTopPx)
        assertTrue(metrics.actionsTopPx + metrics.actionsHeightPx <= 275 - tvBottomPadding)
    }

    @Test
    fun `plot yields from two lines to one to none as copy height shrinks`() {
        assertEquals(2, tvMetrics(availableHeightPx = 345).reservedPlotLines)
        assertEquals(1, tvMetrics(availableHeightPx = 275).reservedPlotLines)
        assertEquals(0, tvMetrics(availableHeightPx = 259).reservedPlotLines)
    }

    @Test
    fun `a taller measured action block keeps the copy above it without overlap`() {
        val regular = tvMetrics(availableHeightPx = 345, actionsHeightPx = 46)
        val stacked = tvMetrics(availableHeightPx = 345, actionsHeightPx = 101)

        assertTrue(stacked.actionsTopPx < regular.actionsTopPx)
        assertEquals(1, stacked.reservedPlotLines)
        assertTrue(stacked.fitsContent)
        assertTrue(stacked.factsTopPx + stacked.factsSlotPx <= stacked.plotTopPx)
        assertTrue(stacked.plotTopPx + stacked.plotLineHeightPx <= stacked.actionsTopPx)
    }

    @Test
    fun `dense facts consume plot lines before moving the title`() {
        assertEquals(
            2,
            heroCopyVisiblePlotLines(
                reservedPlotLines = 2,
                measuredFactsHeightPx = tvFactsSlot,
                factsSlotPx = tvFactsSlot,
                plotLineHeightPx = tvPlotLine,
            ),
        )
        assertEquals(
            1,
            heroCopyVisiblePlotLines(
                reservedPlotLines = 2,
                measuredFactsHeightPx = tvFactsSlot + 14,
                factsSlotPx = tvFactsSlot,
                plotLineHeightPx = tvPlotLine,
            ),
        )
        assertEquals(
            0,
            heroCopyVisiblePlotLines(
                reservedPlotLines = 2,
                measuredFactsHeightPx = tvFactsSlot + 34,
                factsSlotPx = tvFactsSlot,
                plotLineHeightPx = tvPlotLine,
            ),
        )
    }

    @Test
    fun `facts beyond the reserved plus plot budget shift title up but never overlap actions`() {
        val metrics = tvMetrics(availableHeightPx = 345)
        val factsHeight = 100
        val shift = heroCopyFactsShiftPx(
            measuredFactsHeightPx = factsHeight,
            factsTopPx = metrics.factsTopPx,
            actionsTopPx = metrics.actionsTopPx,
            plotGapPx = tvPlotGap,
            availableTopShiftPx = metrics.titleTopPx - tvTopGap,
        )

        assertEquals(10, shift)
        assertTrue(metrics.factsTopPx - shift + factsHeight <= metrics.actionsTopPx - tvPlotGap)
        assertTrue(metrics.titleTopPx - shift >= tvTopGap)
    }

    @Test
    fun `an impossibly short copy region reports that it does not fit instead of pretending`() {
        val metrics = tvMetrics(availableHeightPx = 240)

        assertFalse(metrics.fitsContent)
        assertEquals(tvTopGap, metrics.titleTopPx)
        assertEquals(0, metrics.reservedPlotLines)
    }
}

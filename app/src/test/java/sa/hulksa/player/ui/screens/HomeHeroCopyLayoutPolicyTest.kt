package sa.hulksa.player.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroCopyLayoutPolicyTest {
    private val normalNonTv = HeroCopySpacing(
        topGapPx = 12,
        titleFactsGapPx = 9,
        factsActionsGapPx = 10,
        bottomPaddingPx = 24,
    )
    private val compact = HeroCopySpacing(
        topGapPx = 4,
        titleFactsGapPx = 4,
        factsActionsGapPx = 4,
        bottomPaddingPx = 8,
    )
    private val minimal = HeroCopySpacing(
        topGapPx = 0,
        titleFactsGapPx = 2,
        factsActionsGapPx = 4,
        bottomPaddingPx = 0,
    )

    private fun nonTvPlacement(
        copyHeightPx: Int,
        factsHeightPx: Int,
        reservedFactsSlotPx: Int = 84,
        titleSlotPx: Int = 68,
        actionsHeightPx: Int = 46,
        plotLineHeightPx: Int = 18,
    ): HeroCopyPlacement = resolveHeroCopyPlacement(
        availableHeightPx = copyHeightPx,
        titleSlotPx = titleSlotPx,
        reservedFactsSlotPx = reservedFactsSlotPx,
        measuredFactsHeightPx = factsHeightPx,
        actionsHeightPx = actionsHeightPx,
        normalSpacing = normalNonTv,
        compactSpacing = compact,
        minimalSpacing = minimal,
        plotLineHeightPx = plotLineHeightPx,
    )

    private fun assertNonOverlapping(
        placement: HeroCopyPlacement,
        availableHeightPx: Int,
    ) {
        assertTrue(placement.fitsContent)
        assertTrue(placement.titleTopPx >= 0)
        assertTrue(placement.titleBottomPx <= placement.factsTopPx)
        assertTrue(
            placement.factsBottomPx + placement.spacing.factsActionsGapPx <=
                placement.actionsTopPx,
        )
        assertTrue(placement.actionsBottomPx <= availableHeightPx)
        if (placement.plotLines > 0) {
            assertTrue(placement.plotTopPx >= placement.factsBottomPx)
            assertTrue(
                placement.plotTopPx + placement.plotLines * placement.plotLineHeightPx <=
                    placement.actionsTopPx,
            )
        }
    }

    @Test
    fun `600x960 non-tv header floor uses measured title subtitle and action height`() {
        val header = heroHeaderMinimumHeightDp(
            statusBarInsetDp = 24.dp,
            verticalPaddingDp = 10.dp,
            titleHeightDp = 25.dp,
            subtitleHeightDp = 13.dp,
            topActionsMinHeightDp = 48.dp,
        )

        assertEquals(92f, header.value, 0.01f)
    }

    @Test
    fun `600x960 non-tv hero grows to the real five-row copy floor`() {
        val effective = effectiveHeroHeightDp(
            policyHeightDp = 288.dp,
            headerMinHeightDp = 92.dp,
            titleSlotDp = 68.dp,
            factsRowHeightDp = 24.dp,
            factsRowGapDp = 6.dp,
            maxFactsRows = 5,
            actionsHeightDp = 46.dp,
        )
        val copyRegion = effective.value - 92f

        assertEquals(356f, effective.value, 0.01f)
        assertEquals(264f, copyRegion, 0.01f)
        assertNonOverlapping(
            placement = nonTvPlacement(
                copyHeightPx = 264,
                factsHeightPx = 54,
            ),
            availableHeightPx = 264,
        )
    }

    @Test
    fun `two wrapped facts rows fit without moving title or actions`() {
        val placement = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 54)

        assertEquals(HeroCopyFallback.NONE, placement.fallback)
        assertEquals(23, placement.titleTopPx)
        assertEquals(100, placement.factsTopPx)
        assertEquals(154, placement.factsBottomPx)
        assertEquals(194, placement.actionsTopPx)
        assertNonOverlapping(placement, 264)
    }

    @Test
    fun `two and three wrapped facts rows keep the same title and facts anchors`() {
        val twoRows = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 54)
        val threeRows = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 84)

        assertEquals(HeroCopyFallback.NONE, twoRows.fallback)
        assertEquals(HeroCopyFallback.NONE, threeRows.fallback)
        assertEquals(twoRows.titleTopPx, threeRows.titleTopPx)
        assertEquals(twoRows.factsTopPx, threeRows.factsTopPx)
        assertEquals(twoRows.actionsTopPx, threeRows.actionsTopPx)
        assertNonOverlapping(threeRows, 264)
    }

    @Test
    fun `fixed two-line title slot keeps facts below the title regardless of title line count`() {
        val placement = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 54)

        assertEquals(68, placement.titleSlotPx)
        assertEquals(
            placement.titleTopPx + 68 + normalNonTv.titleFactsGapPx,
            placement.factsTopPx,
        )
        assertTrue(placement.titleBottomPx <= placement.factsTopPx)
    }

    @Test
    fun `measured facts beyond the initial reservation use compact and minimal placement`() {
        val fourRows = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 102)
        val fiveRows = nonTvPlacement(copyHeightPx = 264, factsHeightPx = 144)

        assertEquals(HeroCopyFallback.COMPACT, fourRows.fallback)
        assertEquals(HeroCopyFallback.MINIMAL, fiveRows.fallback)
        assertEquals(0, fourRows.plotLines)
        assertEquals(0, fiveRows.plotLines)
        assertNonOverlapping(fourRows, 264)
        assertNonOverlapping(fiveRows, 264)
    }

    @Test
    fun `ordinary tv hero keeps the accepted band while three rows fall back cleanly`() {
        val normalTv = HeroCopySpacing(
            topGapPx = 16,
            titleFactsGapPx = 8,
            factsActionsGapPx = 8,
            bottomPaddingPx = 40,
        )
        fun tvPlacement(factsHeightPx: Int) = resolveHeroCopyPlacement(
            availableHeightPx = 284,
            titleSlotPx = 84,
            reservedFactsSlotPx = 56,
            measuredFactsHeightPx = factsHeightPx,
            actionsHeightPx = 46,
            normalSpacing = normalTv,
            compactSpacing = compact,
            minimalSpacing = minimal,
            plotLineHeightPx = 17,
        )

        val ordinary = tvPlacement(56)
        assertEquals(HeroCopyFallback.NONE, ordinary.fallback)
        assertEquals(25, ordinary.titleTopPx)
        assertEquals(117, ordinary.factsTopPx)
        assertEquals(173, ordinary.factsBottomPx)
        assertEquals(198, ordinary.actionsTopPx)
        assertEquals(1, ordinary.plotLines)
        assertNonOverlapping(ordinary, 284)

        val dense = tvPlacement(88)
        assertEquals(HeroCopyFallback.COMPACT, dense.fallback)
        assertEquals(0, dense.plotLines)
        assertNonOverlapping(dense, 284)
    }

    @Test
    fun `tv and portrait phone keep their accepted heights when the real band fits`() {
        val tv = effectiveHeroHeightDp(
            policyHeightDp = 360.dp,
            headerMinHeightDp = 76.dp,
            titleSlotDp = 84.dp,
            factsRowHeightDp = 24.dp,
            factsRowGapDp = 8.dp,
            maxFactsRows = 4,
            actionsHeightDp = 46.dp,
        )
        val phone = effectiveHeroHeightDp(
            policyHeightDp = 420.dp,
            headerMinHeightDp = 92.dp,
            titleSlotDp = 68.dp,
            factsRowHeightDp = 24.dp,
            factsRowGapDp = 6.dp,
            maxFactsRows = 5,
            actionsHeightDp = 101.dp,
        )

        assertEquals(360f, tv.value, 0.01f)
        assertEquals(420f, phone.value, 0.01f)
    }
}

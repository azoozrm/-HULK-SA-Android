package sa.hulksa.player.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroCopyLayoutPolicyTest {
    private val nonTvMetrics = homeHeroCopyMetrics(isTv = false, fontScale = 1f)
    private val tvMetrics = homeHeroCopyMetrics(isTv = true, fontScale = 1f)
    private val normalNonTv = HeroCopySpacing(
        topGapPx = 12,
        titleGenreGapPx = 6,
        genreFactsGapPx = 4,
        factsActionsGapPx = 10,
        bottomPaddingPx = 24,
    )
    private val compact = HeroCopySpacing(
        topGapPx = 4,
        titleGenreGapPx = 4,
        genreFactsGapPx = 4,
        factsActionsGapPx = 4,
        bottomPaddingPx = 8,
    )
    private val minimal = HeroCopySpacing(
        topGapPx = 0,
        titleGenreGapPx = 2,
        genreFactsGapPx = 2,
        factsActionsGapPx = 4,
        bottomPaddingPx = 0,
    )

    private fun nonTvPlacement(
        copyHeightPx: Int,
        factsHeightPx: Int,
        reservedFactsSlotPx: Int = 46,
        titleSlotPx: Int = 68,
        genreSlotPx: Int = 18,
        actionsHeightPx: Int = 46,
        plotLineHeightPx: Int = 18,
    ): HeroCopyPlacement = resolveHeroCopyPlacement(
        availableHeightPx = copyHeightPx,
        titleSlotPx = titleSlotPx,
        genreSlotPx = genreSlotPx,
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
        assertTrue(placement.titleBottomPx <= placement.genreTopPx)
        assertTrue(placement.genreBottomPx <= placement.factsTopPx)
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
    fun `600x960 non-tv hero grows only to the real two-row three-part band`() {
        val effective = effectiveHeroHeightDp(
            policyHeightDp = 288.dp,
            headerMinHeightDp = 92.dp,
            actionsHeightDp = 46.dp,
            metrics = nonTvMetrics,
        )
        val copyRegion = effective.value - 92f

        assertEquals(326f, effective.value, 0.01f)
        assertEquals(234f, copyRegion, 0.01f)
        assertNonOverlapping(
            placement = nonTvPlacement(
                copyHeightPx = 234,
                factsHeightPx = 46,
            ),
            availableHeightPx = 234,
        )
    }

    @Test
    fun `two wrapped facts rows fit without moving title genre or actions`() {
        val placement = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 46)

        assertEquals(HeroCopyFallback.NONE, placement.fallback)
        assertEquals(12, placement.titleTopPx)
        assertEquals(86, placement.genreTopPx)
        assertEquals(108, placement.factsTopPx)
        assertEquals(154, placement.factsBottomPx)
        assertEquals(164, placement.actionsTopPx)
        assertNonOverlapping(placement, 234)
    }

    @Test
    fun `one and two measured fact rows keep the same reserved title genre and action anchors`() {
        val oneRow = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 20)
        val twoRows = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 46)

        assertEquals(oneRow.titleTopPx, twoRows.titleTopPx)
        assertEquals(oneRow.genreTopPx, twoRows.genreTopPx)
        assertEquals(oneRow.factsTopPx, twoRows.factsTopPx)
        assertEquals(oneRow.actionsTopPx, twoRows.actionsTopPx)
        assertTrue(oneRow.factsBottomPx < twoRows.factsBottomPx)
        assertNonOverlapping(twoRows, 234)
    }

    @Test
    fun `fixed two-line title slot keeps genre and facts below the title regardless of title line count`() {
        val placement = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 46)

        assertEquals(68, placement.titleSlotPx)
        assertEquals(18, placement.genreSlotPx)
        assertEquals(
            placement.titleTopPx + 68 + normalNonTv.titleGenreGapPx,
            placement.genreTopPx,
        )
        assertEquals(
            placement.genreTopPx + 18 + normalNonTv.genreFactsGapPx,
            placement.factsTopPx,
        )
    }

    @Test
    fun `measured facts beyond the reservation still yield compact then minimal placement`() {
        val compactPlacement = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 70)
        val minimalPlacement = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 80)

        assertEquals(HeroCopyFallback.COMPACT, compactPlacement.fallback)
        assertEquals(HeroCopyFallback.MINIMAL, minimalPlacement.fallback)
        assertEquals(0, compactPlacement.plotLines)
        assertEquals(0, minimalPlacement.plotLines)
        assertNonOverlapping(compactPlacement, 234)
        assertNonOverlapping(minimalPlacement, 234)
    }

    @Test
    fun `ordinary tv hero keeps its policy height with one or two whole fact rows`() {
        val normalTv = HeroCopySpacing(
            topGapPx = 16,
            titleGenreGapPx = 6,
            genreFactsGapPx = 4,
            factsActionsGapPx = 8,
            bottomPaddingPx = 40,
        )
        fun tvPlacement(factsHeightPx: Int) = resolveHeroCopyPlacement(
            availableHeightPx = 274,
            titleSlotPx = 68,
            genreSlotPx = 18,
            reservedFactsSlotPx = 56,
            measuredFactsHeightPx = factsHeightPx,
            actionsHeightPx = 46,
            normalSpacing = normalTv,
            compactSpacing = compact,
            minimalSpacing = minimal,
            plotLineHeightPx = 17,
        )

        val oneRow = tvPlacement(24)
        val twoRows = tvPlacement(56)

        assertEquals(350f, effectiveHeroHeightDp(
            policyHeightDp = 350.dp,
            headerMinHeightDp = 76.dp,
            actionsHeightDp = 46.dp,
            metrics = tvMetrics,
        ).value, 0.01f)
        assertEquals(HeroCopyFallback.NONE, oneRow.fallback)
        assertEquals(HeroCopyFallback.NONE, twoRows.fallback)
        assertEquals(oneRow.titleTopPx, twoRows.titleTopPx)
        assertEquals(oneRow.genreTopPx, twoRows.genreTopPx)
        assertEquals(oneRow.factsTopPx, twoRows.factsTopPx)
        assertEquals(oneRow.actionsTopPx, twoRows.actionsTopPx)
        assertNonOverlapping(oneRow, 274)
        assertNonOverlapping(twoRows, 274)
    }

    @Test
    fun `font scale grows all three reserved copy slots together`() {
        val scaled = homeHeroCopyMetrics(isTv = true, fontScale = 1.5f)

        assertEquals(102f, scaled.titleSlotDp.value, 0.01f)
        assertEquals(27f, scaled.genreSlotDp.value, 0.01f)
        assertEquals(68f, scaled.factsSlotDp.value, 0.01f)
        assertTrue(scaled.titleSlotDp > tvMetrics.titleSlotDp)
        assertTrue(scaled.genreSlotDp > tvMetrics.genreSlotDp)
        assertTrue(scaled.factsSlotDp > tvMetrics.factsSlotDp)
    }
}

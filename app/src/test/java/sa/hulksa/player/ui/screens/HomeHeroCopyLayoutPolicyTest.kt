package sa.hulksa.player.ui.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        genreSlotPx: Int = 18,
        titleSlotPx: Int = 68,
        actionsHeightPx: Int = 46,
        plotLineHeightPx: Int = 18,
        hasPlot: Boolean = false,
    ): HeroCopyPlacement = resolveHeroCopyPlacement(
        availableHeightPx = copyHeightPx,
        titleSlotPx = titleSlotPx,
        genreSlotPx = genreSlotPx,
        measuredFactsHeightPx = factsHeightPx,
        actionsHeightPx = actionsHeightPx,
        normalSpacing = normalNonTv,
        compactSpacing = compact,
        minimalSpacing = minimal,
        plotLineHeightPx = plotLineHeightPx,
        hasPlot = hasPlot,
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
    fun `one and two measured fact rows keep the same action anchor without phantom rows`() {
        val oneRow = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 20)
        val twoRows = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 46)

        assertTrue(oneRow.titleTopPx > twoRows.titleTopPx)
        assertTrue(oneRow.factsTopPx > twoRows.factsTopPx)
        assertEquals(oneRow.actionsTopPx, twoRows.actionsTopPx)
        assertEquals(
            oneRow.factsBottomPx + oneRow.spacing.factsActionsGapPx,
            oneRow.actionsTopPx,
        )
        assertNonOverlapping(oneRow, 234)
        assertNonOverlapping(twoRows, 234)
    }

    @Test
    fun `missing genre facts and plot leave no phantom rows`() {
        val placement = nonTvPlacement(
            copyHeightPx = 234,
            factsHeightPx = 0,
            genreSlotPx = 0,
            hasPlot = false,
        )

        assertEquals(HeroCopyFallback.NONE, placement.fallback)
        assertEquals(0, placement.genreSlotPx)
        assertEquals(0, placement.factsHeightPx)
        assertEquals(0, placement.plotLines)
        assertEquals(
            placement.factsBottomPx + placement.spacing.factsActionsGapPx,
            placement.actionsTopPx,
        )
        assertNonOverlapping(placement, 234)
    }

    @Test
    fun `a real plot uses spare measured space without reserving a phantom band`() {
        val withoutPlot = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 20, hasPlot = false)
        val withPlot = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 20, hasPlot = true)

        assertEquals(0, withoutPlot.plotLines)
        assertEquals(1, withPlot.plotLines)
        assertEquals(withoutPlot.actionsTopPx, withPlot.actionsTopPx)
        assertTrue(withPlot.titleTopPx < withoutPlot.titleTopPx)
        assertEquals(
            withPlot.plotTopPx + withPlot.plotLines * withPlot.plotLineHeightPx,
            withPlot.actionsTopPx,
        )
        assertNonOverlapping(withPlot, 234)
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
            measuredFactsHeightPx = factsHeightPx,
            actionsHeightPx = 46,
            normalSpacing = normalTv,
            compactSpacing = compact,
            minimalSpacing = minimal,
            plotLineHeightPx = 17,
            hasPlot = false,
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
        assertTrue(oneRow.titleTopPx > twoRows.titleTopPx)
        assertTrue(oneRow.factsTopPx > twoRows.factsTopPx)
        assertEquals(oneRow.actionsTopPx, twoRows.actionsTopPx)
        assertEquals(
            oneRow.factsBottomPx + oneRow.spacing.factsActionsGapPx,
            oneRow.actionsTopPx,
        )
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

    @Test
    fun `home section heading text uses the reference header right edge`() {
        assertEquals(18f, homeSectionHeadingRightInsetDp(isTv = true).value, 0.01f)
        assertEquals(18f, homeSectionHeadingRightInsetDp(isTv = false).value, 0.01f)
        assertEquals(
            26f - TV_PAGE_GUTTER.value,
            homeSectionHeadingRightInsetDp(isTv = true).value,
            0.01f,
        )
        assertEquals(
            18f,
            homeSectionHeadingRightInsetDp(isTv = false).value,
            0.01f,
        )
    }

    @Test
    fun `title style keeps inherited font and exact two-line slot metrics`() {
        val base = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp,
        )
        val style = homeHeroTitleTextStyle(base, nonTvMetrics, "AR-SUBS: The Farm (2023)")

        assertEquals(FontFamily.Monospace, style.fontFamily)
        assertEquals(FontWeight.SemiBold, style.fontWeight)
        assertEquals(28.sp, style.fontSize)
        assertEquals(34.sp, style.lineHeight)
        assertEquals(0.5.sp, style.letterSpacing)
        assertEquals(TextAlign.Right, style.textAlign)
        assertEquals(TextDirection.Content, style.textDirection)
        assertTrue(style.platformStyle != null)
        assertEquals(68f, nonTvMetrics.titleSlotDp.value, 0.01f)
    }

    @Test
    fun `pure strong LTR titles select Content only under unspecified direction`() {
        val base = TextStyle(fontFamily = FontFamily.Monospace)
        val pureLtr = listOf(
            "AR-ANM-S: Muchuu sa Kimi ni (2025) (JP)",
            "AR-SUBS: The Farm (2023)",
            "AR-DE: A Different World (2026) (US)",
            "AR-007: The World Is Not Enough (1999)",
            "2026 Movie",
            "\uD801\uDC00 Example",
        )
        for (title in pureLtr) {
            assertEquals(
                "unexpected direction for $title",
                TextDirection.Content,
                homeHeroTitleTextStyle(base, nonTvMetrics, title).textDirection,
            )
        }
    }

    @Test
    fun `mixed RTL Hebrew neutral and supplementary-RTL titles retain inherited direction`() {
        val base = TextStyle(fontFamily = FontFamily.Monospace)
        val preserved = listOf(
            "AR-KH: سقف الوالد",
            "سقف الوالد",
            "AR: שלום",
            "2026 — ★",
            "\uD83A\uDD00 Example",
        )
        for (title in preserved) {
            assertEquals(
                "unexpected direction for $title",
                base.textDirection,
                homeHeroTitleTextStyle(base, nonTvMetrics, title).textDirection,
            )
        }
    }

    @Test
    fun `explicit BiDi controls prevent the Content override`() {
        val base = TextStyle(fontFamily = FontFamily.Monospace)
        val controls = listOf(
            "\u061C",
            "\u200E",
            "\u200F",
            "\u202A",
            "\u202B",
            "\u202C",
            "\u202D",
            "\u202E",
            "\u2066",
            "\u2067",
            "\u2068",
            "\u2069",
        )
        for (control in controls) {
            val title = "$control" + "AR-SUBS: The Farm (2023)"
            assertEquals(
                "control U+${control[0].code.toString(16)} changed direction",
                base.textDirection,
                homeHeroTitleTextStyle(base, nonTvMetrics, title).textDirection,
            )
        }
    }

    @Test
    fun `explicitly directed inherited titles are preserved`() {
        val ltr = "AR-ANM-S: Muchuu sa Kimi ni (2025) (JP)"
        val mixed = "AR-KH: سقف الوالد"
        for (direction in listOf(
            TextDirection.Ltr,
            TextDirection.Rtl,
            TextDirection.Content,
            TextDirection.ContentOrLtr,
            TextDirection.ContentOrRtl,
        )) {
            val base = TextStyle(fontFamily = FontFamily.Monospace, textDirection = direction)
            assertEquals(direction, homeHeroTitleTextStyle(base, nonTvMetrics, ltr).textDirection)
            assertEquals(direction, homeHeroTitleTextStyle(base, nonTvMetrics, mixed).textDirection)
        }
    }

    @Test
    fun `title direction policy returns the inherited value for null and non-pure titles`() {
        assertEquals(null, homeHeroTitleDirection("", null))
        assertEquals(TextDirection.Rtl, homeHeroTitleDirection("AR-SUBS: The Farm (2023)", TextDirection.Rtl))
        assertEquals(TextDirection.Content, homeHeroTitleDirection("AR-SUBS: The Farm (2023)", null))
    }
}

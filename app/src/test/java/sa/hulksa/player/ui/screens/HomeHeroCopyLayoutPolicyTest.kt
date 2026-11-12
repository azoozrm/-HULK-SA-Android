package sa.hulksa.player.ui.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
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
    private val testDensity = Density(2f, 1f)

    private fun px(dp: Float): Int = with(testDensity) { dp.dp.roundToPx() }

    private fun tvPlacement(
        factsHeightPx: Int,
        factsActionsGapDp: Float,
        bottomPaddingDp: Float,
        fontScale: Float = 1f,
    ): Pair<HeroCopyPlacement, Int> {
        val metrics = homeHeroCopyMetrics(isTv = true, fontScale = fontScale)
        val heightDp = metrics.topGapDp.value + metrics.titleSlotDp.value +
            metrics.titleGenreGapDp.value + metrics.genreSlotDp.value +
            metrics.genreFactsGapDp.value + metrics.factsSlotDp.value +
            factsActionsGapDp + bottomPaddingDp + 46f
        val heightPx = px(heightDp)
        val normal = HeroCopySpacing(
            topGapPx = px(metrics.topGapDp.value),
            titleGenreGapPx = px(metrics.titleGenreGapDp.value),
            genreFactsGapPx = px(metrics.genreFactsGapDp.value),
            factsActionsGapPx = px(factsActionsGapDp),
            bottomPaddingPx = px(bottomPaddingDp),
        )
        val compactTv = HeroCopySpacing(
            topGapPx = px(4f),
            titleGenreGapPx = px(4f),
            genreFactsGapPx = px(4f),
            factsActionsGapPx = px(4f),
            bottomPaddingPx = px(8f),
        )
        val minimalTv = HeroCopySpacing(
            topGapPx = 0,
            titleGenreGapPx = px(2f),
            genreFactsGapPx = px(2f),
            factsActionsGapPx = px(4f),
            bottomPaddingPx = 0,
        )
        val placement = resolveHeroCopyPlacement(
            availableHeightPx = heightPx,
            titleSlotPx = px(metrics.titleSlotDp.value),
            genreSlotPx = px(metrics.genreSlotDp.value),
            measuredFactsHeightPx = factsHeightPx,
            actionsHeightPx = px(46f),
            normalSpacing = normal,
            compactSpacing = compactTv,
            minimalSpacing = minimalTv,
        )
        return placement to heightPx
    }

    private fun nonTvPlacement(
        copyHeightPx: Int,
        factsHeightPx: Int,
        genreSlotPx: Int = 18,
        titleSlotPx: Int = 68,
        actionsHeightPx: Int = 46,
    ): HeroCopyPlacement = resolveHeroCopyPlacement(
        availableHeightPx = copyHeightPx,
        titleSlotPx = titleSlotPx,
        genreSlotPx = genreSlotPx,
        measuredFactsHeightPx = factsHeightPx,
        actionsHeightPx = actionsHeightPx,
        normalSpacing = normalNonTv,
        compactSpacing = compact,
        minimalSpacing = minimal,
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
    fun `missing genre and facts leave no phantom rows`() {
        val placement = nonTvPlacement(
            copyHeightPx = 234,
            factsHeightPx = 0,
            genreSlotPx = 0,
        )

        assertEquals(HeroCopyFallback.NONE, placement.fallback)
        assertEquals(0, placement.genreSlotPx)
        assertEquals(0, placement.factsHeightPx)
        assertEquals(
            placement.factsBottomPx + placement.spacing.factsActionsGapPx,
            placement.actionsTopPx,
        )
        assertNonOverlapping(placement, 234)
    }

    @Test
    fun `retained copy leaves no narrative reservation between facts and actions`() {
        val placement = nonTvPlacement(copyHeightPx = 234, factsHeightPx = 20)

        assertEquals(HeroCopyFallback.NONE, placement.fallback)
        assertEquals(
            placement.factsBottomPx + placement.spacing.factsActionsGapPx,
            placement.actionsTopPx,
        )
        assertEquals(38, placement.titleTopPx)
        assertNonOverlapping(placement, 234)
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
        assertNonOverlapping(compactPlacement, 234)
        assertNonOverlapping(minimalPlacement, 234)
    }

    @Test
    fun `normal tv spacing is paired and preserves the 48dp action band`() {
        assertEquals(16f, tvMetrics.factsActionsGapDp.value, 0.01f)
        assertEquals(32f, tvMetrics.bottomPaddingDp.value, 0.01f)
        assertEquals(
            48f,
            tvMetrics.factsActionsGapDp.value + tvMetrics.bottomPaddingDp.value,
            0.01f,
        )
        assertEquals(10f, nonTvMetrics.factsActionsGapDp.value, 0.01f)
        assertEquals(24f, nonTvMetrics.bottomPaddingDp.value, 0.01f)
        assertEquals(
            350f,
            effectiveHeroHeightDp(
                policyHeightDp = 350.dp,
                headerMinHeightDp = 76.dp,
                actionsHeightDp = 46.dp,
                metrics = tvMetrics,
            ).value,
            0.01f,
        )
    }

    @Test
    fun `normal tv action row moves down 8dp while upper copy and hero band stay fixed`() {
        for (scale in listOf(1f, 1.5f)) {
            val metrics = homeHeroCopyMetrics(isTv = true, fontScale = scale)
            val oneRow = px(metrics.factsRowHeightDp.value)
            val twoRows = px(metrics.factsSlotDp.value)
            for (factsHeightPx in listOf(0, oneRow, twoRows)) {
                val (old, heightPx) = tvPlacement(factsHeightPx, 8f, 40f, scale)
                val (new, _) = tvPlacement(factsHeightPx, 16f, 32f, scale)

                assertEquals(HeroCopyFallback.NONE, old.fallback)
                assertEquals(HeroCopyFallback.NONE, new.fallback)
                assertEquals(old.titleTopPx, new.titleTopPx)
                assertEquals(old.genreTopPx, new.genreTopPx)
                assertEquals(old.factsTopPx, new.factsTopPx)
                assertEquals(old.factsHeightPx, new.factsHeightPx)
                assertEquals(px(8f), new.actionsTopPx - old.actionsTopPx)
                assertEquals(old.factsBottomPx + px(8f), old.actionsTopPx)
                assertEquals(new.factsBottomPx + px(16f), new.actionsTopPx)
                assertEquals(old.actionsBottomPx + px(8f), new.actionsBottomPx)
                assertTrue(new.actionsBottomPx <= heightPx)
                assertNonOverlapping(new, heightPx)
            }
        }
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
    fun `tv hero copy shares the header heading axis while keeping the wider title budget`() {
        assertEquals(26f, homeHeroCopyHorizontalPaddingDp(isTv = true).value, 0.01f)
        assertEquals(
            HOME_HEADER_TV_HORIZONTAL_PADDING_DP.toFloat(),
            homeHeroCopyHorizontalPaddingDp(isTv = true).value,
            0.01f,
        )
        assertEquals(27f, homeHeroCopyWidthBudgetDp(isTv = true).value, 0.01f)
        assertTrue(
            homeHeroCopyWidthBudgetDp(isTv = true) > homeHeroCopyHorizontalPaddingDp(isTv = true),
        )
        // Phone keeps the accepted compact anchor and its unchanged copy budget.
        assertEquals(18f, homeHeroCopyHorizontalPaddingDp(isTv = false).value, 0.01f)
        assertEquals(18f, homeHeroCopyWidthBudgetDp(isTv = false).value, 0.01f)
    }

    @Test
    fun `tv title genre facts and primary action end on the same physical right edge`() {
        val heroWidthDp = 888f
        val anchorDp = homeHeroCopyHorizontalPaddingDp(isTv = true).value
        val headerRightDp = heroWidthDp - anchorDp
        val headingRightDp =
            heroWidthDp - TV_PAGE_GUTTER.value - homeSectionHeadingRightInsetDp(isTv = true).value
        val titleRightDp = heroWidthDp - anchorDp
        val actionRightDp = heroWidthDp - anchorDp
        val genreRightDp = heroWidthDp - anchorDp -
            tvMetrics.factsGroupHorizontalPaddingDp.value +
            homeHeroMetadataEdgeCompensationDp(tvMetrics).value
        val factsChipBorderRightDp = heroWidthDp - anchorDp -
            tvMetrics.factsGroupHorizontalPaddingDp.value +
            homeHeroMetadataEdgeCompensationDp(tvMetrics).value

        assertEquals(headerRightDp, headingRightDp, 0.01f)
        assertEquals(headerRightDp, titleRightDp, 0.01f)
        assertEquals(headerRightDp, actionRightDp, 0.01f)
        assertEquals(headerRightDp, genreRightDp, 0.01f)
        assertEquals(headerRightDp, factsChipBorderRightDp, 0.01f)
        // The compensation is exactly the metadata gutter, never a guessed offset.
        assertEquals(
            tvMetrics.factsGroupHorizontalPaddingDp.value,
            homeHeroMetadataEdgeCompensationDp(tvMetrics).value,
            0.01f,
        )
        assertEquals(10f, homeHeroMetadataEdgeCompensationDp(tvMetrics).value, 0.01f)
        assertEquals(0f, homeHeroMetadataEdgeCompensationDp(nonTvMetrics).value, 0.01f)
        // Non-TV keeps the accepted metadata inset inside its copy host.
        val phoneGenreRightDp = heroWidthDp - homeHeroCopyHorizontalPaddingDp(isTv = false).value -
            nonTvMetrics.factsGroupHorizontalPaddingDp.value +
            homeHeroMetadataEdgeCompensationDp(nonTvMetrics).value
        assertEquals(heroWidthDp - 18f - 8f, phoneGenreRightDp, 0.01f)
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

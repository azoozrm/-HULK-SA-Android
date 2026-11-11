package sa.hulksa.player.ui.screens

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * R20: the production fact-row placement must keep every measured box inside the row it reports,
 * while the visible content still ends on the shared Home header axis. These tests use the same
 * helper the renderer calls, not a re-implementation of the renderer.
 */
class HomeHeroFactBoundsPolicyTest {
    private val tvMetrics = homeHeroCopyMetrics(isTv = true, fontScale = 1f)
    private val phoneMetrics = homeHeroCopyMetrics(isTv = false, fontScale = 1f)
    private val testDensity = Density(2f, 1f)

    private fun px(dp: Float): Int = with(testDensity) { dp.dp.roundToPx() }

    private fun inside(rects: List<HomeHeroFactRect>, parentWidthPx: Int): Boolean =
        rects.all { it.leftPx >= 0 && it.leftPx <= it.rightPx && it.rightPx <= parentWidthPx }

    /**
     * The frozen R19 placement: the leading group kept its full padded width and was shifted right
     * by the gutter, so its padded box overhanged the reported row width. Kept here only as the
     * counterexample the correction must fail.
     */
    private fun legacyR19RowRects(
        parentWidthPx: Int,
        groupWidthsPx: List<Int>,
        separatorWidthPx: Int,
        leadingGutterPx: Int,
    ): List<HomeHeroFactRect> {
        val rects = mutableListOf<HomeHeroFactRect>()
        var x = parentWidthPx
        groupWidthsPx.forEachIndexed { index, width ->
            x -= width
            val left = if (index == 0) x + leadingGutterPx else x
            rects += HomeHeroFactRect(index, left, left + width)
            x = left
            if (index < groupWidthsPx.lastIndex) {
                x -= separatorWidthPx
                rects += HomeHeroFactRect(index, x, x + separatorWidthPx, separator = true)
            }
        }
        return rects
    }

    @Test
    fun `frozen r19 counterexample overhangs the reported row and fails the bounds assertion`() {
        val parent = 709
        val gutter = px(tvMetrics.factsGroupHorizontalPaddingDp.value)
        val legacy = legacyR19RowRects(parent, listOf(100), 2, gutter)

        assertEquals(20, gutter)
        assertEquals(629, legacy.first().leftPx)
        assertEquals(729, legacy.first().rightPx)
        assertFalse(inside(legacy, parent))
    }

    @Test
    fun `single tv group keeps only its visible content inside the row`() {
        val parent = 709
        val logicalWidth = 100
        val leadingWidth = logicalWidth - px(tvMetrics.factsGroupHorizontalPaddingDp.value)
        val rects = homeHeroFactRowRects(parent, listOf(leadingWidth), 2)

        assertNotNull(rects)
        requireNotNull(rects)
        assertTrue(inside(rects, parent))
        assertEquals(629, rects.single().leftPx)
        assertEquals(709, rects.single().rightPx)
    }

    @Test
    fun `two tv groups keep the accepted interior gap and stay inside the row`() {
        val parent = 709
        val gutter = px(tvMetrics.factsGroupHorizontalPaddingDp.value)
        val rects = homeHeroFactRowRects(parent, listOf(100 - gutter, 140), 2)

        requireNotNull(rects)
        assertTrue(inside(rects, parent))
        val first = rects[0]
        val separator = rects[1]
        val second = rects[2]
        assertTrue(separator.separator)
        // The leading group is rendered without its unused outer gutter, so its box is one gutter
        // narrower than the logical group while its right edge is still the row edge.
        assertEquals(100 - gutter, first.rightPx - first.leftPx)
        assertEquals(first.leftPx, separator.rightPx)
        assertEquals(second.rightPx, separator.leftPx)
        assertEquals(2, separator.rightPx - separator.leftPx)
        assertEquals(709, first.rightPx)
    }

    @Test
    fun `two rows are each bounded independently`() {
        val parent = 709
        val gutter = px(tvMetrics.factsGroupHorizontalPaddingDp.value)
        val first = homeHeroFactRowRects(parent, listOf(200 - gutter, 120), 2)
        val second = homeHeroFactRowRects(parent, listOf(300 - gutter, 260), 2)

        requireNotNull(first)
        requireNotNull(second)
        assertTrue(inside(first, parent))
        assertTrue(inside(second, parent))
        assertEquals(parent, first.first().rightPx)
        assertEquals(parent, second.first().rightPx)
    }

    @Test
    fun `quality present and absent leading groups both stay inside the row`() {
        val parent = 709
        val gutter = px(tvMetrics.factsGroupHorizontalPaddingDp.value)
        // Quality chip first (Series), then seasons/episodes/rating.
        val withQuality = homeHeroFactRowRects(parent, listOf(46 - gutter, 70, 64, 52), 2)
        // Movie without a quality field: year first.
        val withoutQuality = homeHeroFactRowRects(parent, listOf(58 - gutter, 40, 52), 2)

        requireNotNull(withQuality)
        requireNotNull(withoutQuality)
        assertTrue(inside(withQuality, parent))
        assertTrue(inside(withoutQuality, parent))
        assertEquals(parent, withQuality.first().rightPx)
        assertEquals(parent, withoutQuality.first().rightPx)
    }

    @Test
    fun `narrow and unfit rows are reported instead of placed out of bounds`() {
        val narrow = homeHeroFactRowRects(200, listOf(80, 140), 2)
        val single = homeHeroFactRowRects(100, listOf(120), 2)

        assertNull(narrow)
        assertNull(single)
    }

    @Test
    fun `unfit presentation keeps the accepted no-placement decision`() {
        val presentation = homeHeroFactPresentation(
            groupWidthsPx = listOf(300),
            preferredWidthPx = 100,
            maxWidthPx = 200,
            separatorWidthPx = 2,
        )

        assertFalse(presentation.layout.fits)
        assertTrue(presentation.layout.rows.isEmpty())
    }

    @Test
    fun `non-tv keeps the unchanged symmetrical placement`() {
        val parent = 709
        val widths = listOf(100, 140)
        val rects = homeHeroFactRowRects(parent, widths, 2)

        requireNotNull(rects)
        assertTrue(inside(rects, parent))
        assertEquals(parent, rects[0].rightPx)
        assertEquals(609, rects[0].leftPx)
        assertEquals(609, rects[1].rightPx)
        assertEquals(467, rects[2].leftPx)
        assertEquals(607, rects[2].rightPx)
        // Non-TV compensation is zero, so the renderer keeps the logical width for the leading group.
        assertEquals(0f, homeHeroMetadataEdgeCompensationDp(phoneMetrics).value, 0.01f)
        assertEquals(8f, phoneMetrics.factsGroupHorizontalPaddingDp.value, 0.01f)
    }

    @Test
    fun `production copy width is converted to pixels at the real density`() {
        val heroWidthDp = 888f
        val fraction = .46f
        val budgetDp = homeHeroCopyWidthBudgetDp(isTv = true)
        val expectedDp = heroWidthDp * fraction - budgetDp.value * 2f
        assertEquals(354.48f, expectedDp, 0.01f)
        for (density in listOf(1f, 1.5f, 2f, 2.75f, 3f)) {
            val d = Density(density, 1f)
            val actualPx = with(d) { (heroWidthDp.dp * fraction - budgetDp * 2f).roundToPx() }
            val expectedPx = (expectedDp * density).roundToInt()
            assertEquals("copy width differs at density $density", expectedPx, actualPx)
            if (density == 2f) {
                assertEquals(709, actualPx)
            }
        }
    }
}

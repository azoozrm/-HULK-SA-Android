package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySidebarUnderlapPolicyTest {
    @Test
    fun `mobile category rows keep their original viewport and padding`() {
        val policy = categorySidebarUnderlapPolicy(
            isTv = false,
            railExpandedWidthDp = 236f,
            parentHorizontalInsetDp = 14f,
            baseContentPaddingDp = 24f,
        )

        assertEquals(0f, policy.viewportExtraDp, 0.001f)
        assertEquals(24f, policy.startContentPaddingDp, 0.001f)
    }

    @Test
    fun `tv category viewport reaches through the expanded sidebar`() {
        val policy = categorySidebarUnderlapPolicy(
            isTv = true,
            railExpandedWidthDp = 206.45161f,
            parentHorizontalInsetDp = 14f,
            baseContentPaddingDp = 8f,
        )

        assertEquals(220.45161f, policy.viewportExtraDp, 0.001f)
        assertEquals(228.45161f, policy.startContentPaddingDp, 0.001f)
    }

    @Test
    fun `adaptive rail sizes preserve the visible category start`() {
        listOf(
            960 to 540,
            1280 to 720,
            1920 to 1080,
        ).forEach { (width, height) ->
            val rail = tvRailMetrics(width, height)
            val policy = categorySidebarUnderlapPolicy(
                isTv = true,
                railExpandedWidthDp = rail.expandedWidthDp,
                parentHorizontalInsetDp = 14f,
                baseContentPaddingDp = 8f,
            )

            assertEquals(rail.expandedWidthDp + 14f, policy.viewportExtraDp, 0.001f)
            assertEquals(8f, policy.startContentPaddingDp - policy.viewportExtraDp, 0.001f)
        }
    }

    @Test
    fun `ltr non sidebar edge correction is only the visible overflow`() {
        assertEquals(
            36,
            categoryNonSidebarEdgeCorrection(
                itemOffset = 696,
                itemSize = 140,
                viewportStartOffset = 0,
                viewportEndOffset = 800,
                isRtl = false,
            ),
        )
    }

    @Test
    fun `rtl non sidebar edge correction is only the visible overflow`() {
        assertEquals(
            36,
            categoryNonSidebarEdgeCorrection(
                itemOffset = -36,
                itemSize = 140,
                viewportStartOffset = 0,
                viewportEndOffset = 800,
                isRtl = true,
            ),
        )
    }

    @Test
    fun `fully visible category chip needs no correction`() {
        assertEquals(
            0,
            categoryNonSidebarEdgeCorrection(
                itemOffset = 320,
                itemSize = 140,
                viewportStartOffset = 0,
                viewportEndOffset = 800,
                isRtl = true,
            ),
        )
    }

    @Test
    fun `rtl sidebar side underlap is intentionally ignored`() {
        assertEquals(
            0,
            categoryNonSidebarEdgeCorrection(
                itemOffset = 760,
                itemSize = 140,
                viewportStartOffset = 0,
                viewportEndOffset = 800,
                isRtl = true,
            ),
        )
    }
}

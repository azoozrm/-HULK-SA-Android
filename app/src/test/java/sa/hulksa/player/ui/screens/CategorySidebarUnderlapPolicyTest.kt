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
    fun `fully visible chip needs no physical edge correction`() {
        assertEquals(
            0f,
            categoryPhysicalNonSidebarEdgeCorrection(
                viewportLeft = 14f,
                viewportRight = 1266f,
                chipLeft = 96f,
                chipRight = 236f,
                sidebarOnPhysicalRight = true,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            categoryPhysicalNonSidebarEdgeCorrection(
                viewportLeft = 14f,
                viewportRight = 1266f,
                chipLeft = 1040f,
                chipRight = 1180f,
                sidebarOnPhysicalRight = false,
            ),
            0.001f,
        )
    }

    @Test
    fun `rtl non sidebar left overflow uses only minimal physical correction`() {
        assertEquals(
            36f,
            categoryPhysicalNonSidebarEdgeCorrection(
                viewportLeft = 14f,
                viewportRight = 1266f,
                chipLeft = -22f,
                chipRight = 118f,
                sidebarOnPhysicalRight = true,
            ),
            0.001f,
        )
    }

    @Test
    fun `rtl sidebar side overflow remains available for underlap`() {
        assertEquals(
            0f,
            categoryPhysicalNonSidebarEdgeCorrection(
                viewportLeft = 14f,
                viewportRight = 1266f,
                chipLeft = 1210f,
                chipRight = 1350f,
                sidebarOnPhysicalRight = true,
            ),
            0.001f,
        )
    }

    @Test
    fun `ltr non sidebar right overflow uses only minimal physical correction`() {
        assertEquals(
            34f,
            categoryPhysicalNonSidebarEdgeCorrection(
                viewportLeft = 14f,
                viewportRight = 1266f,
                chipLeft = 1160f,
                chipRight = 1300f,
                sidebarOnPhysicalRight = false,
            ),
            0.001f,
        )
    }

    @Test
    fun `physical edge correction is resolution independent for 720p 1080p and 4k`() {
        listOf(
            1280f to 720f,
            1920f to 1080f,
            3840f to 2160f,
        ).forEach { (width, _) ->
            val viewportLeft = 14f
            val viewportRight = width - 14f
            assertEquals(
                24f,
                categoryPhysicalNonSidebarEdgeCorrection(
                    viewportLeft = viewportLeft,
                    viewportRight = viewportRight,
                    chipLeft = viewportLeft - 24f,
                    chipRight = viewportLeft + 116f,
                    sidebarOnPhysicalRight = true,
                ),
                0.001f,
            )
        }
    }
}

package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCatalogDensityPolicyTest {
    private fun columnsFor(widthDp: Int, heightDp: Int): Int {
        val metrics = tvCatalogMetrics(widthDp, heightDp)
        val available = (
            widthDp - metrics.horizontalContentPaddingDp - metrics.focusSafeEndPaddingDp
        )
        val cellWidth = tvCatalogColumnCellWidth(
            availableWidthDp = available,
            targetColumns = tvCatalogTargetColumns(widthDp, heightDp),
            horizontalSpacingDp = metrics.horizontalSpacingDp,
        )
        return (((available + metrics.horizontalSpacingDp) / (cellWidth + metrics.horizontalSpacingDp)).toInt())
            .coerceAtLeast(1)
    }

    @Test
    fun `compact tv targets six usable columns`() {
        assertEquals(6, columnsFor(960, 540))
    }

    @Test
    fun `standard tv targets seven usable columns`() {
        assertEquals(7, columnsFor(1280, 720))
    }

    @Test
    fun `larger logical canvas targets eight usable columns`() {
        assertEquals(8, columnsFor(1920, 1080))
    }

    @Test
    fun `catalog geometry stays adaptive instead of resolution hardcoded`() {
        assertEquals(6, tvCatalogTargetColumns(800, 480))
        assertEquals(7, tvCatalogTargetColumns(1366, 768))
        assertEquals(8, tvCatalogTargetColumns(2560, 1440))
        assertEquals(6, tvCatalogTargetColumns(1280, 540))
    }

    @Test
    fun `card geometry never drops below a readable minimum`() {
        val narrow = tvCatalogColumnCellWidth(
            availableWidthDp = 150f,
            targetColumns = 6,
            horizontalSpacingDp = 14f,
        )
        assertTrue(narrow >= 118f)
    }

    @Test
    fun `card geometry stays bounded on very large canvases`() {
        val huge = tvCatalogColumnCellWidth(
            availableWidthDp = 8_000f,
            targetColumns = 1,
            horizontalSpacingDp = 0f,
        )
        assertTrue(huge <= 240f)
    }
}

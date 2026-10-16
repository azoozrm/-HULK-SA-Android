package sa.hulksa.player.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileInlineTopActionsPolicyTest {
    // Measured with the bundled IBM Plex Sans Arabic at fontScale 1.0 (Pango ink widths):
    // title 21sp bold ~= 65.3dp, subtitle 11sp ~= 93.1dp; the natural group is 5 x 48dp = 240dp.
    private val titleWidth = 65.3.dp
    private val subtitleWidth = 93.1.dp

    @Test
    fun ordinaryCompactPhoneWidthsKeepTheSingleRowHeader() {
        listOf(384, 393, 412).forEach { width ->
            assertTrue(
                "width=$width",
                mobileInlineTopActionsFitSingleRow(
                    titleWidth = titleWidth,
                    subtitleWidth = subtitleWidth,
                    availableWidth = width.dp - 36.dp,
                ),
            )
        }
    }

    @Test
    fun genuinelyConstrainedWidthsReflowInsteadOfOverlappingTheTitleOrGroup() {
        listOf(320, 360).forEach { width ->
            assertFalse(
                "width=$width",
                mobileInlineTopActionsFitSingleRow(
                    titleWidth = titleWidth,
                    subtitleWidth = subtitleWidth,
                    availableWidth = width.dp - 36.dp,
                ),
            )
        }
    }

    @Test
    fun theWiderRealLineDecidesTheFit() {
        assertFalse(
            mobileInlineTopActionsFitSingleRow(
                titleWidth = 40.dp,
                subtitleWidth = 93.1.dp,
                availableWidth = 332.dp,
            ),
        )
        assertTrue(
            mobileInlineTopActionsFitSingleRow(
                titleWidth = 40.dp,
                subtitleWidth = 93.1.dp,
                availableWidth = 334.dp,
            ),
        )
    }
}

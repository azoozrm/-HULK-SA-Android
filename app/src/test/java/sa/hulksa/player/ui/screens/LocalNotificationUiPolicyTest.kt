package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LocalNotificationUiPolicyTest {
    @Test
    fun tvNotificationCenterMetricsQualifyAt720p1080pAnd4kLogicalSizes() {
        val profiles = listOf(
            localNotificationCenterMetrics(960, 540, isTv = true),
            localNotificationCenterMetrics(1280, 720, isTv = true),
            localNotificationCenterMetrics(1920, 1080, isTv = true),
        )

        profiles.forEach { metrics ->
            assertTrue(metrics.horizontalPaddingDp in 18..64)
            assertTrue(metrics.topPaddingDp in 16..42)
            assertTrue(metrics.maxContentWidthDp in 760..1320)
            assertTrue(metrics.posterWidthDp in 70..112)
            assertTrue(abs(metrics.posterHeightDp - metrics.posterWidthDp * 1.5f) <= 2f)
        }
        assertTrue(profiles.zipWithNext().all { (first, second) -> first.maxContentWidthDp <= second.maxContentWidthDp })
    }

    @Test
    fun tvNotificationListFocusMovesWithoutTrap() {
        assertNull(nextLocalNotificationFocusIndex(0, 3, movingDown = false))
        assertEquals(1, nextLocalNotificationFocusIndex(0, 3, movingDown = true))
        assertEquals(1, nextLocalNotificationFocusIndex(2, 3, movingDown = false))
        assertNull(nextLocalNotificationFocusIndex(2, 3, movingDown = true))
    }

    @Test
    fun invalidFocusIndexFailsClosed() {
        assertNull(nextLocalNotificationFocusIndex(-1, 3, movingDown = true))
        assertNull(nextLocalNotificationFocusIndex(3, 3, movingDown = false))
        assertNull(nextLocalNotificationFocusIndex(0, 0, movingDown = true))
    }

    @Test
    fun seriesDetailsActionsPreservePreviousWithAdaptiveBalancedRows() {
        assertEquals(1, detailsProMobileActionColumns(320))
        assertEquals(1, detailsProMobileActionColumns(359))
        assertEquals(2, detailsProMobileActionColumns(360))
        assertEquals(2, detailsProMobileActionColumns(840))
        assertEquals(5, seriesDetailsActionCount())
        assertEquals(2, seriesDetailsActionColumns(isTv = false))
        assertEquals(3, seriesDetailsActionColumns(isTv = true))
        assertTrue(seriesDetailsPrimarySpansFullWidth(isTv = false))
        assertTrue(!seriesDetailsPrimarySpansFullWidth(isTv = true))
        assertEquals(50, seriesDetailsActionHeightDp())
        assertTrue(!detailsProActionsUseSingleRow(isTv = false, wide = true, actionCount = 3))
        assertTrue(detailsProActionsUseSingleRow(isTv = true, wide = true, actionCount = 5))
    }

    @Test
    fun homeBellMatchesRefreshVisualSizeWithSafeTouchTarget() {
        assertEquals(44, homeHeaderActionVisualSizeDp())
        assertEquals(48, homeHeaderActionTouchSizeDp())
        assertEquals(homeHeaderActionVisualSizeDp(), notificationBellSizeDp(isTv = false))
        assertEquals(homeHeaderActionVisualSizeDp(), notificationBellSizeDp(isTv = true))
    }

    @Test
    fun phoneTabletAndFoldableCenterMetricsStayResponsive() {
        val phone = localNotificationCenterMetrics(360, 800, isTv = false)
        val landscape = localNotificationCenterMetrics(800, 360, isTv = false)
        val tablet = localNotificationCenterMetrics(840, 1180, isTv = false)
        val foldable = localNotificationCenterMetrics(673, 841, isTv = false)

        assertEquals(14, phone.horizontalPaddingDp)
        assertEquals(24, landscape.horizontalPaddingDp)
        assertEquals(24, tablet.horizontalPaddingDp)
        assertEquals(24, foldable.horizontalPaddingDp)
        assertTrue(phone.posterWidthDp < tablet.posterWidthDp)
        assertTrue(landscape.maxContentWidthDp <= 900)
    }

    @Test
    fun relativeTimeNeverShowsNegativeValues() {
        assertEquals("الآن", localNotificationRelativeTime(createdAtEpochMs = 2_000L, nowEpochMs = 1_000L))
        assertEquals("منذ 2 ساعة", localNotificationRelativeTime(0L, 2L * 60L * 60L * 1_000L))
    }
}

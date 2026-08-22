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
            assertTrue(metrics.horizontalPaddingDp in 16..36)
            assertTrue(metrics.topPaddingDp in 12..28)
            assertTrue(metrics.maxContentWidthDp in 620..980)
            assertTrue(metrics.posterWidthDp in 58..78)
            assertTrue(abs(metrics.posterHeightDp - metrics.posterWidthDp * 1.5f) <= 2f)
        }
        assertTrue(profiles.zipWithNext().all { (first, second) ->
            first.maxContentWidthDp <= second.maxContentWidthDp
        })
    }

    @Test
    fun tvNotificationListFocusMovesWithoutTrap() {
        assertNull(nextLocalNotificationFocusIndex(0, 3, movingDown = false))
        assertEquals(1, nextLocalNotificationFocusIndex(0, 3, movingDown = true))
        assertEquals(1, nextLocalNotificationFocusIndex(2, 3, movingDown = false))
        assertNull(nextLocalNotificationFocusIndex(2, 3, movingDown = true))
    }

    @Test
    fun notificationHeaderEntryAlwaysProvidesReachableTopAction() {
        assertEquals(NotificationHeaderEntry.BACK, notificationHeaderEntry(0, false))
        assertEquals(NotificationHeaderEntry.READ_ALL, notificationHeaderEntry(2, true))
        assertEquals(NotificationHeaderEntry.CLEAR_ALL, notificationHeaderEntry(0, true))
    }

    @Test
    fun notificationHeaderFocusGraphStopsAtEdgesAndNeverJumps() {
        assertEquals(
            NotificationHeaderFocusNode.READ_ALL,
            notificationHeaderMove(
                NotificationHeaderFocusNode.BACK,
                unreadCount = 2,
                hasNotifications = true,
                direction = NotificationFocusDirection.LEFT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.BACK,
                2,
                true,
                NotificationFocusDirection.RIGHT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.BACK,
                2,
                true,
                NotificationFocusDirection.UP,
            ),
        )

        assertEquals(
            NotificationHeaderFocusNode.CLEAR_ALL,
            notificationHeaderMove(
                NotificationHeaderFocusNode.READ_ALL,
                2,
                true,
                NotificationFocusDirection.LEFT,
            ),
        )
        assertEquals(
            NotificationHeaderFocusNode.BACK,
            notificationHeaderMove(
                NotificationHeaderFocusNode.READ_ALL,
                2,
                true,
                NotificationFocusDirection.RIGHT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.READ_ALL,
                2,
                true,
                NotificationFocusDirection.UP,
            ),
        )

        assertEquals(
            NotificationHeaderFocusNode.READ_ALL,
            notificationHeaderMove(
                NotificationHeaderFocusNode.CLEAR_ALL,
                2,
                true,
                NotificationFocusDirection.RIGHT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.CLEAR_ALL,
                2,
                true,
                NotificationFocusDirection.LEFT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.CLEAR_ALL,
                2,
                true,
                NotificationFocusDirection.UP,
            ),
        )

        assertEquals(
            NotificationHeaderFocusNode.CLEAR_ALL,
            notificationHeaderMove(
                NotificationHeaderFocusNode.BACK,
                0,
                true,
                NotificationFocusDirection.DOWN,
            ),
        )
        assertEquals(
            NotificationHeaderFocusNode.BACK,
            notificationHeaderMove(
                NotificationHeaderFocusNode.CLEAR_ALL,
                0,
                true,
                NotificationFocusDirection.RIGHT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.CLEAR_ALL,
                0,
                true,
                NotificationFocusDirection.LEFT,
            ),
        )
        assertNull(
            notificationHeaderMove(
                NotificationHeaderFocusNode.BACK,
                0,
                false,
                NotificationFocusDirection.DOWN,
            ),
        )
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
    fun unreadBadgeSupportsThreeDigitCountsWithoutDistortion() {
        assertNull(notificationBadgeMetrics(0))
        assertEquals("1", notificationBadgeMetrics(1)?.label)
        assertEquals(18, notificationBadgeMetrics(1)?.widthDp)
        assertEquals("99", notificationBadgeMetrics(99)?.label)
        assertEquals(20, notificationBadgeMetrics(99)?.widthDp)
        assertEquals("100", notificationBadgeMetrics(100)?.label)
        assertEquals(24, notificationBadgeMetrics(100)?.widthDp)
        assertEquals(18, notificationBadgeMetrics(100)?.heightDp)
        assertEquals("999", notificationBadgeMetrics(999)?.label)
        assertEquals(24, notificationBadgeMetrics(999)?.widthDp)
        assertEquals("999+", notificationBadgeMetrics(1_000)?.label)
        assertEquals(30, notificationBadgeMetrics(1_000)?.widthDp)
    }

    @Test
    fun mobileNotificationButtonUsesCompleteCompactLabelsWithoutChangingTvCopy() {
        assertEquals("تنبيهات الحلقات", seriesNotificationButtonLabel(enabled = false, isTv = false))
        assertEquals("التنبيهات مفعلة", seriesNotificationButtonLabel(enabled = true, isTv = false))
        assertEquals("نبهني عند نزول حلقة جديدة", seriesNotificationButtonLabel(enabled = false, isTv = true))
        assertEquals(12, seriesNotificationButtonTextSizeSp(isTv = false))
        assertNull(seriesNotificationButtonTextSizeSp(isTv = true))
        assertEquals(50, seriesDetailsActionHeightDp())
        assertEquals(2, seriesDetailsActionColumns(isTv = false))
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

package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeNotificationScanCoalescingTest {
    @Test
    fun weakerIncomingTriggerDoesNotReplaceStrongerPendingTrigger() {
        assertEquals(
            NotificationScanTrigger.LIBRARY_REFRESH,
            coalesceNotificationScanTrigger(
                pending = NotificationScanTrigger.LIBRARY_REFRESH,
                incoming = NotificationScanTrigger.APP_RESUME,
            ),
        )
        assertEquals(
            NotificationScanTrigger.MANUAL_REFRESH,
            coalesceNotificationScanTrigger(
                pending = NotificationScanTrigger.MANUAL_REFRESH,
                incoming = NotificationScanTrigger.LIBRARY_REFRESH,
            ),
        )
    }

    @Test
    fun strongerIncomingTriggerReplacesWeakerPendingTrigger() {
        assertEquals(
            NotificationScanTrigger.MANUAL_REFRESH,
            coalesceNotificationScanTrigger(
                pending = NotificationScanTrigger.APP_RESUME,
                incoming = NotificationScanTrigger.MANUAL_REFRESH,
            ),
        )
        assertEquals(
            NotificationScanTrigger.MASTER_REENABLE,
            coalesceNotificationScanTrigger(
                pending = NotificationScanTrigger.MANUAL_REFRESH,
                incoming = NotificationScanTrigger.MASTER_REENABLE,
            ),
        )
    }

    @Test
    fun noPendingTriggerAdoptsIncomingTrigger() {
        assertEquals(
            NotificationScanTrigger.NOTIFICATION_CENTER,
            coalesceNotificationScanTrigger(
                pending = null,
                incoming = NotificationScanTrigger.NOTIFICATION_CENTER,
            ),
        )
    }

    @Test
    fun equalThrottleRetainsExistingPendingTrigger() {
        assertEquals(
            NotificationScanTrigger.LIBRARY_REFRESH,
            coalesceNotificationScanTrigger(
                pending = NotificationScanTrigger.LIBRARY_REFRESH,
                incoming = NotificationScanTrigger.APP_START,
            ),
        )
    }

    @Test
    fun scanThresholdOrderMatchesTheDocumentedPolicy() {
        assertEquals(0L, NotificationScanTrigger.MASTER_REENABLE.minimumAgeMs)
        assertEquals(60_000L, NotificationScanTrigger.MANUAL_REFRESH.minimumAgeMs)
        assertEquals(120_000L, NotificationScanTrigger.NOTIFICATION_CENTER.minimumAgeMs)
        assertEquals(300_000L, NotificationScanTrigger.LIBRARY_REFRESH.minimumAgeMs)
        assertEquals(300_000L, NotificationScanTrigger.APP_START.minimumAgeMs)
        assertEquals(300_000L, NotificationScanTrigger.PROFILE_SWITCH.minimumAgeMs)
        assertEquals(600_000L, NotificationScanTrigger.APP_RESUME.minimumAgeMs)
    }
}

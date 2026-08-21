package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationsNotificationPolicyTest {
    @Test
    fun systemMessagesRemainSeparateFromEpisodeNotifications() {
        val episode = LocalEpisodeNotification(
            id = "episode:1",
            accountId = "account",
            profileId = "profile",
            seriesId = 11,
            episodeStableKey = "s1e1",
            episodeId = 101,
            seasonNumber = 1,
            episodeNumber = 1,
            seriesName = "Series",
            posterUrl = null,
            categoryId = "series",
            createdAtEpochMs = 1_000L,
            read = false,
            popupShown = false,
            batchId = "batch",
        )
        val system = LocalSystemNotification(
            id = "operations:MSG-001",
            messageId = "MSG-001",
            title = "HULK SA",
            message = "رسالة تشغيلية",
            severity = OperationsAnnouncementSeverity.IMPORTANT,
            createdAtEpochMs = 2_000L,
            read = false,
        )

        val merged = mergeNotificationCenterItems(
            episodeNotifications = listOf(episode),
            systemNotifications = listOf(system, system),
        )

        assertEquals(2, merged.size)
        assertEquals(
            listOf(LocalNotificationKind.SYSTEM_MESSAGE, LocalNotificationKind.NEW_EPISODE),
            merged.map(LocalNotificationItem::kind),
        )
        assertEquals(listOf("operations:MSG-001", "episode:1"), merged.map(LocalNotificationItem::id))
    }
}

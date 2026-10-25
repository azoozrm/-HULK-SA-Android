package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.data.buildEpisodeNotificationSubscription
import sa.hulksa.player.data.evaluateEpisodeScan
import sa.hulksa.player.data.markEpisodeNotificationPopupsShown
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode

class EpisodeNotificationPresentationReadinessTest {
    @Test
    fun blockedReadinessKeepsPendingEventUnconsumed() {
        val pending = listOf(event("event-a"))

        assertNull(
            pendingEpisodeNotificationPopup(
                notificationUiReady = false,
                currentPopup = null,
                episodeNotificationsEnabled = true,
                operationsEpisodeNotificationsEnabled = true,
                screen = HulkScreen.MAIN,
                activeProfileId = "profile-a",
                notifications = pending,
            ),
        )
        assertFalse(pending.single().popupShown)

        val popup = pendingEpisodeNotificationPopup(
            notificationUiReady = true,
            currentPopup = null,
            episodeNotificationsEnabled = true,
            operationsEpisodeNotificationsEnabled = true,
            screen = HulkScreen.MAIN,
            activeProfileId = "profile-a",
            notifications = pending,
        )
        assertNotNull(popup)
        assertEquals(listOf("event-a"), popup!!.eventIds)
        assertFalse("selecting a popup must not consume it", pending.single().popupShown)

        val shown = markEpisodeNotificationPopupsShown(pending, popup.eventIds)
        assertNull(
            pendingEpisodeNotificationPopup(
                notificationUiReady = true,
                currentPopup = null,
                episodeNotificationsEnabled = true,
                operationsEpisodeNotificationsEnabled = true,
                screen = HulkScreen.MAIN,
                activeProfileId = "profile-a",
                notifications = shown,
            ),
        )
    }

    @Test
    fun existingPopupKeepsPrecedence() {
        val pending = listOf(event("event-a"))
        val current = pendingEpisodeNotificationPopup(
            notificationUiReady = true,
            currentPopup = null,
            episodeNotificationsEnabled = true,
            operationsEpisodeNotificationsEnabled = true,
            screen = HulkScreen.MAIN,
            activeProfileId = "profile-a",
            notifications = pending,
        )

        assertNull(
            pendingEpisodeNotificationPopup(
                notificationUiReady = true,
                currentPopup = current,
                episodeNotificationsEnabled = true,
                operationsEpisodeNotificationsEnabled = true,
                screen = HulkScreen.MAIN,
                activeProfileId = "profile-a",
                notifications = pending,
            ),
        )
    }

    @Test
    fun masterOperationsAndBlockingScreensSuppressPresentation() {
        val pending = listOf(event("event-a"))

        assertNull(present(pending, episodeNotificationsEnabled = false))
        assertNull(present(pending, operationsEpisodeNotificationsEnabled = false))
        assertNull(present(pending, screen = HulkScreen.PLAYER))
        assertNull(present(pending, screen = HulkScreen.LOGIN))
        assertNull(present(pending, screen = HulkScreen.NOTIFICATION_CENTER))
    }

    @Test
    fun otherProfileCannotDisplayPreviousOwnersEvent() {
        val pending = listOf(event("event-a", profileId = "profile-a"))

        assertNull(present(pending, activeProfileId = "profile-b"))
        assertNotNull(present(pending, activeProfileId = "profile-a"))
    }

    @Test
    fun oneNewEpisodeYieldsOneAlertAndRepeatedScanDoesNotDuplicate() {
        val baseline = checkNotNull(
            buildEpisodeNotificationSubscription(
                accountId = "account-a",
                profileId = "profile-a",
                series = series(),
                episodes = listOf(episode(101, 1, 1)),
                nowEpochMs = 1_000L,
            ),
        )
        val first = evaluateEpisodeScan(
            subscription = baseline,
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)),
            detectedAtEpochMs = 2_000L,
            batchId = "batch-a",
        )
        val firstPopup = pendingEpisodeNotificationPopup(
            notificationUiReady = true,
            currentPopup = null,
            episodeNotificationsEnabled = true,
            operationsEpisodeNotificationsEnabled = true,
            screen = HulkScreen.MAIN,
            activeProfileId = "profile-a",
            notifications = first.newNotifications,
        )

        assertEquals(1, first.newNotifications.size)
        assertEquals(1, firstPopup?.episodeCount)

        val repeated = evaluateEpisodeScan(
            subscription = first.updatedSubscription,
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)),
            detectedAtEpochMs = 3_000L,
            batchId = "batch-a",
        )

        assertTrue(repeated.newNotifications.isEmpty())
        assertEquals(1, (first.newNotifications + repeated.newNotifications).size)
    }

    private fun present(
        pending: List<LocalEpisodeNotification>,
        episodeNotificationsEnabled: Boolean = true,
        operationsEpisodeNotificationsEnabled: Boolean = true,
        screen: HulkScreen = HulkScreen.MAIN,
        activeProfileId: String = "profile-a",
    ) = pendingEpisodeNotificationPopup(
        notificationUiReady = true,
        currentPopup = null,
        episodeNotificationsEnabled = episodeNotificationsEnabled,
        operationsEpisodeNotificationsEnabled = operationsEpisodeNotificationsEnabled,
        screen = screen,
        activeProfileId = activeProfileId,
        notifications = pending,
    )

    private fun event(
        id: String,
        profileId: String = "profile-a",
    ) = LocalEpisodeNotification(
        id = id,
        accountId = "account-a",
        profileId = profileId,
        seriesId = 44,
        episodeStableKey = "id:$id",
        episodeId = 2,
        seasonNumber = 1,
        episodeNumber = 2,
        seriesName = "series",
        posterUrl = null,
        categoryId = "series",
        createdAtEpochMs = 3_000L,
        read = false,
        popupShown = false,
        batchId = "batch-a",
    )

    private fun episode(
        id: Int,
        season: Int,
        number: Int,
    ) = Episode(
        id = id,
        title = "Episode $number",
        season = season,
        episodeNumber = number,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )

    private fun series() = ContentItem(
        id = 44,
        name = "series",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )
}

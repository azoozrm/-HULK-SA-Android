package sa.hulksa.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.data.EpisodeNotificationPopup
import sa.hulksa.player.data.LocalEpisodeNotification
import sa.hulksa.player.pendingEpisodeNotificationPopup

class NotificationReadinessEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyLibraryReadinessTransitionRerunsProductionEffectBothDirections() {
        var libraryReady by mutableStateOf(false)
        val published = mutableListOf<Boolean>()

        composeRule.setContent {
            val sessionReady = true
            val otherProfileGatesReady = true
            NotificationProfileReadinessEffect(
                ready = sessionReady && libraryReady && otherProfileGatesReady,
            ) { ready -> published += ready }
        }

        composeRule.waitForIdle()
        assertEquals(listOf(false), published)

        composeRule.runOnUiThread { libraryReady = true }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), published)

        composeRule.runOnUiThread { libraryReady = false }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true, false), published)
    }

    @Test
    fun libraryReadinessTransitionMakesPendingEventEligibleWithoutConsumingIt() {
        var libraryReady by mutableStateOf(false)
        val pending = listOf(
            LocalEpisodeNotification(
                id = "synthetic-event",
                accountId = "synthetic-account",
                profileId = "synthetic-profile",
                seriesId = 44,
                episodeStableKey = "id:102",
                episodeId = 102,
                seasonNumber = 1,
                episodeNumber = 2,
                seriesName = "synthetic series",
                posterUrl = null,
                categoryId = "series",
                createdAtEpochMs = 1_000L,
                read = false,
                popupShown = false,
                batchId = "synthetic-batch",
            ),
        )
        var readiness = true
        var eligiblePopup: EpisodeNotificationPopup? = null

        composeRule.setContent {
            NotificationProfileReadinessEffect(ready = libraryReady) { ready ->
                readiness = ready
                eligiblePopup = pendingEpisodeNotificationPopup(
                    notificationUiReady = ready,
                    currentPopup = null,
                    episodeNotificationsEnabled = true,
                    operationsEpisodeNotificationsEnabled = true,
                    screen = HulkScreen.MAIN,
                    activeProfileId = "synthetic-profile",
                    notifications = pending,
                )
            }
        }

        composeRule.waitForIdle()
        assertFalse(readiness)
        assertNull(eligiblePopup)

        composeRule.runOnUiThread { libraryReady = true }
        composeRule.waitForIdle()
        assertEquals(true, readiness)
        assertNotNull(eligiblePopup)
        assertEquals(listOf("synthetic-event"), eligiblePopup!!.eventIds)
        assertFalse(pending.single().popupShown)

        composeRule.runOnUiThread { libraryReady = false }
        composeRule.waitForIdle()
        assertFalse(readiness)
        assertNull(eligiblePopup)
        assertFalse(pending.single().popupShown)
    }

    @Test
    fun unverifiedKidsSourceKeepsEffectUnreadyUntilAvailable() {
        var libraryReady by mutableStateOf(true)
        var kidsSourceAvailable by mutableStateOf<Boolean?>(null)
        val published = mutableListOf<Boolean>()

        composeRule.setContent {
            val activeProfileIsKids = true
            val ready = libraryReady &&
                (!activeProfileIsKids || kidsSourceAvailable == true)
            NotificationProfileReadinessEffect(ready = ready) { published += it }
        }

        composeRule.waitForIdle()
        assertEquals(listOf(false), published)

        composeRule.runOnUiThread { kidsSourceAvailable = false }
        composeRule.waitForIdle()
        assertEquals(listOf(false), published)

        composeRule.runOnUiThread { kidsSourceAvailable = true }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), published)
    }
}

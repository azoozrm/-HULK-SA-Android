package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.data.EpisodeNotificationSubscription
import sa.hulksa.player.model.ProfileKind

class ForegroundEpisodeNotificationSchedulerTest {
    private val now = 10_000_000L
    private val interval = NotificationScanTrigger.LIBRARY_REFRESH.minimumAgeMs

    @Test
    fun dueSubscriptionRequestsExistingAutomaticScan() {
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decide(
                subscriptions = listOf(subscription(lastCheckedAt = now - interval)),
            ),
        )
    }

    @Test
    fun notYetDueSubscriptionSchedulesOnlyTheRemainingPolicyDelay() {
        assertEquals(
            ForegroundNotificationScanDecision.WaitForDelay(1L),
            decide(
                subscriptions = listOf(subscription(lastCheckedAt = now - interval + 1L)),
            ),
        )
    }

    @Test
    fun multipleSubscriptionsUseTheEarliestDueOpportunity() {
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decide(
                subscriptions = listOf(
                    subscription(id = 1, lastCheckedAt = now - 1_000L),
                    subscription(id = 2, lastCheckedAt = now - interval),
                ),
            ),
        )
    }

    @Test
    fun noEnabledSubscriptionWaitsForAWake() {
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(subscriptions = emptyList()),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(
                subscriptions = listOf(
                    subscription(lastCheckedAt = now - interval, enabled = false),
                ),
            ),
        )
    }

    @Test
    fun backgroundAndEveryOwnerGateStopScheduling() {
        val due = listOf(subscription(lastCheckedAt = now - interval))
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(foreground = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(featureEnabled = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(masterEnabled = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(authenticated = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(accountReady = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(profileReady = false, subscriptions = due),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(scanActive = true, subscriptions = due),
        )
    }

    @Test
    fun unreadyKidsWaitsForReadinessAndReadyKidsUsesTheSamePolicy() {
        val due = listOf(subscription(lastCheckedAt = now - interval))
        assertEquals(
            ForegroundNotificationScanDecision.WaitForWake,
            decide(
                profileKind = ProfileKind.KIDS,
                notificationUiReady = false,
                subscriptions = due,
            ),
        )
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decide(
                profileKind = ProfileKind.KIDS,
                notificationUiReady = true,
                subscriptions = due,
            ),
        )
    }

    @Test
    fun failedOrRecentAttemptCannotCreateATightRetryLoop() {
        val due = listOf(subscription(lastCheckedAt = now - interval))
        assertEquals(
            ForegroundNotificationScanDecision.WaitForDelay(299_000L),
            decide(
                subscriptions = due,
                elapsedSinceLastAttemptMs = 1_000L,
            ),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForDelay(150_000L),
            decide(
                subscriptions = due,
                elapsedSinceLastAttemptMs = 150_000L,
            ),
        )
    }

    @Test
    fun uninterruptedForegroundTimelineRequestsTheNextDueScan() {
        var clock = now
        var lastCheckedAt = now - interval
        var lastAttemptClock: Long? = null

        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decideForegroundNotificationScan(
                foreground = true,
                featureEnabled = true,
                masterEnabled = true,
                authenticated = true,
                accountReady = true,
                profileReady = true,
                profileKind = ProfileKind.STANDARD,
                notificationUiReady = true,
                scanActive = false,
                subscriptions = listOf(subscription(lastCheckedAt = lastCheckedAt)),
                nowEpochMs = clock,
                elapsedSinceLastAttemptMs = null,
            ),
        )
        lastAttemptClock = clock
        lastCheckedAt = clock
        clock += interval - 1L
        assertEquals(
            ForegroundNotificationScanDecision.WaitForDelay(1L),
            decideForegroundNotificationScan(
                foreground = true,
                featureEnabled = true,
                masterEnabled = true,
                authenticated = true,
                accountReady = true,
                profileReady = true,
                profileKind = ProfileKind.STANDARD,
                notificationUiReady = true,
                scanActive = false,
                subscriptions = listOf(subscription(lastCheckedAt = lastCheckedAt)),
                nowEpochMs = clock,
                elapsedSinceLastAttemptMs = clock - lastAttemptClock,
            ),
        )
        clock += 1L
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decideForegroundNotificationScan(
                foreground = true,
                featureEnabled = true,
                masterEnabled = true,
                authenticated = true,
                accountReady = true,
                profileReady = true,
                profileKind = ProfileKind.STANDARD,
                notificationUiReady = true,
                scanActive = false,
                subscriptions = listOf(subscription(lastCheckedAt = lastCheckedAt)),
                nowEpochMs = clock,
                elapsedSinceLastAttemptMs = clock - lastAttemptClock,
            ),
        )
    }

    @Test
    fun backwardWallClockCorrectionCannotCreateALongRetry() {
        // R04 reproduced: subscription due at epoch 10,000,000, clock corrected back to 6,400,000,
        // and a process-local attempt an hour old must still scan now rather than wait 65 minutes.
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decideForegroundNotificationScan(
                foreground = true,
                featureEnabled = true,
                masterEnabled = true,
                authenticated = true,
                accountReady = true,
                profileReady = true,
                profileKind = ProfileKind.STANDARD,
                notificationUiReady = true,
                scanActive = false,
                subscriptions = listOf(subscription(lastCheckedAt = 10_000_000L)),
                nowEpochMs = 6_400_000L,
                elapsedSinceLastAttemptMs = 3_600_000L,
                intervalMs = 300_000L,
            ),
        )
        assertEquals(
            ForegroundNotificationScanDecision.WaitForDelay(300_000L),
            decideForegroundNotificationScan(
                foreground = true,
                featureEnabled = true,
                masterEnabled = true,
                authenticated = true,
                accountReady = true,
                profileReady = true,
                profileKind = ProfileKind.STANDARD,
                notificationUiReady = true,
                scanActive = false,
                subscriptions = listOf(subscription(lastCheckedAt = 10_000_000L)),
                nowEpochMs = 6_400_000L,
                elapsedSinceLastAttemptMs = 0L,
                intervalMs = 300_000L,
            ),
        )
    }

    @Test
    fun futureTimestampRemainsDueUnderExistingEligibilityRules() {
        assertEquals(
            ForegroundNotificationScanDecision.ScanNow,
            decide(
                subscriptions = listOf(subscription(lastCheckedAt = now + 60_000L)),
            ),
        )
    }

    private fun decide(
        foreground: Boolean = true,
        featureEnabled: Boolean = true,
        masterEnabled: Boolean = true,
        authenticated: Boolean = true,
        accountReady: Boolean = true,
        profileReady: Boolean = true,
        profileKind: ProfileKind = ProfileKind.STANDARD,
        notificationUiReady: Boolean = true,
        scanActive: Boolean = false,
        subscriptions: List<EpisodeNotificationSubscription>,
        elapsedSinceLastAttemptMs: Long? = null,
    ) = decideForegroundNotificationScan(
        foreground = foreground,
        featureEnabled = featureEnabled,
        masterEnabled = masterEnabled,
        authenticated = authenticated,
        accountReady = accountReady,
        profileReady = profileReady,
        profileKind = profileKind,
        notificationUiReady = notificationUiReady,
        scanActive = scanActive,
        subscriptions = subscriptions,
        nowEpochMs = now,
        elapsedSinceLastAttemptMs = elapsedSinceLastAttemptMs,
        intervalMs = interval,
    )

    private fun subscription(
        id: Int = 1,
        lastCheckedAt: Long,
        enabled: Boolean = true,
    ) = EpisodeNotificationSubscription(
        accountId = "account",
        profileId = "profile",
        seriesId = id,
        seriesName = "Series $id",
        posterUrl = null,
        categoryId = "series",
        enabled = enabled,
        knownEpisodeKeys = setOf("id:$id"),
        lastCheckedAtEpochMs = lastCheckedAt,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}

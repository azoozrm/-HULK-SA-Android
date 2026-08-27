package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableDownloadSchedulerTest {
    @Test
    fun immediateDownloadUsesConnectedNetworkAndNoDelay() {
        val plan = durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 42L,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 10_000L,
        )

        assertEquals(
            durableDownloadUniqueWorkName("account-a", 42L),
            plan.uniqueWorkName,
        )
        assertEquals(0L, plan.initialDelayMs)
        assertEquals(30_000L, plan.backoffDelayMs)
        assertEquals(DurableDownloadNetworkRequirement.CONNECTED, plan.networkRequirement)
    }

    @Test
    fun wifiOnlyDownloadUsesUnmeteredConstraint() {
        val plan = durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 7L,
            wifiOnly = true,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 10_000L,
        )

        assertEquals(DurableDownloadNetworkRequirement.UNMETERED, plan.networkRequirement)
    }

    @Test
    fun futureNightScheduleBecomesInitialDelay() {
        val plan = durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 9L,
            wifiOnly = false,
            scheduledAtEpochMs = 25_000L,
            nowEpochMs = 10_000L,
        )

        assertEquals(15_000L, plan.initialDelayMs)
    }

    @Test
    fun elapsedScheduleNeverCreatesNegativeDelay() {
        val plan = durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 11L,
            wifiOnly = false,
            scheduledAtEpochMs = 5_000L,
            nowEpochMs = 10_000L,
        )

        assertEquals(0L, plan.initialDelayMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDownloadIdIsRejected() {
        durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 0L,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 0L,
        )
    }

    @Test
    fun missingProcessSessionIsRestoredOnlyForCurrentValidAccount() {
        val metadata = metadata(accountId = "account-a", expiresAtEpochSeconds = null)

        assertTrue(
            shouldRestoreDownloadWorkerSession(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = metadata,
                hasAuthenticatedSession = false,
            ),
        )
        assertFalse(
            shouldRestoreDownloadWorkerSession(
                workerAccountId = "account-a",
                activeAccountId = "account-b",
                metadata = metadata,
                hasAuthenticatedSession = false,
            ),
        )
        assertFalse(
            shouldRestoreDownloadWorkerSession(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = metadata,
                hasAuthenticatedSession = true,
            ),
        )
    }

    @Test
    fun expiredSessionMetadataIsNeverRestoredForBackgroundDownload() {
        assertFalse(
            shouldRestoreDownloadWorkerSession(
                workerAccountId = "account-a",
                activeAccountId = "account-a",
                metadata = metadata(accountId = "account-a", expiresAtEpochSeconds = 1L),
                hasAuthenticatedSession = false,
            ),
        )
    }

    private fun metadata(
        accountId: String,
        expiresAtEpochSeconds: Long?,
    ) = AccountSessionMetadata(
        accountId = accountId,
        username = "demo",
        portalBaseUrl = "http://example.test:8080",
        authenticatedAtEpochMs = 1L,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        status = "Active",
        installationId = "install",
        sessionId = "session",
    )
}

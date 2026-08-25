package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DurableDownloadSchedulerTest {
    @Test
    fun immediateDownloadUsesConnectedNetworkAndNoDelay() {
        val plan = durableDownloadWorkPlan(
            downloadId = 42L,
            owner = owner,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 10_000L,
        )

        assertEquals("hulk_durable_download_v2_${downloadOwnerStorageKey(owner)}_42", plan.uniqueWorkName)
        assertEquals(owner, plan.owner)
        assertEquals(0L, plan.initialDelayMs)
        assertEquals(30_000L, plan.backoffDelayMs)
        assertEquals(DurableDownloadNetworkRequirement.CONNECTED, plan.networkRequirement)
    }

    @Test
    fun wifiOnlyDownloadUsesUnmeteredConstraint() {
        val plan = durableDownloadWorkPlan(
            downloadId = 7L,
            owner = owner,
            wifiOnly = true,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 10_000L,
        )

        assertEquals(DurableDownloadNetworkRequirement.UNMETERED, plan.networkRequirement)
    }

    @Test
    fun futureNightScheduleBecomesInitialDelay() {
        val plan = durableDownloadWorkPlan(
            downloadId = 9L,
            owner = owner,
            wifiOnly = false,
            scheduledAtEpochMs = 25_000L,
            nowEpochMs = 10_000L,
        )

        assertEquals(15_000L, plan.initialDelayMs)
    }

    @Test
    fun elapsedScheduleNeverCreatesNegativeDelay() {
        val plan = durableDownloadWorkPlan(
            downloadId = 11L,
            owner = owner,
            wifiOnly = false,
            scheduledAtEpochMs = 5_000L,
            nowEpochMs = 10_000L,
        )

        assertEquals(0L, plan.initialDelayMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDownloadIdIsRejected() {
        durableDownloadWorkPlan(
            downloadId = 0L,
            owner = owner,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 0L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingOwnerIsRejected() {
        durableDownloadWorkPlan(
            downloadId = 1L,
            owner = DownloadOwner("", "primary"),
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 0L,
        )
    }

    private val owner = DownloadOwner("account-a", "primary")
}

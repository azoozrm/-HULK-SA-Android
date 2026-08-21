package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenancePolicyTest {
    @Test
    fun operationalDoesNotBlock() {
        assertEquals(
            OperationsServiceStatus.OPERATIONAL,
            effectiveOperationsServiceStatus(service(OperationsServiceStatus.OPERATIONAL), OperationsConfigSource.NETWORK),
        )
    }

    @Test
    fun degradedRemainsNonBlockingBannerState() {
        assertEquals(
            OperationsServiceStatus.DEGRADED,
            effectiveOperationsServiceStatus(service(OperationsServiceStatus.DEGRADED), OperationsConfigSource.NETWORK),
        )
    }

    @Test
    fun freshNetworkMaintenanceCanBlock() {
        assertEquals(
            OperationsServiceStatus.MAINTENANCE,
            effectiveOperationsServiceStatus(service(OperationsServiceStatus.MAINTENANCE), OperationsConfigSource.NETWORK),
        )
    }

    @Test
    fun unavailableApiMustNotForceCachedMaintenance() {
        assertEquals(
            OperationsServiceStatus.OPERATIONAL,
            effectiveOperationsServiceStatus(service(OperationsServiceStatus.MAINTENANCE), OperationsConfigSource.CACHE),
        )
        assertEquals(
            OperationsServiceStatus.OPERATIONAL,
            effectiveOperationsServiceStatus(null, OperationsConfigSource.DEFAULT),
        )
    }

    @Test
    fun scheduledMaintenanceDoesNotStartEarly() {
        assertEquals(
            OperationsServiceStatus.OPERATIONAL,
            effectiveOperationsServiceStatus(
                OperationsServiceConfig(
                    status = OperationsServiceStatus.MAINTENANCE,
                    startsAtEpochSeconds = 2_000L,
                ),
                OperationsConfigSource.NETWORK,
                nowEpochSeconds = 1_999L,
            ),
        )
    }

    private fun service(status: OperationsServiceStatus) = OperationsServiceConfig(status = status)
}

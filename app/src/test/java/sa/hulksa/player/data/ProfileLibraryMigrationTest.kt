package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLibraryMigrationTest {
    @Test
    fun freshInstallMarksMigrationWithoutCopyingLegacyData() {
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = false,
            scopedFavoritesExist = false,
            legacyFavoritesExist = false,
            scopedHistoryExists = false,
            legacyHistoryExists = false,
        )

        assertFalse(plan.copyLegacyFavorites)
        assertFalse(plan.copyLegacyHistory)
        assertTrue(plan.markMigrationComplete)
    }

    @Test
    fun legacyDataCopiesIntoEmptyPrimaryProfile() {
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = false,
            scopedFavoritesExist = false,
            legacyFavoritesExist = true,
            scopedHistoryExists = false,
            legacyHistoryExists = true,
        )

        assertTrue(plan.copyLegacyFavorites)
        assertTrue(plan.copyLegacyHistory)
        assertTrue(plan.markMigrationComplete)
    }

    @Test
    fun existingScopedDataIsNeverOverwrittenByLegacyData() {
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = false,
            scopedFavoritesExist = true,
            legacyFavoritesExist = true,
            scopedHistoryExists = true,
            legacyHistoryExists = true,
        )

        assertFalse(plan.copyLegacyFavorites)
        assertFalse(plan.copyLegacyHistory)
        assertTrue(plan.markMigrationComplete)
    }

    @Test
    fun completedMigrationIsIdempotent() {
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = true,
            scopedFavoritesExist = false,
            legacyFavoritesExist = true,
            scopedHistoryExists = false,
            legacyHistoryExists = true,
        )

        assertFalse(plan.copyLegacyFavorites)
        assertFalse(plan.copyLegacyHistory)
        assertFalse(plan.markMigrationComplete)
    }
}

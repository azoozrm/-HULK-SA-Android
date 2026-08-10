package sa.hulksa.player.data

internal data class ProfileLibraryMigrationPlan(
    val copyLegacyFavorites: Boolean,
    val copyLegacyHistory: Boolean,
    val markMigrationComplete: Boolean,
)

internal fun profileLibraryMigrationPlan(
    migrationAlreadyComplete: Boolean,
    scopedFavoritesExist: Boolean,
    legacyFavoritesExist: Boolean,
    scopedHistoryExists: Boolean,
    legacyHistoryExists: Boolean,
): ProfileLibraryMigrationPlan {
    if (migrationAlreadyComplete) {
        return ProfileLibraryMigrationPlan(
            copyLegacyFavorites = false,
            copyLegacyHistory = false,
            markMigrationComplete = false,
        )
    }

    return ProfileLibraryMigrationPlan(
        copyLegacyFavorites = !scopedFavoritesExist && legacyFavoritesExist,
        copyLegacyHistory = !scopedHistoryExists && legacyHistoryExists,
        markMigrationComplete = true,
    )
}

package sa.hulksa.player.ui

import sa.hulksa.player.MainDestination

/**
 * Session-only catalog UI context owned by one local profile.
 *
 * This deliberately stores only transient catalog presentation state. It is not
 * written to disk and it does not become Search History. Movies, Series and Live
 * each keep an independent category and in-page query for the active app session.
 */
class ProfileCatalogNavigationMemory {
    private val categoryByDestination = mutableMapOf<MainDestination, String?>()
    private val queryByDestination = mutableMapOf<MainDestination, String>()

    fun save(
        destination: MainDestination,
        categoryId: String?,
        query: String,
    ) {
        if (!destination.isProfileCatalogDestination()) return
        categoryByDestination[destination] = categoryId
        queryByDestination[destination] = query
    }

    fun category(destination: MainDestination): String? =
        if (destination.isProfileCatalogDestination()) {
            categoryByDestination[destination]
        } else {
            null
        }

    fun query(destination: MainDestination): String =
        if (destination.isProfileCatalogDestination()) {
            queryByDestination[destination].orEmpty()
        } else {
            ""
        }
}

internal fun MainDestination.isProfileCatalogDestination(): Boolean = when (this) {
    MainDestination.LIVE,
    MainDestination.MOVIES,
    MainDestination.SERIES,
    -> true

    else -> false
}

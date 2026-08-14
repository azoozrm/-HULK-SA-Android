package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.MainDestination

class ProfileCatalogNavigationMemoryTest {
    @Test
    fun searchQueryIsRestoredIndependentlyFromCatalogQueries() {
        val memory = ProfileCatalogNavigationMemory()

        memory.save(
            destination = MainDestination.SEARCH,
            categoryId = null,
            query = "بريزون بريك",
        )
        memory.save(
            destination = MainDestination.MOVIES,
            categoryId = "action",
            query = "اكشن",
        )

        assertEquals("بريزون بريك", memory.query(MainDestination.SEARCH))
        assertNull(memory.category(MainDestination.SEARCH))
        assertEquals("اكشن", memory.query(MainDestination.MOVIES))
        assertEquals("action", memory.category(MainDestination.MOVIES))
    }

    @Test
    fun searchParticipatesInProfileSessionMemoryButHomeDoesNot() {
        val memory = ProfileCatalogNavigationMemory()

        memory.save(
            destination = MainDestination.HOME,
            categoryId = null,
            query = "لا يجب حفظه",
        )

        assertTrue(MainDestination.SEARCH.isProfileCatalogDestination())
        assertFalse(MainDestination.HOME.isProfileCatalogDestination())
        assertEquals("", memory.query(MainDestination.HOME))
    }
}

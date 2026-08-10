package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileFoundationInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearTestState()
    }

    @After
    fun tearDown() {
        clearTestState()
    }

    @Test
    fun newInstallCreatesStablePrimaryProfile() {
        val store = ProfileStore(context)
        val profiles = store.profiles()
        val active = store.activeProfile()

        assertEquals(ProfileStore.CURRENT_SCHEMA_VERSION, store.schemaVersion())
        assertEquals(1, profiles.size)
        assertEquals(ProfileStore.PRIMARY_PROFILE_ID, active.id)
        assertTrue(active.isPrimary)
        assertTrue(active.displayName.isNotBlank())
    }

    @Test
    fun legacyFavoritesAndHistoryMigrateOnceWithoutDeletingLegacyKeys() {
        val libraryPreferences = context.getSharedPreferences(USER_LIBRARY_PREFS, Context.MODE_PRIVATE)
        val legacyFavorites = setOf("MOVIE:7", "SERIES:11")
        val legacyHistory = """[{"key":"MOVIE:7","title":"Legacy movie","kind":"movie","id":7,"extension":"mp4","live":false,"position":1234,"duration":9000,"updated":100}]"""

        libraryPreferences.edit()
            .putStringSet(LEGACY_FAVORITES_KEY, legacyFavorites)
            .putString(LEGACY_HISTORY_KEY, legacyHistory)
            .commit()

        val firstLibrary = UserLibrary(context)
        val primaryId = ProfileStore(context).activeProfileId()
        val scopedFavoritesKey = "profile:$primaryId:favorites"
        val scopedHistoryKey = "profile:$primaryId:history"

        assertEquals(legacyFavorites, firstLibrary.favorites())
        assertEquals(1, firstLibrary.history().size)
        assertEquals("MOVIE:7", firstLibrary.history().single().key)
        assertEquals(legacyFavorites, libraryPreferences.getStringSet(LEGACY_FAVORITES_KEY, emptySet()))
        assertEquals(legacyHistory, libraryPreferences.getString(LEGACY_HISTORY_KEY, null))
        assertEquals(legacyFavorites, libraryPreferences.getStringSet(scopedFavoritesKey, emptySet()))
        assertEquals(legacyHistory, libraryPreferences.getString(scopedHistoryKey, null))

        firstLibrary.replaceFavorites(setOf("MOVIE:99"))
        val secondLibrary = UserLibrary(context)

        assertEquals(setOf("MOVIE:99"), secondLibrary.favorites())
        assertEquals(legacyFavorites, libraryPreferences.getStringSet(LEGACY_FAVORITES_KEY, emptySet()))
    }

    private fun clearTestState() {
        context.getSharedPreferences(USER_LIBRARY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private companion object {
        const val USER_LIBRARY_PREFS = "hulk_user_library"
        const val PROFILE_PREFS = "hulk_profiles_v1"
        const val LEGACY_FAVORITES_KEY = "favorites"
        const val LEGACY_HISTORY_KEY = "history"
    }
}

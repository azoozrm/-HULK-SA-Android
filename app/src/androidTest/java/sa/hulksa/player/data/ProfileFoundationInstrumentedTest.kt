package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialVault

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

    @Test
    fun logoutKeepsAccessCodeAndRotatedCodeKeepsSameSubscriberLibrary() {
        val sessionStore = AccountSessionStore(context)
        val firstMetadata = sessionStore.recordAuthenticated(
            session(host = HOST_A, accessCode = FIRST_ACCESS_CODE),
        )
        val profileStore = ProfileStore(context)
        val createdProfile = requireNotNull(profileStore.createProfile("متابع"))
        val library = UserLibrary(context)
        val movie = ContentItem(
            id = 7,
            name = "فيلم محفوظ",
            categoryId = "movies",
            type = ContentType.MOVIE,
            posterUrl = null,
            rating = null,
            year = null,
            containerExtension = "mp4",
        )
        library.toggle(movie)
        library.recordStart(
            PlaybackRequest(
                title = movie.name,
                posterUrl = movie.posterUrl,
                candidates = listOf("http://stream.example/movie/7.mp4"),
                isLive = false,
                historyKey = "MOVIE:7",
                streamKind = "movie",
                streamId = movie.id,
                extension = "mp4",
            ),
        )

        assertEquals(FIRST_ACCESS_CODE, sessionStore.lastAccessCode())

        sessionStore.clearActiveSession()

        assertNull(sessionStore.activeAccountId())
        assertEquals(FIRST_ACCESS_CODE, sessionStore.lastAccessCode())

        val secondMetadata = sessionStore.recordAuthenticated(
            session(host = HOST_B, accessCode = SECOND_ACCESS_CODE),
        )

        assertEquals(firstMetadata.accountId, secondMetadata.accountId)
        assertEquals(SECOND_ACCESS_CODE, sessionStore.lastAccessCode())
        assertTrue(ProfileStore(context).profiles().any { it.id == createdProfile.id })

        val restoredLibrary = UserLibrary(context)
        assertTrue("MOVIE:7" in restoredLibrary.favorites())
        assertEquals("MOVIE:7", restoredLibrary.history().single().key)
    }

    @Test
    fun repositoryLogoutPersistsCredentialAndSessionClearBeforeReturn() = runBlocking {
        val authenticated = session(host = HOST_A, accessCode = FIRST_ACCESS_CODE)
        val vault = CredentialVault(context)
        val sessionStore = AccountSessionStore(context)

        vault.save(authenticated.credentials)
        sessionStore.recordAuthenticated(authenticated)
        AuthenticatedSessionRegistry.update(authenticated)

        assertNotNull(vault.load())
        assertNotNull(sessionStore.metadata())
        assertNotNull(AccountScopeStore(context).activeAccountId())
        assertNotNull(AuthenticatedSessionRegistry.current())

        HulkRepository(context).logout()

        assertNull(CredentialVault(context).load())
        assertNull(AccountSessionStore(context).metadata())
        assertNull(AccountScopeStore(context).activeAccountId())
        assertNull(AuthenticatedSessionRegistry.current())
    }

    private fun session(
        host: String,
        accessCode: String,
        username: String = USERNAME,
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(host, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = accessCode,
            username = username,
            password = "secret",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        ),
    )

    private fun clearTestState() {
        CredentialVault(context).clear()
        AuthenticatedSessionRegistry.clear()
        val accountIds = setOf(
            stableAccountId(HOST_A, USERNAME),
            stableAccountId(HOST_B, USERNAME),
        )
        val preferenceNames = linkedSetOf(
            USER_LIBRARY_PREFS,
            PROFILE_PREFS,
            ACCOUNT_SCOPE_PREFS,
            ACCOUNT_SESSION_PREFS,
        )
        accountIds.forEach { accountId ->
            preferenceNames += accountScopedPreferencesName(USER_LIBRARY_PREFS, accountId)
            preferenceNames += accountScopedPreferencesName(PROFILE_PREFS, accountId)
        }
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val USER_LIBRARY_PREFS = "hulk_user_library"
        const val PROFILE_PREFS = "hulk_profiles_v1"
        const val ACCOUNT_SCOPE_PREFS = "hulk_account_scope_v1"
        const val ACCOUNT_SESSION_PREFS = "hulk_account_session_v1"
        const val LEGACY_FAVORITES_KEY = "favorites"
        const val LEGACY_HISTORY_KEY = "history"
        const val HOST_A = "http://first.example.test:8080"
        const val HOST_B = "http://second.example.test:8080"
        const val USERNAME = "subscriber"
        const val FIRST_ACCESS_CODE = "VUKqm6Z6ZZ"
        const val SECOND_ACCESS_CODE = "aB12Cd34Ef"
    }
}

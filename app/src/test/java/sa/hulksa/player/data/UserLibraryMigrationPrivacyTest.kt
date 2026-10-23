package sa.hulksa.player.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLibraryMigrationPrivacyTest {
    @After
    fun clearOwner() {
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun `inner profile history migration does not copy unsafe legacy array`() = runBlocking {
        val fixture = Fixture()
        val unsafe = JSONArray()
            .put(historyRecord())
            .put("http://provider.test/series/alpha/secret/99.mkv")
            .toString()
        fixture.scopedUserPreferences.edit()
            .putBoolean("__account_scope_migrated_v1", true)
            .putString("history", unsafe)
            .apply()

        val snapshot = fixture.library.initializeForProfile(ACCOUNT_ID, fixture.profile)

        assertTrue(snapshot != null)
        assertTrue(snapshot!!.history.isEmpty())
        assertNull(fixture.scopedUserPreferences.durableValue("profile:primary:history"))
        assertEquals(unsafe, fixture.scopedUserPreferences.durableValue("history"))
        assertTrue(
            hasUnsafeLegacyMigrationMarker(
                fixture.scopedUserPreferences,
                USER_LIBRARY_PREFERENCES_BASE,
            ),
        )
        assertEquals(
            HistoryDocumentState.UNREADABLE,
            readHistoryDocument(
                fixture.scopedUserPreferences,
                "profile:primary:history",
            ).state,
        )

        val restarted = fixture.restartLibrary()
        assertTrue(restarted.initializeForProfile(ACCOUNT_ID, fixture.profile)!!.history.isEmpty())
        assertNull(fixture.scopedUserPreferences.durableValue("profile:primary:history"))
    }

    @Test
    fun `safe legacy history still migrates normally`() = runBlocking {
        val fixture = Fixture()
        val safe = JSONArray().put(historyRecord()).toString()
        fixture.scopedUserPreferences.edit()
            .putBoolean("__account_scope_migrated_v1", true)
            .putString("history", safe)
            .apply()

        val snapshot = fixture.library.initializeForProfile(ACCOUNT_ID, fixture.profile)

        assertEquals(1, snapshot!!.history.size)
        assertEquals("movie:1", snapshot.history.single().key)
        assertFalse(
            hasUnsafeLegacyMigrationMarker(
                fixture.scopedUserPreferences,
                USER_LIBRARY_PREFERENCES_BASE,
            ),
        )
        assertTrue(fixture.scopedUserPreferences.durableValue("profile:primary:history") != null)
    }

    private class Fixture {
        private val context = TestAppContext()
        private val scopeState = context.getSharedPreferences("hulk_account_scope_v1", 0)
        val scopedUserPreferences = context.getSharedPreferences("hulk_user_library.account.$ACCOUNT_ID", 0) as FakeSharedPreferences
        private val scopedProfilePreferences =
            context.getSharedPreferences("hulk_profiles_v1.account.$ACCOUNT_ID", 0)
        private val accountScope = AccountScopeStore(scopeState) { name ->
            context.getSharedPreferences(name, 0)
        }
        private val profileStore = ProfileStore(accountScope) { name ->
            context.getSharedPreferences(name, 0)
        }
        val library = UserLibrary(
            accountScope,
            profileStore,
            KidsContentFilterStore(context),
        )
        val profile get() = profileStore.profiles().single()

        init {
            check(accountScope.bind(ACCOUNT_ID))
            scopedProfilePreferences.edit()
                .putBoolean("__account_scope_migrated_v1", true)
                .putString(
                    "profiles",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "primary")
                            .put("displayName", "Adult")
                            .put("kind", "STANDARD")
                            .put("avatarKey", "default")
                            .put("createdAtEpochMs", 1_000L)
                            .put("isPrimary", true),
                    ).toString(),
                )
                .putString("active_profile_id", "primary")
                .putInt("schema_version", 1)
                .apply()
        }

        fun restartLibrary(): UserLibrary = UserLibrary(
            accountScope,
            profileStore,
            KidsContentFilterStore(context),
        )
    }

    private companion object {
        const val ACCOUNT_ID = "account-a"

        fun historyRecord(): JSONObject = JSONObject()
            .put("key", "movie:1")
            .put("title", "Movie 1")
            .put("poster", "")
            .put("kind", "movie")
            .put("id", 1)
            .put("extension", "mp4")
            .put("live", false)
            .put("position", 4_000L)
            .put("duration", 60_000L)
            .put("updated", 4_000L)
    }
}

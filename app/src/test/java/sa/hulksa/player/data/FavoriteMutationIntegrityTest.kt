package sa.hulksa.player.data

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.model.ProfileKind

class FavoriteMutationIntegrityTest {
    @Test
    fun `applied add and remove publish the authoritative persisted set`() {
        val fixture = Fixture()
        val item = movie(1)

        val added = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = item,
        )
        assertEquals(FavoriteMutationStatus.APPLIED_ADDED, added.status)
        assertEquals(setOf("MOVIE:1"), added.favorites)
        assertEquals(setOf("MOVIE:1"), fixture.library.favorites())

        val removed = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = item,
        )
        assertEquals(FavoriteMutationStatus.APPLIED_REMOVED, removed.status)
        assertTrue(removed.favorites.isEmpty())
        assertTrue(fixture.library.favorites().isEmpty())
    }

    @Test
    fun `Kids rejection never persists the unfiltered requested set`() {
        val fixture = Fixture(profileKind = ProfileKind.KIDS, allowedMovieIds = setOf(10))
        val allowed = movie(10)
        val blocked = movie(99)

        val added = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = allowed,
        )
        assertEquals(FavoriteMutationStatus.APPLIED_ADDED, added.status)

        val rejected = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = blocked,
        )
        assertEquals(FavoriteMutationStatus.KIDS_REJECTED, rejected.status)
        assertEquals(setOf("MOVIE:10"), rejected.favorites)
        assertEquals(setOf("MOVIE:10"), fixture.library.favorites())
        assertFalse("MOVIE:99" in fixture.library.favorites())
    }

    @Test
    fun `stale profile or session cannot mutate or publish another owner result`() {
        val fixture = Fixture()

        val wrongProfile = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = "other-profile",
            item = movie(1),
        )
        assertEquals(FavoriteMutationStatus.OWNER_CHANGED, wrongProfile.status)
        assertTrue(wrongProfile.favorites.isEmpty())
        assertTrue(fixture.library.favorites().isEmpty())

        AuthenticatedSessionRegistry.clear()
        val staleSession = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = movie(1),
        )
        assertEquals(FavoriteMutationStatus.OWNER_CHANGED, staleSession.status)
        assertTrue(fixture.library.favorites().isEmpty())
    }

    @Test
    fun `durable favorite write failure is not reported as applied`() {
        val fixture = Fixture()
        fixture.libraryPreferences().commitResult = false

        val failed = fixture.library.toggleFavoriteForOwner(
            expectedOwner = fixture.owner,
            expectedAccountId = fixture.accountId,
            expectedProfileId = FAVORITE_PROFILE_ID,
            item = movie(1),
        )

        assertEquals(FavoriteMutationStatus.PERSISTENCE_FAILED, failed.status)
        assertTrue(failed.favorites.isEmpty())
        assertTrue(fixture.library.favorites().isEmpty())
    }

    private class Fixture(
        profileKind: ProfileKind = ProfileKind.STANDARD,
        allowedMovieIds: Set<Int> = emptySet(),
    ) {
        val accountId = "account-${UUID.randomUUID()}"
        private val context = TestAppContext()
        private val accountScope = AccountScopeStore(context)
        private val profileStore = ProfileStore(accountScope) { name ->
            context.getSharedPreferences(name, 0)
        }
        private val kidsContentFilterStore = KidsContentFilterStore(context)
        val library = UserLibrary(accountScope, profileStore, kidsContentFilterStore)
        val owner: AuthenticatedSessionOwner

        init {
            val session = AuthenticatedSession(
                portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
                credentials = Credentials("HULK-TEST-CODE", "user", "pass"),
                account = AccountInfo("user", "Active", Long.MAX_VALUE, 0, 1, false),
            )
            val metadata = AccountSessionStore(context).recordAuthenticated(session, accountId)
            AuthenticatedSessionRegistry.update(session, metadata)
            owner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
            seedProfile(profileKind)
            if (profileKind == ProfileKind.KIDS) {
                check(
                    kidsContentFilterStore.replace(
                        owner,
                        VerifiedKidsCatalogSnapshot(
                            catalogs = mapOf(
                                ContentType.MOVIE to Catalog(
                                    emptyList(),
                                    allowedMovieIds.sorted().map(::movie),
                                ),
                            ),
                            blockedTypes = emptyMap(),
                        ),
                    ),
                )
            }
        }

        fun libraryPreferences(): FakeSharedPreferences =
            context.getSharedPreferences(
                accountScopedPreferencesName("hulk_user_library", accountId),
                0,
            ) as FakeSharedPreferences

        private fun seedProfile(profileKind: ProfileKind) {
            accountScope.preferences(ProfileStore.PREFERENCES_NAME, accountId).edit()
                .putString(
                    "profiles",
                    JSONArray().put(
                        JSONObject()
                            .put("id", FAVORITE_PROFILE_ID)
                            .put("displayName", "Profile")
                            .put("kind", profileKind.name)
                            .put("avatarKey", "default")
                            .put("createdAtEpochMs", 1_000L)
                            .put("isPrimary", true),
                    ).toString(),
                )
                .putString("active_profile_id", FAVORITE_PROFILE_ID)
                .putInt("schema_version", 1)
                .apply()
        }
    }

}

private const val FAVORITE_PROFILE_ID = "profile-1"

private fun movie(id: Int): ContentItem = ContentItem(
    id = id,
    name = "Movie $id",
    categoryId = "category-$id",
    type = ContentType.MOVIE,
    posterUrl = null,
    rating = null,
    year = null,
    containerExtension = "mp4",
)

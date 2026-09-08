package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

@RunWith(AndroidJUnit4::class)
class OwnerScopedMetadataAndKidsInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearState()
        assertTrue(AccountScopeStore(context).bind(ACCOUNT_ID))
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun providerReplacementCannotReadMetadataOrKidsAllowListForSameNumericId() {
        val firstSession = session(PROVIDER_A)
        AuthenticatedSessionRegistry.update(firstSession, metadata(PROVIDER_A, "session-a"))
        val firstOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        val metadataStore = HomeHeroMetadataStore.get(context)
        val seriesMetadataStore = SeriesCardMetadataStore.get(context)
        val kidsStore = KidsContentFilterStore(context)
        val movie = movie(42)
        val kidsSnapshot = VerifiedKidsCatalogSnapshot(
            catalogs = mapOf(
                ContentType.MOVIE to Catalog(categories = emptyList(), items = listOf(movie)),
            ),
            blockedTypes = emptyMap(),
        )

        context.getSharedPreferences(MOVIE_METADATA_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString("movie:42:quality", "LEGACY")
            .commit()
        context.getSharedPreferences(SERIES_METADATA_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString("series:42:quality", "LEGACY")
            .commit()
        context.getSharedPreferences(KIDS_FILTER_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("snapshot_verified", true)
            .putStringSet("allowed_content_keys", setOf("MOVIE:42"))
            .commit()

        assertNull(metadataStore.cached(firstOwner, movie).quality)
        assertNull(seriesMetadataStore.cached(firstOwner, 42).quality)
        assertTrue(kidsStore.allowedKeys().isEmpty())

        assertTrue(
            metadataStore.cacheMovieMetadata(
                owner = firstOwner,
                movieId = movie.id,
                quality = "FHD",
                durationMs = 7_200_000L,
            ),
        )
        assertTrue(kidsStore.replace(firstOwner, kidsSnapshot))
        context.getSharedPreferences(
            authenticatedOwnerPreferencesName(SERIES_METADATA_PREFERENCES, firstOwner),
            Context.MODE_PRIVATE,
        ).edit()
            .putString("series:42:quality", "FHD")
            .putInt("series:42:season_count", 3)
            .putInt("series:42:episode_count", 24)
            .commit()
        assertEquals("FHD", metadataStore.cached(firstOwner, movie).quality)
        assertEquals("FHD", seriesMetadataStore.cached(firstOwner, 42).quality)
        assertTrue(kidsStore.isAllowed(movie))

        val replacementSession = session(PROVIDER_B)
        AuthenticatedSessionRegistry.update(
            replacementSession,
            metadata(PROVIDER_B, "session-b"),
        )
        val replacementOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())

        assertNull(metadataStore.cached(replacementOwner, movie).quality)
        assertNull(seriesMetadataStore.cached(replacementOwner, 42).quality)
        assertTrue(kidsStore.allowedKeys().isEmpty())
        assertNull(metadataStore.cached(firstOwner, movie).quality)
        assertNull(seriesMetadataStore.cached(firstOwner, 42).quality)
    }

    @Test
    fun reloginRejectsLateMetadataAndKidsWritesFromPreviousSession() {
        val firstSession = session(PROVIDER_A)
        AuthenticatedSessionRegistry.update(firstSession, metadata(PROVIDER_A, "session-a"))
        val firstOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        val metadataStore = HomeHeroMetadataStore.get(context)
        val kidsStore = KidsContentFilterStore(context)
        val originalMovie = movie(42)
        val originalSnapshot = VerifiedKidsCatalogSnapshot(
            catalogs = mapOf(
                ContentType.MOVIE to Catalog(
                    categories = emptyList(),
                    items = listOf(originalMovie),
                ),
            ),
            blockedTypes = emptyMap(),
        )
        assertTrue(metadataStore.cacheMovieMetadata(firstOwner, 42, quality = "FHD"))
        assertTrue(kidsStore.replace(firstOwner, originalSnapshot))

        val replacementSession = session(PROVIDER_A)
        AuthenticatedSessionRegistry.update(
            replacementSession,
            metadata(PROVIDER_A, "session-b"),
        )
        val replacementOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        val lateSnapshot = VerifiedKidsCatalogSnapshot(
            catalogs = mapOf(
                ContentType.MOVIE to Catalog(
                    categories = emptyList(),
                    items = listOf(movie(99)),
                ),
            ),
            blockedTypes = emptyMap(),
        )

        assertFalse(metadataStore.cacheMovieMetadata(firstOwner, 42, quality = "SD"))
        assertFalse(kidsStore.replace(firstOwner, lateSnapshot))
        assertEquals("FHD", metadataStore.cached(replacementOwner, originalMovie).quality)
        assertTrue(kidsStore.isAllowed(originalMovie))
        assertFalse(kidsStore.isAllowed(movie(99)))
    }

    private fun session(portal: String) = AuthenticatedSession(
        portal = PortalConfig(portal, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = "abcdefghij",
            username = USERNAME,
            password = "secret",
        ),
        account = AccountInfo(
            username = USERNAME,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        ),
    )

    private fun metadata(portal: String, sessionId: String) = AccountSessionMetadata(
        accountId = ACCOUNT_ID,
        username = USERNAME,
        portalBaseUrl = portal,
        authenticatedAtEpochMs = 1L,
        expiresAtEpochSeconds = null,
        status = "Active",
        installationId = "installation-a",
        sessionId = sessionId,
    )

    private fun movie(id: Int) = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "kids",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
    )

    private fun clearState() {
        AuthenticatedSessionRegistry.clear()
        context.getSharedPreferences(ACCOUNT_SCOPE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        listOf(
            MOVIE_METADATA_PREFERENCES,
            SERIES_METADATA_PREFERENCES,
            KIDS_FILTER_PREFERENCES,
        ).forEach { baseName ->
            context.getSharedPreferences(baseName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        listOf(PROVIDER_A, PROVIDER_B).forEach { provider ->
            val providerId = stableAccountId(provider, USERNAME)
            listOf(
                MOVIE_METADATA_PREFERENCES,
                SERIES_METADATA_PREFERENCES,
                KIDS_FILTER_PREFERENCES,
            ).forEach { baseName ->
                val ownerScopedBase = "$baseName.provider.$providerId"
                val name = accountScopedPreferencesName(ownerScopedBase, ACCOUNT_ID)
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }
    }

    private companion object {
        const val ACCOUNT_ID = "shared-subscriber-account"
        const val USERNAME = "subscriber"
        const val PROVIDER_A = "https://provider-a.example"
        const val PROVIDER_B = "https://provider-b.example"
        const val ACCOUNT_SCOPE_PREFERENCES = "hulk_account_scope_v1"
        const val MOVIE_METADATA_PREFERENCES = "movie_card_verified_metadata"
        const val SERIES_METADATA_PREFERENCES = "series_card_verified_metadata"
        const val KIDS_FILTER_PREFERENCES = "hulk_kids_content_filter_v1"
    }
}

package sa.hulksa.player

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.data.AccountScopeStore
import sa.hulksa.player.data.AccountSessionStore
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.EpisodeNotificationStoreResult
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.LocalEpisodeNotificationStore
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.data.OperationsClient
import sa.hulksa.player.data.OperationsFetchResult
import sa.hulksa.player.data.PortalResolver
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.XtreamClient
import sa.hulksa.player.data.accountScopedPreferencesName
import sa.hulksa.player.data.stableAccountId
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.model.SeriesBundle
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.security.CredentialVault

/**
 * Identical controlled-provider scenarios used against released 68 and released 69 source trees.
 * The test seam only injects the fake provider/operations client; notification scheduling source
 * under comparison is the released implementation itself.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseNotificationParityTest {
    private lateinit var context: Context
    private var viewModel: HulkViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearState()
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        clearState()
    }

    @Test
    fun startupAutomaticDetectionParity() = runBlocking {
        val metadata = seedAccountAndSubscription(
            baselineCheckedAtEpochMs = 0L,
            baselineEpisodes = listOf(episodeA()),
        )
        val client = ParityClient(listOf(episodeA(), episodeB()))
        val model = modelFor(client)
        viewModel = model

        val detected = withTimeout(15_000) {
            model.state.first { state -> state.hasEpisodeB() }
        }
        val calls = client.seriesCalls.get()
        Log.i(TAG, "scenario=startup calls=$calls unread=${detected.unreadNotificationCount} " +
            "profile=${metadata.profileId} episodeB=${detected.countEpisodeB()}")
        assertEquals(1, calls)
        assertEquals(1, detected.unreadNotificationCount)
        assertEquals(1, detected.countEpisodeB())
    }

    @Test
    fun resumeAutomaticDetectionParity() = runBlocking {
        val metadata = seedAccountAndSubscription(
            baselineCheckedAtEpochMs = 0L,
            baselineEpisodes = listOf(episodeA()),
        )
        val client = ParityClient(listOf(episodeA()))
        val model = modelFor(client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }
        withTimeout(10_000) {
            while (client.seriesCalls.get() < 1) delay(50)
        }
        // Let the startup scan commit its baseline before rewinding it for the resume scenario.
        withTimeout(10_000) {
            while (
                context.notificationStore().snapshot(metadata.profileId)
                    .subscriptions.single().lastCheckedAtEpochMs <= 0L
            ) {
                delay(50)
            }
        }
        delay(200)
        client.episodes = listOf(episodeA(), episodeB())
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            context.notificationStore().replaceBaseline(
                profileId = metadata.profileId,
                seriesId = SERIES_ID,
                episodes = listOf(episodeA()),
                checkedAtEpochMs = System.currentTimeMillis() - ELEVEN_MINUTES_MS,
                expectedAccountId = metadata.accountId,
            ),
        )
        val callsBefore = client.seriesCalls.get()
        model.onAppResumed()
        val detected = withTimeout(10_000) {
            model.state.first { state -> state.hasEpisodeB() }
        }
        val calls = client.seriesCalls.get()
        Log.i(TAG, "scenario=resume calls=$calls before=$callsBefore unread=${detected.unreadNotificationCount} " +
            "episodeB=${detected.countEpisodeB()}")
        assertEquals(callsBefore + 1, calls)
        assertEquals(1, detected.unreadNotificationCount)
    }

    @Test
    fun manualRefreshDetectionParity() = runBlocking {
        val metadata = seedAccountAndSubscription(
            baselineCheckedAtEpochMs = System.currentTimeMillis() - TWO_MINUTES_MS,
            baselineEpisodes = listOf(episodeA()),
        )
        val client = ParityClient(listOf(episodeA(), episodeB()))
        val model = modelFor(client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }
        delay(1_000)
        val callsBefore = client.seriesCalls.get()
        model.refresh()
        val detected = withTimeout(10_000) {
            model.state.first { state -> state.hasEpisodeB() }
        }
        val calls = client.seriesCalls.get()
        Log.i(TAG, "scenario=manual calls=$calls before=$callsBefore unread=${detected.unreadNotificationCount} " +
            "episodeB=${detected.countEpisodeB()}")
        assertEquals(0, callsBefore)
        assertEquals(1, calls)
        assertEquals(1, detected.unreadNotificationCount)
    }

    private fun HulkUiState.hasEpisodeB(): Boolean = localNotifications.any { item ->
        item is LocalNotificationItem.Episode && item.notification.episodeNumber == 2
    }

    private fun HulkUiState.countEpisodeB(): Int = localNotifications.count { item ->
        item is LocalNotificationItem.Episode && item.notification.episodeNumber == 2
    }

    private suspend fun seedAccountAndSubscription(
        baselineCheckedAtEpochMs: Long,
        baselineEpisodes: List<Episode>,
    ): Seed {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        val profile: UserProfile = requireNotNull(profileStore.load(metadata.accountId))
            .let { profileStore.activeProfile() }
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            context.notificationStore().enableSubscription(
                profileId = profile.id,
                series = series(),
                episodes = baselineEpisodes,
                nowEpochMs = baselineCheckedAtEpochMs,
                expectedAccountId = metadata.accountId,
            ),
        )
        return Seed(accountId = metadata.accountId, profileId = profile.id)
    }

    private fun modelFor(client: XtreamClient): HulkViewModel {
        CredentialVault(context).save(session().credentials)
        return HulkViewModel(
            application = context as Application,
            repository = HulkRepository(
                context = context,
                vault = CredentialVault(context),
                accountSessionStore = AccountSessionStore(context),
                portalResolver = object : PortalResolver() {
                    override suspend fun resolve(accessCode: String): PortalConfig = session().portal
                },
                client = client,
            ),
            operationsClient = object : OperationsClient() {
                override suspend fun fetch(): OperationsFetchResult = OperationsFetchResult.Failure
            },
        )
    }

    private fun Context.notificationStore() = LocalEpisodeNotificationStore(this)

    private fun session() = AuthenticatedSession(
        portal = PortalConfig(PORTAL, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = ACCESS_CODE,
            username = USERNAME,
            password = PASSWORD,
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

    private fun series() = ContentItem(
        id = SERIES_ID,
        name = "Parity Series",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    private fun episodeA() = Episode(
        id = 101,
        title = "Episode A",
        season = 1,
        episodeNumber = 1,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )

    private fun episodeB() = Episode(
        id = 102,
        title = "Episode B",
        season = 1,
        episodeNumber = 2,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )

    private fun clearState() {
        AuthenticatedSessionRegistry.clear()
        val accountId = stableAccountId(PORTAL, USERNAME)
        listOf(
            "hulk_account_scope_v1",
            "hulk_account_session_v1",
            "hulk_secure_session",
            "hulk_operations_v1",
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        listOf(
            "hulk_profiles_v1",
            "hulk_user_library",
            "hulk_local_episode_notifications_v1",
            "hulk_kids_content_filter_v1.provider.$accountId",
        ).forEach { name ->
            context.getSharedPreferences(
                accountScopedPreferencesName(name, accountId),
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    private inner class ParityClient(
        var episodes: List<Episode>,
    ) : XtreamClient() {
        val seriesCalls = AtomicInteger(0)

        override suspend fun authenticate(
            portal: PortalConfig,
            credentials: Credentials,
        ): AuthenticatedSession = session()

        override suspend fun catalog(
            session: AuthenticatedSession,
            type: ContentType,
        ): Catalog = Catalog(categories = emptyList(), items = emptyList())

        override suspend fun seriesBundle(
            session: AuthenticatedSession,
            seriesId: Int,
        ): SeriesBundle {
            seriesCalls.incrementAndGet()
            return SeriesBundle(details = ContentDetails(), episodes = episodes)
        }
    }

    private data class Seed(val accountId: String, val profileId: String)

    private companion object {
        const val TAG = "R02_PARITY"
        const val PORTAL = "https://parity.invalid"
        const val USERNAME = "parity-user"
        const val PASSWORD = "parity-password"
        const val ACCESS_CODE = "parity-code"
        const val SERIES_ID = 77
        const val TWO_MINUTES_MS = 2L * 60L * 1_000L
        const val ELEVEN_MINUTES_MS = 11L * 60L * 1_000L
    }
}

package sa.hulksa.player

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
import sa.hulksa.player.ui.ProfileAwareHulkApp
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Real production UI comparison: ProfileAwareHulkApp owns profile/library readiness and the
 * notification overlay. No test-only readiness forcing and no directly mounted overlay.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseUiNotificationParityTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun startupUiPresentsEpisodeAfterProductionProfileReadiness() = runBlocking {
        val seed = seedSubscription(baselineEpisodes = listOf(episodeA()), checkedAtEpochMs = 0L)
        val client = UiClient(listOf(episodeA(), episodeB()))
        val model = modelFor(client)
        viewModel = model
        mountProductionUi(model)
        enterFirstProfile()
        waitForHomeReady()
        waitForPopup(seed, client)
    }

    @Test
    fun resumeUiPresentsEpisodeThroughLifecyclePath() = runBlocking {
        val seed = seedSubscription(baselineEpisodes = listOf(episodeA()), checkedAtEpochMs = 0L)
        val client = UiClient(listOf(episodeA()))
        val model = modelFor(client)
        viewModel = model
        mountProductionUi(model)
        enterFirstProfile()
        waitForHomeReady()
        waitForInitialScan(seed)
        client.episodes = listOf(episodeA(), episodeB())
        rewindBaseline(seed, ELEVEN_MINUTES_MS)
        model.onAppResumed()
        waitForPopup(seed, client)
    }

    @Test
    fun manualHomeRefreshUiPresentsEpisodeWithoutManualStateInjection() = runBlocking {
        val seed = seedSubscription(baselineEpisodes = listOf(episodeA()), checkedAtEpochMs = 0L)
        val client = UiClient(listOf(episodeA()))
        val model = modelFor(client)
        viewModel = model
        mountProductionUi(model)
        enterFirstProfile()
        waitForHomeReady()
        waitForInitialScan(seed)
        client.episodes = listOf(episodeA(), episodeB())
        rewindBaseline(seed, TWO_MINUTES_MS)
        composeRule.onAllNodesWithContentDescription("تحديث").onFirst().performClick()
        waitForPopup(seed, client)
    }

    private fun mountProductionUi(model: HulkViewModel) {
        composeRule.setContent {
            HulkTheme {
                ProfileAwareHulkApp(viewModel = model, isTelevisionDevice = true)
            }
        }
    }

    private fun enterFirstProfile() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasText("الرئيسي") and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("الرئيسي") and hasClickAction()).performClick()
    }

    private fun waitForHomeReady() {
        composeRule.waitForIdle()
        composeRule.onRoot().printToLog("R03_TREE")
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithContentDescription("الرئيسية")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForPopup(seed: Seed, client: UiClient) {
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("حلقة جديدة نزلت", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            val state = viewModel!!.state.value
            val snapshot = context.notificationStore().snapshot(seed.profileId)
            Log.e(TAG, "ui-popup-timeout calls=${client.seriesCalls.get()} " +
                "unread=${state.unreadNotificationCount} " +
                "local=${state.localNotifications.size} " +
                "popup=${state.notificationPopup != null} " +
                "ready=${privateField(viewModel!!, "notificationUiReady")} " +
                "subscriptionChecked=${snapshot.subscriptions.firstOrNull()?.lastCheckedAtEpochMs} " +
                "snapshotNotifications=${snapshot.notifications.size}")
            composeRule.onRoot().printToLog("R03_TREE")
            throw error
        }
        val state = viewModel!!.state.value
        val count = state.localNotifications.count { item ->
            item is LocalNotificationItem.Episode && item.notification.episodeNumber == 2
        }
        Log.i(TAG, "ui-popup calls=${client.seriesCalls.get()} unread=${state.unreadNotificationCount} " +
            "episodeB=$count popup=${state.notificationPopup != null}")
        assertEquals(1, count)
        assertEquals(1, state.unreadNotificationCount)
    }

    private suspend fun waitForInitialScan(seed: Seed) {
        withTimeout(10_000) {
            while (
                context.notificationStore().snapshot(seed.profileId)
                    .subscriptions.single().lastCheckedAtEpochMs <= 0L
            ) {
                delay(50)
            }
        }
    }

    private suspend fun rewindBaseline(seed: Seed, ageMs: Long) {
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            context.notificationStore().replaceBaseline(
                profileId = seed.profileId,
                seriesId = SERIES_ID,
                episodes = listOf(episodeA()),
                checkedAtEpochMs = System.currentTimeMillis() - ageMs,
                expectedAccountId = seed.accountId,
            ),
        )
    }

    private suspend fun seedSubscription(
        baselineEpisodes: List<Episode>,
        checkedAtEpochMs: Long,
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
                nowEpochMs = checkedAtEpochMs,
                expectedAccountId = metadata.accountId,
            ),
        )
        CredentialVault(context).save(session().credentials)
        return Seed(accountId = metadata.accountId, profileId = profile.id)
    }

    private fun modelFor(client: UiClient): HulkViewModel {
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

    private fun privateField(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(instance)
        }

    private inner class UiClient(
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
            surface: sa.hulksa.player.data.ProviderSurface,
        ): SeriesBundle {
            seriesCalls.incrementAndGet()
            return SeriesBundle(details = ContentDetails(), episodes = episodes)
        }
    }

    private data class Seed(
        val accountId: String,
        val profileId: String,
    )

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
        name = "UI Series",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )

    private fun episodeA() = Episode(
        id = 301,
        title = "UI A",
        season = 1,
        episodeNumber = 1,
        containerExtension = "mp4",
        posterUrl = null,
        duration = "00:45:00",
    )

    private fun episodeB() = Episode(
        id = 302,
        title = "UI B",
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

    private companion object {
        const val TAG = "R03_UI_PARITY"
        const val PORTAL = "https://ui-parity.invalid"
        const val USERNAME = "ui-parity-user"
        const val PASSWORD = "ui-parity-password"
        const val ACCESS_CODE = "ui-parity-code"
        const val SERIES_ID = 99
        const val TWO_MINUTES_MS = 2L * 60L * 1_000L
        const val ELEVEN_MINUTES_MS = 11L * 60L * 1_000L
    }
}

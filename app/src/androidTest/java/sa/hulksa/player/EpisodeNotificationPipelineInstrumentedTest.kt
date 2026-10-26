package sa.hulksa.player

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.data.AccountSessionStore
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.data.KidsContentFilterStore
import sa.hulksa.player.data.EpisodeNotificationStoreResult
import sa.hulksa.player.data.LocalEpisodeNotificationStore
import sa.hulksa.player.data.LocalNotificationItem
import sa.hulksa.player.data.OperationsClient
import sa.hulksa.player.data.OperationsConfig
import sa.hulksa.player.data.OperationsFeatureFlags
import sa.hulksa.player.data.OperationsFetchResult
import sa.hulksa.player.data.OperationsServiceConfig
import sa.hulksa.player.data.OperationsStore
import sa.hulksa.player.data.OperationsUpdateConfig
import sa.hulksa.player.data.PortalResolver
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.data.VerifiedKidsCatalogSnapshot
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
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.SeriesBundle
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.security.CredentialVault
import sa.hulksa.player.ui.NotificationProfileReadinessEffect
import sa.hulksa.player.ui.screens.NewEpisodeAlertOverlay

@RunWith(AndroidJUnit4::class)
class EpisodeNotificationPipelineInstrumentedTest {
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
    fun activeFailedScanRetainsStrongestPendingTriggerAndPresentsOneEvent() = runBlocking {
        val accountId = stableAccountId(PORTAL, USERNAME)
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        val profile = requireNotNull(profileStore.load(metadata.accountId))
            .let { profileStore.activeProfile() }
        val notificationStore = LocalEpisodeNotificationStore(context)
        val baselineCheckedAtEpochMs = System.currentTimeMillis() - SIX_MINUTES_MS
        assertTrue(baselineCheckedAtEpochMs > 0L)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = profile.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = baselineCheckedAtEpochMs,
                expectedAccountId = metadata.accountId,
            ),
        )

        CredentialVault(context).save(session().credentials)
        val firstResult = CompletableDeferred<Result<SeriesBundle>>()
        val client = ControlledXtreamClient(firstResult)
        val repository = HulkRepository(
            context = context,
            vault = CredentialVault(context),
            accountSessionStore = accountStore,
            portalResolver = object : PortalResolver() {
                override suspend fun resolve(accessCode: String): PortalConfig = session().portal
            },
            client = client,
        )
        val model = HulkViewModel(
            application = context as Application,
            repository = repository,
            operationsClient = object : OperationsClient() {
                override suspend fun fetch(): OperationsFetchResult = OperationsFetchResult.Failure
            },
        )
        viewModel = model

        withTimeout(10_000) { model.state.first { it.account != null } }
        withTimeout(10_000) { client.firstCallStarted.await() }

        model.refreshProfileLibrary(metadata.accountId, profile)
        withTimeout(10_000) { model.state.first { it.isProfileLibraryReady } }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // LIBRARY_REFRESH (5 min) is pending; APP_RESUME (10 min) is weaker and must not
        // overwrite it. The six-minute-old baseline is eligible only for the 5-minute trigger.
        model.onAppResumed()
        firstResult.complete(Result.failure(IOException("synthetic series fetch failure")))

        // Reproduced defect: the active APP_START scan failed, and the queued trigger could be
        // overwritten by the weaker APP_RESUME request, rejecting the six-minute-old baseline.
        val detected = withTimeout(5_000) {
            model.state.first { state ->
                state.localNotifications.any { item ->
                    item is sa.hulksa.player.data.LocalNotificationItem.Episode &&
                        item.notification.seasonNumber == 1 &&
                        item.notification.episodeNumber == 2
                }
            }
        }
        assertEquals(1, detected.unreadNotificationCount)

        composeRule.setContent {
            val state by model.state.collectAsState()
            NotificationProfileReadinessEffect(ready = state.isProfileLibraryReady) { ready ->
                model.setNotificationUiReady(ready)
            }
            state.notificationPopup?.let { popup ->
                NewEpisodeAlertOverlay(
                    popup = popup,
                    isTv = true,
                    onPresented = model::confirmNotificationPopupPresented,
                    onPrimary = {},
                    onLater = model::dismissNotificationPopup,
                )
            }
        }

        composeRule.waitUntil(10_000) { model.state.value.notificationPopup != null }
        composeRule.onNodeWithText("حلقة جديدة نزلت", substring = true).assertExists()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        withTimeout(10_000) {
            while (
                notificationStore.snapshot(profile.id).notifications.none {
                    it.episodeNumber == 2 && it.popupShown
                }
            ) {
                kotlinx.coroutines.delay(50)
            }
        }
        assertTrue(
            notificationStore.snapshot(profile.id).notifications.single {
                it.episodeNumber == 2
            }.popupShown,
        )

        // A repeated trigger for the same provider response must not add a duplicate event.
        notificationStore.replaceBaseline(
            profileId = profile.id,
            seriesId = series().id,
            episodes = listOf(episodeA(), episodeB()),
            checkedAtEpochMs = 1L,
            expectedAccountId = metadata.accountId,
        )
        val callsBeforeRepeat = client.calls.get()
        val checkedBeforeRepeat = notificationStore.snapshot(profile.id)
            .subscriptions
            .single()
            .lastCheckedAtEpochMs
        model.onAppResumed()
        withTimeout(5_000) {
            while (client.calls.get() <= callsBeforeRepeat) {
                kotlinx.coroutines.delay(50)
            }
        }
        withTimeout(5_000) {
            while (
                notificationStore.snapshot(profile.id)
                    .subscriptions
                    .single()
                    .lastCheckedAtEpochMs <= checkedBeforeRepeat
            ) {
                kotlinx.coroutines.delay(50)
            }
        }
        assertEquals(
            1,
            notificationStore.snapshot(profile.id).notifications.count {
                it.episodeNumber == 2
            },
        )
    }

    @Test
    fun profileChangeDuringInFlightScanDoesNotPublishEventUnderNewProfile() = runBlocking {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        val profileA = requireNotNull(profileStore.load(metadata.accountId))
            .let { profileStore.activeProfile() }
        val profileB: UserProfile = requireNotNull(
            profileStore.createProfileForAccount(
                expectedAccountId = metadata.accountId,
                displayName = "Synthetic Second Profile",
                kind = ProfileKind.STANDARD,
            ),
        ).value
        val notificationStore = LocalEpisodeNotificationStore(context)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = profileA.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = 0L,
                expectedAccountId = metadata.accountId,
            ),
        )

        val firstResult = CompletableDeferred<Result<SeriesBundle>>()
        val client = LateCompletionClient(firstResult)
        val model = modelFor(accountStore, client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }
        withTimeout(10_000) { client.firstCallStarted.await() }

        requireNotNull(
            profileStore.setActiveProfileForAccount(metadata.accountId, profileB.id),
        )
        firstResult.complete(
            Result.success(
                SeriesBundle(
                    details = ContentDetails(),
                    episodes = listOf(episodeA(), episodeB()),
                ),
            ),
        )
        withTimeout(5_000) {
            while (
                notificationStore.snapshot(profileA.id).notifications.none {
                    it.episodeNumber == 2
                }
            ) {
                kotlinx.coroutines.delay(50)
            }
        }

        model.onProfileChanged()
        model.setNotificationUiReady(true)

        assertTrue(
            model.state.value.localNotifications.none { item ->
                item is LocalNotificationItem.Episode &&
                    item.notification.episodeNumber == 2
            },
        )
        assertNull(model.state.value.notificationPopup)
    }

    @Test
    fun kidsReadinessDoesNotDiscardStrongerPendingManualScan() = runBlocking {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        requireNotNull(profileStore.load(metadata.accountId))
        val kidsProfile: UserProfile = requireNotNull(
            profileStore.createProfileForAccount(
                expectedAccountId = metadata.accountId,
                displayName = "Synthetic Kids",
                kind = ProfileKind.KIDS,
            ),
        ).value
        requireNotNull(
            profileStore.setActiveProfileForAccount(metadata.accountId, kidsProfile.id),
        )
        val notificationStore = LocalEpisodeNotificationStore(context)
        val twoMinutesAgo = System.currentTimeMillis() - TWO_MINUTES_MS
        assertTrue(twoMinutesAgo > 0L)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = kidsProfile.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = twoMinutesAgo,
                expectedAccountId = metadata.accountId,
            ),
        )

        val client = CountingXtreamClient(episodes = listOf(episodeA(), episodeB()))
        val model = modelFor(accountStore, client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }
        assertTrue(
            KidsContentFilterStore(context).replace(
                VerifiedKidsCatalogSnapshot(
                    catalogs = mapOf(
                        ContentType.SERIES to Catalog(
                            categories = emptyList(),
                            items = listOf(series()),
                        ),
                    ),
                    blockedTypes = emptyMap(),
                ),
            ),
        )
        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })

        // Queue MANUAL_REFRESH (1 min) while Kids readiness blocks scanning.
        model.refresh()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(0, client.calls.get())

        // Kids readiness now calls PROFILE_SWITCH (5 min) while MANUAL_REFRESH is pending.
        // The runnable path must retain the stronger 1-minute request; the 2-minute-old
        // baseline is eligible only for that request.
        model.setNotificationUiReady(true)

        val detected = withTimeout(10_000) {
            model.state.first { state ->
                state.localNotifications.any { item ->
                    item is LocalNotificationItem.Episode &&
                        item.notification.episodeNumber == 2
                }
            }
        }
        assertEquals(1, detected.unreadNotificationCount)
        assertEquals(1, client.calls.get())
    }

    @Test
    fun operationsFeatureReadyAfterEarlierTriggerDrainsPendingScan() = runBlocking {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        val profile = requireNotNull(profileStore.load(metadata.accountId))
            .let { profileStore.activeProfile() }
        val notificationStore = LocalEpisodeNotificationStore(context)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = profile.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = 0L,
                expectedAccountId = metadata.accountId,
            ),
        )
        val operationsStore = OperationsStore(context)
        assertTrue(operationsStore.saveConfig(operationsJson(enabled = false), 1L))

        val operations = SequencedOperationsClient()
        val client = CountingXtreamClient(episodes = listOf(episodeA(), episodeB()))
        val model = modelFor(accountStore, client, operations)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }
        withTimeout(10_000) {
            model.state.first { !it.operations.features.episodeNotificationsEnabled }
        }
        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })

        operations.response = OperationsFetchResult.Success(
            config = OperationsConfig(
                schemaVersion = 1,
                generatedAtEpochSeconds = 2L,
                service = OperationsServiceConfig(),
                update = OperationsUpdateConfig(
                    latestVersionCode = 68,
                    latestVersionName = "1.0.0",
                    minimumSupportedVersionCode = 68,
                ),
                announcements = emptyList(),
                features = OperationsFeatureFlags(episodeNotificationsEnabled = true),
            ),
            rawJson = operationsJson(enabled = true),
            fetchedAtEpochMs = 2L,
        )
        model.retryOperations()

        val detected = withTimeout(10_000) {
            model.state.first { state ->
                state.localNotifications.any { item ->
                    item is LocalNotificationItem.Episode &&
                        item.notification.episodeNumber == 2
                }
            }
        }
        assertEquals(1, detected.unreadNotificationCount)
        assertEquals(1, client.calls.get())
    }

    @Test
    fun masterOffPreventsAnySeriesFetch() = runBlocking {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        val profile = requireNotNull(profileStore.load(metadata.accountId))
            .let { profileStore.activeProfile() }
        val notificationStore = LocalEpisodeNotificationStore(context)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = profile.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = 0L,
                expectedAccountId = metadata.accountId,
            ),
        )
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.setMasterEnabled(
                profileId = profile.id,
                enabled = false,
                expectedAccountId = metadata.accountId,
            ),
        )

        val client = CountingXtreamClient()
        val model = modelFor(accountStore, client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }

        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })
        model.onAppResumed()
        model.setNotificationUiReady(true)
        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })
        assertEquals(0, client.calls.get())
    }

    @Test
    fun unverifiedKidsPreventsAnySeriesFetch() = runBlocking {
        val accountStore = AccountSessionStore(context)
        val metadata = accountStore.recordAuthenticated(session())
        val profileStore = ProfileStore(context)
        requireNotNull(profileStore.load(metadata.accountId))
        val kidsProfile: UserProfile = requireNotNull(
            profileStore.createProfileForAccount(
                expectedAccountId = metadata.accountId,
                displayName = "Synthetic Kids",
                kind = ProfileKind.KIDS,
            ),
        ).value
        requireNotNull(
            profileStore.setActiveProfileForAccount(metadata.accountId, kidsProfile.id),
        )
        val notificationStore = LocalEpisodeNotificationStore(context)
        assertEquals(
            EpisodeNotificationStoreResult.SUCCESS,
            notificationStore.enableSubscription(
                profileId = kidsProfile.id,
                series = series(),
                episodes = listOf(episodeA()),
                nowEpochMs = 0L,
                expectedAccountId = metadata.accountId,
            ),
        )

        val client = CountingXtreamClient()
        val model = modelFor(accountStore, client)
        viewModel = model
        withTimeout(10_000) { model.state.first { it.account != null } }

        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })
        model.setNotificationUiReady(true)
        assertNull(withTimeoutOrNull(1_000) { client.firstCallStarted.await() })
        assertEquals(0, client.calls.get())
    }

    private fun modelFor(
        accountStore: AccountSessionStore,
        client: XtreamClient,
        operationsClient: OperationsClient? = null,
    ): HulkViewModel {
        CredentialVault(context).save(session().credentials)
        return HulkViewModel(
            application = context as Application,
            repository = HulkRepository(
                context = context,
                vault = CredentialVault(context),
                accountSessionStore = accountStore,
                portalResolver = object : PortalResolver() {
                    override suspend fun resolve(accessCode: String): PortalConfig = session().portal
                },
                client = client,
            ),
            operationsClient = operationsClient ?: object : OperationsClient() {
                override suspend fun fetch(): OperationsFetchResult = OperationsFetchResult.Failure
            },
        )
    }

    private inner class SequencedOperationsClient : OperationsClient() {
        var response: OperationsFetchResult = OperationsFetchResult.Failure
        override suspend fun fetch(): OperationsFetchResult = response
    }

    private fun operationsJson(enabled: Boolean): String = """
        {
          "schemaVersion": 1,
          "generatedAt": 1,
          "service": {"status": "OPERATIONAL", "message": null, "startsAt": null, "estimatedEndAt": null},
          "update": {
            "latestVersionCode": 68,
            "latestVersionName": "1.0.0",
            "minimumSupportedVersionCode": 68,
            "updateType": "OPTIONAL",
            "apkUrl": null,
            "apkSha256": null,
            "releaseNotes": ""
          },
          "announcements": [],
          "features": {"episode_notifications_enabled": $enabled}
        }
    """.trimIndent()

    private inner class CountingXtreamClient(
        private val episodes: List<Episode> = listOf(episodeA()),
    ) : XtreamClient() {
        val firstCallStarted = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)

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
            calls.incrementAndGet()
            firstCallStarted.complete(Unit)
            return SeriesBundle(
                details = ContentDetails(),
                episodes = episodes,
            )
        }
    }

    private inner class LateCompletionClient(
        private val firstResult: CompletableDeferred<Result<SeriesBundle>>,
    ) : XtreamClient() {
        val firstCallStarted = CompletableDeferred<Unit>()

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
            firstCallStarted.complete(Unit)
            return firstResult.await().getOrThrow()
        }
    }

    private inner class ControlledXtreamClient(
        private val firstResult: CompletableDeferred<Result<SeriesBundle>>,
    ) : XtreamClient() {
        val firstCallStarted = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)

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
            val call = calls.incrementAndGet()
            if (call == 1) {
                firstCallStarted.complete(Unit)
                return firstResult.await().getOrThrow()
            }
            return SeriesBundle(
                details = ContentDetails(),
                episodes = listOf(episodeA(), episodeB()),
            )
        }
    }

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
        name = "Synthetic Series",
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
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
        listOf(
            "hulk_profiles_v1",
            "hulk_user_library",
            "hulk_local_episode_notifications_v1",
        ).forEach { name ->
            context.getSharedPreferences(
                accountScopedPreferencesName(name, accountId),
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    private companion object {
        const val PORTAL = "https://synthetic.invalid"
        const val USERNAME = "synthetic-user"
        const val PASSWORD = "synthetic-password"
        const val ACCESS_CODE = "synthetic-code"
        const val SERIES_ID = 44
        const val TWO_MINUTES_MS = 2L * 60L * 1_000L
        const val SIX_MINUTES_MS = 6L * 60L * 1_000L
    }
}

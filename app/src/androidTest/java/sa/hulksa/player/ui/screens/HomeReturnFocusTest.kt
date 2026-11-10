package sa.hulksa.player.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.HomeMessagePresentationState
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Real shell-level focus ownership: leave Home, return through the TV rail, and verify the exact
 * remembered card wins over both the generic content handoff and an obsolete list index.
 */
class HomeReturnFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun returningHomeRestoresContinueWatchingByKeyAfterReorder() {
        val history = listOf(
            history(1, "Remembered Continue", updatedAt = 300L),
            history(2, "Other Continue", updatedAt = 200L),
            history(3, "Third Continue", updatedAt = 100L),
        )
        val navigationMemory = NavigationMemoryStore().apply {
            // Deliberately stale index: the surviving key must win against it.
            save(MainDestination.HOME, history.first().key, 2, "continue", 1)
        }
        val state = baseState(history = history)
        mountShellAndReturnHome(state, navigationMemory, "Remembered Continue")
        composeRule.onNode(hasText("Remembered Continue") and isFocused()).assertExists()
    }

    @Test
    fun removedContinueTargetFallsBackToNearbySurvivingCard() {
        val history = listOf(
            history(1, "First Continue", updatedAt = 300L),
            history(2, "Surviving Continue", updatedAt = 200L),
            history(3, "Third Continue", updatedAt = 100L),
        )
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, "movie:99", 1, "continue", 1)
        }
        val state = baseState(history = history)
        mountShellAndReturnHome(state, navigationMemory, "Surviving Continue")
        composeRule.onNode(hasText("Surviving Continue") and isFocused()).assertExists()
    }

    @Test
    fun returningWhileHomeDataLoadsRestoresTargetOnceItArrives() {
        val history = listOf(
            history(1, "Loading Continue", updatedAt = 300L),
            history(2, "Loaded Continue", updatedAt = 200L),
        )
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, history.last().key, 0, "continue", 1)
        }
        val loading = baseState(history = emptyList())
        val loaded = baseState(history = history)
        var observedIntermediate = false
        mountShellAndReturnHome(
            state = loading,
            navigationMemory = navigationMemory,
            expectedText = "Loaded Continue",
            loadedState = loaded,
            onIntermediate = {
                composeRule.waitForIdle()
                composeRule.mainClock.advanceTimeBy(500L)
                assertTrue(
                    composeRule.onAllNodesWithText("Loaded Continue")
                        .fetchSemanticsNodes().isEmpty(),
                )
                assertTrue(
                    composeRule.onAllNodes(hasText("Loaded Continue") and isFocused())
                        .fetchSemanticsNodes().isEmpty(),
                )
                observedIntermediate = true
            },
        )
        assertTrue(observedIntermediate)
        composeRule.onNode(hasText("Loaded Continue") and isFocused()).assertExists()
    }

    @Test
    fun vanishedRememberedRowFallsBackToHero() {
        val movies = Catalog(
            categories = emptyList(),
            items = listOf(movie(1, "Current Hero", addedAt = 900L, rating = "9.0")),
        )
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, "movie:99", 0, "last-live", 8)
        }
        val state = baseState(
            history = emptyList(),
            catalogs = mapOf(ContentType.MOVIE to movies),
        )
        mountShellAndReturnHome(state, navigationMemory, "شاهد الان")
        composeRule.onNode(hasText("شاهد الان") and isFocused()).assertExists()
    }

    @Test
    fun returningHomeRestoresStandardPosterByKeyAfterReorder() {
        val movies = Catalog(
            categories = emptyList(),
            items = listOf(
                movie(1, "Current Hero", addedAt = 900L, rating = "9.0"),
                movie(2, "First Row", addedAt = 800L, rating = "8.5"),
                movie(3, "Second Row", addedAt = 700L, rating = "8.0"),
                movie(4, "Remembered Poster", addedAt = 600L, rating = "7.5"),
                movie(5, "Last Row", addedAt = 500L, rating = "7.0"),
            ),
        )
        val targetKey = "MOVIE:4"
        val navigationMemory = NavigationMemoryStore().apply {
            // Deliberately stale index: the surviving key must win against it.
            save(MainDestination.HOME, targetKey, 0, "recent-movies", 6)
        }
        val state = baseState(
            history = emptyList(),
            catalogs = mapOf(ContentType.MOVIE to movies),
        )
        mountShellAndReturnHome(state, navigationMemory, "Remembered Poster")
        composeRule.onNode(hasText("Remembered Poster") and isFocused()).assertExists()
    }

    @Test
    fun realHomeRoundTripRestoresSameContinueCardFromEverySection() {
        val history = listOf(
            history(1, "Round Trip Target", updatedAt = 300L),
            history(2, "Round Trip Other", updatedAt = 200L),
        )
        val state = baseState(history = history)
        val navigationMemory = NavigationMemoryStore()
        mountShellHost(
            startDestination = MainDestination.HOME,
            state = state,
            navigationMemory = navigationMemory,
        ) {}

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Round Trip Target")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Round Trip Target")).requestFocus()
        composeRule.waitUntil(10_000) {
            navigationMemory.position(MainDestination.HOME).itemKey == "movie:1"
        }

        listOf(
            MainDestination.LIVE to "البث المباشر",
            MainDestination.MOVIES to "الافلام",
            MainDestination.SERIES to "المسلسلات",
        ).forEach { (_, railLabel) ->
            composeRule.onAllNodesWithContentDescription(railLabel).onFirst().performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(hasText("Round Trip Target") and isFocused())
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun realRoundTripRestoresHorizontallyOffscreenContinueCard() {
        val history = (1..12).map { index ->
            history(index, "Continue $index", updatedAt = 400L - index)
        }
        val state = baseState(history = history)
        val navigationMemory = NavigationMemoryStore()
        mountShellHost(
            startDestination = MainDestination.HOME,
            state = state,
            navigationMemory = navigationMemory,
        ) {}

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Continue 1")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home-row-continue").performScrollToIndex(11)
        composeRule.onNode(hasText("Continue 12")).requestFocus()
        composeRule.waitUntil(10_000) {
            navigationMemory.position(MainDestination.HOME).itemKey == "movie:12"
        }

        composeRule.onAllNodesWithContentDescription("البث المباشر").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Continue 12") and isFocused())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("Continue 12")).assertIsDisplayed()
    }

    @Test
    fun railFocusDuringStagedReturnIsNotPulledBackIntoHome() {
        val history = listOf(history(1, "Rail Continue", updatedAt = 300L))
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, history.single().key, 0, "continue", 1)
        }
        val loading = baseState(history = emptyList())
        val loaded = baseState(history = history)
        mountShellAndReturnHome(
            state = loading,
            navigationMemory = navigationMemory,
            expectedText = "Rail Continue",
            loadedState = loaded,
        ) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().requestFocus()
            assertTrue(
                composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Rail Continue").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "rail focus must not be pulled back into Home",
            composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasText("Rail Continue") and isFocused())
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun vanishedRowFallbackRevealsOffscreenHeroBeforeFocus() {
        val requester = FocusRequester()
        val finished = AtomicBoolean(false)
        lateinit var listState: LazyListState
        composeRule.setContent {
            HulkTheme {
                val state = rememberLazyListState(initialFirstVisibleItemIndex = 6)
                listState = state
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().testTag("hero-fallback-list"),
                ) {
                    item(key = "hero") {
                        Box(
                            Modifier
                                .size(80.dp)
                                .testTag("hero-fallback-target")
                                .focusRequester(requester)
                                .focusable(),
                        )
                    }
                    items(10) { index ->
                        Box(Modifier.size(80.dp).testTag("hero-fallback-row-$index"))
                    }
                }
                LaunchedEffect(Unit) {
                    revealAndFocusHomeHero(
                        listState = state,
                        isFocusOwnershipLost = { false },
                        requestFocus = { requester.requestFocus() },
                    )
                    finished.set(true)
                }
            }
        }
        composeRule.waitUntil(10_000) { finished.get() }
        assertEquals(0, listState.firstVisibleItemIndex)
        composeRule.onNodeWithTag("hero-fallback-target").assertIsFocused()
    }

    @Test
    fun productionRailFocusAfterRowRestorationStartsIsNotOverridden() {
        val history = (1..12).map { index ->
            history(index, "Production Continue $index", updatedAt = 500L - index)
        }
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, history.last().key, 0, "continue", 1)
        }
        val state = baseState(history = history)
        composeRule.mainClock.autoAdvance = false
        mountProductionHome(state, navigationMemory)
        var targetRevealed = false
        for (frame in 0 until 8) {
            composeRule.mainClock.advanceTimeByFrame()
            if (
                composeRule.onAllNodesWithText("Production Continue 12")
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                targetRevealed = true
                break
            }
        }
        assertTrue("restoration must reveal the offscreen target before the rail move", targetRevealed)
        assertTrue(
            "restoration must not have completed before the rail move",
            composeRule.onAllNodes(hasText("Production Continue 12") and isFocused())
                .fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().requestFocus()
        assertTrue(
            composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                .fetchSemanticsNodes().isNotEmpty(),
        )
        repeat(8) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(
            "rail focus must not be overridden by an already-started row restoration",
            composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasText("Production Continue 12") and isFocused())
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun productionRailFocusAfterRememberedHeroStartsIsNotOverridden() {
        val movies = Catalog(
            categories = emptyList(),
            items = listOf(movie(1, "Production Hero", addedAt = 900L, rating = "9.0")),
        )
        val navigationMemory = NavigationMemoryStore().apply {
            save(MainDestination.HOME, "MOVIE:1", 0, "hero", 0)
        }
        val state = baseState(
            history = emptyList(),
            catalogs = mapOf(ContentType.MOVIE to movies),
        )
        composeRule.mainClock.autoAdvance = false
        mountProductionHome(state, navigationMemory)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().requestFocus()
        assertTrue(
            composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                .fetchSemanticsNodes().isNotEmpty(),
        )
        repeat(8) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(
            "rail focus must not be overridden by the independent hero restoration",
            composeRule.onAllNodes(hasContentDescription("الرئيسية") and isFocused())
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodes(hasText("شاهد الان") and isFocused())
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun mountProductionHome(
        state: HulkUiState,
        navigationMemory: NavigationMemoryStore,
    ) {
        composeRule.setContent {
            HulkTheme {
                CompositionLocalProvider(
                    LocalAdaptiveUi provides AdaptiveUiState(
                        deviceClass = HulkDeviceClass.TELEVISION,
                        windowWidthClass = HulkWindowWidthClass.EXPANDED,
                        navigationType = HulkNavigationType.RAIL,
                        inputMode = HulkInputMode.REMOTE,
                        screenWidthDp = 1920,
                        screenHeightDp = 1080,
                    ),
                ) {
                    MainShellScreen(
                        state = state,
                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = { false },
                        onSelectDestination = {},
                        onSelectCategory = {},
                        onSearch = {},
                        onOpen = {},
                        onOpenHistory = {},
                        onRemoveHistoryEntry = { _, _ -> },
                        onToggleFavoriteWithResult = { _, _ -> },
                        onRefresh = {},
                        onOpenNotifications = {},
                        homeNotificationReturnOwner = remember { HomeNotificationReturnOwner() },
                        onClearHistory = {},
                        onPlayDownload = {},
                        onDeleteDownload = {},
                        onDownloadPrimaryAction = { _, _ -> },
                        onToggleWifiOnly = {},
                        onToggleDownloadSchedule = {},
                        onCycleConcurrentDownloads = {},
                        onToggleEpisodeNotificationMaster = {},
                        onCycleDownloadPriority = {},
                        onRefreshAccount = {},
                        onRunDiagnostics = {},
                        onLogout = {},
                        homeMessagePresentation = HomeMessagePresentationState(),
                        onOpenAnnouncementDetail = {},
                        onOpenUpdateDetail = {},
                        onActionNotice = { _, _ -> },
                    )
                }
            }
        }
    }

    private fun mountShellHost(
        startDestination: MainDestination,
        state: HulkUiState,
        navigationMemory: NavigationMemoryStore,
        onReady: (setState: (HulkUiState) -> Unit) -> Unit,
    ) {
        var destination by mutableStateOf(startDestination)
        var currentState by mutableStateOf(state)
        composeRule.setContent {
            HulkTheme {
                CompositionLocalProvider(
                    LocalAdaptiveUi provides AdaptiveUiState(
                        deviceClass = HulkDeviceClass.TELEVISION,
                        windowWidthClass = HulkWindowWidthClass.EXPANDED,
                        navigationType = HulkNavigationType.RAIL,
                        inputMode = HulkInputMode.REMOTE,
                        screenWidthDp = 1920,
                        screenHeightDp = 1080,
                    ),
                ) {
                    MainShellScreen(
                        state = currentState.copy(destination = destination),
                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = { false },
                        onSelectDestination = { destination = it },
                        onSelectCategory = {},
                        onSearch = {},
                        onOpen = {},
                        onOpenHistory = {},
                        onRemoveHistoryEntry = { _, _ -> },
                        onToggleFavoriteWithResult = { _, _ -> },
                        onRefresh = {},
                        onOpenNotifications = {},
                        homeNotificationReturnOwner = remember { HomeNotificationReturnOwner() },
                        onClearHistory = {},
                        onPlayDownload = {},
                        onDeleteDownload = {},
                        onDownloadPrimaryAction = { _, _ -> },
                        onToggleWifiOnly = {},
                        onToggleDownloadSchedule = {},
                        onCycleConcurrentDownloads = {},
                        onToggleEpisodeNotificationMaster = {},
                        onCycleDownloadPriority = {},
                        onRefreshAccount = {},
                        onRunDiagnostics = {},
                        onLogout = {},
                        homeMessagePresentation = HomeMessagePresentationState(),
                        onOpenAnnouncementDetail = {},
                        onOpenUpdateDetail = {},
                        onActionNotice = { _, _ -> },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        onReady { updated -> composeRule.runOnUiThread { currentState = updated } }
    }

    private fun mountShellAndReturnHome(
        state: HulkUiState,
        navigationMemory: NavigationMemoryStore,
        expectedText: String,
        loadedState: HulkUiState = state,
        onIntermediate: (() -> Unit)? = null,
    ) {
        mountShellHost(
            startDestination = MainDestination.LIVE,
            state = state,
            navigationMemory = navigationMemory,
        ) { setState ->
            composeRule.onAllNodesWithContentDescription("الرئيسية").onFirst().performClick()
            onIntermediate?.invoke()
            if (loadedState != state) setState(loadedState)
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun baseState(
        history: List<HistoryEntry>,
        catalogs: Map<ContentType, Catalog> = emptyMap(),
    ): HulkUiState = HulkUiState(
        screen = HulkScreen.MAIN,
        isStarting = false,
        isProfileLibraryReady = true,
        destination = MainDestination.HOME,
        catalogs = catalogs,
        history = history,
    )

    private fun history(
        id: Int,
        title: String,
        updatedAt: Long,
    ): HistoryEntry = HistoryEntry(
        key = "movie:$id",
        title = title,
        posterUrl = null,
        streamKind = "movie",
        streamId = id,
        extension = "mp4",
        isLive = false,
        positionMs = 10L,
        durationMs = 100L,
        updatedAtEpochMs = updatedAt,
    )

    private fun movie(
        id: Int,
        name: String,
        addedAt: Long,
        rating: String,
    ): ContentItem = ContentItem(
        id = id,
        name = name,
        categoryId = "all",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = rating,
        year = "2026",
        containerExtension = "mp4",
        addedAtEpochSeconds = addedAt,
    )
}

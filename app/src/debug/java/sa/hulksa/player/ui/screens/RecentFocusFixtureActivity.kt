package sa.hulksa.player.ui.screens

import android.os.Bundle
import android.view.KeyEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.adaptive.AdaptiveUiState
import sa.hulksa.player.ui.adaptive.HulkDeviceClass
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkWindowWidthClass
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Debug-only rendered fixture for the production Movies/Series Recent screen. It uses the real
 * PosterCatalogScreen, real BoxedHistoryGrid, real Refresh toolbar and real category strip; only
 * the history mutation is in-memory and isolated in the disposable `.dev` package.
 */
class RecentFocusFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario").orEmpty().ifBlank { "middle" }
        val isTv = intent.getBooleanExtra("isTv", true)
        val media = intent.getStringExtra("media").orEmpty().ifBlank { "series" }
        setContent {
            HulkTheme {
                CompositionLocalProvider(
                    LocalAdaptiveUi provides AdaptiveUiState(
                        deviceClass = if (isTv) HulkDeviceClass.TELEVISION else HulkDeviceClass.MOBILE,
                        windowWidthClass = if (isTv) HulkWindowWidthClass.EXPANDED else HulkWindowWidthClass.COMPACT,
                        navigationType = if (isTv) HulkNavigationType.RAIL else HulkNavigationType.TOP_BAR,
                        inputMode = if (isTv) HulkInputMode.REMOTE else HulkInputMode.TOUCH,
                        screenWidthDp = if (isTv) 960 else 412,
                        screenHeightDp = if (isTv) 540 else 915,
                    ),
                ) {
                    RecentFocusFixture(scenario, isTv, media)
                }
            }
        }
    }
}

@Composable
private fun RecentFocusFixture(scenario: String, isTv: Boolean, media: String) {
    val scope = rememberCoroutineScope()
    val entryMode = scenario.startsWith("entry-")
    val mobileSearch = scenario == "mobile-search"
    val baseScenario = scenario.removePrefix("entry-")
    val type = if (media == "movie") ContentType.MOVIE else ContentType.SERIES
    val destination = if (type == ContentType.MOVIE) MainDestination.MOVIES else MainDestination.SERIES
    val series = type == ContentType.SERIES
    val entries = remember(scenario, media) {
        fixtureHistory(if (entryMode) baseScenario else scenario, media)
    }
    val focusIndex = remember(scenario, entries) {
        when {
            mobileSearch -> 0
            (if (entryMode) baseScenario else scenario) == "first" -> 0
            (if (entryMode) baseScenario else scenario) == "last" -> entries.lastIndex
            (if (entryMode) baseScenario else scenario) == "reentry" -> 1
            (if (entryMode) baseScenario else scenario) == "failure" -> 1
            (if (entryMode) baseScenario else scenario) == "reorder" -> 1
            else -> (entries.size / 2).coerceAtLeast(0)
        }
    }
    val navigationMemory = remember(scenario, media) {
        NavigationMemoryStore().also { memory ->
            if (entryMode) {
                memory.save(destination, "${if (series) "SERIES" else "MOVIE"}:900", 0)
            } else {
                entries.getOrNull(focusIndex)?.let { entry ->
                    memory.save(destination, entry.key, focusIndex)
                }
            }
        }
    }
    var state by remember(scenario, media) {
        mutableStateOf(
            HulkUiState(
                screen = HulkScreen.MAIN,
                isStarting = false,
                isLoading = false,
                destination = destination,
                selectedType = type,
                selectedCategoryId = if (entryMode) null else CONTINUE_CATEGORY_ID,
                searchQuery = if (mobileSearch) "Alpha" else "",
                history = entries,
                isProfileLibraryReady = true,
                catalogs = mapOf(
                    type to Catalog(
                        categories = emptyList(),
                        items = if (entryMode) fixtureCatalog() else emptyList(),
                    ),
                ),
            ),
        )
    }
    var noticeCount by remember { mutableStateOf(0) }
    val favoriteSnapshot = remember { CatalogFavoriteSnapshot(emptySet()) }
    val guard = remember { HomeRemovalInputGuard() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    guard.onSelectKey(event.type)
                } else {
                    false
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRestorer()
                .focusGroup(),
        ) {
            PosterCatalogScreen(
            title = if (series) "المسلسلات" else "الافلام",
            type = type,
            destination = destination,
            state = state,
            isTv = isTv,
            navigationMemory = navigationMemory,
            favoriteSnapshot = favoriteSnapshot,
            isFavorite = { false },
            onSelectCategory = { categoryId ->
                Log.i(FIXTURE_TAG, "selectCategory=$categoryId")
                state = state.copy(selectedCategoryId = categoryId)
            },
            onSearch = { query ->
                Log.i(FIXTURE_TAG, "searchQuery=$query scenario=$scenario")
                state = state.copy(searchQuery = query)
            },
            onOpen = {},
            onOpenHistory = {},
            onRemoveHistoryEntry = { key, result ->
                val removed = state.history.firstOrNull { it.key == key }
                Log.i(FIXTURE_TAG, "removeStart key=$key title=${removed?.title} scenario=$scenario")
                scope.launch {
                    delay(if (scenario == "slow") 1_500 else 180)
                    if (scenario == "failure") {
                        Log.i(FIXTURE_TAG, "removeResult key=$key success=false")
                        result(false)
                        return@launch
                    }
                    val remaining = state.history
                        .filterNot { it.key == key }
                        .let { if (scenario == "reorder") it.reversed() else it }
                    state = state.copy(history = remaining)
                    Log.i(
                        FIXTURE_TAG,
                        "removeResult key=$key success=true remaining=${remaining.map { it.key }}",
                    )
                    result(true)
                    if (mobileSearch) {
                        // Fixture driver for the reviewed sequence: the empty search result must
                        // be presented and the transaction completed before the query clears and
                        // reveals the second card.
                        scope.launch {
                            delay(1_200)
                            state = state.copy(searchQuery = "")
                            Log.i(FIXTURE_TAG, "clearSearch scenario=$scenario")
                        }
                    }
                }
            },
            onRecentRemovalStarted = {
                guard.begin()
                Log.i(FIXTURE_TAG, "remoteRemovalStarted scenario=$scenario guard=$guard")
            },
            onToggleFavorite = {},
            onRefresh = { Log.i(FIXTURE_TAG, "refreshActivated scenario=$scenario") },
            onActionNotice = { message, _ ->
                noticeCount += 1
                Log.i(FIXTURE_TAG, "notice count=$noticeCount text=$message scenario=$scenario")
            },
            )
        }
    }
}

private const val FIXTURE_TAG = "RecentFocusFixture"

private fun fixtureCatalog(): List<ContentItem> = (0 until 4).map { index ->
    ContentItem(
        id = 900 + index,
        name = "كتالوج ${index + 1}",
        categoryId = "",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mkv",
    )
}

private fun fixtureHistory(scenario: String, media: String): List<HistoryEntry> {
    val names = when (scenario) {
        "mobile-search" -> listOf("Alpha", "Beta")
        "only" -> listOf("الحلقة أ")
        "many" -> (1..12).map { "الحلقة $it" }
        else -> listOf(
            "الحلقة أ",
            "الحلقة ب",
            "الحلقة ج",
            "الحلقة د",
        )
    }
    val series = media != "movie"
    val prefix = if (series) "SERIES" else "MOVIE"
    return names.mapIndexed { index, title ->
        HistoryEntry(
            key = "$prefix:${100 + index}",
            title = title,
            posterUrl = null,
            streamKind = if (series) "series" else "movie",
            streamId = 1000 + index,
            extension = "mkv",
            isLive = false,
            positionMs = 1_000L + index * 1_000L,
            durationMs = 10_000L,
            updatedAtEpochMs = 10_000L - index,
            seriesTitle = if (series) title else null,
            season = if (series) 1 else null,
            episodeNumber = if (series) index + 1 else null,
            episodeTitle = if (series) title else null,
            parentContentId = if (series) 500 + index else null,
        )
    }
}

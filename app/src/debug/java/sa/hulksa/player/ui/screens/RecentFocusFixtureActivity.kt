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
        setContent {
            HulkTheme {
                CompositionLocalProvider(
                    LocalAdaptiveUi provides AdaptiveUiState(
                        deviceClass = HulkDeviceClass.TELEVISION,
                        windowWidthClass = HulkWindowWidthClass.EXPANDED,
                        navigationType = HulkNavigationType.RAIL,
                        inputMode = HulkInputMode.REMOTE,
                        screenWidthDp = 960,
                        screenHeightDp = 540,
                    ),
                ) {
                    RecentFocusFixture(scenario)
                }
            }
        }
    }
}

@Composable
private fun RecentFocusFixture(scenario: String) {
    val scope = rememberCoroutineScope()
    val entryMode = scenario.startsWith("entry-")
    val baseScenario = scenario.removePrefix("entry-")
    val entries = remember(scenario) { fixtureHistory(if (entryMode) baseScenario else scenario) }
    val focusIndex = remember(scenario, entries) {
        when (if (entryMode) baseScenario else scenario) {
            "first" -> 0
            "last" -> entries.lastIndex
            "reentry" -> 1
            "failure" -> 1
            "reorder" -> 1
            else -> (entries.size / 2).coerceAtLeast(0)
        }
    }
    val navigationMemory = remember(scenario) {
        NavigationMemoryStore().also { memory ->
            if (entryMode) {
                memory.save(MainDestination.SERIES, "SERIES:900", 0)
            } else {
                entries.getOrNull(focusIndex)?.let { entry ->
                    memory.save(MainDestination.SERIES, entry.key, focusIndex)
                }
            }
        }
    }
    var state by remember(scenario) {
        mutableStateOf(
            HulkUiState(
                screen = HulkScreen.MAIN,
                isStarting = false,
                isLoading = false,
                destination = MainDestination.SERIES,
                selectedType = ContentType.SERIES,
                selectedCategoryId = if (entryMode) null else CONTINUE_CATEGORY_ID,
                history = entries,
                isProfileLibraryReady = true,
                catalogs = mapOf(
                    ContentType.SERIES to Catalog(
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
            title = "المسلسلات",
            type = ContentType.SERIES,
            destination = MainDestination.SERIES,
            state = state,
            isTv = true,
            navigationMemory = navigationMemory,
            favoriteSnapshot = favoriteSnapshot,
            isFavorite = { false },
            onSelectCategory = { categoryId ->
                Log.i(FIXTURE_TAG, "selectCategory=$categoryId")
                state = state.copy(selectedCategoryId = categoryId)
            },
            onSearch = {},
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

private fun fixtureHistory(scenario: String): List<HistoryEntry> {
    val names = when (scenario) {
        "only" -> listOf("الحلقة أ")
        "many" -> (1..12).map { "الحلقة $it" }
        else -> listOf(
            "الحلقة أ",
            "الحلقة ب",
            "الحلقة ج",
            "الحلقة د",
        )
    }
    return names.mapIndexed { index, title ->
        HistoryEntry(
            key = "SERIES:${100 + index}",
            title = title,
            posterUrl = null,
            streamKind = "series",
            streamId = 1000 + index,
            extension = "mkv",
            isLive = false,
            positionMs = 1_000L + index * 1_000L,
            durationMs = 10_000L,
            updatedAtEpochMs = 10_000L - index,
            seriesTitle = "مسلسل التجربة ${index + 1}",
            season = 1,
            episodeNumber = index + 1,
            episodeTitle = title,
            parentContentId = 500 + index,
        )
    }
}

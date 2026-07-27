package sa.hulksa.player.qa

import android.content.pm.ActivityInfo
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.theme.HulkTheme

/**
 * Debug-only authenticated-shell fixture used by the Compatibility Lab.
 *
 * It renders the production composables with deterministic data and never calls
 * the portal. The source is copied into src/debug by prepare-harness.py and is
 * therefore absent from release builds.
 */
class QaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("ar-SA")
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "home"
        requestedOrientation = when (intent.getStringExtra("orientation")) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val forcedTv = intent.getBooleanExtra("isTv", false)
        val configTv =
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION
        setContent {
            HulkTheme {
                QaAuthenticatedShell(
                    initialDestination = scenario.toDestination(),
                    isTv = forcedTv || configTv,
                )
            }
        }
    }
}

@Composable
private fun QaAuthenticatedShell(
    initialDestination: MainDestination,
    isTv: Boolean,
) {
    val (adaptiveUi, inputController) = rememberAdaptiveUiState(isTv)
    CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi) {
        Box(
            Modifier
                .fillMaxSize()
                .trackAdaptiveInput(inputController),
        ) {
            FixtureMain(initialDestination = initialDestination, isTv = isTv)
        }
    }
}

@Composable
private fun FixtureMain(
    initialDestination: MainDestination,
    isTv: Boolean,
) {
    var favorites by remember {
        mutableStateOf(setOf("MOVIE:101", "SERIES:301", "LIVE:501"))
    }
    var state by remember(initialDestination) {
        mutableStateOf(fixtureState(initialDestination).copy(favorites = favorites))
    }
    val navigationMemory = remember { NavigationMemoryStore() }
    val pageMarker = "qa-page:${state.destination.name.lowercase(Locale.ROOT)}"

    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = false) {
                contentDescription = pageMarker
            },
    ) {
        MainShellScreen(
            state = state.copy(favorites = favorites),
            isTv = isTv,
            navigationMemory = navigationMemory,
            isFavorite = { "${it.type.name}:${it.id}" in favorites },
            onSelectDestination = { destination ->
                state = fixtureState(destination).copy(favorites = favorites)
            },
            onSelectCategory = { state = state.copy(selectedCategoryId = it) },
            onSearch = { state = state.copy(searchQuery = it) },
            onOpen = {},
            onOpenHistory = {},
            onToggleFavorite = { item ->
                val key = "${item.type.name}:${item.id}"
                favorites = if (key in favorites) favorites - key else favorites + key
            },
            onRefresh = {},
            onClearHistory = { state = state.copy(history = emptyList()) },
            onPlayDownload = {},
            onDeleteDownload = { item ->
                state = state.copy(
                    downloads = state.downloads.filterNot {
                        it.downloadId == item.downloadId
                    },
                )
            },
            onRetryDownload = {},
            onToggleWifiOnly = {
                state = state.copy(
                    downloadSettings = state.downloadSettings.copy(
                        wifiOnly = !state.downloadSettings.wifiOnly,
                    ),
                )
            },
            onToggleDownloadSchedule = {
                val next =
                    if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NOW) {
                        DownloadScheduleMode.NIGHT
                    } else {
                        DownloadScheduleMode.NOW
                    }
                state = state.copy(
                    downloadSettings = state.downloadSettings.copy(scheduleMode = next),
                )
            },
            onCycleConcurrentDownloads = {},
            onCycleDownloadPriority = {},
            onRunDiagnostics = {},
            onLogout = {},
        )
    }
}

private fun String.toDestination(): MainDestination = when (lowercase(Locale.ROOT)) {
    "home" -> MainDestination.HOME
    "live" -> MainDestination.LIVE
    "movies" -> MainDestination.MOVIES
    "series" -> MainDestination.SERIES
    "search" -> MainDestination.SEARCH
    "downloads" -> MainDestination.DOWNLOADS
    "settings" -> MainDestination.SETTINGS
    else -> MainDestination.HOME
}

private fun fixtureState(destination: MainDestination): HulkUiState {
    val liveCatalog = Catalog(
        categories = listOf(
            Category("sports", "القنوات الرياضية", ContentType.LIVE),
            Category("news", "القنوات الاخبارية", ContentType.LIVE),
            Category("kids", "قنوات الاطفال", ContentType.LIVE),
            Category("arabic", "القنوات العربية ذات الاسم الطويل", ContentType.LIVE),
        ),
        items = (501..536).map { id ->
            val category = listOf("sports", "news", "kids", "arabic")[(id - 501) % 4]
            live(
                id = id,
                name = "قناة تجريبية رقم ${id - 500} للبث المباشر",
                category = category,
            )
        },
    )
    val movieCatalog = Catalog(
        categories = listOf(
            Category("action", "اكشن ومغامرات", ContentType.MOVIE),
            Category("drama", "دراما", ContentType.MOVIE),
            Category("arabic_movies", "افلام عربية", ContentType.MOVIE),
            Category("family", "عائلي", ContentType.MOVIE),
        ),
        items = (101..140).map { id ->
            movie(
                id = id,
                name = "فيلم تجريبي بعنوان عربي طويل رقم ${id - 100}",
                category = listOf(
                    "action",
                    "drama",
                    "arabic_movies",
                    "family",
                )[(id - 101) % 4],
            )
        },
    )
    val seriesCatalog = Catalog(
        categories = listOf(
            Category("series_ar", "مسلسلات عربية", ContentType.SERIES),
            Category("series_world", "مسلسلات عالمية", ContentType.SERIES),
            Category("series_new", "احدث المسلسلات", ContentType.SERIES),
        ),
        items = (301..340).map { id ->
            series(
                id = id,
                name = "مسلسل تجريبي بعنوان عربي طويل رقم ${id - 300}",
                category = listOf(
                    "series_ar",
                    "series_world",
                    "series_new",
                )[(id - 301) % 3],
            )
        },
    )
    val downloads = listOf(
        OfflineDownload(
            downloadId = 1,
            historyKey = "MOVIE:101",
            title = "فيلم قيد التحميل بعنوان طويل لفحص القص",
            posterUrl = null,
            streamKind = "movie",
            streamId = 101,
            extension = "mkv",
            status = OfflineStatus.DOWNLOADING,
            bytesDownloaded = 500_000_000,
            totalBytes = 1_500_000_000,
            bytesPerSecond = 5_000_000,
            etaSeconds = 200,
        ),
        OfflineDownload(
            downloadId = 2,
            historyKey = "SERIES:3003",
            title = "الحلقة الثالثة بعنوان تجريبي طويل",
            posterUrl = null,
            streamKind = "series",
            streamId = 3003,
            extension = "mkv",
            seriesTitle = "المسلسل التجريبي",
            season = 1,
            episodeNumber = 3,
            status = OfflineStatus.PAUSED,
            bytesDownloaded = 220_000_000,
            totalBytes = 900_000_000,
        ),
        OfflineDownload(
            downloadId = 3,
            historyKey = "MOVIE:110",
            title = "فيلم مكتمل وجاهز للمشاهدة",
            posterUrl = null,
            streamKind = "movie",
            streamId = 110,
            extension = "mp4",
            status = OfflineStatus.COMPLETED,
            bytesDownloaded = 1_000_000_000,
            totalBytes = 1_000_000_000,
            localUri = "file:///data/local/tmp/qa-fixture.mp4",
        ),
    )
    val fixedNow = 1_800_000_000_000L
    val history = listOf(
        HistoryEntry(
            "MOVIE:101",
            "فيلم تجريبي رقم 1",
            null,
            "movie",
            101,
            "mkv",
            false,
            2_000_000,
            7_200_000,
            fixedNow,
        ),
        HistoryEntry(
            "SERIES:3003",
            "الحلقة الثالثة من المسلسل التجريبي",
            null,
            "series",
            3003,
            "mkv",
            false,
            1_000_000,
            2_700_000,
            fixedNow - 10_000,
        ),
        HistoryEntry(
            "LIVE:501",
            "القناة الرياضية التجريبية",
            null,
            "live",
            501,
            "m3u8",
            true,
            0,
            0,
            fixedNow - 20_000,
        ),
    )
    return HulkUiState(
        screen = HulkScreen.MAIN,
        isStarting = false,
        isLoading = false,
        account = AccountInfo(
            username = "qa-fixture",
            status = "Active",
            expiresAtEpochSeconds = 1_893_456_000,
            activeConnections = 1,
            maxConnections = 4,
            isTrial = false,
        ),
        destination = destination,
        selectedType = when (destination) {
            MainDestination.LIVE -> ContentType.LIVE
            MainDestination.SERIES -> ContentType.SERIES
            else -> ContentType.MOVIE
        },
        catalogs = mapOf(
            ContentType.LIVE to liveCatalog,
            ContentType.MOVIE to movieCatalog,
            ContentType.SERIES to seriesCatalog,
        ),
        selectedCategoryId = null,
        searchQuery = if (destination == MainDestination.SEARCH) "فيلم" else "",
        downloads = downloads,
        history = history,
        downloadSettings = DownloadSettings(
            wifiOnly = true,
            scheduleMode = DownloadScheduleMode.NOW,
            concurrentDownloads = 2,
        ),
    )
}

private fun movie(
    id: Int,
    name: String,
    category: String,
) = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.MOVIE,
    posterUrl = null,
    rating = "8.4",
    year = "2026",
    containerExtension = "mkv",
    addedAtEpochSeconds = 1_800_000_000L - id,
    plot = "وصف تجريبي طويل للفيلم لاختبار توزيع النصوص العربية.",
    genre = "اكشن ودراما",
)

private fun series(
    id: Int,
    name: String,
    category: String,
) = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.SERIES,
    posterUrl = null,
    rating = "8.1",
    year = "2026",
    containerExtension = null,
    addedAtEpochSeconds = 1_800_000_000L - id,
    plot = "وصف تجريبي طويل للمسلسل لاختبار توزيع النصوص العربية.",
    genre = "دراما وتشويق",
)

private fun live(
    id: Int,
    name: String,
    category: String,
) = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.LIVE,
    posterUrl = null,
    rating = null,
    year = null,
    containerExtension = "m3u8",
    nowPlaying = "برنامج مباشر تجريبي بعنوان طويل",
    addedAtEpochSeconds = 1_800_000_000L - id,
)

package sa.hulksa.player.qa

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
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.rememberAdaptiveUiState
import sa.hulksa.player.ui.adaptive.trackAdaptiveInput
import sa.hulksa.player.ui.screens.LoginScreen
import sa.hulksa.player.ui.screens.MainShellScreen
import sa.hulksa.player.ui.screens.MovieDetailsScreen
import sa.hulksa.player.ui.screens.NavigationMemoryStore
import sa.hulksa.player.ui.screens.PlayerScreen
import sa.hulksa.player.ui.screens.SeriesScreen
import sa.hulksa.player.ui.theme.HulkTheme

class QaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "login"
        val forcedTv = intent.getBooleanExtra("isTv", false)
        val configTv = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        setContent {
            HulkTheme {
                QaRoot(scenario = scenario, isTv = forcedTv || configTv)
            }
        }
    }
}

@Composable
private fun QaRoot(scenario: String, isTv: Boolean) {
    val (adaptiveUi, inputController) = rememberAdaptiveUiState(isTv)
    CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi) {
        Box(Modifier.fillMaxSize().trackAdaptiveInput(inputController)) {
            when {
                scenario == "login" || scenario == "login_ime_base" -> LoginScreen(
                    isTv = isTv,
                    isStarting = false,
                    isLoading = false,
                    errorMessage = null,
                    onLogin = { _, _, _ -> },
                )
                scenario == "movie" -> MovieQa(isTv)
                scenario == "series_details" -> SeriesQa(isTv)
                scenario.startsWith("player_") -> PlayerQa(scenario)
                else -> MainQa(scenario, isTv)
            }
        }
    }
}

@Composable
private fun MainQa(scenario: String, isTv: Boolean) {
    val destination = when (scenario) {
        "home" -> MainDestination.HOME
        "live" -> MainDestination.LIVE
        "movies" -> MainDestination.MOVIES
        "series" -> MainDestination.SERIES
        "favorites" -> MainDestination.FAVORITES
        "search" -> MainDestination.SEARCH
        "downloads" -> MainDestination.DOWNLOADS
        "settings" -> MainDestination.SETTINGS
        else -> MainDestination.HOME
    }
    var favorites by remember { mutableStateOf(setOf("MOVIE:101", "SERIES:301", "LIVE:501")) }
    var state by remember(destination) {
        mutableStateOf(qaState(destination).copy(favorites = favorites, searchQuery = if (destination == MainDestination.SEARCH) "فيلم" else ""))
    }
    val navMemory = remember { NavigationMemoryStore() }
    MainShellScreen(
        state = state.copy(favorites = favorites),
        isTv = isTv,
        navigationMemory = navMemory,
        isFavorite = { "${it.type.name}:${it.id}" in favorites },
        onSelectDestination = { state = qaState(it).copy(favorites = favorites) },
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
        onDeleteDownload = { d -> state = state.copy(downloads = state.downloads.filterNot { it.downloadId == d.downloadId }) },
        onRetryDownload = {},
        onToggleWifiOnly = { state = state.copy(downloadSettings = state.downloadSettings.copy(wifiOnly = !state.downloadSettings.wifiOnly)) },
        onToggleDownloadSchedule = {
            val next = if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NOW) DownloadScheduleMode.NIGHT else DownloadScheduleMode.NOW
            state = state.copy(downloadSettings = state.downloadSettings.copy(scheduleMode = next))
        },
        onCycleConcurrentDownloads = {},
        onCycleDownloadPriority = {},
        onRunDiagnostics = {},
        onLogout = {},
    )
}

@Composable
private fun MovieQa(isTv: Boolean) {
    val item = movie(101, "الفيلم التجريبي الطويل")
    val related = (102..111).map { movie(it, "عمل مشابه رقم ${it - 101}") }
    var favorites by remember { mutableStateOf(setOf("MOVIE:104")) }
    MovieDetailsScreen(
        item = item,
        details = ContentDetails(
            plot = "وصف تجريبي طويل لفحص المحاذاة العربية وتوزيع النصوص على الشاشات المختلفة بدون الاعتماد على اتصال خارجي.",
            genre = "اكشن • دراما",
            duration = "ساعتان و 14 دقيقة",
            director = "مخرج تجريبي",
            cast = "ممثل اول، ممثل ثان، ممثل ثالث",
            releaseDate = "2026",
        ),
        isLoading = false,
        errorMessage = null,
        isTv = isTv,
        isFavorite = true,
        download = OfflineDownload(
            downloadId = 9001,
            historyKey = "MOVIE:101",
            title = item.name,
            posterUrl = null,
            streamKind = "movie",
            streamId = item.id,
            extension = "mkv",
            status = OfflineStatus.DOWNLOADING,
            bytesDownloaded = 734_003_200,
            totalBytes = 2_147_483_648,
            bytesPerSecond = 8_388_608,
            etaSeconds = 180,
        ),
        historyEntry = HistoryEntry(
            key = "MOVIE:101",
            title = item.name,
            posterUrl = null,
            streamKind = "movie",
            streamId = item.id,
            extension = "mkv",
            isLive = false,
            positionMs = 2_400_000,
            durationMs = 7_200_000,
            updatedAtEpochMs = System.currentTimeMillis(),
        ),
        relatedItems = related,
        isRelatedFavorite = { "${it.type.name}:${it.id}" in favorites },
        onBack = {},
        onPlay = {},
        onDownload = {},
        onCancelDownload = {},
        onToggleFavorite = {},
        onToggleRelatedFavorite = { relatedItem ->
            val key = "${relatedItem.type.name}:${relatedItem.id}"
            favorites = if (key in favorites) favorites - key else favorites + key
        },
        onOpenRelated = {},
    )
}

@Composable
private fun SeriesQa(isTv: Boolean) {
    val series = series(301, "المسلسل التجريبي")
    val episodes = (1..18).map {
        Episode(
            id = 3_000 + it,
            title = "الحلقة $it بعنوان عربي طويل",
            season = if (it <= 10) 1 else 2,
            episodeNumber = if (it <= 10) it else it - 10,
            containerExtension = "mkv",
            posterUrl = null,
            duration = "45 دقيقة",
        )
    }
    val downloads = listOf(
        OfflineDownload(
            downloadId = 8001,
            historyKey = "SERIES:3003",
            title = "الحلقة 3",
            posterUrl = null,
            streamKind = "series",
            streamId = 3003,
            extension = "mkv",
            seriesTitle = series.name,
            season = 1,
            episodeNumber = 3,
            status = OfflineStatus.DOWNLOADING,
            bytesDownloaded = 367_001_600,
            totalBytes = 1_073_741_824,
            bytesPerSecond = 4_194_304,
            etaSeconds = 120,
        ),
    )
    val related = (302..311).map { series(it, "مسلسل مشابه رقم ${it - 301}") }
    var favorites by remember { mutableStateOf(setOf("SERIES:305")) }
    SeriesScreen(
        series = series,
        details = ContentDetails(
            plot = "وصف مسلسل تجريبي لفحص صفحة الحلقات والهوامش والأعمال المشابهة.",
            genre = "دراما • تشويق",
            duration = "حلقة 45 دقيقة",
            releaseDate = "2026",
        ),
        episodes = episodes,
        isLoading = false,
        errorMessage = null,
        isTv = isTv,
        isFavorite = true,
        downloads = downloads,
        history = emptyList(),
        relatedItems = related,
        isRelatedFavorite = { "${it.type.name}:${it.id}" in favorites },
        onBack = {},
        onPlay = {},
        onDownload = {},
        onCancelDownload = {},
        onToggleFavorite = {},
        onToggleRelatedFavorite = { relatedItem ->
            val key = "${relatedItem.type.name}:${relatedItem.id}"
            favorites = if (key in favorites) favorites - key else favorites + key
        },
        onOpenRelated = {},
    )
}

@Composable
private fun PlayerQa(scenario: String) {
    val live = scenario.startsWith("player_live")
    val panel = scenario.substringAfter("panel_", "").uppercase().ifBlank { null }
    val liveItems = (501..512).map { live(it, "قناة رياضية رقم ${it - 500}", if (it <= 506) "sports" else "news") }
    val request = PlaybackRequest(
        title = if (live) "قناة رياضية تجريبية" else "فيلم تجريبي داخل المشغل",
        posterUrl = null,
        candidates = listOf("http://127.0.0.1:9/qa-test.m3u8"),
        isLive = live,
        historyKey = if (live) "LIVE:501" else "MOVIE:101",
        streamKind = if (live) "live" else "movie",
        streamId = if (live) 501 else 101,
        extension = if (live) "m3u8" else "mkv",
        resumePositionMs = 0,
    )
    PlayerScreen(
        request = request,
        liveCatalog = if (live) Catalog(
            categories = listOf(Category("sports", "رياضة", ContentType.LIVE), Category("news", "اخبار", ContentType.LIVE)),
            items = liveItems,
        ) else null,
        isFavorite = { it.id == 501 },
        onSelectLiveChannel = {},
        onToggleFavorite = {},
        onBack = {},
        onProgress = { _, _, _ -> },
        nextEpisodeTitle = if (scenario == "player_next_episode") "الموسم 1 • الحلقة 2 • الحلقة التالية" else null,
        onPlayNextEpisode = if (scenario == "player_next_episode") ({}) else null,
        qaInitialPanel = panel,
        qaShowNextEpisode = scenario == "player_next_episode",
    )
}

private fun qaState(destination: MainDestination): HulkUiState {
    val liveCatalog = Catalog(
        categories = listOf(
            Category("sports", "القنوات الرياضية", ContentType.LIVE),
            Category("news", "القنوات الاخبارية", ContentType.LIVE),
            Category("kids", "قنوات الاطفال", ContentType.LIVE),
            Category("arabic", "القنوات العربية الطويلة", ContentType.LIVE),
        ),
        items = (501..528).map { id ->
            val cat = listOf("sports", "news", "kids", "arabic")[(id - 501) % 4]
            live(id, "قناة تجريبية رقم ${id - 500}", cat)
        },
    )
    val movieCatalog = Catalog(
        categories = listOf(
            Category("action", "اكشن", ContentType.MOVIE),
            Category("drama", "دراما", ContentType.MOVIE),
            Category("arabic_movies", "افلام عربية", ContentType.MOVIE),
            Category("family", "عائلي", ContentType.MOVIE),
        ),
        items = (101..132).map { id -> movie(id, "فيلم تجريبي رقم ${id - 100}", listOf("action", "drama", "arabic_movies", "family")[(id - 101) % 4]) },
    )
    val seriesCatalog = Catalog(
        categories = listOf(
            Category("series_ar", "مسلسلات عربية", ContentType.SERIES),
            Category("series_world", "مسلسلات عالمية", ContentType.SERIES),
            Category("series_new", "احدث المسلسلات", ContentType.SERIES),
        ),
        items = (301..328).map { id -> series(id, "مسلسل تجريبي رقم ${id - 300}", listOf("series_ar", "series_world", "series_new")[(id - 301) % 3]) },
    )
    val downloads = listOf(
        OfflineDownload(1, "MOVIE:101", "فيلم قيد التحميل", null, "movie", 101, "mkv", status = OfflineStatus.DOWNLOADING, bytesDownloaded = 500_000_000, totalBytes = 1_500_000_000, bytesPerSecond = 5_000_000, etaSeconds = 200),
        OfflineDownload(2, "SERIES:3003", "الحلقة الثالثة", null, "series", 3003, "mkv", seriesTitle = "المسلسل التجريبي", season = 1, episodeNumber = 3, status = OfflineStatus.PAUSED, bytesDownloaded = 220_000_000, totalBytes = 900_000_000),
        OfflineDownload(3, "MOVIE:110", "فيلم مكتمل", null, "movie", 110, "mp4", status = OfflineStatus.COMPLETED, bytesDownloaded = 1_000_000_000, totalBytes = 1_000_000_000, localUri = "file:///data/local/tmp/fake.mp4"),
    )
    val history = listOf(
        HistoryEntry("MOVIE:101", "فيلم تجريبي رقم 1", null, "movie", 101, "mkv", false, 2_000_000, 7_200_000, System.currentTimeMillis()),
        HistoryEntry("SERIES:3003", "الحلقة الثالثة", null, "series", 3003, "mkv", false, 1_000_000, 2_700_000, System.currentTimeMillis() - 10_000),
    )
    return HulkUiState(
        screen = sa.hulksa.player.HulkScreen.MAIN,
        isStarting = false,
        isLoading = false,
        account = AccountInfo("qa-user", "Active", 1_800_000_000, 1, 2, false),
        destination = destination,
        selectedType = when (destination) {
            MainDestination.LIVE -> ContentType.LIVE
            MainDestination.SERIES -> ContentType.SERIES
            else -> ContentType.MOVIE
        },
        catalogs = mapOf(ContentType.LIVE to liveCatalog, ContentType.MOVIE to movieCatalog, ContentType.SERIES to seriesCatalog),
        selectedCategoryId = null,
        downloads = downloads,
        history = history,
        downloadSettings = DownloadSettings(wifiOnly = true, scheduleMode = DownloadScheduleMode.NOW, concurrentDownloads = 2),
    )
}

private fun movie(id: Int, name: String, category: String = "action") = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.MOVIE,
    posterUrl = null,
    rating = "8.4",
    year = "2026",
    containerExtension = "mkv",
    plot = "وصف تجريبي للفيلم",
    genre = "اكشن",
)

private fun series(id: Int, name: String, category: String = "series_ar") = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.SERIES,
    posterUrl = null,
    rating = "8.1",
    year = "2026",
    containerExtension = null,
    plot = "وصف تجريبي للمسلسل",
    genre = "دراما",
)

private fun live(id: Int, name: String, category: String) = ContentItem(
    id = id,
    name = name,
    categoryId = category,
    type = ContentType.LIVE,
    posterUrl = null,
    rating = null,
    year = null,
    containerExtension = "m3u8",
    nowPlaying = "برنامج مباشر تجريبي",
)

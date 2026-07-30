package sa.hulksa.player.qa

import android.content.pm.ActivityInfo
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.data.DownloadRepository
import sa.hulksa.player.data.DownloadRepositoryProcessOwner
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
import sa.hulksa.player.model.PlaybackRequest
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
    private var downloadServer: QaRangeServer? = null
    private var downloadHarnessState by mutableStateOf<QaDownloadHarness?>(null)

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
                    downloadHarness = downloadHarnessState,
                )
            }
        }
        if (scenario == "downloads") {
            lifecycleScope.launch {
                downloadHarnessState = withContext(Dispatchers.IO) {
                    prepareDownloadHarness()
                }
            }
        }
    }

    override fun onDestroy() {
        downloadServer?.close()
        downloadServer = null
        super.onDestroy()
    }

    private fun prepareDownloadHarness(): QaDownloadHarness {
        getSharedPreferences("hulk_downloads", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
            .filterNotNull()
            .forEach { directory ->
                directory.listFiles()
                    ?.filter { file ->
                        file.name.startsWith(QA_DOWNLOAD_FILE_PREFIX) ||
                            file.name.startsWith("$QA_DOWNLOAD_FILE_PREFIX.")
                    }
                    ?.forEach { file -> file.delete() }
        }
        val server = QaRangeServer().also(QaRangeServer::start)
        downloadServer = server
        val repository = DownloadRepositoryProcessOwner.get(applicationContext)

        /*
         * WorkManager can restore a previous debug-fixture worker before this
         * Activity starts. In that order, the process owner has already loaded
         * the previous loopback URL even though SharedPreferences was cleared
         * above. Remove those fixture records through the owner itself before
         * enqueueing the new server URLs; production records are never touched.
         */
        repository.downloads()
            .filter { item ->
                item.historyKey.startsWith("$QA_DOWNLOAD_HISTORY_PREFIX:") ||
                    item.title.startsWith(QA_DOWNLOAD_FILE_PREFIX)
            }
            .map(OfflineDownload::downloadId)
            .forEach { downloadId -> repository.remove(downloadId) }

        repository.setWifiOnly(false)
        repository.setScheduleMode(DownloadScheduleMode.NOW)
        repository.setConcurrentDownloads(2)
        repeat(3) { index ->
            val number = index + 1
            repository.enqueue(
                PlaybackRequest(
                    title = "${QA_DOWNLOAD_FILE_PREFIX}_$number تنزيل تجريبي بعنوان عربي طويل",
                    posterUrl = null,
                    candidates = listOf("${server.baseUrl}/fixture-$number.mp4"),
                    isLive = false,
                    historyKey = "$QA_DOWNLOAD_HISTORY_PREFIX:$number",
                    streamKind = "movie",
                    streamId = 9_000 + number,
                    extension = "mp4",
                ),
            )
        }
        return QaDownloadHarness(repository, server)
    }
}

private data class QaDownloadHarness(
    val repository: DownloadRepository,
    val origin: QaRangeServer,
)

@Composable
private fun QaAuthenticatedShell(
    initialDestination: MainDestination,
    isTv: Boolean,
    downloadHarness: QaDownloadHarness?,
) {
    val (adaptiveUi, inputController) = rememberAdaptiveUiState(isTv)
    CompositionLocalProvider(LocalAdaptiveUi provides adaptiveUi) {
        Box(
            Modifier
                .fillMaxSize()
                .trackAdaptiveInput(inputController),
        ) {
            FixtureMain(
                initialDestination = initialDestination,
                isTv = isTv,
                downloadHarness = downloadHarness,
            )
        }
    }
}

@Composable
private fun FixtureMain(
    initialDestination: MainDestination,
    isTv: Boolean,
    downloadHarness: QaDownloadHarness?,
) {
    val downloadRepository = downloadHarness?.repository
    var favorites by remember {
        mutableStateOf(setOf("MOVIE:101", "SERIES:301", "LIVE:501"))
    }
    var state by remember(initialDestination) {
        mutableStateOf(fixtureState(initialDestination).copy(favorites = favorites))
    }
    var actionRevision by remember { mutableStateOf(0) }
    var lastDownloadAction by remember { mutableStateOf<String?>(null) }
    val navigationMemory = remember { NavigationMemoryStore() }
    val pageMarker = "qa-page:${state.destination.name.lowercase(Locale.ROOT)}"
    var originBytesServed by remember(downloadHarness) {
        mutableStateOf(downloadHarness?.origin?.bytesServed() ?: 0L)
    }
    val hasRealDownloadProgress =
        downloadRepository != null && state.downloads.any { it.bytesDownloaded > 0L }
    val hasOriginByteProgress = originBytesServed > 0L

    fun publishDownloadAction(action: String) {
        actionRevision += 1
        lastDownloadAction = "$action:$actionRevision"
    }

    fun refreshDownloads(repository: DownloadRepository) {
        state = state.copy(
            downloads = repository.downloads(),
            downloadSettings = repository.settings(),
        )
    }

    LaunchedEffect(downloadHarness) {
        while (downloadHarness != null) {
            refreshDownloads(downloadHarness.repository)
            originBytesServed = downloadHarness.origin.bytesServed()
            delay(QA_DOWNLOAD_POLL_MS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = false) {
                contentDescription = buildList {
                    add(pageMarker)
                    if (hasOriginByteProgress) {
                        add(QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER)
                    }
                    if (hasRealDownloadProgress) {
                        add(QA_DOWNLOAD_PROGRESS_MARKER)
                    }
                    lastDownloadAction?.let { marker ->
                        if (state.destination == MainDestination.DOWNLOADS) {
                            add("qa-download-action:$marker")
                        }
                    }
                }
                    .joinToString(",")
            },
    ) {
        MainShellScreen(
            state = state.copy(favorites = favorites),
            isTv = isTv,
            navigationMemory = navigationMemory,
            isFavorite = { "${it.type.name}:${it.id}" in favorites },
            onSelectDestination = { destination ->
                val next = fixtureState(destination).copy(favorites = favorites)
                state = if (destination == MainDestination.DOWNLOADS && downloadRepository != null) {
                    next.copy(
                        downloads = downloadRepository.downloads(),
                        downloadSettings = downloadRepository.settings(),
                    )
                } else {
                    next
                }
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
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloads = state.downloads.filterNot { it.downloadId == item.downloadId },
                    )
                } else {
                    repository.remove(item.downloadId)
                    refreshDownloads(repository)
                }
                publishDownloadAction("cancel")
            },
            onRetryDownload = { item ->
                val repository = downloadRepository
                if (repository != null) {
                    val action = when (item.status) {
                        OfflineStatus.QUEUED,
                        OfflineStatus.CHECKING,
                        OfflineStatus.DOWNLOADING,
                        -> {
                            repository.pause(item.downloadId)
                            "pause"
                        }
                        OfflineStatus.PAUSED,
                        OfflineStatus.WAITING_SCHEDULE,
                        OfflineStatus.WAITING_NETWORK,
                        OfflineStatus.WAITING_STORAGE,
                        OfflineStatus.FAILED,
                        -> {
                            repository.resume(item.downloadId)
                            "resume"
                        }
                        OfflineStatus.COMPLETED -> "play"
                    }
                    refreshDownloads(repository)
                    publishDownloadAction(action)
                }
            },
            onToggleWifiOnly = {
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(
                            wifiOnly = !state.downloadSettings.wifiOnly,
                        ),
                    )
                } else {
                    repository.setWifiOnly(!state.downloadSettings.wifiOnly)
                    refreshDownloads(repository)
                }
                publishDownloadAction("wifi")
            },
            onToggleDownloadSchedule = {
                val next =
                    if (state.downloadSettings.scheduleMode == DownloadScheduleMode.NOW) {
                        DownloadScheduleMode.NIGHT
                    } else {
                        DownloadScheduleMode.NOW
                    }
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(scheduleMode = next),
                    )
                } else {
                    repository.setScheduleMode(next)
                    refreshDownloads(repository)
                }
                publishDownloadAction("schedule")
            },
            onCycleConcurrentDownloads = {
                val next = (state.downloadSettings.concurrentDownloads % 3) + 1
                val repository = downloadRepository
                if (repository == null) {
                    state = state.copy(
                        downloadSettings = state.downloadSettings.copy(concurrentDownloads = next),
                    )
                } else {
                    repository.setConcurrentDownloads(next)
                    refreshDownloads(repository)
                }
                publishDownloadAction("concurrent")
            },
            onCycleDownloadPriority = { item ->
                val repository = downloadRepository
                if (repository != null) {
                    repository.cyclePriority(item.downloadId)
                    refreshDownloads(repository)
                }
                publishDownloadAction("priority")
            },
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
    "favorites" -> MainDestination.FAVORITES
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

private const val QA_DOWNLOAD_FILE_PREFIX = "QA_DOWNLOAD"
private const val QA_DOWNLOAD_HISTORY_PREFIX = "QA_DOWNLOAD"
private const val QA_DOWNLOAD_PROGRESS_MARKER = "qa-download-transfer:bytes-positive"
private const val QA_DOWNLOAD_ORIGIN_PROGRESS_MARKER = "qa-download-origin:bytes-positive"
private const val QA_DOWNLOAD_POLL_MS = 100L
private const val QA_DOWNLOAD_TOTAL_BYTES = 64L * 1024L * 1024L
private const val QA_DOWNLOAD_WRITE_BLOCK = 64 * 1024
private const val QA_DOWNLOAD_WRITE_DELAY_MS = 40L

/**
 * Debug-only loopback origin. Bounded byte ranges progress normally, while an
 * open-ended range sends headers and stalls. This makes the v0.9.3.19
 * `bytes=N-` transport fail the lab and proves that the current bounded-range
 * implementation transfers actual bytes.
 */
private class QaRangeServer : Closeable {
    private val running = AtomicBoolean(false)
    private val transferredBytes = AtomicLong(0L)
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "hulk-qa-range-client").apply { isDaemon = true }
    }
    private var acceptThread: Thread? = null

    val baseUrl: String = "http://127.0.0.1:${server.localPort}"

    fun bytesServed(): Long = transferredBytes.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptThread = Thread({
            while (running.get()) {
                try {
                    val socket = server.accept()
                    workers.execute { serveSafely(socket) }
                } catch (_: Exception) {
                    if (running.get()) {
                        // The analyzer will fail the transfer marker if serving stops.
                    }
                }
            }
        }, "hulk-qa-range-accept").apply {
            isDaemon = true
            start()
        }
    }

    private fun serveSafely(socket: Socket) {
        try {
            serve(socket)
        } catch (_: IOException) {
            // A downloader can close a completed or cancelled range before the
            // fixture finishes flushing. The transfer marker remains the gate;
            // an expected client disconnect must not terminate the app process.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun serve(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readAsciiLine(input) ?: return
            if (!requestLine.startsWith("GET ")) {
                writeHeaders(output, "405 Method Not Allowed", 0L)
                return
            }
            var rangeHeader: String? = null
            while (true) {
                val line = readAsciiLine(input) ?: return
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
            }
            val range = parseBoundedRange(rangeHeader)
            if (rangeHeader?.matches(Regex("""bytes=\d+-""")) == true) {
                val start = rangeHeader.substringAfter("bytes=").substringBefore('-').toLong()
                val length = (QA_DOWNLOAD_TOTAL_BYTES - start).coerceAtLeast(0L)
                writeHeaders(
                    output = output,
                    status = "206 Partial Content",
                    length = length,
                    contentRange = "bytes $start-${QA_DOWNLOAD_TOTAL_BYTES - 1}/$QA_DOWNLOAD_TOTAL_BYTES",
                )
                output.flush()
                Thread.sleep(35_000L)
                return
            }
            if (range != null) {
                val start = range.first.coerceAtMost(QA_DOWNLOAD_TOTAL_BYTES - 1L)
                val end = range.last.coerceAtMost(QA_DOWNLOAD_TOTAL_BYTES - 1L)
                val length = (end - start + 1L).coerceAtLeast(0L)
                writeHeaders(
                    output = output,
                    status = "206 Partial Content",
                    length = length,
                    contentRange = "bytes $start-$end/$QA_DOWNLOAD_TOTAL_BYTES",
                )
                streamBytes(output, length)
                return
            }
            writeHeaders(output, "200 OK", QA_DOWNLOAD_TOTAL_BYTES)
            streamBytes(output, QA_DOWNLOAD_TOTAL_BYTES)
        }
    }

    private fun writeHeaders(
        output: BufferedOutputStream,
        status: String,
        length: Long,
        contentRange: String? = null,
    ) {
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $length\r\n")
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun streamBytes(output: BufferedOutputStream, length: Long) {
        val block = ByteArray(QA_DOWNLOAD_WRITE_BLOCK) { index -> (index % 251).toByte() }
        var remaining = length
        while (remaining > 0L && running.get()) {
            val count = minOf(block.size.toLong(), remaining).toInt()
            output.write(block, 0, count)
            output.flush()
            transferredBytes.addAndGet(count.toLong())
            remaining -= count
            Thread.sleep(QA_DOWNLOAD_WRITE_DELAY_MS)
        }
    }

    private fun parseBoundedRange(value: String?): LongRange? {
        val match = Regex("""bytes=(\d+)-(\d+)""").matchEntire(value.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        return if (end >= start) start..end else null
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) {
                return if (bytes.size() == 0) {
                    null
                } else {
                    bytes.toString(StandardCharsets.US_ASCII.name())
                }
            }
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.write(next)
        }
        return bytes.toString(StandardCharsets.US_ASCII.name())
    }

    override fun close() {
        running.set(false)
        runCatching { server.close() }
        workers.shutdownNow()
        acceptThread?.interrupt()
        acceptThread = null
    }
}

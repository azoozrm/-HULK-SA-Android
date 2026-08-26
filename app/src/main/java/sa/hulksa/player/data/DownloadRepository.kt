package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.security.persistableExternalUrlOrNull
import sa.hulksa.player.security.redactCredentialBearingUrl
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

class DownloadRepository internal constructor(
    context: Context,
    internal val accountId: String,
    private val preferences: SharedPreferences,
) {
    private val appContext = context.applicationContext
    private val accountScopeStore = AccountScopeStore(appContext)
    private val accountSessionStore = AccountSessionStore(appContext)
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // OkHttp resets this timeout whenever bytes arrive, so long downloads keep
        // running while a transport that sends headers and then stalls is retried.
        .readTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val calls = ConcurrentHashMap<Long, Call>()
    private val lock = Any()
    private val nextId = AtomicLong(System.currentTimeMillis())
    @Volatile
    private var accountBoundarySuspended = false
    private var cache = readStored().map(::recoverInterruptedState).toMutableList()

    init {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        synchronized(lock) {
            normalizeQueueLocked()
            writeStoredLocked()
        }
        schedule()
    }

    fun downloads(): List<OfflineDownload> {
        val snapshot = synchronized(lock) {
            var changed = false
            cache = cache.map { item ->
                if (item.status == OfflineStatus.COMPLETED && !completedFileExists(item)) {
                    changed = true
                    item.copy(
                        status = OfflineStatus.FAILED,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        integrityVerified = false,
                        errorMessage = "ملف التحميل غير موجود في وحدة التخزين.",
                    )
                } else {
                    item
                }
            }.toMutableList()
            if (changed) writeStoredLocked()
            sortedSnapshotLocked()
        }
        schedule()
        return snapshot
    }

    internal fun record(downloadId: Long): OfflineDownload? = item(downloadId)

    internal fun suspendForAccountBoundary() {
        accountBoundarySuspended = true
        calls.values.forEach(Call::cancel)
        jobs.values.forEach(Job::cancel)
        synchronized(lock) {
            cache = suspendDownloadsForAccountBoundary(cache).toMutableList()
            writeStoredLocked(synchronous = true)
        }
        calls.clear()
        jobs.clear()
    }

    fun settings(): DownloadSettings = DownloadSettings(
        wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, false),
        scheduleMode = runCatching {
            DownloadScheduleMode.valueOf(
                preferences.getString(KEY_SCHEDULE_MODE, DownloadScheduleMode.NOW.name)
                    ?: DownloadScheduleMode.NOW.name,
            )
        }.getOrDefault(DownloadScheduleMode.NOW),
        concurrentDownloads = preferences.getInt(KEY_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS)
            .coerceIn(1, MAX_CONCURRENT_DOWNLOADS),
    )

    fun setWifiOnly(enabled: Boolean): DownloadSettings {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        schedule()
        return settings()
    }

    fun setScheduleMode(mode: DownloadScheduleMode): DownloadSettings {
        preferences.edit().putString(KEY_SCHEDULE_MODE, mode.name).apply()
        val scheduledAt = if (mode == DownloadScheduleMode.NIGHT) nextNightStartEpochMs() else 0L
        synchronized(lock) {
            cache = cache.map { item ->
                when (item.status) {
                    OfflineStatus.QUEUED,
                    OfflineStatus.WAITING_SCHEDULE,
                    OfflineStatus.WAITING_NETWORK,
                    OfflineStatus.WAITING_STORAGE,
                    -> item.copy(
                        status = if (scheduledAt > 0L) OfflineStatus.WAITING_SCHEDULE else OfflineStatus.QUEUED,
                        scheduledAtEpochMs = scheduledAt,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        errorMessage = if (scheduledAt > 0L) "مجدول للتحميل الليلي." else null,
                    )
                    else -> item
                }
            }.toMutableList()
            normalizeQueueLocked()
            writeStoredLocked()
        }
        schedule()
        return settings()
    }

    fun setConcurrentDownloads(count: Int): DownloadSettings {
        preferences.edit().putInt(KEY_CONCURRENT_DOWNLOADS, count.coerceIn(1, MAX_CONCURRENT_DOWNLOADS)).apply()
        schedule()
        return settings()
    }

    fun cyclePriority(downloadId: Long): List<OfflineDownload> {
        synchronized(lock) {
            mutateLocked(downloadId) { item ->
                if (item.status == OfflineStatus.COMPLETED) item else item.copy(
                    priority = when (item.priority) {
                        1 -> -1
                        -1 -> 0
                        else -> 1
                    },
                )
            }
            normalizeQueueLocked()
            writeStoredLocked()
        }
        schedule()
        return downloads()
    }

    fun enqueue(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): EnqueueResult {
        require(!request.isLive) { "لا يمكن تحميل البث المباشر." }
        val hasRuntimeSource = request.candidates.any { it.isNotBlank() }
        if (!hasRuntimeSource) return EnqueueResult.Failed("لا يوجد رابط صالح للتحميل.")

        val target = selectedStorageTarget()
            ?: return EnqueueResult.Failed("مساحة التخزين غير متاحة على هذا الجهاز.")
        if (!target.directory.exists() && !target.directory.mkdirs()) {
            return EnqueueResult.Failed("تعذر تجهيز مجلد التحميل.")
        }
        if (target.directory.usableSpace < MINIMUM_START_SPACE_BYTES) {
            return EnqueueResult.Failed("المساحة المتاحة قليلة جدا. وفر مساحة ثم حاول مرة اخرى.")
        }

        val extension = safeExtension(request.extension)
        val fileName = buildFileName(request.title, request.streamId, extension)
        val finalFile = File(target.directory, fileName)

        val entry = synchronized(lock) {
            cache.firstOrNull { it.historyKey == request.historyKey }?.let {
                return EnqueueResult.AlreadyExists(it)
            }
            val id = nextUniqueIdLocked()
            val queuePosition = (cache.maxOfOrNull(OfflineDownload::queuePosition) ?: -1) + 1
            val scheduledAt = scheduledStartForNewDownload()
            OfflineDownload(
                downloadId = id,
                historyKey = request.historyKey,
                title = request.title,
                posterUrl = persistableExternalUrlOrNull(request.posterUrl),
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = extension,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
                sourceCandidates = emptyList(),
                fileName = fileName,
                storagePath = target.directory.absolutePath,
                storageLabel = target.label,
                localUri = Uri.fromFile(finalFile).toString(),
                status = if (scheduledAt > 0L) OfflineStatus.WAITING_SCHEDULE else OfflineStatus.QUEUED,
                errorMessage = if (scheduledAt > 0L) "مجدول للتحميل الليلي." else null,
                queuePosition = queuePosition,
                scheduledAtEpochMs = scheduledAt,
            ).also {
                cache.add(it)
                writeStoredLocked()
            }
        }
        schedule()
        return EnqueueResult.Started(entry)
    }

    fun pause(downloadId: Long): List<OfflineDownload> {
        synchronized(lock) {
            mutateLocked(downloadId) { item ->
                if (item.status in ACTIVE_STATUSES) {
                    item.copy(
                        status = OfflineStatus.PAUSED,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        errorMessage = null,
                    )
                } else {
                    item
                }
            }
        }
        calls.remove(downloadId)?.cancel()
        jobs.remove(downloadId)?.cancel()
        return downloads()
    }

    fun resume(downloadId: Long): Boolean {
        val resumable = synchronized(lock) {
            val item = cache.firstOrNull { it.downloadId == downloadId } ?: return@synchronized false
            if (item.status == OfflineStatus.COMPLETED) {
                return@synchronized false
            }
            mutateLocked(downloadId) {
                it.copy(
                    status = OfflineStatus.QUEUED,
                    bytesPerSecond = 0L,
                    etaSeconds = -1L,
                    scheduledAtEpochMs = 0L,
                    errorMessage = null,
                    integrityVerified = false,
                )
            }
            true
        }
        if (resumable) schedule()
        return resumable
    }

    fun remove(downloadId: Long): List<OfflineDownload> {
        calls.remove(downloadId)?.cancel()
        jobs.remove(downloadId)?.cancel()
        val removed = synchronized(lock) {
            val item = cache.firstOrNull { it.downloadId == downloadId }
            cache.removeAll { it.downloadId == downloadId }
            normalizeQueueLocked()
            writeStoredLocked()
            item
        }
        removed?.let(::deleteDownloadFiles)
        return downloads()
    }

    private fun schedule() {
        if (accountBoundarySuspended) return
        val metadata = accountSessionStore.metadata()
        if (
            metadata == null ||
            metadata.isExpired() ||
            authenticatedDownloadAccountId(
                session = AuthenticatedSessionRegistry.current(),
                metadata = metadata,
            ) != accountId
        ) {
            return
        }
        val limit = settings().concurrentDownloads
        val availableSlots = (limit - jobs.values.count { it.isActive }).coerceAtLeast(0)
        if (availableSlots == 0) return
        val now = System.currentTimeMillis()
        val networkAvailable = networkConstraintMessage() == null
        val candidates = synchronized(lock) {
            cache.asSequence()
                .filter { it.status in SCHEDULABLE_STATUSES }
                .filter { jobs[it.downloadId]?.isActive != true }
                .filter { item ->
                    decideDownloadAttempt(
                        item = item,
                        nowEpochMs = now,
                        networkAvailable = networkAvailable,
                        storageAvailable = storageTarget(item) != null,
                    ).canRun
                }
                .sortedWith(
                    compareByDescending<OfflineDownload> { it.priority }
                        .thenBy { it.queuePosition }
                        .thenBy { it.createdAtEpochMs },
                )
                .take(availableSlots)
                .toList()
        }
        candidates.forEach { item ->
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    runDownload(item.downloadId)
                } finally {
                    calls.remove(item.downloadId)
                    jobs.remove(item.downloadId, job)
                    schedule()
                }
            }
            if (jobs.putIfAbsent(item.downloadId, job) == null) {
                job.start()
            } else {
                job.cancel()
            }
        }
    }

    private suspend fun runDownload(downloadId: Long) {
        var attempt = item(downloadId)?.retryCount ?: 0
        while (currentCoroutineContext().isActive) {
            if (accountScopeStore.activeAccountId() != accountId) return
            val current = item(downloadId) ?: return
            if (current.status == OfflineStatus.PAUSED || current.status == OfflineStatus.COMPLETED) return

            val networkBlock = networkConstraintMessage()
            if (networkBlock != null) {
                update(
                    downloadId,
                    current.copy(
                        status = OfflineStatus.WAITING_NETWORK,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        errorMessage = networkBlock,
                    ),
                )
                return
            }
            if (storageTarget(current) == null) {
                update(
                    downloadId,
                    current.copy(
                        status = OfflineStatus.WAITING_STORAGE,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        errorMessage = "وحدة التخزين غير متصلة. اعد توصيلها وسيكمل التحميل.",
                    ),
                )
                return
            }

            try {
                performDownload(downloadId)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: InsufficientSpaceException) {
                fail(downloadId, error.message ?: "المساحة غير كافية لاكمال التحميل.")
                return
            } catch (error: StorageUnavailableException) {
                waitingForStorage(downloadId)
                return
            } catch (error: NetworkUnavailableException) {
                waitingForNetwork(downloadId)
                return
            } catch (error: PermanentDownloadException) {
                fail(downloadId, error.message ?: "تعذر تحميل الملف من الخادم.")
                return
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (finalizeCompletedFileAfterTransportError(downloadId)) return
                if (networkConstraintMessage() != null) {
                    waitingForNetwork(downloadId)
                    return
                }
                if (storageTarget(item(downloadId) ?: return) == null) {
                    waitingForStorage(downloadId)
                    return
                }
                attempt += 1
                if (attempt >= MAX_RETRIES) {
                    fail(downloadId, "تعذر استئناف التحميل بعد عدة محاولات. اضغط اعادة المحاولة.")
                    return
                }
                mutate(downloadId) {
                    it.copy(
                        status = OfflineStatus.QUEUED,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                        retryCount = attempt,
                        errorMessage = "انقطع الاتصال مؤقتا. سيكمل التحميل تلقائيا من اخر نقطة.",
                    )
                }
                delay((attempt * 1_500L).coerceAtMost(8_000L))
            }
        }
    }

    @Throws(IOException::class)
    private suspend fun performDownload(downloadId: Long) {
        val startItem = item(downloadId) ?: return
        val runtimeSources = downloadRuntimeSourceCandidates(
            record = startItem,
            expectedAccountId = accountId,
            session = AuthenticatedSessionRegistry.current(),
            metadata = accountSessionStore.metadata(),
        )
        if (runtimeSources.isEmpty()) {
            throw CancellationException("Authenticated download session changed")
        }
        mutate(downloadId) {
            it.copy(
                status = OfflineStatus.CHECKING,
                scheduledAtEpochMs = 0L,
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                errorMessage = null,
            )
        }

        val target = storageTarget(startItem) ?: throw StorageUnavailableException()
        if (!target.directory.exists() && !target.directory.mkdirs()) throw StorageUnavailableException()
        val fileName = startItem.fileName ?: buildFileName(startItem.title, startItem.streamId, startItem.extension)
        val finalFile = File(target.directory, fileName)
        val partFile = File(target.directory, "$fileName.part")

        if (!partFile.exists() && finalFile.exists() && startItem.status != OfflineStatus.COMPLETED) {
            runCatching { finalFile.renameTo(partFile) }
        }

        val probe = probe(downloadId, runtimeSources)
        var totalBytes = probe.totalBytes
        var existingBytes = partFile.takeIf(File::exists)?.length() ?: 0L

        if (totalBytes > 0L && finalFile.exists() && finalFile.length() == totalBytes) {
            markCompleted(downloadId, finalFile, totalBytes, probe.supportsRange)
            return
        }
        if (totalBytes > 0L && existingBytes > totalBytes) {
            partFile.delete()
            existingBytes = 0L
        }
        if (existingBytes > 0L && !probe.supportsRange) {
            partFile.delete()
            existingBytes = 0L
        }

        checkAvailableSpace(target.directory, totalBytes, existingBytes)
        mutate(downloadId) {
            it.copy(
                sourceCandidates = emptyList(),
                fileName = fileName,
                storagePath = target.directory.absolutePath,
                storageLabel = target.label,
                supportsRange = probe.supportsRange,
                totalBytes = totalBytes,
                bytesDownloaded = existingBytes,
                localUri = Uri.fromFile(finalFile).toString(),
                status = OfflineStatus.DOWNLOADING,
                errorMessage = null,
            )
        }

        var downloaded = existingBytes
        var lastReportedBytes = downloaded
        var lastReportedAt = System.nanoTime()
        var smoothedSpeed = item(downloadId)?.bytesPerSecond?.toDouble()?.coerceAtLeast(0.0) ?: 0.0

        while (totalBytes <= 0L || downloaded < totalBytes) {
            var response = executeDownloadCall(
                downloadId = downloadId,
                url = probe.url,
                offset = downloaded,
                useRange = probe.supportsRange,
                totalBytes = totalBytes,
            )
            if (downloaded > 0L && response.code == 416 && totalBytes > 0L && downloaded == totalBytes) {
                response.close()
                finalizePart(downloadId, partFile, finalFile, totalBytes, probe.supportsRange)
                return
            }
            if (downloaded > 0L && response.code == 200) {
                response.close()
                partFile.delete()
                downloaded = 0L
                lastReportedBytes = 0L
                checkAvailableSpace(target.directory, totalBytes, downloaded)
                mutate(downloadId) {
                    it.copy(
                        bytesDownloaded = 0L,
                        bytesPerSecond = 0L,
                        etaSeconds = -1L,
                    )
                }
                response = executeDownloadCall(
                    downloadId = downloadId,
                    url = probe.url,
                    offset = 0L,
                    useRange = false,
                    totalBytes = totalBytes,
                )
            }

            var bytesReadFromResponse = 0L
            response.use { activeResponse ->
                if (!activeResponse.isSuccessful) {
                    if (activeResponse.code in 400..499 && activeResponse.code !in setOf(408, 429)) {
                        throw PermanentDownloadException("الخادم رفض التحميل برمز ${activeResponse.code}.")
                    }
                    throw IOException("HTTP ${activeResponse.code}")
                }
                val body = activeResponse.body ?: throw IOException("Empty response body")
                val responseRangeStart = parseContentRangeStart(activeResponse.header("Content-Range"))
                if (activeResponse.code == 206 && responseRangeStart != downloaded) {
                    throw IOException(
                        "Missing or unexpected Content-Range start $responseRangeStart for offset $downloaded",
                    )
                }
                val responseTotal = parseTotalFromContentRange(activeResponse.header("Content-Range"))
                if (responseTotal > 0L && responseTotal != totalBytes) {
                    totalBytes = responseTotal
                    checkAvailableSpace(target.directory, totalBytes, downloaded)
                    mutate(downloadId) { it.copy(totalBytes = totalBytes) }
                } else if (totalBytes <= 0L) {
                    val responseLength = body.contentLength()
                    if (responseLength > 0L && activeResponse.code != 206) {
                        totalBytes = downloaded + responseLength
                        checkAvailableSpace(target.directory, totalBytes, downloaded)
                        mutate(downloadId) { it.copy(totalBytes = totalBytes) }
                    }
                }

                val declaredResponseBytes = body.contentLength().takeIf { it > 0L }
                val boundedRangeBytes = downloadByteRange(
                    offset = downloaded,
                    supportsRange = probe.supportsRange && activeResponse.code == 206,
                    totalBytes = totalBytes,
                )?.let { range -> range.last - range.first + 1L }
                val maximumResponseBytes = when {
                    declaredResponseBytes != null -> declaredResponseBytes
                    boundedRangeBytes != null -> boundedRangeBytes
                    totalBytes > 0L -> totalBytes - downloaded
                    else -> Long.MAX_VALUE
                }.coerceAtLeast(0L)

                FileOutputStream(partFile, downloaded > 0L).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (bytesReadFromResponse < maximumResponseBytes) {
                            ensureDownloadContextActive(currentCoroutineContext())
                            networkConstraintMessage()?.let { throw NetworkUnavailableException() }
                            if (!target.directory.exists()) throw StorageUnavailableException()
                            if (totalBytes > 0L && downloaded >= totalBytes) break

                            val responseRemaining = maximumResponseBytes - bytesReadFromResponse
                            val totalRemaining = if (totalBytes > 0L) {
                                totalBytes - downloaded
                            } else {
                                Long.MAX_VALUE
                            }
                            val bytesToRead = minOf(
                                buffer.size.toLong(),
                                responseRemaining,
                                totalRemaining,
                            ).toInt()
                            if (bytesToRead <= 0) break
                            val count = input.read(buffer, 0, bytesToRead)
                            if (count < 0) break
                            if (count == 0) {
                                throw IOException("Download response returned an empty read")
                            }
                            output.write(buffer, 0, count)
                            downloaded += count
                            bytesReadFromResponse += count

                            val now = System.nanoTime()
                            val elapsedNanos = now - lastReportedAt
                            if (
                                elapsedNanos >= PROGRESS_INTERVAL_NANOS ||
                                downloaded - lastReportedBytes >= PROGRESS_CHUNK_BYTES
                            ) {
                                val seconds = elapsedNanos / 1_000_000_000.0
                                val instantSpeed = if (seconds > 0.0) {
                                    (downloaded - lastReportedBytes) / seconds
                                } else {
                                    0.0
                                }
                                smoothedSpeed = if (smoothedSpeed <= 0.0) {
                                    instantSpeed
                                } else {
                                    (smoothedSpeed * 0.68) + (instantSpeed * 0.32)
                                }
                                val speed = smoothedSpeed.toLong().coerceAtLeast(0L)
                                val eta = if (totalBytes > downloaded && speed > 0L) {
                                    (totalBytes - downloaded) / speed
                                } else {
                                    -1L
                                }
                                mutate(downloadId) {
                                    it.copy(
                                        status = OfflineStatus.DOWNLOADING,
                                        bytesDownloaded = downloaded,
                                        totalBytes = totalBytes,
                                        bytesPerSecond = speed,
                                        etaSeconds = eta,
                                        errorMessage = null,
                                    )
                                }
                                lastReportedAt = now
                                lastReportedBytes = downloaded
                            }
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                if (bytesReadFromResponse <= 0L) {
                    throw IOException("Download response ended before the first byte")
                }
                val expectedResponseBytes = declaredResponseBytes ?: boundedRangeBytes
                if (
                    activeResponse.code == 206 &&
                    expectedResponseBytes != null &&
                    bytesReadFromResponse < expectedResponseBytes
                ) {
                    throw IOException(
                        "Partial response ended at $bytesReadFromResponse of $expectedResponseBytes bytes",
                    )
                }
                val speed = smoothedSpeed.toLong().coerceAtLeast(0L)
                mutate(downloadId) {
                    it.copy(
                        status = OfflineStatus.DOWNLOADING,
                        bytesDownloaded = downloaded,
                        totalBytes = totalBytes,
                        bytesPerSecond = speed,
                        etaSeconds = if (totalBytes > downloaded && speed > 0L) {
                            (totalBytes - downloaded) / speed
                        } else {
                            -1L
                        },
                        errorMessage = null,
                    )
                }
            }
            calls.remove(downloadId)

            if (!probe.supportsRange || totalBytes <= 0L) {
                if (totalBytes <= 0L) totalBytes = downloaded
                break
            }
        }

        mutate(downloadId) {
            it.copy(
                bytesDownloaded = downloaded,
                totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
            )
        }
        val expected = if (totalBytes > 0L) totalBytes else partFile.length()
        finalizePart(downloadId, partFile, finalFile, expected, probe.supportsRange)
    }

    private fun finalizeCompletedFileAfterTransportError(downloadId: Long): Boolean {
        val current = item(downloadId) ?: return false
        val expectedBytes = current.totalBytes.takeIf { it > 0L } ?: return false
        val target = storageTarget(current) ?: return false
        val fileName = current.fileName ?: return false
        val finalFile = File(target.directory, fileName)
        if (finalFile.exists() && finalFile.length() == expectedBytes) {
            markCompleted(downloadId, finalFile, expectedBytes, current.supportsRange ?: false)
            return true
        }
        val partFile = File(target.directory, "$fileName.part")
        if (!partFile.exists() || partFile.length() != expectedBytes) return false
        return runCatching {
            finalizePart(
                downloadId = downloadId,
                partFile = partFile,
                finalFile = finalFile,
                expectedBytes = expectedBytes,
                supportsRange = current.supportsRange ?: false,
            )
            true
        }.getOrDefault(false)
    }

    private fun executeDownloadCall(
        downloadId: Long,
        url: String,
        offset: Long,
        useRange: Boolean,
        totalBytes: Long,
    ): Response {
        val call = client.newCall(buildDownloadRequest(url, offset, useRange, totalBytes))
        calls[downloadId] = call
        return call.execute()
    }

    private fun probe(downloadId: Long, candidates: List<String>): Probe {
        var lastError: Throwable? = null
        candidates.forEach { candidate ->
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=0-0")
                    .build()
                val call = client.newCall(request)
                calls[downloadId] = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in 400..499 && response.code !in setOf(408, 429)) {
                            lastError = PermanentDownloadException("الخادم رفض رابط التحميل برمز ${response.code}.")
                            return@forEach
                        }
                        throw IOException("HTTP ${response.code}")
                    }
                    val contentRange = response.header("Content-Range")
                    val total = parseTotalFromContentRange(contentRange)
                        .takeIf { it > 0L }
                        ?: response.body?.contentLength()?.takeIf { it > 0L }
                        ?: -1L
                    val supportsRange = response.code == 206 ||
                        response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    return Probe(
                        url = response.request.url.toString(),
                        totalBytes = total,
                        supportsRange = supportsRange,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
            } finally {
                calls.remove(downloadId)
            }
        }
        when (lastError) {
            is PermanentDownloadException -> throw lastError as PermanentDownloadException
            is IOException -> throw lastError as IOException
            else -> throw IOException(lastError?.message ?: "تعذر فحص رابط التحميل.")
        }
    }

    private fun finalizePart(
        downloadId: Long,
        partFile: File,
        finalFile: File,
        expectedBytes: Long,
        supportsRange: Boolean,
    ) {
        if (!partFile.exists()) throw IOException("ملف التحميل المؤقت غير موجود.")
        if (expectedBytes > 0L && partFile.length() != expectedBytes) {
            throw IOException("حجم الملف غير مكتمل.")
        }
        if (finalFile.exists() && !finalFile.delete()) throw IOException("تعذر استبدال الملف القديم.")
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            if (!partFile.delete()) partFile.deleteOnExit()
        }
        val actualBytes = finalFile.length()
        if (expectedBytes > 0L && actualBytes != expectedBytes) {
            finalFile.delete()
            throw IOException("فشل التحقق من سلامة الملف.")
        }
        markCompleted(downloadId, finalFile, actualBytes, supportsRange)
    }

    private fun markCompleted(downloadId: Long, file: File, bytes: Long, supportsRange: Boolean) {
        mutate(downloadId) {
            it.copy(
                status = OfflineStatus.COMPLETED,
                bytesDownloaded = bytes,
                totalBytes = bytes,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                localUri = Uri.fromFile(file).toString(),
                supportsRange = supportsRange,
                errorMessage = null,
                retryCount = 0,
                integrityVerified = true,
            )
        }
    }

    private fun checkAvailableSpace(directory: File, totalBytes: Long, existingBytes: Long) {
        if (totalBytes <= 0L) return
        val remaining = (totalBytes - existingBytes).coerceAtLeast(0L)
        val safety = max(MINIMUM_SAFETY_BYTES, (totalBytes * 2L) / 100L)
        val required = remaining + safety
        val available = directory.usableSpace.coerceAtLeast(0L)
        if (available < required) {
            throw InsufficientSpaceException(
                "المساحة غير كافية. المطلوب ${formatBytes(required)} والمتاح ${formatBytes(available)}.",
            )
        }
    }

    private fun waitingForNetwork(downloadId: Long) {
        mutate(downloadId) {
            it.copy(
                status = OfflineStatus.WAITING_NETWORK,
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                errorMessage = networkConstraintMessage()
                    ?: "انقطع الاتصال. سيكمل التحميل تلقائيا عند عودة الشبكة.",
            )
        }
    }

    private fun waitingForStorage(downloadId: Long) {
        mutate(downloadId) {
            it.copy(
                status = OfflineStatus.WAITING_STORAGE,
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                errorMessage = "وحدة التخزين غير متصلة. اعد توصيلها وسيكمل التحميل.",
            )
        }
    }

    private fun fail(downloadId: Long, message: String) {
        mutate(downloadId) {
            it.copy(
                status = OfflineStatus.FAILED,
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                errorMessage = redactCredentialBearingUrl(message),
                integrityVerified = false,
            )
        }
    }

    private fun scheduledStartForNewDownload(): Long = when (settings().scheduleMode) {
        DownloadScheduleMode.NOW -> 0L
        DownloadScheduleMode.NIGHT -> nextNightStartEpochMs()
    }

    private fun nextNightStartEpochMs(now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (hour in NIGHT_START_HOUR until NIGHT_END_HOUR) return 0L
        calendar.set(Calendar.HOUR_OF_DAY, NIGHT_START_HOUR)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun networkConstraintMessage(): String? {
        val activeNetwork = connectivity.activeNetwork ?: return "لا يوجد اتصال بالشبكة."
        val capabilities = connectivity.getNetworkCapabilities(activeNetwork)
            ?: return "لا يوجد اتصال بالشبكة."
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "لا يوجد اتصال بالانترنت."
        }
        if (preferences.getBoolean(KEY_WIFI_ONLY, false) && connectivity.isActiveNetworkMetered) {
            return "التحميل مضبوط على واي فاي فقط."
        }
        return null
    }

    private fun selectedStorageTarget(): StorageTarget? {
        val targets = storageTargets()
        val preferred = preferences.getString(KEY_STORAGE_PATH, null)
        return targets.firstOrNull {
            it.directory.absolutePath == preferred || it.directory.parentFile?.absolutePath == preferred
        }
            ?: targets.firstOrNull { !it.removable }
            ?: targets.firstOrNull()
    }

    private fun storageTarget(item: OfflineDownload): StorageTarget? {
        val path = item.storagePath ?: return selectedStorageTarget()
        val directory = File(path)
        if (!directory.exists() || !directory.canWrite()) return null
        return StorageTarget(
            directory = directory,
            label = item.storageLabel,
            removable = runCatching { Environment.isExternalStorageRemovable(directory) }.getOrDefault(false),
        )
    }

    private fun storageTargets(): List<StorageTarget> = appContext
        .getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
        .filterNotNull()
        .mapIndexedNotNull { index, baseDirectory ->
            runCatching {
                val directory = File(
                    baseDirectory,
                    downloadAccountDirectoryName(accountId),
                )
                if (!directory.exists()) directory.mkdirs()
                val removable = Environment.isExternalStorageRemovable(directory)
                StorageTarget(
                    directory = directory,
                    label = when {
                        removable -> "USB / تخزين خارجي"
                        index == 0 -> "التخزين الداخلي"
                        else -> "وحدة تخزين اضافية"
                    },
                    removable = removable,
                )
            }.getOrNull()
        }
        .distinctBy { runCatching { it.directory.canonicalPath }.getOrDefault(it.directory.absolutePath) }

    private fun recoverInterruptedState(item: OfflineDownload): OfflineDownload {
        if (item.status == OfflineStatus.COMPLETED) return item
        if (item.status in ACTIVE_STATUSES || item.status == OfflineStatus.CHECKING) {
            return item.copy(
                status = OfflineStatus.QUEUED,
                sourceCandidates = emptyList(),
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                errorMessage = "سيتم استئناف التحميل من اخر نقطة بعد إعادة إنشاء رابط آمن.",
            )
        }
        return item.copy(sourceCandidates = emptyList())
    }

    private fun completedFileExists(item: OfflineDownload): Boolean = item.localUri?.let(::fileFromUri)?.exists() == true

    private fun deleteDownloadFiles(item: OfflineDownload) {
        item.localUri?.let(::fileFromUri)?.let { file ->
            runCatching { file.delete() }
            runCatching { File(file.parentFile, "${file.name}.part").delete() }
        }
        val storage = item.storagePath?.let(::File)
        val name = item.fileName
        if (storage != null && name != null) {
            runCatching { File(storage, name).delete() }
            runCatching { File(storage, "$name.part").delete() }
        }
    }

    private fun fileFromUri(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) Uri.parse(uri).path?.let(::File) else null
    }.getOrNull()

    private fun item(downloadId: Long): OfflineDownload? = synchronized(lock) {
        cache.firstOrNull { it.downloadId == downloadId }
    }

    private fun update(downloadId: Long, replacement: OfflineDownload) {
        synchronized(lock) {
            if (accountBoundarySuspended) return
            val index = cache.indexOfFirst { it.downloadId == downloadId }
            if (index >= 0) {
                cache[index] = replacement.copy(sourceCandidates = emptyList())
                writeStoredLocked()
            }
        }
    }

    private inline fun mutate(downloadId: Long, transform: (OfflineDownload) -> OfflineDownload) {
        synchronized(lock) {
            if (accountBoundarySuspended) return
            mutateLocked(downloadId, transform)
        }
    }

    private inline fun mutateLocked(downloadId: Long, transform: (OfflineDownload) -> OfflineDownload) {
        val index = cache.indexOfFirst { it.downloadId == downloadId }
        if (index < 0) return
        val updated = transform(cache[index]).copy(sourceCandidates = emptyList())
        if (updated != cache[index]) {
            cache[index] = updated
            writeStoredLocked()
        }
    }

    private fun normalizeQueueLocked() {
        cache = cache.sortedWith(
            compareByDescending<OfflineDownload> { it.priority }
                .thenBy { it.queuePosition }
                .thenBy { it.createdAtEpochMs },
        ).mapIndexed { index, item ->
            item.copy(queuePosition = index, sourceCandidates = emptyList())
        }.toMutableList()
    }

    private fun sortedSnapshotLocked(): List<OfflineDownload> = cache.sortedWith(
        compareBy<OfflineDownload> { statusOrder(it.status) }
            .thenByDescending { it.priority }
            .thenBy { it.queuePosition }
            .thenByDescending { it.createdAtEpochMs },
    )

    private fun statusOrder(status: OfflineStatus): Int = when (status) {
        OfflineStatus.DOWNLOADING, OfflineStatus.CHECKING -> 0
        OfflineStatus.QUEUED -> 1
        OfflineStatus.WAITING_SCHEDULE -> 2
        OfflineStatus.PAUSED, OfflineStatus.WAITING_NETWORK, OfflineStatus.WAITING_STORAGE -> 3
        OfflineStatus.FAILED -> 4
        OfflineStatus.COMPLETED -> 5
    }

    private fun nextUniqueIdLocked(): Long {
        var candidate = nextId.incrementAndGet()
        while (cache.any { it.downloadId == candidate }) candidate = nextId.incrementAndGet()
        return candidate
    }

    private fun readStored(): List<OfflineDownload> {
        val raw = preferences.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        val sanitized = sanitizePersistedDownloadJson(raw) ?: return emptyList()
        return runCatching {
            val array = JSONArray(sanitized)
            buildList {
                for (index in 0 until array.length()) {
                    val data = array.getJSONObject(index)
                    add(
                        OfflineDownload(
                            downloadId = data.getLong("downloadId"),
                            historyKey = data.getString("historyKey"),
                            title = data.getString("title"),
                            posterUrl = persistableExternalUrlOrNull(data.optNullableString("posterUrl")),
                            streamKind = data.getString("streamKind"),
                            streamId = data.getInt("streamId"),
                            extension = data.optString("extension", "mp4"),
                            seriesTitle = data.optNullableString("seriesTitle"),
                            season = data.optNullableInt("season"),
                            episodeNumber = data.optNullableInt("episodeNumber"),
                            sourceCandidates = emptyList(),
                            fileName = data.optNullableString("fileName"),
                            storagePath = data.optNullableString("storagePath"),
                            storageLabel = data.optString("storageLabel", "التخزين الداخلي"),
                            supportsRange = data.optNullableBoolean("supportsRange"),
                            status = runCatching {
                                OfflineStatus.valueOf(data.optString("status", OfflineStatus.QUEUED.name))
                            }.getOrDefault(OfflineStatus.QUEUED),
                            bytesDownloaded = data.optLong("bytesDownloaded", 0L).coerceAtLeast(0L),
                            totalBytes = data.optLong("totalBytes", -1L),
                            bytesPerSecond = data.optLong("bytesPerSecond", 0L).coerceAtLeast(0L),
                            etaSeconds = data.optLong("etaSeconds", -1L),
                            localUri = data.optNullableString("localUri"),
                            errorMessage = redactCredentialBearingUrl(data.optNullableString("errorMessage")),
                            retryCount = data.optInt("retryCount", 0).coerceAtLeast(0),
                            integrityVerified = data.optBoolean("integrityVerified", false),
                            priority = data.optInt("priority", 0),
                            queuePosition = data.optInt("queuePosition", index),
                            scheduledAtEpochMs = data.optLong("scheduledAtEpochMs", 0L),
                            createdAtEpochMs = data.optLong("createdAtEpochMs", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeStoredLocked(synchronous: Boolean = false) {
        val array = JSONArray()
        cache.forEach { item ->
            array.put(
                JSONObject()
                    .put("downloadId", item.downloadId)
                    .put("historyKey", item.historyKey)
                    .put("title", item.title)
                    .put("posterUrl", persistableExternalUrlOrNull(item.posterUrl) ?: JSONObject.NULL)
                    .put("streamKind", item.streamKind)
                    .put("streamId", item.streamId)
                    .put("extension", item.extension)
                    .put("seriesTitle", item.seriesTitle ?: JSONObject.NULL)
                    .put("season", item.season ?: JSONObject.NULL)
                    .put("episodeNumber", item.episodeNumber ?: JSONObject.NULL)
                    .put("fileName", item.fileName ?: JSONObject.NULL)
                    .put("storagePath", item.storagePath ?: JSONObject.NULL)
                    .put("storageLabel", item.storageLabel)
                    .put("supportsRange", item.supportsRange ?: JSONObject.NULL)
                    .put("status", item.status.name)
                    .put("bytesDownloaded", item.bytesDownloaded)
                    .put("totalBytes", item.totalBytes)
                    .put("bytesPerSecond", item.bytesPerSecond)
                    .put("etaSeconds", item.etaSeconds)
                    .put("localUri", item.localUri ?: JSONObject.NULL)
                    .put("errorMessage", redactCredentialBearingUrl(item.errorMessage) ?: JSONObject.NULL)
                    .put("retryCount", item.retryCount)
                    .put("integrityVerified", item.integrityVerified)
                    .put("priority", item.priority)
                    .put("queuePosition", item.queuePosition)
                    .put("scheduledAtEpochMs", item.scheduledAtEpochMs)
                    .put("createdAtEpochMs", item.createdAtEpochMs),
            )
        }
        val editor = preferences.edit().putString(KEY_DOWNLOADS, array.toString())
        if (synchronous) editor.commit() else editor.apply()
    }

    private fun parseTotalFromContentRange(value: String?): Long {
        if (value.isNullOrBlank()) return -1L
        return value.substringAfterLast('/', "").toLongOrNull() ?: -1L
    }

    private fun parseContentRangeStart(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return Regex("""bytes\s+(\d+)-""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private fun safeExtension(raw: String?): String = raw.orEmpty()
        .trim()
        .lowercase()
        .filter { it.isLetterOrDigit() }
        .take(8)
        .ifBlank { "mp4" }

    private fun buildFileName(title: String, streamId: Int, extension: String): String {
        val safeTitle = title
            .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(96)
            .ifBlank { "hulk_content" }
        return "${safeTitle}_$streamId.$extension"
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
    }

    sealed interface EnqueueResult {
        data class Started(val item: OfflineDownload) : EnqueueResult
        data class AlreadyExists(val item: OfflineDownload) : EnqueueResult
        data class Failed(val message: String) : EnqueueResult
    }

    private data class StorageTarget(
        val directory: File,
        val label: String,
        val removable: Boolean,
    )

    private data class Probe(
        val url: String,
        val totalBytes: Long,
        val supportsRange: Boolean,
    )

    private class InsufficientSpaceException(message: String) : IOException(message)
    private class StorageUnavailableException : IOException()
    private class NetworkUnavailableException : IOException()
    private class PermanentDownloadException(message: String) : IOException(message)

    private companion object {
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_SCHEDULE_MODE = "schedule_mode"
        const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
        const val KEY_STORAGE_PATH = "storage_path"
        const val DEFAULT_CONCURRENT_DOWNLOADS = 1
        const val MAX_CONCURRENT_DOWNLOADS = 3
        const val MAX_RETRIES = 4
        const val BUFFER_SIZE = 128 * 1024
        const val NIGHT_START_HOUR = 2
        const val NIGHT_END_HOUR = 6
        const val PROGRESS_INTERVAL_NANOS = 500_000_000L
        const val PROGRESS_CHUNK_BYTES = 512 * 1024L
        const val MINIMUM_START_SPACE_BYTES = 64L * 1024L * 1024L
        const val MINIMUM_SAFETY_BYTES = 96L * 1024L * 1024L
        const val USER_AGENT = DOWNLOAD_USER_AGENT

        val ACTIVE_STATUSES = setOf(
            OfflineStatus.QUEUED,
            OfflineStatus.CHECKING,
            OfflineStatus.DOWNLOADING,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        )
        val SCHEDULABLE_STATUSES = setOf(
            OfflineStatus.QUEUED,
            OfflineStatus.WAITING_SCHEDULE,
            OfflineStatus.WAITING_NETWORK,
            OfflineStatus.WAITING_STORAGE,
        )
    }
}

internal const val DOWNLOAD_STALL_TIMEOUT_SECONDS = 30L
internal const val DOWNLOAD_USER_AGENT = "HULK-SA-Android/0.9.3"
internal const val DOWNLOAD_RANGE_CHUNK_BYTES = 4L * 1024L * 1024L

internal fun suspendDownloadsForAccountBoundary(
    records: List<OfflineDownload>,
): List<OfflineDownload> = records.map { item ->
    if (
        item.status == OfflineStatus.QUEUED ||
        item.status == OfflineStatus.CHECKING ||
        item.status == OfflineStatus.DOWNLOADING ||
        item.status == OfflineStatus.WAITING_SCHEDULE ||
        item.status == OfflineStatus.WAITING_NETWORK ||
        item.status == OfflineStatus.WAITING_STORAGE
    ) {
        item.copy(
            status = OfflineStatus.PAUSED,
            sourceCandidates = emptyList(),
            bytesPerSecond = 0L,
            etaSeconds = -1L,
        )
    } else {
        item.copy(sourceCandidates = emptyList())
    }
}

internal fun ensureDownloadContextActive(context: CoroutineContext) {
    context.ensureActive()
}

internal fun downloadByteRange(
    offset: Long,
    supportsRange: Boolean,
    totalBytes: Long = -1L,
    chunkBytes: Long = DOWNLOAD_RANGE_CHUNK_BYTES,
): LongRange? {
    if (!supportsRange || chunkBytes <= 0L) return null
    val start = offset.coerceAtLeast(0L)
    if (totalBytes > 0L && start >= totalBytes) return null
    val unboundedEnd = if (start > Long.MAX_VALUE - (chunkBytes - 1L)) {
        Long.MAX_VALUE
    } else {
        start + chunkBytes - 1L
    }
    val end = if (totalBytes > 0L) {
        minOf(unboundedEnd, totalBytes - 1L)
    } else {
        unboundedEnd
    }
    return start..end
}

internal fun downloadRangeHeader(
    offset: Long,
    supportsRange: Boolean,
    totalBytes: Long = -1L,
    chunkBytes: Long = DOWNLOAD_RANGE_CHUNK_BYTES,
): String? = downloadByteRange(offset, supportsRange, totalBytes, chunkBytes)
    ?.let { range -> "bytes=${range.first}-${range.last}" }

internal fun buildDownloadRequest(
    url: String,
    offset: Long,
    supportsRange: Boolean,
    totalBytes: Long = -1L,
): Request = Request.Builder()
    .url(url)
    .get()
    .header("User-Agent", DOWNLOAD_USER_AGENT)
    .header("Accept-Encoding", "identity")
    .apply {
        downloadRangeHeader(offset, supportsRange, totalBytes)?.let { header("Range", it) }
    }
    .build()

private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name)) null else optInt(name)

private fun JSONObject.optNullableBoolean(name: String): Boolean? = if (isNull(name)) null else optBoolean(name)

package sa.hulksa.player.data

import android.content.Context
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val accountSessionStore = AccountSessionStore(appContext)
    private val profileStore = ProfileStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationMutex = Mutex()
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
    private var initialized = false
    private var cache = mutableListOf<OfflineDownload>()
    private var quarantine = emptyList<QuarantinedDownload>()

    suspend fun initialize() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            initializationMutex.withLock {
                if (!initialized) {
                    val raw = runCatching { preferences.getString(KEY_DOWNLOADS, null) }.getOrNull()
                    val snapshot = DownloadSchemaCodec.decode(raw)
                    synchronized(lock) {
                        cache = snapshot.records.map(::recoverInterruptedState).toMutableList()
                        quarantine = snapshot.quarantined
                        normalizeQueueLocked()
                        initialized = if (snapshot.rewriteAllowed) {
                            writeStoredLocked(commit = true)
                        } else {
                            true
                        }
                        if (!initialized) {
                            cache.clear()
                            quarantine = emptyList()
                        }
                    }
                }
            }
        }
        schedule()
    }

    fun downloads(): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val activeAccountId = activeAuthenticatedAccountId()
        val snapshot = synchronized(lock) {
            var changed = false
            cache = cache.map { item ->
                if (
                    item.accountId == activeAccountId &&
                    item.status == OfflineStatus.COMPLETED &&
                    !completedFileExists(item)
                ) {
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

    fun settings(): DownloadSettings {
        val scheduleMode = runCatching {
            DownloadScheduleMode.valueOf(
                preferences.getString(KEY_SCHEDULE_MODE, DownloadScheduleMode.NOW.name)
                    ?: DownloadScheduleMode.NOW.name,
            )
        }.getOrDefault(DownloadScheduleMode.NOW)
        val concurrentDownloads = runCatching {
            preferences.getInt(KEY_CONCURRENT_DOWNLOADS, DEFAULT_CONCURRENT_DOWNLOADS)
        }.getOrDefault(DEFAULT_CONCURRENT_DOWNLOADS)
        return DownloadSettings(
            wifiOnly = storedWifiOnly(),
            scheduleMode = scheduleMode,
            concurrentDownloads = concurrentDownloads.coerceIn(1, MAX_CONCURRENT_DOWNLOADS),
        )
    }

    fun setWifiOnly(enabled: Boolean): DownloadSettings {
        preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        schedule()
        return settings()
    }

    fun setScheduleMode(mode: DownloadScheduleMode, accountId: String): DownloadSettings {
        val normalizedAccountId = normalizedDownloadAccountId(accountId) ?: return settings()
        preferences.edit().putString(KEY_SCHEDULE_MODE, mode.name).apply()
        val scheduledAt = if (mode == DownloadScheduleMode.NIGHT) nextNightStartEpochMs() else 0L
        synchronized(lock) {
            cache = cache.map { item ->
                if (item.accountId != normalizedAccountId) return@map item
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
            normalizeQueueLocked(
                cache.asSequence()
                    .filter { it.accountId == normalizedAccountId }
                    .map { it.owner() }
                    .toSet(),
            )
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

    fun cyclePriority(downloadId: Long, owner: DownloadOwner): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val normalizedOwner = owner.normalizedOrNull() ?: return downloads()
        val owned = synchronized(lock) {
            if (cache.none { it.downloadId == downloadId && it.isOwnedBy(normalizedOwner) }) {
                return@synchronized false
            }
            mutateOwnedLocked(downloadId, normalizedOwner) { item ->
                if (item.status == OfflineStatus.COMPLETED) item else item.copy(
                    priority = when (item.priority) {
                        1 -> -1
                        -1 -> 0
                        else -> 1
                    },
                )
            }
            normalizeQueueLocked(setOf(normalizedOwner))
            writeStoredLocked()
            true
        }
        if (!owned) return downloads()
        schedule()
        return downloads()
    }

    fun enqueue(
        owner: DownloadOwner,
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): EnqueueResult {
        if (!initialized) return EnqueueResult.Failed("يتم تجهيز التحميلات المحلية. حاول مرة اخرى بعد لحظات.")
        val normalizedOwner = owner.normalizedOrNull()
            ?: return EnqueueResult.Failed("تعذر تحديد مالك التحميل بأمان.")
        require(!request.isLive) { "لا يمكن تحميل البث المباشر." }
        val historyKey = normalizedDownloadHistoryKey(request.historyKey)
            ?: return EnqueueResult.Failed("تعذر تحديد هوية المحتوى بأمان.")
        val sources = request.candidates.map(String::trim).filter(String::isNotBlank).distinct()
        if (sources.isEmpty()) return EnqueueResult.Failed("لا يوجد رابط صالح للتحميل.")

        val baseTarget = selectedStorageTarget()
            ?: return EnqueueResult.Failed("مساحة التخزين غير متاحة على هذا الجهاز.")
        val target = ownedStorageTarget(baseTarget, normalizedOwner)
        if (!target.directory.exists() && !target.directory.mkdirs()) {
            return EnqueueResult.Failed("تعذر تجهيز مجلد التحميل.")
        }
        if (target.directory.usableSpace < MINIMUM_START_SPACE_BYTES) {
            return EnqueueResult.Failed("المساحة المتاحة قليلة جدا. وفر مساحة ثم حاول مرة اخرى.")
        }

        val extension = safeExtension(request.extension)
        val fileName = buildFileName(
            title = request.title,
            streamId = request.streamId,
            extension = extension,
            historyKey = historyKey,
        )
        val finalFile = File(target.directory, fileName)

        val entry = synchronized(lock) {
            cache.firstOrNull {
                it.isOwnedBy(normalizedOwner) && it.historyKey == historyKey
            }?.let {
                return EnqueueResult.AlreadyExists(it)
            }
            val id = nextUniqueIdLocked()
            val queuePosition = cache
                .asSequence()
                .filter { it.isOwnedBy(normalizedOwner) }
                .maxOfOrNull(OfflineDownload::queuePosition)
                ?.plus(1)
                ?: 0
            val scheduledAt = scheduledStartForNewDownload()
            OfflineDownload(
                downloadId = id,
                accountId = normalizedOwner.accountId,
                profileId = normalizedOwner.profileId,
                historyKey = historyKey,
                title = request.title,
                posterUrl = request.posterUrl,
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = extension,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
                sourceCandidates = sources,
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

    fun pause(downloadId: Long, owner: DownloadOwner): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val normalizedOwner = owner.normalizedOrNull() ?: return downloads()
        var owned = false
        synchronized(lock) {
            owned = cache.any { it.downloadId == downloadId && it.isOwnedBy(normalizedOwner) }
            mutateOwnedLocked(downloadId, normalizedOwner) { item ->
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
        if (!owned) return downloads()
        calls.remove(downloadId)?.cancel()
        jobs.remove(downloadId)?.cancel()
        return downloads()
    }

    fun resume(downloadId: Long, owner: DownloadOwner): Boolean {
        if (!initialized) return false
        val normalizedOwner = owner.normalizedOrNull() ?: return false
        val resumable = synchronized(lock) {
            val item = cache.firstOrNull {
                it.downloadId == downloadId && it.isOwnedBy(normalizedOwner)
            } ?: return@synchronized false
            if (item.status == OfflineStatus.COMPLETED || item.sourceCandidates.isEmpty()) {
                return@synchronized false
            }
            mutateOwnedLocked(downloadId, normalizedOwner) {
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

    fun remove(downloadId: Long, owner: DownloadOwner): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val normalizedOwner = owner.normalizedOrNull() ?: return downloads()
        val removed = synchronized(lock) {
            val item = cache.firstOrNull {
                it.downloadId == downloadId && it.isOwnedBy(normalizedOwner)
            } ?: return@synchronized null
            cache.removeAll { it.downloadId == downloadId && it.isOwnedBy(normalizedOwner) }
            normalizeQueueLocked(setOf(normalizedOwner))
            writeStoredLocked()
            item
        }
        if (removed == null) return downloads()
        calls.remove(downloadId)?.cancel()
        jobs.remove(downloadId)?.cancel()
        deleteDownloadFiles(removed)
        return downloads()
    }

    fun owns(downloadId: Long, owner: DownloadOwner): Boolean {
        if (!initialized) return false
        val normalizedOwner = owner.normalizedOrNull() ?: return false
        return synchronized(lock) {
            cache.any { it.downloadId == downloadId && it.isOwnedBy(normalizedOwner) }
        }
    }

    fun playableLocalUri(downloadId: Long, owner: DownloadOwner): String? {
        if (!initialized) return null
        val normalizedOwner = owner.normalizedOrNull() ?: return null
        val record = synchronized(lock) {
            cache.firstOrNull {
                it.downloadId == downloadId &&
                    it.isOwnedBy(normalizedOwner) &&
                    it.status == OfflineStatus.COMPLETED
            }
        } ?: return null
        val file = ownedFile(record)?.takeIf(File::exists) ?: return null
        return Uri.fromFile(file).toString()
    }

    fun record(downloadId: Long): OfflineDownload? {
        if (!initialized) return null
        return item(downloadId)
    }

    fun recordsForAccount(accountId: String): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val normalized = normalizedDownloadAccountId(accountId) ?: return emptyList()
        return synchronized(lock) { cache.filter { it.accountId == normalized } }
    }

    fun prepareForAuthenticatedOwner(
        downloadId: Long,
        owner: DownloadOwner,
        refreshedSources: List<String>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!initialized) return false
        val normalizedOwner = owner.normalizedOrNull() ?: return false
        if (activeAuthenticatedAccountId() != normalizedOwner.accountId) return false
        val prepared = synchronized(lock) {
            val index = cache.indexOfFirst {
                it.downloadId == downloadId && it.isOwnedBy(normalizedOwner)
            }
            if (index < 0) return@synchronized false
            val replacement = prepareDownloadForAuthenticatedOwner(
                item = cache[index],
                owner = normalizedOwner,
                refreshedSources = refreshedSources,
                nowEpochMs = nowEpochMs,
            ) ?: return@synchronized false
            cache[index] = replacement
            writeStoredLocked()
            true
        }
        if (prepared) schedule()
        return prepared
    }

    fun suspendForInactiveOwner(downloadId: Long, owner: DownloadOwner) {
        if (!initialized) return
        val normalizedOwner = owner.normalizedOrNull() ?: return
        var changed = false
        synchronized(lock) {
            val index = cache.indexOfFirst {
                it.downloadId == downloadId && it.isOwnedBy(normalizedOwner)
            }
            if (index < 0) return@synchronized
            val replacement = suspendDownloadForAccountLogout(cache[index], normalizedOwner.accountId)
            if (replacement != cache[index]) {
                cache[index] = replacement
                writeStoredLocked()
                changed = true
            }
        }
        if (changed) {
            calls.remove(downloadId)?.cancel()
            jobs.remove(downloadId)?.cancel()
        }
    }

    fun suspendAccount(accountId: String) {
        if (!initialized) return
        val normalized = normalizedDownloadAccountId(accountId) ?: return
        val affectedIds = mutableListOf<Long>()
        synchronized(lock) {
            cache = cache.map { item ->
                val replacement = suspendDownloadForAccountLogout(item, normalized)
                if (replacement != item) affectedIds += item.downloadId
                replacement
            }.toMutableList()
            if (affectedIds.isNotEmpty()) writeStoredLocked()
        }
        affectedIds.forEach { downloadId ->
            calls.remove(downloadId)?.cancel()
            jobs.remove(downloadId)?.cancel()
        }
    }

    fun suspendAccountsExcept(activeAccountId: String) {
        if (!initialized) return
        val normalizedActive = normalizedDownloadAccountId(activeAccountId) ?: return
        val affectedIds = mutableListOf<Long>()
        synchronized(lock) {
            val replacements = suspendInactiveAccountDownloads(cache, normalizedActive)
            cache.zip(replacements).forEach { (item, replacement) ->
                if (replacement != item) affectedIds += item.downloadId
            }
            cache = replacements.toMutableList()
            if (affectedIds.isNotEmpty()) writeStoredLocked()
        }
        affectedIds.forEach { downloadId ->
            calls.remove(downloadId)?.cancel()
            jobs.remove(downloadId)?.cancel()
        }
    }

    fun removeProfile(owner: DownloadOwner): List<OfflineDownload> {
        if (!initialized) return emptyList()
        val normalizedOwner = owner.normalizedOrNull() ?: return downloads()
        val deletion = synchronized(lock) {
            partitionDownloadsForProfileDeletion(cache, normalizedOwner).also { result ->
                if (result.removed.isNotEmpty()) {
                    cache = result.retained.toMutableList()
                    writeStoredLocked()
                }
            }
        }
        deletion.removed.forEach { removed ->
            calls.remove(removed.downloadId)?.cancel()
            jobs.remove(removed.downloadId)?.cancel()
            deleteDownloadFiles(removed)
        }
        return downloads()
    }

    private fun schedule() {
        if (!initialized) return
        val activeAccountId = activeAuthenticatedAccountId() ?: return
        val activeProfileIds = profileStore.profiles().mapTo(mutableSetOf()) { it.id }
        val limit = settings().concurrentDownloads
        val availableSlots = (limit - jobs.values.count { it.isActive }).coerceAtLeast(0)
        if (availableSlots == 0) return
        val now = System.currentTimeMillis()
        val networkAvailable = networkConstraintMessage() == null
        val candidates = synchronized(lock) {
            cache.asSequence()
                .filter { it.status in SCHEDULABLE_STATUSES }
                .filter { it.accountId == activeAccountId && it.sourceCandidates.isNotEmpty() }
                .filter { it.profileId in activeProfileIds }
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
            val current = item(downloadId) ?: return
            if (current.status == OfflineStatus.PAUSED || current.status == OfflineStatus.COMPLETED) return
            if (!hasActiveAuthenticatedOwner(current)) {
                suspendForInactiveOwner(downloadId, current.owner())
                return
            }

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
            } catch (_: InactiveDownloadOwnerException) {
                val owner = item(downloadId)?.owner() ?: return
                suspendForInactiveOwner(downloadId, owner)
                return
            } catch (error: PermanentDownloadException) {
                fail(downloadId, error.message ?: "تعذر تحميل الملف من الخادم.")
                return
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val ownedItem = item(downloadId) ?: return
                if (!hasActiveAuthenticatedOwner(ownedItem)) {
                    suspendForInactiveOwner(downloadId, ownedItem.owner())
                    return
                }
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
        ensureActiveAuthenticatedOwner(startItem)
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
        val fileName = startItem.fileName ?: buildFileName(
            title = startItem.title,
            streamId = startItem.streamId,
            extension = startItem.extension,
            historyKey = startItem.historyKey,
        )
        val finalFile = File(target.directory, fileName)
        val partFile = File(target.directory, "$fileName.part")

        if (!partFile.exists() && finalFile.exists() && startItem.status != OfflineStatus.COMPLETED) {
            runCatching { finalFile.renameTo(partFile) }
        }

        val probe = probe(downloadId, startItem.sourceCandidates)
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
                sourceCandidates = listOf(probe.url) + it.sourceCandidates.filterNot { source -> source == probe.url },
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
            item(downloadId)?.let(::ensureActiveAuthenticatedOwner)
                ?: throw InactiveDownloadOwnerException()
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
                            item(downloadId)?.let(::ensureActiveAuthenticatedOwner)
                                ?: throw InactiveDownloadOwnerException()
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
                item(downloadId)?.let(::ensureActiveAuthenticatedOwner)
                    ?: throw InactiveDownloadOwnerException()
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
                if (error is InactiveDownloadOwnerException) throw error
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
                sourceCandidates = emptyList(),
                supportsRange = supportsRange,
                errorMessage = null,
                retryCount = 0,
                integrityVerified = true,
                resumeOnOwnerAuthentication = false,
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
                errorMessage = message,
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
        if (storedWifiOnly() && connectivity.isActiveNetworkMetered) {
            return "التحميل مضبوط على واي فاي فقط."
        }
        return null
    }

    private fun selectedStorageTarget(): StorageTarget? {
        val targets = storageTargets()
        val preferred = runCatching { preferences.getString(KEY_STORAGE_PATH, null) }.getOrNull()
        return targets.firstOrNull { it.directory.absolutePath == preferred }
            ?: targets.firstOrNull { !it.removable }
            ?: targets.firstOrNull()
    }

    private fun storageTarget(item: OfflineDownload): StorageTarget? {
        val directory = validatedOwnedStorageDirectory(item) ?: return null
        if (!directory.exists() || !directory.canWrite()) return null
        return StorageTarget(
            directory = directory,
            label = item.storageLabel,
            removable = runCatching { Environment.isExternalStorageRemovable(directory) }.getOrDefault(false),
        )
    }

    private fun ownedStorageTarget(base: StorageTarget, owner: DownloadOwner): StorageTarget = StorageTarget(
        directory = File(
            File(base.directory, OWNER_STORAGE_DIRECTORY),
            downloadOwnerStorageKey(owner),
        ),
        label = base.label,
        removable = base.removable,
    )

    private fun validatedOwnedStorageDirectory(item: OfflineDownload): File? {
        val owner = item.owner().normalizedOrNull() ?: return null
        val storedPath = item.storagePath ?: return null
        val storedCanonical = runCatching { File(storedPath).canonicalFile }.getOrNull() ?: return null
        return storageTargets()
            .asSequence()
            .map { ownedStorageTarget(it, owner).directory }
            .firstOrNull { expected ->
                runCatching { expected.canonicalFile == storedCanonical }.getOrDefault(false)
            }
    }

    private fun storageTargets(): List<StorageTarget> = appContext
        .getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
        .filterNotNull()
        .mapIndexedNotNull { index, directory ->
            runCatching {
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

    private fun recoverInterruptedState(item: OfflineDownload): OfflineDownload =
        recoverDownloadAfterProcessDeath(item, System.currentTimeMillis())

    private fun completedFileExists(item: OfflineDownload): Boolean = ownedFile(item)?.exists() == true

    private fun deleteDownloadFiles(item: OfflineDownload) {
        val file = ownedFile(item) ?: return
        runCatching { file.delete() }
        runCatching { File(file.parentFile, "${file.name}.part").delete() }
    }

    private fun ownedFile(item: OfflineDownload): File? {
        val directory = validatedOwnedStorageDirectory(item) ?: return null
        return resolveOwnedDownloadFile(directory, item.fileName, item.localUri)
    }

    private fun item(downloadId: Long): OfflineDownload? = synchronized(lock) {
        cache.firstOrNull { it.downloadId == downloadId }
    }

    private fun activeAuthenticatedAccountId(): String? {
        val metadata = accountSessionStore.metadata() ?: return null
        if (metadata.isExpired()) return null
        val session = AuthenticatedSessionRegistry.current() ?: return null
        if (metadata.username != session.credentials.username.trim()) return null
        if (
            metadata.portalBaseUrl.trim().trimEnd('/') !=
            session.portal.baseUrl.trim().trimEnd('/')
        ) {
            return null
        }
        return normalizedDownloadAccountId(metadata.accountId)
    }

    private fun hasActiveAuthenticatedOwner(item: OfflineDownload): Boolean =
        activeAuthenticatedAccountId() == item.accountId

    private fun ensureActiveAuthenticatedOwner(item: OfflineDownload) {
        if (!hasActiveAuthenticatedOwner(item)) throw InactiveDownloadOwnerException()
    }

    private fun update(downloadId: Long, replacement: OfflineDownload) {
        synchronized(lock) {
            val index = cache.indexOfFirst { it.downloadId == downloadId }
            if (index >= 0) {
                cache[index] = replacement
                writeStoredLocked()
            }
        }
    }

    private inline fun mutate(downloadId: Long, transform: (OfflineDownload) -> OfflineDownload) {
        synchronized(lock) { mutateLocked(downloadId, transform) }
    }

    private inline fun mutateLocked(downloadId: Long, transform: (OfflineDownload) -> OfflineDownload) {
        val index = cache.indexOfFirst { it.downloadId == downloadId }
        if (index < 0) return
        val updated = transform(cache[index])
        if (updated != cache[index]) {
            cache[index] = updated
            writeStoredLocked()
        }
    }

    private inline fun mutateOwnedLocked(
        downloadId: Long,
        owner: DownloadOwner,
        transform: (OfflineDownload) -> OfflineDownload,
    ) {
        val index = cache.indexOfFirst {
            it.downloadId == downloadId && it.isOwnedBy(owner)
        }
        if (index < 0) return
        val updated = transform(cache[index])
        if (updated != cache[index]) {
            cache[index] = updated
            writeStoredLocked()
        }
    }

    private fun normalizeQueueLocked(owners: Set<DownloadOwner>? = null) {
        cache = normalizeOwnedDownloadQueues(cache, owners).toMutableList()
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

    private fun storedWifiOnly(): Boolean =
        runCatching { preferences.getBoolean(KEY_WIFI_ONLY, false) }.getOrDefault(false)

    private fun writeStoredLocked(commit: Boolean = false): Boolean {
        val encoded = DownloadSchemaCodec.encode(cache, quarantine)
        val editor = preferences.edit().putString(KEY_DOWNLOADS, encoded)
        return if (commit) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
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

    private fun buildFileName(
        title: String,
        streamId: Int,
        extension: String,
        historyKey: String,
    ): String {
        val safeTitle = title
            .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(96)
            .ifBlank { "hulk_content" }
        return "${safeTitle}_${streamId}_${downloadHistoryFileKey(historyKey)}.$extension"
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
    private class InactiveDownloadOwnerException : IOException()
    private class PermanentDownloadException(message: String) : IOException(message)

    private companion object {
        const val PREFERENCES_NAME = "hulk_downloads"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_SCHEDULE_MODE = "schedule_mode"
        const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
        const val KEY_STORAGE_PATH = "storage_path"
        const val OWNER_STORAGE_DIRECTORY = ".hulk-owned-downloads-v2"
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

package sa.hulksa.player.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.security.persistableExternalUrlOrNull
import sa.hulksa.player.security.redactCredentialBearingUrl
import java.util.concurrent.ConcurrentHashMap

internal enum class DownloadIndexState {
    MISSING,
    MISSING_ESTABLISHED,
    VALID,
    PARTIAL_DAMAGE,
    UNREADABLE,
    STORAGE_FAILURE,
}

internal fun DownloadIndexState.allowsIndexWrite(): Boolean = when (this) {
    DownloadIndexState.MISSING,
    DownloadIndexState.VALID,
    DownloadIndexState.PARTIAL_DAMAGE,
    -> true

    DownloadIndexState.MISSING_ESTABLISHED,
    DownloadIndexState.UNREADABLE,
    DownloadIndexState.STORAGE_FAILURE,
    -> false
}

internal data class DownloadIndexRead(
    val records: List<OfflineDownload>,
    val state: DownloadIndexState,
    val safeRawDocument: String?,
    val retainedProgressDownloadIds: Set<Long>,
)

private data class StoredDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
)

/**
 * Owns the durable download index document.
 *
 * A structurally valid collection is decoded record-by-record so one damaged record cannot hide
 * healthy downloads. An unreadable document is never rewritten: no startup, maintenance or later
 * mutation is allowed to turn it into an authoritative empty index.
 */
internal object DownloadPersistenceMutationGate {
    private val lock = Any()
    private val inFlight = mutableSetOf<String>()
    private val generationByAccount = mutableMapOf<String, Long>()
    private val pendingPublished = mutableMapOf<String, () -> Unit>()

    fun begin(accountId: String) {
        synchronized(lock) {
            inFlight += accountId
            generationByAccount[accountId] = (generationByAccount[accountId] ?: 0L) + 1L
        }
    }

    fun end(accountId: String) {
        val publishedAction = synchronized(lock) {
            if (!inFlight.remove(accountId)) return
            generationByAccount[accountId] = (generationByAccount[accountId] ?: 0L) + 1L
            pendingPublished.remove(accountId)
        }
        // Callbacks are intentionally invoked outside the gate lock.
        publishedAction?.invoke()
    }

    fun generation(accountId: String): Long = synchronized(lock) {
        generationByAccount[accountId] ?: 0L
    }

    fun isInFlight(accountId: String): Boolean = synchronized(lock) {
        accountId in inFlight
    }

    /**
     * Registers a post-publication retry atomically with completion observation.
     * If publication is already complete, the retry is invoked immediately instead of
     * being stored for an end() that will never occur.
     */
    fun whenPublished(accountId: String, action: () -> Unit) {
        val invokeNow = synchronized(lock) {
            if (accountId in inFlight) {
                pendingPublished[accountId] = action
                false
            } else {
                true
            }
        }
        if (invokeNow) action()
    }
}

internal class DownloadIndexStore(
    private val preferences: SharedPreferences,
    private val accountId: String? = null,
) {
    private var lastRead: DownloadIndexRead = readInternal()
    private val retainedProgressDownloadIds =
        lastRead.retainedProgressDownloadIds.toMutableSet()

    fun read(): DownloadIndexRead {
        val read = readInternal()
        retainedProgressDownloadIds += read.retainedProgressDownloadIds
        lastRead = read
        return read
    }

    fun write(
        records: List<OfflineDownload>,
        schedulingChanged: Boolean,
        synchronous: Boolean,
    ): Boolean {
        val read = lastRead
        if (!read.state.allowsIndexWrite()) return false
        if (read.state == DownloadIndexState.PARTIAL_DAMAGE) {
            val raw = read.safeRawDocument ?: return false
            if (!preserveDamagedPersistedDocument(preferences, KEY_DOWNLOADS, raw)) {
                return false
            }
        }

        val array = JSONArray()
        records.forEach { item ->
            array.put(encodeRecord(item))
        }
        val activeProgressKeys = records.mapTo(mutableSetOf()) { progressKey(it.downloadId) }
        val retainedKeys = retainedProgressDownloadIds
            .filterNot { it in records.map(OfflineDownload::downloadId) }
            .mapTo(mutableSetOf(), ::progressKey)
        val retainAllProgressKeys = read.state == DownloadIndexState.PARTIAL_DAMAGE ||
            runCatching { preferences.getBoolean(KEY_RETAIN_ALL_PROGRESS, false) }
                .getOrDefault(false)
        val allPreferenceKeys = runCatching { preferences.all.keys }.getOrDefault(emptySet())
        val touchedKeys = linkedSetOf<String>().apply {
            add(KEY_DOWNLOADS)
            add(DurableDownloadPreferenceStore.KEY_STORAGE_VERSION)
            add(KEY_RETAINED_PROGRESS_IDS)
            add(KEY_RETAIN_ALL_PROGRESS)
            addAll(activeProgressKeys)
            if (!retainAllProgressKeys) {
                addAll(
                    allPreferenceKeys.filter {
                        it.startsWith(DurableDownloadPreferenceStore.KEY_PROGRESS_PREFIX) &&
                            it !in activeProgressKeys &&
                            it !in retainedKeys
                    },
                )
            }
            if (schedulingChanged) {
                add(DurableDownloadPreferenceStore.KEY_LIFECYCLE_REVISION)
            }
        }
        val before = touchedKeys.associateWith { key ->
            val present = runCatching { preferences.contains(key) }.getOrDefault(false)
            PreferenceValue(
                present = present,
                value = runCatching { preferences.all[key] }.getOrNull(),
            )
        }
        val editor = preferences.edit()
            .putString(KEY_DOWNLOADS, array.toString())
            .putInt(
                DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
                DurableDownloadPreferenceStore.STORAGE_VERSION,
            )
            .putString(
                KEY_RETAINED_PROGRESS_IDS,
                JSONArray(retainedProgressDownloadIds.sorted()).toString(),
            )
        if (retainAllProgressKeys) {
            editor.putBoolean(KEY_RETAIN_ALL_PROGRESS, true)
        }
        records.forEach { item ->
            editor.putString(progressKey(item.downloadId), encodeProgress(item).toString())
        }
        if (!retainAllProgressKeys) {
            allPreferenceKeys
                .filter {
                    it.startsWith(DurableDownloadPreferenceStore.KEY_PROGRESS_PREFIX) &&
                        it !in activeProgressKeys &&
                        it !in retainedKeys
                }
                .forEach(editor::remove)
        }
        if (schedulingChanged) {
            editor.putLong(
                DurableDownloadPreferenceStore.KEY_LIFECYCLE_REVISION,
                preferences.getLong(DurableDownloadPreferenceStore.KEY_LIFECYCLE_REVISION, 0L) + 1L,
            )
        }
        val persisted = if (synchronous) {
            if (accountId != null) DownloadPersistenceMutationGate.begin(accountId)
            var committed = false
            try {
                committed = runCatching { editor.commit() }.getOrDefault(false)
            } finally {
                if (!committed) restoreDownloadPreferenceMemory(preferences, before)
                if (accountId != null) DownloadPersistenceMutationGate.end(accountId)
            }
            committed
        } else {
            editor.apply()
            true
        }
        if (!persisted) {
            return false
        }
        if (read.state == DownloadIndexState.PARTIAL_DAMAGE) {
            lastRead = read.copy(state = DownloadIndexState.VALID)
        }
        return true
    }

    private fun readInternal(): DownloadIndexRead {
        val raw = try {
            preferences.getString(KEY_DOWNLOADS, null)
        } catch (_: Exception) {
            return DownloadIndexRead(
                records = emptyList(),
                state = DownloadIndexState.STORAGE_FAILURE,
                safeRawDocument = null,
                retainedProgressDownloadIds = emptySet(),
            )
        } ?: return DownloadIndexRead(
            records = emptyList(),
            state = if (hasEstablishedDownloadState()) {
                DownloadIndexState.MISSING_ESTABLISHED
            } else {
                DownloadIndexState.MISSING
            },
            safeRawDocument = null,
            retainedProgressDownloadIds = readPersistedRetainedProgressIds(),
        )
        if (raw.isBlank()) {
            return DownloadIndexRead(
                records = emptyList(),
                state = DownloadIndexState.UNREADABLE,
                safeRawDocument = null,
                retainedProgressDownloadIds = readPersistedRetainedProgressIds(),
            )
        }
        val array = runCatching { JSONArray(raw) }.getOrNull()
            ?: return DownloadIndexRead(
                records = emptyList(),
                state = DownloadIndexState.UNREADABLE,
                safeRawDocument = null,
                retainedProgressDownloadIds = readPersistedRetainedProgressIds(),
            )
        if (array.length() == 0) {
            return DownloadIndexRead(
                records = emptyList(),
                state = DownloadIndexState.VALID,
                safeRawDocument = raw,
                retainedProgressDownloadIds = readPersistedRetainedProgressIds(),
            )
        }
        val safeRaw = sanitizePersistedDownloadJson(raw) ?: return DownloadIndexRead(
            records = emptyList(),
            state = DownloadIndexState.UNREADABLE,
            safeRawDocument = null,
            retainedProgressDownloadIds = readPersistedRetainedProgressIds(),
        )
        val retained = readPersistedRetainedProgressIds().toMutableSet()
        val records = mutableListOf<OfflineDownload>()
        var damaged = false
        for (index in 0 until array.length()) {
            val data = array.optJSONObject(index)
            if (data == null) {
                damaged = true
                continue
            }
            val decoded = runCatching {
                decodeRecord(data, index)
            }.getOrNull()
            if (decoded == null) {
                damaged = true
                data.optLong("downloadId", 0L)
                    .takeIf { it > 0L }
                    ?.let(retained::add)
            } else {
                records += decoded
            }
        }
        if (records.isEmpty()) {
            return DownloadIndexRead(
                records = emptyList(),
                state = DownloadIndexState.UNREADABLE,
                safeRawDocument = safeRaw,
                retainedProgressDownloadIds = retained,
            )
        }
        return DownloadIndexRead(
            records = records,
            state = if (damaged) DownloadIndexState.PARTIAL_DAMAGE else DownloadIndexState.VALID,
            safeRawDocument = safeRaw,
            retainedProgressDownloadIds = retained,
        )
    }

    private fun decodeRecord(data: JSONObject, index: Int): OfflineDownload {
        val downloadId = data.getLong("downloadId")
        val progress = readStoredProgress(downloadId, data)
        return OfflineDownload(
            downloadId = downloadId,
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
            bytesDownloaded = progress.bytesDownloaded,
            totalBytes = progress.totalBytes,
            bytesPerSecond = progress.bytesPerSecond,
            etaSeconds = progress.etaSeconds,
            localUri = data.optNullableString("localUri"),
            errorMessage = redactCredentialBearingUrl(data.optNullableString("errorMessage")),
            retryCount = data.optInt("retryCount", 0).coerceAtLeast(0),
            integrityVerified = data.optBoolean("integrityVerified", false),
            priority = data.optInt("priority", 0),
            queuePosition = data.optInt("queuePosition", index),
            scheduledAtEpochMs = data.optLong("scheduledAtEpochMs", 0L),
            createdAtEpochMs = data.optLong("createdAtEpochMs", System.currentTimeMillis()),
            ownerProfileIds = data.optStringSet("ownerProfileIds"),
        )
    }

    private fun readStoredProgress(
        downloadId: Long,
        legacyRecord: JSONObject,
    ): StoredDownloadProgress {
        val splitProgress = runCatching {
            preferences.getString(progressKey(downloadId), null)
        }.getOrNull()?.let { raw ->
            runCatching {
                val data = JSONObject(raw)
                StoredDownloadProgress(
                    bytesDownloaded = data.optLong("bytesDownloaded", 0L).coerceAtLeast(0L),
                    totalBytes = data.optLong("totalBytes", -1L),
                    bytesPerSecond = data.optLong("bytesPerSecond", 0L).coerceAtLeast(0L),
                    etaSeconds = data.optLong("etaSeconds", -1L),
                )
            }.getOrNull()
        }
        return splitProgress ?: StoredDownloadProgress(
            bytesDownloaded = legacyRecord.optLong("bytesDownloaded", 0L).coerceAtLeast(0L),
            totalBytes = legacyRecord.optLong("totalBytes", -1L),
            bytesPerSecond = legacyRecord.optLong("bytesPerSecond", 0L).coerceAtLeast(0L),
            etaSeconds = legacyRecord.optLong("etaSeconds", -1L),
        )
    }

    private fun encodeRecord(item: OfflineDownload): JSONObject = JSONObject()
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
        .put("localUri", item.localUri ?: JSONObject.NULL)
        .put("errorMessage", redactCredentialBearingUrl(item.errorMessage) ?: JSONObject.NULL)
        .put("retryCount", item.retryCount)
        .put("integrityVerified", item.integrityVerified)
        .put("priority", item.priority)
        .put("queuePosition", item.queuePosition)
        .put("scheduledAtEpochMs", item.scheduledAtEpochMs)
        .put("createdAtEpochMs", item.createdAtEpochMs)
        .put("ownerProfileIds", JSONArray(item.ownerProfileIds.sorted()))

    private fun encodeProgress(item: OfflineDownload): JSONObject = JSONObject()
        .put("bytesDownloaded", item.bytesDownloaded)
        .put("totalBytes", item.totalBytes)
        .put("bytesPerSecond", item.bytesPerSecond)
        .put("etaSeconds", item.etaSeconds)

    private fun hasEstablishedDownloadState(): Boolean = runCatching {
        val version = preferences.getInt(
            DurableDownloadPreferenceStore.KEY_STORAGE_VERSION,
            0,
        )
        val keys = preferences.all.keys
        version > 0 ||
            keys.any { it.startsWith(DurableDownloadPreferenceStore.KEY_PROGRESS_PREFIX) } ||
            KEY_RETAINED_PROGRESS_IDS in keys ||
            KEY_RETAIN_ALL_PROGRESS in keys ||
            DurableDownloadPreferenceStore.KEY_LIFECYCLE_REVISION in keys ||
            keys.any { it.startsWith(persistedRecoveryKeyPrefix(KEY_DOWNLOADS)) } ||
            hasUnsafeLegacyMigrationMarker(
                preferences,
                DurableDownloadPreferenceStore.PREFERENCES_NAME,
            )
    }.getOrDefault(false)

    private fun readPersistedRetainedProgressIds(): Set<Long> = runCatching {
        preferences.getString(KEY_RETAINED_PROGRESS_IDS, null)?.let { raw ->
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optLong(index, 0L).takeIf { it > 0L }?.let(::add)
                }
            }
        }.orEmpty()
    }.getOrDefault(emptySet())

    private fun progressKey(downloadId: Long): String =
        "${DurableDownloadPreferenceStore.KEY_PROGRESS_PREFIX}$downloadId"

    private data class PreferenceValue(
        val present: Boolean,
        val value: Any?,
    )

    private fun restoreDownloadPreferenceMemory(
        preferences: SharedPreferences,
        before: Map<String, PreferenceValue>,
    ) {
        val editor = preferences.edit()
        before.forEach { (key, value) ->
            when {
                !value.present -> editor.remove(key)
                value.value is String -> editor.putString(key, value.value)
                value.value is Int -> editor.putInt(key, value.value)
                value.value is Long -> editor.putLong(key, value.value)
                value.value is Boolean -> editor.putBoolean(key, value.value)
                value.value is Float -> editor.putFloat(key, value.value)
                value.value is Set<*> -> editor.putStringSet(
                    key,
                    value.value.filterIsInstance<String>().toSet(),
                )
                else -> editor.remove(key)
            }
        }
        // A failed restore commit still restores in-process memory, which is the goal here.
        runCatching { editor.commit() }
    }

    private companion object {
        const val KEY_DOWNLOADS = DurableDownloadPreferenceStore.KEY_DOWNLOADS
        const val KEY_RETAINED_PROGRESS_IDS = "__integrity_retained_progress_ids__"
        const val KEY_RETAIN_ALL_PROGRESS = "__integrity_retain_all_progress__"
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (isNull(name)) null else optInt(name)

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (isNull(name)) null else optBoolean(name)

private fun JSONObject.optStringSet(name: String): Set<String> {
    val values = optJSONArray(name) ?: return emptySet()
    return buildSet {
        for (index in 0 until values.length()) {
            values.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}

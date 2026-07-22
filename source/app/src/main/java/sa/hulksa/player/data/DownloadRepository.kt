package sa.hulksa.player.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import java.io.File

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun downloads(): List<OfflineDownload> = refresh(readStored())

    @Synchronized
    fun enqueue(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): EnqueueResult {
        require(!request.isLive) { "لا يمكن تحميل البث المباشر." }
        val current = refresh(readStored())
        current.firstOrNull {
            it.historyKey == request.historyKey && it.status != OfflineStatus.FAILED
        }?.let { return EnqueueResult.AlreadyExists(it) }

        val source = request.candidates.firstOrNull()
            ?: return EnqueueResult.Failed("لا يوجد رابط صالح للتحميل.")
        val extension = safeExtension(request.extension)
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: return EnqueueResult.Failed("مساحة التخزين غير متاحة على هذا الجهاز.")
        val fileName = buildFileName(request.title, request.streamId, extension)
        val destination = File(directory, fileName)
        if (destination.exists()) destination.delete()

        return runCatching {
            val downloadRequest = DownloadManager.Request(Uri.parse(source))
                .setTitle(request.title)
                .setDescription(seriesTitle ?: "HULK")
                .setMimeType(mimeType(extension))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .addRequestHeader("User-Agent", "HULK Android TV")
                .setDestinationUri(Uri.fromFile(destination))
            val downloadId = manager.enqueue(downloadRequest)
            val entry = OfflineDownload(
                downloadId = downloadId,
                historyKey = request.historyKey,
                title = request.title,
                posterUrl = request.posterUrl,
                streamKind = request.streamKind,
                streamId = request.streamId,
                extension = extension,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
                localUri = Uri.fromFile(destination).toString(),
            )
            val withoutOldFailure = current.filterNot { it.historyKey == request.historyKey }
            writeStored(listOf(entry) + withoutOldFailure)
            EnqueueResult.Started(entry)
        }.getOrElse { error ->
            EnqueueResult.Failed(error.message ?: "تعذر بدء التحميل.")
        }
    }

    @Synchronized
    fun remove(downloadId: Long): List<OfflineDownload> {
        val current = readStored()
        val removed = current.firstOrNull { it.downloadId == downloadId }
        runCatching { manager.remove(downloadId) }
        removed?.localUri?.let { uri ->
            runCatching {
                if (uri.startsWith("file:")) File(requireNotNull(Uri.parse(uri).path)).delete()
            }
        }
        return current.filterNot { it.downloadId == downloadId }.also(::writeStored)
    }

    @Synchronized
    private fun refresh(stored: List<OfflineDownload>): List<OfflineDownload> {
        if (stored.isEmpty()) return emptyList()
        val ids = stored.map(OfflineDownload::downloadId).toLongArray()
        val updates = mutableMapOf<Long, OfflineDownload>()
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(*ids))
        }.getOrNull()?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val reasonColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val original = stored.firstOrNull { it.downloadId == id } ?: continue
                updates[id] = original.copy(
                    status = cursor.getInt(statusColumn).toOfflineStatus(),
                    bytesDownloaded = cursor.getLong(downloadedColumn).coerceAtLeast(0L),
                    totalBytes = cursor.getLong(totalColumn),
                    localUri = cursor.getString(localUriColumn) ?: original.localUri,
                    failureReason = cursor.getInt(reasonColumn).takeIf {
                        cursor.getInt(statusColumn) == DownloadManager.STATUS_FAILED
                    },
                )
            }
        }
        val refreshed = stored.map { original ->
            updates[original.downloadId] ?: if (
                original.status == OfflineStatus.COMPLETED &&
                original.localUri?.let(::fileExists) == true
            ) {
                original
            } else {
                original.copy(status = OfflineStatus.FAILED)
            }
        }.sortedByDescending(OfflineDownload::createdAtEpochMs)
        if (refreshed != stored) writeStored(refreshed)
        return refreshed
    }

    private fun readStored(): List<OfflineDownload> {
        val raw = preferences.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        OfflineDownload(
                            downloadId = item.getLong("downloadId"),
                            historyKey = item.getString("historyKey"),
                            title = item.getString("title"),
                            posterUrl = item.optNullableString("posterUrl"),
                            streamKind = item.getString("streamKind"),
                            streamId = item.getInt("streamId"),
                            extension = item.optString("extension", "mp4"),
                            seriesTitle = item.optNullableString("seriesTitle"),
                            season = item.optNullableInt("season"),
                            episodeNumber = item.optNullableInt("episodeNumber"),
                            status = runCatching {
                                OfflineStatus.valueOf(item.optString("status", OfflineStatus.QUEUED.name))
                            }.getOrDefault(OfflineStatus.QUEUED),
                            bytesDownloaded = item.optLong("bytesDownloaded", 0L),
                            totalBytes = item.optLong("totalBytes", -1L),
                            localUri = item.optNullableString("localUri"),
                            failureReason = item.optNullableInt("failureReason"),
                            createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeStored(downloads: List<OfflineDownload>) {
        val array = JSONArray()
        downloads.forEach { item ->
            array.put(
                JSONObject()
                    .put("downloadId", item.downloadId)
                    .put("historyKey", item.historyKey)
                    .put("title", item.title)
                    .put("posterUrl", item.posterUrl ?: JSONObject.NULL)
                    .put("streamKind", item.streamKind)
                    .put("streamId", item.streamId)
                    .put("extension", item.extension)
                    .put("seriesTitle", item.seriesTitle ?: JSONObject.NULL)
                    .put("season", item.season ?: JSONObject.NULL)
                    .put("episodeNumber", item.episodeNumber ?: JSONObject.NULL)
                    .put("status", item.status.name)
                    .put("bytesDownloaded", item.bytesDownloaded)
                    .put("totalBytes", item.totalBytes)
                    .put("localUri", item.localUri ?: JSONObject.NULL)
                    .put("failureReason", item.failureReason ?: JSONObject.NULL)
                    .put("createdAtEpochMs", item.createdAtEpochMs)
            )
        }
        preferences.edit().putString(KEY_DOWNLOADS, array.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun Int.toOfflineStatus(): OfflineStatus = when (this) {
        DownloadManager.STATUS_PENDING -> OfflineStatus.QUEUED
        DownloadManager.STATUS_RUNNING -> OfflineStatus.DOWNLOADING
        DownloadManager.STATUS_PAUSED -> OfflineStatus.PAUSED
        DownloadManager.STATUS_SUCCESSFUL -> OfflineStatus.COMPLETED
        else -> OfflineStatus.FAILED
    }

    private fun fileExists(uri: String): Boolean = runCatching {
        uri.startsWith("file:") && File(requireNotNull(Uri.parse(uri).path)).exists()
    }.getOrDefault(false)

    private fun safeExtension(raw: String): String = raw
        .trim()
        .trimStart('.')
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
        ?: "mp4"

    private fun buildFileName(title: String, streamId: Int, extension: String): String {
        val safeTitle = title
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(72)
            .ifBlank { "HULK" }
        return "${safeTitle}_${streamId}.$extension"
    }

    private fun mimeType(extension: String): String = when (extension) {
        "mkv" -> "video/x-matroska"
        "ts", "mpegts" -> "video/mp2t"
        "m3u8" -> "application/x-mpegURL"
        else -> "video/mp4"
    }

    sealed interface EnqueueResult {
        data class Started(val item: OfflineDownload) : EnqueueResult
        data class AlreadyExists(val item: OfflineDownload) : EnqueueResult
        data class Failed(val message: String) : EnqueueResult
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_offline_downloads"
        const val KEY_DOWNLOADS = "downloads"
    }
}

package sa.hulksa.player.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newFixedThreadPool(2)
    private val running = ConcurrentHashMap<Long, okhttp3.Call>()
    private val idGenerator = AtomicLong(System.currentTimeMillis())
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Synchronized
    fun downloads(): List<OfflineDownload> = readStored()
        .map { item ->
            if (item.status == OfflineStatus.DOWNLOADING && running[item.downloadId] == null) {
                item.copy(status = OfflineStatus.FAILED, failureReason = ERROR_INTERRUPTED)
            } else item
        }
        .sortedByDescending(OfflineDownload::createdAtEpochMs)
        .also(::writeStored)

    @Synchronized
    fun enqueue(
        request: PlaybackRequest,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): EnqueueResult {
        require(!request.isLive) { "لا يمكن تحميل البث المباشر." }
        val current = readStored()
        current.firstOrNull {
            it.historyKey == request.historyKey && it.status != OfflineStatus.FAILED
        }?.let { return EnqueueResult.AlreadyExists(it) }

        val source = request.candidates.firstOrNull()
            ?: return EnqueueResult.Failed("لا يوجد رابط صالح للتحميل.")
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            return EnqueueResult.Failed("رابط التحميل غير مدعوم.")
        }

        val extension = safeExtension(request.extension)
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: return EnqueueResult.Failed("مساحة التخزين غير متاحة على هذا الجهاز.")
        if (!directory.exists() && !directory.mkdirs()) {
            return EnqueueResult.Failed("تعذر إنشاء مجلد التحميلات.")
        }

        val destination = File(directory, buildFileName(request.title, request.streamId, extension))
        val existingBytes = destination.takeIf(File::exists)?.length() ?: 0L
        val downloadId = idGenerator.incrementAndGet()
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
            status = OfflineStatus.QUEUED,
            bytesDownloaded = existingBytes,
            localUri = Uri.fromFile(destination).toString(),
            sourceUrl = source,
        )
        writeStored(listOf(entry) + current.filterNot { it.historyKey == request.historyKey })
        startDownload(entry, destination)
        return EnqueueResult.Started(entry)
    }

    @Synchronized
    fun remove(downloadId: Long): List<OfflineDownload> {
        running.remove(downloadId)?.cancel()
        val current = readStored()
        current.firstOrNull { it.downloadId == downloadId }?.localUri?.let { uri ->
            runCatching { if (uri.startsWith("file:")) File(requireNotNull(Uri.parse(uri).path)).delete() }
        }
        return current.filterNot { it.downloadId == downloadId }.also(::writeStored)
    }

    private fun startDownload(entry: OfflineDownload, destination: File) {
        executor.execute {
            update(entry.downloadId) { it.copy(status = OfflineStatus.DOWNLOADING, failureReason = null) }
            var existing = destination.takeIf(File::exists)?.length() ?: 0L
            val builder = Request.Builder()
                .url(requireNotNull(entry.sourceUrl))
                .header("User-Agent", "HULK Android TV/0.6.1")
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
            if (existing > 0L) builder.header("Range", "bytes=$existing-")

            val call = client.newCall(builder.build())
            running[entry.downloadId] = call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw DownloadFailure(httpMessage(response.code), response.code)
                    val body = response.body ?: throw DownloadFailure("الخادم لم يرسل ملف الفيديو.", ERROR_EMPTY_BODY)
                    val contentType = body.contentType()?.toString().orEmpty()
                    if (contentType.contains("mpegurl", true) || entry.extension == "m3u8") {
                        throw DownloadFailure("هذا المحتوى يستخدم بث HLS ولا يمكن حفظه كملف واحد حاليا.", ERROR_HLS)
                    }

                    val append = existing > 0L && response.code == 206
                    if (!append) {
                        existing = 0L
                        if (destination.exists()) destination.delete()
                    }
                    val incoming = body.contentLength()
                    val total = if (incoming >= 0L) existing + incoming else -1L
                    val available = destination.parentFile?.usableSpace ?: 0L
                    if (incoming > 0L && available > 0L && incoming > available - MIN_FREE_SPACE) {
                        throw DownloadFailure("المساحة غير كافية لتحميل هذا الملف.", ERROR_NO_SPACE)
                    }

                    RandomAccessFile(destination, "rw").use { output ->
                        if (append) output.seek(existing) else output.setLength(0L)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                            var downloaded = existing
                            var lastSavedAt = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = System.currentTimeMillis()
                                if (now - lastSavedAt >= 750L) {
                                    update(entry.downloadId) {
                                        it.copy(status = OfflineStatus.DOWNLOADING, bytesDownloaded = downloaded, totalBytes = total)
                                    }
                                    lastSavedAt = now
                                }
                            }
                            if (total > 0L && downloaded < total) {
                                throw DownloadFailure("انقطع التحميل قبل اكتمال الملف.", ERROR_INTERRUPTED)
                            }
                            update(entry.downloadId) {
                                it.copy(
                                    status = OfflineStatus.COMPLETED,
                                    bytesDownloaded = downloaded,
                                    totalBytes = if (total > 0L) total else downloaded,
                                    localUri = Uri.fromFile(destination).toString(),
                                    failureReason = null,
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                if (running[entry.downloadId]?.isCanceled() == true) return@execute
                val reason = (error as? DownloadFailure)?.reason ?: ERROR_NETWORK
                update(entry.downloadId) { it.copy(status = OfflineStatus.FAILED, failureReason = reason) }
            } finally {
                running.remove(entry.downloadId)
            }
        }
    }

    @Synchronized
    private fun update(downloadId: Long, transform: (OfflineDownload) -> OfflineDownload) {
        val updated = readStored().map { if (it.downloadId == downloadId) transform(it) else it }
        writeStored(updated)
    }

    private fun readStored(): List<OfflineDownload> {
        val raw = preferences.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(OfflineDownload(
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
                        status = runCatching { OfflineStatus.valueOf(item.optString("status", OfflineStatus.QUEUED.name)) }.getOrDefault(OfflineStatus.QUEUED),
                        bytesDownloaded = item.optLong("bytesDownloaded", 0L),
                        totalBytes = item.optLong("totalBytes", -1L),
                        localUri = item.optNullableString("localUri"),
                        failureReason = item.optNullableInt("failureReason"),
                        sourceUrl = item.optNullableString("sourceUrl"),
                        createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeStored(downloads: List<OfflineDownload>) {
        val array = JSONArray()
        downloads.forEach { item ->
            array.put(JSONObject()
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
                .put("sourceUrl", item.sourceUrl ?: JSONObject.NULL)
                .put("createdAtEpochMs", item.createdAtEpochMs))
        }
        preferences.edit().putString(KEY_DOWNLOADS, array.toString()).commit()
    }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun safeExtension(raw: String): String = raw.trim().trimStart('.').lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
    private fun buildFileName(title: String, streamId: Int, extension: String): String {
        val safeTitle = title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "").trim().replace(Regex("\\s+"), "_").take(72).ifBlank { "HULK" }
        return "${safeTitle}_${streamId}.$extension"
    }
    private fun httpMessage(code: Int): String = when (code) {
        401, 403 -> "الخادم رفض رابط التحميل أو انتهت صلاحيته."
        404 -> "ملف الفيديو غير موجود على الخادم."
        416 -> "تعذر استكمال الملف؛ احذفه وأعد التحميل."
        in 500..599 -> "خادم الفيديو غير متاح مؤقتا."
        else -> "فشل التحميل من الخادم (رمز $code)."
    }

    sealed interface EnqueueResult {
        data class Started(val item: OfflineDownload) : EnqueueResult
        data class AlreadyExists(val item: OfflineDownload) : EnqueueResult
        data class Failed(val message: String) : EnqueueResult
    }

    private class DownloadFailure(message: String, val reason: Int) : Exception(message)

    private companion object {
        const val PREFERENCES_NAME = "hulk_offline_downloads"
        const val KEY_DOWNLOADS = "downloads"
        const val MIN_FREE_SPACE = 128L * 1024L * 1024L
        const val ERROR_NETWORK = 1001
        const val ERROR_NO_SPACE = 1002
        const val ERROR_HLS = 1003
        const val ERROR_EMPTY_BODY = 1004
        const val ERROR_INTERRUPTED = 1005
    }
}

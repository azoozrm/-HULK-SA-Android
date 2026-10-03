package sa.hulksa.player.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Bounded, serialized seek-preview frame source for the currently authorized VOD media.
 *
 * One retriever serves the current playback source on a single background thread; a small keyframe
 * cache reuses frames while the user scrubs. Every failure (unsupported playlist, network, decode,
 * closed session) returns `null`, which keeps the timestamp-only preview without fabricating an
 * image. No media bytes are written to disk and no URL is ever logged.
 */
internal class VodSeekPreviewFrames(private val source: String?) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vod-seek-preview").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHED_FRAMES) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    @Volatile
    private var closed = false

    private var retriever: MediaMetadataRetriever? = null

    suspend fun frameAt(context: Context, timeMs: Long): Bitmap? {
        if (closed || !vodPreviewDecodableCandidate(source)) return null
        val media = source?.trim().orEmpty()
        if (media.isEmpty()) return null
        val cacheKey = "${media.hashCode()}|${vodPreviewBucketMs(timeMs)}"
        cache.get(cacheKey)?.let { return it }
        return withContext(dispatcher) {
            if (closed) return@withContext null
            cache.get(cacheKey)?.let { return@withContext it }
            runCatching {
                val active = retriever ?: createRetriever(context, media).also { retriever = it }
                active.getFrameAtTime(
                    vodPreviewBucketMs(timeMs) * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            }.getOrNull()?.also { cache.put(cacheKey, it) }
        }
    }

    private fun createRetriever(context: Context, media: String): MediaMetadataRetriever {
        val created = MediaMetadataRetriever()
        val lower = media.lowercase()
        when {
            lower.startsWith("content://") || lower.startsWith("file://") ->
                created.setDataSource(context, Uri.parse(media))
            lower.startsWith("http://") || lower.startsWith("https://") ->
                created.setDataSource(media, emptyMap())
            else -> created.setDataSource(media)
        }
        return created
    }

    fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        // Queued behind any in-flight extraction, then the executor drains and stops.
        executor.execute {
            runCatching { retriever?.release() }
            retriever = null
        }
        runCatching { dispatcher.close() }
    }

    private companion object {
        const val MAX_CACHED_FRAMES = 8
    }
}

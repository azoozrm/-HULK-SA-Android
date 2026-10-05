package sa.hulksa.player.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One identity-tagged preview value: which source and which five-second bucket it belongs to. */
internal data class VodSeekPreviewResult<T : Any>(
    val sourceKey: Int,
    val bucketMs: Long,
    val value: T,
)

/**
 * The published value only when it belongs to the requested source and target bucket, so an
 * old-source or unrelated-bucket frame can never be shown under a new target timestamp. A null
 * target (no active seek intent) always keeps the time-only fallback.
 */
internal fun <T : Any> vodPreviewValueFor(
    publication: VodSeekPreviewResult<T>?,
    sourceKey: Int,
    targetMs: Long?,
): T? {
    val target = targetMs ?: return null
    return publication
        ?.takeIf { it.sourceKey == sourceKey && it.bucketMs == vodPreviewBucketMs(target) }
        ?.value
}

/**
 * Bounded single-worker preview scheduling state.
 *
 * At most one [load] runs at a time; while it runs, at most one newer pending bucket is retained
 * and the latest request replaces any earlier pending one. Completed values are cached by bucket,
 * so repeating or reversing over a target never re-decodes it. [close] rejects everything after
 * disposal. This class is intentionally free of Android and coroutine types so it can be driven
 * deterministically by tests.
 */
internal class VodSeekPreviewLoader<T : Any>(
    private val sourceKey: Int,
    private val load: (bucketMs: Long) -> T?,
    private val cacheCapacity: Int = MAX_CACHED_PREVIEW_FRAMES,
) {
    private val lock = Any()
    private val cache = LinkedHashMap<Long, T>(cacheCapacity, 0.75f, true)
    private var inFlightBucket: Long? = null
    private var pendingBucket: Long? = null
    private var closed = false

    /** Cached value for the bucket, or null after registering the latest pending request. */
    fun request(bucketMs: Long): T? = synchronized(lock) {
        if (closed) return null
        cache[bucketMs]?.let { return it }
        // The bucket already being extracted needs no second request; its result will publish.
        if (bucketMs == inFlightBucket) return null
        pendingBucket = bucketMs
        null
    }

    fun hasPending(): Boolean = synchronized(lock) { !closed && pendingBucket != null }

    /** Runs the single latest pending bucket once; null when nothing is pending or load failed. */
    fun runNext(): VodSeekPreviewResult<T>? {
        val bucket = synchronized(lock) {
            if (closed || inFlightBucket != null) return null
            val next = pendingBucket ?: return null
            pendingBucket = null
            inFlightBucket = next
            next
        }
        val value = try {
            load(bucket)
        } finally {
            synchronized(lock) { inFlightBucket = null }
        }
        return synchronized(lock) {
            if (closed || value == null) return null
            cache[bucket] = value
            trimCache()
            VodSeekPreviewResult(sourceKey = sourceKey, bucketMs = bucket, value = value)
        }
    }

    fun close() = synchronized(lock) {
        closed = true
        pendingBucket = null
        cache.clear()
    }

    private fun trimCache() {
        while (cache.size > cacheCapacity) {
            val oldest = cache.keys.firstOrNull() ?: return
            cache.remove(oldest)
        }
    }

    private companion object {
        const val MAX_CACHED_PREVIEW_FRAMES = 8
    }
}

/** Decodes a preview frame from the prepared source; every call arrives on the single worker. */
internal interface VodSeekPreviewExtractor {
    fun extract(source: String, bucketMs: Long): Bitmap?

    fun close()
}

/** Production extractor: one lazy [MediaMetadataRetriever] for the prepared source. */
internal class MediaMetadataVodPreviewExtractor(
    private val appContext: Context,
) : VodSeekPreviewExtractor {
    private var retriever: MediaMetadataRetriever? = null

    override fun extract(source: String, bucketMs: Long): Bitmap? {
        val active = retriever ?: createRetriever(source).also { retriever = it }
        return active.getFrameAtTime(bucketMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    override fun close() {
        val active = retriever ?: return
        retriever = null
        runCatching { active.release() }
    }

    private fun createRetriever(media: String): MediaMetadataRetriever {
        val created = MediaMetadataRetriever()
        val lower = media.lowercase()
        when {
            lower.startsWith("content://") || lower.startsWith("file://") ->
                created.setDataSource(appContext, Uri.parse(media))
            lower.startsWith("http://") || lower.startsWith("https://") ->
                created.setDataSource(media, emptyMap())
            else -> created.setDataSource(media)
        }
        return created
    }
}

/**
 * Bounded, serialized seek-preview frame source for the current prepared VOD source.
 *
 * One worker thread drives [VodSeekPreviewLoader]: a new target only replaces the single pending
 * bucket, so a continuous seek cannot build a FIFO backlog, and a completed frame is published
 * while the user keeps seeking. Every publication carries its source key and bucket, and the UI
 * accepts it only for the matching target. Every failure returns no frame, which keeps the
 * timestamp-only preview without fabricating an image. No media bytes are written to disk and no
 * URL is ever logged.
 */
internal class VodSeekPreviewFrames(
    source: String?,
    private val extractor: VodSeekPreviewExtractor,
) {
    private val media = source?.trim().orEmpty()
    val sourceKey: Int = media.hashCode()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vod-seek-preview").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val loader = VodSeekPreviewLoader(
        sourceKey = sourceKey,
        // Native extraction failures always degrade to the time-only fallback, never to a crash
        // that would end the single worker.
        load = { bucket -> runCatching { extractor.extract(media, bucket) }.getOrNull() },
    )
    private val _publication = MutableStateFlow<VodSeekPreviewResult<Bitmap>?>(null)
    val publication: StateFlow<VodSeekPreviewResult<Bitmap>?> = _publication.asStateFlow()

    @Volatile
    private var closed = false

    init {
        scope.launch {
            for (ignored in signal) {
                while (true) {
                    val result = loader.runNext() ?: break
                    if (!closed) _publication.value = result
                }
            }
        }
    }

    /** Registers the target bucket; a cached bucket publishes immediately. Never blocks Main. */
    fun request(timeMs: Long) {
        if (closed || !vodPreviewDecodableCandidate(media)) return
        val bucket = vodPreviewBucketMs(timeMs)
        val cached = loader.request(bucket)
        if (cached != null) {
            _publication.value = VodSeekPreviewResult(sourceKey = sourceKey, bucketMs = bucket, value = cached)
            return
        }
        signal.trySend(Unit)
    }

    fun close() {
        if (closed) return
        closed = true
        loader.close()
        _publication.value = null
        signal.close()
        scope.cancel()
        // Queued behind any in-flight extraction, then the extractor releases its retriever.
        executor.execute { runCatching { extractor.close() } }
        runCatching { dispatcher.close() }
    }
}

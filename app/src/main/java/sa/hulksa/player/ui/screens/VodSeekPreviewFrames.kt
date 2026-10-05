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

/** Outcome of registering a preview target with [VodSeekPreviewLoader.request]. */
internal enum class VodPreviewRequestDisposition {
    /** The latest target was already cached and was published immediately. */
    PUBLISHED_CACHE,

    /** The latest target is already being extracted; its completion is eligible. */
    AWAITING_IN_FLIGHT,

    /** A new single pending bucket was registered for the worker. */
    PENDING_NEW,

    /** The loader is closed; the request was ignored. */
    REJECTED,
}

/**
 * Bounded single-worker preview scheduling state with one authoritative latest request.
 *
 * At most one [load] runs at a time; while it runs, at most one newer pending bucket is retained
 * and the latest request replaces any earlier pending one. The latest requested bucket is recorded
 * on every request, including cache hits and requests returning to the in-flight bucket, so
 * obsolete pending work is dropped and only a completion still matching the latest target can
 * publish. Completed values are cached by bucket (obsolete ones included) so repeating or
 * reversing over a target never re-decodes it. [close] rejects everything after disposal. This
 * class is intentionally free of Android and coroutine types so it can be driven deterministically
 * by tests.
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
    private var latestBucket: Long? = null
    private var closed = false

    /**
     * Registers [bucketMs] as the latest requested target and reports what the caller must do.
     *
     * A cached target drops obsolete pending work and publishes the matching frame inside the same
     * critical section that updates the latest target, so a late completion can never interleave
     * between the eligibility decision and the publication write. An in-flight target also drops
     * obsolete pending work because its own completion is eligible.
     */
    fun request(
        bucketMs: Long,
        publish: (VodSeekPreviewResult<T>) -> Unit,
    ): VodPreviewRequestDisposition = synchronized(lock) {
        if (closed) return VodPreviewRequestDisposition.REJECTED
        latestBucket = bucketMs
        val cached = cache[bucketMs]
        if (cached != null) {
            pendingBucket = null
            publish(VodSeekPreviewResult(sourceKey = sourceKey, bucketMs = bucketMs, value = cached))
            return VodPreviewRequestDisposition.PUBLISHED_CACHE
        }
        if (bucketMs == inFlightBucket) {
            pendingBucket = null
            return VodPreviewRequestDisposition.AWAITING_IN_FLIGHT
        }
        pendingBucket = bucketMs
        return VodPreviewRequestDisposition.PENDING_NEW
    }

    fun hasPending(): Boolean = synchronized(lock) { !closed && pendingBucket != null }

    /**
     * Runs the single latest pending bucket once. Returns true whenever an extraction was
     * attempted, so the worker re-checks for newer pending work (including after a failure).
     */
    fun runNext(publish: (VodSeekPreviewResult<T>) -> Unit): Boolean {
        val bucket = synchronized(lock) {
            if (closed || inFlightBucket != null) return false
            val next = pendingBucket ?: return false
            pendingBucket = null
            inFlightBucket = next
            next
        }
        val value = runCatching { load(bucket) }.getOrNull()
        synchronized(lock) {
            // Clearing in-flight together with caching closes the gap where a concurrent request
            // could re-register the same bucket and cause duplicate extraction.
            inFlightBucket = null
            if (closed) return false
            if (value != null) {
                cache[bucket] = value
                trimCache()
                // Only a completion still matching the latest target may publish; obsolete
                // completions stay cached but never replace the current publication.
                if (latestBucket == bucket) {
                    publish(VodSeekPreviewResult(sourceKey = sourceKey, bucketMs = bucket, value = value))
                }
            }
        }
        return true
    }

    fun close() = synchronized(lock) {
        closed = true
        pendingBucket = null
        latestBucket = null
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

    /** Single publication sink; the loader calls it under its lock for eligibility-atomic writes. */
    private fun publish(result: VodSeekPreviewResult<Bitmap>) {
        if (!closed) _publication.value = result
    }

    init {
        scope.launch {
            for (ignored in signal) {
                // Drain pending work; runNext reports true after every attempt so newer pending
                // targets (including after a failed extraction) are never stranded.
                while (loader.runNext(::publish)) { }
            }
        }
    }

    /** Registers the target bucket; a cached bucket publishes immediately. Never blocks Main. */
    fun request(timeMs: Long) {
        if (closed || !vodPreviewDecodableCandidate(media)) return
        val bucket = vodPreviewBucketMs(timeMs)
        if (loader.request(bucket, ::publish) == VodPreviewRequestDisposition.PENDING_NEW) {
            signal.trySend(Unit)
        }
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

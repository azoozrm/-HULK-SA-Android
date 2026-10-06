package sa.hulksa.player.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.util.concurrent.Executors
import kotlin.math.roundToInt
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
 * publish. A small bounded warm-up queue holds speculative lookahead buckets that never touch the
 * latest user target; the worker always drains user pending work first, and [cancelWarmUp] drops
 * pending speculation without interrupting an already-running extraction. Completed values are
 * cached by bucket (obsolete ones included) so repeating or reversing over a target never
 * re-decodes it. [close] rejects everything after disposal. This class is intentionally free of
 * Android and coroutine types so it can be driven deterministically by tests.
 */
internal class VodSeekPreviewLoader<T : Any>(
    private val sourceKey: Int,
    private val load: (bucketMs: Long) -> T?,
    private val cacheCapacity: Int = MAX_CACHED_PREVIEW_FRAMES,
) {
    private val lock = Any()
    private val cache = LinkedHashMap<Long, T>(cacheCapacity, 0.75f, true)
    private val warmQueue = ArrayDeque<Long>()
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
        // Real demand now owns this bucket: drop a matching speculative entry so it can never
        // cause a duplicate extraction after the user's own request completes.
        warmQueue.remove(bucketMs)
        pendingBucket = bucketMs
        return VodPreviewRequestDisposition.PENDING_NEW
    }

    fun hasPending(): Boolean = synchronized(lock) { !closed && pendingBucket != null }

    fun hasWarmUpPending(): Boolean = synchronized(lock) { !closed && warmQueue.isNotEmpty() }

    /**
     * Registers one speculative warm-up bucket without touching the user's latest target.
     *
     * Cached, user-pending, in-flight or latest user buckets are never queued again; the bounded
     * warm queue only holds a short lookahead. Returns true when a new speculative entry was added.
     */
    fun warmUp(bucketMs: Long): Boolean = synchronized(lock) {
        if (closed) return false
        if (
            bucketMs == latestBucket || bucketMs == pendingBucket || bucketMs == inFlightBucket ||
            cache.containsKey(bucketMs) || warmQueue.contains(bucketMs)
        ) {
            return false
        }
        if (warmQueue.size >= VOD_PREVIEW_WARM_UP_BUCKETS) return false
        warmQueue.addLast(bucketMs)
        true
    }

    /**
     * Drops pending speculative work. An already-running native extraction cannot be interrupted;
     * its completion still lands in the bounded cache but can never publish by itself.
     */
    fun cancelWarmUp() = synchronized(lock) {
        warmQueue.clear()
    }

    /**
     * Runs one extraction: the user's latest pending bucket first, then one speculative warm-up
     * bucket. Returns true whenever an extraction was attempted, so the worker re-checks for newer
     * pending work (including after a failure).
     */
    fun runNext(publish: (VodSeekPreviewResult<T>) -> Unit): Boolean {
        val bucket = synchronized(lock) {
            if (closed || inFlightBucket != null) return false
            // Real user demand is always processed before pending speculative warm-up work.
            val userPending = pendingBucket
            val next = if (userPending != null) {
                pendingBucket = null
                userPending
            } else {
                if (warmQueue.isEmpty()) return false
                warmQueue.removeFirst()
            }
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
        warmQueue.clear()
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

/**
 * Retained Movies preview-thumbnail budget for the bounded cache.
 *
 * The largest preview card is 216.dp wide at 16:9, so a 480x270 source thumbnail covers TV and
 * phone densities up to about 2.2x, while eight cached frames stay around 4.1 MB instead of
 * retaining full-size video frames.
 */
internal const val VOD_PREVIEW_THUMBNAIL_WIDTH_PX = 480
internal const val VOD_PREVIEW_THUMBNAIL_HEIGHT_PX = 270

/**
 * Extraction behavior for the shared VOD preview source.
 *
 * The pre-R23 pipeline always decoded a full frame with
 * `getFrameAtTime(timeUs, OPTION_CLOSEST_SYNC)`. The bounded thumbnail path is an explicit Movies
 * opt-in at construction, so Series/default callers keep the exact legacy call at every API level.
 */
internal enum class VodPreviewExtractionMode {
    LEGACY_FULL_FRAME,
    THUMBNAIL,
}

/** Which native frame call one preview extraction performs. */
internal enum class VodPreviewFramePlan {
    FULL_FRAME,
    SCALED,
    DECODE_THEN_SCALE,
}

/** Movies opt in to the bounded thumbnail path; every other caller keeps the legacy full frame. */
internal fun vodPreviewExtractionMode(isMovie: Boolean): VodPreviewExtractionMode =
    if (isMovie) VodPreviewExtractionMode.THUMBNAIL else VodPreviewExtractionMode.LEGACY_FULL_FRAME

/** First API level that provides `MediaMetadataRetriever.getScaledFrameAtTime`. */
internal const val VOD_PREVIEW_SCALED_FRAME_MIN_API = 27

internal fun vodPreviewExtractionPlan(
    mode: VodPreviewExtractionMode,
    apiLevel: Int,
): VodPreviewFramePlan = when {
    mode == VodPreviewExtractionMode.LEGACY_FULL_FRAME -> VodPreviewFramePlan.FULL_FRAME
    apiLevel >= VOD_PREVIEW_SCALED_FRAME_MIN_API -> VodPreviewFramePlan.SCALED
    else -> VodPreviewFramePlan.DECODE_THEN_SCALE
}

/**
 * Production extractor: one lazy [MediaMetadataRetriever] for the prepared source.
 *
 * The default mode is the exact pre-R23 full-frame call; only the Movies call site opts in to the
 * bounded thumbnail path.
 */
internal class MediaMetadataVodPreviewExtractor(
    private val appContext: Context,
    private val mode: VodPreviewExtractionMode = VodPreviewExtractionMode.LEGACY_FULL_FRAME,
) : VodSeekPreviewExtractor {
    private var retriever: MediaMetadataRetriever? = null

    override fun extract(source: String, bucketMs: Long): Bitmap? {
        val active = retriever ?: createRetriever(source).also { retriever = it }
        val timeUs = bucketMs * 1_000L
        return when (vodPreviewExtractionPlan(mode, Build.VERSION.SDK_INT)) {
            VodPreviewFramePlan.FULL_FRAME ->
                active.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            VodPreviewFramePlan.SCALED ->
                // The plan selects SCALED only at API >= 27; the explicit version guard keeps the
                // platform requirement visible to static analysis.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    active.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        VOD_PREVIEW_THUMBNAIL_WIDTH_PX,
                        VOD_PREVIEW_THUMBNAIL_HEIGHT_PX,
                    )
                } else {
                    null
                }
            VodPreviewFramePlan.DECODE_THEN_SCALE ->
                // API < 27 has no scaled frame API; decode then scale while preserving the aspect
                // ratio so the existing ContentScale.Crop fit is unchanged.
                active.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let(::scaleToPreviewThumbnail)
        }
    }

    override fun close() {
        val active = retriever ?: return
        retriever = null
        runCatching { active.release() }
    }

    private fun scaleToPreviewThumbnail(frame: Bitmap): Bitmap {
        val width = frame.width.coerceAtLeast(1)
        val height = frame.height.coerceAtLeast(1)
        val scale = minOf(
            VOD_PREVIEW_THUMBNAIL_WIDTH_PX.toFloat() / width,
            VOD_PREVIEW_THUMBNAIL_HEIGHT_PX.toFloat() / height,
        )
        if (scale >= 1f) return frame
        val scaled = Bitmap.createScaledBitmap(
            frame,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== frame) frame.recycle()
        return scaled
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
 * while the user keeps seeking. A short bounded warm-up lookahead can be registered speculatively;
 * it never becomes the latest target and is only ever published through the matching-demand path.
 * Every publication carries its source key and bucket, and the UI accepts it only for the matching
 * target. Every failure returns no frame, which keeps the timestamp-only preview without
 * fabricating an image. No media bytes are written to disk and no URL is ever logged.
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

    /**
     * Registers one speculative pre-extraction bucket near the prepared playback position.
     *
     * Warm work never becomes the latest target, so it can only become visible when the user later
     * requests the same bucket; otherwise its decoded frame is cached and never published. Source
     * eligibility and the five-second bucket match [request].
     */
    fun warmUp(timeMs: Long) {
        if (closed || !vodPreviewDecodableCandidate(media)) return
        val bucket = vodPreviewBucketMs(timeMs)
        if (loader.warmUp(bucket)) signal.trySend(Unit)
    }

    /** Drops pending warm-up work; an already-running extraction still finishes and is cached. */
    fun cancelWarmUp() {
        loader.cancelWarmUp()
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

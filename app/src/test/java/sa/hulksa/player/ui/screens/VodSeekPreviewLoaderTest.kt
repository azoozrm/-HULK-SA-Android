package sa.hulksa.player.ui.screens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic conflation, ownership and lifecycle proofs for the bounded Movies seek-preview
 * loader. The production loader is driven directly with latch/barrier-controlled fake decoding, so
 * these tests never sleep and never decode media.
 */
class VodSeekPreviewLoaderTest {

    private class Recorder<T : Any> {
        val publications = mutableListOf<VodSeekPreviewResult<T>>()
        fun publish(result: VodSeekPreviewResult<T>) {
            publications += result
        }

        val latest: VodSeekPreviewResult<T>? get() = publications.lastOrNull()
    }

    @Test
    fun olderCompletionCannotReplaceNewerCachedPublication() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 7,
            load = { bucket ->
                loaded += bucket
                if (bucket == 20_000L) {
                    started.countDown()
                    release.await()
                }
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        // A is cached and published.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(10_000L, recorder.latest?.bucketMs)

        // B extraction starts.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        val worker = Thread { loader.runNext(recorder::publish) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // The user returns to cached A: it publishes immediately and drops obsolete pending work.
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(10_000L, recorder::publish))
        assertEquals(10_000L, recorder.latest?.bucketMs)

        // B completes late: it is cached but must not replace the newer matching publication.
        release.countDown()
        worker.join(2_000)
        assertEquals(10_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(10_000L, 20_000L), loaded)

        // The obsolete completion is still useful from the bounded cache.
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(20_000L, recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)
    }

    @Test
    fun returningToInFlightBucketClearsObsoletePendingAndPublishesOnce() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 3,
            load = { bucket ->
                loaded += bucket
                if (bucket == 10_000L) {
                    started.countDown()
                    release.await()
                }
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        val worker = Thread { loader.runNext(recorder::publish) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // A newer bucket is pending, then the user returns to the in-flight bucket: the obsolete
        // pending bucket must be dropped, and A publishes exactly once.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.AWAITING_IN_FLIGHT, loader.request(10_000L, recorder::publish))
        assertFalse(loader.hasPending())

        release.countDown()
        worker.join(2_000)
        assertEquals(10_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(10_000L), loaded)
    }

    @Test
    fun cachedTargetDropsPendingWorkBeforeItStarts() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 5,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(10_000L, recorder.latest?.bucketMs)

        // B is pending but never starts; returning to cached A drops it.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(10_000L, recorder::publish))
        assertEquals(10_000L, recorder.latest?.bucketMs)
        assertFalse(loader.hasPending())
        assertFalse(loader.runNext(recorder::publish))
        assertEquals(listOf(10_000L), loaded)
    }

    @Test
    fun rapidReversalsKeepOnlyTheLatestPendingAndIgnoreStaleCompletions() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 11,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(30_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(20_000L), loaded)

        // A newer cached target survives a subsequent pending target (no stale overwrite).
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(20_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(30_000L, recorder::publish))
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(20_000L, recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)
        assertFalse(loader.hasPending())
        assertFalse(loader.runNext(recorder::publish))
        assertEquals(listOf(20_000L), loaded)
    }

    @Test
    fun extractionFailureDoesNotStrandPendingWorkOrLeakIdentity() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 9,
            load = { bucket ->
                loaded += bucket
                if (bucket == 10_000L) {
                    started.countDown()
                    release.await()
                    null
                } else {
                    "frame-$bucket"
                }
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        val worker = Thread {
            assertTrue(loader.runNext(recorder::publish))
            assertTrue(loader.runNext(recorder::publish))
        }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))

        release.countDown()
        worker.join(2_000)
        assertEquals(20_000L, recorder.latest?.bucketMs)
        assertEquals(9, recorder.latest?.sourceKey)
        assertNull(recorder.publications.firstOrNull { it.bucketMs == 10_000L })
    }

    @Test
    fun closeRejectsPendingWorkAndBlocksLatePublication() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 13,
            load = {
                started.countDown()
                release.await()
                "frame"
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(10_000L, recorder::publish))
        val worker = Thread { loader.runNext(recorder::publish) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        loader.close()
        assertEquals(VodPreviewRequestDisposition.REJECTED, loader.request(20_000L, recorder::publish))
        release.countDown()
        worker.join(2_000)

        assertTrue(recorder.publications.isEmpty())
        assertFalse(loader.hasPending())
        assertFalse(loader.runNext(recorder::publish))
        assertTrue(recorder.publications.isEmpty())
    }

    @Test
    fun cacheKeepsTheEightBucketBaseline() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<Int>(
            sourceKey = 1,
            load = { bucket ->
                loaded += bucket
                bucket.toInt()
            },
        )
        val recorder = Recorder<Int>()
        repeat(9) { index ->
            val bucket = index * 5_000L
            assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(bucket, recorder::publish))
            assertTrue(loader.runNext(recorder::publish))
            assertEquals(bucket.toInt(), recorder.latest?.value)
        }
        val loadsBefore = loaded.size

        // The oldest bucket was evicted and decodes again; the most recent stays cached.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(0L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(loadsBefore + 1, loaded.size)
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(40_000L, recorder::publish))
        assertFalse(loader.hasPending())
    }

    @Test
    fun publicationIdentityAcceptsOnlyTheMatchingSourceAndBucket() {
        val frame = VodSeekPreviewResult(sourceKey = 5, bucketMs = 10_000L, value = "frame")
        // A target inside the same five-second bucket keeps the nearest-keyframe approximation.
        assertEquals("frame", vodPreviewValueFor(frame, sourceKey = 5, targetMs = 12_000L))
        assertEquals("frame", vodPreviewValueFor(frame, sourceKey = 5, targetMs = 10_000L))
        assertNull(vodPreviewValueFor(frame, sourceKey = 6, targetMs = 10_000L))
        assertNull(vodPreviewValueFor(frame, sourceKey = 5, targetMs = 20_000L))
        assertNull(vodPreviewValueFor(frame, sourceKey = 5, targetMs = null))
        assertNull(vodPreviewValueFor(null, sourceKey = 5, targetMs = 10_000L))
    }

    @Test
    fun warmUpCachesWithoutPublishingAndSatisfiesMatchingDemand() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 21,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertTrue(loader.warmUp(10_000L))
        assertFalse(loader.warmUp(10_000L))
        assertTrue(loader.hasWarmUpPending())
        assertTrue(loader.runNext(recorder::publish))
        // Speculative work never publishes by itself.
        assertTrue(recorder.publications.isEmpty())
        assertFalse(loader.runNext(recorder::publish))
        assertEquals(listOf(10_000L), loaded)

        // A later real request is served from the warm cache without duplicate extraction.
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(10_000L, recorder::publish))
        assertEquals(VodSeekPreviewResult(sourceKey = 21, bucketMs = 10_000L, value = "frame-10000"), recorder.latest)
        assertEquals(listOf(10_000L), loaded)
    }

    @Test
    fun userDemandOvertakesPendingWarmUpWorkAndPromotionAvoidsDuplicates() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 22,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertTrue(loader.warmUp(10_000L))
        assertTrue(loader.warmUp(15_000L))
        assertTrue(loader.warmUp(20_000L))

        // Real demand for a queued warm bucket runs first and removes the speculative duplicate.
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(15_000L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(15_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(15_000L), loaded)

        // Remaining bounded warm work runs after user demand and never replaces the publication.
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(15_000L, recorder.latest?.bucketMs)
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(15_000L, recorder.latest?.bucketMs)
        assertFalse(loader.runNext(recorder::publish))
        assertFalse(loader.hasWarmUpPending())
        assertEquals(listOf(15_000L, 10_000L, 20_000L), loaded)
    }

    @Test
    fun matchingInFlightWarmUpSatisfiesRealRequestWithoutDuplicateExtraction() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 23,
            load = { bucket ->
                loaded += bucket
                if (bucket == 10_000L) {
                    started.countDown()
                    release.await()
                }
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertTrue(loader.warmUp(10_000L))
        val worker = Thread { assertTrue(loader.runNext(recorder::publish)) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        assertEquals(VodPreviewRequestDisposition.AWAITING_IN_FLIGHT, loader.request(10_000L, recorder::publish))
        release.countDown()
        worker.join(2_000)

        assertEquals(listOf(10_000L), loaded)
        assertEquals(10_000L, recorder.latest?.bucketMs)
        assertEquals(23, recorder.latest?.sourceKey)
        assertFalse(loader.hasWarmUpPending())
    }

    @Test
    fun cancelWarmUpDropsPendingSpeculationAndCloseClearsWarmWork() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 24,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertTrue(loader.warmUp(10_000L))
        assertTrue(loader.warmUp(15_000L))
        loader.cancelWarmUp()
        assertFalse(loader.hasWarmUpPending())
        assertFalse(loader.runNext(recorder::publish))
        assertTrue(loaded.isEmpty())

        assertTrue(loader.warmUp(20_000L))
        loader.close()
        assertFalse(loader.runNext(recorder::publish))
        assertTrue(recorder.publications.isEmpty())
        assertEquals(VodPreviewRequestDisposition.REJECTED, loader.request(10_000L, recorder::publish))
    }

    @Test
    fun warmUpNeverOverwritesLatestIntentAndObsoleteWarmCompletionStaysUnpublished() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 25,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)

        // The user still owns 20_000; the speculative completion is cached but not published.
        assertTrue(loader.warmUp(10_000L))
        assertTrue(loader.runNext(recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(20_000L, 10_000L), loaded)

        // Rapid reversal to the warmed bucket is a cache hit with no extra extraction.
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(10_000L, recorder::publish))
        assertEquals(10_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(20_000L, 10_000L), loaded)
    }

    @Test
    fun warmUpFailureDoesNotStrandRealDemand() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 26,
            load = { bucket ->
                loaded += bucket
                if (bucket == 10_000L) {
                    started.countDown()
                    release.await()
                    null
                } else {
                    "frame-$bucket"
                }
            },
        )
        val recorder = Recorder<String>()

        assertTrue(loader.warmUp(10_000L))
        val worker = Thread { assertTrue(loader.runNext(recorder::publish)) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertEquals(VodPreviewRequestDisposition.PENDING_NEW, loader.request(20_000L, recorder::publish))

        release.countDown()
        worker.join(2_000)
        assertFalse(loader.hasWarmUpPending())
        assertTrue(recorder.publications.isEmpty())

        assertTrue(loader.runNext(recorder::publish))
        assertEquals(20_000L, recorder.latest?.bucketMs)
        assertEquals(listOf(10_000L, 20_000L), loaded)
    }

    @Test
    fun warmUpQueueIsBoundedAndServesLaterDemandFromTheBoundedCache() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 27,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )
        val recorder = Recorder<String>()

        repeat(VOD_PREVIEW_WARM_UP_BUCKETS) { index ->
            assertTrue(loader.warmUp(index * 5_000L))
        }
        // No long speculative backlog can be queued.
        assertFalse(loader.warmUp(40_000L))
        while (loader.runNext(recorder::publish)) { }

        assertEquals(VOD_PREVIEW_WARM_UP_BUCKETS, loaded.size)
        assertTrue(recorder.publications.isEmpty())

        // The warmed frame later satisfies a real request without another extraction.
        assertEquals(VodPreviewRequestDisposition.PUBLISHED_CACHE, loader.request(5_000L, recorder::publish))
        assertEquals(5_000L, recorder.latest?.bucketMs)
        assertEquals(VOD_PREVIEW_WARM_UP_BUCKETS, loaded.size)
    }

    @Test
    fun previewExtractionModeIsMoviesOptInAndDefaultsToLegacy() {
        assertEquals(VodPreviewExtractionMode.THUMBNAIL, vodPreviewExtractionMode(isMovie = true))
        assertEquals(VodPreviewExtractionMode.LEGACY_FULL_FRAME, vodPreviewExtractionMode(isMovie = false))
    }

    @Test
    fun legacyExtractionModeNeverUsesTheThumbnailBranches() {
        // The default/pre-R23 mode is the full-frame call at every supported API level.
        listOf(23, 26, 27, 28, 34, 36).forEach { apiLevel ->
            assertEquals(
                VodPreviewFramePlan.FULL_FRAME,
                vodPreviewExtractionPlan(VodPreviewExtractionMode.LEGACY_FULL_FRAME, apiLevel),
            )
        }
    }

    @Test
    fun moviesThumbnailExtractionKeepsTheBoundedPlanBranches() {
        assertEquals(
            VodPreviewFramePlan.DECODE_THEN_SCALE,
            vodPreviewExtractionPlan(
                VodPreviewExtractionMode.THUMBNAIL,
                VOD_PREVIEW_SCALED_FRAME_MIN_API - 1,
            ),
        )
        assertEquals(
            VodPreviewFramePlan.SCALED,
            vodPreviewExtractionPlan(VodPreviewExtractionMode.THUMBNAIL, VOD_PREVIEW_SCALED_FRAME_MIN_API),
        )
        assertEquals(
            VodPreviewFramePlan.SCALED,
            vodPreviewExtractionPlan(VodPreviewExtractionMode.THUMBNAIL, 36),
        )
    }
}

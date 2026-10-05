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
}

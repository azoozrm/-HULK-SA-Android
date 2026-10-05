package sa.hulksa.player.ui.screens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic conflation/lifecycle proofs for the bounded Movies seek-preview loader. The loader
 * is driven directly with latch/barrier-controlled fake decoding, so these tests never sleep and
 * never decode media.
 */
class VodSeekPreviewLoaderTest {

    @Test
    fun rapidAndReversedRequestsKeepOnlyTheLatestPendingBucket() {
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 7,
            load = { bucket ->
                loaded += bucket
                "frame-$bucket"
            },
        )

        assertNull(loader.request(bucketMs = 30_000L))
        assertNull(loader.request(bucketMs = 20_000L))
        assertNull(loader.request(bucketMs = 10_000L))

        val result = loader.runNext()
        assertEquals("frame-10000", result?.value)
        assertEquals(7, result?.sourceKey)
        assertEquals(10_000L, result?.bucketMs)
        assertEquals(listOf(10_000L), loaded)
        assertFalse(loader.hasPending())
        assertNull(loader.runNext())
    }

    @Test
    fun sameBucketRequestWhileInFlightIsCoalescedAndCachedAfterwards() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var loadCount = 0
        val loader = VodSeekPreviewLoader<Int>(
            sourceKey = 3,
            load = {
                loadCount += 1
                started.countDown()
                release.await()
                42
            },
        )

        assertNull(loader.request(bucketMs = 5_000L))
        val workerResult = AtomicReference<VodSeekPreviewResult<Int>?>()
        val worker = Thread { workerResult.set(loader.runNext()) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // A target inside the bucket already being extracted must not queue a second extraction.
        assertNull(loader.request(bucketMs = 5_000L))
        assertFalse(loader.hasPending())
        release.countDown()
        worker.join(2_000)

        assertEquals(42, workerResult.get()?.value)
        assertEquals(1, loadCount)
        // After completion the bucket is cached, so revisiting it never decodes again.
        assertEquals(42, loader.request(bucketMs = 5_000L))
        assertFalse(loader.hasPending())
        assertNull(loader.runNext())
        assertEquals(1, loadCount)
    }

    @Test
    fun anInFlightFrameIsUsableWhileANewerTargetStaysPending() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loaded = mutableListOf<Long>()
        val loader = VodSeekPreviewLoader<String>(
            sourceKey = 11,
            load = { bucket ->
                loaded += bucket
                if (bucket == 10_000L) {
                    started.countDown()
                    release.await()
                }
                "frame-$bucket"
            },
        )

        assertNull(loader.request(10_000L))
        val firstResult = AtomicReference<VodSeekPreviewResult<String>?>()
        val worker = Thread { firstResult.set(loader.runNext()) }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // Continuing seek input arrives while the first extraction is still running.
        assertNull(loader.request(15_000L))
        release.countDown()
        worker.join(2_000)

        // The completed frame is published without waiting for the user to stop seeking, and the
        // newer target survives as the single pending request.
        assertEquals("frame-10000", firstResult.get()?.value)
        assertTrue(loader.hasPending())
        assertEquals("frame-15000", loader.runNext()?.value)
        assertEquals(listOf(10_000L, 15_000L), loaded)
        assertFalse(loader.hasPending())
    }

    @Test
    fun closeRejectsPendingAndFutureResults() {
        val loader = VodSeekPreviewLoader<Int>(sourceKey = 1, load = { 1 })
        assertNull(loader.request(1_000L))
        loader.close()
        assertNull(loader.request(1_000L))
        assertFalse(loader.hasPending())
        assertNull(loader.runNext())
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
        repeat(9) { index ->
            val bucket = index * 5_000L
            assertNull(loader.request(bucket))
            assertEquals(bucket.toInt(), loader.runNext()?.value)
        }
        val loadsBefore = loaded.size

        // The oldest bucket was evicted and decodes again; the most recent stays cached.
        assertNull(loader.request(0L))
        assertEquals(0, loader.runNext()?.value)
        assertEquals(loadsBefore + 1, loaded.size)
        assertNotNull(loader.request(40_000L))
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

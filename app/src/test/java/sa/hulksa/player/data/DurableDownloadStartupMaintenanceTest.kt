package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableDownloadStartupMaintenanceTest {
    @Test
    fun `startup maintenance runs once off caller thread in scrub then capture order`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var maintenanceThreadId = callerThreadId
        val order = mutableListOf<String>()

        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = {
                calls.incrementAndGet()
                maintenanceThreadId = Thread.currentThread().id
                order += "scrub"
                true
            },
            captureLegacyOwner = {
                calls.incrementAndGet()
                order += "capture"
                true
            },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.READY, result)
        assertEquals(2, calls.get())
        assertEquals(listOf("scrub", "capture"), order)
        assertNotEquals(callerThreadId, maintenanceThreadId)
    }

    @Test
    fun `failed scrub keeps startup closed and skips owner capture`() = runBlocking {
        var captureCalled = false

        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = { false },
            captureLegacyOwner = {
                captureCalled = true
                true
            },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.CREDENTIAL_SCRUB_FAILED, result)
        assertFalse(captureCalled)
    }

    @Test
    fun `failed owner capture keeps startup closed`() = runBlocking {
        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = { true },
            captureLegacyOwner = { false },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.LEGACY_OWNER_CAPTURE_FAILED, result)
    }

    @Test
    fun `owner capture exception remains a typed startup failure`() = runBlocking {
        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = { true },
            captureLegacyOwner = { error("storage temporarily unavailable") },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.LEGACY_OWNER_CAPTURE_FAILED, result)
    }

    @Test
    fun `transient scrub failure retries without an account change`() = runBlocking {
        var scrubAttempts = 0
        var captureAttempts = 0
        val observedBackoff = mutableListOf<Long>()

        val result = runDurableDownloadStartupMaintenanceWithRetry(
            maintenance = {
                runDurableDownloadStartupMaintenance(
                    scrubPersistedCredentials = { ++scrubAttempts > 1 },
                    captureLegacyOwner = {
                        captureAttempts++
                        true
                    },
                )
            },
            waitBeforeRetry = { observedBackoff += it },
            onReady = {},
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.READY, result)
        assertEquals(2, scrubAttempts)
        assertEquals(1, captureAttempts)
        assertEquals(listOf(DURABLE_DOWNLOAD_STARTUP_RETRY_BACKOFF_MS.first()), observedBackoff)
    }

    @Test
    fun `success on a later attempt initializes lifecycle once`() = runBlocking {
        var captureAttempts = 0
        var initializationCount = 0

        val result = runDurableDownloadStartupMaintenanceWithRetry(
            maintenance = {
                runDurableDownloadStartupMaintenance(
                    scrubPersistedCredentials = { true },
                    captureLegacyOwner = { ++captureAttempts > 1 },
                )
            },
            waitBeforeRetry = {},
            onReady = { initializationCount++ },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.READY, result)
        assertEquals(2, captureAttempts)
        assertEquals(1, initializationCount)
    }

    @Test
    fun `cancellation stops startup maintenance retry`() = runBlocking {
        val retryWaiting = CompletableDeferred<Unit>()
        var attempts = 0

        val job = launch {
            runDurableDownloadStartupMaintenanceWithRetry(
                maintenance = {
                    attempts++
                    DurableDownloadStartupMaintenanceResult.CREDENTIAL_SCRUB_FAILED
                },
                waitBeforeRetry = {
                    retryWaiting.complete(Unit)
                    awaitCancellation()
                },
                onReady = {},
            )
        }
        retryWaiting.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, attempts)
    }

    @Test
    fun `persistent startup failure stops after bounded attempts`() = runBlocking {
        var attempts = 0
        val observedBackoff = mutableListOf<Long>()

        val result = runDurableDownloadStartupMaintenanceWithRetry(
            maintenance = {
                attempts++
                DurableDownloadStartupMaintenanceResult.LEGACY_OWNER_CAPTURE_FAILED
            },
            waitBeforeRetry = { observedBackoff += it },
            onReady = { error("persistent failure must not initialize lifecycle") },
        )

        assertEquals(DurableDownloadStartupMaintenanceResult.LEGACY_OWNER_CAPTURE_FAILED, result)
        assertEquals(DURABLE_DOWNLOAD_STARTUP_RETRY_BACKOFF_MS.size + 1, attempts)
        assertEquals(DURABLE_DOWNLOAD_STARTUP_RETRY_BACKOFF_MS, observedBackoff)
    }
}

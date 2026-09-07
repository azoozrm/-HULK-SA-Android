package sa.hulksa.player.data

import java.util.concurrent.atomic.AtomicInteger
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

        assertTrue(result)
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

        assertFalse(result)
        assertFalse(captureCalled)
    }

    @Test
    fun `failed owner capture keeps startup closed`() = runBlocking {
        val result = runDurableDownloadStartupMaintenance(
            scrubPersistedCredentials = { true },
            captureLegacyOwner = { false },
        )

        assertFalse(result)
    }
}

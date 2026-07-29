package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTransportPolicyTest {
    @Test
    fun `range capable download requests bytes from zero on first transfer`() {
        val request = buildDownloadRequest(
            url = "http://example.test/movie.mp4",
            offset = 0L,
            supportsRange = true,
        )

        assertEquals("bytes=0-", request.header("Range"))
    }

    @Test
    fun `range capable download resumes from existing bytes`() {
        assertEquals("bytes=4096-", downloadRangeHeader(4096L, supportsRange = true))
    }

    @Test
    fun `non range transport sends a normal full request`() {
        val request = buildDownloadRequest(
            url = "http://example.test/movie.mp4",
            offset = 0L,
            supportsRange = false,
        )

        assertNull(request.header("Range"))
    }

    @Test
    fun `download transport cannot wait forever for the next byte`() {
        assertTrue(DOWNLOAD_STALL_TIMEOUT_SECONDS in 1L..60L)
    }
}

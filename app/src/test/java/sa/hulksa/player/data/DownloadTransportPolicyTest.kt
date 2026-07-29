package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTransportPolicyTest {
    @Test
    fun `range capable download requests a bounded first chunk`() {
        val request = buildDownloadRequest(
            url = "http://example.test/movie.mp4",
            offset = 0L,
            supportsRange = true,
        )

        assertEquals("bytes=0-4194303", request.header("Range"))
    }

    @Test
    fun `range capable download resumes with the next bounded chunk`() {
        assertEquals(
            "bytes=4096-8191",
            downloadRangeHeader(
                offset = 4096L,
                supportsRange = true,
                totalBytes = 10_000L,
                chunkBytes = 4096L,
            ),
        )
    }

    @Test
    fun `last range is clamped to the declared file size`() {
        assertEquals(
            "bytes=8192-9999",
            downloadRangeHeader(
                offset = 8192L,
                supportsRange = true,
                totalBytes = 10_000L,
                chunkBytes = 4096L,
            ),
        )
        assertNull(
            downloadRangeHeader(
                offset = 10_000L,
                supportsRange = true,
                totalBytes = 10_000L,
                chunkBytes = 4096L,
            ),
        )
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
    fun `range transport never emits an open ended request`() {
        val header = downloadRangeHeader(
            offset = 123L,
            supportsRange = true,
            totalBytes = -1L,
        )

        assertTrue(header != null && !header.endsWith("-"))
    }

    @Test
    fun `download transport cannot wait forever for the next byte`() {
        assertTrue(DOWNLOAD_STALL_TIMEOUT_SECONDS in 1L..60L)
    }
}

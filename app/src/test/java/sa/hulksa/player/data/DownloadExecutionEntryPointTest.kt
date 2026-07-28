package sa.hulksa.player.data

import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadExecutionEntryPointTest {
    @Test
    fun `download id must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                validateDurableDownloadId(0L)
            }
        }
    }
}

internal fun validateDurableDownloadId(downloadId: Long) {
    require(downloadId > 0L) { "downloadId must be positive" }
}

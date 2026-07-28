package sa.hulksa.player.data

import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadExecutionEntryPointTest {
    @Test
    fun `download id must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateDurableDownloadId(0L)
        }
    }
}

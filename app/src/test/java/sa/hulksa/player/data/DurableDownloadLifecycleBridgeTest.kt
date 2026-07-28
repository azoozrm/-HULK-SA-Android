package sa.hulksa.player.data

import org.junit.Assert.assertThrows
import org.junit.Test

class DurableDownloadLifecycleBridgeTest {
    @Test
    fun `cancel rejects non positive download id`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateDurableDownloadId(0L)
        }
    }
}

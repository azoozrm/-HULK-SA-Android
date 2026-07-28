package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DurableDownloadForegroundTest {
    @Test
    fun `notification id is stable and positive`() {
        assertEquals(
            durableDownloadNotificationId(42L),
            durableDownloadNotificationId(42L),
        )
        assertNotEquals(0, durableDownloadNotificationId(42L))
    }

    @Test
    fun `different download ids produce different notification ids`() {
        assertNotEquals(
            durableDownloadNotificationId(42L),
            durableDownloadNotificationId(43L),
        )
    }

    @Test
    fun `invalid download id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            durableDownloadNotificationId(0L)
        }
    }
}

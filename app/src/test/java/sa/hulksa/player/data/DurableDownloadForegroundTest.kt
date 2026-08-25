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
    fun `notification actions have independent request codes`() {
        assertNotEquals(
            durableDownloadNotificationRequestCode(42L, owner, ACTION_PAUSE_DOWNLOAD),
            durableDownloadNotificationRequestCode(42L, owner, ACTION_RESUME_DOWNLOAD),
        )
    }

    @Test
    fun `notification request codes stay stable`() {
        assertEquals(
            durableDownloadNotificationRequestCode(42L, owner, ACTION_PAUSE_DOWNLOAD),
            durableDownloadNotificationRequestCode(42L, owner, ACTION_PAUSE_DOWNLOAD),
        )
    }

    @Test
    fun `notification request code is isolated by owner`() {
        assertNotEquals(
            durableDownloadNotificationRequestCode(42L, owner, ACTION_PAUSE_DOWNLOAD),
            durableDownloadNotificationRequestCode(
                42L,
                DownloadOwner("account-b", "primary"),
                ACTION_PAUSE_DOWNLOAD,
            ),
        )
    }

    @Test
    fun `invalid download id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            durableDownloadNotificationId(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            durableDownloadNotificationRequestCode(0L, owner, ACTION_PAUSE_DOWNLOAD)
        }
    }

    private val owner = DownloadOwner("account-a", "primary")
}

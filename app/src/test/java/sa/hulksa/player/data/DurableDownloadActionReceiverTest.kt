package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurableDownloadActionReceiverTest {
    @Test
    fun `pause notification action is recognized`() {
        assertEquals(
            DurableDownloadNotificationAction.PAUSE,
            durableDownloadNotificationAction(ACTION_PAUSE_DOWNLOAD),
        )
    }

    @Test
    fun `unknown notification action is ignored`() {
        assertNull(durableDownloadNotificationAction("unknown"))
        assertNull(durableDownloadNotificationAction(null))
    }
}

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
    fun `resume notification action is recognized`() {
        assertEquals(
            DurableDownloadNotificationAction.RESUME,
            durableDownloadNotificationAction(ACTION_RESUME_DOWNLOAD),
        )
    }

    @Test
    fun `unknown notification action is ignored`() {
        assertNull(durableDownloadNotificationAction("unknown"))
        assertNull(durableDownloadNotificationAction(null))
    }

    @Test
    fun `pause notification is dispatched asynchronously while resume remains direct`() {
        assertEquals(
            DurableDownloadReceiverExecution.ASYNC_PAUSE,
            durableDownloadReceiverExecution(ACTION_PAUSE_DOWNLOAD),
        )
        assertEquals(
            DurableDownloadReceiverExecution.DIRECT_RESUME,
            durableDownloadReceiverExecution(ACTION_RESUME_DOWNLOAD),
        )
        assertNull(durableDownloadReceiverExecution("unknown"))
    }
}

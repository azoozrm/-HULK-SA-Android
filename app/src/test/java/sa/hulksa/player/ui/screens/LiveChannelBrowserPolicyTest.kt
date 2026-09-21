package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LiveChannelBrowserPolicyTest {
    @Test
    fun `normal live origin keeps the canonical channel browser title`() {
        assertEquals(
            LiveChannelBrowserCopy(
                title = "القنوات المباشرة",
                description = "اختر قناة أو تنقل بين الفئات",
            ),
            liveChannelBrowserCopy(LiveChannelBrowserOrigin.NORMAL_LIVE),
        )
    }

    @Test
    fun `error recovery origin keeps the same title with recovery guidance`() {
        val copy = liveChannelBrowserCopy(LiveChannelBrowserOrigin.ERROR_RECOVERY)

        assertEquals("القنوات المباشرة", copy.title)
        assertNotEquals(
            liveChannelBrowserCopy(LiveChannelBrowserOrigin.NORMAL_LIVE).description,
            copy.description,
        )
    }
}

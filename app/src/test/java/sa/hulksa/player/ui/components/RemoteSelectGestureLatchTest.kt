package sa.hulksa.player.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The latch is the exact row-local state used by ChannelListItem's remote SELECT path.
 * A failed removal may leave the row in place while the shell consumes its release; the next
 * fresh initial KeyDown must reset the latch so the first independent short SELECT works.
 */
class RemoteSelectGestureLatchTest {
    @Test
    fun `failed hold release consumed by shell then first fresh short select works`() {
        val latch = RemoteSelectGestureLatch()
        assertFalse(latch.onKeyDown(repeatCount = 0, isLongPress = false))
        assertTrue(latch.onKeyDown(repeatCount = 1, isLongPress = false))
        // The shell owns the original KeyUp, so the row-local latch remains handled.
        assertFalse("fresh initial KeyDown resets the latch", latch.onKeyDown(0, false))
        assertFalse("fresh short SELECT reaches onClick", latch.onKeyUp())
    }

    @Test
    fun `first fresh long hold works after a failed attempt`() {
        val latch = RemoteSelectGestureLatch()
        latch.onKeyDown(0, false)
        assertTrue(latch.onKeyDown(1, false))
        latch.onKeyDown(0, false)
        assertTrue(latch.onKeyDown(1, false))
        assertTrue("the held release belongs to the fresh long hold", latch.onKeyUp())
    }

    @Test
    fun `one hold produces one action and repeats do not re-arm`() {
        val latch = RemoteSelectGestureLatch()
        assertFalse(latch.onKeyDown(0, false))
        assertTrue(latch.onKeyDown(1, false))
        repeat(6) { assertFalse(latch.onKeyDown(2, false)) }
        assertTrue(latch.onKeyUp())
    }

    @Test
    fun `normal short select remains a click`() {
        val latch = RemoteSelectGestureLatch()
        assertFalse(latch.onKeyDown(0, false))
        assertFalse(latch.onKeyUp())
    }
}

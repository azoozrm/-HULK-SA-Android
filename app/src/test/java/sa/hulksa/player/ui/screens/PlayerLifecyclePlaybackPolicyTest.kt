package sa.hulksa.player.ui.screens

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLifecyclePlaybackPolicyTest {
    @Test
    fun `live playback stops when app enters background`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.STOP,
            playerBackgroundLifecycleAction(Player.STATE_READY),
        )
    }

    @Test
    fun `movie buffering stops when app enters background`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.STOP,
            playerBackgroundLifecycleAction(Player.STATE_BUFFERING),
        )
    }

    @Test
    fun `episode playback stops when app enters background`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.STOP,
            playerBackgroundLifecycleAction(Player.STATE_READY),
        )
    }

    @Test
    fun `returning to foreground prepares the existing stopped player once`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.PREPARE,
            playerForegroundLifecycleAction(
                playbackState = Player.STATE_IDLE,
                hasMediaItem = true,
                hasPlaybackError = false,
                sourceAvailable = true,
            ),
        )
        assertEquals(
            PlayerLifecyclePlaybackAction.NONE,
            playerForegroundLifecycleAction(
                playbackState = Player.STATE_READY,
                hasMediaItem = true,
                hasPlaybackError = false,
                sourceAvailable = true,
            ),
        )
    }

    @Test
    fun `finished playback is not restarted by background lifecycle`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.NONE,
            playerBackgroundLifecycleAction(Player.STATE_ENDED),
        )
    }

    @Test
    fun `episode autoplay remains suspended while app is backgrounded`() {
        assertFalse(shouldAdvancePlayerAutoplayCountdown(appForeground = false, countdown = 4))
        assertTrue(shouldAdvancePlayerAutoplayCountdown(appForeground = true, countdown = 4))
    }

    @Test
    fun `ordinary foreground lifecycle does not stop or restart active playback`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.NONE,
            playerForegroundLifecycleAction(
                playbackState = Player.STATE_READY,
                hasMediaItem = true,
                hasPlaybackError = false,
                sourceAvailable = true,
            ),
        )
    }

    @Test
    fun `foreground does not retry failed or unavailable playback`() {
        assertEquals(
            PlayerLifecyclePlaybackAction.NONE,
            playerForegroundLifecycleAction(
                playbackState = Player.STATE_IDLE,
                hasMediaItem = true,
                hasPlaybackError = true,
                sourceAvailable = true,
            ),
        )
        assertEquals(
            PlayerLifecyclePlaybackAction.NONE,
            playerForegroundLifecycleAction(
                playbackState = Player.STATE_IDLE,
                hasMediaItem = true,
                hasPlaybackError = false,
                sourceAvailable = false,
            ),
        )
    }
}

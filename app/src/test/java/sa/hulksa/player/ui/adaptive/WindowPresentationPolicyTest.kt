package sa.hulksa.player.ui.adaptive

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowPresentationPolicyTest {
    @Test
    fun `mobile shell uses edge to edge without immersive mode`() {
        val policy = resolveWindowPresentationPolicy(
            isTelevisionDevice = false,
            isPlayer = false,
        )

        assertEquals(HulkSystemBarsMode.EDGE_TO_EDGE, policy.systemBarsMode)
        assertEquals(HulkOrientationRequest.KEEP_CURRENT, policy.orientationRequest)
    }

    @Test
    fun `mobile player is immersive landscape`() {
        val policy = resolveWindowPresentationPolicy(
            isTelevisionDevice = false,
            isPlayer = true,
        )

        assertEquals(HulkSystemBarsMode.IMMERSIVE, policy.systemBarsMode)
        assertEquals(HulkOrientationRequest.SENSOR_LANDSCAPE, policy.orientationRequest)
    }

    @Test
    fun `portrait shell is restored after leaving player`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            restoreOrientationRequest(Configuration.ORIENTATION_PORTRAIT),
        )
    }

    @Test
    fun `landscape shell is restored when player was opened from landscape`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            restoreOrientationRequest(Configuration.ORIENTATION_LANDSCAPE),
        )
    }

    @Test
    fun `undefined pre-player orientation falls back to portrait`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            restoreOrientationRequest(Configuration.ORIENTATION_UNDEFINED),
        )
    }

    @Test
    fun `television remains immersive without changing activity orientation`() {
        val policy = resolveWindowPresentationPolicy(
            isTelevisionDevice = true,
            isPlayer = true,
        )

        assertEquals(HulkSystemBarsMode.IMMERSIVE, policy.systemBarsMode)
        assertEquals(HulkOrientationRequest.KEEP_CURRENT, policy.orientationRequest)
    }
}

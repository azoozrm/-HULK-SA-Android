package sa.hulksa.player.ui.adaptive

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
    fun `television remains immersive without changing activity orientation`() {
        val policy = resolveWindowPresentationPolicy(
            isTelevisionDevice = true,
            isPlayer = true,
        )

        assertEquals(HulkSystemBarsMode.IMMERSIVE, policy.systemBarsMode)
        assertEquals(HulkOrientationRequest.KEEP_CURRENT, policy.orientationRequest)
    }
}

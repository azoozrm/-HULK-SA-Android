package sa.hulksa.player

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationPermissionTest {
    @Test
    fun `android thirteen phone requests missing permission`() {
        assertTrue(
            shouldRequestDownloadNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = false,
                televisionDevice = false,
            ),
        )
    }

    @Test
    fun `granted permission is never requested again`() {
        assertFalse(
            shouldRequestDownloadNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = true,
                televisionDevice = false,
            ),
        )
    }

    @Test
    fun `television devices skip notification prompt`() {
        assertFalse(
            shouldRequestDownloadNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = false,
                televisionDevice = true,
            ),
        )
    }

    @Test
    fun `older android versions do not request runtime permission`() {
        assertFalse(
            shouldRequestDownloadNotificationPermission(
                sdkInt = Build.VERSION_CODES.S_V2,
                permissionGranted = false,
                televisionDevice = false,
            ),
        )
    }
}

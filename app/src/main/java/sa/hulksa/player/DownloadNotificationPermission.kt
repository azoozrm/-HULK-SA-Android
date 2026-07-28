package sa.hulksa.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity

internal fun shouldRequestDownloadNotificationPermission(
    sdkInt: Int,
    permissionGranted: Boolean,
    televisionDevice: Boolean,
): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU &&
        !permissionGranted &&
        !televisionDevice

internal fun ComponentActivity.requestDownloadNotificationPermissionIfNeeded(
    televisionDevice: Boolean,
) {
    val permissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (
        shouldRequestDownloadNotificationPermission(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = permissionGranted,
            televisionDevice = televisionDevice,
        )
    ) {
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST_CODE,
        )
    }
}

private const val DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST_CODE = 9031

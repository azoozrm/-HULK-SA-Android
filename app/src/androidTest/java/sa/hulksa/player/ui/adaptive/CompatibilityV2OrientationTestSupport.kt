package sa.hulksa.player.ui.adaptive

import android.content.pm.ActivityInfo
import android.content.res.Configuration

/** Android-test-only orientation helper used by Compatibility Lab V2. */
internal fun restoreOrientationRequest(prePlayerOrientation: Int): Int =
    if (prePlayerOrientation == Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

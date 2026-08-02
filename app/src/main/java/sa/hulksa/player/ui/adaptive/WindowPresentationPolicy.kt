package sa.hulksa.player.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

internal enum class HulkSystemBarsMode {
    EDGE_TO_EDGE,
    IMMERSIVE,
}

internal enum class HulkOrientationRequest {
    KEEP_CURRENT,
    SENSOR_LANDSCAPE,
}

internal data class HulkWindowPresentationPolicy(
    val systemBarsMode: HulkSystemBarsMode,
    val orientationRequest: HulkOrientationRequest,
)

internal fun resolveWindowPresentationPolicy(
    isTelevisionDevice: Boolean,
    isPlayer: Boolean,
): HulkWindowPresentationPolicy = when {
    isTelevisionDevice -> HulkWindowPresentationPolicy(
        systemBarsMode = HulkSystemBarsMode.IMMERSIVE,
        orientationRequest = HulkOrientationRequest.KEEP_CURRENT,
    )

    isPlayer -> HulkWindowPresentationPolicy(
        systemBarsMode = HulkSystemBarsMode.IMMERSIVE,
        orientationRequest = HulkOrientationRequest.SENSOR_LANDSCAPE,
    )

    else -> HulkWindowPresentationPolicy(
        systemBarsMode = HulkSystemBarsMode.EDGE_TO_EDGE,
        orientationRequest = HulkOrientationRequest.KEEP_CURRENT,
    )
}

@Composable
fun ApplyAdaptiveWindowPresentation(
    isTelevisionDevice: Boolean,
    isPlayer: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val policy = resolveWindowPresentationPolicy(isTelevisionDevice, isPlayer)

    DisposableEffect(activity, policy, isTelevisionDevice) {
        if (activity == null) return@DisposableEffect onDispose { }

        val previousOrientation = activity.requestedOrientation
        applySystemBars(activity, policy.systemBarsMode)
        if (policy.orientationRequest == HulkOrientationRequest.SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        onDispose {
            if (policy.orientationRequest == HulkOrientationRequest.SENSOR_LANDSCAPE) {
                activity.requestedOrientation = previousOrientation
            }
            applySystemBars(
                activity,
                resolveWindowPresentationPolicy(
                    isTelevisionDevice = isTelevisionDevice,
                    isPlayer = false,
                ).systemBarsMode,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
private fun applySystemBars(activity: Activity, mode: HulkSystemBarsMode) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.let { controller ->
            when (mode) {
                HulkSystemBarsMode.IMMERSIVE -> {
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.systemBars())
                }

                HulkSystemBarsMode.EDGE_TO_EDGE -> {
                    controller.show(WindowInsets.Type.systemBars())
                }
            }
        }
    } else {
        window.decorView.systemUiVisibility = when (mode) {
            HulkSystemBarsMode.IMMERSIVE ->
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            HulkSystemBarsMode.EDGE_TO_EDGE ->
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}

package sa.hulksa.player.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

internal fun restoreOrientationRequest(prePlayerOrientation: Int): Int =
    if (prePlayerOrientation == Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

@Composable
fun ApplyAdaptiveWindowPresentation(
    isTelevisionDevice: Boolean,
    isPlayer: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val policy = resolveWindowPresentationPolicy(isTelevisionDevice, isPlayer)
    var wasPlayer by remember(activity) { mutableStateOf(false) }
    var prePlayerOrientation by remember(activity) {
        mutableIntStateOf(Configuration.ORIENTATION_UNDEFINED)
    }

    DisposableEffect(activity, policy, isTelevisionDevice) {
        if (activity == null) return@DisposableEffect onDispose { }

        applySystemBars(activity, policy.systemBarsMode)

        onDispose {
            applySystemBars(
                activity,
                resolveWindowPresentationPolicy(
                    isTelevisionDevice = isTelevisionDevice,
                    isPlayer = false,
                ).systemBarsMode,
            )
        }
    }

    LaunchedEffect(activity, isTelevisionDevice, isPlayer) {
        if (activity == null) return@LaunchedEffect

        if (isTelevisionDevice) {
            wasPlayer = isPlayer
            return@LaunchedEffect
        }

        when {
            isPlayer && !wasPlayer -> {
                prePlayerOrientation = activity.resources.configuration.orientation
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            !isPlayer && wasPlayer -> {
                activity.requestedOrientation = restoreOrientationRequest(prePlayerOrientation)
            }
        }
        wasPlayer = isPlayer
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

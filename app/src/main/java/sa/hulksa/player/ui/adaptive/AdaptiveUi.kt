package sa.hulksa.player.ui.adaptive

import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.roundToInt

enum class HulkDeviceClass {
    MOBILE,
    TABLET,
    TELEVISION,
}

enum class HulkWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class HulkInputMode {
    TOUCH,
    REMOTE,
    KEYBOARD,
}

enum class HulkNavigationType {
    TOP_BAR,
    RAIL,
}

@Immutable
data class AdaptiveUiState(
    val deviceClass: HulkDeviceClass,
    val windowWidthClass: HulkWindowWidthClass,
    val navigationType: HulkNavigationType,
    val inputMode: HulkInputMode,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
) {
    val isTelevision: Boolean get() = deviceClass == HulkDeviceClass.TELEVISION

    val showFocusHighlights: Boolean
        get() = shouldShowFocusHighlights(deviceClass, inputMode)

    val tvPremiumPolicy: TvPremiumWindowPolicy
        get() = tvPremiumWindowPolicy(screenWidthDp, screenHeightDp)
}

/**
 * Shared Android TV / Google TV presentation policy for v2.0.
 *
 * The policy is based on the current Compose window, not the physical panel resolution. This is
 * important for TV boxes whose density reporting can vary between 720p, 1080p and 4K displays.
 * Screens can adopt these tokens progressively without changing mobile or tablet behavior.
 */
@Immutable
data class TvPremiumWindowPolicy(
    val horizontalSafeInsetDp: Float,
    val verticalSafeInsetDp: Float,
    val contentWidthFraction: Float,
    val railLogoSizeDp: Float,
    val railExpandedWidthDp: Float,
    val railCollapsedWidthDp: Float,
    val railItemMinHeightDp: Float,
    val railItemVerticalPaddingDp: Float,
    val focusBorderWidthDp: Float,
    val focusScale: Float,
)

fun tvPremiumWindowPolicy(
    screenWidthDp: Int,
    screenHeightDp: Int,
): TvPremiumWindowPolicy {
    val width = screenWidthDp.coerceAtLeast(1).toFloat()
    val height = screenHeightDp.coerceAtLeast(1).toFloat()

    // Compact TV canvases need extra protection from overscan. Larger canvases keep a stable
    // living-room margin while allowing the content to use most of the available width.
    val widthPressure = ((1280f - width) / 320f).coerceIn(0f, 1f)
    val heightPressure = ((720f - height) / 180f).coerceIn(0f, 1f)
    val compactPressure = maxOf(widthPressure, heightPressure)

    val compactTv = width <= 960f || height <= 540f
    val standardTv = !compactTv && (width <= 1280f || height <= 720f)

    val contentWidthFraction = when {
        compactTv -> 0.96f
        standardTv -> 0.95f
        else -> 0.94f
    }

    return TvPremiumWindowPolicy(
        horizontalSafeInsetDp = 8f + (10f * compactPressure),
        verticalSafeInsetDp = 8f + (8f * compactPressure),
        contentWidthFraction = contentWidthFraction,
        railLogoSizeDp = (width / 32f).coerceIn(28f, 60f),
        railExpandedWidthDp = when {
            compactTv -> 188f
            standardTv -> 212f
            else -> 236f
        },
        railCollapsedWidthDp = when {
            compactTv -> 64f
            standardTv -> 68f
            else -> 72f
        },
        railItemMinHeightDp = when {
            compactTv -> 44f
            standardTv -> 48f
            else -> 52f
        },
        railItemVerticalPaddingDp = when {
            compactTv -> 8f
            standardTv -> 9f
            else -> 10f
        },
        focusBorderWidthDp = 2f,
        focusScale = when {
            compactTv -> 1.02f
            standardTv -> 1.025f
            else -> 1.03f
        },
    )
}

@Stable
class AdaptiveInputController internal constructor(
    initialMode: HulkInputMode,
    private val isTelevisionDevice: Boolean = false,
) {
    var mode by mutableStateOf(initialMode)
        private set

    fun recordTouchInput() {
        if (mode != HulkInputMode.TOUCH) mode = HulkInputMode.TOUCH
    }

    fun recordKeyInput(source: Int) {
        val nextMode = classifyInputSource(source)
        if (mode != nextMode) mode = nextMode
    }

    fun recordKeyInput(source: Int, keyCode: Int) {
        val nextMode = classifyInputEvent(
            source = source,
            keyCode = keyCode,
            isTelevisionDevice = isTelevisionDevice,
        )
        if (mode != nextMode) mode = nextMode
    }
}

val LocalAdaptiveUi = staticCompositionLocalOf {
    AdaptiveUiState(
        deviceClass = HulkDeviceClass.MOBILE,
        windowWidthClass = HulkWindowWidthClass.COMPACT,
        navigationType = HulkNavigationType.TOP_BAR,
        inputMode = HulkInputMode.TOUCH,
        screenWidthDp = 360,
        screenHeightDp = 640,
    )
}

@Composable
fun rememberAdaptiveUiState(
    isTelevisionDevice: Boolean,
): Pair<AdaptiveUiState, AdaptiveInputController> {
    val windowSize = LocalWindowInfo.current.containerDpSize
    val widthDp = windowSize.width.value.roundToInt().coerceAtLeast(1)
    val heightDp = windowSize.height.value.roundToInt().coerceAtLeast(1)
    val smallestWidthDp = minOf(widthDp, heightDp)
    val deviceClass = classifyDeviceClass(
        isTelevisionDevice = isTelevisionDevice,
        smallestWidthDp = smallestWidthDp,
        widthDp = widthDp,
    )
    val windowWidthClass = classifyWindowWidth(widthDp)
    val controller = remember(isTelevisionDevice) {
        AdaptiveInputController(
            initialMode = if (isTelevisionDevice) HulkInputMode.REMOTE else HulkInputMode.TOUCH,
            isTelevisionDevice = isTelevisionDevice,
        )
    }
    val state = AdaptiveUiState(
        deviceClass = deviceClass,
        windowWidthClass = windowWidthClass,
        navigationType = selectNavigationType(deviceClass, windowWidthClass),
        inputMode = controller.mode,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
    )
    return state to controller
}

fun Modifier.trackAdaptiveInput(controller: AdaptiveInputController): Modifier =
    pointerInput(controller) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                controller.recordTouchInput()
            }
        }
    }.onPreviewKeyEvent { event ->
        controller.recordKeyInput(
            source = event.nativeKeyEvent.source,
            keyCode = event.nativeKeyEvent.keyCode,
        )
        false
    }

fun classifyInputSource(source: Int): HulkInputMode {
    val isRemote =
        source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    return if (isRemote) HulkInputMode.REMOTE else HulkInputMode.KEYBOARD
}

/**
 * Some Android TV remotes report DPAD keys with SOURCE_KEYBOARD. On a television we still treat
 * those navigation key codes as REMOTE so downstream TV surfaces do not flip interaction models
 * while the user is moving through the rail. Non-TV devices keep normal keyboard semantics.
 */
fun classifyInputEvent(
    source: Int,
    keyCode: Int,
    isTelevisionDevice: Boolean,
): HulkInputMode {
    val sourceMode = classifyInputSource(source)
    if (sourceMode == HulkInputMode.REMOTE) return HulkInputMode.REMOTE

    return if (isTelevisionDevice && isTvNavigationKey(keyCode)) {
        HulkInputMode.REMOTE
    } else {
        sourceMode
    }
}

private fun isTvNavigationKey(keyCode: Int): Boolean = when (keyCode) {
    AndroidKeyEvent.KEYCODE_DPAD_UP,
    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_PAGE_UP,
    AndroidKeyEvent.KEYCODE_PAGE_DOWN,
    -> true
    else -> false
}

fun classifyWindowWidth(widthDp: Int): HulkWindowWidthClass = when {
    widthDp < 600 -> HulkWindowWidthClass.COMPACT
    widthDp < 840 -> HulkWindowWidthClass.MEDIUM
    else -> HulkWindowWidthClass.EXPANDED
}

fun classifyDeviceClass(
    isTelevisionDevice: Boolean,
    smallestWidthDp: Int,
    widthDp: Int,
): HulkDeviceClass = when {
    isTelevisionDevice -> HulkDeviceClass.TELEVISION
    smallestWidthDp >= 600 -> HulkDeviceClass.TABLET
    else -> HulkDeviceClass.MOBILE
}

fun selectNavigationType(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
): HulkNavigationType = when {
    deviceClass == HulkDeviceClass.TELEVISION -> HulkNavigationType.RAIL
    deviceClass == HulkDeviceClass.TABLET && windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}

fun shouldShowFocusHighlights(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass == HulkDeviceClass.TELEVISION || inputMode != HulkInputMode.TOUCH

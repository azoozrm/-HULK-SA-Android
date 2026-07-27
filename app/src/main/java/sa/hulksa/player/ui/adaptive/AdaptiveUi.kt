package sa.hulksa.player.ui.adaptive

import android.view.InputDevice
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
}

@Stable
class AdaptiveInputController internal constructor(initialMode: HulkInputMode) {
    var mode by mutableStateOf(initialMode)
        private set

    fun recordTouchInput() {
        if (mode != HulkInputMode.TOUCH) mode = HulkInputMode.TOUCH
    }

    fun recordKeyInput(source: Int) {
        val nextMode = classifyInputSource(source)
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
            if (isTelevisionDevice) HulkInputMode.REMOTE else HulkInputMode.TOUCH,
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
        controller.recordKeyInput(event.nativeKeyEvent.source)
        false
    }

fun classifyInputSource(source: Int): HulkInputMode {
    val isRemote =
        source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    return if (isRemote) HulkInputMode.REMOTE else HulkInputMode.KEYBOARD
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
    smallestWidthDp >= 600 || widthDp >= 840 -> HulkDeviceClass.TABLET
    else -> HulkDeviceClass.MOBILE
}

fun selectNavigationType(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
): HulkNavigationType = when {
    deviceClass == HulkDeviceClass.TELEVISION -> HulkNavigationType.RAIL
    windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}

fun shouldShowFocusHighlights(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass == HulkDeviceClass.TELEVISION || inputMode != HulkInputMode.TOUCH

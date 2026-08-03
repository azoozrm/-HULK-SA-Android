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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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

enum class HulkWindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class HulkOrientation {
    PORTRAIT,
    LANDSCAPE,
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

enum class HulkContentDensity {
    COMPACT,
    COMFORTABLE,
    SPACIOUS,
}

@Immutable
data class AdaptiveLayoutPolicy(
    val navigationType: HulkNavigationType,
    val contentDensity: HulkContentDensity,
    val pageHorizontalPaddingDp: Int,
    val pageVerticalPaddingDp: Int,
    val contentSpacingDp: Int,
    val minimumPosterWidthDp: Int,
    val minimumTouchTargetDp: Int,
    val maximumContentWidthDp: Int?,
    val useTwoPane: Boolean,
    val restoreFocus: Boolean,
)

@Immutable
data class AdaptiveUiState(
    val deviceClass: HulkDeviceClass,
    val windowWidthClass: HulkWindowWidthClass,
    val navigationType: HulkNavigationType,
    val inputMode: HulkInputMode,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val windowHeightClass: HulkWindowHeightClass = classifyWindowHeight(screenHeightDp),
    val orientation: HulkOrientation = classifyOrientation(screenWidthDp, screenHeightDp),
    val deviceSmallestWidthDp: Int = minOf(screenWidthDp, screenHeightDp),
    val fontScale: Float = 1f,
    val density: Float = 1f,
) {
    val isTelevision: Boolean get() = deviceClass == HulkDeviceClass.TELEVISION

    val showFocusHighlights: Boolean
        get() = shouldShowFocusHighlights(deviceClass, inputMode)

    val layoutPolicy: AdaptiveLayoutPolicy
        get() = resolveAdaptiveLayoutPolicy(
            deviceClass = deviceClass,
            windowWidthClass = windowWidthClass,
            windowHeightClass = windowHeightClass,
            orientation = orientation,
            inputMode = inputMode,
        )
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
    val configuration = LocalConfiguration.current
    val localDensity = LocalDensity.current
    val widthDp = windowSize.width.value.roundToInt().coerceAtLeast(1)
    val heightDp = windowSize.height.value.roundToInt().coerceAtLeast(1)
    val deviceSmallestWidthDp = configuration.smallestScreenWidthDp
        .takeIf { it > 0 }
        ?: minOf(widthDp, heightDp)
    val deviceClass = classifyDeviceClass(
        isTelevisionDevice = isTelevisionDevice,
        smallestWidthDp = deviceSmallestWidthDp,
        widthDp = widthDp,
    )
    val windowWidthClass = classifyWindowWidth(widthDp)
    val windowHeightClass = classifyWindowHeight(heightDp)
    val controller = remember(isTelevisionDevice) {
        AdaptiveInputController(
            if (isTelevisionDevice) HulkInputMode.REMOTE else HulkInputMode.TOUCH,
        )
    }
    val state = AdaptiveUiState(
        deviceClass = deviceClass,
        windowWidthClass = windowWidthClass,
        windowHeightClass = windowHeightClass,
        orientation = classifyOrientation(widthDp, heightDp),
        navigationType = selectNavigationType(deviceClass, windowWidthClass),
        inputMode = controller.mode,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        deviceSmallestWidthDp = deviceSmallestWidthDp,
        fontScale = localDensity.fontScale,
        density = localDensity.density,
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

fun classifyWindowHeight(heightDp: Int): HulkWindowHeightClass = when {
    heightDp < 480 -> HulkWindowHeightClass.COMPACT
    heightDp < 900 -> HulkWindowHeightClass.MEDIUM
    else -> HulkWindowHeightClass.EXPANDED
}

fun classifyOrientation(widthDp: Int, heightDp: Int): HulkOrientation =
    if (widthDp >= heightDp) HulkOrientation.LANDSCAPE else HulkOrientation.PORTRAIT

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
    deviceClass == HulkDeviceClass.TABLET && windowWidthClass != HulkWindowWidthClass.COMPACT -> HulkNavigationType.RAIL
    else -> HulkNavigationType.TOP_BAR
}

fun resolveAdaptiveLayoutPolicy(
    deviceClass: HulkDeviceClass,
    windowWidthClass: HulkWindowWidthClass,
    windowHeightClass: HulkWindowHeightClass,
    orientation: HulkOrientation,
    inputMode: HulkInputMode,
): AdaptiveLayoutPolicy = when {
    deviceClass == HulkDeviceClass.TELEVISION -> AdaptiveLayoutPolicy(
        navigationType = HulkNavigationType.RAIL,
        contentDensity = if (windowHeightClass == HulkWindowHeightClass.COMPACT) {
            HulkContentDensity.COMPACT
        } else {
            HulkContentDensity.COMFORTABLE
        },
        pageHorizontalPaddingDp = 8,
        pageVerticalPaddingDp = 8,
        contentSpacingDp = if (windowHeightClass == HulkWindowHeightClass.COMPACT) 12 else 16,
        minimumPosterWidthDp = if (windowWidthClass == HulkWindowWidthClass.COMPACT) 108 else 132,
        minimumTouchTargetDp = 48,
        maximumContentWidthDp = null,
        useTwoPane = false,
        restoreFocus = true,
    )

    deviceClass == HulkDeviceClass.TABLET && windowWidthClass != HulkWindowWidthClass.COMPACT ->
        AdaptiveLayoutPolicy(
            navigationType = HulkNavigationType.RAIL,
            contentDensity = if (windowWidthClass == HulkWindowWidthClass.EXPANDED) {
                HulkContentDensity.SPACIOUS
            } else {
                HulkContentDensity.COMFORTABLE
            },
            pageHorizontalPaddingDp = if (windowWidthClass == HulkWindowWidthClass.EXPANDED) 24 else 20,
            pageVerticalPaddingDp = 16,
            contentSpacingDp = if (windowWidthClass == HulkWindowWidthClass.EXPANDED) 20 else 16,
            minimumPosterWidthDp = if (windowWidthClass == HulkWindowWidthClass.EXPANDED) 168 else 148,
            minimumTouchTargetDp = 48,
            maximumContentWidthDp = if (windowWidthClass == HulkWindowWidthClass.EXPANDED) 1600 else 1200,
            useTwoPane = windowWidthClass == HulkWindowWidthClass.EXPANDED && orientation == HulkOrientation.LANDSCAPE,
            restoreFocus = inputMode != HulkInputMode.TOUCH,
        )

    else -> AdaptiveLayoutPolicy(
        navigationType = HulkNavigationType.TOP_BAR,
        contentDensity = if (windowHeightClass == HulkWindowHeightClass.COMPACT) {
            HulkContentDensity.COMPACT
        } else {
            HulkContentDensity.COMFORTABLE
        },
        pageHorizontalPaddingDp = if (windowWidthClass == HulkWindowWidthClass.COMPACT) 12 else 16,
        pageVerticalPaddingDp = if (windowHeightClass == HulkWindowHeightClass.COMPACT) 8 else 12,
        contentSpacingDp = if (windowHeightClass == HulkWindowHeightClass.COMPACT) 10 else 14,
        minimumPosterWidthDp = if (windowWidthClass == HulkWindowWidthClass.COMPACT) 112 else 132,
        minimumTouchTargetDp = 48,
        maximumContentWidthDp = if (deviceClass == HulkDeviceClass.TABLET) 960 else 720,
        useTwoPane = false,
        restoreFocus = inputMode != HulkInputMode.TOUCH,
    )
}

fun shouldShowFocusHighlights(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass == HulkDeviceClass.TELEVISION || inputMode != HulkInputMode.TOUCH

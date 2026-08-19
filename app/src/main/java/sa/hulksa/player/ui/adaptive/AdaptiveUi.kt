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
    val railCollapsedWidthDp: Float,
    val railExpandedWidthDp: Float,
    val railLogoSizeDp: Float,
    val railItemHeightDp: Float,
    val railIconSizeDp: Float,
    val railLabelSizeSp: Float,
    val railOuterHorizontalPaddingDp: Float,
    val railItemHorizontalPaddingDp: Float,
    val railIconLabelGapDp: Float,
    val railLogoItemGapDp: Float,
    val railItemGapDp: Float,
    val railTopPaddingDp: Float,
    val railBottomPaddingDp: Float,
    val railCornerRadiusDp: Float,
    val focusBorderWidthDp: Float,
    val focusScale: Float,
)

fun tvPremiumWindowPolicy(
    screenWidthDp: Int,
    screenHeightDp: Int,
): TvPremiumWindowPolicy {
    val width = screenWidthDp.coerceAtLeast(1).toFloat()
    val height = screenHeightDp.coerceAtLeast(1).toFloat()
    val shortSide = minOf(width, height)

    // Compact TV canvases need extra protection from overscan. Larger canvases keep a stable
    // living-room margin while allowing the content to use most of the available width.
    val widthPressure = ((1280f - width) / 320f).coerceIn(0f, 1f)
    val heightPressure = ((720f - height) / 180f).coerceIn(0f, 1f)
    val compactPressure = maxOf(widthPressure, heightPressure)

    val compactTv = width <= 960f || height <= 540f
    val standardTv = !compactTv && (width <= 1280f || height <= 720f)

    // These rail calculations intentionally preserve the already-qualified shell proportions.
    // v2.0 centralizes them here so every TV surface can share one deterministic source of truth.
    val railCollapsedWidth = (width / 14f).coerceIn(88f, 102f)
    val railExpandedWidth = (width / 6.2f).coerceIn(194f, 236f)
    val railLogoSize = (shortSide / 10f).coerceIn(54f, 78f)
    val railItemHeight = (height / 14.5f).coerceIn(46f, 56f)
    val railIconSize = (height / 30f).coerceIn(23f, 28f)
    val railLabelSize = (height / 50f).coerceIn(14f, 17f)
    val railOuterHorizontalPadding = (railCollapsedWidth * .11f).coerceIn(9f, 12f)
    val railItemHorizontalPadding = (railExpandedWidth * .064f).coerceIn(12f, 15f)
    val railIconLabelGap = (railExpandedWidth * .052f).coerceIn(10f, 13f)
    val railLogoItemGap = (height / 72f).coerceIn(9f, 15f)
    val railItemGap = (height / 300f).coerceIn(2f, 4f)
    val railTopPadding = (height / 28f).coerceIn(20f, 30f)
    val railBottomPadding = (height / 36f).coerceIn(16f, 24f)
    val railCornerRadius = (railItemHeight * .25f).coerceIn(11f, 14f)

    return TvPremiumWindowPolicy(
        horizontalSafeInsetDp = 8f + (10f * compactPressure),
        verticalSafeInsetDp = 8f + (8f * compactPressure),
        contentWidthFraction = when {
            compactTv -> 0.96f
            standardTv -> 0.95f
            else -> 0.94f
        },
        railCollapsedWidthDp = railCollapsedWidth,
        railExpandedWidthDp = railExpandedWidth,
        railLogoSizeDp = railLogoSize,
        railItemHeightDp = railItemHeight,
        railIconSizeDp = railIconSize,
        railLabelSizeSp = railLabelSize,
        railOuterHorizontalPaddingDp = railOuterHorizontalPadding,
        railItemHorizontalPaddingDp = railItemHorizontalPadding,
        railIconLabelGapDp = railIconLabelGap,
        railLogoItemGapDp = railLogoItemGap,
        railItemGapDp = railItemGap,
        railTopPaddingDp = railTopPadding,
        railBottomPaddingDp = railBottomPadding,
        railCornerRadiusDp = railCornerRadius,
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

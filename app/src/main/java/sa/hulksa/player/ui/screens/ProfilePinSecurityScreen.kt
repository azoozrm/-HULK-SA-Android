package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.data.PROFILE_PIN_LENGTH
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun ProfilePinUnlockScreen(
    profile: UserProfile,
    isTv: Boolean,
    onVerify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var error by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var resetToken by rememberSaveable(profile.id) { mutableIntStateOf(0) }

    BackHandler(onBack = onCancel)

    ProfilePinEntryScaffold(
        profile = profile,
        isTv = isTv,
        title = "أدخل رمز PIN",
        subtitle = "هذا الملف محمي. أدخل الرمز المكوّن من 4 أرقام للمتابعة.",
        errorMessage = error,
        resetToken = resetToken,
        onComplete = { pin ->
            if (onVerify(pin)) {
                error = null
                onUnlocked()
            } else {
                error = "رمز PIN غير صحيح. حاول مرة أخرى."
                resetToken++
            }
        },
        onCancel = onCancel,
    )
}

@Composable
fun ProfilePinProtectionScreen(
    profile: UserProfile,
    isTv: Boolean,
    isProtected: Boolean,
    onVerify: (String) -> Boolean,
    onSetPin: (String) -> Boolean,
    onClearPin: () -> Boolean,
    onClose: () -> Unit,
) {
    var step by rememberSaveable(profile.id, isProtected) {
        mutableStateOf(if (isProtected) PinProtectionStep.OVERVIEW else PinProtectionStep.NEW_PIN)
    }
    var firstPin by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var resetToken by rememberSaveable(profile.id) { mutableIntStateOf(0) }

    fun returnToOverviewOrClose() {
        error = null
        firstPin = null
        if (isProtected) {
            step = PinProtectionStep.OVERVIEW
        } else {
            onClose()
        }
    }

    BackHandler {
        if (step == PinProtectionStep.OVERVIEW) onClose() else returnToOverviewOrClose()
    }

    if (step == PinProtectionStep.OVERVIEW) {
        ProfilePinOverview(
            profile = profile,
            isTv = isTv,
            onChangePin = {
                error = null
                firstPin = null
                resetToken++
                step = PinProtectionStep.VERIFY_CHANGE
            },
            onRemovePin = {
                error = null
                firstPin = null
                resetToken++
                step = PinProtectionStep.VERIFY_REMOVE
            },
            onClose = onClose,
        )
        return
    }

    val title = when (step) {
        PinProtectionStep.VERIFY_CHANGE -> "تحقق من رمز PIN الحالي"
        PinProtectionStep.VERIFY_REMOVE -> "تأكيد إلغاء الحماية"
        PinProtectionStep.NEW_PIN -> if (isProtected) "رمز PIN جديد" else "حماية الملف برمز PIN"
        PinProtectionStep.CONFIRM_PIN -> "تأكيد رمز PIN"
        PinProtectionStep.OVERVIEW -> ""
    }
    val subtitle = when (step) {
        PinProtectionStep.VERIFY_CHANGE ->
            "أدخل الرمز الحالي قبل تغييره."
        PinProtectionStep.VERIFY_REMOVE ->
            "أدخل الرمز الحالي لإلغاء حماية هذا الملف."
        PinProtectionStep.NEW_PIN ->
            "اختر 4 أرقام يسهل عليك تذكرها ويصعب على الآخرين تخمينها."
        PinProtectionStep.CONFIRM_PIN ->
            "أعد إدخال الرمز نفسه للتأكد."
        PinProtectionStep.OVERVIEW -> ""
    }

    ProfilePinEntryScaffold(
        profile = profile,
        isTv = isTv,
        title = title,
        subtitle = subtitle,
        errorMessage = error,
        resetToken = resetToken,
        onComplete = { pin ->
            when (step) {
                PinProtectionStep.VERIFY_CHANGE -> {
                    if (onVerify(pin)) {
                        error = null
                        firstPin = null
                        resetToken++
                        step = PinProtectionStep.NEW_PIN
                    } else {
                        error = "رمز PIN الحالي غير صحيح."
                        resetToken++
                    }
                }

                PinProtectionStep.VERIFY_REMOVE -> {
                    if (!onVerify(pin)) {
                        error = "رمز PIN الحالي غير صحيح."
                        resetToken++
                    } else if (onClearPin()) {
                        error = null
                        onClose()
                    } else {
                        error = "تعذر إلغاء الحماية. حاول مرة أخرى."
                        resetToken++
                    }
                }

                PinProtectionStep.NEW_PIN -> {
                    firstPin = pin
                    error = null
                    resetToken++
                    step = PinProtectionStep.CONFIRM_PIN
                }

                PinProtectionStep.CONFIRM_PIN -> {
                    val expected = firstPin
                    if (expected == null || pin != expected) {
                        error = "الرمزان غير متطابقين. أعد التأكيد."
                        resetToken++
                    } else if (onSetPin(pin)) {
                        error = null
                        onClose()
                    } else {
                        error = "تعذر حفظ رمز PIN. حاول مرة أخرى."
                        resetToken++
                    }
                }

                PinProtectionStep.OVERVIEW -> Unit
            }
        },
        onCancel = ::returnToOverviewOrClose,
    )
}

private enum class PinProtectionStep {
    OVERVIEW,
    VERIFY_CHANGE,
    VERIFY_REMOVE,
    NEW_PIN,
    CONFIRM_PIN,
}

@Composable
private fun ProfilePinOverview(
    profile: UserProfile,
    isTv: Boolean,
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onClose: () -> Unit,
) {
    ProfileSecurityBackdrop(isTv = isTv) { useTwoColumns, shortLandscape, compactHeight ->
        val contentPadding = when {
            isTv -> 38.dp
            shortLandscape -> 10.dp
            useTwoColumns -> 28.dp
            else -> 20.dp
        }

        if (useTwoColumns) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 48.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileSecurityIdentity(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = shortLandscape,
                    modifier = Modifier.weight(1f),
                )
                SecurityActionPanel(
                    title = "حماية الملف مفعّلة",
                    subtitle = "يمكنك تغيير رمز PIN أو إلغاء الحماية بعد التحقق من الرمز الحالي.",
                    isTv = isTv,
                    shortLandscape = shortLandscape,
                    compactHeight = compactHeight,
                    onChangePin = onChangePin,
                    onRemovePin = onRemovePin,
                    onClose = onClose,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ProfileSecurityIdentity(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = false,
                    compact = compactHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(if (isTv) 28.dp else if (compactHeight) 10.dp else 18.dp))
                SecurityActionPanel(
                    title = "حماية الملف مفعّلة",
                    subtitle = "يمكنك تغيير رمز PIN أو إلغاء الحماية بعد التحقق من الرمز الحالي.",
                    isTv = isTv,
                    shortLandscape = false,
                    compactHeight = compactHeight,
                    onChangePin = onChangePin,
                    onRemovePin = onRemovePin,
                    onClose = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SecurityActionPanel(
    title: String,
    subtitle: String,
    isTv: Boolean,
    shortLandscape: Boolean,
    compactHeight: Boolean,
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)

    Column(
        modifier = modifier
            .widthIn(max = if (isTv) 560.dp else 500.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = .97f))
            .border(1.dp, colors.gold.copy(alpha = .30f), shape)
            .padding(
                horizontal = if (isTv) 32.dp else 22.dp,
                vertical = when {
                    shortLandscape -> 18.dp
                    isTv -> 30.dp
                    compactHeight -> 18.dp
                    else -> 24.dp
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = when {
                isTv -> 29.sp
                shortLandscape || compactHeight -> 21.sp
                else -> 24.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = colors.textMuted,
            fontSize = if (isTv) 14.sp else 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight) 12.dp else 22.dp))
        SecurityButton(
            text = "تغيير رمز PIN",
            isTv = isTv,
            onClick = onChangePin,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SecurityButton(
            text = "إلغاء الحماية",
            isTv = isTv,
            danger = true,
            onClick = onRemovePin,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SecurityButton(
            text = "رجوع",
            isTv = isTv,
            secondary = true,
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfilePinEntryScaffold(
    profile: UserProfile,
    isTv: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String?,
    resetToken: Int,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    ProfileSecurityBackdrop(isTv = isTv) { useTwoColumns, shortLandscape, compactHeight ->
        val padding = when {
            isTv -> 36.dp
            shortLandscape -> 8.dp
            useTwoColumns -> 24.dp
            else -> 18.dp
        }

        if (useTwoColumns) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 46.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileSecurityIdentity(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = shortLandscape,
                    modifier = Modifier.weight(1f),
                )
                PinPanel(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = shortLandscape,
                    compactHeight = compactHeight,
                    title = title,
                    subtitle = subtitle,
                    errorMessage = errorMessage,
                    resetToken = resetToken,
                    onComplete = onComplete,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ProfileSecurityIdentity(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = false,
                    compact = compactHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(if (isTv) 22.dp else if (compactHeight) 8.dp else 12.dp))
                PinPanel(
                    profile = profile,
                    isTv = isTv,
                    shortLandscape = false,
                    compactHeight = compactHeight,
                    title = title,
                    subtitle = subtitle,
                    errorMessage = errorMessage,
                    resetToken = resetToken,
                    onComplete = onComplete,
                    onCancel = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProfileSecurityBackdrop(
    isTv: Boolean,
    content: @Composable (
        useTwoColumns: Boolean,
        shortLandscape: Boolean,
        compactHeight: Boolean,
    ) -> Unit,
) {
    val colors = LocalHulkColors.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.goldDeep.copy(alpha = .14f),
                        Color.Transparent,
                    ),
                    radius = if (isTv) 1100f else 720f,
                ),
            )
            .safeDrawingPadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val shortLandscape = !isTv && landscape && maxHeight < 520.dp
        val compactHeight = !isTv && !landscape && maxHeight < 720.dp
        val useTwoColumns = isTv || maxWidth >= 840.dp || (landscape && maxWidth >= 600.dp)
        content(useTwoColumns, shortLandscape, compactHeight)
    }
}

@Composable
private fun ProfileSecurityIdentity(
    profile: UserProfile,
    isTv: Boolean,
    shortLandscape: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val avatarSize = when {
        shortLandscape -> 66.dp
        compact -> 60.dp
        isTv -> 116.dp
        else -> 92.dp
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProfileAvatarArtwork(
            avatarKey = profile.avatarKey,
            displayName = profile.displayName,
            size = avatarSize,
            highlighted = true,
        )
        Spacer(Modifier.height(if (shortLandscape) 8.dp else 12.dp))
        Text(
            text = profile.displayName,
            color = colors.text,
            fontSize = when {
                shortLandscape -> 21.sp
                isTv -> 30.sp
                compact -> 19.sp
                else -> 25.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "حماية الملف الشخصي",
            color = colors.goldBright,
            fontSize = if (isTv) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PinPanel(
    profile: UserProfile,
    isTv: Boolean,
    shortLandscape: Boolean,
    compactHeight: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String?,
    resetToken: Int,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var pin by rememberSaveable(profile.id, title, resetToken) { mutableStateOf("") }
    val firstFocusRequester = remember(title, resetToken) { FocusRequester() }
    val keySize = when {
        shortLandscape -> 54.dp
        isTv -> 78.dp
        compactHeight -> 56.dp
        else -> 66.dp
    }
    val shape = RoundedCornerShape(if (isTv) 26.dp else 20.dp)

    LaunchedEffect(isTv, title, resetToken) {
        if (!isTv) return@LaunchedEffect
        delay(100L)
        runCatching { firstFocusRequester.requestFocus() }
    }

    fun appendDigit(value: String) {
        if (pin.length >= PROFILE_PIN_LENGTH) return
        val next = pin + value
        pin = next
        if (next.length == PROFILE_PIN_LENGTH) {
            onComplete(next)
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = if (isTv) 520.dp else 430.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = .97f))
            .border(1.dp, colors.gold.copy(alpha = .30f), shape)
            .padding(
                horizontal = if (shortLandscape) 16.dp else if (isTv) 30.dp else 22.dp,
                vertical = when {
                    shortLandscape -> 12.dp
                    isTv -> 26.dp
                    compactHeight -> 14.dp
                    else -> 20.dp
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = when {
                shortLandscape -> 19.sp
                isTv -> 29.sp
                compactHeight -> 20.sp
                else -> 23.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight) 4.dp else 7.dp))
        Text(
            text = subtitle,
            color = colors.textMuted,
            fontSize = if (isTv) 13.sp else 11.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight) 8.dp else 15.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PROFILE_PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(if (isTv) 15.dp else 13.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index < pin.length) colors.goldBright
                            else colors.surfaceRaised,
                        )
                        .border(
                            1.dp,
                            if (index < pin.length) colors.gold else Color.White.copy(alpha = .16f),
                            RoundedCornerShape(50),
                        ),
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(if (shortLandscape || compactHeight) 6.dp else 10.dp))
            Text(
                text = errorMessage,
                color = colors.danger,
                fontSize = if (isTv) 12.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(if (shortLandscape || compactHeight) 7.dp else 14.dp))

        NumberPad(
            isTv = isTv,
            keySize = keySize,
            firstFocusRequester = firstFocusRequester,
            backspaceEnabled = pin.isNotEmpty(),
            onDigit = ::appendDigit,
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )

        Spacer(Modifier.height(if (shortLandscape || compactHeight) 6.dp else 12.dp))

        SecurityButton(
            text = "رجوع",
            isTv = isTv,
            secondary = true,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberPad(
    isTv: Boolean,
    keySize: Dp,
    firstFocusRequester: FocusRequester,
    backspaceEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp)) {
                row.forEachIndexed { columnIndex, digit ->
                    PinKey(
                        text = digit,
                        isTv = isTv,
                        keySize = keySize,
                        focusRequester = if (rowIndex == 0 && columnIndex == 0) firstFocusRequester else null,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 7.dp)) {
            Spacer(Modifier.size(keySize))
            PinKey(
                text = "0",
                isTv = isTv,
                keySize = keySize,
                onClick = { onDigit("0") },
            )
            PinKey(
                text = "⌫",
                isTv = isTv,
                keySize = keySize,
                enabled = backspaceEnabled,
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun PinKey(
    text: String,
    isTv: Boolean,
    keySize: Dp,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.08f else 1f,
        label = "profilePinKeyScale",
    )
    val shape = RoundedCornerShape(if (isTv) 18.dp else 16.dp)

    Box(
        modifier = Modifier
            .size(keySize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 16.dp.toPx() else 0f
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(
                when {
                    !enabled -> colors.surface.copy(alpha = .55f)
                    focused -> colors.surfaceRaised
                    else -> colors.surfaceRaised.copy(alpha = .88f)
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    !enabled -> Color.White.copy(alpha = .05f)
                    else -> Color.White.copy(alpha = .11f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !remoteSelect) {
                    false
                } else if (!enabled) {
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) {
                if (focused) colors.goldBright else colors.text
            } else {
                colors.textMuted.copy(alpha = .45f)
            },
            fontSize = if (isTv) 24.sp else 21.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SecurityButton(
    text: String,
    isTv: Boolean,
    secondary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.035f else 1f,
        label = "profileSecurityActionScale",
    )
    val shape = RoundedCornerShape(if (isTv) 15.dp else 13.dp)
    val background = when {
        danger -> colors.danger.copy(alpha = if (focused) .24f else .14f)
        secondary -> colors.surfaceRaised
        else -> if (focused) colors.goldBright else colors.gold
    }
    val foreground = when {
        danger -> colors.danger
        secondary -> if (focused) colors.goldBright else colors.text
        else -> Color.Black
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> colors.goldBright
                    danger -> colors.danger.copy(alpha = .45f)
                    secondary -> Color.White.copy(alpha = .10f)
                    else -> colors.gold.copy(alpha = .40f)
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isTv || !remoteSelect) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                }
            }
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isTv) 18.dp else 15.dp,
                vertical = if (isTv) 12.dp else 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = if (isTv) 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

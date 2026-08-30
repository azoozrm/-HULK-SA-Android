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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sa.hulksa.player.data.FOUR_DIGIT_CREDENTIAL_LENGTH
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun ProfilePinUnlockScreen(
    profile: UserProfile,
    isTv: Boolean,
    onVerify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var error by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var resetToken by rememberSaveable(profile.id) { mutableIntStateOf(0) }
    var operationInProgress by remember(profile.id) { mutableStateOf(false) }
    var operationJob by remember(profile.id) { mutableStateOf<Job?>(null) }
    val operationGuard = remember(profile.id) { ProfilePinOperationGuard() }
    val operationScope = rememberCoroutineScope()

    fun cancelPinOperation() {
        operationGuard.cancel()
        operationJob?.cancel()
        operationJob = null
        operationInProgress = false
    }

    DisposableEffect(profile.id) {
        onDispose {
            operationGuard.cancel()
            operationJob?.cancel()
        }
    }

    BackHandler {
        cancelPinOperation()
        onCancel()
    }

    ProfilePinEntryScaffold(
        profile = profile,
        isTv = isTv,
        title = "أدخل رمز PIN",
        subtitle = "أدخل رمز PIN المكوّن من 4 أرقام للمتابعة",
        errorMessage = error,
        resetToken = resetToken,
        inputEnabled = !operationInProgress,
        onComplete = { pin ->
            val token = operationGuard.begin()
            if (token != null) {
                operationInProgress = true
                error = null
                operationJob = operationScope.launch {
                    try {
                        val verified = onVerify(pin)
                        ensureActive()
                        if (operationGuard.isCurrent(token)) {
                            if (verified) {
                                error = null
                                onUnlocked()
                            } else {
                                error = "رمز PIN غير صحيح"
                                resetToken++
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (operationGuard.isCurrent(token)) {
                            error = "رمز PIN غير صحيح"
                            resetToken++
                        }
                    } finally {
                        if (operationGuard.isCurrent(token)) {
                            operationGuard.finish(token)
                            operationInProgress = false
                            operationJob = null
                        }
                    }
                }
            }
        },
        onCancel = {
            cancelPinOperation()
            onCancel()
        },
    )
}

@Composable
fun ProfilePinProtectionScreen(
    profile: UserProfile,
    isTv: Boolean,
    isProtected: Boolean,
    onVerify: suspend (String) -> Boolean,
    onSetPin: suspend (String) -> Boolean,
    onClearPin: suspend () -> Boolean,
    onClose: () -> Unit,
) {
    var step by rememberSaveable(profile.id, isProtected) {
        mutableStateOf(if (isProtected) PinProtectionStep.OVERVIEW else PinProtectionStep.NEW_PIN)
    }
    var firstPin by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var error by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var resetToken by rememberSaveable(profile.id) { mutableIntStateOf(0) }
    var operationInProgress by remember(profile.id) { mutableStateOf(false) }
    var operationJob by remember(profile.id) { mutableStateOf<Job?>(null) }
    val operationGuard = remember(profile.id) { ProfilePinOperationGuard() }
    val operationScope = rememberCoroutineScope()

    fun cancelPinOperation() {
        operationGuard.cancel()
        operationJob?.cancel()
        operationJob = null
        operationInProgress = false
    }

    fun launchPinOperation(block: suspend (Long) -> Unit) {
        val token = operationGuard.begin() ?: return
        operationInProgress = true
        error = null
        operationJob = operationScope.launch {
            try {
                block(token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (operationGuard.isCurrent(token)) {
                    error = "تعذر إتمام العملية. حاول مرة أخرى."
                    resetToken++
                }
            } finally {
                if (operationGuard.isCurrent(token)) {
                    operationGuard.finish(token)
                    operationInProgress = false
                    operationJob = null
                }
            }
        }
    }

    fun returnToOverviewOrClose() {
        cancelPinOperation()
        error = null
        firstPin = null
        if (isProtected) {
            step = PinProtectionStep.OVERVIEW
        } else {
            onClose()
        }
    }

    DisposableEffect(profile.id) {
        onDispose {
            operationGuard.cancel()
            operationJob?.cancel()
        }
    }

    BackHandler {
        if (step == PinProtectionStep.OVERVIEW) {
            cancelPinOperation()
            onClose()
        } else {
            returnToOverviewOrClose()
        }
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
            onClose = {
                cancelPinOperation()
                onClose()
            },
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
        inputEnabled = !operationInProgress,
        onComplete = { pin ->
            when (step) {
                PinProtectionStep.VERIFY_CHANGE -> {
                    launchPinOperation { token ->
                        val verified = onVerify(pin)
                        ensureActive()
                        if (operationGuard.isCurrent(token)) {
                            if (verified) {
                                error = null
                                firstPin = null
                                resetToken++
                                step = PinProtectionStep.NEW_PIN
                            } else {
                                error = "رمز PIN الحالي غير صحيح"
                                resetToken++
                            }
                        }
                    }
                }

                PinProtectionStep.VERIFY_REMOVE -> {
                    launchPinOperation { token ->
                        val verified = onVerify(pin)
                        ensureActive()
                        if (operationGuard.isCurrent(token)) {
                            if (!verified) {
                                error = "رمز PIN الحالي غير صحيح"
                                resetToken++
                            } else {
                                val cleared = onClearPin()
                                ensureActive()
                                if (operationGuard.isCurrent(token)) {
                                    if (cleared) {
                                        error = null
                                        onClose()
                                    } else {
                                        error = "تعذر إلغاء الحماية. حاول مرة أخرى."
                                        resetToken++
                                    }
                                }
                            }
                        }
                    }
                }

                PinProtectionStep.NEW_PIN -> {
                    if (!operationInProgress) {
                        firstPin = pin
                        error = null
                        resetToken++
                        step = PinProtectionStep.CONFIRM_PIN
                    }
                }

                PinProtectionStep.CONFIRM_PIN -> {
                    val expected = firstPin
                    if (expected == null || pin != expected) {
                        error = "الرمزان غير متطابقين. أعد التأكيد."
                        resetToken++
                    } else {
                        launchPinOperation { token ->
                            val stored = onSetPin(pin)
                            ensureActive()
                            if (operationGuard.isCurrent(token)) {
                                if (stored) {
                                    error = null
                                    onClose()
                                } else {
                                    error = "تعذر حفظ رمز PIN. حاول مرة أخرى."
                                    resetToken++
                                }
                            }
                        }
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
            isTv -> 28.dp
            shortLandscape -> 10.dp
            useTwoColumns -> 28.dp
            else -> 20.dp
        }

        if (useTwoColumns) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = if (isTv) 820.dp else 880.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 24.dp),
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
                Spacer(Modifier.height(if (isTv) 20.dp else if (compactHeight) 10.dp else 16.dp))
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
    val shape = RoundedCornerShape(if (isTv) 22.dp else 20.dp)
    val firstActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        delay(120L)
        runCatching { firstActionFocusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .widthIn(max = if (isTv) 430.dp else 500.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = .97f))
            .border(1.dp, colors.gold.copy(alpha = .30f), shape)
            .padding(
                horizontal = if (isTv) 24.dp else 22.dp,
                vertical = when {
                    shortLandscape -> 18.dp
                    isTv -> 24.dp
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
                isTv -> 26.sp
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
            fontSize = if (isTv) 13.sp else 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight) 12.dp else 18.dp))
        SecurityButton(
            text = "تغيير رمز PIN",
            isTv = isTv,
            focusRequester = firstActionFocusRequester,
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
    inputEnabled: Boolean,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    ProfileSecurityBackdrop(isTv = isTv) { useTwoColumns, shortLandscape, compactHeight ->
        if (isTv) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val compactTv = maxHeight < 620.dp || maxWidth < 900.dp
                val compositionWidth = if (compactTv) 360.dp else 420.dp
                val panelWidth = if (compactTv) 340.dp else 380.dp

                Column(
                    modifier = Modifier
                        .widthIn(max = compositionWidth)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ProfileSecurityIdentity(
                        profile = profile,
                        isTv = true,
                        shortLandscape = false,
                        compact = compactTv,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(if (compactTv) 12.dp else 18.dp))

                    PinPanel(
                        stateKey = profile.id,
                        isTv = true,
                        shortLandscape = false,
                        compactHeight = false,
                        tvCompact = compactTv,
                        title = title,
                        subtitle = subtitle,
                        errorMessage = errorMessage,
                        resetToken = resetToken,
                        inputEnabled = inputEnabled,
                        onComplete = onComplete,
                        onCancel = onCancel,
                        modifier = Modifier
                            .widthIn(max = panelWidth)
                            .fillMaxWidth(),
                    )
                }
            }
        } else {
            val padding = when {
                shortLandscape -> 8.dp
                useTwoColumns -> 18.dp
                else -> 16.dp
            }

            if (useTwoColumns) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileSecurityIdentity(
                            profile = profile,
                            isTv = false,
                            shortLandscape = shortLandscape,
                            modifier = Modifier.weight(1f),
                        )
                        PinPanel(
                            stateKey = profile.id,
                            isTv = false,
                            shortLandscape = shortLandscape,
                            compactHeight = compactHeight,
                            title = title,
                            subtitle = subtitle,
                            errorMessage = errorMessage,
                            resetToken = resetToken,
                            inputEnabled = inputEnabled,
                            onComplete = onComplete,
                            onCancel = onCancel,
                            modifier = Modifier.widthIn(min = 300.dp, max = 380.dp),
                        )
                    }
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
                        isTv = false,
                        shortLandscape = false,
                        compact = compactHeight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (compactHeight) 6.dp else 9.dp))
                    PinPanel(
                        stateKey = profile.id,
                        isTv = false,
                        shortLandscape = false,
                        compactHeight = compactHeight,
                        title = title,
                        subtitle = subtitle,
                        errorMessage = errorMessage,
                        resetToken = resetToken,
                        inputEnabled = inputEnabled,
                        onComplete = onComplete,
                        onCancel = onCancel,
                        modifier = Modifier.fillMaxWidth(if (compactHeight) .96f else .94f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileSecurityBackdrop(
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
                    radius = if (isTv) 900f else 720f,
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
        shortLandscape -> 64.dp
        isTv && compact -> 78.dp
        isTv -> 96.dp
        compact -> 58.dp
        else -> 88.dp
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
        Spacer(Modifier.height(if (shortLandscape || compact) 8.dp else 10.dp))
        Text(
            text = profile.displayName,
            color = colors.text,
            fontSize = when {
                shortLandscape -> 20.sp
                isTv && compact -> 22.sp
                isTv -> 25.sp
                compact -> 19.sp
                else -> 24.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "حماية الملف الشخصي",
            color = colors.goldBright,
            fontSize = if (isTv) 11.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PinPanel(
    stateKey: String,
    isTv: Boolean,
    shortLandscape: Boolean,
    compactHeight: Boolean,
    tvCompact: Boolean = false,
    title: String,
    subtitle: String,
    errorMessage: String?,
    resetToken: Int,
    inputEnabled: Boolean,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    // PIN digits are intentionally not saveable so process state cannot persist the raw value.
    var pin by remember(stateKey, title, resetToken) { mutableStateOf("") }
    val firstFocusRequester = remember(title, resetToken) { FocusRequester() }
    val cancelFocusRequester = remember(title, resetToken) { FocusRequester() }
    val keySize = when {
        shortLandscape -> 46.dp
        isTv && tvCompact -> 50.dp
        isTv -> 56.dp
        compactHeight -> 48.dp
        else -> 54.dp
    }
    val indicatorSize = when {
        isTv && tvCompact -> 11.dp
        isTv -> 12.dp
        shortLandscape || compactHeight -> 11.dp
        else -> 12.dp
    }
    val shape = RoundedCornerShape(if (isTv) 20.dp else 18.dp)

    LaunchedEffect(isTv, title, resetToken) {
        if (!isTv) return@LaunchedEffect
        delay(100L)
        runCatching { firstFocusRequester.requestFocus() }
    }

    fun appendDigit(value: String) {
        if (!inputEnabled || pin.length >= FOUR_DIGIT_CREDENTIAL_LENGTH) return
        val next = pin + value
        pin = next
        if (next.length == FOUR_DIGIT_CREDENTIAL_LENGTH) {
            onComplete(next)
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = if (isTv) 380.dp else 360.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = .97f))
            .border(1.dp, colors.gold.copy(alpha = .30f), shape)
            .padding(
                horizontal = when {
                    shortLandscape -> 12.dp
                    isTv && tvCompact -> 16.dp
                    isTv -> 20.dp
                    else -> 16.dp
                },
                vertical = when {
                    shortLandscape -> 8.dp
                    isTv && tvCompact -> 11.dp
                    isTv -> 14.dp
                    compactHeight -> 10.dp
                    else -> 14.dp
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = when {
                shortLandscape -> 18.sp
                isTv && tvCompact -> 21.sp
                isTv -> 24.sp
                compactHeight -> 19.sp
                else -> 22.sp
            },
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 4.dp))
        Text(
            text = subtitle,
            color = colors.textMuted,
            fontSize = when {
                isTv && tvCompact -> 10.sp
                isTv -> 12.sp
                else -> 10.sp
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 5.dp else 7.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 9.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(FOUR_DIGIT_CREDENTIAL_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index < pin.length) colors.goldBright
                            else colors.surfaceRaised,
                        )
                        .border(
                            if (index < pin.length) 1.5.dp else 1.dp,
                            if (index < pin.length) colors.gold else Color.White.copy(alpha = .18f),
                            RoundedCornerShape(50),
                        ),
                )
            }
        }

        Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 18.dp else 17.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = colors.danger,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 5.dp))

        NumberPad(
            isTv = isTv,
            keySize = keySize,
            firstFocusRequester = firstFocusRequester,
            cancelFocusRequester = cancelFocusRequester,
            backspaceEnabled = true,
            onDigit = ::appendDigit,
            onBackspace = {
                if (inputEnabled && pin.isNotEmpty()) pin = pin.dropLast(1)
            },
        )

        Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 4.dp else 6.dp))

        SecurityButton(
            text = "رجوع",
            isTv = isTv,
            secondary = true,
            compact = true,
            focusRequester = cancelFocusRequester,
            onClick = onCancel,
            modifier = if (isTv) {
                Modifier.widthIn(min = 148.dp, max = 184.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

@Composable
private fun NumberPad(
    isTv: Boolean,
    keySize: Dp,
    firstFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    backspaceEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val keySpacing = if (isTv) 7.dp else 6.dp
    val requesters = remember(firstFocusRequester) {
        listOf(firstFocusRequester) + List(10) { FocusRequester() }
    }

    fun requester(index: Int): FocusRequester = requesters[index]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(keySpacing),
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
                repeat(3) { column ->
                    val index = row * 3 + column
                    val digit = (index + 1).toString()
                    val self = requester(index)
                    val left = if (column < 2) requester(index + 1) else self
                    val right = if (column > 0) requester(index - 1) else self
                    val up = if (row > 0) requester(index - 3) else self
                    val down = when {
                        row < 2 -> requester(index + 3)
                        column == 0 -> self
                        column == 1 -> requester(9)
                        backspaceEnabled -> requester(10)
                        else -> self
                    }
                    PinKey(
                        text = digit,
                        isTv = isTv,
                        keySize = keySize,
                        focusRequester = self,
                        leftRequester = left,
                        rightRequester = right,
                        upRequester = up,
                        downRequester = down,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(keySpacing)) {
            Spacer(Modifier.size(keySize))
            PinKey(
                text = "0",
                isTv = isTv,
                keySize = keySize,
                focusRequester = requester(9),
                leftRequester = if (backspaceEnabled) requester(10) else requester(9),
                rightRequester = requester(9),
                upRequester = requester(7),
                downRequester = requester(9),
                onClick = { onDigit("0") },
            )
            PinKey(
                text = "⌫",
                isTv = isTv,
                keySize = keySize,
                enabled = backspaceEnabled,
                focusRequester = requester(10),
                leftRequester = requester(10),
                rightRequester = requester(9),
                upRequester = requester(8),
                downRequester = requester(10),
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
    leftRequester: FocusRequester? = null,
    rightRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.07f else 1f,
        label = "profilePinKeyScale",
    )
    val shape = RoundedCornerShape(if (isTv) 14.dp else 15.dp)

    Box(
        modifier = Modifier
            .size(keySize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused && isTv) 12.dp.toPx() else 0f
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
                if (!isTv) {
                    false
                } else if (event.type == KeyEventType.KeyDown) {
                    val target = when (event.key) {
                        Key.DirectionLeft -> leftRequester
                        Key.DirectionRight -> rightRequester
                        Key.DirectionUp -> upRequester
                        Key.DirectionDown -> downRequester
                        else -> null
                    }
                    if (target != null) {
                        runCatching { target.requestFocus() }
                        true
                    } else {
                        val remoteSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                        if (!remoteSelect) false else true
                    }
                } else if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    if (enabled) onClick()
                    true
                } else {
                    false
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
            fontSize = if (isTv) 20.sp else 20.sp,
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
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember(text) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.035f else 1f,
        label = "profileSecurityActionScale",
    )
    val shape = RoundedCornerShape(if (isTv) 13.dp else 13.dp)
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
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier,
            )
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
                horizontal = if (isTv) 16.dp else 15.dp,
                vertical = when {
                    compact -> 8.dp
                    isTv -> 11.dp
                    else -> 10.dp
                },
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

package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sa.hulksa.player.data.FOUR_DIGIT_CREDENTIAL_LENGTH
import sa.hulksa.player.data.FourDigitCredentialLockedException
import sa.hulksa.player.data.FourDigitCredentialProtectionUnavailableException
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.components.EntryActionButton
import sa.hulksa.player.ui.components.EntryBackAction
import sa.hulksa.player.ui.components.EntryNotice
import sa.hulksa.player.ui.theme.LocalHulkColors

internal const val CREDENTIAL_PROTECTION_UNAVAILABLE_MESSAGE =
    "تعذر قراءة حالة الحماية بامان"

internal fun credentialLockoutMessage(retryAfterMs: Long): String {
    val seconds = ((retryAfterMs.coerceAtLeast(1L) + 999L) / 1_000L).coerceAtLeast(1L)
    return if (seconds < 60L) {
        "محاولات كثيرة ، انتظر $seconds ث"
    } else {
        val minutes = (seconds + 59L) / 60L
        "محاولات كثيرة ، انتظر $minutes د"
    }
}

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
        title = "ادخل رمز PIN",
        subtitle = "ادخل رمز PIN المكوّن من 4 ارقام للمتابعة",
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
                    } catch (locked: FourDigitCredentialLockedException) {
                        if (operationGuard.isCurrent(token)) {
                            error = credentialLockoutMessage(locked.retryAfterMs)
                            resetToken++
                        }
                    } catch (_: FourDigitCredentialProtectionUnavailableException) {
                        if (operationGuard.isCurrent(token)) {
                            error = CREDENTIAL_PROTECTION_UNAVAILABLE_MESSAGE
                            resetToken++
                        }
                    } catch (_: Exception) {
                        if (operationGuard.isCurrent(token)) {
                            error = "تعذر التحقق من رمز PIN ، حاول مرة اخرى"
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
            } catch (locked: FourDigitCredentialLockedException) {
                if (operationGuard.isCurrent(token)) {
                    error = credentialLockoutMessage(locked.retryAfterMs)
                    resetToken++
                }
            } catch (_: FourDigitCredentialProtectionUnavailableException) {
                if (operationGuard.isCurrent(token)) {
                    error = CREDENTIAL_PROTECTION_UNAVAILABLE_MESSAGE
                    resetToken++
                }
            } catch (_: Exception) {
                if (operationGuard.isCurrent(token)) {
                    error = "تعذر اتمام العملية ، حاول مرة اخرى"
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
        PinProtectionStep.VERIFY_REMOVE -> "تاكيد الغاء الحماية"
        PinProtectionStep.NEW_PIN -> if (isProtected) "رمز PIN جديد" else "حماية الملف برمز PIN"
        PinProtectionStep.CONFIRM_PIN -> "تاكيد رمز PIN"
        PinProtectionStep.OVERVIEW -> ""
    }
    val subtitle = when (step) {
        PinProtectionStep.VERIFY_CHANGE ->
            "ادخل الرمز الحالي قبل تغييره"
        PinProtectionStep.VERIFY_REMOVE ->
            "ادخل الرمز الحالي لالغاء حماية هذا الملف"
        PinProtectionStep.NEW_PIN ->
            "اختر 4 ارقام يسهل عليك تذكرها ويصعب على الاخرين تخمينها"
        PinProtectionStep.CONFIRM_PIN ->
            "اعد ادخال الرمز نفسه للتاكد"
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
                                        error = "تعذر الغاء الحماية ، حاول مرة اخرى"
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
                        error = "الرمزان غير متطابقين ، اعد التاكيد"
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
                                    error = "تعذر حفظ رمز PIN ، حاول مرة اخرى"
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
                        subtitle = "يمكنك تغيير رمز PIN او الغاء الحماية بعد التحقق من الرمز الحالي",
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
                    subtitle = "يمكنك تغيير رمز PIN او الغاء الحماية بعد التحقق من الرمز الحالي",
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
            text = "الغاء الحماية",
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
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                val compactTv = maxHeight < 620.dp || maxWidth < 900.dp
                val panelWidth = if (compactTv) 560.dp else 640.dp
                // Board 05: profile/context/masked slots RIGHT, one keypad LEFT, Back in the
                // RIGHT header. The split keeps identity, slots, keypad and Back inside the
                // real usable window instead of clipping the stacked composition.
                PinPanel(
                    stateKey = profile.id,
                    isTv = true,
                    shortLandscape = false,
                    compactHeight = false,
                    tvCompact = compactTv,
                    splitTv = true,
                    title = title,
                    subtitle = subtitle,
                    errorMessage = errorMessage,
                    resetToken = resetToken,
                    inputEnabled = inputEnabled,
                    onComplete = onComplete,
                    onCancel = onCancel,
                    identity = {
                        ProfileSecurityIdentity(
                            profile = profile,
                            isTv = true,
                            shortLandscape = false,
                            compact = compactTv,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.widthIn(max = panelWidth),
                )
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
                        .padding(padding)
                        .then(
                            if (shortLandscape) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = if (shortLandscape) Alignment.TopCenter else Alignment.Center,
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
                        .padding(padding)
                        .then(
                            if (shortLandscape) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (shortLandscape) Arrangement.Top else Arrangement.Center,
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
    splitTv: Boolean = false,
    identity: (@Composable () -> Unit)? = null,
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
    // One production buffer per entry stage: a stage change (title/resetToken) starts empty and
    // enabled, each activation appends one digit and completion fires once. PIN digits are
    // intentionally not saveable so process state cannot persist the raw value.
    val buffer = remember(stateKey, title, resetToken) { PinEntryBuffer() }
    var pinLength by remember(stateKey, title, resetToken) { mutableIntStateOf(0) }
    val firstFocusRequester = remember(title, resetToken) { FocusRequester() }
    val cancelFocusRequester = remember(title, resetToken) { FocusRequester() }
    var keypadPlaced by remember(title, resetToken) { mutableStateOf(false) }
    var keypadHasFocus by remember(title, resetToken) { mutableStateOf(false) }
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

    // Focus the current stage's keypad only after the real keypad node is placed, and only when
    // no key inside it already owns focus. No sleep and no focus on a disposed stage.
    LaunchedEffect(isTv, title, resetToken, keypadPlaced) {
        if (!isTv || !keypadPlaced) return@LaunchedEffect
        withFrameNanos { }
        if (!keypadHasFocus) {
            runCatching { firstFocusRequester.requestFocus() }
        }
    }

    fun appendDigit(value: String) {
        buffer.append(value, inputEnabled) { complete -> onComplete(complete) }
        pinLength = buffer.digits.length
    }

    Column(
        modifier = modifier
            .widthIn(max = if (splitTv) 620.dp else if (isTv) 380.dp else 360.dp)
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
        val contextContent: @Composable ColumnScope.() -> Unit = {
            if (splitTv) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EntryBackAction(
                        onClick = onCancel,
                        minHeightDp = movieActionHeightDp(isTv = true, compactHeight = false),
                        modifier = Modifier.focusRequester(cancelFocusRequester),
                    )
                    Text(
                        text = title,
                        color = colors.text,
                        fontSize = if (tvCompact) 21.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(if (tvCompact) 10.dp else 14.dp))
                identity?.invoke()
                Spacer(Modifier.height(if (tvCompact) 8.dp else 12.dp))
            } else {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = when {
                        shortLandscape -> 18.sp
                        compactHeight -> 19.sp
                        else -> 22.sp
                    },
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(if (shortLandscape || compactHeight) 3.dp else 4.dp))
            }
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
                                if (index < pinLength) colors.goldBright
                                else colors.surfaceRaised,
                            )
                            .border(
                                if (index < pinLength) 1.5.dp else 1.dp,
                                if (index < pinLength) colors.gold else Color.White.copy(alpha = .18f),
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 4.dp))

            if (!errorMessage.isNullOrBlank()) {
                EntryNotice(
                    message = errorMessage,
                    compact = true,
                )
                Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 4.dp))
            }

            if (splitTv) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "استخدم ارقام الريموت او لوحة الارقام",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val keypadContent: @Composable () -> Unit = {
            Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 3.dp else 5.dp))

            Box(
                modifier = Modifier
                    .onGloballyPositioned { keypadPlaced = true }
                    .onFocusChanged { keypadHasFocus = it.hasFocus },
            ) {
                NumberPad(
                    isTv = isTv,
                    keySize = keySize,
                    firstFocusRequester = firstFocusRequester,
                    cancelFocusRequester = cancelFocusRequester,
                    backspaceEnabled = true,
                    onDigit = ::appendDigit,
                    onBackspace = {
                        buffer.backspace(inputEnabled)
                        pinLength = buffer.digits.length
                    },
                )
            }

            if (!splitTv) {
                Spacer(Modifier.height(if (shortLandscape || compactHeight || tvCompact) 4.dp else 6.dp))

                SecurityButton(
                    text = "رجوع",
                    isTv = isTv,
                    secondary = true,
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

        if (splitTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    contextContent()
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // A stage change owns a fresh keypad subtree, so every stage's keys deliver
                    // to that stage's own buffer/callback instead of the previous stage's state.
                    key(title, resetToken) {
                        keypadContent()
                    }
                }
            }
        } else {
            contextContent()
            key(title, resetToken) {
                keypadContent()
            }
        }
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

    // A real numeric remote sends digit key codes: deliver them through the same production
    // onDigit owner as the on-screen keys while the keypad subtree owns focus.
    val hardwareDigits = mapOf(
        Key.Zero to "0",
        Key.One to "1",
        Key.Two to "2",
        Key.Three to "3",
        Key.Four to "4",
        Key.Five to "5",
        Key.Six to "6",
        Key.Seven to "7",
        Key.Eight to "8",
        Key.Nine to "9",
        Key.NumPad0 to "0",
        Key.NumPad1 to "1",
        Key.NumPad2 to "2",
        Key.NumPad3 to "3",
        Key.NumPad4 to "4",
        Key.NumPad5 to "5",
        Key.NumPad6 to "6",
        Key.NumPad7 to "7",
        Key.NumPad8 to "8",
        Key.NumPad9 to "9",
    )

    // PIN digits stay in their stable physical LTR order; layout and D-pad wiring share it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (!isTv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = hardwareDigits[event.key] ?: return@onPreviewKeyEvent false
                onDigit(digit)
                true
            },
        ) {
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
                        val left = if (column > 0) requester(index - 1) else self
                        val right = if (column < 2) requester(index + 1) else self
                        val up = if (row > 0) requester(index - 3) else self
                        val down = when {
                            row < 2 -> requester(index + 3)
                            column == 0 -> if (backspaceEnabled) requester(10) else self
                            column == 1 -> requester(9)
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
                PinKey(
                    text = "⌫",
                    isTv = isTv,
                    keySize = keySize,
                    enabled = backspaceEnabled,
                    focusRequester = requester(10),
                    leftRequester = requester(10),
                    rightRequester = requester(9),
                    upRequester = requester(6),
                    downRequester = cancelFocusRequester,
                    onClick = onBackspace,
                )
                PinKey(
                    text = "0",
                    isTv = isTv,
                    keySize = keySize,
                    focusRequester = requester(9),
                    leftRequester = if (backspaceEnabled) requester(10) else requester(9),
                    rightRequester = requester(9),
                    upRequester = requester(7),
                    downRequester = cancelFocusRequester,
                    onClick = { onDigit("0") },
                )
                Spacer(Modifier.size(keySize))
            }
        }
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
    val shape = RoundedCornerShape(if (isTv) 14.dp else 15.dp)

    Box(
        modifier = Modifier
            .size(keySize)
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
                        runCatching { target.requestFocus() }.getOrDefault(false)
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
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EntryActionButton(
        text = text,
        onClick = onClick,
        primary = !secondary && !danger,
        outlined = secondary,
        danger = danger,
        minHeightDp = movieActionHeightDp(isTv = isTv, compactHeight = false),
        modifier = if (focusRequester != null) {
            modifier.focusRequester(focusRequester)
        } else {
            modifier
        },
    )
}

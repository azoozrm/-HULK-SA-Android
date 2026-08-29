package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sa.hulksa.player.ManualParentAuthProofRegistry

@Composable
fun ParentalCodeBootstrapScreen(
    credentialScopeKey: String,
    isTv: Boolean,
    onSetCode: suspend (String) -> Boolean,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember(credentialScopeKey) {
        mutableStateOf(ParentalCodeSetupStep.CREATE)
    }
    // The confirmation value is deliberately memory-only and never enters saved instance state.
    var firstCode by remember(credentialScopeKey) { mutableStateOf<String?>(null) }
    var error by remember(credentialScopeKey) { mutableStateOf<String?>(null) }
    var resetToken by remember(credentialScopeKey) { mutableIntStateOf(0) }
    var operationInProgress by remember(credentialScopeKey) { mutableStateOf(false) }
    var operationJob by remember(credentialScopeKey) { mutableStateOf<Job?>(null) }
    val operationGuard = remember(credentialScopeKey) { ProfilePinOperationGuard() }
    val operationScope = rememberCoroutineScope()

    fun cancelOperation() {
        operationGuard.cancel()
        operationJob?.cancel()
        operationJob = null
        operationInProgress = false
    }

    fun cancelOrReturnToCreate() {
        cancelOperation()
        error = null
        firstCode = null
        if (step == ParentalCodeSetupStep.CONFIRM) {
            step = ParentalCodeSetupStep.CREATE
            resetToken++
        } else {
            onCancel()
        }
    }

    DisposableEffect(credentialScopeKey) {
        onDispose {
            operationGuard.cancel()
            operationJob?.cancel()
        }
    }

    BackHandler(onBack = ::cancelOrReturnToCreate)

    val title = when (step) {
        ParentalCodeSetupStep.CREATE -> "إنشاء رمز الوالدين"
        ParentalCodeSetupStep.CONFIRM -> "تأكيد رمز الوالدين"
    }
    val subtitle = when (step) {
        ParentalCodeSetupStep.CREATE ->
            "سيُستخدم رمز الوالدين للخروج من وضع الأطفال وإدارة الملفات الشخصية."
        ParentalCodeSetupStep.CONFIRM ->
            "أعد إدخال رمز الوالدين نفسه للتأكد."
    }

    ParentalCodeEntryScaffold(
        credentialScopeKey = credentialScopeKey,
        isTv = isTv,
        title = title,
        subtitle = subtitle,
        errorMessage = error,
        resetToken = resetToken,
        inputEnabled = !operationInProgress,
        onComplete = { code ->
            when (step) {
                ParentalCodeSetupStep.CREATE -> {
                    firstCode = code
                    error = null
                    resetToken++
                    step = ParentalCodeSetupStep.CONFIRM
                }

                ParentalCodeSetupStep.CONFIRM -> {
                    val expected = firstCode
                    if (expected == null || code != expected) {
                        error = "رمزا الوالدين غير متطابقين. أعد التأكيد."
                        resetToken++
                    } else {
                        val token = operationGuard.begin()
                        if (token != null) {
                            operationInProgress = true
                            error = null
                            operationJob = operationScope.launch {
                                try {
                                    val stored = onSetCode(code)
                                    ensureActive()
                                    if (operationGuard.isCurrent(token)) {
                                        if (stored) {
                                            firstCode = null
                                            ManualParentAuthProofRegistry.consumeValidProof()
                                            onCompleted()
                                        } else {
                                            error = "تعذر حفظ رمز الوالدين. حاول مرة أخرى."
                                            resetToken++
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    if (operationGuard.isCurrent(token)) {
                                        error = "تعذر حفظ رمز الوالدين. حاول مرة أخرى."
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
                    }
                }
            }
        },
        onCancel = ::cancelOrReturnToCreate,
    )
}

@Composable
fun ParentalCodeUnlockScreen(
    credentialScopeKey: String,
    isTv: Boolean,
    onVerify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var error by remember(credentialScopeKey) { mutableStateOf<String?>(null) }
    var resetToken by remember(credentialScopeKey) { mutableIntStateOf(0) }
    var operationInProgress by remember(credentialScopeKey) { mutableStateOf(false) }
    var operationJob by remember(credentialScopeKey) { mutableStateOf<Job?>(null) }
    val operationGuard = remember(credentialScopeKey) { ProfilePinOperationGuard() }
    val operationScope = rememberCoroutineScope()

    fun cancelOperation() {
        operationGuard.cancel()
        operationJob?.cancel()
        operationJob = null
        operationInProgress = false
    }

    fun cancel() {
        cancelOperation()
        onCancel()
    }

    DisposableEffect(credentialScopeKey) {
        onDispose {
            operationGuard.cancel()
            operationJob?.cancel()
        }
    }

    BackHandler(onBack = ::cancel)

    ParentalCodeEntryScaffold(
        credentialScopeKey = credentialScopeKey,
        isTv = isTv,
        title = "رمز الوالدين",
        subtitle = "أدخل رمز الوالدين للخروج من وضع الأطفال أو إدارة الملفات الشخصية.",
        errorMessage = error,
        resetToken = resetToken,
        inputEnabled = !operationInProgress,
        onComplete = { code ->
            val token = operationGuard.begin()
            if (token != null) {
                operationInProgress = true
                error = null
                operationJob = operationScope.launch {
                    try {
                        val verified = onVerify(code)
                        ensureActive()
                        if (operationGuard.isCurrent(token)) {
                            if (verified) {
                                onUnlocked()
                            } else {
                                error = "رمز الوالدين غير صحيح"
                                resetToken++
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (operationGuard.isCurrent(token)) {
                            error = "رمز الوالدين غير صحيح"
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
        onCancel = ::cancel,
    )
}

private enum class ParentalCodeSetupStep {
    CREATE,
    CONFIRM,
}

@Composable
private fun ParentalCodeEntryScaffold(
    credentialScopeKey: String,
    isTv: Boolean,
    title: String,
    subtitle: String,
    errorMessage: String?,
    resetToken: Int,
    inputEnabled: Boolean,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    ProfileSecurityBackdrop(isTv = isTv) { _, shortLandscape, compactHeight ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isTv) 32.dp else if (shortLandscape) 8.dp else 16.dp,
                    vertical = if (isTv) 24.dp else if (shortLandscape) 8.dp else 16.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val compactTv = isTv && (maxHeight < 620.dp || maxWidth < 900.dp)
            PinPanel(
                stateKey = "parental-code:$credentialScopeKey",
                isTv = isTv,
                shortLandscape = !isTv && shortLandscape,
                compactHeight = !isTv && compactHeight,
                tvCompact = compactTv,
                title = title,
                subtitle = subtitle,
                errorMessage = errorMessage,
                resetToken = resetToken,
                inputEnabled = inputEnabled,
                onComplete = onComplete,
                onCancel = onCancel,
                modifier = Modifier
                    .widthIn(max = if (isTv && compactTv) 340.dp else 380.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sa.hulksa.player.data.ProfilePinCredentialStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.ui.LegacyParentProofDecision
import sa.hulksa.player.ui.legacyParentProofDecision

private const val LEGACY_PARENT_PROOF_UNAVAILABLE_MESSAGE =
    "لا يوجد رمز PIN صالح للملف البالغ الرئيسي لإثبات ولي الأمر. لا يمكن إنشاء رمز الوالدين من وضع الأطفال."

@Composable
fun ParentalCodeBootstrapScreen(
    credentialScopeKey: String,
    isTv: Boolean,
    onSetCode: suspend (String) -> Boolean,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val profileStore = remember(context, credentialScopeKey) { ProfileStore(context) }
    val profilePinCredentialStore = remember(context, credentialScopeKey) {
        ProfilePinCredentialStore(context)
    }
    val profiles = remember(credentialScopeKey) { profileStore.profiles() }
    val activeProfile = remember(credentialScopeKey, profiles) {
        val activeProfileId = profileStore.activeProfileId()
        profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    }
    val legacyPrimaryAdultProfileId = remember(credentialScopeKey, profiles) {
        profiles.firstOrNull { profile ->
            profile.isPrimary &&
                profile.kind == ProfileKind.STANDARD &&
                profilePinCredentialStore.hasPin(profile.id)
        }?.id
    }
    val legacyProofDecision = remember(
        credentialScopeKey,
        activeProfile?.kind,
        legacyPrimaryAdultProfileId,
    ) {
        legacyParentProofDecision(
            currentProfileKind = activeProfile?.kind,
            legacyPrimaryAdultProfilePinAvailable = legacyPrimaryAdultProfileId != null,
        )
    }
    var step by remember(credentialScopeKey) {
        mutableStateOf(
            when (legacyProofDecision) {
                LegacyParentProofDecision.NOT_REQUIRED -> ParentalCodeSetupStep.CREATE
                LegacyParentProofDecision.REQUIRE_PRIMARY_ADULT_PROFILE_PIN,
                LegacyParentProofDecision.DENY_FAIL_CLOSED,
                -> ParentalCodeSetupStep.LEGACY_PARENT_PROOF
            },
        )
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
        ParentalCodeSetupStep.LEGACY_PARENT_PROOF -> "تحقق ولي الأمر"
        ParentalCodeSetupStep.CREATE -> "إنشاء رمز الوالدين"
        ParentalCodeSetupStep.CONFIRM -> "تأكيد رمز الوالدين"
    }
    val subtitle = when (step) {
        ParentalCodeSetupStep.LEGACY_PARENT_PROOF -> when (legacyProofDecision) {
            LegacyParentProofDecision.REQUIRE_PRIMARY_ADULT_PROFILE_PIN ->
                "أدخل رمز PIN للملف البالغ الرئيسي مرة واحدة، ثم أنشئ رمز والدين مستقلًا."
            LegacyParentProofDecision.DENY_FAIL_CLOSED ->
                "يلزم إثبات ولي أمر صالح قبل إنشاء رمز الوالدين من وضع الأطفال."
            LegacyParentProofDecision.NOT_REQUIRED ->
                "أنشئ رمز والدين مستقلًا لإدارة ملفات الأطفال."
        }
        ParentalCodeSetupStep.CREATE ->
            "سيُستخدم رمز الوالدين للخروج من وضع الأطفال وإدارة الملفات الشخصية."
        ParentalCodeSetupStep.CONFIRM ->
            "أعد إدخال رمز الوالدين نفسه للتأكد."
    }
    val displayedError = error ?: if (
        step == ParentalCodeSetupStep.LEGACY_PARENT_PROOF &&
        legacyProofDecision == LegacyParentProofDecision.DENY_FAIL_CLOSED
    ) {
        LEGACY_PARENT_PROOF_UNAVAILABLE_MESSAGE
    } else {
        null
    }
    val inputEnabled = !operationInProgress && !(
        step == ParentalCodeSetupStep.LEGACY_PARENT_PROOF &&
            legacyProofDecision == LegacyParentProofDecision.DENY_FAIL_CLOSED
        )

    ParentalCodeEntryScaffold(
        credentialScopeKey = credentialScopeKey,
        isTv = isTv,
        title = title,
        subtitle = subtitle,
        errorMessage = displayedError,
        resetToken = resetToken,
        inputEnabled = inputEnabled,
        onComplete = { code ->
            when (step) {
                ParentalCodeSetupStep.LEGACY_PARENT_PROOF -> {
                    val legacyProfileId = legacyPrimaryAdultProfileId
                    if (
                        legacyProofDecision !=
                        LegacyParentProofDecision.REQUIRE_PRIMARY_ADULT_PROFILE_PIN ||
                        legacyProfileId == null
                    ) {
                        error = LEGACY_PARENT_PROOF_UNAVAILABLE_MESSAGE
                        resetToken++
                    } else {
                        val token = operationGuard.begin()
                        if (token != null) {
                            operationInProgress = true
                            error = null
                            operationJob = operationScope.launch {
                                try {
                                    val verified = profilePinCredentialStore.verifyPin(
                                        profileId = legacyProfileId,
                                        pin = code,
                                    )
                                    ensureActive()
                                    if (operationGuard.isCurrent(token)) {
                                        if (verified) {
                                            firstCode = null
                                            error = null
                                            resetToken++
                                            step = ParentalCodeSetupStep.CREATE
                                        } else {
                                            error = "رمز PIN للملف البالغ غير صحيح"
                                            resetToken++
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    if (operationGuard.isCurrent(token)) {
                                        error = "تعذر التحقق من رمز PIN للملف البالغ. حاول مرة أخرى."
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
    LEGACY_PARENT_PROOF,
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

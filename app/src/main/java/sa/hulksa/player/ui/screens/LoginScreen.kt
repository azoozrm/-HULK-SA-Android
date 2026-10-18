package sa.hulksa.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.R
import sa.hulksa.player.PairingUiState
import sa.hulksa.player.data.GrowthDestination
import sa.hulksa.player.data.PortalException
import sa.hulksa.player.data.OperationsGrowthLinkConfig
import sa.hulksa.player.ui.components.EntryActionButton
import sa.hulksa.player.ui.components.PairingQuickLoginIcon
import sa.hulksa.player.ui.components.EntryNotice
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val HULK_WEBSITE = "https://hulksa.com/"

internal const val LOGIN_OFFLINE_MESSAGE =
    "لا يوجد اتصال بالانترنت ، تحقق من اتصالك وحاول مرة اخرى"

internal data class LoginErrorPresentation(
    val displayText: String?,
    val confirmedOffline: Boolean,
)

/**
 * Routes the raw Login failure to its display copy: a confirmed-offline transport failure uses the
 * offline wording; a service failure with internet available keeps the app-authored service retry
 * copy; validation/credential failures stay unchanged. Raw classification is untouched.
 */
internal fun loginErrorPresentation(
    errorMessage: String?,
    serviceUnavailableMessage: String,
    confirmedOffline: Boolean,
): LoginErrorPresentation = when {
    errorMessage.isNullOrBlank() ->
        LoginErrorPresentation(displayText = null, confirmedOffline = false)
    errorMessage == serviceUnavailableMessage && confirmedOffline ->
        LoginErrorPresentation(displayText = LOGIN_OFFLINE_MESSAGE, confirmedOffline = true)
    else ->
        LoginErrorPresentation(displayText = errorMessage, confirmedOffline = false)
}

internal class LoginCardFocusRequesterHolder(
    initialRequester: FocusRequester,
) {
    private var currentRequester = initialRequester

    fun update(requester: FocusRequester) {
        currentRequester = requester
    }

    fun current(): FocusRequester = currentRequester
}

internal class LoginKeyboardFocusTransitionController {
    private var textInputFocused = false

    fun onTextInputFocused() {
        textInputFocused = true
    }

    fun onNonTextFocused(hideKeyboard: () -> Unit) {
        if (!textInputFocused) return
        textInputFocused = false
        hideKeyboard()
    }
}

private enum class LoginErrorTarget {
    ACCESS_CODE,
    USERNAME,
    PASSWORD,
    NONE,
}

private data class LoginLayoutPolicy(
    val horizontalSafeInset: Dp,
    val verticalSafeInset: Dp,
    val pageHorizontalPadding: Dp,
    val cardMaxWidth: Dp,
    val cardHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
    val cardRadius: Dp,
    val fieldHeight: Dp,
    val optionHeight: Dp,
    val titleSizeSp: Int,
    val descriptionSizeSp: Int,
    val fieldTextSizeSp: Int,
    val optionTextSizeSp: Int,
    val compact: Boolean,
)

private fun resolveLoginLayoutPolicy(
    isTv: Boolean,
    width: Dp,
    height: Dp,
): LoginLayoutPolicy {
    val compactHeight = height < if (isTv) 620.dp else 560.dp
    val horizontalSafeInset =
        if (isTv) {
            (width * .045f).coerceIn(32.dp, 64.dp)
        } else {
            0.dp
        }
    val verticalSafeInset =
        if (isTv) {
            (height * .04f).coerceIn(24.dp, 40.dp)
        } else {
            0.dp
        }
    val pageHorizontalPadding =
        if (isTv) {
            horizontalSafeInset
        } else {
            (width * .055f).coerceIn(16.dp, 28.dp)
        }
    val cardMaxWidth = when {
        isTv -> (width * .41f).coerceIn(420.dp, 520.dp)
        width >= 600.dp -> 560.dp
        else -> 480.dp
    }

    return LoginLayoutPolicy(
        horizontalSafeInset = horizontalSafeInset,
        verticalSafeInset = verticalSafeInset,
        pageHorizontalPadding = pageHorizontalPadding,
        cardMaxWidth = cardMaxWidth,
        cardHorizontalPadding = when {
            isTv -> (cardMaxWidth * .07f).coerceIn(28.dp, 40.dp)
            width >= 600.dp -> 26.dp
            else -> 22.dp
        },
        cardVerticalPadding = when {
            isTv && compactHeight -> 16.dp
            isTv && height >= 720.dp -> 30.dp
            isTv -> 26.dp
            compactHeight -> 20.dp
            else -> 24.dp
        },
        cardRadius = when {
            isTv && compactHeight -> 24.dp
            isTv -> 28.dp
            width >= 600.dp -> 24.dp
            else -> 22.dp
        },
        fieldHeight = when {
            isTv && compactHeight -> 50.dp
            isTv -> 56.dp
            width >= 600.dp -> 52.dp
            else -> 50.dp
        },
        optionHeight = when {
            isTv && compactHeight -> 30.dp
            isTv -> 32.dp
            else -> 40.dp
        },
        titleSizeSp = when {
            isTv && compactHeight -> 25
            isTv -> 31
            width >= 600.dp -> 28
            else -> 25
        },
        descriptionSizeSp = when {
            isTv -> if (compactHeight) 14 else 16
            else -> 14
        },
        fieldTextSizeSp = if (isTv && !compactHeight) 17 else 15,
        optionTextSizeSp = if (isTv && !compactHeight) 15 else 14,
        compact = compactHeight,
    )
}

@Composable
fun LoginScreen(
    isTv: Boolean,
    pairingOffered: Boolean,
    pairing: PairingUiState,
    pairingReturnFocusSuspended: Boolean,
    isStarting: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String, String, Boolean) -> Unit,
    onStartPairing: (Boolean) -> Unit,
    onRetryPairing: () -> Unit,
    onRegeneratePairing: () -> Unit,
    onCancelPairing: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val activity = remember(view.context) { view.context.findActivity() }
    val tvInitialFocusRequester = remember { FocusRequester() }
    val submitRequester = remember { FocusRequester() }
    val subscribeRequester = remember { FocusRequester() }
    val lastCardFocusRequester = remember(submitRequester) {
        LoginCardFocusRequesterHolder(submitRequester)
    }
    val keyboardFocusTransitionController = remember { LoginKeyboardFocusTransitionController() }
    var initialTvFocusRequested by remember { mutableStateOf(false) }
    var viewerEngaged by remember { mutableStateOf(false) }
    var showSubscriptionQr by rememberSaveable { mutableStateOf(false) }
    val loginRenewalLink = remember {
        OperationsGrowthLinkConfig(
            enabled = true,
            title = "اشتراك جديد",
            url = HULK_WEBSITE,
            displayText = "hulksa.com",
        )
    }
    val persistedAccessCode = remember(view.context) {
        sa.hulksa.player.data.AccountSessionStore(view.context).lastAccessCode().orEmpty()
    }
    val accessCodeState = rememberSaveable(persistedAccessCode) { mutableStateOf(persistedAccessCode) }
    val usernameState = rememberSaveable { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberAccount by rememberSaveable { mutableStateOf(true) }
    var websiteOpenError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(isTv, activity) {
        if (isTv && activity != null) {
            val window = activity.window
            val previousSoftInputMode = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose { window.setSoftInputMode(previousSoftInputMode) }
        } else {
            onDispose { }
        }
    }

    val hideKeyboard: () -> Unit = {
        if (keyboardController != null) {
            keyboardController.hide()
        } else {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        Unit
    }
    val dismissKeyboard: () -> Unit = {
        hideKeyboard()
        focusManager.clearFocus(force = true)
        Unit
    }
    val onTextInputFocus: () -> Unit = {
        if (isTv) keyboardFocusTransitionController.onTextInputFocused()
    }
    val onNonTextFocus: () -> Unit = {
        if (isTv) {
            keyboardFocusTransitionController.onNonTextFocused(hideKeyboard)
        } else {
            hideKeyboard()
        }
    }
    val submit = {
        if (!isLoading && !isStarting) {
            if (isTv) {
                onNonTextFocus()
            } else {
                dismissKeyboard()
            }
            onLogin(
                accessCodeState.value,
                usernameState.value.trim(),
                passwordState.value,
                rememberAccount,
            )
        }
    }
    val openWebsite = {
        runCatching { uriHandler.openUri(HULK_WEBSITE) }
            .onSuccess { websiteOpenError = null }
            .onFailure {
                websiteOpenError = "تعذر فتح الموقع. تحقق من وجود متصفح او اتصال ثم حاول مرة اخرى"
            }
        Unit
    }

    LaunchedEffect(isTv, isLoading, isStarting) {
        if (isLoading || isStarting) {
            hideKeyboard()
            if (!isTv) {
                focusManager.clearFocus(force = true)
            }
        }
    }
    LaunchedEffect(isTv, isStarting, isLoading, initialTvFocusRequested) {
        if (isTv && !initialTvFocusRequested && !isStarting && !isLoading) {
            withFrameNanos { }
            hideKeyboard()
            runCatching { tvInitialFocusRequester.requestFocus() }
            initialTvFocusRequested = true
            withFrameNanos { }
            hideKeyboard()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent {
                    viewerEngaged = true
                    false
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { viewerEngaged = true },
                        onTap = { dismissKeyboard() },
                    )
                },
        ) {
            val policy = resolveLoginLayoutPolicy(
                isTv = isTv,
                width = maxWidth,
                height = maxHeight,
            )
            val subscriptionUsesQr = isTv || maxWidth >= 600.dp
            val useTvWall = isTv || maxWidth >= maxHeight
            val safeWidth =
                (maxWidth - if (isTv) policy.horizontalSafeInset * 2 else policy.pageHorizontalPadding * 2)
                    .coerceAtLeast(0.dp)
            val resolvedCardMaxWidth = policy.cardMaxWidth.coerceAtMost(safeWidth)

            LoginPosterWallBackground(
                useTvWall = useTvWall,
                modifier = Modifier.fillMaxSize(),
            )

            if (isStarting) {
                LoadingRing(
                    label = "جاري التجهيز...",
                    modifier = Modifier.align(Alignment.Center),
                )
                return@BoxWithConstraints
            }

            val usableNetwork by rememberUsableNetworkState()
            val errorPresentation = loginErrorPresentation(
                errorMessage = errorMessage,
                serviceUnavailableMessage = PortalException.ServiceUnavailable.message.orEmpty(),
                confirmedOffline = !usableNetwork,
            )

            val panel: @Composable (Modifier) -> Unit = { modifier ->
                LoginPanel(
                    accessCodeState = accessCodeState,
                    usernameState = usernameState,
                    passwordState = passwordState,
                    showPassword = showPassword,
                    onShowPasswordChange = { showPassword = !showPassword },
                    rememberAccount = rememberAccount,
                    onRememberChange = { rememberAccount = !rememberAccount },
                    isLoading = isLoading,
                    isTv = isTv,
                    showKeyboardOnFocus = !isTv || viewerEngaged,
                    subscriptionUsesQr = subscriptionUsesQr,
                    pairingOffered = pairingOffered,
                    pairingVisible = pairing.visible,
                    pairingReturnFocusSuspended = pairingReturnFocusSuspended,
                    errorMessage = errorMessage,
                    displayedErrorMessage = errorPresentation.displayText,
                    networkFailure = errorPresentation.confirmedOffline,
                    websiteErrorMessage = websiteOpenError,
                    onSubmit = submit,
                    onOpenSubscription = {
                        if (subscriptionUsesQr) {
                            showSubscriptionQr = true
                        } else {
                            openWebsite()
                        }
                    },
                    onStartPairing = onStartPairing,
                    onTextInputFocus = onTextInputFocus,
                    onNonTextFocus = onNonTextFocus,
                    onCardFocusChanged = lastCardFocusRequester::update,
                    initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                    submitRequester = submitRequester,
                    subscribeRequester = subscribeRequester,
                    policy = policy,
                    modifier = modifier,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isTv) {
                            Modifier.padding(
                                horizontal = policy.horizontalSafeInset,
                                vertical = policy.verticalSafeInset,
                            )
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(
                                    horizontal = policy.pageHorizontalPadding,
                                    vertical = if (policy.compact) 8.dp else 12.dp,
                                )
                                .imePadding()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val pageScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .verticalScroll(pageScrollState)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    panel(
                        Modifier
                            .widthIn(max = resolvedCardMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            }

            if (showSubscriptionQr) {
                GrowthQrDialog(
                    destination = GrowthDestination.RENEWAL,
                    link = loginRenewalLink,
                    onDismiss = { showSubscriptionQr = false },
                )
            }

            if (pairingOffered && pairing.visible) {
                PairingLoginDialog(
                    state = pairing,
                    onDismiss = onCancelPairing,
                    onRetry = onRetryPairing,
                    onRegenerate = onRegeneratePairing,
                )
            }
        }
    }
}

@Composable
private fun LoginPosterWallBackground(
    useTvWall: Boolean,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(
            if (useTvWall) {
                R.drawable.login_poster_wall_tv
            } else {
                R.drawable.login_poster_wall_phone
            },
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    )
}

@Composable
private fun LoginPanel(
    accessCodeState: MutableState<String>,
    usernameState: MutableState<String>,
    passwordState: MutableState<String>,
    showPassword: Boolean,
    onShowPasswordChange: () -> Unit,
    rememberAccount: Boolean,
    onRememberChange: () -> Unit,
    isLoading: Boolean,
    isTv: Boolean,
    showKeyboardOnFocus: Boolean,
    subscriptionUsesQr: Boolean,
    pairingOffered: Boolean,
    pairingVisible: Boolean,
    pairingReturnFocusSuspended: Boolean,
    errorMessage: String?,
    displayedErrorMessage: String?,
    networkFailure: Boolean,
    websiteErrorMessage: String?,
    onSubmit: () -> Unit,
    onOpenSubscription: () -> Unit,
    onStartPairing: (Boolean) -> Unit,
    onTextInputFocus: () -> Unit,
    onNonTextFocus: () -> Unit,
    onCardFocusChanged: (FocusRequester) -> Unit,
    initialFocusRequester: FocusRequester?,
    submitRequester: FocusRequester,
    subscribeRequester: FocusRequester,
    policy: LoginLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val accessRequester = initialFocusRequester ?: remember { FocusRequester() }
    val usernameRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val rememberRequester = remember { FocusRequester() }
    val showPasswordRequester = remember { FocusRequester() }
    val pairingRequester = remember { FocusRequester() }
    val panelShape = RoundedCornerShape(policy.cardRadius)
    val displayedError = displayedErrorMessage
    val actionFloorDp = movieActionHeightDp(isTv = isTv, compactHeight = !isTv && policy.compact)
    val actionCaptionGap = rememberActionCaptionSpace()
    var pairingWasVisible by remember { mutableStateOf(false) }
    var pairingTriggerPlaced by remember { mutableStateOf(false) }
    var pendingPairingReturnFocus by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading, errorMessage) {
        if (!isLoading && !errorMessage.isNullOrBlank()) {
            when (resolveLoginErrorTarget(errorMessage, usernameState.value, passwordState.value)) {
                LoginErrorTarget.ACCESS_CODE -> runCatching { accessRequester.requestFocus() }
                LoginErrorTarget.USERNAME -> runCatching { usernameRequester.requestFocus() }
                LoginErrorTarget.PASSWORD -> runCatching { passwordRequester.requestFocus() }
                LoginErrorTarget.NONE -> Unit
            }
        }
    }
    LaunchedEffect(pairingVisible) {
        if (pairingVisible) {
            pairingWasVisible = true
        } else if (pairingWasVisible) {
            pairingWasVisible = false
            // Restore only the actual pairing trigger while it is still offered. Discovery loss
            // keeps the existing eligible signup/login focus fallback untouched.
            if (pairingOffered) pendingPairingReturnFocus = true
        }
    }
    LaunchedEffect(pairingOffered) {
        if (!pairingOffered) pendingPairingReturnFocus = false
    }
    // Placement-driven return focus: the pending request resolves only after the real target has
    // been placed and no newer owner (loading, error target, account decision) owns focus.
    LaunchedEffect(
        pendingPairingReturnFocus,
        pairingOffered,
        pairingTriggerPlaced,
        isLoading,
        errorMessage,
        pairingReturnFocusSuspended,
    ) {
        if (
            pendingPairingReturnFocus &&
            pairingOffered &&
            pairingTriggerPlaced &&
            !isLoading &&
            errorMessage.isNullOrBlank() &&
            !pairingReturnFocusSuspended
        ) {
            pendingPairingReturnFocus = false
            runCatching { pairingRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF151713),
                        Color(0xFF0A0C09),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = .09f), panelShape)
            .padding(
                horizontal = policy.cardHorizontalPadding,
                vertical = policy.cardVerticalPadding,
            ),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "تسجيل الدخول",
            color = colors.text,
            fontSize = policy.titleSizeSp.sp,
            lineHeight = (policy.titleSizeSp + if (policy.compact) 5 else 7).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "ادخل بيانات اشتراكك للمتابعة",
            color = colors.textMuted,
            fontSize = policy.descriptionSizeSp.sp,
            lineHeight = (policy.descriptionSizeSp + if (policy.compact) 4 else 7).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (policy.compact) 12.dp else 20.dp))

        LoginCredentialTextField(
            state = accessCodeState,
            label = "كود الدخول",
            icon = Icons.Rounded.Key,
            iconBase = colors.text,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
            onFocused = {
                onTextInputFocus()
                onCardFocusChanged(accessRequester)
            },
            modifier = Modifier
                .focusRequester(accessRequester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = usernameRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth()
                .heightIn(min = policy.fieldHeight),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                showKeyboardOnFocus = showKeyboardOnFocus,
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { usernameRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (policy.compact) 2.dp else 10.dp))

        LoginCredentialTextField(
            state = usernameState,
            label = "اسم المستخدم",
            icon = Icons.Rounded.Person,
            iconBase = colors.text,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
            onFocused = {
                onTextInputFocus()
                onCardFocusChanged(usernameRequester)
            },
            modifier = Modifier
                .focusRequester(usernameRequester)
                .focusProperties {
                    up = accessRequester
                    down = passwordRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth()
                .heightIn(min = policy.fieldHeight),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
                showKeyboardOnFocus = showKeyboardOnFocus,
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { passwordRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (policy.compact) 2.dp else 10.dp))

        LoginCredentialTextField(
            state = passwordState,
            label = "كلمة المرور",
            icon = Icons.Rounded.Lock,
            iconBase = colors.text,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
            onFocused = {
                onTextInputFocus()
                onCardFocusChanged(passwordRequester)
            },
            modifier = Modifier
                .focusRequester(passwordRequester)
                .focusProperties {
                    up = usernameRequester
                    down = showPasswordRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth()
                .heightIn(min = policy.fieldHeight),
            visualTransformation =
                if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                showKeyboardOnFocus = showKeyboardOnFocus,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )

        Spacer(
            Modifier.height(
                if (isTv) {
                    if (policy.compact) 12.dp else 16.dp
                } else {
                    if (policy.compact) 8.dp else 12.dp
                },
            ),
        )

        LoginOptionGroups(
            isTv = isTv,
            showPassword = showPassword,
            onShowPasswordChange = onShowPasswordChange,
            rememberAccount = rememberAccount,
            onRememberChange = onRememberChange,
            onNonTextFocus = onNonTextFocus,
            onCardFocusChanged = onCardFocusChanged,
            passwordRequester = passwordRequester,
            submitRequester = submitRequester,
            showPasswordRequester = showPasswordRequester,
            rememberRequester = rememberRequester,
            policy = policy,
        )

        if (displayedError != null) {
            Spacer(Modifier.height(if (isTv) 6.dp else 10.dp))
            EntryNotice(
                message = displayedError,
                networkFailure = networkFailure,
                compact = policy.compact,
            )
        }

        if (websiteErrorMessage != null) {
            Spacer(Modifier.height(if (isTv) 6.dp else 10.dp))
            EntryNotice(
                message = websiteErrorMessage,
                compact = policy.compact,
                liveRegionPolite = false,
            )
        }

        Spacer(
            Modifier.height(
                if (isTv) {
                    if (displayedError != null || websiteErrorMessage != null) 6.dp else 10.dp
                } else {
                    if (policy.compact) 0.dp else 8.dp
                },
            ),
        )
        EntryActionButton(
            text = "تسجيل الدخول",
            onClick = onSubmit,
            enabled = !isLoading,
            loading = isLoading,
            primary = true,
            glyph = Icons.AutoMirrored.Rounded.Login,
            minHeightDp = actionFloorDp,
            captionGlyphGap = actionCaptionGap,
            onFocused = {
                onNonTextFocus()
                onCardFocusChanged(submitRequester)
            },
            modifier = Modifier
                .focusRequester(submitRequester)
                .focusProperties {
                    up = showPasswordRequester
                    down = subscribeRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(if (policy.compact) 4.dp else 9.dp))
        EntryActionButton(
            text = "اشتراك جديد",
            onClick = onOpenSubscription,
            outlined = true,
            glyph = if (subscriptionUsesQr) Icons.Rounded.QrCode2 else Icons.Rounded.ShoppingCart,
            minHeightDp = actionFloorDp,
            captionGlyphGap = actionCaptionGap,
            onFocused = {
                onNonTextFocus()
                onCardFocusChanged(subscribeRequester)
            },
            modifier = Modifier
                .focusRequester(subscribeRequester)
                .focusProperties {
                    up = submitRequester
                    down = if (pairingOffered) pairingRequester else FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth(),
        )
        if (pairingOffered) {
            Spacer(Modifier.height(if (policy.compact) 4.dp else 9.dp))
            EntryActionButton(
                text = "الدخول السريع",
                onClick = {
                    onNonTextFocus()
                    onStartPairing(rememberAccount)
                },
                outlined = true,
                accentOutlined = true,
                glyph = PairingQuickLoginIcon,
                minHeightDp = actionFloorDp,
                onFocused = {
                    onNonTextFocus()
                    onCardFocusChanged(pairingRequester)
                },
                modifier = Modifier
                    .focusRequester(pairingRequester)
                    .onPlaced { pairingTriggerPlaced = true }
                    .focusProperties {
                        up = subscribeRequester
                        down = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoginCredentialTextField(
    state: MutableState<String>,
    label: String,
    icon: ImageVector,
    iconBase: Color,
    textSizeSp: Int,
    bringIntoViewOnFocus: Boolean = true,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    LoginTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = label,
        icon = icon,
        iconBase = iconBase,
        textSizeSp = textSizeSp,
        bringIntoViewOnFocus = bringIntoViewOnFocus,
        onFocused = onFocused,
        modifier = modifier,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    iconBase: Color,
    textSizeSp: Int,
    bringIntoViewOnFocus: Boolean = true,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalHulkColors.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    val background by animateColorAsState(
        targetValue =
            if (focused) {
                Color(0xFF1A1B15)
            } else {
                Color(0xFF11130F)
            },
        label = "loginFieldBackground",
    )
    val border by animateColorAsState(
        targetValue = if (focused) colors.gold else Color.White.copy(alpha = .13f),
        label = "loginFieldBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused) colors.goldBright else iconBase,
        label = "loginFieldIcon",
    )

    LaunchedEffect(focused, bringIntoViewOnFocus) {
        if (focused && bringIntoViewOnFocus) bringIntoViewRequester.bringIntoView()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .then(
                    if (bringIntoViewOnFocus) {
                        Modifier.bringIntoViewRequester(bringIntoViewRequester)
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .clip(shape)
                .background(background)
                .border(if (focused) 2.dp else 1.dp, border, shape)
                .padding(horizontal = 17.dp)
                .semantics { contentDescription = label },
            singleLine = true,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                textDirection = TextDirection.ContentOrLtr,
            ),
            cursorBrush = Brush.verticalGradient(listOf(colors.goldBright, colors.goldBright)),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // First child in RTL: the adornment sits physically RIGHT of the wording.
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = label,
                                color = colors.textMuted,
                                fontSize = (textSizeSp - 1).sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                innerField()
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun rememberActionCaptionSpace(): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val actionFontFamily = LocalTextStyle.current.fontFamily
    return remember(density, textMeasurer, actionFontFamily) {
        with(density) {
            textMeasurer.measure(
                text = AnnotatedString(" "),
                style = TextStyle(
                    fontFamily = actionFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            ).size.width.toDp()
        }
    }
}

@Composable
private fun LoginOptionGroups(
    isTv: Boolean,
    showPassword: Boolean,
    onShowPasswordChange: () -> Unit,
    rememberAccount: Boolean,
    onRememberChange: () -> Unit,
    onNonTextFocus: () -> Unit,
    onCardFocusChanged: (FocusRequester) -> Unit,
    passwordRequester: FocusRequester,
    submitRequester: FocusRequester,
    showPasswordRequester: FocusRequester,
    rememberRequester: FocusRequester,
    policy: LoginLayoutPolicy,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val captionStyle = TextStyle(
            fontSize = policy.optionTextSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val boxPx = with(density) { 21.dp.roundToPx() }
        val innerGapPx = with(density) { 10.dp.roundToPx() }
        val groupPaddingPx = with(density) { 18.dp.roundToPx() }
        val minOuterGapPx = with(density) { 6.dp.roundToPx() }
        val rememberWidthPx =
            textMeasurer.measure(AnnotatedString("تذكر الحساب"), captionStyle).size.width
        val showWidthPx =
            textMeasurer.measure(AnnotatedString("اظهار كلمة المرور"), captionStyle).size.width
        val requiredPx = rememberWidthPx + showWidthPx +
            boxPx * 2 + innerGapPx * 2 + groupPaddingPx * 2 + minOuterGapPx
        val fitsSingleRow = with(density) { maxWidth.roundToPx() } >= requiredPx

        if (fitsSingleRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // First RTL child anchors to the physical RIGHT edge (show-password group).
                LoginOption(
                    text = "اظهار كلمة المرور",
                    checked = showPassword,
                    onClick = onShowPasswordChange,
                    onFocused = {
                        onNonTextFocus()
                        onCardFocusChanged(showPasswordRequester)
                    },
                    minHeight = policy.optionHeight,
                    textSizeSp = policy.optionTextSizeSp,
                    animateFocus = !isTv,
                    modifier = Modifier
                        .focusRequester(showPasswordRequester)
                        .focusProperties {
                            up = passwordRequester
                            down = submitRequester
                            left = rememberRequester
                            right = FocusRequester.Cancel
                        },
                )
                // Last RTL child anchors to the physical LEFT edge (remember group).
                LoginOption(
                    text = "تذكر الحساب",
                    checked = rememberAccount,
                    onClick = onRememberChange,
                    onFocused = {
                        onNonTextFocus()
                        onCardFocusChanged(rememberRequester)
                    },
                    minHeight = policy.optionHeight,
                    textSizeSp = policy.optionTextSizeSp,
                    animateFocus = !isTv,
                    modifier = Modifier
                        .focusRequester(rememberRequester)
                        .focusProperties {
                            up = passwordRequester
                            down = submitRequester
                            left = FocusRequester.Cancel
                            right = showPasswordRequester
                        },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    LoginOption(
                        text = "اظهار كلمة المرور",
                        checked = showPassword,
                        onClick = onShowPasswordChange,
                        onFocused = {
                            onNonTextFocus()
                            onCardFocusChanged(showPasswordRequester)
                        },
                        minHeight = policy.optionHeight,
                        textSizeSp = policy.optionTextSizeSp,
                        animateFocus = !isTv,
                        modifier = Modifier
                            .focusRequester(showPasswordRequester)
                            .focusProperties {
                                up = passwordRequester
                                down = rememberRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    LoginOption(
                        text = "تذكر الحساب",
                        checked = rememberAccount,
                        onClick = onRememberChange,
                        onFocused = {
                            onNonTextFocus()
                            onCardFocusChanged(rememberRequester)
                        },
                        minHeight = policy.optionHeight,
                        textSizeSp = policy.optionTextSizeSp,
                        animateFocus = !isTv,
                        modifier = Modifier
                            .focusRequester(rememberRequester)
                            .focusProperties {
                                up = showPasswordRequester
                                down = submitRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    minHeight: Dp,
    textSizeSp: Int,
    animateFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val targetBackground = if (focused) colors.gold.copy(alpha = .08f) else Color.Transparent
    val targetOutline = if (focused) colors.gold.copy(alpha = .78f) else Color.Transparent
    val background = if (animateFocus) {
        val animated by animateColorAsState(
            targetValue = targetBackground,
            label = "loginOptionBackground",
        )
        animated
    } else {
        targetBackground
    }
    val outline = if (animateFocus) {
        val animated by animateColorAsState(
            targetValue = targetOutline,
            label = "loginOptionOutline",
        )
        animated
    } else {
        targetOutline
    }

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 0.dp, outline, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 9.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val checkShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(checkShape)
                .background(if (checked) colors.gold else Color(0xFF191B16))
                .border(
                    1.dp,
                    if (checked) {
                        colors.goldBright.copy(alpha = .86f)
                    } else {
                        Color.White.copy(alpha = .22f)
                    },
                    checkShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = if (focused) colors.text else colors.textMuted,
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp + 5).sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
        )
    }
}


private fun resolveLoginErrorTarget(
    errorMessage: String,
    username: String,
    password: String,
): LoginErrorTarget {
    val normalized = errorMessage.withoutArabicHamzas()
    return when {
        "كود الدخول" in normalized || "كود دخول" in normalized -> LoginErrorTarget.ACCESS_CODE
        "اسم المستخدم وكلمة المرور" in normalized && username.isBlank() -> LoginErrorTarget.USERNAME
        "اسم المستخدم وكلمة المرور" in normalized && password.isEmpty() -> LoginErrorTarget.PASSWORD
        "بيانات الاشتراك غير صحيحة" in normalized -> LoginErrorTarget.USERNAME
        else -> LoginErrorTarget.NONE
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun String.withoutArabicHamzas(): String =
    replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')

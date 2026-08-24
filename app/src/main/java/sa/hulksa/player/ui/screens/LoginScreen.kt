package sa.hulksa.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val HULK_WEBSITE = "https://hulksa.com/"

private enum class LoginComposition {
    PREMIUM_SPLIT,
    CENTERED,
}

private enum class LoginErrorTarget {
    ACCESS_CODE,
    USERNAME,
    PASSWORD,
    NONE,
}

private data class LoginLayoutPolicy(
    val composition: LoginComposition,
    val horizontalSafeInset: Dp,
    val verticalSafeInset: Dp,
    val pageHorizontalPadding: Dp,
    val splitGap: Dp,
    val cardMaxWidth: Dp,
    val cardMaxHeight: Dp,
    val cardHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
    val cardRadius: Dp,
    val fieldHeight: Dp,
    val primaryActionHeight: Dp,
    val secondaryActionHeight: Dp,
    val optionHeight: Dp,
    val logoSize: Dp,
    val brandRegionHeight: Dp,
    val titleSizeSp: Int,
    val descriptionSizeSp: Int,
    val fieldTextSizeSp: Int,
    val optionTextSizeSp: Int,
    val buttonTextSizeSp: Int,
    val panelScrollable: Boolean,
    val compact: Boolean,
)

private fun resolveLoginLayoutPolicy(
    isTv: Boolean,
    width: Dp,
    height: Dp,
    fontScale: Float,
    imeVisible: Boolean,
): LoginLayoutPolicy {
    val compactHeight = height < if (isTv) 620.dp else 560.dp
    val aspectRatio = width.value / height.value.coerceAtLeast(1f)
    val expandedLandscape =
        !imeVisible &&
            width >= 840.dp &&
            height >= 480.dp &&
            width > height
    val roomyMediumLandscape =
        !imeVisible &&
            width >= 720.dp &&
            width < 840.dp &&
            height >= 480.dp &&
            aspectRatio >= 1.45f
    val composition =
        if (isTv || expandedLandscape || roomyMediumLandscape) {
            LoginComposition.PREMIUM_SPLIT
        } else {
            LoginComposition.CENTERED
        }

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
        composition == LoginComposition.PREMIUM_SPLIT ->
            (width * .44f).coerceIn(400.dp, 520.dp)
        width >= 600.dp -> 560.dp
        else -> 480.dp
    }
    val logoSize = when {
        isTv -> (minOf(width, height) * .38f).coerceIn(180.dp, 250.dp)
        width >= 600.dp -> (minOf(width, height) * .25f).coerceIn(120.dp, 190.dp)
        imeVisible -> 60.dp
        else -> (minOf(width, height) * .20f).coerceIn(68.dp, 88.dp)
    }
    val brandRegionHeight = when {
        composition == LoginComposition.PREMIUM_SPLIT -> height
        imeVisible -> 72.dp
        width >= 840.dp -> 176.dp
        width >= 600.dp -> 152.dp
        compactHeight -> 96.dp
        else -> 116.dp
    }
    val cardMaxHeight = when {
        isTv -> (height * .88f).coerceAtLeast(360.dp)
        composition == LoginComposition.PREMIUM_SPLIT -> (height * .90f).coerceAtLeast(360.dp)
        else -> height * .94f
    }

    return LoginLayoutPolicy(
        composition = composition,
        horizontalSafeInset = horizontalSafeInset,
        verticalSafeInset = verticalSafeInset,
        pageHorizontalPadding = pageHorizontalPadding,
        splitGap = (width * .035f).coerceIn(24.dp, 52.dp),
        cardMaxWidth = cardMaxWidth,
        cardMaxHeight = cardMaxHeight,
        cardHorizontalPadding = when {
            isTv -> (cardMaxWidth * .07f).coerceIn(28.dp, 40.dp)
            width >= 600.dp -> 26.dp
            else -> 22.dp
        },
        cardVerticalPadding = when {
            isTv && compactHeight -> 18.dp
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
        primaryActionHeight = when {
            isTv && compactHeight -> 52.dp
            isTv -> 56.dp
            else -> 52.dp
        },
        secondaryActionHeight = when {
            isTv && compactHeight -> 50.dp
            isTv -> 54.dp
            else -> 50.dp
        },
        optionHeight = when {
            isTv && compactHeight -> 38.dp
            isTv -> 42.dp
            else -> 48.dp
        },
        logoSize = logoSize,
        brandRegionHeight = brandRegionHeight,
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
        buttonTextSizeSp = if (isTv && !compactHeight) 17 else 15,
        panelScrollable =
            !isTv &&
                composition == LoginComposition.PREMIUM_SPLIT &&
                (compactHeight || fontScale >= 1.3f || imeVisible),
        compact = compactHeight,
    )
}

@Composable
fun LoginScreen(
    isTv: Boolean,
    isStarting: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String, String, Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val density = LocalDensity.current
    val activity = remember(view.context) { view.context.findActivity() }
    val tvInitialFocusRequester = remember { FocusRequester() }
    var initialTvFocusRequested by remember { mutableStateOf(false) }
    val persistedAccessCode = remember(view.context) {
        sa.hulksa.player.data.AccountSessionStore(view.context).lastAccessCode().orEmpty()
    }
    var accessCode by rememberSaveable(persistedAccessCode) { mutableStateOf(persistedAccessCode) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberAccount by rememberSaveable { mutableStateOf(true) }

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
        keyboardController?.hide()
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        Unit
    }
    val dismissKeyboard: () -> Unit = {
        hideKeyboard()
        focusManager.clearFocus(force = true)
        Unit
    }
    val submit = {
        if (!isLoading && !isStarting) {
            if (isTv) {
                hideKeyboard()
            } else {
                dismissKeyboard()
            }
            onLogin(accessCode, username.trim(), password, rememberAccount)
        }
    }
    val openWebsite = {
        runCatching { uriHandler.openUri(HULK_WEBSITE) }
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismissKeyboard() })
                },
        ) {
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            val policy = resolveLoginLayoutPolicy(
                isTv = isTv,
                width = maxWidth,
                height = maxHeight,
                fontScale = density.fontScale,
                imeVisible = imeVisible,
            )

            PremiumCinematicBackground(Modifier.fillMaxSize())

            if (isStarting) {
                LoadingRing(
                    label = "جاري التجهيز...",
                    modifier = Modifier.align(Alignment.Center),
                )
                return@BoxWithConstraints
            }

            val panel: @Composable (Modifier) -> Unit = { modifier ->
                LoginPanel(
                    accessCode = accessCode,
                    onAccessCodeChange = { accessCode = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    showPassword = showPassword,
                    onShowPasswordChange = { showPassword = !showPassword },
                    rememberAccount = rememberAccount,
                    onRememberChange = { rememberAccount = !rememberAccount },
                    isLoading = isLoading,
                    isTv = isTv,
                    errorMessage = errorMessage,
                    onSubmit = submit,
                    onOpenWebsite = openWebsite,
                    onNonTextFocus = hideKeyboard,
                    initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                    policy = policy,
                    modifier = modifier,
                )
            }

            when (policy.composition) {
                LoginComposition.PREMIUM_SPLIT -> {
                    Row(
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
                                },
                            )
                            .then(if (isTv) Modifier else Modifier.imePadding()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.08f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            panel(
                                Modifier
                                    .widthIn(max = policy.cardMaxWidth)
                                    .fillMaxWidth(),
                            )
                        }

                        Spacer(Modifier.width(policy.splitGap))

                        LoginBrandRegion(
                            logoSize = policy.logoSize,
                            modifier = Modifier
                                .weight(.92f)
                                .fillMaxHeight(),
                        )
                    }
                }

                LoginComposition.CENTERED -> {
                    val pageScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding()
                            .verticalScroll(pageScrollState)
                            .padding(
                                horizontal = policy.pageHorizontalPadding,
                                vertical = if (policy.compact) 8.dp else 12.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LoginBrandRegion(
                            logoSize = policy.logoSize,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(policy.brandRegionHeight),
                        )
                        Spacer(Modifier.height(if (policy.compact) 4.dp else 10.dp))
                        panel(
                            Modifier
                                .widthIn(max = policy.cardMaxWidth)
                                .fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCinematicBackground(modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current

    Canvas(modifier = modifier.background(colors.background)) {
        val wideScene = size.width >= size.height * 1.18f
        val brandCenter = Offset(
            size.width * if (wideScene) .28f else .50f,
            size.height * if (wideScene) .49f else .18f,
        )

        drawRect(
            brush =
                if (wideScene) {
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF12120D),
                            .28f to Color(0xFF0D0E0A),
                            .55f to Color(0xFF080906),
                            .76f to Color(0xFF050604),
                            1f to Color(0xFF020302),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF12120D),
                            .24f to Color(0xFF0B0C08),
                            .50f to Color(0xFF070805),
                            1f to Color(0xFF020302),
                        ),
                    )
                },
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to colors.goldDeep.copy(alpha = if (wideScene) .20f else .14f),
                    .22f to colors.goldDeep.copy(alpha = .08f),
                    .52f to Color(0xFF18150C).copy(alpha = .035f),
                    1f to Color.Transparent,
                ),
                center = brandCenter,
                radius = maxOf(size.width, size.height) * if (wideScene) .43f else .34f,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = .055f),
                    .20f to colors.goldBright.copy(alpha = .028f),
                    .58f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(
                    size.width * if (wideScene) .18f else .50f,
                    size.height * if (wideScene) .08f else 0f,
                ),
                radius = maxOf(size.width, size.height) * .48f,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to colors.goldDeep.copy(alpha = .065f),
                    .26f to colors.goldDeep.copy(alpha = .025f),
                    .68f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = Offset(
                    size.width * if (wideScene) .27f else .50f,
                    size.height * if (wideScene) .93f else .36f,
                ),
                radius = maxOf(size.width, size.height) * .52f,
            ),
        )

        drawRect(
            brush =
                if (wideScene) {
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            .32f to Color.Transparent,
                            .56f to colors.background.copy(alpha = .34f),
                            .72f to colors.background.copy(alpha = .70f),
                            1f to colors.background.copy(alpha = .94f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            .23f to Color.Transparent,
                            .45f to colors.background.copy(alpha = .20f),
                            .70f to colors.background.copy(alpha = .58f),
                            1f to colors.background.copy(alpha = .82f),
                        ),
                    )
                },
        )

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to colors.background.copy(alpha = .46f),
                    .17f to Color.Transparent,
                    .67f to Color.Transparent,
                    1f to colors.background.copy(alpha = .76f),
                ),
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    colors.background.copy(alpha = .62f),
                ),
                center = brandCenter,
                radius = maxOf(size.width, size.height) * .90f,
            ),
        )
    }
}

@Composable
private fun LoginBrandRegion(
    logoSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val frameShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(logoSize + 44.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = frameShape,
                    clip = false,
                )
                .clip(frameShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE612140F),
                            Color(0xF0080A07),
                        ),
                    ),
                )
                .border(
                    width = 1.5.dp,
                    color = colors.gold.copy(alpha = .72f),
                    shape = frameShape,
                )
                .padding(20.dp)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(.98f)
                    .clearAndSetSemantics { },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun LoginPanel(
    accessCode: String,
    onAccessCodeChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: () -> Unit,
    rememberAccount: Boolean,
    onRememberChange: () -> Unit,
    isLoading: Boolean,
    isTv: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    onNonTextFocus: () -> Unit,
    initialFocusRequester: FocusRequester?,
    policy: LoginLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val accessRequester = initialFocusRequester ?: remember { FocusRequester() }
    val usernameRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val rememberRequester = remember { FocusRequester() }
    val showPasswordRequester = remember { FocusRequester() }
    val submitRequester = remember { FocusRequester() }
    val subscribeRequester = remember { FocusRequester() }
    val panelScrollState = rememberScrollState()
    val panelShape = RoundedCornerShape(policy.cardRadius)
    val displayedError = errorMessage?.withoutArabicHamzas()

    LaunchedEffect(isLoading, errorMessage) {
        if (!isLoading && !errorMessage.isNullOrBlank()) {
            when (resolveLoginErrorTarget(errorMessage, username, password)) {
                LoginErrorTarget.ACCESS_CODE -> runCatching { accessRequester.requestFocus() }
                LoginErrorTarget.USERNAME -> runCatching { usernameRequester.requestFocus() }
                LoginErrorTarget.PASSWORD -> runCatching { passwordRequester.requestFocus() }
                LoginErrorTarget.NONE -> Unit
            }
        }
    }

    Column(
        modifier = modifier
            .then(
                if (policy.composition == LoginComposition.PREMIUM_SPLIT) {
                    Modifier.heightIn(max = policy.cardMaxHeight)
                } else {
                    Modifier
                },
            )
            .shadow(
                elevation = if (isTv) 18.dp else 12.dp,
                shape = panelShape,
                clip = false,
            )
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF5151713),
                        Color(0xF20A0C09),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = .09f), panelShape)
            .then(
                if (policy.panelScrollable) {
                    Modifier.verticalScroll(panelScrollState)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = policy.cardHorizontalPadding,
                vertical = policy.cardVerticalPadding,
            ),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "اهلا بك",
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

        LoginTextField(
            value = accessCode,
            onValueChange = onAccessCodeChange,
            label = "كود الدخول",
            icon = Icons.Rounded.Key,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
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
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { usernameRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (policy.compact) 2.dp else 10.dp))

        LoginTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "اسم المستخدم",
            icon = Icons.Rounded.Person,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
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
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { passwordRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (policy.compact) 2.dp else 10.dp))

        LoginTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "كلمة المرور",
            icon = Icons.Rounded.Lock,
            textSizeSp = policy.fieldTextSizeSp,
            bringIntoViewOnFocus = !isTv,
            modifier = Modifier
                .focusRequester(passwordRequester)
                .focusProperties {
                    up = usernameRequester
                    down = rememberRequester
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
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(if (policy.compact) 1.dp else 8.dp))

        LoginOption(
            text = "تذكر الحساب",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
            minHeight = policy.optionHeight,
            textSizeSp = policy.optionTextSizeSp,
            modifier = Modifier
                .align(Alignment.Start)
                .then(
                    if (isTv) {
                        Modifier.widthIn(min = 220.dp, max = 320.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .focusRequester(rememberRequester)
                .focusProperties {
                    up = passwordRequester
                    down = showPasswordRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        )
        LoginOption(
            text = "اظهر كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
            minHeight = policy.optionHeight,
            textSizeSp = policy.optionTextSizeSp,
            modifier = Modifier
                .align(Alignment.Start)
                .then(
                    if (isTv) {
                        Modifier.widthIn(min = 220.dp, max = 320.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .focusRequester(showPasswordRequester)
                .focusProperties {
                    up = rememberRequester
                    down = submitRequester
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        )

        if (isTv) {
            Spacer(Modifier.height(4.dp))
            val errorShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(errorShape)
                    .background(
                        if (displayedError != null) {
                            Color(0xFF4B171A).copy(alpha = .78f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = if (displayedError != null) 1.dp else 0.dp,
                        color = if (displayedError != null) {
                            Color(0xFFFF8A80).copy(alpha = .58f)
                        } else {
                            Color.Transparent
                        },
                        shape = errorShape,
                    )
                    .padding(horizontal = 10.dp)
                    .semantics {
                        if (displayedError != null) {
                            liveRegion = LiveRegionMode.Polite
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (displayedError != null) {
                    Text(
                        text = displayedError,
                        color = Color(0xFFFFDAD6),
                        fontSize = if (policy.compact) 12.sp else 13.sp,
                        lineHeight = if (policy.compact) 15.sp else 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else if (displayedError != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                ErrorNotice(displayedError)
            }
        }

        Spacer(Modifier.height(if (policy.compact) 2.dp else 13.dp))
        LoginActionButton(
            text = "دخول الى HULK",
            onClick = onSubmit,
            enabled = !isLoading,
            loading = isLoading,
            primary = true,
            minHeight = policy.primaryActionHeight,
            textSizeSp = policy.buttonTextSizeSp,
            onFocused = onNonTextFocus,
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
        LoginActionButton(
            text = "اشتراك او تجديد",
            onClick = onOpenWebsite,
            enabled = true,
            loading = false,
            primary = false,
            minHeight = policy.secondaryActionHeight,
            textSizeSp = policy.buttonTextSizeSp,
            onFocused = onNonTextFocus,
            modifier = Modifier
                .focusRequester(subscribeRequester)
                .focusProperties {
                    up = submitRequester
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(if (policy.compact) 2.dp else 8.dp))
        Text(
            text = "hulksa.com",
            color = colors.textMuted.copy(alpha = .78f),
            fontSize = if (policy.compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    textSizeSp: Int,
    bringIntoViewOnFocus: Boolean = true,
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
        targetValue = if (focused) colors.goldBright else colors.textMuted,
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
                .onFocusChanged { focused = it.isFocused }
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
private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    minHeight: Dp,
    textSizeSp: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val background by animateColorAsState(
        targetValue = if (focused) colors.gold.copy(alpha = .08f) else Color.Transparent,
        label = "loginOptionBackground",
    )
    val outline by animateColorAsState(
        targetValue = if (focused) colors.gold.copy(alpha = .78f) else Color.Transparent,
        label = "loginOptionOutline",
    )

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
            .padding(horizontal = 9.dp, vertical = 5.dp),
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

@Composable
private fun LoginActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    primary: Boolean,
    minHeight: Dp,
    textSizeSp: Int,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    val background by animateColorAsState(
        targetValue = when {
            !enabled && primary -> colors.gold.copy(alpha = .58f)
            primary && focused -> colors.goldBright
            primary -> colors.gold
            focused -> Color(0xFF24251D)
            else -> Color(0xFF12140F)
        },
        label = "loginButtonBackground",
    )
    val outline by animateColorAsState(
        targetValue = when {
            focused -> colors.goldBright
            primary -> colors.gold.copy(alpha = .64f)
            else -> colors.gold.copy(alpha = .44f)
        },
        label = "loginButtonOutline",
    )
    val textColor = if (primary) Color(0xFF111006) else colors.goldBright
    val displayText = if (loading) "جاري الدخول..." else text

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = displayText
                if (!enabled) disabled()
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                enabled = true,
                role = Role.Button,
                onClick = { if (enabled) onClick() },
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = textColor,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = displayText,
                color = textColor,
                fontSize = textSizeSp.sp,
                lineHeight = (textSizeSp + 5).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
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

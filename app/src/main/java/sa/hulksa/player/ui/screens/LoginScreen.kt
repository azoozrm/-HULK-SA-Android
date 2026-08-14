package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val HULK_WEBSITE = "https://hulksa.com/"

@Composable
fun LoginScreen(
    isTv: Boolean,
    isStarting: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String, String, Boolean) -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val fontScale = LocalDensity.current.fontScale
    val tvInitialFocusRequester = remember { FocusRequester() }
    val persistedAccessCode = remember(view.context) {
        sa.hulksa.player.data.AccountSessionStore(view.context).lastAccessCode().orEmpty()
    }
    var accessCode by rememberSaveable(persistedAccessCode) { mutableStateOf(persistedAccessCode) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var rememberAccount by rememberSaveable { mutableStateOf(true) }
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
        dismissKeyboard()
        onLogin(accessCode, username.trim(), password, rememberAccount)
    }
    val openWebsite = { runCatching { uriHandler.openUri(HULK_WEBSITE) }; Unit }

    LaunchedEffect(isLoading, isStarting) {
        if (isLoading || isStarting) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(isTv, isStarting, isLoading) {
        if (isTv && !isStarting && !isLoading) {
            withFrameNanos { }
            hideKeyboard()
            tvInitialFocusRequester.requestFocus()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.goldDeep.copy(alpha = .16f),
                        Color(0xFF090A07).copy(alpha = .42f),
                        Color.Transparent,
                    ),
                ),
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { dismissKeyboard() }) }
            .safeDrawingPadding()
            .imePadding(),
    ) {
        if (isStarting) {
            LoadingRing(
                label = "جاري التجهيز...",
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val availableWidth = maxWidth
        val wideThreshold = if (isTv) 900.dp else 760.dp
        val stableWindowWidthDp = adaptiveUi.screenWidthDp
        val stableWindowHeightDp = adaptiveUi.screenHeightDp
        val compactHeight = !isTv && stableWindowHeightDp < 600
        val compactMobileLandscape = !isTv &&
            stableWindowWidthDp > stableWindowHeightDp &&
            stableWindowHeightDp < 520
        val wide = maxWidth >= wideThreshold && (!compactHeight || compactMobileLandscape)
        val compactWideTv = isTv && wide && maxWidth < 1180.dp
        val compactTvHeight = isTv && wide && maxHeight < 640.dp

        if (wide) {
            val horizontalPadding = when {
                compactMobileLandscape -> 10.dp
                isTv && maxWidth >= 1440.dp -> 58.dp
                compactWideTv -> 20.dp
                isTv -> 30.dp
                else -> 36.dp
            }
            val verticalPadding = when {
                compactMobileLandscape -> 4.dp
                compactTvHeight -> 12.dp
                compactWideTv -> 8.dp
                isTv -> 24.dp
                else -> 20.dp
            }
            val sideGap = when {
                compactMobileLandscape -> 16.dp
                compactTvHeight -> 28.dp
                compactWideTv -> 32.dp
                isTv -> 36.dp
                else -> 30.dp
            }
            val innerWidth = availableWidth - horizontalPadding * 2
            val maxAccessibleLandscapePanelWidth =
                (innerWidth - sideGap * 2 - 1.dp - 252.dp).coerceAtLeast(280.dp)
            val panelWidth = when {
                compactMobileLandscape && fontScale >= 1.3f ->
                    (innerWidth * .52f)
                        .coerceIn(320.dp, 450.dp)
                        .coerceAtMost(maxAccessibleLandscapePanelWidth)
                compactMobileLandscape -> (innerWidth * .48f).coerceIn(280.dp, 344.dp)
                compactTvHeight -> 402.dp
                compactWideTv -> 424.dp
                isTv -> 456.dp
                else -> 430.dp
            }
            val brandColumnWidth = when {
                compactMobileLandscape -> (innerWidth - panelWidth - sideGap * 2 - 1.dp)
                    .coerceAtLeast(252.dp)
                compactTvHeight -> 402.dp
                compactWideTv -> 424.dp
                isTv -> 432.dp
                else -> 430.dp
            }
            val tvRightBias = when {
                compactTvHeight -> 8.dp
                compactWideTv -> 12.dp
                isTv -> 18.dp
                else -> 0.dp
            }
            val rowWidth = (panelWidth + brandColumnWidth + sideGap * 2 + 1.dp)
                .coerceAtMost(innerWidth)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .width(rowWidth)
                        .then(
                            if (compactMobileLandscape) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.heightIn(max = if (compactTvHeight) 560.dp else 680.dp)
                            },
                        )
                        .then(if (isTv) Modifier.padding(start = tvRightBias) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isTv) {
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
                            errorMessage = errorMessage,
                            onSubmit = submit,
                            onOpenWebsite = openWebsite,
                            onNonTextFocus = hideKeyboard,
                            initialFocusRequester = tvInitialFocusRequester,
                            compact = compactTvHeight,
                            landscapePhone = false,
                            modifier = Modifier.width(panelWidth),
                        )

                        Spacer(Modifier.width(sideGap))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(
                                    when {
                                        compactTvHeight -> 290.dp
                                        compactWideTv -> 300.dp
                                        else -> 320.dp
                                    },
                                )
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.gold.copy(alpha = .22f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Spacer(Modifier.width(sideGap))

                        LoginBrand(
                            isTv = true,
                            compact = compactTvHeight,
                            modifier = Modifier
                                .width(brandColumnWidth)
                                .fillMaxHeight(),
                        )
                    } else {
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
                            errorMessage = errorMessage,
                            onSubmit = submit,
                            onOpenWebsite = openWebsite,
                            onNonTextFocus = hideKeyboard,
                            initialFocusRequester = null,
                            compact = true,
                            landscapePhone = true,
                            modifier = Modifier
                                .width(panelWidth)
                                .offset(x = (-10).dp),
                        )

                        Spacer(Modifier.width(sideGap))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(210.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.gold.copy(alpha = .30f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Spacer(Modifier.width(sideGap))

                        LoginBrand(
                            isTv = false,
                            compact = true,
                            landscapePhone = true,
                            modifier = Modifier
                                .width(brandColumnWidth)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isTv) 24.dp else 20.dp,
                        vertical = when {
                            isTv -> 18.dp
                            compactHeight -> 8.dp
                            else -> 16.dp
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginBrand(
                    isTv = false,
                    compact = compactHeight,
                    modifier = Modifier.height(
                        when {
                            isTv -> 176.dp
                            compactHeight -> 104.dp
                            else -> 166.dp
                        },
                    ),
                )
                Spacer(
                    Modifier.height(
                        when {
                            isTv -> 10.dp
                            compactHeight -> 6.dp
                            else -> 20.dp
                        },
                    ),
                )
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
                    errorMessage = errorMessage,
                    onSubmit = submit,
                    onOpenWebsite = openWebsite,
                    onNonTextFocus = hideKeyboard,
                    initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                    compact = compactHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 474.dp),
                )
            }
        }
    }
}

@Composable
private fun LoginBrand(
    isTv: Boolean,
    compact: Boolean = false,
    landscapePhone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val haloSize = when {
        isTv && compact -> 320.dp
        isTv -> 390.dp
        landscapePhone -> 232.dp
        compact -> 104.dp
        else -> 166.dp
    }
    val logoSize = when {
        isTv && compact -> 250.dp
        isTv -> 306.dp
        landscapePhone -> 196.dp
        compact -> 84.dp
        else -> 136.dp
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(haloSize)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.gold.copy(alpha = .16f),
                            colors.goldDeep.copy(alpha = .045f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val logoShape = RoundedCornerShape(
                when {
                    isTv && compact -> 26.dp
                    isTv -> 32.dp
                    landscapePhone -> 24.dp
                    compact -> 16.dp
                    else -> 22.dp
                },
            )
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .clip(logoShape)
                    .background(Color.Black)
                    .border(1.dp, colors.gold.copy(alpha = .20f), logoShape)
                    .padding(
                        when {
                            isTv && compact -> 8.dp
                            isTv -> 10.dp
                            landscapePhone -> 6.dp
                            compact -> 4.dp
                            else -> 6.dp
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BrandLogo(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
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
    errorMessage: String?,
    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    onNonTextFocus: () -> Unit,
    initialFocusRequester: FocusRequester?,
    compact: Boolean = false,
    landscapePhone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(
        when {
            landscapePhone -> 20.dp
            compact -> 18.dp
            else -> 26.dp
        },
    )
    val panelHorizontalPadding = when {
        landscapePhone -> 12.dp
        compact -> 14.dp
        else -> 28.dp
    }
    val panelVerticalPadding = when {
        landscapePhone -> 2.dp
        compact -> 6.dp
        else -> 22.dp
    }
    val compactFieldHeight = if (landscapePhone) 36.dp else 38.dp
    val compactButtonHeight = if (landscapePhone) 36.dp else 36.dp
    val panelScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .widthIn(max = 474.dp)
            .then(if (landscapePhone) Modifier.verticalScroll(panelScrollState) else Modifier)
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF5141611),
                        Color(0xF20A0B08),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = .075f), panelShape)
            .padding(horizontal = panelHorizontalPadding, vertical = panelVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "اهلا بك",
            color = colors.text,
            fontSize = if (compact) if (landscapePhone) 19.sp else 18.sp else 31.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compact) 1.dp else 4.dp))
        Text(
            text = "ادخل بيانات الاشتراك الخاص بك",
            color = colors.textMuted,
            fontSize = if (compact) if (landscapePhone) 9.sp else 9.sp else 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compact) 2.dp else 7.dp))
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.gold),
        )
        Spacer(Modifier.height(if (compact) 3.dp else 18.dp))

        HulkTextField(
            value = accessCode,
            onValueChange = onAccessCodeChange,
            label = "كود الدخول",
            modifier = Modifier
                .then(
                    if (initialFocusRequester != null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = if (compact) compactFieldHeight else 55.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(if (compact) 3.dp else 10.dp))
        HulkTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "اسم المستخدم",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) compactFieldHeight else 55.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(if (compact) 3.dp else 10.dp))
        HulkTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "كلمة المرور",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) compactFieldHeight else 55.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(if (compact) 1.dp else 8.dp))

        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
            compact = compact,
        )
        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
            compact = compact,
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(9.dp))
            ErrorNotice(errorMessage)
        }

        Spacer(Modifier.height(if (compact) 3.dp else 13.dp))
        FocusButton(
            text = if (isLoading) "جاري الدخول..." else "دخول الى HULK",
            onClick = onSubmit,
            enabled = !isLoading,
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) compactButtonHeight else 52.dp),
            onFocused = onNonTextFocus,
        )
        Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
        FocusButton(
            text = "اشترك او جدد",
            onClick = onOpenWebsite,
            compact = compact,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) compactButtonHeight else 52.dp),
            primary = false,
            onFocused = onNonTextFocus,
            outlined = true,
        )
        Spacer(Modifier.height(if (compact) 1.dp else 4.dp))
        Text(
            text = "hulksa.com",
            color = colors.goldBright.copy(alpha = .86f),
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = if (compact) 28.dp else 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onOpenWebsite)
                .padding(horizontal = 14.dp, vertical = if (compact) 4.dp else 12.dp),
        )
    }
}

@Composable
private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    compact: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = if (focused) colors.gold.copy(alpha = .09f) else Color.Transparent,
        label = "loginOptionBackground",
    )
    val outline by animateColorAsState(
        targetValue = if (focused) colors.gold.copy(alpha = .72f) else Color.Transparent,
        label = "loginOptionOutline",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 30.dp else 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(if (focused) 1.dp else 0.dp, outline, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 18.dp else 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.gold else Color(0xFF1B1C17))
                .border(
                    1.dp,
                    if (checked) colors.goldBright.copy(alpha = .75f) else Color.White.copy(alpha = .18f),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
        Text(
            text = text,
            color = if (focused) colors.text else colors.textMuted,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
        )
    }
}
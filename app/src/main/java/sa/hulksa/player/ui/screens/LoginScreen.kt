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
    onLogin: (String, String, Boolean) -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val tvInitialFocusRequester = remember { FocusRequester() }
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
        onLogin(username.trim(), password, rememberAccount)
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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { dismissKeyboard() })
            }
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
                compactMobileLandscape -> 12.dp
                isTv && maxWidth >= 1440.dp -> 58.dp
                compactWideTv -> 20.dp
                isTv -> 30.dp
                else -> 36.dp
            }
            val verticalPadding = when {
                compactMobileLandscape -> 6.dp
                compactTvHeight -> 12.dp
                compactWideTv -> 8.dp
                isTv -> 24.dp
                else -> 20.dp
            }
            val itemSpacing = when {
                compactMobileLandscape -> 12.dp
                compactTvHeight -> 18.dp
                compactWideTv -> 22.dp
                isTv -> 30.dp
                else -> 30.dp
            }
            val innerWidth = availableWidth - horizontalPadding * 2
            val panelWidth = when {
                compactMobileLandscape -> (innerWidth * .58f).coerceAtMost(430.dp)
                compactTvHeight -> 382.dp
                compactWideTv -> 404.dp
                isTv -> 424.dp
                else -> 430.dp
            }
            val brandColumnWidth = when {
                compactMobileLandscape -> (innerWidth - panelWidth - itemSpacing * 2 - 1.dp)
                    .coerceAtLeast(180.dp)
                compactTvHeight -> 382.dp
                compactWideTv -> 404.dp
                isTv -> 424.dp
                else -> 430.dp
            }
            val rowWidth = (panelWidth + brandColumnWidth + itemSpacing * 2 + 1.dp)
                .coerceAtMost(innerWidth)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding,
                    ),
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
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                ) {
                    if (isTv) {
                        LoginPanel(
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
                            compact = false,
                            compactTv = compactTvHeight,
                            landscapePhone = false,
                            modifier = Modifier.width(panelWidth),
                        )

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

                        LoginBrand(
                            isTv = true,
                            compact = compactTvHeight,
                            modifier = Modifier
                                .width(brandColumnWidth)
                                .fillMaxHeight(),
                        )
                    } else {
                        LoginBrand(
                            isTv = false,
                            compact = compactMobileLandscape,
                            landscapePhone = compactMobileLandscape,
                            modifier = Modifier
                                .width(brandColumnWidth)
                                .fillMaxHeight(),
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(if (compactMobileLandscape) 250.dp else 330.dp)
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

                        LoginPanel(
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
                            compact = compactMobileLandscape,
                            compactTv = false,
                            landscapePhone = compactMobileLandscape,
                            modifier = Modifier
                                .width(panelWidth)
                                .then(
                                    if (compactMobileLandscape) Modifier.fillMaxHeight() else Modifier,
                                ),
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
                            compactHeight -> 12.dp
                            else -> 24.dp
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
                            compactHeight -> 112.dp
                            else -> 200.dp
                        },
                    ),
                )
                Spacer(
                    Modifier.height(
                        when {
                            isTv -> 10.dp
                            compactHeight -> 8.dp
                            else -> 16.dp
                        },
                    ),
                )
                LoginPanel(
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
        landscapePhone -> 164.dp
        compact -> 112.dp
        else -> 196.dp
    }
    val logoSize = when {
        isTv && compact -> 250.dp
        isTv -> 306.dp
        landscapePhone -> 136.dp
        compact -> 92.dp
        else -> 160.dp
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
                    landscapePhone -> 20.dp
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
                            landscapePhone -> 5.dp
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
    compactTv: Boolean = false,
    landscapePhone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(
        when {
            landscapePhone -> 20.dp
            compactTv -> 22.dp
            compact -> 18.dp
            else -> 26.dp
        },
    )
    val panelHorizontalPadding = when {
        landscapePhone -> 18.dp
        compactTv -> 22.dp
        compact -> 14.dp
        else -> 28.dp
    }
    val panelVerticalPadding = when {
        landscapePhone -> 8.dp
        compactTv -> 12.dp
        compact -> 6.dp
        else -> 22.dp
    }
    val compactFieldHeight = if (landscapePhone) 42.dp else 38.dp
    val compactButtonHeight = if (landscapePhone) 42.dp else 36.dp
    val fieldHeight = if (compactTv) 46.dp else if (compact) compactFieldHeight else 55.dp
    val buttonHeight = if (compactTv) 44.dp else if (compact) compactButtonHeight else 52.dp
    val panelScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .widthIn(max = 474.dp)
            .then(if (landscapePhone) Modifier.verticalScroll(panelScrollState) else Modifier)
            .clip(panelShape)
            .background(colors.surface)
            .border(1.dp, colors.gold.copy(alpha = .18f), panelShape)
            .padding(horizontal = panelHorizontalPadding, vertical = panelVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "اهلا بك",
            color = colors.textPrimary,
            fontSize = when {
                landscapePhone -> 24.sp
                compactTv -> 26.sp
                compact -> 20.sp
                else -> 30.sp
            },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact || compactTv) 4.dp else 8.dp))
        Text(
            text = "ادخل بيانات الاشتراك الخاص بك",
            color = colors.textSecondary,
            fontSize = when {
                landscapePhone -> 12.sp
                compactTv -> 13.sp
                compact -> 11.sp
                else -> 14.sp
            },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact || compactTv) 6.dp else 10.dp))
        Box(
            Modifier
                .width(if (compact || compactTv) 36.dp else 46.dp)
                .height(3.dp)
                .background(colors.gold, RoundedCornerShape(50)),
        )
        Spacer(Modifier.height(if (compact || compactTv) 8.dp else 14.dp))
        HulkTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "اسم المستخدم",
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .then(
                    if (initialFocusRequester != null) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.height(if (compact || compactTv) 6.dp else 10.dp))
        HulkTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "كلمة المرور",
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight),
        )
        Spacer(Modifier.height(if (compact || compactTv) 2.dp else 4.dp))
        LoginOption(
            label = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocus = onNonTextFocus,
            compact = compact || compactTv,
        )
        LoginOption(
            label = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocus = onNonTextFocus,
            compact = compact || compactTv,
        )
        Spacer(Modifier.height(if (compact || compactTv) 4.dp else 8.dp))
        FocusButton(
            text = if (isLoading) "جاري الدخول..." else "دخول الى HULK",
            onClick = onSubmit,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
        )
        Spacer(Modifier.height(if (compact || compactTv) 6.dp else 10.dp))
        FocusButton(
            text = "اشتراك او تجديد",
            onClick = onOpenWebsite,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            filled = false,
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(if (compact || compactTv) 6.dp else 10.dp))
            ErrorNotice(errorMessage)
        }
        Spacer(Modifier.height(if (compact || compactTv) 6.dp else 10.dp))
        Text(
            text = "hulksa.com",
            color = colors.textSecondary,
            fontSize = if (compact || compactTv) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClick = onOpenWebsite,
                )
                .onFocusChanged { if (it.isFocused) onNonTextFocus() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LoginOption(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    compact: Boolean,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.gold else colors.border,
        label = "login-option-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 40.dp else 48.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 18.dp else 20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.gold else Color.Transparent)
                .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = Color.Black,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = if (compact) 12.sp else 13.sp,
            maxLines = 2,
        )
    }
}

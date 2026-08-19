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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.focus.focusProperties
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
    val openWebsite = {
        runCatching { uriHandler.openUri(HULK_WEBSITE) }
        Unit
    }

    LaunchedEffect(isLoading, isStarting) {
        if (isLoading || isStarting) {
            hideKeyboard()
            focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(isTv, isStarting, isLoading) {
        if (isTv && !isStarting && !isLoading) {
            withFrameNanos { }
            hideKeyboard()
            runCatching { tvInitialFocusRequester.requestFocus() }
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
                        Color(0xFF090A07).copy(alpha = .46f),
                        Color.Transparent,
                    ),
                ),
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { dismissKeyboard() }) }
            .imePadding(),
    ) {
        if (isStarting) {
            LoadingRing(
                label = "جاري التجهيز...",
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val phoneLandscape = !isTv && maxWidth > maxHeight
        val compactPhoneHeight = !isTv && maxHeight < 650.dp
        val compactWide = if (isTv) maxHeight < 620.dp else maxHeight < 520.dp
        val useTwoPane = isTv || (phoneLandscape && maxWidth >= 640.dp)

        if (useTwoPane) {
            val horizontalPadding = when {
                isTv && maxWidth >= 1440.dp -> 48.dp
                isTv && maxWidth >= 1100.dp -> 30.dp
                isTv -> 18.dp
                else -> 10.dp
            }
            val verticalPadding = when {
                isTv && compactWide -> 8.dp
                isTv -> 18.dp
                else -> 4.dp
            }
            val centerGap = when {
                isTv && maxWidth >= 1440.dp -> 38.dp
                isTv -> 28.dp
                else -> 14.dp
            }
            val panelMaxWidth = when {
                isTv && maxWidth >= 1440.dp -> 462.dp
                isTv && maxWidth >= 1100.dp -> 442.dp
                isTv -> 410.dp
                fontScale >= 1.3f -> 430.dp
                else -> 392.dp
            }
            val dividerHeight = when {
                isTv && compactWide -> 280.dp
                isTv -> 320.dp
                else -> 210.dp
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
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
                        compact = compactWide || phoneLandscape,
                        landscapePhone = phoneLandscape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = panelMaxWidth),
                    )
                }

                Spacer(Modifier.width(centerGap))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(dividerHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    colors.gold.copy(alpha = .34f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Spacer(Modifier.width(centerGap))

                LoginBrand(
                    isTv = isTv,
                    compact = compactWide,
                    landscapePhone = phoneLandscape,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (maxWidth >= 600.dp) 24.dp else 16.dp,
                        vertical = if (compactPhoneHeight) 8.dp else 14.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginBrand(
                    isTv = false,
                    compact = compactPhoneHeight,
                    modifier = Modifier.height(if (compactPhoneHeight) 110.dp else 158.dp),
                )
                Spacer(Modifier.height(if (compactPhoneHeight) 5.dp else 10.dp))
                Box(
                    modifier = Modifier
                        .width(if (compactPhoneHeight) 54.dp else 72.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    colors.gold.copy(alpha = .58f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Spacer(Modifier.height(if (compactPhoneHeight) 7.dp else 14.dp))
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
                    compact = compactPhoneHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                )
                Spacer(Modifier.height(8.dp))
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
        isTv && compact -> 300.dp
        isTv -> 380.dp
        landscapePhone -> 190.dp
        compact -> 100.dp
        else -> 154.dp
    }
    val logoSize = when {
        isTv && compact -> 232.dp
        isTv -> 296.dp
        landscapePhone -> 158.dp
        compact -> 82.dp
        else -> 126.dp
    }
    val logoShape = RoundedCornerShape(
        when {
            isTv && compact -> 24.dp
            isTv -> 30.dp
            landscapePhone -> 20.dp
            compact -> 15.dp
            else -> 20.dp
        },
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .clip(logoShape)
                    .background(Color.Black)
                    .border(1.dp, colors.gold.copy(alpha = .20f), logoShape)
                    .padding(
                        when {
                            isTv -> 9.dp
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
    val localAccessRequester = remember { FocusRequester() }
    val accessRequester = initialFocusRequester ?: localAccessRequester
    val usernameRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val showPasswordRequester = remember { FocusRequester() }
    val rememberRequester = remember { FocusRequester() }
    val submitRequester = remember { FocusRequester() }
    val subscribeRequester = remember { FocusRequester() }
    val websiteRequester = remember { FocusRequester() }
    val tvFocusGraph = initialFocusRequester != null
    val panelScrollState = rememberScrollState()
    var websiteFocused by remember { mutableStateOf(false) }

    val panelShape = RoundedCornerShape(
        when {
            landscapePhone -> 18.dp
            compact -> 20.dp
            else -> 26.dp
        },
    )
    val panelHorizontalPadding = when {
        landscapePhone -> 12.dp
        compact -> 16.dp
        else -> 26.dp
    }
    val panelVerticalPadding = when {
        landscapePhone -> 6.dp
        compact -> 12.dp
        else -> 22.dp
    }
    val fieldHeight = if (compact) 44.dp else 52.dp
    val actionHeight = if (compact) 42.dp else 50.dp

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
            .border(1.dp, Color.White.copy(alpha = .08f), panelShape)
            .padding(horizontal = panelHorizontalPadding, vertical = panelVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "اهلا بك",
            color = colors.text,
            fontSize = if (compact) 20.sp else 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compact) 1.dp else 4.dp))
        Text(
            text = "ادخل بيانات الاشتراك الخاص بك",
            color = colors.textMuted,
            fontSize = if (compact) 10.sp else 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(if (compact) 5.dp else 9.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.gold),
        )
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        HulkTextField(
            value = accessCode,
            onValueChange = onAccessCodeChange,
            label = "كود الدخول",
            modifier = Modifier
                .focusRequester(accessRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties { down = usernameRequester }
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = fieldHeight),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { usernameRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

        HulkTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "اسم المستخدم",
            modifier = Modifier
                .focusRequester(usernameRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = accessRequester
                            down = passwordRequester
                        }
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = fieldHeight),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { runCatching { passwordRequester.requestFocus() } },
            ),
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

        HulkTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "كلمة المرور",
            modifier = Modifier
                .focusRequester(passwordRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = usernameRequester
                            down = showPasswordRequester
                        }
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = fieldHeight),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(if (compact) 3.dp else 7.dp))

        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
            compact = compact,
            modifier = Modifier
                .focusRequester(showPasswordRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = passwordRequester
                            down = rememberRequester
                        }
                    } else {
                        Modifier
                    },
                ),
        )
        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
            compact = compact,
            modifier = Modifier
                .focusRequester(rememberRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = showPasswordRequester
                            down = submitRequester
                        }
                    } else {
                        Modifier
                    },
                ),
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            ErrorNotice(errorMessage)
        }

        Spacer(Modifier.height(if (compact) 7.dp else 12.dp))
        FocusButton(
            text = if (isLoading) "جاري الدخول..." else "دخول الى HULK",
            onClick = onSubmit,
            enabled = !isLoading,
            compact = compact,
            modifier = Modifier
                .focusRequester(submitRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = rememberRequester
                            down = subscribeRequester
                        }
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = actionHeight),
            onFocused = onNonTextFocus,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        FocusButton(
            text = "اشترك او جدد",
            onClick = onOpenWebsite,
            compact = compact,
            modifier = Modifier
                .focusRequester(subscribeRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties {
                            up = submitRequester
                            down = websiteRequester
                        }
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .heightIn(min = actionHeight),
            primary = false,
            onFocused = onNonTextFocus,
            outlined = true,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

        Text(
            text = "hulksa.com",
            color = if (websiteFocused) colors.goldBright else colors.goldBright.copy(alpha = .86f),
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .focusRequester(websiteRequester)
                .then(
                    if (tvFocusGraph) {
                        Modifier.focusProperties { up = subscribeRequester }
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged {
                    websiteFocused = it.isFocused
                    if (it.isFocused) onNonTextFocus()
                }
                .heightIn(min = if (compact) 30.dp else 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (websiteFocused) colors.gold.copy(alpha = .08f) else Color.Transparent)
                .border(
                    if (websiteFocused) 1.dp else 0.dp,
                    if (websiteFocused) colors.gold.copy(alpha = .55f) else Color.Transparent,
                    RoundedCornerShape(10.dp),
                )
                .clickable(role = Role.Button, onClick = onOpenWebsite)
                .padding(horizontal = 14.dp, vertical = if (compact) 5.dp else 9.dp),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 32.dp else 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(if (focused) 1.dp else 0.dp, outline, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 6.dp),
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

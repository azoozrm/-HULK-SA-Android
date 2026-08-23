package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.ErrorNotice
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
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF020302),
                        Color(0xFF090A07),
                        Color(0xFF020302),
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.goldDeep.copy(alpha = .12f),
                        Color(0xFF0A0B08).copy(alpha = .30f),
                        Color.Transparent,
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) { detectTapGestures(onTap = { dismissKeyboard() }) },
    ) {
        if (isStarting) {
            LoadingRing(
                label = "جاري التجهيز...",
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        val largeFont = fontScale >= 1.30f
        val useSplitLayout = isTv || (!largeFont && maxWidth >= 840.dp && maxHeight >= 520.dp)
        val compactHeight = when {
            isTv -> maxHeight < 690.dp
            useSplitLayout -> maxHeight < 680.dp
            else -> maxHeight < 860.dp
        }
        val compactWidth = maxWidth < 390.dp
        val contentHorizontalPadding = when {
            isTv && maxWidth >= 1800.dp -> 72.dp
            isTv && maxWidth >= 1200.dp -> 44.dp
            isTv -> 30.dp
            maxWidth >= 600.dp -> 28.dp
            else -> 16.dp
        }
        val contentVerticalPadding = when {
            isTv && compactHeight -> 20.dp
            isTv -> 34.dp
            compactHeight -> 12.dp
            else -> 20.dp
        }
        val contentModifier = Modifier
            .fillMaxSize()
            .then(if (isTv) Modifier else Modifier.imePadding())

        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.Center,
        ) {
            if (useSplitLayout) {
                val groupMaxWidth = if (isTv) 1220.dp else 1040.dp
                val groupMaxHeight = if (isTv) 820.dp else 720.dp
                val centerGap = when {
                    isTv && maxWidth >= 1600.dp -> 56.dp
                    isTv -> 40.dp
                    else -> 28.dp
                }
                val cardMaxWidth = when {
                    isTv && maxWidth >= 1600.dp -> 580.dp
                    isTv && maxWidth >= 1200.dp -> 550.dp
                    isTv -> 500.dp
                    else -> 470.dp
                }
                val availableGroupWidth = minOf(
                    maxWidth - (contentHorizontalPadding * 2),
                    groupMaxWidth,
                )
                val cardWidth = minOf(cardMaxWidth, availableGroupWidth * .58f)

                Row(
                    modifier = Modifier
                        .padding(
                            horizontal = contentHorizontalPadding,
                            vertical = contentVerticalPadding,
                        )
                        .widthIn(max = groupMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = groupMaxHeight)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
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
                            isTv = isTv,
                            errorMessage = errorMessage,
                            onSubmit = submit,
                            onOpenWebsite = openWebsite,
                            onNonTextFocus = hideKeyboard,
                            initialFocusRequester = if (isTv) tvInitialFocusRequester else null,
                            compact = compactHeight,
                            scrollInside = !isTv || errorMessage != null,
                            modifier = Modifier
                                .fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.width(centerGap))

                    LoginBrand(
                        isTv = isTv,
                        compact = compactHeight,
                        landscape = !isTv,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = contentHorizontalPadding,
                            vertical = contentVerticalPadding,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoginBrand(
                        isTv = false,
                        compact = compactHeight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (compactHeight) 12.dp else 20.dp))
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
                        isTv = false,
                        errorMessage = errorMessage,
                        onSubmit = submit,
                        onOpenWebsite = openWebsite,
                        onNonTextFocus = hideKeyboard,
                        initialFocusRequester = null,
                        compact = compactHeight || compactWidth,
                        scrollInside = false,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LoginBrand(
    isTv: Boolean,
    compact: Boolean = false,
    landscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val haloSize = when {
        isTv && compact -> 264.dp
        isTv -> 350.dp
        landscape -> 224.dp
        compact -> 104.dp
        else -> 142.dp
    }
    val logoSize = when {
        isTv && compact -> 190.dp
        isTv -> 252.dp
        landscape -> 164.dp
        compact -> 78.dp
        else -> 106.dp
    }
    val brandNameSize = when {
        isTv && compact -> 30.sp
        isTv -> 38.sp
        landscape -> 28.sp
        compact -> 21.sp
        else -> 25.sp
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(haloSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.gold.copy(alpha = .15f),
                                colors.goldDeep.copy(alpha = .045f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            BrandLogo(
                modifier = Modifier.size(logoSize),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(if (compact) 2.dp else 8.dp))
        Text(
            text = "HULK SA",
            color = colors.text,
            fontSize = brandNameSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Text(
            text = "مشاهدة بلا حدود",
            color = colors.textMuted,
            fontSize = when {
                isTv && compact -> 14.sp
                isTv -> 18.sp
                landscape -> 14.sp
                compact -> 11.sp
                else -> 13.sp
            },
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        Box(
            modifier = Modifier
                .width(if (isTv) 68.dp else 52.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            colors.gold.copy(alpha = .88f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
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
    compact: Boolean,
    scrollInside: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val localAccessRequester = remember { FocusRequester() }
    val accessRequester = initialFocusRequester ?: localAccessRequester
    val usernameRequester = remember { FocusRequester() }
    val passwordRequester = remember { FocusRequester() }
    val rememberRequester = remember { FocusRequester() }
    val showPasswordRequester = remember { FocusRequester() }
    val submitRequester = remember { FocusRequester() }
    val subscribeRequester = remember { FocusRequester() }
    val tvFocusGraph = initialFocusRequester != null
    val panelScrollState = rememberScrollState()

    val panelShape = RoundedCornerShape(if (compact) 22.dp else 28.dp)
    val panelHorizontalPadding = if (compact) 22.dp else 30.dp
    val panelVerticalPadding = if (compact) 20.dp else 28.dp
    val fieldHeight = when {
        isTv && compact -> 50.dp
        isTv -> 58.dp
        compact -> 52.dp
        else -> 56.dp
    }
    val actionHeight = when {
        isTv && compact -> 48.dp
        isTv -> 56.dp
        compact -> 50.dp
        else -> 54.dp
    }
    val fieldSpacing = if (compact) 8.dp else 12.dp

    BoxWithConstraints(modifier = modifier) {
        val optionsHorizontal = isTv || maxWidth >= 380.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF0141611),
                            Color(0xF5090A08),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = .09f), panelShape)
                .then(if (scrollInside) Modifier.verticalScroll(panelScrollState) else Modifier)
                .padding(
                    horizontal = panelHorizontalPadding,
                    vertical = panelVerticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "أهلا بك",
                color = colors.text,
                fontSize = when {
                    isTv && compact -> 25.sp
                    isTv -> 31.sp
                    compact -> 22.sp
                    else -> 27.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compact) 2.dp else 5.dp))
            Text(
                text = "ادخل بيانات اشتراكك للمتابعة",
                color = colors.textMuted,
                fontSize = when {
                    isTv && compact -> 13.sp
                    isTv -> 15.sp
                    compact -> 12.sp
                    else -> 14.sp
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compact) 9.dp else 13.dp))
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.gold),
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                ErrorNotice(
                    message = errorMessage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(if (compact) 13.dp else 20.dp))
            PremiumLoginField(
                value = accessCode,
                onValueChange = onAccessCodeChange,
                label = "كود الدخول",
                leadingIcon = Icons.Rounded.VpnKey,
                compact = compact,
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
                    .height(fieldHeight),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { runCatching { usernameRequester.requestFocus() } },
                ),
            )
            Spacer(Modifier.height(fieldSpacing))

            PremiumLoginField(
                value = username,
                onValueChange = onUsernameChange,
                label = "اسم المستخدم",
                leadingIcon = Icons.Rounded.Person,
                compact = compact,
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
                    .height(fieldHeight),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { runCatching { passwordRequester.requestFocus() } },
                ),
            )
            Spacer(Modifier.height(fieldSpacing))

            PremiumLoginField(
                value = password,
                onValueChange = onPasswordChange,
                label = "كلمة المرور",
                leadingIcon = Icons.Rounded.Lock,
                compact = compact,
                modifier = Modifier
                    .focusRequester(passwordRequester)
                    .then(
                        if (tvFocusGraph) {
                            Modifier.focusProperties {
                                up = usernameRequester
                                down = rememberRequester
                            }
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .height(fieldHeight),
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
            )

            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            if (optionsHorizontal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoginOption(
                        text = "تذكر الحساب",
                        checked = rememberAccount,
                        onClick = onRememberChange,
                        onFocused = onNonTextFocus,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(rememberRequester)
                            .then(
                                if (tvFocusGraph) {
                                    Modifier.focusProperties {
                                        up = passwordRequester
                                        down = submitRequester
                                        left = showPasswordRequester
                                        right = showPasswordRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    LoginOption(
                        text = "إظهار كلمة المرور",
                        checked = showPassword,
                        onClick = onShowPasswordChange,
                        onFocused = onNonTextFocus,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(showPasswordRequester)
                            .then(
                                if (tvFocusGraph) {
                                    Modifier.focusProperties {
                                        up = passwordRequester
                                        down = submitRequester
                                        left = rememberRequester
                                        right = rememberRequester
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            } else {
                LoginOption(
                    text = "تذكر الحساب",
                    checked = rememberAccount,
                    onClick = onRememberChange,
                    onFocused = onNonTextFocus,
                    compact = compact,
                    modifier = Modifier.focusRequester(rememberRequester),
                )
                Spacer(Modifier.height(4.dp))
                LoginOption(
                    text = "إظهار كلمة المرور",
                    checked = showPassword,
                    onClick = onShowPasswordChange,
                    onFocused = onNonTextFocus,
                    compact = compact,
                    modifier = Modifier.focusRequester(showPasswordRequester),
                )
            }

            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            PremiumLoginButton(
                text = if (isLoading) "جاري الدخول..." else "دخول إلى HULK",
                onClick = onSubmit,
                enabled = !isLoading,
                loading = isLoading,
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
                    .height(actionHeight),
                onFocused = onNonTextFocus,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            PremiumLoginButton(
                text = "اشتراك أو تجديد",
                onClick = onOpenWebsite,
                primary = false,
                compact = compact,
                modifier = Modifier
                    .focusRequester(subscribeRequester)
                    .then(
                        if (tvFocusGraph) {
                            Modifier.focusProperties { up = submitRequester }
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .height(actionHeight),
                onFocused = onNonTextFocus,
            )
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Text(
                text = "hulksa.com",
                color = colors.goldBright.copy(alpha = .78f),
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PremiumLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    compact: Boolean,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val background by animateColorAsState(
        targetValue = if (focused) Color(0xFF181911) else Color(0xFF10110D),
        label = "premiumLoginFieldBackground",
    )
    val outline by animateColorAsState(
        targetValue = if (focused) colors.goldBright else Color.White.copy(alpha = .10f),
        label = "premiumLoginFieldOutline",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, outline, shape),
        singleLine = true,
        textStyle = TextStyle(
            color = colors.text,
            fontSize = if (compact) 14.sp else 16.sp,
            textAlign = TextAlign.Start,
            textDirection = TextDirection.Ltr,
        ),
        cursorBrush = Brush.verticalGradient(listOf(colors.gold, colors.gold)),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 14.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (focused) colors.goldBright else colors.textMuted,
                    modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                )
                Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = colors.textMuted,
                            fontSize = if (compact) 13.sp else 15.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        innerField()
                    }
                }
            }
        },
    )
}

@Composable
private fun PremiumLoginButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val background by animateColorAsState(
        targetValue = when {
            !enabled && primary -> colors.gold.copy(alpha = .62f)
            !enabled -> Color(0xFF14150F)
            primary && focused -> colors.goldBright
            primary -> colors.gold
            focused -> colors.gold.copy(alpha = .16f)
            else -> Color(0xFF11120E)
        },
        label = "premiumLoginButtonBackground",
    )
    val outline by animateColorAsState(
        targetValue = when {
            focused -> colors.goldBright
            primary -> colors.gold.copy(alpha = .65f)
            else -> colors.gold.copy(alpha = .42f)
        },
        label = "premiumLoginButtonOutline",
    )
    val textColor = if (primary) Color(0xFF100E04) else if (focused) colors.goldBright else colors.text

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                    color = textColor,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val background by animateColorAsState(
        targetValue = if (focused) colors.gold.copy(alpha = .10f) else Color.Transparent,
        label = "loginOptionBackground",
    )
    val outline by animateColorAsState(
        targetValue = if (focused) colors.goldBright else Color.Transparent,
        label = "loginOptionOutline",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 42.dp else 46.dp)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 0.dp, outline, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val checkShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .size(if (compact) 19.dp else 21.dp)
                .clip(checkShape)
                .background(if (checked) colors.gold else Color(0xFF171812))
                .border(
                    1.dp,
                    if (checked) colors.goldBright.copy(alpha = .92f) else Color.White.copy(alpha = .22f),
                    checkShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
            }
        }
        Spacer(Modifier.width(if (compact) 7.dp else 9.dp))
        Text(
            text = text,
            color = if (focused) colors.text else colors.textMuted,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

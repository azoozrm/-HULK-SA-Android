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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var rememberAccount by remember { mutableStateOf(true) }
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

        val wideThreshold = if (isTv) 1180.dp else 760.dp
        val wide = maxWidth >= wideThreshold
        if (wide) {
            val horizontalPadding = when {
                isTv && maxWidth >= 1440.dp -> 58.dp
                isTv -> 30.dp
                else -> 36.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = if (isTv) 24.dp else 20.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1110.dp)
                        .fillMaxWidth()
                        .heightIn(max = 680.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 36.dp else 30.dp),
                ) {
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
                        modifier = Modifier.width(if (isTv) 450.dp else 430.dp),
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(330.dp)
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
                        isTv = isTv,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isTv) 24.dp else 20.dp,
                        vertical = if (isTv) 18.dp else 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoginBrand(
                    isTv = false,
                    modifier = Modifier.height(if (isTv) 176.dp else 200.dp),
                )
                Spacer(Modifier.height(if (isTv) 10.dp else 16.dp))
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 390.dp else 196.dp)
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
            val logoShape = RoundedCornerShape(if (isTv) 32.dp else 22.dp)
            Box(
                modifier = Modifier
                    .size(if (isTv) 306.dp else 160.dp)
                    .clip(logoShape)
                    .background(Color.Black)
                    .border(1.dp, colors.gold.copy(alpha = .20f), logoShape)
                    .padding(if (isTv) 10.dp else 6.dp),
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .widthIn(max = 474.dp)
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
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "اهلا بك",
            color = colors.text,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ادخل بيانات الاشتراك الخاص بك",
            color = colors.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.gold),
        )
        Spacer(Modifier.height(18.dp))

        HulkTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "اسم المستخدم",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(10.dp))
        HulkTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "كلمة المرور",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 55.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(8.dp))

        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
        )
        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(9.dp))
            ErrorNotice(errorMessage)
        }

        Spacer(Modifier.height(13.dp))
        FocusButton(
            text = if (isLoading) "جاري الدخول..." else "دخول الى HULK",
            onClick = onSubmit,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            onFocused = onNonTextFocus,
        )
        Spacer(Modifier.height(8.dp))
        FocusButton(
            text = "اشترك او جدد",
            onClick = onOpenWebsite,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            primary = false,
            onFocused = onNonTextFocus,
            outlined = true,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "hulksa.com",
            color = colors.goldBright.copy(alpha = .86f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onOpenWebsite)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(if (focused) 1.dp else 0.dp, outline, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
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
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = if (focused) colors.text else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
        )
    }
}

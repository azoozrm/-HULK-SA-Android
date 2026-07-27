#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

p='app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt'
rep(p,'package sa.hulksa.player.ui.screens\n\n','package sa.hulksa.player.ui.screens\n\nimport android.content.Context\nimport android.view.inputmethod.InputMethodManager\n','IME imports')
rep(p,'import androidx.compose.ui.platform.LocalUriHandler\n','import androidx.compose.ui.platform.LocalUriHandler\nimport androidx.compose.ui.platform.LocalView\n','LocalView import')
rep(p,'''    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
''','''    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
''','platform view')
rep(p,'''    val dismissKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
''','''    val hideKeyboard: () -> Unit = {
        keyboardController?.hide()
        val hidePlatformIme: () -> Unit = {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            Unit
        }
        view.post(hidePlatformIme)
        view.postDelayed(hidePlatformIme, 100L)
        view.postDelayed(hidePlatformIme, 260L)
        Unit
    }
    val dismissKeyboard: () -> Unit = {
        hideKeyboard()
        focusManager.clearFocus(force = true)
        Unit
    }
''','robust IME hide')
rep(p,'''                        onOpenWebsite = openWebsite,
                        modifier = Modifier.width(if (isTv) 474.dp else 430.dp),
''','''                        onOpenWebsite = openWebsite,
                        onNonTextFocus = hideKeyboard,
                        modifier = Modifier.width(if (isTv) 474.dp else 430.dp),
''','wide panel callback')
rep(p,'''                    onOpenWebsite = openWebsite,
                    modifier = Modifier.fillMaxWidth(),
''','''                    onOpenWebsite = openWebsite,
                    onNonTextFocus = hideKeyboard,
                    modifier = Modifier.fillMaxWidth(),
''','compact panel callback')
rep(p,'''    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
''','''    onSubmit: () -> Unit,
    onOpenWebsite: () -> Unit,
    onNonTextFocus: () -> Unit,
    modifier: Modifier = Modifier,
''','panel signature')
rep(p,'''    val colors = LocalHulkColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val panelShape = RoundedCornerShape(26.dp)
''','''    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(26.dp)
''','remove panel controller')
rep(p,'''        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
        )
''','''        LoginOption(
            text = "اظهار كلمة المرور",
            checked = showPassword,
            onClick = onShowPasswordChange,
            onFocused = onNonTextFocus,
        )
''','show password focus')
rep(p,'''        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
        )
''','''        LoginOption(
            text = "تذكر الحساب على هذا الجهاز",
            checked = rememberAccount,
            onClick = onRememberChange,
            onFocused = onNonTextFocus,
        )
''','remember focus')
rep(p,'''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
''','''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            onFocused = onNonTextFocus,
''','login button focus')
rep(p,'''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            primary = false,
''','''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            primary = false,
            onFocused = onNonTextFocus,
''','website button focus')
rep(p,'''private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
''','''private fun LoginOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
''','option signature')
rep(p,'            .onFocusChanged { focused = it.isFocused }\n','''            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
''','option focus handler')
print('Prepared v0.9.3.16 login fixes')

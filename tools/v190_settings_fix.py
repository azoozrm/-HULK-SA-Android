from pathlib import Path

p = Path('app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt')
s = p.read_text()

old_refresh = '''                        focusRequester = subscriptionRefreshRequester,
                        upRequester = FocusRequester.Cancel,
                        downRequester = playbackFirstRequester,
                        onClick = {
                            if (!state.isAccountRefreshing) onRefreshAccount()
                        },'''
new_refresh = '''                        focusRequester = subscriptionRefreshRequester,
                        upRequester = FocusRequester.Cancel,
                        downRequester = playbackFirstRequester,
                        onFocused = {
                            if (isTv) {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        onClick = {
                            if (!state.isAccountRefreshing) onRefreshAccount()
                        },'''
if old_refresh not in s:
    raise SystemExit('subscription refresh focus contract not found')
s = s.replace(old_refresh, new_refresh, 1)

old_signature = '''    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onClick: () -> Unit,'''
new_signature = '''    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,'''
if old_signature not in s:
    raise SystemExit('menu row focus signature not found')
s = s.replace(old_signature, new_signature, 1)

old_focus = '.onFocusChanged { focused = it.isFocused }'
new_focus = '''.onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (nowFocused && !focused) onFocused?.invoke()
                focused = nowFocused
            }'''
if old_focus not in s:
    raise SystemExit('menu row focus handler not found')
s = s.replace(old_focus, new_focus, 1)

required = [
    'SettingsPanel(title = "بيانات الاشتراك"',
    'enabled = account != null',
    'settingsConnectionUsage(account)',
    'SimpleDateFormat("yyyy/MM/dd", Locale.US)',
]
for text in required:
    if text not in s:
        raise SystemExit(f'required v1.9 setting missing: {text}')

p.write_text(s)

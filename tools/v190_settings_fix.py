from pathlib import Path

p = Path('app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt')
s = p.read_text()

old_refresh = '''                    SettingsMenuRow(
                        label = if (state.isAccountRefreshing) "جاري تحديث الاشتراك" else "تحديث بيانات الاشتراك",
                        value = if (state.isAccountRefreshing) "..." else "تحديث",
                        enabled = !state.isAccountRefreshing && account != null,
                        isTv = isTv,
                        onClick = onRefreshAccount,
                    )'''
new_refresh = '''                    SettingsMenuRow(
                        label = if (state.isAccountRefreshing) "جاري تحديث بيانات الاشتراك" else "تحديث بيانات الاشتراك",
                        value = if (state.isAccountRefreshing) "..." else "تحديث",
                        enabled = account != null,
                        isTv = isTv,
                        onFocused = {
                            if (isTv) {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        onClick = {
                            if (!state.isAccountRefreshing) onRefreshAccount()
                        },
                    )'''
if old_refresh not in s:
    raise SystemExit('refresh row contract not found')
s = s.replace(old_refresh, new_refresh, 1)

old_signature = '''private fun SettingsMenuRow(
    label: String,
    value: String,
    accentValue: Boolean = false,
    enabled: Boolean = true,
    isTv: Boolean,
    onClick: () -> Unit,
)'''
new_signature = '''private fun SettingsMenuRow(
    label: String,
    value: String,
    accentValue: Boolean = false,
    enabled: Boolean = true,
    isTv: Boolean,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
)'''
if old_signature not in s:
    raise SystemExit('menu row signature not found')
s = s.replace(old_signature, new_signature, 1)

old_focus = '.onFocusChanged { focused = it.isFocused }'
new_focus = '''.onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (nowFocused && !focused) onFocused?.invoke()
                focused = nowFocused
            }'''
if old_focus not in s:
    raise SystemExit('focus handler not found')
s = s.replace(old_focus, new_focus, 1)

replacements = {
    'SettingsPanel(title = "اشتراكي", isTv = isTv)': 'SettingsPanel(title = "بيانات الاشتراك", isTv = isTv)',
    '"الاتصالات" to account?.let { "${it.activeConnections} / ${it.maxConnections}" }': '"البث النشط" to account?.let { "${it.activeConnections} / ${it.maxConnections}" }',
    'SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar-SA"))': 'SimpleDateFormat("yyyy/MM/dd", Locale.US)',
}
for old, new in replacements.items():
    if old not in s:
        raise SystemExit(f'expected text not found: {old}')
    s = s.replace(old, new, 1)

p.write_text(s)

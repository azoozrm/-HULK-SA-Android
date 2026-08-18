from pathlib import Path
p = Path('app/src/main/java/sa/hulksa/player/ui/screens/SettingsProScreen.kt')
s = p.read_text()
s = s.replace('SettingsPanel(title = "اشتراكي", isTv = isTv)', 'SettingsPanel(title = "بيانات الاشتراك", isTv = isTv)')
s = s.replace('enabled = !state.isAccountRefreshing && account != null,\n                        isTv = isTv,\n                        onClick = onRefreshAccount,', 'enabled = account != null,\n                        isTv = isTv,\n                        onClick = {\n                            if (!state.isAccountRefreshing) onRefreshAccount()\n                        },')
s = s.replace('"الاتصالات" to account?.let { "${it.activeConnections} / ${it.maxConnections}" }', '"البث النشط" to account?.let { "${it.activeConnections} / ${it.maxConnections}" }')
s = s.replace('SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar-SA"))', 'SimpleDateFormat("yyyy/MM/dd", Locale.US)')
p.write_text(s)

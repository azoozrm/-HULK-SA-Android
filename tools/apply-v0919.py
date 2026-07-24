from pathlib import Path
import sys

root = Path(sys.argv[1])
main = root / 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
gradle = root / 'app/build.gradle.kts'

M = main.read_text()
G = gradle.read_text()

G = G.replace('versionCode = 30', 'versionCode = 31')
G = G.replace('versionName = "0.9.1.8"', 'versionName = "0.9.1.9"')

if 'import androidx.compose.foundation.focusable\n' not in M:
    M = M.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.focusable\n', 1)

anchor = '''    val account = state.account
    LazyColumn(
        modifier = Modifier.fillMaxSize(),'''
replacement = '''    val account = state.account
    val settingsListState = rememberLazyListState()
    val diagnosticsTopRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        settingsListState.scrollToItem(0)
    }
    LaunchedEffect(state.diagnostics.report?.generatedAtEpochMs) {
        if (state.diagnostics.report != null) {
            settingsListState.scrollToItem(2)
            delay(120L)
            diagnosticsTopRequester.requestFocus()
        }
    }
    LazyColumn(
        state = settingsListState,
        modifier = Modifier.fillMaxSize(),'''
if anchor not in M:
    raise SystemExit('settings list anchor missing')
M = M.replace(anchor, replacement, 1)

call_anchor = '''                onRun = onRunDiagnostics,
                onShare = { report -> shareDiagnosticsReport(context, report) },
            )'''
call_replacement = '''                onRun = onRunDiagnostics,
                onShare = { report -> shareDiagnosticsReport(context, report) },
                topRequester = diagnosticsTopRequester,
            )'''
if call_anchor not in M:
    raise SystemExit('diagnostics call anchor missing')
M = M.replace(call_anchor, call_replacement, 1)

sig_anchor = '''    onRun: () -> Unit,
    onShare: (ServerDiagnosticsReport) -> Unit,
) {'''
sig_replacement = '''    onRun: () -> Unit,
    onShare: (ServerDiagnosticsReport) -> Unit,
    topRequester: FocusRequester,
) {'''
if sig_anchor not in M:
    raise SystemExit('diagnostics signature anchor missing')
M = M.replace(sig_anchor, sig_replacement, 1)

button_anchor = '''                onClick = onRun,
                enabled = !state.isRunning,
                compact = true,
            )'''
button_replacement = '''                onClick = onRun,
                enabled = !state.isRunning,
                compact = true,
                modifier = Modifier.focusRequester(topRequester),
            )'''
if button_anchor not in M:
    raise SystemExit('diagnostics top button anchor missing')
M = M.replace(button_anchor, button_replacement, 1)

for marker in [
    '.background(Color(0xFF181914))\n            .padding(12.dp),',
    '.background(accent.copy(alpha = .08f))\n            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(13.dp))\n            .padding(12.dp),',
]:
    pass

# Make every report row focusable so D-pad moves through the report instead of jumping straight to the bottom button.
M = M.replace(
    '''            .background(Color(0xFF181914))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,''',
    '''            .background(Color(0xFF181914))
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,''',
    2,
)
M = M.replace(
    '''            .background(accent.copy(alpha = .08f))
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(13.dp))
            .padding(12.dp),''',
    '''            .background(accent.copy(alpha = .08f))
            .border(1.dp, accent.copy(alpha = .28f), RoundedCornerShape(13.dp))
            .focusable()
            .padding(12.dp),''',
    1,
)

share_anchor = '''                FocusButton("مشاركة التقرير الامن", { onShare(value) }, primary = false, compact = true)
            }
        }
    }
}'''
share_replacement = '''                FocusButton("مشاركة التقرير الامن", { onShare(value) }, primary = false, compact = true)
            }
            Spacer(Modifier.height(if (isTv) 34.dp else 22.dp))
        }
    }
}'''
if share_anchor not in M:
    raise SystemExit('diagnostics bottom padding anchor missing')
M = M.replace(share_anchor, share_replacement, 1)

if 'versionName = "0.9.1.9"' not in G:
    raise SystemExit('version update failed')
if 'state = settingsListState' not in M:
    raise SystemExit('settings state not applied')
if 'modifier = Modifier.focusRequester(topRequester)' not in M:
    raise SystemExit('top focus requester not applied')
if M.count('.focusable()') < 3:
    raise SystemExit('report rows were not made focusable')

main.write_text(M)
gradle.write_text(G)

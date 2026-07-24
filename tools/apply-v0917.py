from pathlib import Path
import sys

root = Path(sys.argv[1])
player_file = root / 'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
gradle_file = root / 'app/build.gradle.kts'

P = player_file.read_text()
G = gradle_file.read_text()

G = G.replace('versionCode = 28', 'versionCode = 29')
G = G.replace('versionName = "0.9.1.6"', 'versionName = "0.9.1.7"')

state_anchor = '    var seekFeedback by remember(request) { mutableStateOf<String?>(null) }\n'
if state_anchor not in P:
    raise SystemExit('v0917 seek feedback state anchor missing')
P = P.replace(
    state_anchor,
    state_anchor + '    var seekBarFocused by remember(request) { mutableStateOf(false) }\n',
    1,
)

old_timeout_effect = '''    LaunchedEffect(controlsVisible, buffering, finalError, isPlaying, browserVisible, activePanel, resumePromptVisible, controlsLocked) {
        if (
            controlsVisible && !browserVisible && activePanel == null && !resumePromptVisible &&
            !buffering && finalError == null && isPlaying && !controlsLocked
        ) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }'''
new_timeout_effect = '''    LaunchedEffect(
        controlsVisible,
        buffering,
        finalError,
        isPlaying,
        browserVisible,
        activePanel,
        resumePromptVisible,
        controlsLocked,
        seekBarFocused,
        manualSeekTargetMs,
    ) {
        if (
            controlsVisible && !browserVisible && activePanel == null && !resumePromptVisible &&
            !buffering && finalError == null && isPlaying && !controlsLocked &&
            !seekBarFocused && manualSeekTargetMs == null
        ) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }'''
if old_timeout_effect not in P:
    raise SystemExit('v0917 controls timeout effect anchor missing')
P = P.replace(old_timeout_effect, new_timeout_effect, 1)

call_anchor = '''                    onSeekTo = ::seekToPosition,
                    onAudio = { activePanel = PlayerPanel.AUDIO },'''
call_replacement = '''                    onSeekTo = ::seekToPosition,
                    onSeekingChanged = { seekBarFocused = it },
                    onAudio = { activePanel = PlayerPanel.AUDIO },'''
if call_anchor not in P:
    raise SystemExit('v0917 ModernVodControls call anchor missing')
P = P.replace(call_anchor, call_replacement, 1)

signature_anchor = '''    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAudio: () -> Unit,'''
signature_replacement = '''    onForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    onAudio: () -> Unit,'''
if signature_anchor not in P:
    raise SystemExit('v0917 ModernVodControls signature anchor missing')
P = P.replace(signature_anchor, signature_replacement, 1)

progress_call_anchor = '''            buffered = bufferedPercent / 100f,
            onSeekTo = onSeekTo,
        )'''
progress_call_replacement = '''            buffered = bufferedPercent / 100f,
            onSeekTo = onSeekTo,
            onSeekingChanged = onSeekingChanged,
        )'''
if progress_call_anchor not in P:
    raise SystemExit('v0917 SeekableProgressBar call anchor missing')
P = P.replace(progress_call_anchor, progress_call_replacement, 1)

progress_signature_anchor = '''    buffered: Float,
    onSeekTo: (Long) -> Unit,
) {'''
progress_signature_replacement = '''    buffered: Float,
    onSeekTo: (Long) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
) {'''
if progress_signature_anchor not in P:
    raise SystemExit('v0917 SeekableProgressBar signature anchor missing')
P = P.replace(progress_signature_anchor, progress_signature_replacement, 1)

preview_anchor = '''    var focused by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(positionMs, durationMs, focused) {'''
preview_replacement = '''    var focused by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }

    DisposableEffect(Unit) {
        onDispose { onSeekingChanged(false) }
    }

    LaunchedEffect(positionMs, durationMs, focused) {'''
if preview_anchor not in P:
    raise SystemExit('v0917 seek preview state anchor missing')
P = P.replace(preview_anchor, preview_replacement, 1)

focus_anchor = '''                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                }'''
focus_replacement = '''                .onFocusChanged { state ->
                    focused = state.isFocused
                    onSeekingChanged(state.isFocused)
                    if (state.isFocused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                }'''
if focus_anchor not in P:
    raise SystemExit('v0917 seek bar focus anchor missing')
P = P.replace(focus_anchor, focus_replacement, 1)

if 'versionName = "0.9.1.7"' not in G:
    raise SystemExit('v0917 version update failed')
if 'var seekBarFocused by remember(request)' not in P:
    raise SystemExit('v0917 seek focus state missing')
if '!seekBarFocused && manualSeekTargetMs == null' not in P:
    raise SystemExit('v0917 timeout guard missing')
if 'onSeekingChanged(state.isFocused)' not in P:
    raise SystemExit('v0917 seek focus callback missing')

player_file.write_text(P)
gradle_file.write_text(G)

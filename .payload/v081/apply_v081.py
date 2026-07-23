from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'project')

def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f'Expected block not found in {path}: {old[:80]!r}')
    path.write_text(text.replace(old, new, 1))

p = root / 'app/build.gradle.kts'
text = p.read_text().replace('versionCode = 20', 'versionCode = 21').replace('versionName = "0.8.0"', 'versionName = "0.8.1"')
p.write_text(text)

p = root / 'app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt'
replace_once(p, 'import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n')
replace_once(
    p,
    '''    var selectedSeason by rememberSaveable(series.id, seasons) {\n        mutableIntStateOf(resumePair?.first?.season ?: seasons.firstOrNull() ?: 0)\n    }\n''',
    '''    var selectedSeason by rememberSaveable(series.id, seasons) {\n        mutableIntStateOf(resumePair?.first?.season ?: seasons.firstOrNull() ?: 0)\n    }\n    LaunchedEffect(resumePair?.first?.id) {\n        resumePair?.first?.let { latest -> selectedSeason = latest.season }\n    }\n''',
)
replace_once(
    p,
    '''                    modifier = Modifier.padding(\n                        start = if (isTv) 7.dp else 4.dp,\n                        end = if (isTv) 7.dp else 4.dp,\n                    ),''',
    '''                    modifier = Modifier.padding(\n                        start = if (isTv) 18.dp else 7.dp,\n                        end = if (isTv) 18.dp else 7.dp,\n                    ),''',
)

p = root / 'app/src/main/java/sa/hulksa/player/HulkViewModel.kt'
replace_once(
    p,
    '''    fun onPlaybackProgress(positionMs: Long, durationMs: Long) {\n        val request = mutableState.value.playback ?: return\n        if (request.isLive) return\n        val updated = userLibrary.updateProgress(request, positionMs, durationMs)\n        mutableState.update { it.copy(history = updated) }\n    }\n''',
    '''    fun onPlaybackProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long) {\n        if (request.isLive) return\n        val updated = userLibrary.updateProgress(request, positionMs, durationMs)\n        mutableState.update { it.copy(history = updated) }\n    }\n''',
)

p = root / 'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
text = p.read_text()
text = text.replace(
    'onProgress: (positionMs: Long, durationMs: Long) -> Unit,',
    'onProgress: (request: PlaybackRequest, positionMs: Long, durationMs: Long) -> Unit,',
    1,
)
text = text.replace(
    'onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))',
    'onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))',
)
p.write_text(text)

replace_once(
    p,
    '''    fun seekBy(deltaMs: Long) {\n        if (request.isLive || durationMs <= 0L) return\n        val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs)\n        player.seekTo(target)\n        currentPositionMs = target\n        seekFeedback = if (deltaMs > 0) "+10 ث" else "-10 ث"\n        controlsVisible = true\n    }\n''',
    '''    fun seekBy(deltaMs: Long) {\n        if (request.isLive || durationMs <= 0L) return\n        val target = (player.currentPosition + deltaMs).coerceIn(0L, durationMs)\n        player.seekTo(target)\n        currentPositionMs = target\n        seekFeedback = if (deltaMs > 0) "+10 ث" else "-10 ث"\n        controlsVisible = true\n    }\n\n    fun seekToPosition(targetMs: Long) {\n        if (request.isLive || durationMs <= 0L) return\n        val target = targetMs.coerceIn(0L, durationMs)\n        player.seekTo(target)\n        currentPositionMs = target\n        seekFeedback = "انتقال إلى ${formatTime(target)}"\n        controlsVisible = true\n    }\n\n    fun saveCurrentProgress() {\n        if (!request.isLive) {\n            onProgress(request, player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))\n        }\n    }\n\n    fun saveAndBack() {\n        saveCurrentProgress()\n        onBack()\n    }\n\n    fun saveAndPlayNext() {\n        saveCurrentProgress()\n        onPlayNextEpisode?.invoke()\n    }\n''',
)
replace_once(p, '            else -> onBack()\n', '            else -> saveAndBack()\n')
replace_once(p, '                onBack = onBack,\n', '                onBack = ::saveAndBack,\n')
replace_once(p, '            onPlayNextEpisode?.invoke()\n', '            saveAndPlayNext()\n')
replace_once(p, '                onPlayNow = { nextCountdown = -1; onPlayNextEpisode() },\n', '                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },\n')
replace_once(
    p,
    '''                    onRewind = { seekBy(-SEEK_STEP_MS) },\n                    onForward = { seekBy(SEEK_STEP_MS) },\n''',
    '''                    onRewind = { seekBy(-SEEK_STEP_MS) },\n                    onForward = { seekBy(SEEK_STEP_MS) },\n                    onSeekTo = ::seekToPosition,\n''',
)
replace_once(
    p,
    '''    onRewind: () -> Unit,\n    onForward: () -> Unit,\n    onAudio: () -> Unit,\n''',
    '''    onRewind: () -> Unit,\n    onForward: () -> Unit,\n    onSeekTo: (Long) -> Unit,\n    onAudio: () -> Unit,\n''',
)
replace_once(
    p,
    '        BufferedProgressBar(progress = animatedProgress, buffered = bufferedPercent / 100f)\n',
    '''        SeekableProgressBar(\n            positionMs = positionMs,\n            durationMs = durationMs,\n            buffered = bufferedPercent / 100f,\n            onSeekTo = onSeekTo,\n        )\n''',
)

seekable = r'''@Composable
private fun SeekableProgressBar(
    positionMs: Long,
    durationMs: Long,
    buffered: Float,
    onSeekTo: (Long) -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(positionMs, durationMs, focused) {
        if (!focused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    val activePosition = if (focused) previewMs else positionMs
    val progress = if (durationMs > 0L) {
        (activePosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(20.dp)

    Column(Modifier.fillMaxWidth()) {
        if (focused) {
            Text(
                "${formatTime(previewMs)}  •  حرّك يمين ويسار ثم اضغط OK",
                color = colors.goldBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 13.dp else 8.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = .18f))
                .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)
                .onFocusChanged { state ->
                    focused = state.isFocused
                    if (state.isFocused) previewMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || durationMs <= 0L) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            previewMs = (previewMs - SEEK_STEP_MS).coerceAtLeast(0L)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            previewMs = (previewMs + SEEK_STEP_MS).coerceAtMost(durationMs)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            onSeekTo(previewMs)
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .28f)))
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
        }
    }
}

'''
replace_once(
    p,
    '@Composable\nprivate fun BufferedProgressBar(progress: Float, buffered: Float) {\n',
    seekable + '@Composable\nprivate fun BufferedProgressBar(progress: Float, buffered: Float) {\n',
)

print('Applied HULK SA v0.8.1 series progress and seek hotfix')

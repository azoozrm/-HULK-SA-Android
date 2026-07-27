#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

p='app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
rep(p,'import androidx.compose.foundation.focusable\n','import androidx.compose.foundation.focusable\nimport androidx.compose.foundation.focusGroup\n','focusGroup import')
rep(p,'import androidx.compose.ui.focus.FocusRequester\n','import androidx.compose.ui.focus.FocusRequester\nimport androidx.compose.ui.focus.focusProperties\n','focusProperties import')
rep(p,'import sa.hulksa.player.ui.components.BrandBadge\n','import sa.hulksa.player.ui.adaptive.HulkInputMode\nimport sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.components.BrandBadge\n','adaptive imports')
rep(p,'    val unlockFocus = remember { FocusRequester() }\n','''    val unlockFocus = remember { FocusRequester() }
    val nextEpisodePlayFocus = remember { FocusRequester() }
    val nextEpisodeCancelFocus = remember { FocusRequester() }
''','next focus requesters')
rep(p,'''                    nextCountdown = NEXT_EPISODE_SECONDS
                    controlsVisible = true
''','''                    nextCountdown = NEXT_EPISODE_SECONDS
                    controlsVisible = false
''','hide controls when prompt opens')
rep(p,'''    LaunchedEffect(controlsVisible, activePanel, browserVisible, resumePromptVisible, unlockVisible, controlsLocked) {
        if (browserVisible || activePanel != null || resumePromptVisible || unlockVisible) return@LaunchedEffect
''','''    LaunchedEffect(controlsVisible, activePanel, browserVisible, resumePromptVisible, unlockVisible, controlsLocked, nextCountdown) {
        if (browserVisible || activePanel != null || resumePromptVisible || unlockVisible || nextCountdown >= 0) return@LaunchedEffect
''','focus isolation effect')
rep(p,'    LaunchedEffect(resumePromptVisible, request.historyKey) {\n','''    LaunchedEffect(nextCountdown, request.historyKey) {
        if (nextCountdown >= 0) {
            repeat(4) {
                delay(if (it == 0) 70L else 130L)
                runCatching { nextEpisodePlayFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(resumePromptVisible, request.historyKey) {
''','prompt focus request')
rep(p,'''                    resumePromptVisible || unlockVisible
''','''                    resumePromptVisible || unlockVisible || nextCountdown >= 0
''','disable parent keys under prompt')
rep(p,'''        if (controlsVisible && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
''','''        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            PlayerTopBar(
''','hide top controls')
rep(p,'''        if (controlsVisible && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
''','''        if (controlsVisible && nextCountdown < 0 && finalError == null && !browserVisible && activePanel == null && !controlsLocked) {
            if (request.isLive) {
''','hide bottom controls')
rep(p,'''            NextEpisodePrompt(
                title = nextEpisodeTitle,
                seconds = nextCountdown,
                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },
                onCancel = { nextCountdown = -1 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp),
            )
''','''            NextEpisodePrompt(
                title = nextEpisodeTitle,
                seconds = nextCountdown,
                playFocusRequester = nextEpisodePlayFocus,
                cancelFocusRequester = nextEpisodeCancelFocus,
                onPlayNow = { nextCountdown = -1; saveAndPlayNext() },
                onCancel = { nextCountdown = -1 },
                modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp),
            )
''','prompt wiring')
rep(p,'''    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .92f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
''','''    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .92f), Color.Transparent)))
            .statusBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 36.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 36.dp else 24.dp,
                top = if (adaptiveUi.isTelevision) 24.dp else 10.dp,
                bottom = 10.dp,
            ),
''','top safe area')
rep(p,'''    val colors = LocalHulkColors.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
''','''    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
''','VOD adaptive context')
rep(p,'            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),\n','''            .padding(
                start = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 30.dp else 20.dp,
            ),
''','VOD safe area')
rep(p,'''    val colors = LocalHulkColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
''','''    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(
                start = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                end = if (adaptiveUi.isTelevision) 34.dp else 24.dp,
                top = 12.dp,
                bottom = if (adaptiveUi.isTelevision) 32.dp else 24.dp,
            ),
''','live safe area and context')
rep(p,'            Text("اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة", color = colors.textMuted, fontSize = 10.sp)\n','''            Text(
                if (adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE) {
                    "السهم لاعلى: القناة التالية  •  السهم لاسفل: القناة السابقة"
                } else {
                    "اسحب لاعلى للقناة التالية  •  اسحب لاسفل للقناة السابقة"
                },
                color = colors.textMuted,
                fontSize = 10.sp,
            )
''','remote-aware hint')
rep(p,'''private fun NextEpisodePrompt(
    title: String,
    seconds: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
''','''private fun NextEpisodePrompt(
    title: String,
    seconds: Int,
    playFocusRequester: FocusRequester,
    cancelFocusRequester: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
''','prompt parameters')
rep(p,'''        modifier = modifier
            .width(430.dp)
            .clip(RoundedCornerShape(20.dp))
''','''        modifier = modifier
            .width(430.dp)
            .focusGroup()
            .clip(RoundedCornerShape(20.dp))
''','prompt focus group')
rep(p,'''        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton("تشغيل الان", onPlayNow, compact = true)
            FocusButton("الغاء", onCancel, primary = false, compact = true)
        }
''','''        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(
                "تشغيل الان",
                onPlayNow,
                modifier = Modifier.focusRequester(playFocusRequester).focusProperties {
                    left = cancelFocusRequester
                    right = cancelFocusRequester
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                },
                compact = true,
            )
            FocusButton(
                "الغاء",
                onCancel,
                modifier = Modifier.focusRequester(cancelFocusRequester).focusProperties {
                    left = playFocusRequester
                    right = playFocusRequester
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                },
                primary = false,
                compact = true,
            )
        }
''','prompt focus trap')
print('Prepared v0.9.3.16 player fixes')

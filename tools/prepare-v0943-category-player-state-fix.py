#!/usr/bin/env python3
# Dedicated v0.9.3.13 build trigger after registered workflow replacement.
from pathlib import Path
import sys

root = Path(sys.argv[1])

def rep(path, old, new, label):
    p = root / path
    s = p.read_text(encoding='utf-8')
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

rep('app/build.gradle.kts', 'versionCode = 56', 'versionCode = 57', 'versionCode')
rep('app/build.gradle.kts', 'versionName = "0.9.3.12"', 'versionName = "0.9.3.13"', 'versionName')

main = 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
# Restore category position only when the selected category changes. Reordering changes ordered,
# and must not re-run the restore effect because that fights the user's active movement.
rep(main,
'''    LaunchedEffect(selectedId, ordered) {
''',
'''    LaunchedEffect(selectedId) {
''',
'catalog restore effect key')
# There are two identical effects (catalog + live); replace the second occurrence too.
p = root / main
s = p.read_text(encoding='utf-8')
old = '    LaunchedEffect(selectedId, ordered) {\n'
if old in s:
    s = s.replace(old, '    LaunchedEffect(selectedId) {\n', 1)
    p.write_text(s, encoding='utf-8')

# During manual reorder always keep the moved category visible at its exact new adapter index.
rep(main,
'''            scope.launch {
                val targetIndex = to + 3
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: targetIndex
                val last = visible.lastOrNull()?.index ?: targetIndex
                when {
                    targetIndex < first -> listState.animateScrollToItem(targetIndex)
                    targetIndex > last -> listState.animateScrollToItem(targetIndex)
                }
            }
''',
'''            scope.launch {
                val targetIndex = to + 3
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
            }
''',
'catalog active reorder follow')
rep(main,
'''            scope.launch {
                val targetIndex = to + 1
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: targetIndex
                val last = visible.lastOrNull()?.index ?: targetIndex
                when {
                    targetIndex < first -> listState.animateScrollToItem(targetIndex)
                    targetIndex > last -> listState.animateScrollToItem(targetIndex)
                }
            }
''',
'''            scope.launch {
                val targetIndex = to + 1
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
            }
''',
'live active reorder follow')

player = 'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
rep(player,
'''    BackHandler {
        when {
            browserVisible -> browserVisible = false
            activePanel != null -> activePanel = null
            resumePromptVisible -> {
                resumePromptVisible = false
                player.seekTo(0L)
                player.play()
            }
            nextCountdown >= 0 -> nextCountdown = -1
            controlsLocked -> {
                unlockVisible = true
                controlsVisible = true
            }
            controlsVisible -> controlsVisible = false
            else -> saveAndBack()
        }
    }
''',
'''    fun handleBackAction() {
        when {
            browserVisible -> browserVisible = false
            activePanel != null -> activePanel = null
            resumePromptVisible -> {
                resumePromptVisible = false
                player.seekTo(0L)
                player.play()
            }
            nextCountdown >= 0 -> nextCountdown = -1
            controlsLocked -> {
                unlockVisible = true
                controlsVisible = true
            }
            controlsVisible -> {
                controlsVisible = false
                activePanel = null
                browserVisible = false
            }
            else -> saveAndBack()
        }
    }

    BackHandler { handleBackAction() }
''',
'central player back action')

rep(player,
'''                val keyCode = event.nativeKeyEvent.keyCode
                if (controlsLocked) {
''',
'''                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK || keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                    handleBackAction()
                    return@onPreviewKeyEvent true
                }
                if (controlsLocked) {
''',
'consume player back on first key event')

rep(player,
'''                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    -> false
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
''',
'''                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
''',
'remove duplicate back fallthrough')

print('Prepared v0.9.3.13 category tracking and player back state fix')

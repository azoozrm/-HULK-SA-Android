#!/usr/bin/env python3
# v0.9.3.13 targeted regression fix: category follow + exact two-step player back.
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
rep(main,
'''    LaunchedEffect(selectedId, ordered) {
        val targetIndex = when (selectedId) {
            null -> 0
            FAVORITES_CATEGORY_ID -> 1
            CONTINUE_CATEGORY_ID -> 2
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(3)
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }
''',
'''    LaunchedEffect(selectedId, ordered.size, moving) {
        if (moving != null) return@LaunchedEffect
        val targetIndex = when (selectedId) {
            null -> 0
            FAVORITES_CATEGORY_ID -> 1
            CONTINUE_CATEGORY_ID -> 2
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(3)
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }
''', 'catalog restore must not fight reorder')
rep(main,
'''    LaunchedEffect(selectedId, ordered) {
        val targetIndex = when (selectedId) {
            FAVORITES_CATEGORY_ID -> 0
            null -> 0
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }
''',
'''    LaunchedEffect(selectedId, ordered.size, moving) {
        if (moving != null) return@LaunchedEffect
        val targetIndex = when (selectedId) {
            FAVORITES_CATEGORY_ID -> 0
            null -> 0
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }
''', 'live restore must not fight reorder')

old_catalog = '''            scope.launch {
                val targetIndex = to + 3
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: targetIndex
                val last = visible.lastOrNull()?.index ?: targetIndex
                when {
                    targetIndex < first -> listState.animateScrollToItem(targetIndex)
                    targetIndex > last -> listState.animateScrollToItem(targetIndex)
                }
            }
'''
new_catalog = '''            scope.launch {
                delay(55L)
                val targetIndex = to + 3
                val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (targetIndex !in visibleIndices) {
                    listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
                }
            }
'''
rep(main, old_catalog, new_catalog, 'catalog reorder follows moved chip')

old_live = '''            scope.launch {
                val targetIndex = to + 1
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: targetIndex
                val last = visible.lastOrNull()?.index ?: targetIndex
                when {
                    targetIndex < first -> listState.animateScrollToItem(targetIndex)
                    targetIndex > last -> listState.animateScrollToItem(targetIndex)
                }
            }
'''
new_live = '''            scope.launch {
                delay(55L)
                val targetIndex = to + 1
                val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (targetIndex !in visibleIndices) {
                    listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
                }
            }
'''
rep(main, old_live, new_live, 'live reorder follows moved chip')

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
'''    fun handleBackPress() {
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

    BackHandler { handleBackPress() }
''', 'single player back handler')
rep(player,
'''                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    -> false
''',
'''                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    -> {
                        if (event.nativeKeyEvent.repeatCount == 0) handleBackPress()
                        true
                    }
''', 'remote back consumed exactly once')

print('Prepared v0.9.3.13 reorder and player back fix')

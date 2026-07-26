#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path; s=p.read_text(encoding='utf-8')
    if new in s: return
    if old not in s: raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 54','versionCode = 55','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.10"','versionName = "0.9.3.11"','versionName')

main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
rep(main,
'''            scope.launch { listState.animateScrollToItem((to + 2).coerceAtLeast(0)) }
''',
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
''','catalog edge-only follow')
rep(main,
'''            scope.launch {
                listState.scrollToItem((to + 1).coerceAtLeast(0))
            }
''',
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
''','live edge-only follow')

login='app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt'
rep(login,
'''import androidx.compose.runtime.Composable
''',
'''import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
''','login LaunchedEffect import')
rep(login,
'''    val openWebsite = { runCatching { uriHandler.openUri(HULK_WEBSITE) }; Unit }

    BoxWithConstraints(
''',
'''    val openWebsite = { runCatching { uriHandler.openUri(HULK_WEBSITE) }; Unit }
    LaunchedEffect(isLoading, isStarting) {
        if (isLoading || isStarting) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    BoxWithConstraints(
''','hide login keyboard while submitting')

print('Prepared v0.9.3.11 RTL reorder and login keyboard fix')

#!/usr/bin/env python3
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

rep('app/build.gradle.kts', 'versionCode = 55', 'versionCode = 56', 'versionCode')
rep('app/build.gradle.kts', 'versionName = "0.9.3.11"', 'versionName = "0.9.3.12"', 'versionName')

main = root / 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
text = main.read_text(encoding='utf-8')

def inject_into_function(source, function_name, code, label):
    start_marker = f'private fun {function_name}('
    start = source.find(start_marker)
    if start < 0:
        raise SystemExit(f'missing {label} function')
    next_fun = source.find('\n@Composable\nprivate fun ', start + len(start_marker))
    end = len(source) if next_fun < 0 else next_fun
    block = source[start:end]
    if code.strip() in block:
        return source
    marker = '\n    fun move(id: String, direction: Int) {'
    pos = block.find(marker)
    if pos < 0:
        raise SystemExit(f'missing {label} move marker')
    block = block[:pos] + '\n' + code + block[pos:]
    return source[:start] + block + source[end:]

catalog_restore = '''    LaunchedEffect(selectedId, ordered) {
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
'''

live_restore = '''    LaunchedEffect(selectedId, ordered) {
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
'''

text = inject_into_function(text, 'ReorderableCatalogCategoryBar', catalog_restore, 'catalog category restore')
text = inject_into_function(text, 'ReorderableLiveCategoryBar', live_restore, 'live category restore')
main.write_text(text, encoding='utf-8')

print('Prepared v0.9.3.12 category position memory fix')

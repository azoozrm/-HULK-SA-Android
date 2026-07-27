#!/usr/bin/env python3
# v0.9.3.15: Xiaomi/search stability fixes.
# Keep the active text field focused while its externally-owned value changes,
# and never restore a stale content-grid position inside global search results.
from pathlib import Path
import sys

root = Path(sys.argv[1])


def rep(path, old, new, label, count=1):
    p = root / path
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if old not in s:
        raise SystemExit(f"missing {label}")
    p.write_text(s.replace(old, new, count), encoding="utf-8")


rep("app/build.gradle.kts", "versionCode = 58", "versionCode = 59", "versionCode")
rep("app/build.gradle.kts", 'versionName = "0.9.3.14"', 'versionName = "0.9.3.15"', "versionName")

components = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
rep(
    components,
    """import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
""",
    """import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
""",
    "HulkTextField LaunchedEffect import",
)
rep(
    components,
    """import androidx.compose.ui.focus.onFocusChanged
""",
    """import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
""",
    "HulkTextField focus requester imports",
)
rep(
    components,
    """    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
""",
    """    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)

    // The search value lives in HulkUiState. Updating it recomposes the whole
    // destination on some Android TV launchers, which can make the result list
    // steal focus after the first character. Re-assert focus only while this
    // field was already active; this never opens the keyboard for an inactive field.
    LaunchedEffect(value, focused) {
        if (focused) runCatching { focusRequester.requestFocus() }
    }

    BasicTextField(
""",
    "HulkTextField active focus retention",
)
rep(
    components,
    """        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
""",
    """        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
""",
    "HulkTextField requester attachment",
)

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
rep(
    main,
    """    val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
        .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    val targetKey = content.getOrNull(targetIndex)?.let { "${it.type}:${it.id}" }.orEmpty()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, content, destination) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
            content.getOrNull(index)?.let { navigationMemory.save(destination, "${it.type}:${it.id}", index) }
        }
    }
""",
    """    val targetIndex = if (destination == MainDestination.SEARCH) {
        0
    } else {
        (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
            .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    }
    val targetKey = content.getOrNull(targetIndex)?.let { "${it.type}:${it.id}" }.orEmpty()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    LaunchedEffect(gridState, content, destination) {
        if (destination == MainDestination.SEARCH) {
            // Search result sets can change by hundreds of rows per keystroke.
            // A remembered index from the previous result set must never be
            // clamped to (and displayed as) the final item in the new set.
            if (gridState.firstVisibleItemIndex != 0) gridState.scrollToItem(0)
        } else {
            snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
                content.getOrNull(index)?.let { navigationMemory.save(destination, "${it.type}:${it.id}", index) }
            }
        }
    }
""",
    "global search stale grid position",
)
rep(
    main,
    """            val restore = remembered.itemKey == key || index == targetIndex
""",
    """            val restore = destination != MainDestination.SEARCH &&
                (remembered.itemKey == key || index == targetIndex)
""",
    "disable result-card focus restoration while typing",
)

print("Prepared v0.9.3.15 Xiaomi and search stability fixes")

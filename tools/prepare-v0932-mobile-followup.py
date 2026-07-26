#!/usr/bin/env python3
from pathlib import Path
import sys


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        fail(f"{label} marker missing in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main(root_arg: str) -> None:
    root = Path(root_arg).resolve()
    build = root / "app/build.gradle.kts"
    replace_once(build, "versionCode = 45", "versionCode = 46", "version code")
    replace_once(build, 'versionName = "0.9.3.1"', 'versionName = "0.9.3.2"', "version name")

    shell = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    replace_once(shell, "import androidx.compose.foundation.focusGroup\n", "import androidx.compose.foundation.focusGroup\nimport androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n", "drag import")
    replace_once(shell, "import androidx.compose.runtime.mutableStateOf\n", "import androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.mutableStateOf\n", "drag state import")
    replace_once(shell, "import androidx.compose.ui.input.key.type\n", "import androidx.compose.ui.input.key.type\nimport androidx.compose.ui.input.pointer.pointerInput\n", "drag pointer import")
    replace_once(shell, '''@Composable
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val primaryDestinations = remember {
        destinations.filter { it.destination in setOf(
            MainDestination.HOME,
            MainDestination.LIVE,
            MainDestination.MOVIES,
            MainDestination.SERIES,
        ) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandBadge(Modifier.size(40.dp))
        primaryDestinations.forEach { entry ->
            FocusButton(
                text = entry.label,
                onClick = { onSelect(entry.destination) },
                primary = selected == entry.destination,
                compact = true,
                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
            )
        }
    }
}
''', '''@Composable
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val navigationState = rememberLazyListState()
    LaunchedEffect(selected) {
        val selectedIndex = destinations.indexOfFirst { it.destination == selected }.coerceAtLeast(0)
        navigationState.animateScrollToItem(selectedIndex + 1)
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090A07))
            .statusBarsPadding(),
        state = navigationState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { BrandBadge(Modifier.size(40.dp)) }
        items(destinations, key = { it.destination.name }) { entry ->
            FocusButton(
                text = entry.label,
                onClick = { onSelect(entry.destination) },
                primary = selected == entry.destination,
                compact = true,
                modifier = Modifier.heightIn(min = 42.dp),
            )
        }
    }
}
''', "all mobile destinations")
    replace_once(shell, "    var selectPressed by remember { mutableStateOf(false) }\n", "    var selectPressed by remember { mutableStateOf(false) }\n    var dragAccumulator by remember { mutableFloatStateOf(0f) }\n", "category drag state")
    shell_text = shell.read_text(encoding="utf-8")
    function_index = shell_text.index("private fun LiveCategoryChip")
    prefix, tail = shell_text[:function_index], shell_text[function_index:]
    old_anchor = '''            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
'''
    new_anchor = '''            .pointerInput(category.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragAccumulator = 0f
                        onLongClick()
                    },
                    onDragCancel = { dragAccumulator = 0f },
                    onDragEnd = { dragAccumulator = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount.x
                        when {
                            dragAccumulator >= 46f -> {
                                onMoveRight()
                                dragAccumulator = 0f
                            }
                            dragAccumulator <= -46f -> {
                                onMoveLeft()
                                dragAccumulator = 0f
                            }
                        }
                    },
                )
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
'''
    if new_anchor not in tail:
        if old_anchor not in tail:
            fail("category pointer anchor missing")
        shell.write_text(prefix + tail.replace(old_anchor, new_anchor, 1), encoding="utf-8")

    movie = root / "app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt"
    replace_once(movie, ".height(if (isTv) 510.dp else 440.dp)", ".height(if (isTv) 510.dp else 510.dp)", "movie hero safe height")
    replace_once(movie, "fontSize = if (isTv) 42.sp else 29.sp,\n                            lineHeight = if (isTv) 49.sp else 35.sp,", "fontSize = if (isTv) 42.sp else 26.sp,\n                            lineHeight = if (isTv) 49.sp else 31.sp,", "movie title mobile size")

    series = root / "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt"
    replace_once(series, ".height(if (isTv) 390.dp else 330.dp)", ".height(if (isTv) 390.dp else 400.dp)", "series hero safe height")
    replace_once(series, "fontSize = if (isTv) 39.sp else 27.sp,\n                    lineHeight = if (isTv) 46.sp else 33.sp,", "fontSize = if (isTv) 39.sp else 25.sp,\n                    lineHeight = if (isTv) 46.sp else 30.sp,", "series title mobile size")

    player = root / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
    replace_once(player, "import androidx.compose.foundation.gestures.detectTapGestures\n", "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\nimport androidx.compose.foundation.gestures.detectTapGestures\n", "player drag import")
    replace_once(player, '''                .background(Color.White.copy(alpha = .18f))
                .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)
                .onFocusChanged { state ->
''', '''                .background(Color.White.copy(alpha = .18f))
                .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L && size.width > 0) {
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekTo((durationMs * fraction).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { onSeekingChanged(true) },
                        onDragCancel = { onSeekingChanged(false) },
                        onDragEnd = { onSeekingChanged(false) },
                    ) { change, _ ->
                        change.consume()
                        if (durationMs > 0L && size.width > 0) {
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val target = (durationMs * fraction).toLong()
                            previewMs = target
                            onSeekTo(target)
                        }
                    }
                }
                .onFocusChanged { state ->
''', "touch seek bar")

    print("Prepared v0.9.3.2 mobile follow-up")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        fail("usage: prepare-v0932-mobile-followup.py PROJECT")
    main(sys.argv[1])

#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
main_path = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
if not main_path.is_file():
    raise SystemExit("missing MainShellScreen.kt")
text = main_path.read_text(encoding="utf-8")


def bounds(source: str, name: str):
    match = re.search(rf"(?:private\s+)?fun\s+{re.escape(name)}\s*\(", source)
    if not match:
        raise SystemExit(f"missing function {name}")
    brace = source.find("{", match.end())
    depth = 0
    in_string = False
    escaped = False
    for index in range(brace, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return match.start(), index + 1
    raise SystemExit(f"unclosed function {name}")


def patch_function(source: str, name: str, update):
    start, end = bounds(source, name)
    block = source[start:end]
    changed = update(block)
    print(f"PASS: {name} {'patched' if changed != block else 'already patched'}")
    return source[:start] + changed + source[end:]


def search_update(block: str) -> str:
    if "val initialSearchQuery = remember { state.searchQuery }" not in block:
        marker = "    val resultsFocusRequester = remember { FocusRequester() }\n"
        if marker not in block:
            raise SystemExit("missing results focus requester")
        block = block.replace(marker, marker + "    val initialSearchQuery = remember { state.searchQuery }\n", 1)

    old_column = "    Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {"
    new_column = '''    LaunchedEffect(isTv, initialSearchQuery, results) {
        if (isTv && initialSearchQuery.isNotBlank() && state.searchQuery == initialSearchQuery && results.isNotEmpty()) {
            delay(240L)
            runCatching { resultsFocusRequester.requestFocus() }
        }
    }
    val searchRootModifier = Modifier
        .fillMaxSize()
        .padding(if (isTv) 24.dp else 13.dp)
        .then(
            if (isTv) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && results.isNotEmpty()) {
                        runCatching { resultsFocusRequester.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
        )
    Column(searchRootModifier) {'''
    if new_column not in block:
        if old_column not in block:
            raise SystemExit("missing search root column")
        block = block.replace(old_column, new_column, 1)
    return block


def hero_update(block: str) -> str:
    return block.replace(
        ".height(if (isTv) 374.dp else 288.dp)",
        ".height(if (isTv) 344.dp else 288.dp)",
        1,
    )


def settings_update(block: str) -> str:
    return block.replace(
        "    LaunchedEffect(Unit) {\n        settingsListState.scrollToItem(0)\n    }\n",
        "",
        1,
    )


def progress_update(block: str) -> str:
    if "val adaptiveUi = LocalAdaptiveUi.current" not in block:
        marker = "    val colors = LocalHulkColors.current\n"
        if marker not in block:
            raise SystemExit("missing DownloadProgress colors")
        block = block.replace(marker, marker + "    val adaptiveUi = LocalAdaptiveUi.current\n", 1)
    block = block.replace("fontSize = 9.sp", "fontSize = if (adaptiveUi.isTelevision) 12.sp else 9.sp")
    block = block.replace("fontSize = 8.sp", "fontSize = if (adaptiveUi.isTelevision) 11.sp else 8.sp")
    return block


text = patch_function(text, "UnifiedSearchScreen", search_update)
text = patch_function(text, "CinemaHero", hero_update)
text = patch_function(text, "SettingsScreen", settings_update)
text = patch_function(text, "DownloadProgress", progress_update)
main_path.write_text(text, encoding="utf-8")
print("PASS: prepared final TV focus and safe-area hardening")

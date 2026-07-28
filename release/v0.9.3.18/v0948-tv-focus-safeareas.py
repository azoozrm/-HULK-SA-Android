#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()


def load(relative: str) -> tuple[Path, str]:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"missing source file: {relative}")
    return path, path.read_text(encoding="utf-8")


def save(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def function_bounds(text: str, name: str) -> tuple[int, int] | None:
    match = re.search(rf"(?:private\s+)?fun\s+{re.escape(name)}\s*\(", text)
    if not match:
        return None
    start = match.start()
    brace = text.find("{", match.end())
    if brace < 0:
        return None
    depth = 0
    in_string = False
    escaped = False
    for index in range(brace, len(text)):
        char = text[index]
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
                return start, index + 1
    return None


def replace_function_block(text: str, name: str, updater) -> str:
    bounds = function_bounds(text, name)
    if bounds is None:
        raise SystemExit(f"missing function: {name}")
    start, end = bounds
    block = text[start:end]
    updated = updater(block)
    print(f"PASS: {name} {'already patched' if updated == block else 'patched'}")
    return text[:start] + updated + text[end:]


def patch_unified_search(block: str) -> str:
    if "val resultsFocusRequester = remember { FocusRequester() }" not in block:
        marker = "    val focusManager = LocalFocusManager.current\n"
        if marker not in block:
            raise SystemExit("missing UnifiedSearchScreen focus manager marker")
        block = block.replace(marker, marker + "    val resultsFocusRequester = remember { FocusRequester() }\n", 1)

    block = block.replace(
        "Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)",
        "Key.DirectionDown -> runCatching { resultsFocusRequester.requestFocus() }.isSuccess",
        1,
    )

    if ".focusProperties { down = resultsFocusRequester }" not in block:
        marker = "        val searchFieldModifier = Modifier\n            .fillMaxWidth()\n"
        if marker not in block:
            raise SystemExit("missing search field modifier marker")
        block = block.replace(
            marker,
            marker + "            .then(if (isTv) Modifier.focusProperties { down = resultsFocusRequester } else Modifier)\n",
            1,
        )

    one_line = "            ContentGrid(results, isTv, MainDestination.SEARCH, navigationMemory, isFavorite, onOpen, onToggleFavorite)"
    multi_pattern = re.compile(
        r"\s{12}ContentGrid\(\s*\n"
        r"\s*(?:content\s*=\s*)?results,\s*\n"
        r"\s*(?:isTv\s*=\s*)?isTv,\s*\n"
        r"\s*(?:destination\s*=\s*)?MainDestination\.SEARCH,\s*\n"
        r"\s*(?:navigationMemory\s*=\s*)?navigationMemory,\s*\n"
        r"\s*(?:isFavorite\s*=\s*)?isFavorite,\s*\n"
        r"\s*(?:onOpen\s*=\s*)?onOpen,\s*\n"
        r"\s*(?:onToggleFavorite\s*=\s*)?onToggleFavorite,\s*\n"
        r"(?:\s*restoreFocusedCard\s*=\s*[^,]+,\s*\n)?"
        r"(?:\s*entryFocusRequester\s*=\s*[^,]+,\s*\n)?"
        r"\s*\)",
        re.MULTILINE,
    )
    replacement = '''            ContentGrid(
                content = results,
                isTv = isTv,
                destination = MainDestination.SEARCH,
                navigationMemory = navigationMemory,
                isFavorite = isFavorite,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                restoreFocusedCard = false,
                entryFocusRequester = if (isTv) resultsFocusRequester else null,
            )'''
    if one_line in block:
        block = block.replace(one_line, replacement, 1)
    elif multi_pattern.search(block):
        block = multi_pattern.sub(replacement, block, count=1)
    elif "entryFocusRequester = if (isTv) resultsFocusRequester else null" not in block:
        raise SystemExit("missing UnifiedSearchScreen ContentGrid call")
    return block


def patch_content_grid(block: str) -> str:
    if "entryFocusRequester: FocusRequester? = null," not in block:
        marker = "    restoreFocusedCard: Boolean = true,\n"
        if marker not in block:
            raise SystemExit("missing ContentGrid signature marker")
        block = block.replace(marker, marker + "    entryFocusRequester: FocusRequester? = null,\n", 1)

    old_modifier = "                modifier = Modifier.fillMaxWidth().restoreFocus(restore, targetRequester),"
    new_modifier = '''                modifier = if (index == 0 && entryFocusRequester != null) {
                    Modifier.fillMaxWidth().focusRequester(entryFocusRequester)
                } else {
                    Modifier.fillMaxWidth().restoreFocus(restore, targetRequester)
                },'''
    if new_modifier not in block:
        if old_modifier not in block:
            raise SystemExit("missing ContentGrid card modifier marker")
        block = block.replace(old_modifier, new_modifier, 1)

    block = re.sub(
        r"(if \(destination == MainDestination\.SEARCH\) \{\s*\n"
        r"\s*if \(content\.isNotEmpty\(\)\) gridState\.scrollToItem\(0\)\s*\n"
        r"\s*navigationMemory\.save\([^\n]+\)\s*\n)"
        r"\s*if \(restoreFocusedCard && content\.isNotEmpty\(\)\) \{\s*\n"
        r"\s*delay\(\d+\)\s*\n"
        r"\s*runCatching \{ targetRequester\.requestFocus\(\) \}\s*\n"
        r"\s*\}\s*\n",
        r"\1",
        block,
        count=1,
        flags=re.MULTILINE,
    )
    return block


def patch_history_section(block: str) -> str:
    return block.replace(
        "Modifier.width(if (isTv) 238.dp else 190.dp).restoreFocus(restore, targetRequester)",
        "Modifier.width(if (isTv) 214.dp else 190.dp).restoreFocus(restore, targetRequester)",
        1,
    )


def patch_settings(block: str) -> str:
    old = "        contentPadding = PaddingValues(if (isTv) 27.dp else 15.dp),"
    new = '''        contentPadding = PaddingValues(
            start = if (isTv) 27.dp else 15.dp,
            top = if (isTv) 36.dp else 15.dp,
            end = if (isTv) 27.dp else 15.dp,
            bottom = if (isTv) 32.dp else 15.dp,
        ),'''
    if new not in block:
        if old not in block:
            raise SystemExit("missing SettingsScreen content padding marker")
        block = block.replace(old, new, 1)
    return block


def patch_download_card(block: str) -> str:
    return block.replace(
        ".height(if (isTv) 236.dp else 220.dp)",
        ".height(if (isTv) 220.dp else 220.dp)",
        1,
    )


def patch_main_shell() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    path, text = load(relative)
    text = replace_function_block(text, "UnifiedSearchScreen", patch_unified_search)
    text = replace_function_block(text, "ContentGrid", patch_content_grid)
    text = replace_function_block(text, "HistorySection", patch_history_section)
    text = replace_function_block(text, "SettingsScreen", patch_settings)
    text = replace_function_block(text, "DownloadCard", patch_download_card)
    save(path, text)


def patch_history_card() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
    path, text = load(relative)

    def updater(block: str) -> str:
        old = '            Text("استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)'
        new = '''            Text(
                "استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}",
                color = colors.goldBright,
                fontSize = if (adaptiveUi.isTelevision) 12.sp else 9.sp,
                lineHeight = if (adaptiveUi.isTelevision) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )'''
        if new not in block:
            if old not in block:
                raise SystemExit("missing HistoryCard metadata marker")
            block = block.replace(old, new, 1)
        return block

    text = replace_function_block(text, "HistoryCard", updater)
    save(path, text)


patch_main_shell()
patch_history_card()
print("PASS: prepared TV focus and safe-area follow-up")

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


def ensure_import(text: str, import_line: str) -> tuple[str, bool]:
    if import_line in text:
        return text, False
    lines = text.splitlines(keepends=True)
    indexes = [index for index, line in enumerate(lines) if line.startswith("import ")]
    if not indexes:
        raise SystemExit(f"missing import section for {import_line.strip()}")
    lines.insert(indexes[-1] + 1, import_line)
    return "".join(lines), True


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


def patch_hulk_text_field() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
    path, text = load(relative)
    bounds = function_bounds(text, "HulkTextField")
    if bounds is None:
        raise SystemExit("missing HulkTextField")
    start, end = bounds
    block = text[start:end]

    if "    readOnly: Boolean = false,\n" not in block:
        marker = "    modifier: Modifier = Modifier,\n"
        if marker not in block:
            raise SystemExit("missing HulkTextField modifier parameter")
        block = block.replace(marker, marker + "    readOnly: Boolean = false,\n", 1)

    if "        readOnly = readOnly,\n" not in block:
        marker = "        onValueChange = onValueChange,\n"
        if marker not in block:
            raise SystemExit("missing BasicTextField onValueChange marker")
        block = block.replace(marker, marker + "        readOnly = readOnly,\n", 1)

    save(path, text[:start] + block + text[end:])
    print("PASS: HulkTextField TV read-only mode support")


def patch_unified_search() -> None:
    relative = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    path, text = load(relative)

    for import_line in (
        "import androidx.compose.foundation.text.KeyboardActions\n",
        "import androidx.compose.foundation.text.KeyboardOptions\n",
        "import androidx.compose.ui.platform.LocalSoftwareKeyboardController\n",
        "import androidx.compose.ui.text.input.ImeAction\n",
    ):
        text, added = ensure_import(text, import_line)
        if added:
            print(f"PASS: {import_line.strip()} added")

    bounds = function_bounds(text, "UnifiedSearchScreen")
    if bounds is None:
        raise SystemExit("missing UnifiedSearchScreen")
    start, end = bounds
    block = text[start:end]

    if "val keyboardController = LocalSoftwareKeyboardController.current" not in block:
        marker = "    val focusManager = LocalFocusManager.current\n"
        if marker not in block:
            raise SystemExit("missing UnifiedSearchScreen focus manager marker")
        block = block.replace(
            marker,
            marker
            + "    val keyboardController = LocalSoftwareKeyboardController.current\n"
            + "    var tvSearchEditing by remember { mutableStateOf(false) }\n",
            1,
        )

    if "LaunchedEffect(isTv, tvSearchEditing)" not in block:
        marker = "    val results = remember(state.catalogs, state.searchQuery) {\n"
        if marker not in block:
            raise SystemExit("missing UnifiedSearchScreen results marker")
        effect = '''    LaunchedEffect(isTv, tvSearchEditing) {
        if (isTv) {
            if (tvSearchEditing) keyboardController?.show() else keyboardController?.hide()
        }
    }
'''
        block = block.replace(marker, effect + marker, 1)

    if ".onFocusChanged { focusState ->" not in block:
        marker = "        val searchFieldModifier = Modifier\n            .fillMaxWidth()\n"
        if marker not in block:
            raise SystemExit("missing search field modifier marker")
        addition = '''        val searchFieldModifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (isTv && !focusState.isFocused) {
                    tvSearchEditing = false
                    keyboardController?.hide()
                }
            }
'''
        block = block.replace(marker, addition, 1)

    old_when = '''                            when (event.key) {
                                Key.DirectionDown -> runCatching { resultsFocusRequester.requestFocus() }.isSuccess
                                Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                                else -> false
                            }'''
    new_when = '''                            when (event.key) {
                                Key.Enter, Key.DirectionCenter -> {
                                    if (tvSearchEditing) {
                                        false
                                    } else {
                                        tvSearchEditing = true
                                        true
                                    }
                                }
                                Key.DirectionDown -> {
                                    tvSearchEditing = false
                                    keyboardController?.hide()
                                    runCatching { resultsFocusRequester.requestFocus() }.isSuccess
                                }
                                Key.DirectionLeft -> {
                                    tvSearchEditing = false
                                    keyboardController?.hide()
                                    focusManager.moveFocus(FocusDirection.Left)
                                }
                                else -> false
                            }'''
    if new_when not in block:
        if old_when not in block:
            raise SystemExit("missing UnifiedSearchScreen key handler marker")
        block = block.replace(old_when, new_when, 1)

    old_call = '''            label = "ابحث بالاسم او السنة او النوع…",
            modifier = searchFieldModifier,
        )'''
    new_call = '''            label = "ابحث بالاسم او السنة او النوع…",
            modifier = searchFieldModifier,
            readOnly = isTv && !tvSearchEditing,
            keyboardOptions = if (isTv) {
                KeyboardOptions(imeAction = ImeAction.Search)
            } else {
                KeyboardOptions.Default
            },
            keyboardActions = if (isTv) {
                KeyboardActions(
                    onSearch = {
                        tvSearchEditing = false
                        keyboardController?.hide()
                        runCatching { resultsFocusRequester.requestFocus() }
                    },
                )
            } else {
                KeyboardActions.Default
            },
        )'''
    if new_call not in block:
        if old_call not in block:
            raise SystemExit("missing UnifiedSearchScreen HulkTextField call marker")
        block = block.replace(old_call, new_call, 1)

    save(path, text[:start] + block + text[end:])
    print("PASS: TV Search uses explicit edit mode and D-pad-safe focus")


patch_hulk_text_field()
patch_unified_search()
print("PASS: prepared TV search edit-mode focus fix")

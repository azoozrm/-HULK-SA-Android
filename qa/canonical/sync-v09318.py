#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import re
import stat
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()


def read(relative: str) -> tuple[Path, str]:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"missing required file: {relative}")
    return path, path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        print(f"PASS: {label} already applied")
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, found {count}")
    print(f"PASS: {label}")
    return text.replace(old, new, 1)


def ensure_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    lines = text.splitlines(keepends=True)
    import_indexes = [index for index, line in enumerate(lines) if line.startswith("import ")]
    if not import_indexes:
        raise SystemExit(f"missing import section for {import_line.strip()}")
    lines.insert(import_indexes[-1] + 1, import_line)
    print(f"PASS: added {import_line.strip()}")
    return "".join(lines)


def function_bounds(text: str, name: str) -> tuple[int, int]:
    match = re.search(rf"(?:private\s+)?fun\s+{re.escape(name)}\s*\(", text)
    if not match:
        raise SystemExit(f"missing function: {name}")
    start = match.start()
    annotation_start = text.rfind("@", 0, start)
    if annotation_start >= 0 and text[annotation_start:start].strip().startswith("@"):
        previous_break = text.rfind("\n\n", 0, annotation_start)
        start = previous_break + 2 if previous_break >= 0 else annotation_start
    brace = text.find("{", match.end())
    if brace < 0:
        raise SystemExit(f"missing function body: {name}")
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
    raise SystemExit(f"unterminated function body: {name}")


def patch_build_version() -> None:
    path, text = read("app/build.gradle.kts")
    text = replace_once(text, "        versionCode = 61", "        versionCode = 62", "versionCode 62")
    text = replace_once(text, '        versionName = "0.9.3.17"', '        versionName = "0.9.3.18"', "versionName 0.9.3.18")
    write(path, text)


def patch_adaptive_classifier() -> None:
    path, text = read("app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt")
    text = replace_once(
        text,
        "    smallestWidthDp >= 600 || widthDp >= 840 -> HulkDeviceClass.TABLET",
        "    smallestWidthDp >= 600 -> HulkDeviceClass.TABLET",
        "landscape phones remain mobile",
    )
    text = replace_once(
        text,
        "    windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL",
        "    deviceClass == HulkDeviceClass.TABLET && windowWidthClass == HulkWindowWidthClass.EXPANDED -> HulkNavigationType.RAIL",
        "expanded phones retain top navigation",
    )
    write(path, text)

    test_path, tests = read("app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt")
    test_name = "fun landscapePhoneDoesNotBecomeTabletOrRail()"
    if test_name not in tests:
        marker = "    @Test\n    fun portraitTabletUsesTabletLayoutWithoutTelevisionSizing()"
        test_case = '''    @Test
    fun landscapePhoneDoesNotBecomeTabletOrRail() {
        val device = classifyDeviceClass(
            isTelevisionDevice = false,
            smallestWidthDp = 411,
            widthDp = 891,
        )
        val window = classifyWindowWidth(891)

        assertEquals(HulkDeviceClass.MOBILE, device)
        assertEquals(HulkWindowWidthClass.EXPANDED, window)
        assertEquals(HulkNavigationType.TOP_BAR, selectNavigationType(device, window))
    }

'''
        tests = replace_once(tests, marker, test_case + marker, "adaptive landscape regression test")
        write(test_path, tests)
    else:
        print("PASS: adaptive landscape regression test already present")


def patch_hulk_components() -> None:
    path, text = read("app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt")
    text = replace_once(
        text,
        "    modifier: Modifier = Modifier,\n    visualTransformation: VisualTransformation = VisualTransformation.None,",
        "    modifier: Modifier = Modifier,\n    readOnly: Boolean = false,\n    visualTransformation: VisualTransformation = VisualTransformation.None,",
        "HulkTextField readOnly parameter",
    )
    text = replace_once(
        text,
        "        onValueChange = onValueChange,\n        modifier = modifier",
        "        onValueChange = onValueChange,\n        readOnly = readOnly,\n        modifier = modifier",
        "BasicTextField readOnly wiring",
    )
    old_history = '            Text("استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)'
    new_history = '''            Text(
                "استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}",
                color = colors.goldBright,
                fontSize = if (adaptiveUi.isTelevision) 12.sp else 9.sp,
                lineHeight = if (adaptiveUi.isTelevision) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )'''
    text = replace_once(text, old_history, new_history, "TV continue-watching metadata sizing")
    write(path, text)


def patch_main_shell() -> None:
    path, text = read("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")
    text = ensure_import(text, "import androidx.compose.foundation.layout.navigationBarsPadding\n")

    start, end = function_bounds(text, "MobileNavigation")
    block = text[start:end]
    block = replace_once(
        block,
        "            .statusBarsPadding(),",
        "            .statusBarsPadding()\n            .navigationBarsPadding(),",
        "mobile navigation system-bar insets",
    )
    block = replace_once(
        block,
        "        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),",
        "        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),",
        "mobile navigation vertical padding",
    )
    block = replace_once(
        block,
        "        horizontalArrangement = Arrangement.spacedBy(5.dp),",
        "        horizontalArrangement = Arrangement.spacedBy(6.dp),",
        "mobile navigation spacing",
    )
    block = replace_once(
        block,
        "                modifier = Modifier.heightIn(min = 42.dp),",
        "                modifier = Modifier.heightIn(min = 48.dp),",
        "mobile navigation minimum target",
    )
    text = text[:start] + block + text[end:]

    old_category = "PaddingValues(horizontal = 24.dp, vertical = 4.dp)"
    if old_category in text:
        text = text.replace(old_category, "PaddingValues(horizontal = 24.dp, vertical = 8.dp)")
        print("PASS: category-row vertical padding")
    else:
        print("PASS: no legacy category-row padding marker remains")

    text, rail_count = re.subn(
        r"(state\s*=\s*state,\s*\n\s*)isTv\s*=\s*true,(\s*\n\s*navigationMemory\s*=\s*navigationMemory,)",
        r"\1isTv = isTv,\2",
        text,
        count=1,
    )
    print(f"PASS: canonical rail device-class corrections={rail_count}")

    home_start, home_end = function_bounds(text, "CinemaHomeScreen")
    home = text[home_start:home_end]
    if "compatibilityTvBottomPadding" not in home:
        home = replace_once(
            home,
            "        modifier = Modifier.fillMaxSize(),",
            "        modifier = Modifier\n            .fillMaxSize()\n            .padding(bottom = if (isTv) 32.dp else 0.dp), // compatibilityTvBottomPadding",
            "TV home bottom safe area",
        )
    text = text[:home_start] + home + text[home_end:]

    search_start, search_end = function_bounds(text, "TvSearchField")
    replacement = '''@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    isTv: Boolean,
    hasResults: Boolean,
    fieldRequester: FocusRequester,
    firstResultRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var tvSearchEditing by remember { mutableStateOf(false) }
    val moveToResults: () -> Boolean = {
        if (!isTv || !hasResults) {
            false
        } else {
            tvSearchEditing = false
            keyboardController?.hide()
            runCatching { firstResultRequester.requestFocus() }.isSuccess
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(140L)
            runCatching { fieldRequester.requestFocus() }
        }
    }
    LaunchedEffect(isTv, tvSearchEditing) {
        if (isTv) {
            if (tvSearchEditing) keyboardController?.show() else keyboardController?.hide()
        }
    }

    val tvModifier = if (isTv) {
        Modifier
            .focusRequester(fieldRequester)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    tvSearchEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!tvSearchEditing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    tvSearchEditing = true
                    true
                } else {
                    when (tvSearchFocusAction(true, event.type, event.key, hasResults, imeVisible)) {
                        TvSearchFocusAction.MOVE_TO_RESULTS -> moveToResults()
                        TvSearchFocusAction.DISMISS_KEYBOARD -> {
                            tvSearchEditing = false
                            keyboardController?.hide()
                            true
                        }
                        TvSearchFocusAction.NONE -> false
                    }
                }
            }
    } else {
        Modifier
    }

    HulkTextField(
        value = value,
        onValueChange = onValueChange,
        label = "ابحث بالاسم او السنة او النوع…",
        modifier = modifier.then(tvModifier),
        readOnly = isTv && !tvSearchEditing,
        keyboardOptions = if (isTv) {
            KeyboardOptions(imeAction = ImeAction.Search)
        } else {
            KeyboardOptions.Default
        },
        keyboardActions = if (isTv) {
            KeyboardActions(onSearch = { moveToResults() })
        } else {
            KeyboardActions.Default
        },
    )
}'''
    current_search = text[search_start:search_end]
    if "readOnly = isTv && !tvSearchEditing" in current_search:
        print("PASS: canonical TV Search edit mode already applied")
    else:
        text = text[:search_start] + replacement + text[search_end:]
        print("PASS: canonical TV Search edit mode")

    text = replace_once(
        text,
        "Modifier.width(if (isTv) 238.dp else 190.dp).restoreFocus(restore, targetRequester)",
        "Modifier.width(if (isTv) 214.dp else 190.dp).restoreFocus(restore, targetRequester)",
        "TV history-card safe width",
    )
    text = replace_once(
        text,
        "        contentPadding = PaddingValues(if (isTv) 27.dp else 15.dp),",
        """        contentPadding = PaddingValues(
            start = if (isTv) 27.dp else 15.dp,
            top = if (isTv) 36.dp else 15.dp,
            end = if (isTv) 27.dp else 15.dp,
            bottom = if (isTv) 32.dp else 15.dp,
        ),""",
        "TV settings safe-area padding",
    )
    text = replace_once(
        text,
        "            .height(if (isTv) 236.dp else 220.dp)",
        "            .height(if (isTv) 220.dp else 220.dp)",
        "TV download-card height",
    )
    write(path, text)


def patch_prepare_project() -> None:
    path = ROOT / "qa/compatibility/prepare-project.sh"
    content = '''#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT="${1:-$REPO_ROOT/project}"

if [[ -e "$OUTPUT" ]]; then
  echo "Refusing to overwrite existing output: $OUTPUT" >&2
  exit 2
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/hulk-canonical-lab-XXXXXX")"
cleanup() {
  rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$WORK_DIR/project"
cp -a "$REPO_ROOT/app" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/gradle" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/gradlew" "$REPO_ROOT/gradlew.bat" "$WORK_DIR/project/"
cp -a "$REPO_ROOT/build.gradle.kts" "$REPO_ROOT/settings.gradle.kts" "$REPO_ROOT/gradle.properties" "$WORK_DIR/project/"
chmod +x "$WORK_DIR/project/gradlew"

rm -rf "$WORK_DIR/project/app/build" "$WORK_DIR/project/.gradle" "$WORK_DIR/project/build"
python3 "$REPO_ROOT/qa/compatibility/prepare-harness.py" "$WORK_DIR/project"

mkdir -p "$(dirname "$OUTPUT")"
mv "$WORK_DIR/project" "$OUTPUT"
echo "PASS: prepared canonical HULK SA source with debug-only compatibility harness at $OUTPUT"
'''
    path.write_text(content, encoding="utf-8")
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print("PASS: Compatibility Lab now prepares canonical checkout")


def add_path_triggers(text: str) -> str:
    trigger = "      - 'qa/compatibility/**'\n"
    addition = """      - 'qa/compatibility/**'
      - 'app/**'
      - 'gradle/**'
      - 'gradlew'
      - 'gradlew.bat'
      - 'build.gradle*'
      - 'settings.gradle*'
      - 'gradle.properties'
"""
    if "      - 'app/**'\n" not in text:
        count = text.count(trigger)
        if count != 2:
            raise SystemExit(f"Compatibility workflow path trigger count={count}")
        text = text.replace(trigger, addition)
        print("PASS: Compatibility Lab watches canonical source paths")
    return text


def patch_workflows() -> None:
    path, text = read(".github/workflows/compatibility-lab.yml")
    text = add_path_triggers(text)
    text = text.replace("Reconstruct current source and inject debug harness", "Prepare canonical source and inject debug harness")
    text = text.replace("          gradle --no-daemon --console=plain \\", "          ./gradlew --no-daemon --console=plain \\")
    text = re.sub(r"-PHULK_PORTAL_URL=[^\s\\]+", "-PHULK_PORTAL_URL=https://example.invalid", text)
    write(path, text)
    print("PASS: Compatibility workflow uses Wrapper and non-production fixture configuration")

    snapshot_path, snapshot = read(".github/workflows/generated-source-snapshot.yml")
    if "      - 'app/**'\n" not in snapshot:
        marker = "      - 'qa/compatibility/prepare-project.sh'\n"
        addition = marker + "      - 'app/**'\n      - 'gradle/**'\n      - 'gradlew'\n      - 'build.gradle*'\n      - 'settings.gradle*'\n      - 'gradle.properties'\n"
        snapshot = replace_once(snapshot, marker, addition, "Generated Source Snapshot canonical path triggers")
    snapshot = snapshot.replace("Reconstruct generated UI source", "Prepare canonical UI source")
    snapshot = snapshot.replace("Reconstruct current source", "Prepare canonical current source")
    write(snapshot_path, snapshot)

    signing_path, signing = read(".github/workflows/signed-release-qualification.yml")
    signing = replace_once(signing, "        default: '61'", "        default: '62'", "signed qualification versionCode default")
    signing = replace_once(signing, "        default: '0.9.3.17'", "        default: '0.9.3.18'", "signed qualification versionName default")
    write(signing_path, signing)


def update_manifest() -> None:
    include_roots = [ROOT / "app", ROOT / "gradle"]
    files: list[Path] = []
    for include_root in include_roots:
        for path in include_root.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(ROOT)
            if any(part in {"build", ".gradle"} for part in relative.parts):
                continue
            files.append(path)
    for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"):
        files.append(ROOT / name)
    unique = sorted(set(files), key=lambda item: item.relative_to(ROOT).as_posix())
    lines = []
    for path in unique:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {path.relative_to(ROOT).as_posix()}")
    manifest = ROOT / "qa/canonical/canonical-source.sha256"
    manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"PASS: canonical manifest updated for {len(lines)} files")


def patch_canonical_workflow() -> None:
    path, text = read(".github/workflows/canonical-build.yml")
    marker = "      - name: Debug and unit tests\n"
    verification = '''      - name: Verify canonical source manifest
        run: |
          set -euo pipefail
          sha256sum -c qa/canonical/canonical-source.sha256

'''
    if "Verify canonical source manifest" not in text:
        text = replace_once(text, marker, verification + marker, "canonical source manifest gate")
    write(path, text)


def patch_evidence_docs() -> None:
    readme = ROOT / "qa/canonical/README.md"
    readme.write_text(
        """# Canonical source evidence

- `v0.9.3.17-baseline.sha256` is the historical reconstruction baseline accepted by PR #22.
- `canonical-source.sha256` records the current direct Gradle project, including source added after PR #22.
- `sync-v09318.py` is the auditable one-time migration that ports the verified v0.9.3.18 responsive fixes into the canonical source without replacing later canonical work.
- Canonical CI verifies this manifest and builds directly from checkout.
- Compatibility Lab also copies the canonical checkout and injects only `app/src/debug` fixtures.
- `qa/compatibility/prepare-reconstructed-project.sh` preserves the historical ZIP + patch reconstruction path for audit/recovery; it is no longer the active product source.
""",
        encoding="utf-8",
    )
    parity = ROOT / "docs/project-audit/CANONICAL-SOURCE-PARITY.md"
    parity.write_text(
        """# HULK SA Android — Canonical source governance

## Current authority

The direct Gradle project at repository root is the product source of truth. PR #22 initially materialized v0.9.3.17 and proved byte-for-byte parity with the historical reconstruction. PRs #23–#45 then intentionally evolved the canonical source with signing safeguards, adaptive fixes, recommendation-cache fixes, and durable downloads.

The historical reconstruction chain did not receive those canonical-only changes. Compatibility Lab therefore became split from the actual product when PRs #50 and #51 modified reconstruction patches only.

## v0.9.3.18 reconciliation

This reconciliation does not replace the canonical project with reconstructed files. It ports only the verified responsive changes into the current canonical source:

- versionName `0.9.3.18`, versionCode `62`;
- phone-landscape device classification and top navigation;
- mobile navigation insets/targets;
- TV safe-area adjustments;
- explicit Android TV Search navigation/edit mode.

All canonical work merged after PR #22 remains in place, including durable downloads and signing qualification infrastructure.

## Compatibility authority

`qa/compatibility/prepare-project.sh` now copies the canonical checkout and injects debug-only fixtures. The previous ZIP + patch pipeline remains available as `prepare-reconstructed-project.sh` for historical audit and recovery, but it is not the active product source.

## Required evidence

- canonical manifest verification;
- clean, lint, unit, debug and release/R8 builds from checkout;
- ABI verification for APK/AAB;
- Compatibility Lab on the canonical application across all nine profiles;
- artifact review, not workflow color alone.

Reconstruction history must remain until a separate explicit governance decision; this reconciliation does not delete it.
""",
        encoding="utf-8",
    )
    print("PASS: canonical governance evidence updated")


patch_build_version()
patch_adaptive_classifier()
patch_hulk_components()
patch_main_shell()
patch_prepare_project()
patch_workflows()
patch_canonical_workflow()
patch_evidence_docs()
update_manifest()
print("PASS: canonical v0.9.3.18 synchronization complete")

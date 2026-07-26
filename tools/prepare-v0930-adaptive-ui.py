#!/usr/bin/env python3
"""Apply HULK SA v0.9.3.1 adaptive UI and responsive mobile-polish changes."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        fail(f"source marker not found for {label}")
    return text.replace(old, new, 1)


def update(path: Path, transforms: list[tuple[str, str, str]]) -> None:
    text = path.read_text(encoding="utf-8")
    for old, new, label in transforms:
        text = replace_once(text, old, new, label)
    path.write_text(text, encoding="utf-8")


def install_template(templates: Path, name: str, destination: Path, marker: str) -> None:
    template = templates / name
    if not template.is_file():
        fail(f"adaptive source template missing: {template}")
    if destination.exists() and marker not in destination.read_text(encoding="utf-8"):
        fail(f"destination source marker missing: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(template.read_text(encoding="utf-8"), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    root = args.project.resolve()
    if not (root / "settings.gradle.kts").is_file():
        fail(f"not an Android project: {root}")

    repo_root = Path(__file__).resolve().parents[1]
    templates = repo_root / "patches/v0.9.3.0-adaptive-source"

    build = root / "app/build.gradle.kts"
    update(build, [
        ("versionCode = 43", "versionCode = 45", "versionCode"),
        ('versionName = "0.9.2.0"', 'versionName = "0.9.3.1"', "versionName"),
        ("// Phase 2: ship one universal APK with only qualified ABIs.", "// Phase 3.1: preserve qualified ABIs while polishing responsive mobile UI.", "phase comment"),
    ])

    install_template(templates, "MainActivity.kt", root / "app/src/main/java/sa/hulksa/player/MainActivity.kt", "class MainActivity")
    install_template(templates, "HulkApp.kt", root / "app/src/main/java/sa/hulksa/player/ui/HulkApp.kt", "fun HulkApp")
    install_template(templates, "AdaptiveUi.kt", root / "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt", "package sa.hulksa.player.ui.adaptive")
    install_template(templates, "AdaptiveUiClassifierTest.kt", root / "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt", "package sa.hulksa.player.ui.adaptive")

    shell = root / "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
    update(shell, [
        ("import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n", "mobile nav height import"),
        ("import androidx.compose.foundation.layout.size\n", "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n", "mobile nav status import"),
        ("import sa.hulksa.player.ui.components.BrandLogo\n", "import sa.hulksa.player.ui.adaptive.HulkNavigationType\nimport sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.components.BrandLogo\n", "MainShell adaptive imports"),
        ("    val colors = LocalHulkColors.current\n    val context = LocalContext.current\n    val toggleFavoriteWithFeedback", "    val colors = LocalHulkColors.current\n    val context = LocalContext.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    val useNavigationRail = adaptiveUi.navigationType == HulkNavigationType.RAIL\n    val toggleFavoriteWithFeedback", "MainShell adaptive state"),
        ("        if (isTv) {\n            Row(Modifier.fillMaxSize())", "        if (useNavigationRail) {\n            Row(Modifier.fillMaxSize())", "MainShell rail selection"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val active = selected || focused\n    Row(\n", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val active = selected || showFocused\n    Row(\n", "NavigationItem adaptive focus"),
        ("                    focused -> colors.gold", "                    showFocused -> colors.gold", "NavigationItem focus background"),
        ("tint = if (focused) Color.Black else if (active)", "tint = if (showFocused) Color.Black else if (active)", "NavigationItem icon focus"),
        ("color = if (focused) Color.Black else if (active)", "color = if (showFocused) Color.Black else if (active)", "NavigationItem text focus"),
        ("""@Composable
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF090A07)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { BrandBadge(Modifier.size(45.dp)) }
        items(destinations, key = { it.destination.name }) { entry ->
            FocusButton(entry.label, { onSelect(entry.destination) }, primary = selected == entry.destination, compact = true)
        }
    }
}
""", """@Composable
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
""", "responsive mobile navigation"),
    ])

    login = root / "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
    update(login, [
        ("import androidx.compose.foundation.clickable\n", "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectTapGestures\n", "login tap import"),
        ("import androidx.compose.ui.platform.LocalUriHandler\n", "import androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.platform.LocalSoftwareKeyboardController\nimport androidx.compose.ui.platform.LocalUriHandler\n", "login keyboard imports"),
        ("import androidx.compose.ui.semantics.Role\n", "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.ui.semantics.Role\n", "login pointer import"),
        ("    val uriHandler = LocalUriHandler.current\n", "    val uriHandler = LocalUriHandler.current\n    val keyboardController = LocalSoftwareKeyboardController.current\n    val focusManager = LocalFocusManager.current\n", "login keyboard state"),
        ("    val submit = { onLogin(username, password, rememberAccount) }\n", "    val dismissKeyboard = {\n        keyboardController?.hide()\n        focusManager.clearFocus(force = true)\n    }\n    val submit = {\n        dismissKeyboard()\n        onLogin(username.trim(), password, rememberAccount)\n    }\n", "login submit keyboard dismissal"),
        ("            .imePadding(),\n", "            .pointerInput(Unit) {\n                detectTapGestures(onTap = { dismissKeyboard() })\n            }\n            .imePadding(),\n", "login outside tap dismissal"),
    ])

    player = root / "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
    update(player, [
        ("import androidx.compose.foundation.layout.fillMaxWidth\n", "import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.navigationBarsPadding\nimport androidx.compose.foundation.layout.statusBarsPadding\n", "player inset imports"),
        ("            .padding(horizontal = 24.dp, vertical = 18.dp),", "            .statusBarsPadding()\n            .padding(horizontal = 14.dp, vertical = 10.dp),", "player top safe area"),
        ("""        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .padding(horizontal = 28.dp, vertical = 22.dp),
""", """        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
""", "VOD controls safe area"),
        ("Spacer(Modifier.height(13.dp))\n        LazyRow", "Spacer(Modifier.height(8.dp))\n        LazyRow", "VOD compact controls"),
        ("""        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .padding(horizontal = 28.dp, vertical = 22.dp),
""", """        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .97f))))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
""", "live controls safe area"),
        ("Spacer(Modifier.height(12.dp))\n        LazyRow", "Spacer(Modifier.height(8.dp))\n        LazyRow", "live compact controls"),
    ])

    movie = root / "app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt"
    update(movie, [
        ("import androidx.compose.foundation.layout.size\n", "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBarsPadding\n", "movie status import"),
        ("""                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = if (isTv) 30.dp else 16.dp, vertical = if (isTv) 24.dp else 14.dp),
""", """                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                        .padding(horizontal = if (isTv) 30.dp else 12.dp, vertical = if (isTv) 24.dp else 8.dp),
""", "movie top safe area"),
        ("""                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            FocusButton(
                                if (resumePosition != null) "▶ استكمال من ${detailsFormatTime(resumePosition)}" else "▶ مشاهدة الفيلم",
                                onPlay,
                            )
                            FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false)
                        }
""", """                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FocusButton(
                                text = if (resumePosition != null) "▶ استكمال" else "▶ مشاهدة الفيلم",
                                onClick = onPlay,
                                compact = !isTv,
                                modifier = Modifier.weight(1f),
                            )
                            FocusButton(
                                text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                                onClick = onToggleFavorite,
                                primary = false,
                                compact = !isTv,
                                modifier = Modifier.weight(1f),
                            )
                        }
""", "movie action widths"),
    ])

    components = root / "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
    update(components, [
        ("import sa.hulksa.player.ui.theme.LocalHulkColors\n", "import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi\nimport sa.hulksa.player.ui.theme.LocalHulkColors\n", "component adaptive import"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = \"buttonScale\")", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = \"buttonScale\")", "FocusButton state"),
        ("primary && focused ->", "primary && showFocused ->", "FocusButton primary focus"),
        ("        focused -> Color(0xFF2A281B)", "        showFocused -> Color(0xFF2A281B)", "FocusButton background focus"),
        ("                    focused -> 2.dp", "                    showFocused -> 2.dp", "FocusButton border width"),
        ("                    focused -> colors.goldBright", "                    showFocused -> colors.goldBright", "FocusButton border color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val shape = RoundedCornerShape(13.dp)\n    val active = focused || selected", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val shape = RoundedCornerShape(13.dp)\n    val active = showFocused || selected", "NavRailButton state"),
        ("if (focused) .25f else .13f", "if (showFocused) .25f else .13f", "NavRailButton alpha"),
        ("if (focused) 2.dp else 0.dp", "if (showFocused) 2.dp else 0.dp", "NavRailButton border"),
        ("if (focused) colors.goldBright else Color.Transparent", "if (showFocused) colors.goldBright else Color.Transparent", "NavRailButton color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    var artworkFailed", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    var artworkFailed", "PosterCard adaptive state"),
        ("    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = \"posterScale\")", "    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.04f else 1f, label = \"posterScale\")", "PosterCard scale"),
        ("shadowElevation = if (focused) 14.dp.toPx() else 0f", "shadowElevation = if (showFocused) 14.dp.toPx() else 0f", "PosterCard shadow"),
        ("                if (focused) 3.dp else 0.dp", "                if (showFocused) 3.dp else 0.dp", "PosterCard border width"),
        ("                if (focused) colors.goldBright else Color.Transparent", "                if (showFocused) colors.goldBright else Color.Transparent", "PosterCard border color"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = \"historyScale\")", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = \"historyScale\")", "HistoryCard state"),
        (".border(if (focused) 3.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)", ".border(if (showFocused) 3.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)", "HistoryCard border"),
        ("    val colors = LocalHulkColors.current\n    var focused by remember { mutableStateOf(false) }\n    var remoteLongPressHandled", "    val colors = LocalHulkColors.current\n    val adaptiveUi = LocalAdaptiveUi.current\n    var focused by remember { mutableStateOf(false) }\n    var remoteLongPressHandled", "Channel item adaptive state"),
        ("    val active = focused || selected\n    val shape = RoundedCornerShape(11.dp)", "    val showFocused = focused && adaptiveUi.showFocusHighlights\n    val active = showFocused || selected\n    val shape = RoundedCornerShape(11.dp)", "Channel item active state"),
        (".border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)", ".border(if (showFocused) 2.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)", "Channel item border"),
    ])

    if not any("HomeContentSnapshot" in p.read_text(encoding="utf-8", errors="ignore") for p in (root / "app/src/main").rglob("*.kt")):
        fail("HomeContentSnapshot preservation marker missing")

    print(f"Prepared adaptive mobile source: {root}")
    print("Version: 0.9.3.1 (45)")
    print("Mobile polish: keyboard, navigation, player insets, movie actions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


adaptive = "app/src/main/java/sa/hulksa/player/ui/adaptive/AdaptiveUi.kt"
replace_once(
    adaptive,
    '''fun shouldShowKeyboardFocusIndicator(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass != HulkDeviceClass.TELEVISION && inputMode == HulkInputMode.KEYBOARD
''',
    '''fun shouldShowKeyboardFocusIndicator(
    deviceClass: HulkDeviceClass,
    inputMode: HulkInputMode,
): Boolean = deviceClass != HulkDeviceClass.TELEVISION && inputMode != HulkInputMode.TOUCH
''',
)

main_shell = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
path = Path(main_shell)
text = path.read_text(encoding="utf-8")

# HulkApp already applies safeDrawing insets to the complete non-player shell.
# A second navigationBarsPadding on the bottom bar would double the bottom inset.
text = text.replace("import androidx.compose.foundation.layout.navigationBarsPadding\n", "")
text = text.replace("            .navigationBarsPadding()\n", "")
path.write_text(text, encoding="utf-8")

replace_once(
    main_shell,
    '''import sa.hulksa.player.ui.adaptive.HulkNavigationType
''',
    '''import sa.hulksa.player.ui.adaptive.HulkNavigationType
import sa.hulksa.player.ui.adaptive.HulkOrientation
import sa.hulksa.player.ui.adaptive.HulkWindowHeightClass
''',
)
replace_once(
    main_shell,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val active = selected || showFocused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    showFocused -> colors.gold
                    selected -> colors.gold.copy(alpha = .13f)
                    else -> Color.Transparent
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp),
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val active = selected || televisionFocused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    televisionFocused -> colors.gold
                    selected -> colors.gold.copy(alpha = .13f)
                    else -> Color.Transparent
                },
            )
            .border(
                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,
                colors.goldBright,
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp),
''',
)
replace_once(
    main_shell,
    '''            tint = if (showFocused) Color.Black else if (active) colors.goldBright else colors.textMuted,
''',
    '''            tint = if (televisionFocused) Color.Black else if (active) colors.goldBright else colors.textMuted,
''',
)
replace_once(
    main_shell,
    '''                color = if (showFocused) Color.Black else if (active) colors.text else colors.textMuted,
''',
    '''                color = if (televisionFocused) Color.Black else if (active) colors.text else colors.textMuted,
''',
)
replace_once(
    main_shell,
    '''        items(destinations, key = { it.destination.name }) { entry ->
            val active = selected == entry.destination
            Column(
                modifier = Modifier
                    .width(if (compactLandscape) 56.dp else 66.dp)
                    .heightIn(min = 48.dp)
                    .testTag("mobile-bottom-nav-${entry.destination.name.lowercase(Locale.ROOT)}")
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.gold.copy(alpha = .16f) else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(entry.destination) }
''',
    '''        items(destinations, key = { it.destination.name }) { entry ->
            val active = selected == entry.destination
            var focused by remember { mutableStateOf(false) }
            val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
            Column(
                modifier = Modifier
                    .width(if (compactLandscape) 56.dp else 66.dp)
                    .heightIn(min = 48.dp)
                    .testTag("mobile-bottom-nav-${entry.destination.name.lowercase(Locale.ROOT)}")
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.gold.copy(alpha = .16f) else Color.Transparent)
                    .border(
                        if (keyboardFocused) 2.dp else 0.dp,
                        colors.goldBright,
                        RoundedCornerShape(12.dp),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(role = Role.Button) { onSelect(entry.destination) }
''',
)

components = "app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt"
replace_once(
    components,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = "buttonScale")
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.035f else 1f, label = "buttonScale")
''',
)
replace_once(
    components,
    '''        primary && showFocused -> colors.goldBright
        primary -> colors.gold
        showFocused -> Color(0xFF2A281B)
''',
    '''        primary && televisionFocused -> colors.goldBright
        primary -> colors.gold
        televisionFocused -> Color(0xFF2A281B)
''',
)
replace_once(
    components,
    '''                    showFocused -> 2.dp
                    outlined -> 1.dp
''',
    '''                    televisionFocused || keyboardFocused -> 2.dp
                    outlined -> 1.dp
''',
)
replace_once(
    components,
    '''                    showFocused -> colors.goldBright
                    outlined -> colors.gold.copy(alpha = .42f)
''',
    '''                    televisionFocused || keyboardFocused -> colors.goldBright
                    outlined -> colors.gold.copy(alpha = .42f)
''',
)
replace_once(
    components,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(13.dp)
    val active = showFocused || selected
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val shape = RoundedCornerShape(13.dp)
    val active = televisionFocused || selected
''',
)
replace_once(
    components,
    '''            .background(if (active) colors.gold.copy(alpha = if (showFocused) .25f else .13f) else Color.Transparent)
            .border(
                if (showFocused) 2.dp else 0.dp,
                if (showFocused) colors.goldBright else Color.Transparent,
''',
    '''            .background(if (active) colors.gold.copy(alpha = if (televisionFocused) .25f else .13f) else Color.Transparent)
            .border(
                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
''',
)
replace_once(
    components,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.04f else 1f, label = "posterScale")
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.04f else 1f, label = "posterScale")
''',
)
replace_once(
    components,
    '''                shadowElevation = if (showFocused) 14.dp.toPx() else 0f
''',
    '''                shadowElevation = if (televisionFocused) 14.dp.toPx() else 0f
''',
)
replace_once(
    components,
    '''                if (showFocused) 3.dp else 0.dp,
                if (focused) colors.goldBright else Color.Transparent,
''',
    '''                when {
                    televisionFocused -> 3.dp
                    keyboardFocused -> 2.dp
                    else -> 0.dp
                },
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
''',
)
replace_once(
    components,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = "historyScale")
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.035f else 1f, label = "historyScale")
''',
)
replace_once(
    components,
    '''            .border(if (showFocused) 3.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)
''',
    '''            .border(
                when {
                    televisionFocused -> 3.dp
                    keyboardFocused -> 2.dp
                    else -> 0.dp
                },
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
                shape,
            )
''',
)
replace_once(
    components,
    '''    val showFocused = focused && adaptiveUi.showFocusHighlights
    val active = showFocused || selected
''',
    '''    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val active = televisionFocused || selected
''',
)
replace_once(
    components,
    '''            .border(if (showFocused) 2.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)
''',
    '''            .border(
                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
                shape,
            )
''',
)

classifier = "app/src/test/java/sa/hulksa/player/ui/adaptive/AdaptiveUiClassifierTest.kt"
replace_once(
    classifier,
    '''        assertEquals(2, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))
''',
    '''        assertEquals(3, calculateAdaptiveGridColumns(360, 112, 12, maximumColumns = 6))
''',
)
replace_once(
    classifier,
    '''        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.KEYBOARD))
''',
    '''        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.KEYBOARD))
        assertTrue(shouldShowKeyboardFocusIndicator(HulkDeviceClass.MOBILE, HulkInputMode.REMOTE))
''',
)

for file_path, required in {
    adaptive: ("inputMode != HulkInputMode.TOUCH",),
    main_shell: ("keyboardFocused", 'testTag("mobile-bottom-navigation")'),
    components: ("televisionFocused", "keyboardFocused"),
    classifier: ("assertEquals(3, calculateAdaptiveGridColumns",),
}.items():
    data = Path(file_path).read_text(encoding="utf-8")
    for marker in required:
        if marker not in data:
            raise SystemExit(f"{file_path}: missing required marker {marker}")

if "navigationBarsPadding" in Path(main_shell).read_text(encoding="utf-8"):
    raise SystemExit("Bottom navigation still applies a duplicate navigation-bar inset")

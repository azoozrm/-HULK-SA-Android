#!/usr/bin/env python3
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

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"

# Search results always start from item zero instead of a stale remembered position.
rep(
    main,
    '''    val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
        .coerceIn(0, content.lastIndex.coerceAtLeast(0))
''',
    '''    val targetIndex = if (destination == MainDestination.SEARCH) {
        0
    } else {
        (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
            .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    }
''',
    "search initial index",
)
rep(
    main,
    '''    LaunchedEffect(content.map { "${it.type}:${it.id}" }, remembered.itemKey, destination) {
        if (destination != MainDestination.SEARCH && content.isNotEmpty()) {
''',
    '''    LaunchedEffect(content.map { "${it.type}:${it.id}" }, remembered.itemKey, destination, restoreFocusedCard) {
        if (destination == MainDestination.SEARCH) {
            if (content.isNotEmpty()) gridState.scrollToItem(0)
            navigationMemory.save(destination, content.firstOrNull()?.let { "${it.type}:${it.id}" }.orEmpty(), 0)
        } else if (restoreFocusedCard && content.isNotEmpty()) {
''',
    "search result reset and focus guard",
)

# Do not let result recomposition steal focus from the keyboard in Movies/Series search.
rep(
    main,
    '''    onToggleFavorite: (ContentItem) -> Unit,
) {
    val remembered = navigationMemory.position(destination)
''',
    '''    onToggleFavorite: (ContentItem) -> Unit,
    restoreFocusedCard: Boolean = true,
) {
    val remembered = navigationMemory.position(destination)
''',
    "content grid focus guard parameter",
)
rep(
    main,
    '''                ContentGrid(visible, isTv, destination, navigationMemory, isFavorite, onOpen, onToggleFavorite)
''',
    '''                ContentGrid(
                    visible, isTv, destination, navigationMemory, isFavorite, onOpen, onToggleFavorite,
                    restoreFocusedCard = state.searchQuery.isBlank(),
                )
''',
    "catalog search keyboard focus",
)

# Do not let live-channel result updates steal focus after the first typed character.
rep(
    main,
    '''        if (remembered.itemKey.isNotBlank() && visible.isNotEmpty()) {
            listState.scrollToItem(rememberedIndex)
            delay(180)
            runCatching { channelRequester.requestFocus() }
        }
''',
    '''        if (state.searchQuery.isBlank() && remembered.itemKey.isNotBlank() && visible.isNotEmpty()) {
            listState.scrollToItem(rememberedIndex)
            delay(180)
            runCatching { channelRequester.requestFocus() }
        }
''',
    "live search keyboard focus",
)

# Give Xiaomi overscan extra room around the collapsed navigation rail logo.
rep(
    main,
    '''            .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 18.dp),
''',
    '''            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 18.dp),
''',
    "rail top safe area",
)
rep(
    main,
    '''        BrandBadge(Modifier.size(if (expanded) 82.dp else 58.dp))
''',
    '''        BrandBadge(Modifier.size(if (expanded) 78.dp else 52.dp))
''',
    "collapsed logo safe size",
)

# Add right-side breathing room before the first episode card.
series = "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt"
rep(
    series,
    '''            horizontalArrangement = Arrangement.spacedBy(12.dp),
''',
    '''            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 20.dp),
''',
    "episodes right padding",
)

# Player safe areas cover live, movies and series controls, logo, back action and hints.
player = "app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt"
rep(
    player,
    '''            .padding(horizontal = 14.dp, vertical = 10.dp),
''',
    '''            .padding(horizontal = 24.dp, vertical = 16.dp),
''',
    "player top safe area",
)
rep(
    player,
    '''            .padding(horizontal = 12.dp, vertical = 10.dp),
''',
    '''            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
''',
    "vod controls safe area",
)
rep(
    player,
    '''            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 18.dp),
''',
    '''            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
''',
    "live controls safe area",
)

# Hide the login keyboard when focus reaches the login button, without clearing button focus.
login = "app/src/main/java/sa/hulksa/player/ui/screens/LoginScreen.kt"
rep(
    login,
    '''private fun LoginPanel(
''',
    '''private fun LoginPanel(
''',
    "login panel marker",
)
rep(
    login,
    '''    val colors = LocalHulkColors.current
    val panelShape = RoundedCornerShape(26.dp)
''',
    '''    val colors = LocalHulkColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val panelShape = RoundedCornerShape(26.dp)
''',
    "login panel keyboard controller",
)
rep(
    login,
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
''',
    '''            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
''',
    "hide keyboard on login button focus",
)

print("Prepared v0.9.3.15 complete Xiaomi, keyboard, search and safe-area fixes")

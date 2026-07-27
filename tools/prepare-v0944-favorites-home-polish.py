#!/usr/bin/env python3
# v0.9.3.14: symmetric category movement, correct favorites focus/feedback,
# and stable home recommendation rows while toggling favorites.
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


rep("app/build.gradle.kts", "versionCode = 57", "versionCode = 58", "versionCode")
rep("app/build.gradle.kts", 'versionName = "0.9.3.13"', 'versionName = "0.9.3.14"', "versionName")

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"

# Keep category movement identical in both directions and avoid an asymmetric animation tail.
rep(
    main,
    "listState.animateScrollToItem(targetIndex.coerceAtLeast(0))",
    "listState.scrollToItem(targetIndex.coerceAtLeast(0))",
    "catalog/live symmetric category follow",
    count=2,
)

# Favorite changes must not invalidate and rebuild all personalized home rows.
rep(
    main,
    """            homeLiveCatalog === liveCatalog &&
            homeHistory === state.history &&
            homeFavorites === state.favorites
""",
    """            homeLiveCatalog === liveCatalog &&
            homeHistory === state.history
""",
    "home cache favorite invalidation",
)
rep(
    main,
    """        val pool = (movies + series).asSequence()
            .filterNot { isFavorite(it) || "${it.type}:${it.id}" in watchedKeys }
""",
    """        val pool = (movies + series).asSequence()
            .filterNot { "${it.type}:${it.id}" in watchedKeys }
""",
    "home recommendation favorite removal",
)
rep(
    main,
    """            homeHistory = state.history
            homeFavorites = state.favorites
            cachedHome = snapshot
""",
    """            homeHistory = state.history
            cachedHome = snapshot
""",
    "home cache favorite assignment",
)

# Freeze the feedback data before state mutation so the toast always names the pressed card.
rep(
    main,
    """    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        val wasFavorite = isFavorite(item)
        onToggleFavorite(item)
        Toast.makeText(
            context,
            if (wasFavorite) "تمت ازالة ${item.name} من المفضلة" else "تمت اضافة ${item.name} الى المفضلة",
""",
    """    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        val wasFavorite = isFavorite(item)
        val feedbackName = item.name
        onToggleFavorite(item)
        Toast.makeText(
            context,
            if (wasFavorite) "تمت ازالة $feedbackName من المفضلة" else "تمت اضافة $feedbackName الى المفضلة",
""",
    "favorite feedback snapshot",
)

# When a favorite disappears, retarget focus to the nearest surviving card instead of the removed node.
rep(
    main,
    """    val remembered = navigationMemory.position(destination)
    val targetIndex = remembered.itemIndex.coerceIn(0, content.lastIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
""",
    """    val remembered = navigationMemory.position(destination)
    val rememberedKeyIndex = content.indexOfFirst { "${it.type}:${it.id}" == remembered.itemKey }
    val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
        .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    val targetKey = content.getOrNull(targetIndex)?.let { "${it.type}:${it.id}" }.orEmpty()
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
""",
    "favorites nearest surviving target",
)
rep(
    main,
    """    LaunchedEffect(content, remembered.itemKey, destination) {
        if (destination != MainDestination.SEARCH && remembered.itemKey.isNotBlank() && content.isNotEmpty()) {
            gridState.scrollToItem(targetIndex)
            delay(180)
            runCatching { targetRequester.requestFocus() }
        }
    }
""",
    """    LaunchedEffect(content.map { "${it.type}:${it.id}" }, remembered.itemKey, destination) {
        if (destination != MainDestination.SEARCH && content.isNotEmpty()) {
            if (destination == MainDestination.FAVORITES && targetKey.isNotBlank() && targetKey != remembered.itemKey) {
                navigationMemory.save(destination, targetKey, targetIndex)
            }
            gridState.scrollToItem(targetIndex)
            delay(90)
            runCatching { targetRequester.requestFocus() }
        }
    }
""",
    "favorites focus retarget",
)
rep(
    main,
    """            val restore = remembered.itemKey == key || (remembered.itemKey.isBlank() && index == targetIndex)
""",
    """            val restore = remembered.itemKey == key || index == targetIndex
""",
    "favorites target requester attachment",
)

print("Prepared v0.9.3.14 favorites and home polish")

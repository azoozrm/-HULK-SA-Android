#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 59','versionCode = 60','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.15"','versionName = "0.9.3.16"','versionName')
main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
rep(main,'import androidx.compose.foundation.layout.padding\n','import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.offset\n','offset import')
rep(main,'''    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        val wasFavorite = isFavorite(item)
        val feedbackName = item.name
        onToggleFavorite(item)
        Toast.makeText(
            context,
            if (wasFavorite) "تمت ازالة $feedbackName من المفضلة" else "تمت اضافة $feedbackName الى المفضلة",
            Toast.LENGTH_SHORT,
        ).show()
    }
''','''    val favoriteOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var favoriteActionLocked by remember { mutableStateOf(false) }
    val favoriteScope = rememberCoroutineScope()
    LaunchedEffect(state.favorites) {
        favoriteOverrides.entries.toList().forEach { (key, optimisticValue) ->
            if ((key in state.favorites) == optimisticValue) favoriteOverrides.remove(key)
        }
    }
    val resolvedIsFavorite: (ContentItem) -> Boolean = { item ->
        val key = "${item.type.name}:${item.id}"
        favoriteOverrides[key] ?: isFavorite(item)
    }
    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { pressedItem ->
        if (!favoriteActionLocked) {
            favoriteActionLocked = true
            val pressedKey = "${pressedItem.type.name}:${pressedItem.id}"
            val pressedTitle = pressedItem.name
            val wasFavorite = resolvedIsFavorite(pressedItem)
            val optimisticValue = !wasFavorite
            favoriteOverrides[pressedKey] = optimisticValue
            onToggleFavorite(pressedItem)
            Toast.makeText(
                context,
                if (wasFavorite) "تمت ازالة $pressedTitle من المفضلة" else "تمت اضافة $pressedTitle الى المفضلة",
                Toast.LENGTH_SHORT,
            ).show()
            favoriteScope.launch {
                delay(1_600L)
                favoriteActionLocked = false
                delay(3_400L)
                if (favoriteOverrides[pressedKey] == optimisticValue) favoriteOverrides.remove(pressedKey)
            }
        }
    }
''','favorite state and pressed snapshot')
rep(main,'''                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = isFavorite,
''','''                        isTv = true,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
''','TV favorite resolver')
rep(main,'''                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = isFavorite,
''','''                        isTv = false,
                        navigationMemory = navigationMemory,
                        isFavorite = resolvedIsFavorite,
''','mobile favorite resolver')
rep(main,'    val railWidth by animateDpAsState(if (expanded) 202.dp else 78.dp, label = "railWidth")\n','    val railWidth by animateDpAsState(if (expanded) 202.dp else 90.dp, label = "railWidth")\n','rail width')
rep(main,'        BrandBadge(Modifier.size(if (expanded) 78.dp else 52.dp))\n','''        BrandBadge(
            Modifier
                .size(if (expanded) 76.dp else 42.dp)
                .offset(x = if (expanded) 0.dp else (-8).dp),
        )
''','rail logo inset')
rep(main,'''                val targetIndex = to + 3
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
''','''                val targetIndex = to + 3
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
''','catalog symmetric follow')
rep(main,'''                val targetIndex = to + 1
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0))
''','''                val targetIndex = to + 1
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
''','live symmetric follow')
print('Prepared v0.9.3.16 main fixes')

#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 60','versionCode = 61','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.16"','versionName = "0.9.3.17"','versionName')
main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
rep(main,'''.size(if (expanded) 76.dp else 42.dp)
                .offset(x = if (expanded) 0.dp else (-8).dp),''','''.size(if (expanded) 76.dp else 50.dp)
                .offset(x = if (expanded) 0.dp else (-4).dp),''','collapsed logo balance')
rep(main,'''    val catalog = state.catalogs[ContentType.LIVE]
    LaunchedEffect(catalog?.categories, state.selectedCategoryId) {
        if (state.selectedCategoryId == null) catalog?.categories?.firstOrNull()?.id?.let(onSelectCategory)
    }
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {''','''    val catalog = state.catalogs[ContentType.LIVE]
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {''','live all selection')
rep(main,'''        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }

    fun move(id: String, direction: Int) {''','''        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }

    fun move(id: String, direction: Int) {''','catalog safe selected anchor',1)
rep(main,'''            scope.launch {
                val targetIndex = to + 3
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
            }''','''            scope.launch {
                delay(40L)
                val targetIndex = to + 3
                val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(anchorIndex)
            }''','catalog safe move anchor')
rep(main,'''        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }''','''        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
    ) {
        item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }''','catalog safe edge padding',1)
rep(main,'''    LaunchedEffect(selectedId) {
        val targetIndex = when (selectedId) {
            FAVORITES_CATEGORY_ID -> 0
            null -> 0
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex.coerceAtLeast(0))
        }
    }''','''    LaunchedEffect(selectedId) {
        val targetIndex = when (selectedId) {
            null -> 0
            FAVORITES_CATEGORY_ID -> 1
            else -> ordered.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.plus(2)
        }
        if (targetIndex != null) {
            val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
    }''','live all and safe selected anchor')
rep(main,'''            scope.launch {
                val targetIndex = to + 1
                listState.scrollToItem(targetIndex.coerceAtLeast(0))
            }''','''            scope.launch {
                delay(40L)
                val targetIndex = to + 2
                val anchorIndex = (targetIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(anchorIndex)
            }''','live safe move anchor')
rep(main,'''        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        item {
            FocusButton(
                "★ المفضلة",''','''        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
    ) {
        item {
            FocusButton(
                "الكل",
                { onSelect(null) },
                primary = selectedId == null,
                compact = true,
            )
        }
        item {
            FocusButton(
                "★ المفضلة",''','live all button and edge padding')
print('Prepared v0.9.3.17 navigation and category fixes')

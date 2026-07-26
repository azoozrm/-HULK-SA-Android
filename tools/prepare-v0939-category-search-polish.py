#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path; s=p.read_text(encoding='utf-8')
    if new in s: return
    if old not in s: raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 52','versionCode = 53','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.8"','versionName = "0.9.3.9"','versionName')
main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'

rep(main,
'''        ReorderableCatalogCategoryBar(type, catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory)
''',
'''        ReorderableCatalogCategoryBar(type, catalog?.categories.orEmpty(), ordered, state.selectedCategoryId, onSelectCategory)
''','catalog category artwork input')

rep(main,
'''private fun ReorderableCatalogCategoryBar(
    type: ContentType,
    categories: List<Category>,
    selectedId: String?,
''',
'''private fun ReorderableCatalogCategoryBar(
    type: ContentType,
    categories: List<Category>,
    items: List<ContentItem>,
    selectedId: String?,
''','catalog items parameter')

rep(main,
'''    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    fun move(id: String, direction: Int) {
''',
'''    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val artworkByCategory = remember(items) {
        items.filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, content) -> content.first() }
    }
    fun move(id: String, direction: Int) {
''','catalog artwork map')

rep(main,
'''            scope.launch { listState.scrollToItem((to + 3).coerceAtLeast(0)) }
''',
'''            scope.launch { listState.animateScrollToItem((to + 2).coerceAtLeast(0)) }
''','catalog smooth list follow')

rep(main,
'''                representative = null,
''',
'''                representative = artworkByCategory[category.id],
''','restore catalog icons')

rep(main,
'''    LaunchedEffect(content, remembered.itemKey) {
        if (remembered.itemKey.isNotBlank() && content.isNotEmpty()) {
''',
'''    LaunchedEffect(content, remembered.itemKey, destination) {
        if (destination != MainDestination.SEARCH && remembered.itemKey.isNotBlank() && content.isNotEmpty()) {
''','keep search keyboard focus')

rep(main,
'''                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDragEnd = { dragAccumulator = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        while (dragAccumulator >= 34f) { onMoveRight(); dragAccumulator -= 34f }
                        while (dragAccumulator <= -34f) { onMoveLeft(); dragAccumulator += 34f }
                    }
''',
'''                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDragEnd = {
                            when {
                                dragAccumulator >= 48f -> onMoveRight()
                                dragAccumulator <= -48f -> onMoveLeft()
                            }
                            dragAccumulator = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
''','single-step touch reorder')

rep(main,
'''                    moving && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        onMoveLeft(); true
                    }
                    moving && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        onMoveRight(); true
                    }
''',
'''                    moving && event.type == KeyEventType.KeyUp && event.key == Key.DirectionLeft -> {
                        onMoveLeft(); true
                    }
                    moving && event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight -> {
                        onMoveRight(); true
                    }
                    moving && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> true
''','single-step remote reorder')

print('Prepared v0.9.3.9 category and search polish')

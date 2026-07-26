#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path; s=p.read_text(encoding='utf-8')
    if new in s: return
    if old not in s: raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 50','versionCode = 51','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.6"','versionName = "0.9.3.7"','versionName')
main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'

rep(main,
'''        CategoryBar(catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory, showFavorites = true, showContinue = true)
        FavoriteHint(isTv)
''',
'''        ReorderableCatalogCategoryBar(type, catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory)
        CatalogInteractionHints(isTv)
''','catalog reorder call')

rep(main,
'''                                modifier = Modifier.restoreFocus(restore, channelRequester),
''',
'''                                modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                    left = playRequester
                                    right = channelRequester
                                    up = channelRequester
                                    down = channelRequester
                                },
''','live channel focus route')

rep(main,
'''            scope.launch {
                listState.animateScrollToItem((to + 1).coerceAtLeast(0))
            }
''',
'''            scope.launch {
                listState.scrollToItem((to + 1).coerceAtLeast(0))
            }
''','fast category scroll')

rep(main,
'''                        while (dragAccumulator >= 18f) { onMoveRight(); dragAccumulator -= 18f }
                        while (dragAccumulator <= -18f) { onMoveLeft(); dragAccumulator += 18f }
''',
'''                        while (dragAccumulator >= 34f) { onMoveRight(); dragAccumulator -= 34f }
                        while (dragAccumulator <= -34f) { onMoveLeft(); dragAccumulator += 34f }
''','stable drag threshold')

marker='''@Composable
private fun ReorderableLiveCategoryBar(
'''
insert='''@Composable
private fun ReorderableCatalogCategoryBar(
    type: ContentType,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(type) { context.getSharedPreferences("catalog_category_order_${type.name}", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories, type) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
            scope.launch { listState.scrollToItem((to + 3).coerceAtLeast(0)) }
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    ) {
        item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }
        item { FocusButton("★ المفضلة", { onSelect(FAVORITES_CATEGORY_ID) }, primary = selectedId == FAVORITES_CATEGORY_ID, compact = true) }
        item { FocusButton("▶ استكمال اخر مشاهدة", { onSelect(CONTINUE_CATEGORY_ID) }, primary = selectedId == CONTINUE_CATEGORY_ID, compact = true) }
        items(ordered, key = Category::id) { category ->
            LiveCategoryChip(
                category = category,
                representative = null,
                selected = selectedId == category.id,
                moving = moving == category.id,
                onClick = { if (moving == category.id) moving = null else onSelect(category.id) },
                onLongClick = { moving = category.id },
                onMoveLeft = { move(category.id, 1) },
                onMoveRight = { move(category.id, -1) },
            )
        }
    }
}

@Composable
private fun CatalogInteractionHints(isTv: Boolean) {
    val colors = LocalHulkColors.current
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            if (isTv) "ترتيب الفئات: اضغط مطولا OK، حرك بالاسهم، ثم اضغط OK للحفظ" else "لترتيب الفئات: اضغط مطولا على الفئة، اسحبها يمينا او يسارا، ثم اضغط عليها للحفظ",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
        Text(
            if (isTv) "المفضلة: اضغط مطولا OK فوق العنصر" else "المفضلة: اضغط مطولا على العنصر",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
    }
}

'''+marker
rep(main,marker,insert,'catalog reorder functions')

player='app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
rep(player,
'''            !controlsVisible -> revealControls()
            else -> saveAndBack()
''',
'''            controlsVisible -> controlsVisible = false
            else -> saveAndBack()
''','player back behavior')

print('Prepared v0.9.3.7 TCL navigation and player polish')

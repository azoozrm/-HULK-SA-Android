from pathlib import Path
import sys

root = Path(sys.argv[1])
main = root / 'app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
player = root / 'app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
gradle = root / 'app/build.gradle.kts'

M = main.read_text()
P = player.read_text()
G = gradle.read_text()

G = G.replace('versionCode = 25', 'versionCode = 26')
G = G.replace('versionName = "0.9.1.3"', 'versionName = "0.9.1.4"')

M = M.replace('.height(if (isTv) 258.dp else 244.dp)', '.height(if (isTv) 232.dp else 222.dp)', 1)
M = M.replace('.width(if (isTv) 105.dp else 86.dp)', '.width(if (isTv) 94.dp else 82.dp)', 1)
M = M.replace('maxLines = 2,\n                overflow = TextOverflow.Ellipsis,\n                lineHeight = if (isTv) 18.sp else 16.sp,',
              'maxLines = 1,\n                overflow = TextOverflow.Ellipsis,\n                lineHeight = if (isTv) 17.sp else 15.sp,', 1)
old_spacer = '''            Spacer(Modifier.weight(1f))
            DownloadProgress(item)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {'''
new_spacer = '''            Spacer(Modifier.height(4.dp))
            DownloadProgress(item)
            Spacer(Modifier.height(5.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {'''
if old_spacer not in M:
    raise SystemExit('download compact layout anchor missing')
M = M.replace(old_spacer, new_spacer, 1)
M = M.replace('''                    -> FocusButton(
                        "ايقاف مؤقت",''', '''                    -> FocusButton(
                        "ايقاف التحميل مؤقتا",''', 1)
M = M.replace('''                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {''', '''                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {''', 1)

M = M.replace(
    'ReorderableLiveCategoryBar(catalog?.categories.orEmpty(), state.selectedCategoryId, onSelectCategory)\n        FavoriteHint()',
    'ReorderableLiveCategoryBar(catalog?.categories.orEmpty(), catalog?.items.orEmpty(), state.selectedCategoryId, onSelectCategory)\n        LiveInteractionHints()',
    1,
)
start = M.index('@Composable\nprivate fun ReorderableLiveCategoryBar(')
end = M.index('\n@Composable\nprivate fun FavoriteHint()', start)
replacement = r'''@Composable
private fun ReorderableLiveCategoryBar(
    categories: List<Category>,
    items: List<ContentItem>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("live_category_order", android.content.Context.MODE_PRIVATE) }
    var ids by remember(categories) {
        mutableStateOf(prefs.getString("ids", "").orEmpty().split(',').filter { it.isNotBlank() })
    }
    var moving by remember { mutableStateOf<String?>(null) }
    val ordered = remember(categories, ids) {
        val byId = categories.associateBy { it.id }
        (ids.mapNotNull(byId::get) + categories.filterNot { it.id in ids }).distinctBy { it.id }
    }
    val artworkByCategory = remember(items) {
        items.filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }
    fun move(id: String, direction: Int) {
        val values = ordered.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        val to = (from + direction).coerceIn(0, values.lastIndex)
        if (from >= 0 && from != to) {
            values.add(to, values.removeAt(from))
            ids = values
            prefs.edit().putString("ids", values.joinToString(",")).apply()
        }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
    ) {
        item {
            FocusButton(
                "★ المفضلة",
                { onSelect(FAVORITES_CATEGORY_ID) },
                primary = selectedId == FAVORITES_CATEGORY_ID,
                compact = true,
            )
        }
        items(ordered, key = Category::id) { category ->
            LiveCategoryChip(
                category = category,
                representative = artworkByCategory[category.id],
                selected = selectedId == category.id,
                moving = moving == category.id,
                onClick = {
                    if (moving == category.id) moving = null else onSelect(category.id)
                },
                onLongClick = { moving = category.id },
                onMoveLeft = { move(category.id, 1) },
                onMoveRight = { move(category.id, -1) },
            )
        }
    }
}

@Composable
private fun LiveCategoryChip(
    category: Category,
    representative: ContentItem?,
    selected: Boolean,
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    LaunchedEffect(selectPressed) {
        if (selectPressed) {
            delay(650L)
            if (selectPressed && !remoteLongPressHandled) {
                remoteLongPressHandled = true
                onLongClick()
            }
        }
    }
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> colors.goldBright
                    selected -> colors.gold
                    moving -> colors.gold.copy(alpha = .30f)
                    else -> Color(0xFF181914)
                },
            )
            .border(
                if (focused || moving) 2.dp else 1.dp,
                if (focused || moving) colors.goldBright else colors.line.copy(alpha = .40f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val selectKey = event.key == Key.Enter || event.key == Key.DirectionCenter
                when {
                    selectKey && event.type == KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    selectKey && event.type == KeyEventType.KeyUp -> {
                        selectPressed = false
                        if (!remoteLongPressHandled) onClick()
                        remoteLongPressHandled = false
                        true
                    }
                    moving && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        onMoveLeft(); true
                    }
                    moving && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        onMoveRight(); true
                    }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (representative != null) {
            ChannelLogo(representative, Modifier.size(28.dp))
        } else {
            BrandBadge(Modifier.size(28.dp))
        }
        Text(
            text = if (moving) "↔ ${category.name}" else category.name,
            color = if (focused || selected) Color.Black else colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveInteractionHints() {
    val colors = LocalHulkColors.current
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            "تلميح: ضغطة مطولة على OK فوق الفئة لتفعيل ترتيب الفئات",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
        Text(
            "تلميح: ضغطة مطولة على OK فوق القناة لاضافتها او ازالتها من المفضلة",
            color = colors.textMuted,
            fontSize = 9.sp,
        )
    }
}
'''
M = M[:start] + replacement + M[end:]
if 'import androidx.compose.foundation.combinedClickable\n' not in M:
    M = M.replace('import androidx.compose.foundation.clickable\n', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n', 1)
M = M.replace('import androidx.compose.ui.input.key.nativeKeyEvent\n', '')

P = P.replace('.fillMaxHeight(.84f)\n                .fillMaxWidth(.82f)\n                .offset(x = 22.dp)',
              '.fillMaxHeight(.80f)\n                .fillMaxWidth(.76f)\n                .widthIn(max = 920.dp)', 1)
P = P.replace('.padding(20.dp),', '.padding(18.dp),', 1)
P = P.replace('BrandBadge(Modifier.size(58.dp))', 'BrandBadge(Modifier.size(50.dp))', 1)
P = P.replace('fontSize = 25.sp', 'fontSize = 22.sp', 1)
P = P.replace('.width(230.dp)', '.width(215.dp)', 1)

anchor = '''    var favoriteIds by remember(catalog) {
        mutableStateOf(catalog?.items.orEmpty().filter(isFavorite).map(ContentItem::id).toSet())
    }
'''
addition = '''    var favoriteIds by remember(catalog) {
        mutableStateOf(catalog?.items.orEmpty().filter(isFavorite).map(ContentItem::id).toSet())
    }
    val categoryArtwork = remember(catalog) {
        catalog?.items.orEmpty()
            .filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }
'''
if anchor not in P:
    raise SystemExit('player category artwork anchor missing')
P = P.replace(anchor, addition, 1)
old_cat = '''                    items(catalog?.categories.orEmpty(), key = { it.id }) { category ->
                        FocusButton(
                            text = category.name,
                            onClick = { selectedCategory = category.id; searchQuery = "" },
                            modifier = Modifier.fillMaxWidth(),
                            primary = selectedCategory == category.id && searchQuery.isBlank(),
                            compact = true,
                        )
                    }'''
new_cat = '''                    items(catalog?.categories.orEmpty(), key = { it.id }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            categoryArtwork[category.id]?.let { channel ->
                                ChannelLogo(channel, Modifier.size(32.dp))
                            }
                            FocusButton(
                                text = category.name,
                                onClick = { selectedCategory = category.id; searchQuery = "" },
                                modifier = Modifier.weight(1f),
                                primary = selectedCategory == category.id && searchQuery.isBlank(),
                                compact = true,
                            )
                        }
                    }'''
if old_cat not in P:
    raise SystemExit('player category list anchor missing')
P = P.replace(old_cat, new_cat, 1)
if 'import sa.hulksa.player.ui.components.ChannelLogo\n' not in P:
    P = P.replace('import sa.hulksa.player.ui.components.BrandBadge\n', 'import sa.hulksa.player.ui.components.BrandBadge\nimport sa.hulksa.player.ui.components.ChannelLogo\n', 1)

main.write_text(M)
player.write_text(P)
gradle.write_text(G)

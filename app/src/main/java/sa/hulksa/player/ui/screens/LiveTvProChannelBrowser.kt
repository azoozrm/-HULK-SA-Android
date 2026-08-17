package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * v1.6 Live TV Pro browser shown over playback.
 *
 * Restores the launch filter (Favorites / Recent / All / exact category), gives every channel an
 * explicit LEFT target back to the currently selected category, and uses a denser opaque TV shell
 * so the underlying ticker/video no longer competes visually with the browser content.
 */
@Composable
fun LiveTvProChannelBrowser(
    catalog: Catalog?,
    currentStreamId: Int,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onSelectChannel: (ContentItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val tvLayout = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
    val current = remember(catalog, currentStreamId) {
        catalog?.items?.firstOrNull { it.id == currentStreamId }
    }

    val categoryOrderPrefs = remember(context) {
        context.getSharedPreferences("live_category_order", Context.MODE_PRIVATE)
    }
    var orderedCategoryIds by remember(catalog) {
        mutableStateOf(
            categoryOrderPrefs.getString("ids", "")
                .orEmpty()
                .split(',')
                .filter(String::isNotBlank),
        )
    }
    var movingCategoryId by remember { mutableStateOf<String?>(null) }
    val orderedCategories = remember(catalog?.categories, orderedCategoryIds) {
        val categories = catalog?.categories.orEmpty()
        val byId = categories.associateBy { it.id }
        (orderedCategoryIds.mapNotNull(byId::get) + categories.filterNot { it.id in orderedCategoryIds })
            .distinctBy { it.id }
    }

    fun moveCategory(id: String, delta: Int) {
        val values = orderedCategories.map { it.id }.toMutableList()
        val from = values.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, values.lastIndex)
        if (from == to) return
        values.add(to, values.removeAt(from))
        orderedCategoryIds = values
        categoryOrderPrefs.edit().putString("ids", values.joinToString(",")).apply()
    }

    var recentChannelIds by remember(catalog, currentStreamId) {
        mutableStateOf(context.liveTvProRecentChannelIds())
    }
    LaunchedEffect(currentStreamId, catalog) {
        if (catalog?.items?.any { it.id == currentStreamId } == true) {
            val updated = liveTvProUpdateRecentChannelIds(
                existingIds = recentChannelIds,
                currentStreamId = currentStreamId,
                limit = 60,
            )
            if (updated != recentChannelIds) {
                recentChannelIds = updated
                context.saveLiveTvProRecentChannelIds(updated)
            }
        }
    }
    val recentChannels = remember(catalog, recentChannelIds) {
        val byId = catalog?.items.orEmpty().associateBy(ContentItem::id)
        recentChannelIds.mapNotNull(byId::get)
    }

    var favoriteIds by remember(catalog) {
        mutableStateOf(catalog?.items.orEmpty().filter(isFavorite).map(ContentItem::id).toSet())
    }
    val launchContext = remember(currentStreamId) { context.liveTvProLaunchContext() }
    val initialCategory = remember(
        launchContext,
        current?.categoryId,
        currentStreamId,
        favoriteIds,
        recentChannelIds,
    ) {
        liveTvProInitialBrowserCategory(
            launchContext = launchContext,
            currentCategoryId = current?.categoryId,
            currentStreamId = currentStreamId,
            favoriteIds = favoriteIds,
            recentIds = recentChannelIds,
        ) ?: if (launchContext == LIVE_TV_PRO_CONTEXT_ALL) null else current?.categoryId
    }
    var selectedCategory by remember(catalog, currentStreamId, launchContext) {
        mutableStateOf(initialCategory)
    }
    var searchQuery by remember { mutableStateOf("") }

    val categoryArtwork = remember(catalog) {
        catalog?.items.orEmpty()
            .filter { !it.posterUrl.isNullOrBlank() }
            .groupBy(ContentItem::categoryId)
            .mapValues { (_, channels) -> channels.first() }
    }

    val categoryChannels = when (selectedCategory) {
        LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> recentChannels
        LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> catalog?.items.orEmpty().filter { it.id in favoriteIds }
        null -> catalog?.items.orEmpty()
        else -> catalog?.items.orEmpty().filter { it.categoryId == selectedCategory }
    }
    val normalizedQuery = searchQuery.trim()
    val visible = if (normalizedQuery.isBlank()) {
        categoryChannels
    } else {
        catalog?.items.orEmpty().filter { channel ->
            channel.name.contains(normalizedQuery, ignoreCase = true) ||
                channel.id.toString().contains(normalizedQuery)
        }
    }
    val selectedCategoryTitle = when {
        normalizedQuery.isNotBlank() -> "نتائج البحث"
        selectedCategory == LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> "متابعة المشاهدة"
        selectedCategory == LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> "القنوات المفضلة"
        selectedCategory == null -> "كل القنوات"
        else -> orderedCategories.firstOrNull { it.id == selectedCategory }?.name ?: "كل القنوات"
    }

    val listState = rememberLazyListState()
    val channelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val allCategoryFocus = remember { FocusRequester() }
    val recentCategoryFocus = remember { FocusRequester() }
    val favoritesCategoryFocus = remember { FocusRequester() }
    val categoryFocusRequesters = remember(orderedCategories.map { it.id }) {
        orderedCategories.associate { it.id to FocusRequester() }
    }
    val selectedCategoryFocusRequester = when (selectedCategory) {
        null -> allCategoryFocus
        LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> recentCategoryFocus
        LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> favoritesCategoryFocus
        else -> selectedCategory?.let(categoryFocusRequesters::get)
    }
    val focusIndex = visible.indexOfFirst { it.id == currentStreamId }.takeIf { it >= 0 } ?: 0

    LaunchedEffect(visible, selectedCategory, normalizedQuery) {
        if (visible.isNotEmpty() && normalizedQuery.isBlank()) {
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { channelFocus.requestFocus() }
        }
    }

    fun selectCategory(id: String?) {
        movingCategoryId = null
        selectedCategory = id
        searchQuery = ""
        context.saveLiveTvProLaunchContext(liveTvProBrowserCategoryToContext(id))
    }

    val closeOnBackModifier = Modifier.onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        val isBack = code == AndroidKeyEvent.KEYCODE_BACK || code == AndroidKeyEvent.KEYCODE_ESCAPE
        if (isBack) {
            if (event.type == KeyEventType.KeyDown) onClose()
            true
        } else {
            false
        }
    }

    @Composable
    fun ReorderableCategoryRow(
        category: sa.hulksa.player.model.Category,
        focusRequester: FocusRequester?,
    ) {
        var focused by remember(category.id) { mutableStateOf(false) }
        var selectPressed by remember(category.id) { mutableStateOf(false) }
        var longPressHandled by remember(category.id) { mutableStateOf(false) }
        val moving = movingCategoryId == category.id
        val shape = RoundedCornerShape(11.dp)

        LaunchedEffect(selectPressed, moving) {
            if (selectPressed && !moving) {
                delay(650L)
                if (selectPressed && !longPressHandled) {
                    longPressHandled = true
                    movingCategoryId = category.id
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        focused -> colors.goldBright
                        selectedCategory == category.id && searchQuery.isBlank() -> colors.gold
                        moving -> colors.gold.copy(alpha = .30f)
                        else -> Color.White.copy(alpha = .055f)
                    },
                )
                .border(
                    if (focused || moving) 2.dp else 1.dp,
                    if (focused || moving) colors.goldBright else Color.White.copy(alpha = .11f),
                    shape,
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val selectKey = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                    when {
                        selectKey && event.type == KeyEventType.KeyDown -> {
                            selectPressed = true
                            true
                        }
                        selectKey && event.type == KeyEventType.KeyUp -> {
                            selectPressed = false
                            if (!longPressHandled) {
                                if (moving) movingCategoryId = null else selectCategory(category.id)
                            }
                            longPressHandled = false
                            true
                        }
                        moving && event.type == KeyEventType.KeyDown &&
                            (code == AndroidKeyEvent.KEYCODE_DPAD_UP || code == AndroidKeyEvent.KEYCODE_DPAD_DOWN) -> true
                        moving && event.type == KeyEventType.KeyUp && code == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            moveCategory(category.id, -1)
                            true
                        }
                        moving && event.type == KeyEventType.KeyUp && code == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveCategory(category.id, 1)
                            true
                        }
                        else -> false
                    }
                }
                .clickable(
                    role = Role.Button,
                    onClick = {
                        if (moving) movingCategoryId = null else selectCategory(category.id)
                    },
                )
                .focusable()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categoryArtwork[category.id]?.let { ChannelLogo(it, Modifier.size(29.dp)) }
            Text(
                text = if (moving) "↕ ${category.name}" else category.name,
                modifier = Modifier.weight(1f),
                color = if (focused || (selectedCategory == category.id && searchQuery.isBlank())) Color.Black else colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    fun CategoryPane(paneModifier: Modifier) {
        val paneShape = RoundedCornerShape(if (tvLayout) 16.dp else 18.dp)
        Column(
            modifier = paneModifier
                .clip(paneShape)
                .background(if (tvLayout) Color(0xED0B0C09) else Color(0xF20D0E0B))
                .border(1.dp, Color.White.copy(alpha = .11f), paneShape)
                .padding(if (tvLayout) 11.dp else 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "الفئات",
                        color = colors.text,
                        fontSize = if (tvLayout) 17.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (movingCategoryId != null) "حرك ↑ ↓ ثم OK للحفظ" else "${orderedCategories.size} فئة",
                        color = if (movingCategoryId != null) colors.goldBright else colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = if (movingCategoryId != null) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                item("all") {
                    FocusButton(
                        text = "الكل",
                        onClick = { selectCategory(null) },
                        modifier = Modifier.fillMaxWidth().focusRequester(allCategoryFocus),
                        primary = selectedCategory == null && searchQuery.isBlank(),
                        compact = true,
                    )
                }
                item("continue") {
                    FocusButton(
                        text = "▶ متابعة المشاهدة (${recentChannels.size})",
                        onClick = { selectCategory(LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY) },
                        modifier = Modifier.fillMaxWidth().focusRequester(recentCategoryFocus),
                        primary = selectedCategory == LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY && searchQuery.isBlank(),
                        compact = true,
                        enabled = recentChannels.isNotEmpty(),
                    )
                }
                item("favorites") {
                    FocusButton(
                        text = "★ المفضلة (${favoriteIds.size})",
                        onClick = { selectCategory(LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY) },
                        modifier = Modifier.fillMaxWidth().focusRequester(favoritesCategoryFocus),
                        primary = selectedCategory == LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY && searchQuery.isBlank(),
                        compact = true,
                    )
                }
                items(orderedCategories, key = { it.id }) { category ->
                    ReorderableCategoryRow(category, categoryFocusRequesters[category.id])
                }
            }
        }
    }

    @Composable
    fun ChannelPane(paneModifier: Modifier) {
        val paneShape = RoundedCornerShape(if (tvLayout) 16.dp else 18.dp)
        Column(
            modifier = paneModifier
                .clip(paneShape)
                .background(if (tvLayout) Color(0xE711120E) else Color(0xF212130F))
                .border(1.dp, Color.White.copy(alpha = .10f), paneShape)
                .padding(if (tvLayout) 13.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedCategoryTitle,
                        color = colors.text,
                        fontSize = if (tvLayout) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append("${visible.size} قناة")
                            current?.let { append("  •  تشاهد الان: ${it.name}") }
                        },
                        color = if (current != null) colors.goldBright else colors.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FocusButton("اغلاق", onClose, primary = false, compact = true)
            }

            Spacer(Modifier.height(10.dp))
            HulkTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "بحث سريع عن قناة",
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
            )
            Spacer(Modifier.height(10.dp))

            when {
                catalog == null -> LoadingRing(
                    label = "جاري تجهيز القنوات…",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp),
                )
                visible.isEmpty() -> Text(
                    when {
                        normalizedQuery.isNotBlank() -> "لا توجد قناة مطابقة للبحث"
                        selectedCategory == LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> "لا توجد قنوات في متابعة المشاهدة"
                        selectedCategory == LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> "لا توجد قنوات مفضلة"
                        else -> "لا توجد قنوات في هذه الفئة"
                    },
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (tvLayout) 5.dp else 6.dp),
                    contentPadding = PaddingValues(bottom = 14.dp),
                ) {
                    items(visible, key = ContentItem::id) { channel ->
                        val index = visible.indexOf(channel)
                        val favorite = channel.id in favoriteIds
                        val baseModifier = if (index == focusIndex && normalizedQuery.isBlank()) {
                            Modifier.focusRequester(channelFocus)
                        } else {
                            Modifier
                        }
                        ChannelListItem(
                            item = channel,
                            selected = channel.id == currentStreamId,
                            onFocused = {},
                            onClick = { onSelectChannel(channel) },
                            isFavorite = favorite,
                            onLongClick = {
                                favoriteIds = if (favorite) favoriteIds - channel.id else favoriteIds + channel.id
                                onToggleFavorite(channel)
                                Toast.makeText(
                                    context,
                                    if (favorite) "تمت ازالة القناة من المفضلة" else "تمت اضافة القناة الى المفضلة",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = baseModifier.focusProperties {
                                selectedCategoryFocusRequester?.let { left = it }
                            },
                        )
                    }
                }
            }
        }
    }

    val shellShape = RoundedCornerShape(if (tvLayout) 22.dp else 20.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (tvLayout) .34f else .72f))
            .then(closeOnBackModifier),
    )
    Row(
        modifier = modifier
            .fillMaxHeight(if (tvLayout) .92f else .86f)
            .fillMaxWidth(if (tvLayout) .78f else .92f)
            .then(closeOnBackModifier)
            .clip(shellShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF5080907), Color(0xF014150F)),
                ),
            )
            .border(1.dp, colors.gold.copy(alpha = .30f), shellShape)
            .padding(if (tvLayout) 14.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tvLayout) 13.dp else 10.dp),
    ) {
        ChannelPane(Modifier.weight(1f).fillMaxHeight())
        CategoryPane(Modifier.width(if (tvLayout) 246.dp else 210.dp).fillMaxHeight())
    }
}

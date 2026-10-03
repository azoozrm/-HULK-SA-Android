package sa.hulksa.player.ui.screens

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.adaptive.HulkInputMode
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkArtworkSurface
import sa.hulksa.player.ui.components.HulkFallbackArtwork
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

internal class LiveTvProCategoryReturnGate {
    private var active = false

    fun tryStart(): Boolean {
        if (active) return false
        active = true
        return true
    }

    fun finish() {
        active = false
    }
}

internal fun liveTvProCategoryReturnNeedsReveal(
    targetIndex: Int,
    visibleIndices: Set<Int>,
): Boolean = targetIndex !in visibleIndices

/**
 * Launch origin of the canonical Live channel browser.
 *
 * The browser itself must understand why it was opened so its copy and the surrounding
 * close/selection contract stay deterministic for both entry points. The origin is explicit and
 * never inferred from timing.
 */
internal enum class LiveChannelBrowserOrigin {
    NORMAL_LIVE,
    ERROR_RECOVERY,
}

internal data class LiveChannelBrowserCopy(
    val title: String,
    val description: String,
)

internal fun liveChannelBrowserCopy(origin: LiveChannelBrowserOrigin): LiveChannelBrowserCopy =
    when (origin) {
        LiveChannelBrowserOrigin.NORMAL_LIVE -> LiveChannelBrowserCopy(
            title = "القنوات المباشرة",
            description = "اختر قناة أو تنقل بين الفئات",
        )
        LiveChannelBrowserOrigin.ERROR_RECOVERY -> LiveChannelBrowserCopy(
            title = "القنوات المباشرة",
            description = "تعذر تشغيل القناة الحالية، اختر قناة أخرى للمتابعة",
        )
    }

/**
 * Canonical Live channel browser shown over playback.
 *
 * This is the single implementation used by both Live entry points: normal Live OK and the
 * final-error "اختيار قناة" action. It keeps the launch context, explicit channel-to-category
 * focus return and a safe responsive shell for TV/remote and touch layouts. The owning surface
 * (PlayerScreen) owns the single visibility/launch-origin state; this composable is stateless in
 * that regard and only reports row/category interactions.
 */
@Composable
internal fun LiveChannelBrowser(
    catalog: Catalog?,
    currentStreamId: Int,
    origin: LiveChannelBrowserOrigin,
    isFavorite: (ContentItem) -> Boolean,
    favoriteKeys: Set<String>,
    onToggleFavorite: (ContentItem) -> Unit,
    onSelectChannel: (ContentItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val copy = remember(origin) { liveChannelBrowserCopy(origin) }
    val tvLayout = adaptiveUi.isTelevision || adaptiveUi.inputMode == HulkInputMode.REMOTE
    val stackedMobile = !tvLayout && adaptiveUi.screenWidthDp < 600
    val safeInsets = tvPageSafeInsets(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    val safeHorizontal = if (tvLayout) maxOf(24f, safeInsets.horizontalDp).dp else 10.dp
    val safeVertical = if (tvLayout) maxOf(16f, safeInsets.verticalDp).dp else 10.dp
    val hintFontSize = if (tvLayout) 10.sp else 9.sp
    val hintLineHeight = if (tvLayout) 14.sp else 13.sp
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

    val liveProfileScope = context.liveTvProStateScope()
    var recentChannelIds by remember(catalog, currentStreamId, liveProfileScope) {
        mutableStateOf(context.liveTvProRecentChannelIds())
    }
    LaunchedEffect(currentStreamId, catalog, liveProfileScope) {
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

    // Authoritative membership: derive from the observed favorites snapshot and the owning
    // predicate. There is deliberately no local optimistic flip here, so a rejected rapid toggle
    // can never paint a heart or a category membership that the library did not accept. The
    // snapshot is delivered by the player boundary exactly as the HUD receives it.
    val favoriteIds = remember(catalog, favoriteKeys) {
        catalog?.items.orEmpty().filter(isFavorite).map(ContentItem::id).toSet()
    }
    val launchContext = remember(currentStreamId, liveProfileScope) {
        context.liveTvProLaunchContext()
    }
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
    val listState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val categoryFocusScope = rememberCoroutineScope()
    val categoryReturnGate = remember { LiveTvProCategoryReturnGate() }
    val channelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val allCategoryFocus = remember { FocusRequester() }
    val recentCategoryFocus = remember { FocusRequester() }
    val favoritesCategoryFocus = remember { FocusRequester() }
    val stableCategoryIds = remember(catalog?.categories) {
        catalog?.categories.orEmpty().map { it.id }
    }
    val categoryFocusRequesters = remember(stableCategoryIds) {
        stableCategoryIds.associateWith { FocusRequester() }
    }
    val leadingCategoryIds = remember {
        listOf<String?>(
            null,
            LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY,
            LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY,
        )
    }
    val focusIndex = visible.indexOfFirst { it.id == currentStreamId }.takeIf { it >= 0 } ?: 0

    fun categoryFocusRequester(categoryId: String?): FocusRequester? = when (categoryId) {
        null -> allCategoryFocus
        LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> recentCategoryFocus
        LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> favoritesCategoryFocus
        else -> categoryId?.let(categoryFocusRequesters::get)
    }

    fun resolveCategoryReturnTarget(categoryId: String?): Pair<Int, FocusRequester>? {
        val targetIndex = selectedCategoryFocusIndex(
            selectedId = categoryId,
            leadingIds = leadingCategoryIds,
            orderedIds = orderedCategories.map { it.id },
        ) ?: return null
        val requester = categoryFocusRequester(categoryId) ?: return null
        return targetIndex to requester
    }

    fun returnFocusToSelectedCategory() {
        if (!tvLayout || normalizedQuery.isNotBlank()) return
        val targetCategoryId = selectedCategory
        val initialTarget = resolveCategoryReturnTarget(targetCategoryId) ?: return
        if (!categoryReturnGate.tryStart()) return
        categoryFocusScope.launch {
            try {
                val visibleIndices = categoryListState.layoutInfo.visibleItemsInfo
                    .mapTo(mutableSetOf()) { it.index }
                if (liveTvProCategoryReturnNeedsReveal(initialTarget.first, visibleIndices)) {
                    categoryListState.scrollToItem(initialTarget.first)
                    snapshotFlow {
                        val resolvedIndex = resolveCategoryReturnTarget(targetCategoryId)?.first
                        resolvedIndex != null &&
                            categoryListState.layoutInfo.visibleItemsInfo.any { it.index == resolvedIndex }
                    }.first { it }
                    withFrameNanos { }
                }
                val resolvedTarget = resolveCategoryReturnTarget(targetCategoryId) ?: return@launch
                runCatching { resolvedTarget.second.requestFocus() }
            } finally {
                categoryReturnGate.finish()
            }
        }
    }

    LaunchedEffect(visible, selectedCategory, normalizedQuery) {
        if (visible.isNotEmpty() && normalizedQuery.isBlank()) {
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { channelFocus.requestFocus() }
        }
    }
    LaunchedEffect(orderedCategoryIds, movingCategoryId) {
        val movingId = movingCategoryId ?: return@LaunchedEffect
        val categoryIndex = orderedCategories.indexOfFirst { it.id == movingId }
        if (categoryIndex >= 0) {
            categoryListState.scrollToItem(categoryIndex + 3)
            withFrameNanos { }
            runCatching { categoryFocusRequesters[movingId]?.requestFocus() }
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
    fun BrowserArtwork(
        posterUrl: String?,
        description: String,
        artworkModifier: Modifier = Modifier,
    ) {
        var imageFailed by remember(posterUrl) { mutableStateOf(false) }
        if (!posterUrl.isNullOrBlank() && !imageFailed) {
            Box(
                modifier = artworkModifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF0EEE7))
                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = ContentScale.Fit,
                    onError = { imageFailed = true },
                )
            }
        } else {
            HulkFallbackArtwork(
                modifier = artworkModifier,
                surface = HulkArtworkSurface.SQUARE,
            )
        }
    }

    @Composable
    fun BrowserChannelRow(
        channel: ContentItem,
        selected: Boolean,
        favorite: Boolean,
        rowModifier: Modifier = Modifier,
        onReturnToCategory: () -> Unit,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) {
        var focused by remember(channel.id) { mutableStateOf(false) }
        var remoteLongPressHandled by remember(channel.id) { mutableStateOf(false) }
        val showFocused = focused && adaptiveUi.showFocusHighlights
        val currentPlaying = selected
        val shape = RoundedCornerShape(11.dp)
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .background(
                    when {
                        showFocused -> colors.gold.copy(alpha = .20f)
                        currentPlaying -> colors.gold.copy(alpha = .10f)
                        else -> Color(0xFF14150F)
                    },
                )
                .border(
                    width = if (showFocused) 2.dp else 1.dp,
                    color = when {
                        showFocused -> colors.goldBright
                        currentPlaying -> colors.gold.copy(alpha = .45f)
                        else -> Color.White.copy(alpha = .08f)
                    },
                    shape = shape,
                )
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val returnToCategory = tvLayout && code == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                    val remoteSelect = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_SPACE
                    when {
                        returnToCategory -> {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                onReturnToCategory()
                            }
                            true
                        }
                        !remoteSelect -> false
                        event.type == KeyEventType.KeyDown -> {
                            if (
                                (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress) &&
                                !remoteLongPressHandled
                            ) {
                                remoteLongPressHandled = true
                                onLongClick()
                            }
                            true
                        }
                        event.type == KeyEventType.KeyUp -> {
                            if (!remoteLongPressHandled) onClick()
                            remoteLongPressHandled = false
                            true
                        }
                        else -> false
                    }
                }
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            BrowserArtwork(channel.posterUrl, channel.name, Modifier.size(48.dp))
            Text(
                text = channel.name,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (currentPlaying) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color.White.copy(alpha = .18f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "تشاهد الان",
                        color = colors.goldBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.goldBright,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) colors.gold else colors.textMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }

    @Composable
    fun BrowserCategoryBadge() {
        Box(
            modifier = Modifier
                .size(if (tvLayout) 24.dp else 22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF10110D))
                .border(1.dp, colors.gold.copy(alpha = .70f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(Modifier.fillMaxSize().padding(3.dp))
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
        var dragAccumulator by remember(category.id) { mutableFloatStateOf(0f) }
        val moving = movingCategoryId == category.id
        val showFocused = focused && adaptiveUi.showFocusHighlights
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
                        showFocused -> colors.gold.copy(alpha = .24f)
                        selectedCategory == category.id && searchQuery.isBlank() -> colors.gold.copy(alpha = .14f)
                        moving -> colors.gold.copy(alpha = .20f)
                        else -> Color.White.copy(alpha = .045f)
                    },
                )
                .border(
                    width = if (showFocused) 2.dp else 1.dp,
                    color = when {
                        showFocused -> colors.goldBright
                        selectedCategory == category.id && searchQuery.isBlank() -> colors.gold.copy(alpha = .60f)
                        moving -> colors.gold.copy(alpha = .45f)
                        else -> Color.White.copy(alpha = .10f)
                    },
                    shape = shape,
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
                .pointerInput(category.id, moving, tvLayout) {
                    if (!tvLayout && moving) {
                        detectVerticalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                            onDragEnd = {
                                when {
                                    dragAccumulator >= 48f -> moveCategory(category.id, 1)
                                    dragAccumulator <= -48f -> moveCategory(category.id, -1)
                                }
                                dragAccumulator = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                        }
                    }
                }
                .combinedClickable(
                    role = Role.Button,
                    onClick = {
                        if (moving) movingCategoryId = null else selectCategory(category.id)
                    },
                    onLongClick = { movingCategoryId = category.id },
                )
                .focusable()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowserCategoryBadge()
            Text(
                text = if (moving) "↕ ${category.name}" else category.name,
                modifier = Modifier.weight(1f),
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    fun BrowserCategoryRow(
        text: String,
        icon: ImageVector,
        selected: Boolean,
        onClick: () -> Unit,
        focusRequester: FocusRequester,
        iconTint: Color? = null,
    ) {
        var focused by remember { mutableStateOf(false) }
        val showFocused = focused && adaptiveUi.showFocusHighlights
        val shape = RoundedCornerShape(11.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        showFocused -> colors.gold.copy(alpha = .24f)
                        selected -> colors.gold.copy(alpha = .14f)
                        else -> Color.White.copy(alpha = .045f)
                    },
                )
                .border(
                    width = if (showFocused) 2.dp else 1.dp,
                    color = when {
                        showFocused -> colors.goldBright
                        selected -> colors.gold.copy(alpha = .60f)
                        else -> Color.White.copy(alpha = .10f)
                    },
                    shape = shape,
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: if (selected) colors.gold else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    @Composable
    fun CategoryPane(paneModifier: Modifier) {
        Column(
            modifier = paneModifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x66101108))
                .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(14.dp))
                .padding(if (tvLayout) 9.dp else 8.dp),
        ) {
            Text(
                "الفئات",
                color = colors.text,
                fontSize = if (tvLayout) 15.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
            Spacer(Modifier.height(7.dp))
            LazyColumn(
                state = categoryListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item("all") {
                    BrowserCategoryRow(
                        text = "الكل",
                        icon = Icons.Rounded.GridView,
                        selected = selectedCategory == null && searchQuery.isBlank(),
                        onClick = { selectCategory(null) },
                        focusRequester = allCategoryFocus,
                    )
                }
                item("favorites") {
                    BrowserCategoryRow(
                        text = "المفضلة",
                        icon = Icons.Rounded.Star,
                        iconTint = colors.gold,
                        selected = selectedCategory == LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY && searchQuery.isBlank(),
                        onClick = { selectCategory(LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY) },
                        focusRequester = favoritesCategoryFocus,
                    )
                }
                item("continue") {
                    BrowserCategoryRow(
                        text = "اخر مشاهدة",
                        icon = Icons.Outlined.Schedule,
                        selected = selectedCategory == LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY && searchQuery.isBlank(),
                        onClick = { selectCategory(LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY) },
                        focusRequester = recentCategoryFocus,
                    )
                }
                items(orderedCategories, key = { it.id }) { category ->
                    ReorderableCategoryRow(category, categoryFocusRequesters[category.id])
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = if (movingCategoryId != null) {
                    if (tvLayout) {
                        "وضع الترتيب : حرك الفئة ↑ ↓ ثم اضغط OK للحفظ"
                    } else {
                        "وضع الترتيب : اسحب الفئة ↑ ↓ ثم اضغط عليها للحفظ"
                    }
                } else {
                    if (tvLayout) {
                        "ترتيب الفئات : اضغط OK مطولا على الفئة، ثم حرك ↑ ↓ واضغط OK للحفظ"
                    } else {
                        "ترتيب الفئات : اضغط مطولا على الفئة ثم اسحبها ↑ ↓"
                    }
                },
                color = if (movingCategoryId != null) colors.goldBright else colors.textMuted,
                fontSize = hintFontSize,
                lineHeight = hintLineHeight,
                fontWeight = if (movingCategoryId != null) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }

    @Composable
    fun BrowserHeader() {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = if (tvLayout) 96.dp else 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    copy.title,
                    color = colors.text,
                    fontSize = if (tvLayout) 21.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (origin == LiveChannelBrowserOrigin.ERROR_RECOVERY) {
                    Text(
                        copy.description,
                        color = colors.textMuted,
                        fontSize = if (tvLayout) 10.sp else 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            FocusButton(
                "اغلاق",
                onClose,
                primary = false,
                outlined = true,
                compact = true,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }

    @Composable
    fun ChannelPane(paneModifier: Modifier) {
        Column(modifier = paneModifier) {
            Text(
                text = "${visible.size} قناة",
                color = colors.textMuted,
                fontSize = if (tvLayout) 11.sp else 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
            HulkTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "بحث عن قناة",
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                leadingIcon = Icons.Rounded.Search,
            )
            Spacer(Modifier.height(9.dp))

            when {
                catalog == null -> LoadingRing(
                    label = "جاري تجهيز القنوات…",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 70.dp),
                )
                visible.isEmpty() -> Text(
                    when {
                        normalizedQuery.isNotBlank() -> "لا توجد قناة مطابقة للبحث"
                        selectedCategory == LIVE_TV_PRO_BROWSER_CONTINUE_CATEGORY -> "لا توجد قنوات في اخر مشاهدة"
                        selectedCategory == LIVE_TV_PRO_BROWSER_FAVORITES_CATEGORY -> "لا توجد قنوات مفضلة"
                        else -> "لا توجد قنوات في هذه الفئة"
                    },
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 70.dp),
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
                        BrowserChannelRow(
                            channel = channel,
                            selected = channel.id == currentStreamId,
                            favorite = favorite,
                            onReturnToCategory = ::returnFocusToSelectedCategory,
                            onClick = { onSelectChannel(channel) },
                            onLongClick = { onToggleFavorite(channel) },
                            rowModifier = baseModifier,
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun InstructionToken(text: String) {
        Text(
            text = text,
            color = colors.textMuted,
            fontSize = hintFontSize,
            lineHeight = hintLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }

    @Composable
    fun InstructionDivider() {
        Box(
            Modifier
                .width(1.dp)
                .height(12.dp)
                .background(Color.White.copy(alpha = .18f)),
        )
    }

    @Composable
    fun SelectInstructionGroup() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InstructionToken("اختيار القناة")
            InstructionToken(":")
            InstructionToken("OK")
        }
    }

    @Composable
    fun FavoriteInstructionGroup() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InstructionToken("اضافة او ازالة من المفضلة")
            InstructionToken(":")
            InstructionToken("مطولا")
            InstructionToken("OK")
        }
    }

    @Composable
    fun CloseInstructionGroup() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InstructionToken("رجوع")
            InstructionToken(":")
            InstructionToken("اغلاق")
        }
    }

    @Composable
    fun BrowserInstructions() {
        if (!tvLayout) {
            Text(
                text = "اضغط مطولا على القناة لاضافتها او ازالتها من المفضلة",
                color = colors.textMuted,
                fontSize = hintFontSize,
                lineHeight = hintLineHeight,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            return
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            if (maxWidth >= 640.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseInstructionGroup()
                    Box(Modifier.padding(horizontal = 9.dp)) { InstructionDivider() }
                    FavoriteInstructionGroup()
                    Box(Modifier.padding(horizontal = 9.dp)) { InstructionDivider() }
                    SelectInstructionGroup()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    SelectInstructionGroup()
                    FavoriteInstructionGroup()
                    CloseInstructionGroup()
                }
            }
        }
    }

    val shellShape = RoundedCornerShape(if (tvLayout) 22.dp else 18.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (tvLayout) .38f else .72f))
            .then(closeOnBackModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = safeHorizontal, vertical = safeVertical),
        ) {
            if (stackedMobile) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(.96f)
                        .clip(shellShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFA080907), Color(0xF814150F))))
                        .border(1.dp, colors.gold.copy(alpha = .42f), shellShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrowserHeader()
                    CategoryPane(
                        Modifier
                            .fillMaxWidth()
                            .height((adaptiveUi.screenHeightDp * .31f).coerceIn(190f, 255f).dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = .12f)),
                    )
                    ChannelPane(Modifier.fillMaxWidth().weight(1f))
                    BrowserInstructions()
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(if (tvLayout) .94f else .90f)
                        .fillMaxWidth(if (tvLayout) .82f else .94f)
                        .clip(shellShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFA080907), Color(0xF814150F))))
                        .border(1.dp, colors.gold.copy(alpha = .42f), shellShape)
                        .padding(if (tvLayout) 14.dp else 11.dp),
                ) {
                    BrowserHeader()
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(if (tvLayout) 13.dp else 10.dp),
                    ) {
                        ChannelPane(Modifier.weight(1f).fillMaxHeight())
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = .12f)),
                        )
                        CategoryPane(
                            Modifier
                                .width(if (tvLayout) 246.dp else 210.dp)
                                .fillMaxHeight(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    BrowserInstructions()
                }
            }
        }
    }
}

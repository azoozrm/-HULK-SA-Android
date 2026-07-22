package sa.hulksa.player.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.BuildConfig
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.ChannelListItem
import sa.hulksa.player.ui.components.CompactPosterCard
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HistoryCard
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val WEBSITE_URL = "https://hulksa.com/"
private const val ACCOUNT_URL = "https://hulksa.com/account/login.php"
private const val APPS_URL = "https://hulksa.com/hulk-app/"
private const val SUPPORT_URL = "https://wa.me/966506349935"
private const val FAVORITES_CATEGORY_ID = "__hulk_favorites__"
private const val CONTINUE_CATEGORY_ID = "__hulk_continue__"

@Composable
fun MainShellScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectDestination: (MainDestination) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onRetryDownload: (OfflineDownload) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val context = LocalContext.current
    val toggleFavoriteWithFeedback: (ContentItem) -> Unit = { item ->
        val wasFavorite = isFavorite(item)
        onToggleFavorite(item)
        Toast.makeText(
            context,
            if (wasFavorite) "تمت إزالة ${item.name} من المفضلة" else "تمت إضافة ${item.name} إلى المفضلة",
            Toast.LENGTH_SHORT,
        ).show()
    }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (isTv) {
            Row(Modifier.fillMaxSize()) {
                CinematicNavigationRail(
                    selected = state.destination,
                    onSelect = onSelectDestination,
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DestinationContent(
                        state = state,
                        isTv = true,
                        isFavorite = isFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onLogout = onLogout,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                MobileNavigation(state.destination, onSelectDestination)
                Box(Modifier.weight(1f)) {
                    DestinationContent(
                        state = state,
                        isTv = false,
                        isFavorite = isFavorite,
                        onSelectCategory = onSelectCategory,
                        onSearch = onSearch,
                        onOpen = onOpen,
                        onOpenHistory = onOpenHistory,
                        onToggleFavorite = toggleFavoriteWithFeedback,
                        onRefresh = onRefresh,
                        onSelectDestination = onSelectDestination,
                        onClearHistory = onClearHistory,
                        onPlayDownload = onPlayDownload,
                        onDeleteDownload = onDeleteDownload,
                        onRetryDownload = onRetryDownload,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun CinematicNavigationRail(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus
    val railWidth by animateDpAsState(if (expanded) 202.dp else 78.dp, label = "railWidth")

    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .focusGroup()
            .onFocusChanged { railHasFocus = it.hasFocus }
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF090A07), Color(0xF70A0B08)),
                ),
            )
            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBadge(Modifier.size(if (expanded) 92.dp else 64.dp))
        Spacer(Modifier.height(14.dp))
        destinations.forEach { entry ->
            NavigationItem(
                entry = entry,
                selected = selected == entry.destination,
                expanded = expanded,
                onClick = { onSelect(entry.destination) },
            )
            Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NavigationItem(
    entry: DestinationEntry,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> colors.gold
                    selected -> colors.gold.copy(alpha = .13f)
                    else -> Color.Transparent
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = if (focused) Color.Black else if (active) colors.goldBright else colors.textMuted,
            modifier = Modifier.size(23.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(11.dp))
            Text(
                entry.label,
                color = if (focused) Color.Black else if (active) colors.text else colors.textMuted,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MobileNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF090A07)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item { BrandBadge(Modifier.size(45.dp)) }
        items(destinations, key = { it.destination.name }) { entry ->
            FocusButton(entry.label, { onSelect(entry.destination) }, primary = selected == entry.destination, compact = true)
        }
    }
}

@Composable
private fun DestinationContent(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onSelectDestination: (MainDestination) -> Unit,
    onClearHistory: () -> Unit,
    onPlayDownload: (OfflineDownload) -> Unit,
    onDeleteDownload: (OfflineDownload) -> Unit,
    onRetryDownload: (OfflineDownload) -> Unit,
    onLogout: () -> Unit,
) {
    when (state.destination) {
        MainDestination.HOME -> CinemaHomeScreen(state, isTv, isFavorite, onOpen, onOpenHistory, onToggleFavorite, onRefresh)
        MainDestination.LIVE -> LiveCatalogScreen(state, isTv, isFavorite, onSelectCategory, onSearch, onOpen, onToggleFavorite, onRefresh)
        MainDestination.MOVIES -> PosterCatalogScreen("الأفلام", ContentType.MOVIE, state, isTv, isFavorite, onSelectCategory, onSearch, onOpen, onOpenHistory, onToggleFavorite, onRefresh)
        MainDestination.SERIES -> PosterCatalogScreen("المسلسلات", ContentType.SERIES, state, isTv, isFavorite, onSelectCategory, onSearch, onOpen, onOpenHistory, onToggleFavorite, onRefresh)
        MainDestination.FAVORITES -> FavoritesScreen(state, isTv, isFavorite, onOpen, onToggleFavorite)
        MainDestination.SEARCH -> UnifiedSearchScreen(state, isTv, isFavorite, onSearch, onOpen, onToggleFavorite)
        MainDestination.DOWNLOADS -> DownloadsScreen(
            downloads = state.downloads,
            isTv = isTv,
            onPlay = onPlayDownload,
            onDelete = onDeleteDownload,
            onRetry = onRetryDownload,
        )
        MainDestination.SETTINGS -> SettingsScreen(
            state = state,
            isTv = isTv,
            onRefreshAll = {
                onSelectDestination(MainDestination.HOME)
                onRefresh()
            },
            onClearHistory = onClearHistory,
            onLogout = onLogout,
        )
    }
}

@Composable
private fun CinemaHomeScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val movies = remember(state.catalogs[ContentType.MOVIE]) { newest(state.catalogs[ContentType.MOVIE]?.items.orEmpty()) }
    val series = remember(state.catalogs[ContentType.SERIES]) { newest(state.catalogs[ContentType.SERIES]?.items.orEmpty()) }
    val featured = movies.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: series.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: movies.firstOrNull()
        ?: series.firstOrNull()
    val continueWatching = state.history.filter { !it.isLive && it.positionMs > 0L }.take(15)
    val favoriteItems = remember(state.catalogs, state.favorites) {
        state.catalogs.values.flatMap { it.items }.filter(isFavorite).distinctBy { "${it.type}:${it.id}" }
    }
    val loading = ContentType.MOVIE in state.loadingTypes || ContentType.SERIES in state.loadingTypes

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 23.dp else 17.dp),
    ) {
        item {
            if (featured != null) {
                CinemaHero(featured, isTv, isFavorite(featured), { onOpen(featured) }, { onToggleFavorite(featured) }, onRefresh, loading)
            } else {
                HomePlaceholder(loading, onRefresh, isTv)
            }
        }
        if (state.errorMessage != null) {
            item { ErrorNotice(state.errorMessage, Modifier.padding(horizontal = if (isTv) 25.dp else 14.dp)) }
        }
        if (continueWatching.isNotEmpty()) {
            item { HomeSectionPadding { HistorySection("متابعة المشاهدة", continueWatching, isTv, onOpenHistory) } }
        }
        if (movies.isNotEmpty()) {
            item { HomeSectionPadding { PosterSection("أحدث الأفلام", movies.take(28), isTv, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (series.isNotEmpty()) {
            item { HomeSectionPadding { PosterSection("أحدث المسلسلات", series.take(28), isTv, isFavorite, onOpen, onToggleFavorite) } }
        }
        if (favoriteItems.isNotEmpty()) {
            item { HomeSectionPadding { PosterSection("لأنك اخترتها", favoriteItems.take(20), isTv, isFavorite, onOpen, onToggleFavorite) } }
        }
    }
}

@Composable
private fun HomeSectionPadding(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 25.dp)) { content() }
}

@Composable
private fun CinemaHero(
    item: ContentItem,
    isTv: Boolean,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
) {
    val colors = LocalHulkColors.current
    val image = item.backdropUrl ?: item.posterUrl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 374.dp else 288.dp)
            .background(Color(0xFF0A0B08)),
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(190.dp).graphicsLayer { alpha = .38f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .18f),
                    .55f to Color.Transparent,
                    1f to colors.background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .18f), colors.background.copy(alpha = .94f)),
                ),
            ),
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("الرئيسية", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("أحدث إضافات HULK SA", color = colors.textMuted, fontSize = 11.sp)
            }
            if (isLoading) LoadingRing()
            Spacer(Modifier.width(10.dp))
            RoundAction(Icons.Rounded.Refresh, "تحديث المحتوى", onRefresh)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isTv) .58f else .86f)
                .padding(start = 27.dp, end = 27.dp, bottom = if (isTv) 38.dp else 24.dp),
        ) {
            Text("وصل حديثًا", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                item.name,
                color = Color.White,
                fontSize = if (isTv) 39.sp else 28.sp,
                lineHeight = if (isTv) 47.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.year?.let { InfoPill(it) }
                item.rating?.let { InfoPill("★ $it") }
                item.genre?.takeIf(String::isNotBlank)?.let { InfoPill(it.take(27)) }
            }
            item.plot?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD4D0C5), fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FocusButton(if (item.type == ContentType.SERIES) "عرض الحلقات" else "شاهد الآن", onOpen, compact = true)
                FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false, compact = true)
            }
        }
    }
}

@Composable
private fun HomePlaceholder(loading: Boolean, onRefresh: () -> Unit, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Box(
        Modifier.fillMaxWidth().height(if (isTv) 360.dp else 270.dp).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) LoadingRing(label = "نجهز أحدث الإضافات…")
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("سيظهر أحدث المحتوى هنا", color = colors.textMuted)
            Spacer(Modifier.height(12.dp))
            FocusButton("تحديث", onRefresh, compact = true)
        }
    }
}

@Composable
private fun PosterSection(
    title: String,
    content: List<ContentItem>,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("${content.size}", color = colors.textMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
        ) {
            items(content, key = { "${it.type}:${it.id}" }) { item ->
                CompactPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (isTv) 136.dp else 111.dp),
                    onLongClick = { onToggleFavorite(item) },
                )
            }
        }
    }
}

@Composable
private fun HistorySection(title: String, entries: List<HistoryEntry>, isTv: Boolean, onOpen: (HistoryEntry) -> Unit) {
    val colors = LocalHulkColors.current
    Column {
        Text(title, color = colors.text, fontSize = if (isTv) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 5.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(entries, key = HistoryEntry::key) { entry ->
                HistoryCard(entry, { onOpen(entry) }, Modifier.width(if (isTv) 238.dp else 190.dp))
            }
        }
    }
}

@Composable
private fun PosterCatalogScreen(
    title: String,
    type: ContentType,
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalogs[type]
    val ordered = remember(catalog) { newest(catalog?.items.orEmpty()) }
    val visible = remember(ordered, state.selectedCategoryId, state.searchQuery, state.favorites) {
        ordered.filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                (state.searchQuery.isBlank() || item.name.contains(state.searchQuery.trim(), ignoreCase = true))
        }
    }
    val continueWatching = remember(state.history, state.searchQuery, type) {
        val kind = if (type == ContentType.MOVIE) "movie" else "series"
        state.history.filter { entry ->
            !entry.isLive &&
                entry.streamKind == kind &&
                entry.positionMs > 0L &&
                (state.searchQuery.isBlank() || entry.title.contains(state.searchQuery.trim(), ignoreCase = true))
        }
    }
    val showingContinue = state.selectedCategoryId == CONTINUE_CATEGORY_ID
    val resultCount = if (showingContinue) continueWatching.size else visible.size
    Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp else 13.dp, vertical = if (isTv) 19.dp else 12.dp)) {
        CatalogHeader(title, resultCount, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            ErrorNotice(state.errorMessage)
        }
        Spacer(Modifier.height(11.dp))
        CategoryBar(
            categories = catalog?.categories.orEmpty(),
            selectedId = state.selectedCategoryId,
            onSelect = onSelectCategory,
            showFavorites = true,
            showContinue = true,
        )
        FavoriteHint()
        Spacer(Modifier.height(9.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showingContinue && continueWatching.isNotEmpty()) {
                HistoryGrid(continueWatching, isTv, onOpenHistory)
            } else if (showingContinue) {
                EmptyState("لا توجد مشاهدة غير مكتملة في $title")
            } else if (catalog == null && type in state.loadingTypes) {
                LoadingRing(label = "جاري تحميل $title…", modifier = Modifier.align(Alignment.Center))
            } else if (visible.isEmpty()) {
                EmptyState("لا توجد نتائج مطابقة")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTv) 132.dp else 105.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { "${it.type}:${it.id}" }) { item ->
                        CompactPosterCard(
                            item = item,
                            isFavorite = isFavorite(item),
                            onClick = { onOpen(item) },
                            modifier = Modifier.fillMaxWidth(),
                            onLongClick = { onToggleFavorite(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCatalogScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val catalog = state.catalogs[ContentType.LIVE]
    LaunchedEffect(catalog?.categories, state.selectedCategoryId) {
        if (state.selectedCategoryId == null) {
            catalog?.categories?.firstOrNull()?.id?.let(onSelectCategory)
        }
    }
    val visible = remember(catalog, state.selectedCategoryId, state.searchQuery, state.favorites) {
        catalog?.items.orEmpty().filter { item ->
            categoryMatches(item, state.selectedCategoryId, isFavorite) &&
                (state.searchQuery.isBlank() || item.name.contains(state.searchQuery.trim(), ignoreCase = true))
        }
    }
    var preview by remember(catalog, state.selectedCategoryId) { mutableStateOf<ContentItem?>(null) }
    LaunchedEffect(visible) {
        if (preview == null || preview !in visible) preview = visible.firstOrNull()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = if (isTv) 23.dp else 12.dp, vertical = if (isTv) 18.dp else 11.dp)) {
        CatalogHeader("البث المباشر", visible.size, state.searchQuery, onSearch, onRefresh, isTv)
        if (state.errorMessage != null) {
            Spacer(Modifier.height(9.dp))
            ErrorNotice(state.errorMessage)
        }
        Spacer(Modifier.height(10.dp))
        CategoryBar(
            categories = catalog?.categories.orEmpty(),
            selectedId = state.selectedCategoryId,
            onSelect = onSelectCategory,
            showFavorites = true,
            showAll = false,
        )
        FavoriteHint()
        Spacer(Modifier.height(8.dp))
        if (catalog == null && ContentType.LIVE in state.loadingTypes) {
            LoadingRing(label = "جاري تحميل القنوات…", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 90.dp))
        } else if (visible.isEmpty()) {
            EmptyState("لا توجد قنوات مطابقة")
        } else if (isTv) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(
                    modifier = Modifier
                        .width(408.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xA30D0E0B))
                        .padding(9.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("القنوات", color = LocalHulkColors.current.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${visible.size}", color = LocalHulkColors.current.textMuted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        items(visible, key = { it.id }) { channel ->
                            ChannelListItem(
                                item = channel,
                                selected = preview?.id == channel.id,
                                onFocused = { preview = channel },
                                onClick = { onOpen(channel) },
                                isFavorite = isFavorite(channel),
                                onLongClick = { onToggleFavorite(channel) },
                            )
                        }
                    }
                }
                LiveStage(
                    item = preview,
                    isFavorite = preview?.let(isFavorite) == true,
                    onWatch = { preview?.let(onOpen) },
                    onToggleFavorite = { preview?.let(onToggleFavorite) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visible, key = { it.id }) { channel ->
                    ChannelListItem(
                        item = channel,
                        selected = false,
                        onFocused = {},
                        onClick = { onOpen(channel) },
                        isFavorite = isFavorite(channel),
                        onLongClick = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStage(
    item: ContentItem?,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        listOf(colors.gold.copy(alpha = .12f), Color(0xFF090A08)),
                    ),
                ),
        ) {
            if (item == null) {
                Text("اختر قناة", color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
            } else {
                ChannelLogo(item, Modifier.align(Alignment.Center).size(145.dp))
                Box(
                    Modifier.align(Alignment.TopStart).padding(17.dp).clip(CircleShape).background(Color(0xFFD3262E)).padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text("اضغط تشغيل للانتقال إلى ملء الشاشة", color = colors.textMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        if (item != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("على الهواء الآن", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(item.name, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(9.dp))
                FocusButton(if (isFavorite) "★ في المفضلة" else "+ المفضلة", onToggleFavorite, primary = false, compact = true)
                FocusButton("تشغيل القناة", onWatch, compact = true)
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val content = remember(state.catalogs, state.favorites) {
        state.catalogs.values.flatMap { it.items }.filter(isFavorite).distinctBy { "${it.type}:${it.id}" }
    }
    Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {
        PageTitle("قائمتي", "كل ما حفظته في مكان واحد", content.size, Icons.Rounded.Star)
        Spacer(Modifier.height(18.dp))
        if (content.isEmpty() && state.loadingTypes.isEmpty()) {
            EmptyState("لم تضف أي محتوى إلى قائمتك بعد")
        } else {
            ContentGrid(content, isTv, isFavorite, onOpen, onToggleFavorite)
        }
    }
}

@Composable
private fun UnifiedSearchScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val results = remember(state.catalogs, state.searchQuery) {
        val query = state.searchQuery.trim()
        if (query.isBlank()) emptyList() else state.catalogs.values.flatMap { it.items }
            .filter { it.name.contains(query, ignoreCase = true) }
            .distinctBy { "${it.type}:${it.id}" }
    }
    Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {
        PageTitle("البحث", "القنوات والأفلام والمسلسلات", results.size, Icons.Rounded.Search)
        Spacer(Modifier.height(14.dp))
        HulkTextField(state.searchQuery, onSearch, "اكتب اسم المحتوى…", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        if (state.searchQuery.isBlank()) {
            EmptyState("ابدأ بكتابة اسم القناة أو الفيلم أو المسلسل")
        } else if (results.isEmpty()) {
            EmptyState("لا توجد نتائج مطابقة")
        } else {
            Text("${results.size} نتيجة", color = colors.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(9.dp))
            ContentGrid(results, isTv, isFavorite, onOpen, onToggleFavorite)
        }
    }
}

@Composable
private fun DownloadsScreen(
    downloads: List<OfflineDownload>,
    isTv: Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
) {
    val colors = LocalHulkColors.current
    val completed = downloads.count { it.status == OfflineStatus.COMPLETED }
    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED
    }
    val storedBytes = downloads
        .filter { it.status == OfflineStatus.COMPLETED }
        .sumOf { it.totalBytes.coerceAtLeast(it.bytesDownloaded).coerceAtLeast(0L) }

    Column(Modifier.fillMaxSize().padding(if (isTv) 24.dp else 13.dp)) {
        PageTitle("التحميلات", "شاهد أفلامك وحلقاتك بدون انتظار", downloads.size, Icons.Rounded.Download)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("مكتمل  $completed")
            if (active > 0) InfoPill("قيد التحميل  $active")
            if (storedBytes > 0L) InfoPill("المحفوظ  ${formatBytes(storedBytes)}")
        }
        Spacer(Modifier.height(15.dp))
        if (downloads.isEmpty()) {
            EmptyState("ستظهر هنا الأفلام والحلقات التي تختار تحميلها")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 340.dp else 285.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = OfflineDownload::downloadId) { item ->
                    DownloadCard(item, isTv, onPlay, onDelete, onRetry)
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: OfflineDownload,
    isTv: Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 184.dp else 164.dp)
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .10f) else Color(0xFF11120E))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line.copy(alpha = .45f), shape)
            .onFocusChanged { focused = it.hasFocus }
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(if (isTv) 96.dp else 82.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1C15)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(Modifier.size(58.dp).graphicsLayer { alpha = .55f })
            }
            if (item.status == OfflineStatus.COMPLETED) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(colors.gold)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("جاهز", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                item.seriesTitle ?: if (item.streamKind == "movie") "فيلم" else "حلقة",
                color = colors.goldBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                item.title,
                color = colors.text,
                fontSize = if (isTv) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isTv) 18.sp else 16.sp,
            )
            val episodeMeta = listOfNotNull(
                item.season?.let { "الموسم $it" },
                item.episodeNumber?.let { "الحلقة $it" },
            ).joinToString(" • ")
            if (episodeMeta.isNotBlank()) {
                Text(episodeMeta, color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            DownloadProgress(item)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "إعادة المحاولة",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    else -> Text(
                        downloadStatusLabel(item.status),
                        color = colors.goldBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                    )
                }
                FocusButton(
                    "حذف",
                    { onDelete(item) },
                    primary = false,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun DownloadProgress(item: OfflineDownload) {
    val colors = LocalHulkColors.current
    val percent = (item.progress * 100).toInt()
    val label = when {
        item.status == OfflineStatus.COMPLETED -> formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))
        item.totalBytes > 0L -> "$percent%  •  ${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> downloadStatusLabel(item.status)
    }
    Text(label, color = colors.textMuted, fontSize = 8.sp, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) {
        val progress = when (item.status) {
            OfflineStatus.COMPLETED -> 1f
            else -> item.progress
        }
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
    }
}

private fun downloadStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "بانتظار بدء التحميل"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا بسبب الشبكة"
    OfflineStatus.COMPLETED -> "اكتمل التحميل"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        String.format(Locale.US, "%.1f GB", megabytes / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", megabytes)
    }
}

@Composable
private fun ContentGrid(
    content: List<ContentItem>,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 132.dp else 105.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
        contentPadding = PaddingValues(5.dp, 5.dp, 5.dp, 28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(content, key = { "${it.type}:${it.id}" }) { item ->
            CompactPosterCard(
                item = item,
                isFavorite = isFavorite(item),
                onClick = { onOpen(item) },
                modifier = Modifier.fillMaxWidth(),
                onLongClick = { onToggleFavorite(item) },
            )
        }
    }
}

@Composable
private fun HistoryGrid(
    entries: List<HistoryEntry>,
    isTv: Boolean,
    onOpen: (HistoryEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 232.dp else 180.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
        contentPadding = PaddingValues(5.dp, 5.dp, 5.dp, 28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = HistoryEntry::key) { entry ->
            HistoryCard(entry, { onOpen(entry) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsScreen(
    state: HulkUiState,
    isTv: Boolean,
    onRefreshAll: () -> Unit,
    onClearHistory: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) }; Unit }
    val account = state.account
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (isTv) 27.dp else 15.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageTitle("الحساب والإعدادات", "إدارة اشتراكك وتجربة المشاهدة", 0, Icons.Rounded.Settings) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                AccountMetric("الحساب", account?.username?.let { "••••${it.takeLast(4)}" } ?: "—", Modifier.weight(1f))
                AccountMetric("حالة الاشتراك", account?.status ?: "—", Modifier.weight(1f))
                AccountMetric("الصلاحية", account?.let(::accountExpiry) ?: "—", Modifier.weight(1f))
                AccountMetric("الاتصالات", account?.let { "${it.activeConnections} / ${it.maxConnections}" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Column {
                Text("خدمات HULK SA", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("الموقع أصبح امتدادًا للتطبيق", color = colors.textMuted, fontSize = 11.sp)
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    WebsiteCard(Icons.Rounded.Language, "اشتراك أو تجديد", "hulksa.com", { open(WEBSITE_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.Person, "حساب العميل", "دخول برمز البريد", { open(ACCOUNT_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.SupportAgent, "الدعم الفني", "واتساب الرسمي", { open(SUPPORT_URL) }, Modifier.weight(1f))
                    WebsiteCard(Icons.Rounded.Apps, "مركز التطبيقات", "كل أجهزتك", { open(APPS_URL) }, Modifier.weight(1f))
                }
            }
        }
        item {
            SettingsStrip("المحتوى والبيانات") {
                FocusButton("تحديث المكتبة", onRefreshAll, compact = true)
                FocusButton("مسح سجل المشاهدة", onClearHistory, primary = false, compact = true, enabled = state.history.isNotEmpty())
            }
        }
        item {
            SettingsStrip("التطبيق") {
                Text("HULK SA  •  الإصدار ${BuildConfig.VERSION_NAME}", color = colors.textMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                FocusButton("تسجيل الخروج", onLogout, primary = false, compact = true)
            }
        }
    }
}

@Composable
private fun AccountMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xFF11120E)).padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text(label, color = colors.textMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebsiteCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1.035f else 1f, label = "websiteCardScale")
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) colors.gold else Color(0xFF11120E))
            .border(if (focused) 2.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(15.dp),
    ) {
        Icon(icon, title, tint = if (focused) Color.Black else colors.goldBright, modifier = Modifier.size(24.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = if (focused) Color.Black else colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = if (focused) Color.Black.copy(alpha = .65f) else colors.textMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun SettingsStrip(title: String, content: @Composable RowScope.() -> Unit) {
    val colors = LocalHulkColors.current
    Column {
        Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF11120E)).padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun CatalogHeader(
    title: String,
    resultCount: Int,
    query: String,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Column(Modifier.width(if (isTv) 185.dp else 105.dp)) {
            Text(title, color = colors.text, fontSize = if (isTv) 27.sp else 20.sp, fontWeight = FontWeight.Bold)
            Text("$resultCount عنصر", color = colors.textMuted, fontSize = 10.sp)
        }
        HulkTextField(query, onSearch, "ابحث في $title…", Modifier.weight(1f).widthIn(max = 630.dp))
        RoundAction(Icons.Rounded.Refresh, "تحديث", onRefresh)
    }
}

@Composable
private fun CategoryBar(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    showFavorites: Boolean = false,
    showContinue: Boolean = false,
    showAll: Boolean = true,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
    ) {
        if (showAll) {
            item { FocusButton("الكل", { onSelect(null) }, primary = selectedId == null, compact = true) }
        }
        if (showFavorites) {
            item {
                FocusButton(
                    "★ المفضلة",
                    { onSelect(FAVORITES_CATEGORY_ID) },
                    primary = selectedId == FAVORITES_CATEGORY_ID,
                    compact = true,
                )
            }
        }
        if (showContinue) {
            item {
                FocusButton(
                    "▶ استكمال آخر مشاهدة",
                    { onSelect(CONTINUE_CATEGORY_ID) },
                    primary = selectedId == CONTINUE_CATEGORY_ID,
                    compact = true,
                )
            }
        }
        items(categories, key = Category::id) { category ->
            FocusButton(category.name, { onSelect(category.id) }, primary = selectedId == category.id, compact = true)
        }
    }
}

@Composable
private fun FavoriteHint() {
    val colors = LocalHulkColors.current
    Text(
        text = "تلميح: اضغط مطولًا زر OK لإضافة أو إزالة العنصر من المفضلة",
        color = colors.textMuted,
        fontSize = 9.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun PageTitle(title: String, subtitle: String, count: Int, icon: ImageVector) {
    val colors = LocalHulkColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(colors.gold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, title, tint = colors.goldBright, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = colors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(if (count > 0) "$subtitle  •  $count" else subtitle, color = colors.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focused) colors.gold else Color.Black.copy(alpha = .46f))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (focused) Color.Black else colors.text, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun EmptyState(message: String) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandLogo(Modifier.size(70.dp).graphicsLayer { alpha = .65f })
        Spacer(Modifier.height(10.dp))
        Text(message, color = colors.textMuted, fontSize = 13.sp)
    }
}

private fun newest(content: List<ContentItem>): List<ContentItem> =
    content.sortedByDescending { it.addedAtEpochSeconds ?: 0L }

private fun categoryMatches(
    item: ContentItem,
    selectedId: String?,
    isFavorite: (ContentItem) -> Boolean,
): Boolean = when (selectedId) {
    null -> true
    FAVORITES_CATEGORY_ID -> isFavorite(item)
    CONTINUE_CATEGORY_ID -> false
    else -> item.categoryId == selectedId
}

private fun accountExpiry(account: AccountInfo): String {
    val epoch = account.expiresAtEpochSeconds ?: return "اشتراك فعال"
    val millis = epoch * 1000L
    val days = TimeUnit.MILLISECONDS.toDays(millis - System.currentTimeMillis())
    return if (days >= 0) {
        "متبقي $days يوم · ${SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar-SA")).format(Date(millis))}"
    } else {
        "منتهي"
    }
}

private data class DestinationEntry(val destination: MainDestination, val icon: ImageVector, val label: String)

private val destinations = listOf(
    DestinationEntry(MainDestination.HOME, Icons.Rounded.Home, "الرئيسية"),
    DestinationEntry(MainDestination.LIVE, Icons.Rounded.LiveTv, "البث المباشر"),
    DestinationEntry(MainDestination.MOVIES, Icons.Rounded.Movie, "الأفلام"),
    DestinationEntry(MainDestination.SERIES, Icons.Rounded.Tv, "المسلسلات"),
    DestinationEntry(MainDestination.FAVORITES, Icons.Rounded.Favorite, "قائمتي"),
    DestinationEntry(MainDestination.SEARCH, Icons.Rounded.Search, "البحث"),
    DestinationEntry(MainDestination.DOWNLOADS, Icons.Rounded.Download, "التحميلات"),
    DestinationEntry(MainDestination.SETTINGS, Icons.Rounded.Settings, "الإعدادات"),
)

package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.PosterCard
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val FAVORITES_CATEGORY = "__favorites__"

@Composable
fun HomeScreen(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectType: (ContentType) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val catalog = state.catalogs[state.selectedType]
    val visibleItems = remember(
        catalog,
        state.selectedCategoryId,
        state.searchQuery,
        state.favorites,
    ) {
        catalog?.items.orEmpty().filter { item ->
            val categoryMatches = when (state.selectedCategoryId) {
                null -> true
                FAVORITES_CATEGORY -> isFavorite(item)
                else -> item.categoryId == state.selectedCategoryId
            }
            val searchMatches = state.searchQuery.isBlank() ||
                item.name.contains(state.searchQuery.trim(), ignoreCase = true)
            categoryMatches && searchMatches
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                selectedType = state.selectedType,
                account = state.account,
                isTv = isTv,
                onSelectType = onSelectType,
                onLogout = onLogout,
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 178.dp else 132.dp),
                contentPadding = PaddingValues(
                    start = if (isTv) 44.dp else 16.dp,
                    end = if (isTv) 44.dp else 16.dp,
                    bottom = 42.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        if (state.errorMessage != null) {
                            ErrorRow(state.errorMessage, onRefresh)
                            Spacer(Modifier.height(16.dp))
                        }

                        visibleItems.firstOrNull()?.let { featured ->
                            FeaturedHero(
                                item = featured,
                                isTv = isTv,
                                isFavorite = isFavorite(featured),
                                onWatch = { onOpen(featured) },
                                onToggleFavorite = { onToggleFavorite(featured) },
                            )
                            Spacer(Modifier.height(if (isTv) 22.dp else 16.dp))
                        }

                        CategoryRow(
                            categories = catalog?.categories.orEmpty(),
                            selectedId = state.selectedCategoryId,
                            onSelect = onSelectCategory,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HulkTextField(
                                value = state.searchQuery,
                                onValueChange = onSearch,
                                label = "ابحث في ${state.selectedType.arabicName()}…",
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${visibleItems.size} نتيجة",
                                color = colors.textMuted,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = sectionTitle(state.selectedType, state.selectedCategoryId, catalog?.categories.orEmpty()),
                            color = colors.text,
                            fontSize = if (isTv) 24.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                items(visibleItems, key = { "${it.type}:${it.id}" }) { item ->
                    PosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (!state.isLoading && catalog != null && visibleItems.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        EmptyState(onReset = {
                            onSearch("")
                            onSelectCategory(null)
                        })
                    }
                }
            }
        }

        if (state.isLoading && catalog == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .82f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRing(label = "جاري تحميل المحتوى…")
            }
        }
    }
}

@Composable
private fun TopBar(
    selectedType: ContentType,
    account: AccountInfo?,
    isTv: Boolean,
    onSelectType: (ContentType) -> Unit,
    onLogout: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .93f))
            .padding(
                horizontal = if (isTv) 42.dp else 14.dp,
                vertical = if (isTv) 13.dp else 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 13.dp else 7.dp),
    ) {
        BrandLogo(Modifier.size(if (isTv) 76.dp else 54.dp))
        ContentType.entries.forEach { type ->
            FocusButton(
                text = type.arabicName(),
                onClick = { onSelectType(type) },
                primary = type == selectedType,
                compact = !isTv,
                modifier = if (isTv) Modifier else Modifier.weight(1f),
            )
        }
        Spacer(Modifier.weight(1f))
        if (isTv && account != null) {
            AccountSummary(account)
        }
        FocusButton("خروج", onLogout, primary = false, compact = !isTv)
    }
}

@Composable
private fun AccountSummary(account: AccountInfo) {
    val colors = LocalHulkColors.current
    val expiry = account.expiresAtEpochSeconds?.let { epoch ->
        val remaining = TimeUnit.MILLISECONDS.toDays(epoch * 1000L - System.currentTimeMillis())
        if (remaining >= 0) "متبقي $remaining يوم" else "منتهي"
    } ?: "اشتراك فعال"
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            text = "حساب ••••${account.username.takeLast(4)}",
            color = colors.text,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            text = expiry,
            color = if (expiry == "منتهي") colors.danger else colors.gold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FeaturedHero(
    item: ContentItem,
    isTv: Boolean,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 310.dp else 220.dp)
            .clip(RoundedCornerShape(if (isTv) 24.dp else 18.dp))
            .background(colors.surface),
    ) {
        if (!item.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrandLogo(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(if (isTv) 250.dp else 170.dp)
                    .padding(20.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .97f), Color.Black.copy(alpha = .64f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (isTv) .58f else .82f)
                .padding(if (isTv) 34.dp else 20.dp),
        ) {
            Text(
                text = when (item.type) {
                    ContentType.LIVE -> "على الهواء الآن"
                    ContentType.MOVIE -> "مختار لك"
                    ContentType.SERIES -> "مسلسل مقترح"
                },
                color = colors.goldBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.name,
                color = Color.White,
                fontSize = if (isTv) 34.sp else 25.sp,
                lineHeight = if (isTv) 42.sp else 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Text(meta, color = colors.textMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(if (isTv) 20.dp else 13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusButton(
                    text = if (item.type == ContentType.SERIES) "عرض الحلقات" else "شاهد الآن",
                    onClick = onWatch,
                )
                FocusButton(
                    text = if (isFavorite) "★ محفوظ" else "☆ للمفضلة",
                    onClick = onToggleFavorite,
                    primary = false,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            FocusButton("الكل", { onSelect(null) }, primary = selectedId == null)
        }
        item {
            FocusButton("★ المفضلة", { onSelect(FAVORITES_CATEGORY) }, primary = selectedId == FAVORITES_CATEGORY)
        }
        items(categories.size, key = { categories[it].id }) { index ->
            val category = categories[index]
            FocusButton(
                text = category.name,
                onClick = { onSelect(category.id) },
                primary = selectedId == category.id,
            )
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErrorNotice(message, Modifier.weight(1f))
        FocusButton("إعادة المحاولة", onRefresh, primary = false)
    }
}

@Composable
private fun EmptyState(onReset: () -> Unit) {
    val colors = LocalHulkColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 50.dp),
    ) {
        Text("لا توجد نتائج مطابقة", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("غيّر التصنيف أو امسح عبارة البحث", color = colors.textMuted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        FocusButton("عرض الكل", onReset, primary = false)
    }
}

private fun ContentType.arabicName(): String = when (this) {
    ContentType.LIVE -> "مباشر"
    ContentType.MOVIE -> "أفلام"
    ContentType.SERIES -> "مسلسلات"
}

private fun sectionTitle(type: ContentType, selectedId: String?, categories: List<Category>): String {
    if (selectedId == FAVORITES_CATEGORY) return "المفضلة"
    return categories.firstOrNull { it.id == selectedId }?.name ?: type.arabicName()
}

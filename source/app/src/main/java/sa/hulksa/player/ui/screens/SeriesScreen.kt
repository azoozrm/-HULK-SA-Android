package sa.hulksa.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun SeriesScreen(
    series: ContentItem,
    details: ContentDetails?,
    episodes: List<Episode>,
    isLoading: Boolean,
    errorMessage: String?,
    isTv: Boolean,
    isFavorite: Boolean,
    downloads: List<OfflineDownload>,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val seasons = remember(episodes) { episodes.map(Episode::season).distinct().sorted() }
    var selectedSeason by remember(seasons) { mutableIntStateOf(seasons.firstOrNull() ?: 0) }
    val visibleEpisodes = remember(episodes, selectedSeason) {
        if (selectedSeason == 0) episodes else episodes.filter { it.season == selectedSeason }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTv) 218.dp else 155.dp),
            contentPadding = PaddingValues(start = if (isTv) 25.dp else 13.dp, end = if (isTv) 25.dp else 13.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesHero(
                    series = series,
                    details = details,
                    firstEpisode = episodes.firstOrNull(),
                    isTv = isTv,
                    isFavorite = isFavorite,
                    onBack = onBack,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الحلقات", color = colors.text, fontSize = if (isTv) 22.sp else 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${visibleEpisodes.size} حلقة", color = colors.textMuted, fontSize = 10.sp)
                    }
                    if (seasons.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(seasons, key = { it }) { season ->
                                FocusButton("الموسم $season", { selectedSeason = season }, primary = selectedSeason == season, compact = true)
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Spacer(Modifier.height(9.dp))
                        ErrorNotice(errorMessage)
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }
            items(visibleEpisodes, key = Episode::id) { episode ->
                EpisodeCard(
                    episode = episode,
                    download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" },
                    onClick = { onPlay(episode) },
                    onDownload = { onDownload(episode) },
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .67f)), contentAlignment = Alignment.Center) {
                LoadingRing(label = "جاري تجهيز الحلقات…")
            }
        }
    }
}

@Composable
private fun SeriesHero(
    series: ContentItem,
    details: ContentDetails?,
    firstEpisode: Episode?,
    isTv: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (isTv) 292.dp else 250.dp)
            .background(Color(0xFF0B0C09)),
    ) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(backdrop, series.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(170.dp).graphicsLayer { alpha = .25f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(.2f), .5f to Color.Transparent, 1f to colors.background),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(.18f), colors.background.copy(.95f))),
            ),
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(Modifier.size(if (isTv) 60.dp else 48.dp))
            Spacer(Modifier.weight(1f))
            FocusButton("رجوع", onBack, primary = false, compact = true)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(if (isTv) .68f else .92f).padding(bottom = 22.dp),
        ) {
            Text("مسلسل", color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                series.name,
                color = Color.White,
                fontSize = if (isTv) 35.sp else 25.sp,
                lineHeight = if (isTv) 42.sp else 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                series.year?.let { InfoPill(it) }
                series.rating?.let { InfoPill("★ $it") }
                (details?.genre ?: series.genre)?.takeIf(String::isNotBlank)?.let { InfoPill(it.take(28)) }
            }
            val plot = details?.plot ?: series.plot
            if (!plot.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(plot, color = Color(0xFFD6D2C8), fontSize = 11.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FocusButton("▶ ابدأ المشاهدة", { firstEpisode?.let(onPlay) }, enabled = firstEpisode != null, compact = true)
                FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false, compact = true)
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    download: OfflineDownload?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 13.dp.toPx() else 0f
                }
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color(0xFF14150F))
                .border(if (focused) 3.dp else 0.dp, if (focused) colors.goldBright else Color.Transparent, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            if (!episode.posterUrl.isNullOrBlank()) {
                AsyncImage(episode.posterUrl, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                BrandLogo(Modifier.align(Alignment.Center).size(54.dp).graphicsLayer { alpha = .18f })
                Text(
                    "الحلقة ${episode.episodeNumber}",
                    color = colors.text.copy(alpha = .78f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .92f)))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(9.dp)) {
                Text("م${episode.season}  •  ح${episode.episodeNumber}", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(episode.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                episode.duration?.let { Text(it, color = colors.textMuted, fontSize = 8.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(6.dp))
        FocusButton(
            text = when (download?.status) {
                OfflineStatus.COMPLETED -> "✓ تم التحميل"
                OfflineStatus.DOWNLOADING,
                OfflineStatus.QUEUED,
                OfflineStatus.PAUSED,
                -> "↓ جاري التحميل"
                OfflineStatus.FAILED -> "↻ إعادة التحميل"
                null -> "↓ تحميل الحلقة"
            },
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            compact = true,
            enabled = download == null || download.status == OfflineStatus.FAILED,
        )
    }
}

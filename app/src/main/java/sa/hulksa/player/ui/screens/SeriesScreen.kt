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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.CompactPosterCard
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
    history: List<HistoryEntry>,
    relatedItems: List<ContentItem>,
    isRelatedFavorite: (ContentItem) -> Boolean,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onCancelDownload: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRelatedFavorite: (ContentItem) -> Unit,
    onOpenRelated: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val relatedFavoriteOverrides = remember(series.id) { mutableStateMapOf<String, Boolean>() }
    val relatedIsFavorite: (ContentItem) -> Boolean = { related ->
        relatedFavoriteOverrides["${related.type.name}:${related.id}"] ?: isRelatedFavorite(related)
    }
    val toggleRelatedFavorite: (ContentItem) -> Unit = { related ->
        val key = "${related.type.name}:${related.id}"
        relatedFavoriteOverrides[key] = !relatedIsFavorite(related)
        onToggleRelatedFavorite(related)
    }
    val orderedEpisodes = remember(episodes) { episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber)) }
    val seasons = remember(orderedEpisodes) { orderedEpisodes.map(Episode::season).distinct() }
    val historyByKey = remember(history) { history.associateBy(HistoryEntry::key) }
    val resumePair = remember(orderedEpisodes, history) {
        orderedEpisodes.mapNotNull { episode ->
            val entry = historyByKey["SERIES:${episode.id}"] ?: return@mapNotNull null
            val progress = entry.watchProgress() ?: return@mapNotNull null
            Triple(episode, entry, progress)
        }.maxByOrNull { it.second.updatedAtEpochMs }
    }
    val completedCount = remember(orderedEpisodes, history) {
        orderedEpisodes.count { episode -> historyByKey["SERIES:${episode.id}"]?.isCompleted() == true }
    }
    var selectedSeason by rememberSaveable(series.id, seasons) {
        mutableIntStateOf(resumePair?.first?.season ?: seasons.firstOrNull() ?: 0)
    }
    LaunchedEffect(resumePair?.first?.id) {
        resumePair?.first?.let { latest -> selectedSeason = latest.season }
    }
    val visibleEpisodes = remember(orderedEpisodes, selectedSeason) {
        if (selectedSeason == 0) orderedEpisodes else orderedEpisodes.filter { it.season == selectedSeason }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTv) 218.dp else 155.dp),
            contentPadding = PaddingValues(
                start = if (isTv) 36.dp else 12.dp,
                end = if (isTv) 36.dp else 12.dp,
                bottom = if (isTv) 42.dp else 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 15.dp else 10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesHero(
                    series = series,
                    details = details,
                    firstEpisode = orderedEpisodes.firstOrNull(),
                    resumeEpisode = resumePair?.first,
                    resumeEntry = resumePair?.second,
                    totalEpisodes = orderedEpisodes.size,
                    completedEpisodes = completedCount,
                    isTv = isTv,
                    isFavorite = isFavorite,
                    onBack = onBack,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.padding(
                        start = if (isTv) 30.dp else 15.dp,
                        end = if (isTv) 30.dp else 15.dp,
                        top = 8.dp,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("الحلقات", color = colors.text, fontSize = if (isTv) 24.sp else 19.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (completedCount > 0) "$completedCount مكتملة من ${orderedEpisodes.size}" else "${orderedEpisodes.size} حلقة",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        resumePair?.let { (episode, entry, _) ->
                            Text(
                                "الحلقة الحالية  •  الموسم ${episode.season}  •  الحلقة ${episode.episodeNumber}  •  ${seriesFormatTime(entry.positionMs)}",
                                color = colors.goldBright,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (seasons.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(seasons, key = { it }) { season ->
                                FocusButton(
                                    "الموسم $season",
                                    { selectedSeason = season },
                                    primary = selectedSeason == season,
                                    compact = true,
                                )
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        ErrorNotice(errorMessage)
                    }
                }
            }

            items(
                items = visibleEpisodes,
                key = Episode::id,
                contentType = { "episode" },
            ) { episode ->
                EpisodeCard(
                    episode = episode,
                    download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" },
                    historyEntry = historyByKey["SERIES:${episode.id}"],
                    onClick = { onPlay(episode) },
                    onDownload = { onDownload(episode) },
                    onCancelDownload = { onCancelDownload(episode) },
                    modifier = Modifier.padding(
                        start = if (isTv) 10.dp else 7.dp,
                        end = if (isTv) 10.dp else 7.dp,
                    ),
                )
            }

            if (relatedItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RelatedSeriesRow(
                        items = relatedItems,
                        isFavorite = relatedIsFavorite,
                        onToggleFavorite = toggleRelatedFavorite,
                        onOpen = onOpenRelated,
                        isTv = isTv,
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .64f)), contentAlignment = Alignment.Center) {
                LoadingRing(label = "جاري تجهيز تفاصيل المسلسل…")
            }
        }
    }
}

@Composable
private fun SeriesHero(
    series: ContentItem,
    details: ContentDetails?,
    firstEpisode: Episode?,
    resumeEpisode: Episode?,
    resumeEntry: HistoryEntry?,
    totalEpisodes: Int,
    completedEpisodes: Int,
    isTv: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    val heroEpisode = resumeEpisode ?: firstEpisode
    val progress = resumeEntry?.watchProgress()

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (isTv) 390.dp else 400.dp)
            .background(Color(0xFF0B0C09)),
    ) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(backdrop, series.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(180.dp).graphicsLayer { alpha = .22f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .24f),
                    .52f to Color.Black.copy(alpha = .30f),
                    1f to colors.background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(colors.background.copy(alpha = .96f), Color.Black.copy(alpha = .48f), Color.Transparent),
                ),
            ),
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                .padding(horizontal = if (isTv) 30.dp else 15.dp, vertical = if (isTv) 22.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(Modifier.size(if (isTv) 62.dp else 48.dp))
            Spacer(Modifier.weight(1f))
            FocusButton("رجوع", onBack, primary = false, compact = true)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = if (isTv) 34.dp else 17.dp,
                    end = if (isTv) 34.dp else 17.dp,
                    bottom = if (isTv) 28.dp else 19.dp,
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("مسلسل", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    series.name,
                    color = Color.White,
                    fontSize = if (isTv) 39.sp else 25.sp,
                    lineHeight = if (isTv) 46.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    series.year?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                    series.rating?.takeIf(String::isNotBlank)?.let { InfoPill("★ $it") }
                    if (totalEpisodes > 0) InfoPill("$totalEpisodes حلقة")
                    if (completedEpisodes > 0) InfoPill("✓ $completedEpisodes مكتملة")
                }
                (details?.genre ?: series.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                    Spacer(Modifier.height(7.dp))
                    Text(genre, color = colors.goldBright.copy(alpha = .9f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val plot = details?.plot ?: series.plot
                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        plot,
                        color = Color(0xFFD6D2C8),
                        fontSize = if (isTv) 13.sp else 11.sp,
                        lineHeight = if (isTv) 20.sp else 17.sp,
                        maxLines = if (isTv) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null && resumeEntry != null && resumeEpisode != null) {
                    Spacer(Modifier.height(13.dp))
                    Column(Modifier.fillMaxWidth(.72f)) {
                        Text(
                            "متابعة الموسم ${resumeEpisode.season} الحلقة ${resumeEpisode.episodeNumber} من ${seriesFormatTime(resumeEntry.positionMs)}",
                            color = colors.goldBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .18f))) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FocusButton(
                        text = if (resumeEpisode != null && resumeEntry != null) {
                            "▶ استكمال الموسم ${resumeEpisode.season} • الحلقة ${resumeEpisode.episodeNumber}"
                        } else {
                            "▶ ابدا المشاهدة"
                        },
                        onClick = { heroEpisode?.let(onPlay) },
                        enabled = heroEpisode != null,
                        compact = true,
                    )
                    FocusButton(if (isFavorite) "★ في قائمتي" else "+ قائمتي", onToggleFavorite, primary = false, compact = true)
                }
            }

            SeriesPoster(
                posterUrl = series.posterUrl,
                title = series.name,
                modifier = Modifier.width(if (isTv) 150.dp else 108.dp),
            )
        }
    }
}

@Composable
private fun SeriesPoster(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(2.dp, colors.gold.copy(alpha = .42f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(posterUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.fillMaxSize().padding(22.dp).graphicsLayer { alpha = .5f })
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    download: OfflineDownload?,
    historyEntry: HistoryEntry?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "episodeScale")
    val shape = RoundedCornerShape(13.dp)
    val watchProgress = historyEntry?.watchProgress()
    val completed = historyEntry?.isCompleted() == true

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 14.dp.toPx() else 0f
                }
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color(0xFF14150F))
                .border(if (focused) 3.dp else 1.dp, if (focused) colors.goldBright else colors.line.copy(alpha = .42f), shape)
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
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .93f)))))

            when {
                completed -> EpisodeBadge("✓ تمت المشاهدة", Modifier.align(Alignment.TopEnd), success = true)
                watchProgress != null && historyEntry != null -> EpisodeBadge(
                    "استكمال ${seriesFormatTime(historyEntry.positionMs)}",
                    Modifier.align(Alignment.TopEnd),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = if (watchProgress != null || completed) 12.dp else 9.dp, top = 9.dp),
            ) {
                Text("م${episode.season}  •  ح${episode.episodeNumber}", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(episode.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                episode.duration?.takeIf(String::isNotBlank)?.let { Text(it, color = colors.textMuted, fontSize = 8.sp, maxLines = 1) }
            }

            if (watchProgress != null || completed) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .20f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (completed) 1f else watchProgress ?: 0f)
                            .fillMaxHeight()
                            .background(colors.goldBright),
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            when {
                completed -> "✓ تمت مشاهدة الحلقة"
                watchProgress != null && historyEntry != null -> "▶ استكمال المشاهدة من ${seriesFormatTime(historyEntry.positionMs)}"
                else -> "▶ تشغيل الحلقة"
            },
            color = if (completed) colors.textMuted else colors.goldBright,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))

        if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
            DownloadProgress(download)
            Spacer(Modifier.height(6.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FocusButton(
                text = episodeDownloadLabel(download),
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                primary = false,
                compact = true,
                enabled = download?.status != OfflineStatus.COMPLETED,
            )
            if (download != null) {
                FocusButton(
                    text = if (download.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                    onClick = onCancelDownload,
                    modifier = Modifier.weight(.72f),
                    primary = false,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun EpisodeBadge(text: String, modifier: Modifier = Modifier, success: Boolean = false) {
    val colors = LocalHulkColors.current
    Text(
        text = text,
        color = if (success) colors.goldBright else Color.Black,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (success) Color.Black.copy(alpha = .78f) else colors.goldBright)
            .border(1.dp, colors.goldBright.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun DownloadProgress(download: OfflineDownload) {
    val colors = LocalHulkColors.current
    val percent = (download.progress * 100).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = .06f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (download.status) {
                    OfflineStatus.PAUSED -> "متوقف مؤقتا"
                    OfflineStatus.WAITING_SCHEDULE -> "مجدول للساعة 2 ليلا"
                    OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
                    OfflineStatus.WAITING_STORAGE -> "بانتظار التخزين"
                    OfflineStatus.CHECKING -> "فحص الحجم والمساحة"
                    OfflineStatus.QUEUED -> "في قائمة الانتظار"
                    else -> "جاري تحميل الحلقة"
                },
                color = colors.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("$percent%", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(download.progress).height(4.dp).background(colors.goldBright))
        }
        if (download.totalBytes > 0L) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${episodeFormatBytes(download.bytesDownloaded)} / ${episodeFormatBytes(download.totalBytes)}",
                color = colors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RelatedSeriesRow(
    items: List<ContentItem>,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Text(
            "مسلسلات مشابهة",
            color = colors.text,
            fontSize = if (isTv) 23.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (isTv) 30.dp else 15.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            contentPadding = PaddingValues(horizontal = if (isTv) 30.dp else 15.dp, vertical = 6.dp),
        ) {
            items(items, key = ContentItem::id) { item ->
                CompactPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (isTv) 142.dp else 112.dp),
                    onLongClick = { onToggleFavorite(item) },
                )
            }
        }
    }
}

private fun HistoryEntry.watchProgress(): Float? {
    if (positionMs < 30_000L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

private fun HistoryEntry.isCompleted(): Boolean =
    durationMs > 0L && positionMs.toDouble() / durationMs.toDouble() >= .95

private fun episodeDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ايقاف ${(download.progress * 100).toInt()}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف التحميل"
    OfflineStatus.FAILED -> "↻ اعادة التحميل"
    null -> "↓ تحميل الحلقة"
}

private fun episodeFormatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}

private fun seriesFormatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import sa.hulksa.player.ui.components.SeriesPosterCard
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
fun KidsMobileMovieDetailsScreen(
    item: ContentItem,
    details: ContentDetails?,
    isLoading: Boolean,
    errorMessage: String?,
    isFavorite: Boolean,
    download: OfflineDownload?,
    historyEntry: HistoryEntry?,
    relatedItems: List<ContentItem>,
    isRelatedFavorite: (ContentItem) -> Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRelatedFavorite: (ContentItem) -> Unit,
    onOpenRelated: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl
    val resume = historyEntry?.positionMs?.takeIf { it >= 30_000L }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                KidsMobileHero(
                    title = item.name,
                    label = "فيلم",
                    artwork = backdrop,
                    onBack = onBack,
                )
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        item.name,
                        color = colors.text,
                        fontSize = 27.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item.year?.takeIf(String::isNotBlank)?.let { year -> item { InfoPill(year) } }
                        item.rating?.takeIf(String::isNotBlank)?.let { rating -> item { InfoPill("★ $rating") } }
                        details?.duration?.takeIf(String::isNotBlank)?.let { duration -> item { InfoPill(duration) } }
                    }
                    (details?.genre ?: item.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                        Spacer(Modifier.height(10.dp))
                        Text(genre, color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    val plot = details?.plot ?: item.plot
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(11.dp))
                        Text(
                            plot,
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = if (resume != null) "▶ استكمال المشاهدة" else "▶ مشاهدة",
                            onClick = onPlay,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        FocusButton(
                            text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                            onClick = onToggleFavorite,
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = kidsMovieDownloadLabel(download),
                            onClick = onDownload,
                            primary = false,
                            compact = true,
                            enabled = download?.status != OfflineStatus.COMPLETED,
                            modifier = Modifier.weight(1f),
                        )
                        if (download != null) {
                            FocusButton(
                                text = if (download.status == OfflineStatus.COMPLETED) "حذف التحميل" else "إلغاء التحميل",
                                onClick = onCancelDownload,
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (download != null && download.status != OfflineStatus.COMPLETED) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            kidsDownloadStatus(download),
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (errorMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNotice(errorMessage)
                    }
                }
            }

            if (hasKidsMovieInfo(details)) {
                item {
                    KidsMovieInfoCard(details)
                }
            }

            if (relatedItems.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
                        Text(
                            "أعمال مشابهة",
                            color = colors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(relatedItems, key = { "movie:${it.id}" }) { related ->
                                CompactPosterCard(
                                    item = related,
                                    isFavorite = isRelatedFavorite(related),
                                    onClick = { onOpenRelated(related) },
                                    modifier = Modifier.width(116.dp),
                                    onLongClick = { onToggleRelatedFavorite(related) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRing(label = "جاري تجهيز التفاصيل…")
            }
        }
    }
}

@Composable
fun KidsMobileSeriesDetailsScreen(
    series: ContentItem,
    details: ContentDetails?,
    episodes: List<Episode>,
    isLoading: Boolean,
    errorMessage: String?,
    isFavorite: Boolean,
    notificationsEnabled: Boolean,
    notificationToggleAvailable: Boolean,
    targetEpisodeId: Int?,
    targetSeason: Int?,
    targetEpisodeNumber: Int?,
    downloads: List<OfflineDownload>,
    history: List<HistoryEntry>,
    relatedItems: List<ContentItem>,
    isRelatedFavorite: (ContentItem) -> Boolean,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onCancelDownload: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleNotifications: () -> Unit,
    onToggleRelatedFavorite: (ContentItem) -> Unit,
    onOpenRelated: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    val orderedEpisodes = remember(episodes) {
        episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber))
    }
    val seasons = remember(orderedEpisodes) { orderedEpisodes.map(Episode::season).filter { it > 0 }.distinct() }
    val historyByKey = remember(history) { history.associateBy(HistoryEntry::key) }
    val resumeEpisode = remember(orderedEpisodes, history) {
        orderedEpisodes.mapNotNull { episode ->
            historyByKey["SERIES:${episode.id}"]?.let { entry -> episode to entry }
        }.maxByOrNull { it.second.updatedAtEpochMs }?.first
    }
    val targetEpisode = remember(orderedEpisodes, targetEpisodeId, targetSeason, targetEpisodeNumber) {
        if (targetEpisodeId != null) {
            orderedEpisodes.firstOrNull { it.id == targetEpisodeId }
        } else {
            orderedEpisodes.firstOrNull {
                targetSeason != null &&
                    targetEpisodeNumber != null &&
                    it.season == targetSeason &&
                    it.episodeNumber == targetEpisodeNumber
            }
        }
    }
    var selectedSeason by rememberSaveable(
        series.id,
        seasons,
        targetEpisodeId,
        targetSeason,
        targetEpisodeNumber,
    ) {
        mutableIntStateOf(
            targetEpisode?.season
                ?: targetSeason?.takeIf { it in seasons }
                ?: resumeEpisode?.season
                ?: seasons.firstOrNull()
                ?: 0,
        )
    }
    LaunchedEffect(targetEpisode?.id, targetSeason, seasons, resumeEpisode?.id) {
        selectedSeason = targetEpisode?.season
            ?: targetSeason?.takeIf { it in seasons }
            ?: resumeEpisode?.season
            ?: selectedSeason
    }
    val visibleEpisodes = remember(orderedEpisodes, selectedSeason) {
        if (selectedSeason == 0) orderedEpisodes else orderedEpisodes.filter { it.season == selectedSeason }
    }
    val heroEpisode = targetEpisode ?: resumeEpisode ?: orderedEpisodes.firstOrNull()
    val heroEpisodeIndex = orderedEpisodes.indexOfFirst { it.id == heroEpisode?.id }
    val previousEpisode = if (heroEpisodeIndex >= 0) orderedEpisodes.getOrNull(heroEpisodeIndex - 1) else null
    val nextEpisode = if (heroEpisodeIndex >= 0) orderedEpisodes.getOrNull(heroEpisodeIndex + 1) else null
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    val listState = rememberLazyListState()
    LaunchedEffect(targetEpisode?.id, selectedSeason, visibleEpisodes) {
        val targetIndex = visibleEpisodes.indexOfFirst { it.id == targetEpisode?.id }
        if (targetIndex >= 0) {
            val episodeStartIndex = if (seasons.isNotEmpty()) 3 else 2
            listState.scrollToItem(episodeStartIndex + targetIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            item {
                KidsMobileHero(
                    title = series.name,
                    label = "مسلسل",
                    artwork = backdrop,
                    onBack = onBack,
                )
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        series.name,
                        color = colors.text,
                        fontSize = 27.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        series.year?.takeIf(String::isNotBlank)?.let { year -> item { InfoPill(year) } }
                        series.rating?.takeIf(String::isNotBlank)?.let { rating -> item { InfoPill("★ $rating") } }
                        if (orderedEpisodes.isNotEmpty()) item { InfoPill("${orderedEpisodes.size} حلقة") }
                        if (seasons.isNotEmpty()) item { InfoPill("${seasons.size} موسم") }
                    }
                    (details?.genre ?: series.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                        Spacer(Modifier.height(10.dp))
                        Text(genre, color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    val plot = details?.plot ?: series.plot
                    if (!plot.isNullOrBlank()) {
                        Spacer(Modifier.height(11.dp))
                        Text(
                            plot,
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    FocusButton(
                        text = when {
                            targetEpisode != null -> "▶ مشاهدة الحلقة الجديدة"
                            resumeEpisode != null -> "▶ استكمال المشاهدة"
                            else -> "▶ ابدأ المشاهدة"
                        },
                        onClick = { heroEpisode?.let(onPlay) },
                        enabled = heroEpisode != null,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(seriesDetailsActionHeightDp().dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = previousEpisode?.let {
                                "السابق · S${it.season} E${it.episodeNumber}"
                            } ?: "السابق",
                            onClick = { previousEpisode?.let(onPlay) },
                            enabled = previousEpisode != null,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .weight(1f)
                                .height(seriesDetailsActionHeightDp().dp),
                        )
                        FocusButton(
                            text = nextEpisode?.let {
                                "التالي · S${it.season} E${it.episodeNumber}"
                            } ?: "التالي",
                            onClick = { nextEpisode?.let(onPlay) },
                            enabled = nextEpisode != null,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .weight(1f)
                                .height(seriesDetailsActionHeightDp().dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                            onClick = onToggleFavorite,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            modifier = Modifier
                                .weight(1f)
                                .height(seriesDetailsActionHeightDp().dp),
                        )
                        FocusButton(
                            text = if (notificationsEnabled) {
                                "التنبيهات مفعلة"
                            } else {
                                "نبهني عند نزول حلقة جديدة"
                            },
                            onClick = onToggleNotifications,
                            enabled = notificationsEnabled || notificationToggleAvailable,
                            primary = false,
                            outlined = true,
                            compact = true,
                            scaleOnFocus = false,
                            accent = notificationsEnabled,
                            leadingIcon = Icons.Rounded.Notifications,
                            textMaxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .height(seriesDetailsActionHeightDp().dp),
                        )
                    }
                    if (errorMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNotice(errorMessage)
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("الحلقات", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons, key = { it }) { season ->
                                FocusButton(
                                    text = "الموسم $season",
                                    onClick = { selectedSeason = season },
                                    primary = selectedSeason == season,
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            }

            items(visibleEpisodes, key = Episode::id) { episode ->
                val download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                KidsMobileEpisodeCard(
                    episode = episode,
                    highlighted = targetEpisode?.id == episode.id,
                    fallbackArtwork = backdrop,
                    download = download,
                    historyEntry = historyByKey["SERIES:${episode.id}"],
                    onPlay = { onPlay(episode) },
                    onDownload = { onDownload(episode) },
                    onCancelDownload = { onCancelDownload(episode) },
                )
            }

            if (relatedItems.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                        Text(
                            "مسلسلات مشابهة",
                            color = colors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(relatedItems, key = { "series:${it.id}" }) { related ->
                                SeriesPosterCard(
                                    item = related,
                                    isFavorite = isRelatedFavorite(related),
                                    onClick = { onOpenRelated(related) },
                                    modifier = Modifier.width(116.dp),
                                    onLongClick = { onToggleRelatedFavorite(related) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRing(label = "جاري تجهيز تفاصيل المسلسل…")
            }
        }
    }
}

@Composable
private fun KidsMobileHero(
    title: String,
    label: String,
    artwork: String?,
    onBack: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(colors.surface),
    ) {
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(120.dp).graphicsLayer { alpha = .3f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .26f), Color.Black.copy(alpha = .34f), colors.background),
                ),
            ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(Modifier.size(46.dp))
            Spacer(Modifier.weight(1f))
            FocusButton("رجوع", onBack, primary = false, compact = true)
        }
        Text(
            label,
            color = colors.goldBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun KidsMobileEpisodeCard(
    episode: Episode,
    highlighted: Boolean,
    fallbackArtwork: String?,
    download: OfflineDownload?,
    historyEntry: HistoryEntry?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val artwork = episode.posterUrl?.takeIf(String::isNotBlank) ?: fallbackArtwork
    val progress = historyEntry?.let(::kidsHistoryProgress)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = .94f))
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) colors.goldBright else colors.line.copy(alpha = .55f),
                RoundedCornerShape(16.dp),
            )
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceRaised),
        ) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(Modifier.align(Alignment.Center).size(64.dp).graphicsLayer { alpha = .3f })
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))),
                ),
            )
            Text(
                "الموسم ${episode.season} · الحلقة ${episode.episodeNumber}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        episode.title.takeIf(String::isNotBlank)?.let { title ->
            Text(
                title,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
        }
        if (progress != null) {
            Text(
                "تمت مشاهدة ${(progress * 100).toInt()}%",
                color = colors.goldBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(
                text = if (progress != null) "▶ استكمال" else "▶ مشاهدة",
                onClick = onPlay,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            FocusButton(
                text = kidsEpisodeDownloadLabel(download),
                onClick = onDownload,
                primary = false,
                compact = true,
                enabled = download?.status != OfflineStatus.COMPLETED,
                modifier = Modifier.weight(1f),
            )
        }
        if (download != null) {
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(kidsDownloadStatus(download), color = colors.textMuted, fontSize = 10.sp)
                FocusButton(
                    text = if (download.status == OfflineStatus.COMPLETED) "حذف" else "إلغاء",
                    onClick = onCancelDownload,
                    primary = false,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun KidsMovieInfoCard(details: ContentDetails?) {
    val colors = LocalHulkColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.surface.copy(alpha = .86f))
            .border(1.dp, colors.line.copy(alpha = .65f), RoundedCornerShape(15.dp))
            .padding(15.dp),
    ) {
        Text("معلومات الفيلم", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(9.dp))
        details?.releaseDate?.takeIf(String::isNotBlank)?.let { KidsInfoLine("تاريخ العرض", it) }
        details?.director?.takeIf(String::isNotBlank)?.let { KidsInfoLine("الإخراج", it) }
        details?.cast?.takeIf(String::isNotBlank)?.let { KidsInfoLine("البطولة", it) }
    }
}

@Composable
private fun KidsInfoLine(label: String, value: String) {
    val colors = LocalHulkColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
        Text(value, color = colors.textMuted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

private fun hasKidsMovieInfo(details: ContentDetails?): Boolean =
    !details?.releaseDate.isNullOrBlank() || !details?.director.isNullOrBlank() || !details?.cast.isNullOrBlank()

private fun kidsHistoryProgress(entry: HistoryEntry): Float? {
    if (entry.positionMs < 30_000L || entry.durationMs <= 0L) return null
    return (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

private fun kidsMovieDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ إيقاف التحميل"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف التحميل"
    OfflineStatus.FAILED -> "↻ إعادة التحميل"
    null -> "↓ تحميل الفيلم"
}

private fun kidsEpisodeDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ محملة"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ إيقاف"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف"
    OfflineStatus.FAILED -> "↻ إعادة"
    null -> "↓ تحميل"
}

private fun kidsDownloadStatus(download: OfflineDownload): String = when (download.status) {
    OfflineStatus.COMPLETED -> "جاهز للمشاهدة بدون إنترنت"
    OfflineStatus.QUEUED -> "في قائمة الانتظار"
    OfflineStatus.CHECKING -> "جاري تجهيز التحميل"
    OfflineStatus.DOWNLOADING -> "جاري التحميل ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED -> "متوقف مؤقتًا"
    OfflineStatus.WAITING_SCHEDULE -> "مجدول للتحميل"
    OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار مساحة تخزين"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

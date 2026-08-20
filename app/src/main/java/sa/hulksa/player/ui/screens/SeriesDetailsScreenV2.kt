package sa.hulksa.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import sa.hulksa.player.data.SeriesCardMetadataStore
import sa.hulksa.player.data.SeriesCardTechnicalMetadata
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.SeriesPosterCard
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

@Composable
fun SeriesDetailsScreenV2(
    series: ContentItem,
    details: ContentDetails?,
    episodes: List<Episode>,
    isLoading: Boolean,
    errorMessage: String?,
    isTv: Boolean,
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
    val context = LocalContext.current
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    var technicalMetadata by remember(series.id) { mutableStateOf(SeriesCardTechnicalMetadata()) }
    LaunchedEffect(series.id, metadataStore) {
        technicalMetadata = metadataStore.metadata(series.id)
    }

    val relatedFavoriteOverrides = remember(series.id) { mutableStateMapOf<String, Boolean>() }
    val relatedIsFavorite: (ContentItem) -> Boolean = { related ->
        relatedFavoriteOverrides["${related.type.name}:${related.id}"] ?: isRelatedFavorite(related)
    }
    val toggleRelatedFavorite: (ContentItem) -> Unit = { related ->
        val key = "${related.type.name}:${related.id}"
        relatedFavoriteOverrides[key] = !relatedIsFavorite(related)
        onToggleRelatedFavorite(related)
    }

    val orderedEpisodes = remember(episodes) {
        episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber))
    }
    val gridState = rememberLazyGridState()
    val seasons = remember(orderedEpisodes) { orderedEpisodes.map(Episode::season).filter { it > 0 }.distinct() }
    val historyByKey = remember(history) { history.associateBy(HistoryEntry::key) }
    val resumePair = remember(orderedEpisodes, history) {
        orderedEpisodes.mapNotNull { episode ->
            val entry = historyByKey["SERIES:${episode.id}"] ?: return@mapNotNull null
            val progress = entry.seriesWatchProgress() ?: return@mapNotNull null
            Triple(episode, entry, progress)
        }.maxByOrNull { it.second.updatedAtEpochMs }
    }
    val completedCount = remember(orderedEpisodes, history) {
        orderedEpisodes.count { episode -> historyByKey["SERIES:${episode.id}"]?.seriesCompleted() == true }
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
                ?: resumePair?.first?.season
                ?: seasons.firstOrNull()
                ?: 0,
        )
    }
    LaunchedEffect(targetEpisode?.id, targetSeason, seasons, resumePair?.first?.id) {
        selectedSeason = targetEpisode?.season
            ?: targetSeason?.takeIf { it in seasons }
            ?: resumePair?.first?.season
            ?: selectedSeason
    }
    val visibleEpisodes = remember(orderedEpisodes, selectedSeason) {
        if (selectedSeason == 0) orderedEpisodes else orderedEpisodes.filter { it.season == selectedSeason }
    }
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    val primaryRequester = remember(series.id) { FocusRequester() }
    val favoriteRequester = remember(series.id) { FocusRequester() }
    val notificationRequester = remember(series.id) { FocusRequester() }
    val previousRequester = remember(series.id) { FocusRequester() }
    val nextRequester = remember(series.id) { FocusRequester() }
    val targetEpisodeRequester = remember(series.id, targetEpisodeId, targetEpisodeNumber) { FocusRequester() }
    var initialFocusRequested by remember(series.id) { mutableStateOf(false) }
    val heroEpisode = targetEpisode ?: resumePair?.first ?: orderedEpisodes.firstOrNull()
    val heroEpisodeIndex = orderedEpisodes.indexOfFirst { it.id == heroEpisode?.id }
    val previousEpisode = if (heroEpisodeIndex >= 0) orderedEpisodes.getOrNull(heroEpisodeIndex - 1) else null
    val nextEpisode = if (heroEpisodeIndex >= 0) orderedEpisodes.getOrNull(heroEpisodeIndex + 1) else null
    LaunchedEffect(isTv, heroEpisode?.id, series.id, targetEpisode?.id) {
        if (isTv && targetEpisode == null && heroEpisode != null && !initialFocusRequested) {
            delay(180L)
            if (runCatching { primaryRequester.requestFocus() }.isSuccess) {
                initialFocusRequested = true
            }
        }
    }
    LaunchedEffect(isTv, targetEpisode?.id, selectedSeason, visibleEpisodes) {
        val targetIndex = visibleEpisodes.indexOfFirst { it.id == targetEpisode?.id }
        if (targetIndex >= 0) {
            delay(180L)
            runCatching { gridState.scrollToItem(targetIndex + 2) }
            if (isTv) {
                delay(80L)
                runCatching { targetEpisodeRequester.requestFocus() }
            }
        }
    }

    val columns = if (isTv) GridCells.Fixed(3) else GridCells.Adaptive(155.dp)
    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalGrid(
            columns = columns,
            state = gridState,
            contentPadding = if (isTv) PaddingValues(0.dp) else PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 9.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesHeroV2(
                    series = series,
                    details = details,
                    technicalMetadata = technicalMetadata,
                    firstEpisode = orderedEpisodes.firstOrNull(),
                    targetEpisode = targetEpisode,
                    previousEpisode = previousEpisode,
                    nextEpisode = nextEpisode,
                    resumeEpisode = resumePair?.first,
                    resumeEntry = resumePair?.second,
                    totalEpisodes = orderedEpisodes.size,
                    completedEpisodes = completedCount,
                    isTv = isTv,
                    isFavorite = isFavorite,
                    notificationsEnabled = notificationsEnabled,
                    notificationToggleAvailable = notificationToggleAvailable,
                    primaryRequester = primaryRequester,
                    previousRequester = previousRequester,
                    nextRequester = nextRequester,
                    favoriteRequester = favoriteRequester,
                    notificationRequester = notificationRequester,
                    onBack = onBack,
                    onPlay = onPlay,
                    onToggleFavorite = onToggleFavorite,
                    onToggleNotifications = onToggleNotifications,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesEpisodesHeaderV2(
                    selectedSeason = selectedSeason,
                    seasons = seasons,
                    totalEpisodes = orderedEpisodes.size,
                    completedEpisodes = completedCount,
                    resumeEpisode = resumePair?.first,
                    resumeEntry = resumePair?.second,
                    isTv = isTv,
                    errorMessage = errorMessage,
                    onSelectSeason = { selectedSeason = it },
                )
            }

            items(
                items = visibleEpisodes,
                key = Episode::id,
                contentType = { "series_episode_v2" },
            ) { episode ->
                val download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                EpisodeCardV2(
                    episode = episode,
                    highlighted = targetEpisode?.id == episode.id,
                    targetFocusRequester = targetEpisodeRequester.takeIf { targetEpisode?.id == episode.id },
                    fallbackBackdrop = backdrop,
                    download = download,
                    historyEntry = historyByKey["SERIES:${episode.id}"],
                    onClick = { onPlay(episode) },
                    onDownload = { onDownload(episode) },
                    onCancelDownload = { onCancelDownload(episode) },
                    modifier = Modifier.padding(
                        start = if (isTv) 14.dp else 5.dp,
                        end = if (isTv) 14.dp else 5.dp,
                        bottom = if (isTv) 8.dp else 5.dp,
                    ),
                )
            }

            if (relatedItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RelatedSeriesRowV2(
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
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .56f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRing(label = "جاري تجهيز تفاصيل المسلسل…")
            }
        }
    }
}

@Composable
private fun SeriesHeroV2(
    series: ContentItem,
    details: ContentDetails?,
    technicalMetadata: SeriesCardTechnicalMetadata,
    firstEpisode: Episode?,
    targetEpisode: Episode?,
    previousEpisode: Episode?,
    nextEpisode: Episode?,
    resumeEpisode: Episode?,
    resumeEntry: HistoryEntry?,
    totalEpisodes: Int,
    completedEpisodes: Int,
    isTv: Boolean,
    isFavorite: Boolean,
    notificationsEnabled: Boolean,
    notificationToggleAvailable: Boolean,
    primaryRequester: FocusRequester,
    previousRequester: FocusRequester,
    nextRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    notificationRequester: FocusRequester,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleNotifications: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    val heroEpisode = targetEpisode ?: resumeEpisode ?: firstEpisode
    val progress = resumeEntry?.seriesWatchProgress()
    val seasonCount = technicalMetadata.seasonCount
        ?: listOfNotNull(firstEpisode?.season, resumeEpisode?.season).maxOrNull()

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (isTv) 466.dp else 514.dp)
            .background(Color(0xFF090A08)),
    ) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = series.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(190.dp).graphicsLayer { alpha = .24f })
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .18f),
                    .42f to Color.Black.copy(alpha = .14f),
                    .73f to Color.Black.copy(alpha = .62f),
                    1f to colors.background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = .28f),
                        colors.background.copy(alpha = .94f),
                    ),
                ),
            ),
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                .padding(horizontal = if (isTv) 26.dp else 15.dp, vertical = if (isTv) 18.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(Modifier.size(if (isTv) 56.dp else 48.dp))
            Spacer(Modifier.weight(1f))
            FocusButton("رجوع", onBack, primary = false, compact = true)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = if (isTv) 30.dp else 17.dp,
                    end = if (isTv) 30.dp else 17.dp,
                    bottom = if (isTv) 24.dp else 18.dp,
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 26.dp else 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("مسلسل", color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = series.name,
                    color = Color.White,
                    fontSize = if (isTv) 38.sp else 25.sp,
                    lineHeight = if (isTv) 44.sp else 30.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    technicalMetadata.quality?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                    seasonCount?.takeIf { it > 0 }?.let { count -> InfoPill(seriesSeasonText(count)) }
                    if (totalEpisodes > 0) InfoPill("${latinSeriesInt(totalEpisodes)} حلقة")
                    compactSeriesRatingV2(series.rating)?.let { InfoPill("★ $it") }
                    if (completedEpisodes > 0) InfoPill("✓ ${latinSeriesInt(completedEpisodes)} مكتملة")
                }
                (details?.genre ?: series.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        genre,
                        color = colors.goldBright.copy(alpha = .9f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val plot = details?.plot ?: series.plot
                if (!plot.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = plot,
                        color = Color(0xFFE1DDD3),
                        fontSize = if (isTv) 13.sp else 11.sp,
                        lineHeight = if (isTv) 19.sp else 17.sp,
                        maxLines = if (isTv) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null && resumeEntry != null && resumeEpisode != null) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth(if (isTv) .68f else .92f)) {
                        Text(
                            "استكمال الموسم ${latinSeriesInt(resumeEpisode.season)} · الحلقة ${latinSeriesInt(resumeEpisode.episodeNumber)} · ${seriesFormatTimeV2(resumeEntry.positionMs)}",
                            color = colors.goldBright,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = .18f)),
                        ) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    FocusButton(
                        text = if (targetEpisode != null) {
                            "▶ مشاهدة الحلقة الجديدة"
                        } else if (resumeEpisode != null && resumeEntry != null) {
                            "▶ استكمال المشاهدة"
                        } else {
                            "▶ ابدأ المشاهدة"
                        },
                        onClick = { heroEpisode?.let(onPlay) },
                        enabled = heroEpisode != null,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .weight(1f)
                            .height(seriesDetailsActionHeightDp().dp)
                            .focusRequester(primaryRequester)
                            .focusProperties {
                                down = if (isTv) {
                                    favoriteRequester
                                } else if (previousEpisode != null) {
                                    previousRequester
                                } else if (nextEpisode != null) {
                                    nextRequester
                                } else {
                                    favoriteRequester
                                }
                                left = if (isTv && previousEpisode != null) {
                                    previousRequester
                                } else if (isTv && nextEpisode != null) {
                                    nextRequester
                                } else {
                                    FocusRequester.Cancel
                                }
                                right = FocusRequester.Cancel
                            },
                    )
                    if (isTv) {
                        SeriesAdjacentEpisodeButtonV2(
                            label = "السابق",
                            episode = previousEpisode,
                            requester = previousRequester,
                            upRequester = null,
                            downRequester = notificationRequester,
                            rightRequester = primaryRequester,
                            leftRequester = nextRequester.takeIf { nextEpisode != null },
                            onPlay = onPlay,
                            modifier = Modifier.weight(1f),
                        )
                        SeriesAdjacentEpisodeButtonV2(
                            label = "التالي",
                            episode = nextEpisode,
                            requester = nextRequester,
                            upRequester = null,
                            downRequester = notificationRequester,
                            rightRequester = previousRequester.takeIf { previousEpisode != null }
                                ?: primaryRequester,
                            leftRequester = null,
                            onPlay = onPlay,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!isTv) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SeriesAdjacentEpisodeButtonV2(
                            label = "السابق",
                            episode = previousEpisode,
                            requester = previousRequester,
                            upRequester = primaryRequester,
                            downRequester = favoriteRequester,
                            rightRequester = null,
                            leftRequester = nextRequester.takeIf { nextEpisode != null },
                            onPlay = onPlay,
                            modifier = Modifier.weight(1f),
                        )
                        SeriesAdjacentEpisodeButtonV2(
                            label = "التالي",
                            episode = nextEpisode,
                            requester = nextRequester,
                            upRequester = primaryRequester,
                            downRequester = notificationRequester,
                            rightRequester = previousRequester.takeIf { previousEpisode != null },
                            leftRequester = null,
                            onPlay = onPlay,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
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
                            .height(seriesDetailsActionHeightDp().dp)
                            .focusRequester(favoriteRequester)
                            .focusProperties {
                                up = primaryRequester
                                right = FocusRequester.Cancel
                                left = notificationRequester
                            },
                    )
                    FocusButton(
                        text = seriesNotificationButtonLabel(notificationsEnabled, isTv),
                        onClick = onToggleNotifications,
                        enabled = notificationsEnabled || notificationToggleAvailable,
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        accent = notificationsEnabled,
                        leadingIcon = Icons.Rounded.Notifications,
                        textMaxLines = 2,
                        textSizeSp = seriesNotificationButtonTextSizeSp(isTv),
                        modifier = Modifier
                            .weight(1f)
                            .height(seriesDetailsActionHeightDp().dp)
                            .focusRequester(notificationRequester)
                            .focusProperties {
                                up = if (isTv) {
                                    previousRequester
                                } else if (nextEpisode != null) {
                                    nextRequester
                                } else {
                                    previousRequester
                                }
                                right = favoriteRequester
                                left = FocusRequester.Cancel
                            },
                    )
                    if (isTv) Spacer(Modifier.weight(1f))
                }
            }

            SeriesHeroPosterV2(
                posterUrl = series.posterUrl,
                title = series.name,
                modifier = Modifier.width(if (isTv) 158.dp else 108.dp),
            )
        }
    }
}

@Composable
private fun SeriesAdjacentEpisodeButtonV2(
    label: String,
    episode: Episode?,
    requester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester,
    rightRequester: FocusRequester?,
    leftRequester: FocusRequester?,
    onPlay: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusButton(
        text = episode?.let { "$label · S${it.season} E${it.episodeNumber}" } ?: label,
        onClick = { episode?.let(onPlay) },
        enabled = episode != null,
        primary = false,
        outlined = true,
        compact = true,
        scaleOnFocus = false,
        modifier = modifier
            .height(seriesDetailsActionHeightDp().dp)
            .focusRequester(requester)
            .focusProperties {
                if (upRequester != null) up = upRequester
                down = downRequester
                right = rightRequester ?: FocusRequester.Cancel
                left = leftRequester ?: FocusRequester.Cancel
            },
    )
}

@Composable
private fun SeriesHeroPosterV2(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(2.dp, colors.gold.copy(alpha = .46f), shape),
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
private fun SeriesEpisodesHeaderV2(
    selectedSeason: Int,
    seasons: List<Int>,
    totalEpisodes: Int,
    completedEpisodes: Int,
    resumeEpisode: Episode?,
    resumeEntry: HistoryEntry?,
    isTv: Boolean,
    errorMessage: String?,
    onSelectSeason: (Int) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (isTv) 28.dp else 10.dp,
                end = if (isTv) 28.dp else 10.dp,
                top = if (isTv) 12.dp else 8.dp,
                bottom = 5.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("الحلقات", color = colors.text, fontSize = if (isTv) 24.sp else 19.sp, fontWeight = FontWeight.Black)
                Text(
                    if (completedEpisodes > 0) {
                        "${latinSeriesInt(completedEpisodes)} مكتملة من ${latinSeriesInt(totalEpisodes)}"
                    } else {
                        "${latinSeriesInt(totalEpisodes)} حلقة"
                    },
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (resumeEpisode != null && resumeEntry != null) {
                Text(
                    "الحالية: الموسم ${latinSeriesInt(resumeEpisode.season)} · الحلقة ${latinSeriesInt(resumeEpisode.episodeNumber)} · ${seriesFormatTimeV2(resumeEntry.positionMs)}",
                    color = colors.goldBright,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        if (seasons.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(seasons, key = { it }) { season ->
                    FocusButton(
                        "الموسم ${latinSeriesInt(season)}",
                        { onSelectSeason(season) },
                        primary = selectedSeason == season,
                        compact = true,
                    )
                }
            }
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            ErrorNotice(errorMessage)
        }
    }
}

@Composable
private fun EpisodeCardV2(
    episode: Episode,
    highlighted: Boolean,
    targetFocusRequester: FocusRequester?,
    fallbackBackdrop: String?,
    download: OfflineDownload?,
    historyEntry: HistoryEntry?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember(episode.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "episodeV2Scale")
    val shape = RoundedCornerShape(14.dp)
    val watchProgress = historyEntry?.seriesWatchProgress()
    val completed = historyEntry?.seriesCompleted() == true
    val artwork = episode.posterUrl?.takeIf(String::isNotBlank) ?: fallbackBackdrop
    val duration = compactEpisodeDuration(episode.duration)
    val activeDownload = download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED

    Column(modifier.fillMaxWidth().focusGroup()) {
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
                .background(Color(0xFF13140F))
                .border(
                    if (focused) 3.dp else if (highlighted) 2.dp else 1.dp,
                    if (focused || highlighted) colors.goldBright else colors.line.copy(alpha = .40f),
                    shape,
                )
                .onFocusChanged { focused = it.isFocused }
                .then(
                    targetFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                )
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = "الحلقة ${episode.episodeNumber}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(Modifier.align(Alignment.Center).size(62.dp).graphicsLayer { alpha = .28f })
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .10f),
                        .46f to Color.Transparent,
                        .72f to Color.Black.copy(alpha = .58f),
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
            )

            Text(
                text = "S${latinSeriesInt(episode.season)} · E${latinSeriesInt(episode.episodeNumber)}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = .76f))
                    .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )

            duration?.let { value ->
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = .76f))
                        .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }

            when {
                completed -> EpisodeStateBadgeV2("✓ تمت المشاهدة", Modifier.align(Alignment.CenterEnd))
                watchProgress != null && historyEntry != null -> EpisodeStateBadgeV2(
                    "استكمال ${seriesFormatTimeV2(historyEntry.positionMs)}",
                    Modifier.align(Alignment.CenterEnd),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Text(
                    "الحلقة ${latinSeriesInt(episode.episodeNumber)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                usefulEpisodeTitleV2(episode)?.let { title ->
                    Text(
                        title,
                        color = Color(0xFFD8D4CB),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (download != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = episodeDownloadStateV2(download),
                        color = if (download.status == OfflineStatus.COMPLETED) colors.goldBright else Color.White.copy(alpha = .78f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            if (watchProgress != null || completed) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.White.copy(alpha = .18f)),
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

        Spacer(Modifier.height(6.dp))
        if (activeDownload) {
            EpisodeDownloadProgressV2(checkNotNull(download))
            Spacer(Modifier.height(5.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FocusButton(
                text = episodeDownloadPrimaryLabelV2(download),
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
                    modifier = Modifier.weight(.62f),
                    primary = false,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun EpisodeStateBadgeV2(text: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Text(
        text = text,
        color = colors.goldBright,
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = .78f))
            .border(1.dp, colors.goldBright.copy(alpha = .45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun EpisodeDownloadProgressV2(download: OfflineDownload) {
    val colors = LocalHulkColors.current
    val percent = (download.progress * 100).toInt().coerceIn(0, 100)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = .05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(episodeDownloadStateV2(download), color = colors.textMuted, fontSize = 8.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("$percent%", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .13f))) {
            Box(Modifier.fillMaxWidth(download.progress).fillMaxHeight().background(colors.goldBright))
        }
    }
}

@Composable
private fun RelatedSeriesRowV2(
    items: List<ContentItem>,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (isTv) 16.dp else 12.dp, bottom = if (isTv) 14.dp else 10.dp),
    ) {
        Text(
            "مسلسلات مشابهة",
            color = colors.text,
            fontSize = if (isTv) 23.sp else 19.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = if (isTv) 28.dp else 15.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            contentPadding = PaddingValues(horizontal = if (isTv) 28.dp else 15.dp, vertical = 8.dp),
        ) {
            items(items, key = { "${it.type}:${it.id}" }) { item ->
                SeriesPosterCard(
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

private fun HistoryEntry.seriesWatchProgress(): Float? {
    if (positionMs < 30_000L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

private fun HistoryEntry.seriesCompleted(): Boolean =
    durationMs > 0L && positionMs.toDouble() / durationMs.toDouble() >= .95

private fun compactSeriesRatingV2(raw: String?): String? {
    val value = raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
    return String.format(Locale.US, "%.1f", value)
}

private fun seriesSeasonText(count: Int): String =
    if (count == 1) "${latinSeriesInt(count)} موسم" else "${latinSeriesInt(count)} مواسم"

private fun latinSeriesInt(value: Int): String = String.format(Locale.US, "%d", value)

private fun usefulEpisodeTitleV2(episode: Episode): String? {
    val title = episode.title.trim().takeIf(String::isNotBlank) ?: return null
    val normalized = title.lowercase(Locale.ROOT)
    val generic = listOf(
        "الحلقة ${episode.episodeNumber}",
        "episode ${episode.episodeNumber}",
        "ep ${episode.episodeNumber}",
    ).any { normalized == it.lowercase(Locale.ROOT) }
    return title.takeUnless { generic }
}

private fun compactEpisodeDuration(raw: String?): String? {
    val clean = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val parts = clean.split(':').map(String::trim)
    val seconds = when (parts.size) {
        3 -> {
            val h = parts[0].toLongOrNull() ?: return clean
            val m = parts[1].toLongOrNull() ?: return clean
            val s = parts[2].substringBefore('.').toLongOrNull() ?: 0L
            h * 3600L + m * 60L + s
        }
        2 -> {
            val m = parts[0].toLongOrNull() ?: return clean
            val s = parts[1].substringBefore('.').toLongOrNull() ?: 0L
            m * 60L + s
        }
        else -> return clean
    }
    val totalMinutes = ((seconds + 30L) / 60L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        else -> String.format(Locale.US, "%dm", minutes)
    }
}

private fun episodeDownloadPrimaryLabelV2(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ايقاف ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف"
    OfflineStatus.FAILED -> "↻ اعادة التحميل"
    null -> "↓ تحميل"
}

private fun episodeDownloadStateV2(download: OfflineDownload): String = when (download.status) {
    OfflineStatus.COMPLETED -> "✓ محملة وجاهزة"
    OfflineStatus.QUEUED -> "في قائمة الانتظار"
    OfflineStatus.CHECKING -> "جاري فحص الحجم"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا"
    OfflineStatus.WAITING_SCHEDULE -> "مجدول للتحميل"
    OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار مساحة"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun seriesFormatTimeV2(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

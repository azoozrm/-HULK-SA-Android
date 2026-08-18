package sa.hulksa.player.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sa.hulksa.player.data.SeriesCardMetadataStore
import sa.hulksa.player.data.SeriesCardTechnicalMetadata
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.BrandBadge
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.CompactPosterCard
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.SeriesPosterCard
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

private const val DETAILS_PRO_MOVIE_METADATA_PREFS = "movie_card_verified_metadata"
private const val DETAILS_PRO_FOCUS_DELAY_MS = 90L
private const val DETAILS_PRO_GRID_FOCUS_DELAY_MS = 28L

private data class DetailsProMovieTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

private data class DetailsProAction(
    val text: String,
    val onClick: () -> Unit,
    val requester: FocusRequester,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val mobileWeight: Float = 1f,
)

private data class EpisodeFocusTargets(
    val card: FocusRequester = FocusRequester(),
    val primaryAction: FocusRequester = FocusRequester(),
    val secondaryAction: FocusRequester = FocusRequester(),
)

@Composable
fun MovieDetailsProScreen(
    item: ContentItem,
    details: ContentDetails?,
    isLoading: Boolean,
    errorMessage: String?,
    isTv: Boolean,
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
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metrics = detailsProMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, isTv)
    val technical = remember(item.id) { context.detailsProMovieTechnicalMetadata(item.id) }
    val progress = historyEntry?.detailsProWatchProgress()
    val resumePosition = historyEntry?.positionMs?.takeIf { progress != null }
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl

    val backRequester = remember(item.id) { FocusRequester() }
    val playRequester = remember(item.id) { FocusRequester() }
    val favoriteRequester = remember(item.id) { FocusRequester() }
    val downloadRequester = remember(item.id) { FocusRequester() }
    val cancelDownloadRequester = remember(item.id) { FocusRequester() }
    val relatedKeys = relatedItems.map { "${it.type}:${it.id}" }
    val relatedRequesters = remember(relatedKeys) { List(relatedItems.size) { FocusRequester() } }
    var heroReturnRequester by remember(item.id) { mutableStateOf(playRequester) }

    LaunchedEffect(item.id, isTv) {
        if (isTv) {
            delay(DETAILS_PRO_FOCUS_DELAY_MS)
            runCatching { playRequester.requestFocus() }
        }
    }

    val actions = buildList {
        add(
            DetailsProAction(
                text = if (resumePosition != null) {
                    "▶ متابعة المشاهدة"
                } else {
                    "▶ ابدا المشاهدة"
                },
                onClick = onPlay,
                requester = playRequester,
                primary = true,
                mobileWeight = 1.5f,
            ),
        )
        add(
            DetailsProAction(
                text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                onClick = onToggleFavorite,
                requester = favoriteRequester,
                mobileWeight = 1f,
            ),
        )
        add(
            DetailsProAction(
                text = detailsProMovieDownloadLabel(download),
                onClick = onDownload,
                requester = downloadRequester,
                enabled = download?.status != OfflineStatus.COMPLETED,
            ),
        )
        if (download != null) {
            add(
                DetailsProAction(
                    text = if (download.status == OfflineStatus.COMPLETED) "حذف التحميل" else "الغاء التحميل",
                    onClick = onCancelDownload,
                    requester = cancelDownloadRequester,
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = metrics.verticalPaddingDp.dp),
    ) {
        item(key = "movie_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.heroHeightDp.dp)
                    .background(colors.background),
            ) {
                if (!backdrop.isNullOrBlank()) {
                    AsyncImage(
                        model = backdrop,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    BrandLogo(
                        Modifier
                            .align(Alignment.Center)
                            .size((metrics.heroPosterWidthDp * 1.18f).dp)
                            .graphicsLayer { alpha = .16f },
                    )
                }

                DetailsProHeroScrim()

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = metrics.horizontalPaddingDp.dp,
                            vertical = metrics.verticalPaddingDp.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandBadge(Modifier.size(if (isTv) 52.dp else 44.dp))
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        text = "رجوع",
                        onClick = onBack,
                        primary = false,
                        compact = true,
                        outlined = true,
                        modifier = Modifier.detailsProTvTarget(
                            isTv = isTv,
                            requester = backRequester,
                            downTarget = playRequester,
                        ),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = metrics.horizontalPaddingDp.dp,
                            end = metrics.horizontalPaddingDp.dp,
                            bottom = metrics.verticalPaddingDp.dp,
                        ),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(
                        if (metrics.wideLayout) 22.dp else 12.dp,
                    ),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "فيلم",
                            color = colors.goldBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = metrics.titleSizeSp.sp,
                            lineHeight = (metrics.titleSizeSp + 5).sp,
                            fontWeight = FontWeight.Black,
                            maxLines = if (metrics.compactHeight) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        DetailsProMoviePills(
                            item = item,
                            details = details,
                            technical = technical,
                            compact = !metrics.wideLayout,
                        )
                        (details?.genre ?: item.genre)
                            ?.takeIf(String::isNotBlank)
                            ?.let { genre ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = genre,
                                    color = colors.goldBright.copy(alpha = .92f),
                                    fontSize = if (isTv) 11.sp else 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        val plot = details?.plot ?: item.plot
                        if (!plot.isNullOrBlank()) {
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = plot,
                                color = Color(0xFFE3DFD5),
                                fontSize = metrics.plotSizeSp.sp,
                                lineHeight = (metrics.plotSizeSp + 6).sp,
                                maxLines = when {
                                    metrics.compactHeight -> 1
                                    isTv -> 3
                                    else -> 2
                                },
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (progress != null && historyEntry != null) {
                            Spacer(Modifier.height(9.dp))
                            DetailsProProgress(
                                progress = progress,
                                label = "متابعة من ${detailsProFormatTime(historyEntry.positionMs)}",
                                modifier = Modifier.fillMaxWidth(if (metrics.wideLayout) .72f else 1f),
                            )
                        }
                        Spacer(Modifier.height(if (metrics.compactHeight) 9.dp else 12.dp))
                        DetailsProActions(
                            actions = actions,
                            isTv = isTv,
                            upTarget = backRequester,
                            downTarget = relatedRequesters.firstOrNull(),
                            onFocused = { heroReturnRequester = it },
                            wide = metrics.wideLayout,
                        )
                    }

                    DetailsProPoster(
                        posterUrl = item.posterUrl,
                        title = item.name,
                        modifier = Modifier.width(metrics.heroPosterWidthDp.dp),
                    )
                }

                if (isLoading) {
                    LoadingRing(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = (metrics.horizontalPaddingDp + 64).dp,
                                top = metrics.verticalPaddingDp.dp,
                            ),
                    )
                }
            }
        }

        if (errorMessage != null) {
            item(key = "movie_error") {
                ErrorNotice(
                    errorMessage,
                    Modifier.padding(
                        horizontal = metrics.horizontalPaddingDp.dp,
                        vertical = 8.dp,
                    ),
                )
            }
        }

        if (detailsProHasInformation(details)) {
            item(key = "movie_info") {
                DetailsProInformationPanel(
                    title = "معلومات الفيلم",
                    details = details,
                    isTv = isTv,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item(key = "movie_related") {
                DetailsProRelatedRow(
                    title = "اعمال مشابهة",
                    items = relatedItems,
                    isTv = isTv,
                    cardWidthDp = metrics.relatedCardWidthDp,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    requesters = relatedRequesters,
                    upRequester = heroReturnRequester,
                    isFavorite = isRelatedFavorite,
                    onToggleFavorite = onToggleRelatedFavorite,
                    onOpen = onOpenRelated,
                )
            }
        }

        if (download != null && download.status != OfflineStatus.COMPLETED) {
            item(key = "movie_download_progress") {
                DetailsProDownloadProgress(
                    download = download,
                    modifier = Modifier.padding(
                        horizontal = metrics.horizontalPaddingDp.dp,
                        vertical = 8.dp,
                    ),
                )
            }
        }
    }
}

@Composable
fun SeriesDetailsProScreen(
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
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metrics = detailsProMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, isTv)
    val seriesHorizontalPaddingDp = if (isTv) metrics.horizontalPaddingDp else 4
    val gridState = rememberLazyGridState()
    val navigationScope = rememberCoroutineScope()
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    var technicalMetadata by remember(series.id) { mutableStateOf(SeriesCardTechnicalMetadata()) }

    LaunchedEffect(series.id, metadataStore) {
        technicalMetadata = metadataStore.metadata(series.id)
    }

    val orderedEpisodes = remember(episodes) {
        episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber, Episode::id))
    }
    val seasons = remember(orderedEpisodes) {
        orderedEpisodes.map(Episode::season).filter { it > 0 }.distinct()
    }
    val historyByKey = remember(history) { history.associateBy(HistoryEntry::key) }
    val resumePair = remember(orderedEpisodes, historyByKey) {
        orderedEpisodes.mapNotNull { episode ->
            val entry = historyByKey["SERIES:${episode.id}"] ?: return@mapNotNull null
            val progress = entry.detailsProWatchProgress() ?: return@mapNotNull null
            Triple(episode, entry, progress)
        }.maxByOrNull { it.second.updatedAtEpochMs }
    }
    val mobileResumeHeroExtraDp = if (!isTv && resumePair != null) 30 else 0
    val completedCount = remember(orderedEpisodes, historyByKey) {
        orderedEpisodes.count { episode ->
            historyByKey["SERIES:${episode.id}"]?.detailsProCompleted() == true
        }
    }

    var selectedSeason by rememberSaveable(series.id, seasons) {
        mutableIntStateOf(resumePair?.first?.season ?: seasons.firstOrNull() ?: 0)
    }
    LaunchedEffect(resumePair?.first?.id) {
        resumePair?.first?.let { selectedSeason = it.season }
    }

    val visibleEpisodes = remember(orderedEpisodes, selectedSeason) {
        if (selectedSeason == 0) orderedEpisodes else orderedEpisodes.filter { it.season == selectedSeason }
    }
    val heroEpisode = resumePair?.first ?: orderedEpisodes.firstOrNull()
    val previousEpisode = detailsProAdjacentEpisode(orderedEpisodes, heroEpisode?.id, -1)
    val nextEpisode = detailsProAdjacentEpisode(orderedEpisodes, heroEpisode?.id, 1)
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl

    val backRequester = remember(series.id) { FocusRequester() }
    val playRequester = remember(series.id) { FocusRequester() }
    val favoriteRequester = remember(series.id) { FocusRequester() }
    val previousRequester = remember(series.id) { FocusRequester() }
    val nextRequester = remember(series.id) { FocusRequester() }
    val seasonKeys = seasons.toList()
    val seasonRequesters = remember(seasonKeys) {
        seasons.associateWith { FocusRequester() }
    }
    val episodeKeys = visibleEpisodes.map(Episode::id)
    val episodeTargets = remember(episodeKeys) {
        visibleEpisodes.associate { it.id to EpisodeFocusTargets() }
    }
    val relatedKeys = relatedItems.map { "${it.type}:${it.id}" }
    val relatedRequesters = remember(relatedKeys) { List(relatedItems.size) { FocusRequester() } }
    var heroReturnRequester by remember(series.id) { mutableStateOf(playRequester) }
    val selectedSeasonRequester = seasonRequesters[selectedSeason]
        ?: seasons.firstOrNull()?.let(seasonRequesters::get)
    val firstEpisodeRequester = visibleEpisodes.firstOrNull()?.let { episodeTargets[it.id]?.card }
    val heroDownTarget = selectedSeasonRequester ?: firstEpisodeRequester
    val hasMobileSeriesInfo = !isTv && detailsProHasInformation(details)
    val episodeStartGridIndex = if (hasMobileSeriesInfo) 3 else 2
    val relatedGridIndex = episodeStartGridIndex + visibleEpisodes.size

    fun requestEpisodeAt(index: Int): Boolean {
        val episode = visibleEpisodes.getOrNull(index) ?: return false
        val requester = episodeTargets[episode.id]?.card ?: return false
        val visible = gridState.layoutInfo.visibleItemsInfo.any { info ->
            info.index == episodeStartGridIndex + index
        }
        if (visible && runCatching { requester.requestFocus() }.getOrDefault(false)) return true
        navigationScope.launch {
            runCatching { gridState.scrollToItem(episodeStartGridIndex + index) }
            delay(DETAILS_PRO_GRID_FOCUS_DELAY_MS)
            runCatching { requester.requestFocus() }
        }
        return true
    }

    fun requestRelatedFirst(): Boolean {
        val requester = relatedRequesters.firstOrNull() ?: return false
        val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == relatedGridIndex }
        if (visible && runCatching { requester.requestFocus() }.getOrDefault(false)) return true
        navigationScope.launch {
            runCatching { gridState.scrollToItem(relatedGridIndex) }
            delay(DETAILS_PRO_GRID_FOCUS_DELAY_MS)
            runCatching { requester.requestFocus() }
        }
        return true
    }

    LaunchedEffect(series.id, isTv, heroEpisode?.id) {
        if (isTv && heroEpisode != null) {
            delay(DETAILS_PRO_FOCUS_DELAY_MS)
            runCatching { playRequester.requestFocus() }
        }
    }

    val heroActions = buildList {
        add(
            DetailsProAction(
                text = if (resumePair != null) "▶ متابعة المشاهدة" else "▶ ابدا المشاهدة",
                onClick = { heroEpisode?.let(onPlay) },
                requester = playRequester,
                primary = true,
                enabled = heroEpisode != null,
                mobileWeight = 1.5f,
            ),
        )
        previousEpisode?.let { episode ->
            add(
                DetailsProAction(
                    text = if (isTv || episode.season != heroEpisode?.season) {
                        "السابق S${episode.season} E${episode.episodeNumber}"
                    } else {
                        "السابق · E${episode.episodeNumber}"
                    },
                    onClick = { onPlay(episode) },
                    requester = previousRequester,
                ),
            )
        }
        nextEpisode?.let { episode ->
            add(
                DetailsProAction(
                    text = if (isTv || episode.season != heroEpisode?.season) {
                        "التالي S${episode.season} E${episode.episodeNumber}"
                    } else {
                        "التالي · E${episode.episodeNumber}"
                    },
                    onClick = { onPlay(episode) },
                    requester = nextRequester,
                ),
            )
        }
        add(
            DetailsProAction(
                text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                onClick = onToggleFavorite,
                requester = favoriteRequester,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(metrics.episodeColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.verticalPaddingDp.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 8.dp),
        ) {
            item(
                key = "series_hero",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((metrics.heroHeightDp + mobileResumeHeroExtraDp).dp)
                        .background(colors.background),
                ) {
                    if (!backdrop.isNullOrBlank()) {
                        AsyncImage(
                            model = backdrop,
                            contentDescription = series.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        BrandLogo(
                            Modifier
                                .align(Alignment.Center)
                                .size((metrics.heroPosterWidthDp * 1.15f).dp)
                                .graphicsLayer { alpha = .18f },
                        )
                    }
                    DetailsProHeroScrim()

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(
                                horizontal = metrics.horizontalPaddingDp.dp,
                                vertical = metrics.verticalPaddingDp.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandBadge(Modifier.size(if (isTv) 52.dp else 44.dp))
                        Spacer(Modifier.weight(1f))
                        FocusButton(
                            text = "رجوع",
                            onClick = onBack,
                            primary = false,
                            compact = true,
                            outlined = true,
                            modifier = Modifier.detailsProTvTarget(
                                isTv = isTv,
                                requester = backRequester,
                                downTarget = playRequester,
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = seriesHorizontalPaddingDp.dp,
                                end = seriesHorizontalPaddingDp.dp,
                                bottom = metrics.verticalPaddingDp.dp,
                            ),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(
                            if (metrics.wideLayout) 22.dp else 12.dp,
                        ),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "مسلسل",
                                color = colors.goldBright,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = series.name,
                                color = Color.White,
                                fontSize = metrics.titleSizeSp.sp,
                                lineHeight = (metrics.titleSizeSp + 5).sp,
                                fontWeight = FontWeight.Black,
                                maxLines = if (metrics.compactHeight) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(7.dp))
                            DetailsProSeriesPills(
                                series = series,
                                technicalMetadata = technicalMetadata,
                                seasonCount = seasons.size,
                                episodeCount = orderedEpisodes.size,
                                completedCount = completedCount,
                                compact = !metrics.wideLayout,
                            )
                            (details?.genre ?: series.genre)
                                ?.takeIf(String::isNotBlank)
                                ?.let { genre ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = genre,
                                        color = colors.goldBright.copy(alpha = .92f),
                                        fontSize = if (isTv) 11.sp else 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            val plot = details?.plot ?: series.plot
                            if (!plot.isNullOrBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    text = plot,
                                    color = Color(0xFFE3DFD5),
                                    fontSize = metrics.plotSizeSp.sp,
                                    lineHeight = (metrics.plotSizeSp + 6).sp,
                                    maxLines = when {
                                        metrics.compactHeight -> 1
                                        isTv -> 3
                                        else -> 2
                                    },
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (resumePair != null) {
                                Spacer(Modifier.height(9.dp))
                                val entry = resumePair.second
                                DetailsProProgress(
                                    progress = resumePair.third,
                                    label = "متابعة من ${detailsProFormatTime(entry.positionMs)}",
                                    modifier = Modifier.fillMaxWidth(if (metrics.wideLayout) .72f else 1f),
                                )
                            }
                            Spacer(Modifier.height(if (metrics.compactHeight) 9.dp else 12.dp))
                            DetailsProActions(
                                actions = heroActions,
                                isTv = isTv,
                                upTarget = backRequester,
                                downTarget = heroDownTarget,
                                onFocused = { heroReturnRequester = it },
                                wide = metrics.wideLayout,
                            )
                        }

                        DetailsProPoster(
                            posterUrl = series.posterUrl,
                            title = series.name,
                            modifier = Modifier.width(metrics.heroPosterWidthDp.dp),
                        )
                    }
                }
            }

            if (hasMobileSeriesInfo) {
                item(
                    key = "series_info_mobile",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DetailsProInformationPanel(
                        title = "معلومات المسلسل",
                        details = details,
                        isTv = false,
                        horizontalPaddingDp = seriesHorizontalPaddingDp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(
                key = "series_episode_header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                SeriesDetailsProHeader(
                    selectedSeason = selectedSeason,
                    seasons = seasons,
                    totalEpisodes = orderedEpisodes.size,
                    completedEpisodes = completedCount,
                    resumeEpisode = resumePair?.first,
                    resumeEntry = resumePair?.second,
                    isTv = isTv,
                    horizontalPaddingDp = seriesHorizontalPaddingDp,
                    seasonRequesters = seasonRequesters,
                    upRequester = heroReturnRequester,
                    downRequester = firstEpisodeRequester,
                    onSelectSeason = { selectedSeason = it },
                    errorMessage = errorMessage,
                )
            }

            gridItemsIndexed(
                items = visibleEpisodes,
                key = { _, episode -> "episode:${episode.id}" },
                contentType = { _, _ -> "details_pro_episode" },
            ) { index, episode ->
                val download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                val row = index / metrics.episodeColumns
                val rowStart = row * metrics.episodeColumns
                val rowEnd = minOf(rowStart + metrics.episodeColumns - 1, visibleEpisodes.lastIndex)
                val leftTarget = if (index < rowEnd) {
                    episodeTargets[visibleEpisodes[index + 1].id]?.card
                } else {
                    null
                }
                val rightTarget = if (index > rowStart) {
                    episodeTargets[visibleEpisodes[index - 1].id]?.card
                } else {
                    null
                }
                val upTarget = if (index - metrics.episodeColumns >= 0) {
                    episodeTargets[visibleEpisodes[index - metrics.episodeColumns].id]?.card
                } else {
                    selectedSeasonRequester ?: playRequester
                }
                val nextRowIndex = index + metrics.episodeColumns
                val targets = checkNotNull(episodeTargets[episode.id])

                EpisodeDetailsProCard(
                    episode = episode,
                    fallbackBackdrop = backdrop,
                    download = download,
                    historyEntry = historyByKey["SERIES:${episode.id}"],
                    isTv = isTv,
                    targets = targets,
                    upTarget = upTarget,
                    leftTarget = leftTarget,
                    rightTarget = rightTarget,
                    onDownFromActions = {
                        if (nextRowIndex < visibleEpisodes.size) {
                            requestEpisodeAt(nextRowIndex)
                        } else {
                            requestRelatedFirst()
                        }
                    },
                    onPlay = { onPlay(episode) },
                    onDownload = { onDownload(episode) },
                    onCancelDownload = { onCancelDownload(episode) },
                    modifier = Modifier.padding(
                        start = if (isTv) 10.dp else 0.dp,
                        end = if (isTv) 10.dp else 0.dp,
                        bottom = if (isTv) 8.dp else 5.dp,
                    ),
                )
            }

            if (relatedItems.isNotEmpty()) {
                item(
                    key = "series_related",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DetailsProRelatedRow(
                        title = "مسلسلات مشابهة",
                        items = relatedItems,
                        isTv = isTv,
                        cardWidthDp = metrics.relatedCardWidthDp,
                        horizontalPaddingDp = metrics.horizontalPaddingDp,
                        requesters = relatedRequesters,
                        upRequester = null,
                        onUpOverride = {
                            if (visibleEpisodes.isNotEmpty()) {
                                requestEpisodeAt(visibleEpisodes.lastIndex)
                            } else {
                                heroReturnRequester.requestFocus()
                            }
                        },
                        isFavorite = isRelatedFavorite,
                        onToggleFavorite = onToggleRelatedFavorite,
                        onOpen = onOpenRelated,
                    )
                }
            }

            if (isTv && detailsProHasInformation(details)) {
                item(
                    key = "series_info",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DetailsProInformationPanel(
                        title = "معلومات المسلسل",
                        details = details,
                        isTv = true,
                        horizontalPaddingDp = metrics.horizontalPaddingDp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .52f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingRing(label = "جاري تجهيز التفاصيل…")
            }
        }
    }
}

@Composable
private fun DetailsProHeroScrim() {
    val colors = LocalHulkColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .20f),
                    .38f to Color.Black.copy(alpha = .18f),
                    .70f to colors.background.copy(alpha = .62f),
                    1f to colors.background,
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        colors.background.copy(alpha = .92f),
                        Color.Black.copy(alpha = .56f),
                        Color.Black.copy(alpha = .10f),
                    ),
                ),
            ),
    )
}

@Composable
private fun DetailsProPoster(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(1.dp, colors.gold.copy(alpha = .50f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BrandLogo(
                Modifier
                    .fillMaxSize()
                    .padding(22.dp)
                    .graphicsLayer { alpha = .50f },
            )
        }
    }
}

@Composable
private fun DetailsProMoviePills(
    item: ContentItem,
    details: ContentDetails?,
    technical: DetailsProMovieTechnicalMetadata,
    compact: Boolean,
) {
    val values = buildList {
        technical.quality?.takeIf(String::isNotBlank)?.let(::add)
        detailsProMovieDuration(technical.durationMs ?: detailsProParseDuration(details?.duration))?.let(::add)
        detailsProRating(item.rating)?.let { add("★ $it") }
        item.year?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }.let { if (compact) it.take(3) else it.take(4) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { DetailsProPill(it) }
    }
}

@Composable
private fun DetailsProSeriesPills(
    series: ContentItem,
    technicalMetadata: SeriesCardTechnicalMetadata,
    seasonCount: Int,
    episodeCount: Int,
    completedCount: Int,
    compact: Boolean,
) {
    val verifiedSeasonCount = technicalMetadata.seasonCount?.takeIf { it > 0 } ?: seasonCount.takeIf { it > 0 }
    val seasonLabel = verifiedSeasonCount?.let { if (it == 1) "1 موسم" else "$it مواسم" }
    val episodeLabel = episodeCount.takeIf { it > 0 }?.let { "$it حلقة" }
    val values = if (compact) {
        buildList {
            technicalMetadata.quality?.takeIf(String::isNotBlank)?.let(::add)
            detailsProRating(series.rating)?.let { add("★ $it") }
            seasonLabel?.let(::add)
            episodeLabel?.let(::add)
        }.take(4)
    } else {
        buildList {
            technicalMetadata.quality?.takeIf(String::isNotBlank)?.let(::add)
            detailsProRating(series.rating)?.let { add("★ $it") }
            series.year?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            seasonLabel?.let(::add)
            episodeLabel?.let(::add)
            if (completedCount > 0) add("✓ $completedCount مكتملة")
        }.take(5)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        values.forEach { DetailsProPill(it, compact = compact) }
    }
}

@Composable
private fun DetailsProPill(
    text: String,
    compact: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (compact) 6.dp else 7.dp)
    Text(
        text = text,
        color = Color.White,
        fontSize = if (compact) 8.sp else 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = .70f))
            .border(1.dp, colors.gold.copy(alpha = .36f), shape)
            .padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 3.dp else 4.dp,
            ),
    )
}

@Composable
private fun DetailsProProgress(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier) {
        Text(
            text = label,
            color = colors.goldBright,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = .17f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.goldBright),
            )
        }
    }
}

@Composable
private fun DetailsProActions(
    actions: List<DetailsProAction>,
    isTv: Boolean,
    upTarget: FocusRequester?,
    downTarget: FocusRequester?,
    onFocused: (FocusRequester) -> Unit,
    wide: Boolean,
) {
    if (isTv || wide) {
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                val leftTarget = actions.getOrNull(index + 1)?.requester
                val rightTarget = actions.getOrNull(index - 1)?.requester
                FocusButton(
                    text = action.text,
                    onClick = action.onClick,
                    primary = action.primary,
                    enabled = action.enabled,
                    compact = true,
                    outlined = !action.primary,
                    modifier = Modifier
                        .weight(if (action.primary) 1.18f else 1f)
                        .detailsProTvTarget(
                            isTv = isTv,
                            requester = action.requester,
                            upTarget = upTarget,
                            downTarget = downTarget,
                            leftTarget = leftTarget,
                            rightTarget = rightTarget,
                            onFocused = { onFocused(action.requester) },
                        ),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            actions.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { action ->
                        FocusButton(
                            text = action.text,
                            onClick = action.onClick,
                            primary = action.primary,
                            enabled = action.enabled,
                            compact = true,
                            outlined = !action.primary,
                            modifier = Modifier.weight(action.mobileWeight),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailsProHeader(
    selectedSeason: Int,
    seasons: List<Int>,
    totalEpisodes: Int,
    completedEpisodes: Int,
    resumeEpisode: Episode?,
    resumeEntry: HistoryEntry?,
    isTv: Boolean,
    horizontalPaddingDp: Int,
    seasonRequesters: Map<Int, FocusRequester>,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    onSelectSeason: (Int) -> Unit,
    errorMessage: String?,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPaddingDp.dp,
                end = horizontalPaddingDp.dp,
                top = if (isTv) 12.dp else 9.dp,
                bottom = 7.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = "الحلقات",
                    color = colors.text,
                    fontSize = if (isTv) 23.sp else 19.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (completedEpisodes > 0) {
                        "$completedEpisodes مكتملة من $totalEpisodes"
                    } else {
                        "$totalEpisodes حلقة"
                    },
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (isTv && resumeEpisode != null && resumeEntry != null) {
                Text(
                    text = "الحلقة الحالية : S${resumeEpisode.season} E${resumeEpisode.episodeNumber} · ${detailsProFormatTime(resumeEntry.positionMs)}",
                    color = colors.goldBright,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        if (!isTv && resumeEpisode != null && resumeEntry != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "الحلقة الحالية : S${resumeEpisode.season} E${resumeEpisode.episodeNumber}  •  ${detailsProFormatTime(resumeEntry.positionMs)}",
                color = colors.goldBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface.copy(alpha = .82f))
                    .border(1.dp, colors.gold.copy(alpha = .45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        if (seasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 3.dp),
            ) {
                itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                    val requester = checkNotNull(seasonRequesters[season])
                    val leftTarget = seasons.getOrNull(index + 1)?.let(seasonRequesters::get)
                    val rightTarget = seasons.getOrNull(index - 1)?.let(seasonRequesters::get)
                    FocusButton(
                        text = "الموسم $season",
                        onClick = { onSelectSeason(season) },
                        primary = selectedSeason == season,
                        compact = true,
                        outlined = selectedSeason != season,
                        modifier = Modifier.detailsProTvTarget(
                            isTv = isTv,
                            requester = requester,
                            upTarget = upRequester,
                            downTarget = downRequester,
                            leftTarget = leftTarget,
                            rightTarget = rightTarget,
                        ),
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(7.dp))
            ErrorNotice(errorMessage)
        }
    }
}

@Composable
private fun EpisodeDetailsProCard(
    episode: Episode,
    fallbackBackdrop: String?,
    download: OfflineDownload?,
    historyEntry: HistoryEntry?,
    isTv: Boolean,
    targets: EpisodeFocusTargets,
    upTarget: FocusRequester?,
    leftTarget: FocusRequester?,
    rightTarget: FocusRequester?,
    onDownFromActions: () -> Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember(episode.id) { mutableStateOf(false) }
    val showFocused = focused && LocalAdaptiveUi.current.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.028f else 1f, label = "detailsProEpisodeScale")
    val progress = historyEntry?.detailsProWatchProgress()
    val completed = historyEntry?.detailsProCompleted() == true
    val artwork = episode.posterUrl?.takeIf(String::isNotBlank) ?: fallbackBackdrop
    val duration = detailsProEpisodeDuration(episode.duration)
    val hasSecondaryAction = download != null &&
        download.status != OfflineStatus.COMPLETED &&
        download.status != OfflineStatus.FAILED

    Column(modifier = modifier.fillMaxWidth().focusGroup()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (showFocused) 12.dp.toPx() else 0f
                }
                .aspectRatio(if (isTv) 16f / 9f else 1.45f)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF13140F))
                .border(
                    if (showFocused) 3.dp else 1.dp,
                    if (showFocused) colors.goldBright else colors.line.copy(alpha = .38f),
                    RoundedCornerShape(13.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .detailsProTvTarget(
                    isTv = isTv,
                    requester = targets.card,
                    upTarget = upTarget,
                    downTarget = targets.primaryAction,
                    leftTarget = leftTarget,
                    rightTarget = rightTarget,
                )
                .clickable(role = Role.Button, onClick = onPlay),
        ) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(
                    Modifier
                        .align(Alignment.Center)
                        .size(58.dp)
                        .graphicsLayer { alpha = .28f },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            .52f to Color.Transparent,
                            .78f to Color.Black.copy(alpha = .68f),
                            1f to Color.Black.copy(alpha = .96f),
                        ),
                    ),
            )
            Text(
                text = "S${episode.season} · E${episode.episodeNumber}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = .76f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
            duration?.let { value ->
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = .76f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "الحلقة ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = if (isTv) 14.sp else 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                detailsProUsefulEpisodeTitle(episode)?.let { title ->
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = .80f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    completed -> Text(
                        text = "✓ تمت المشاهدة",
                        color = colors.goldBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    progress != null && historyEntry != null -> Text(
                        text = "استكمال ${detailsProFormatTime(historyEntry.positionMs)}",
                        color = colors.goldBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    else -> Unit
                }
            }

            if (progress != null || completed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = .16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (completed) 1f else progress ?: 0f)
                            .fillMaxHeight()
                            .background(colors.goldBright),
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
            DetailsProDownloadProgress(download, Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FocusButton(
                text = detailsProEpisodeDownloadLabel(download),
                onClick = if (download?.status == OfflineStatus.COMPLETED) onCancelDownload else onDownload,
                primary = false,
                compact = true,
                outlined = true,
                modifier = Modifier
                    .weight(1f)
                    .detailsProTvActionExit(
                        isTv = isTv,
                        requester = targets.primaryAction,
                        upTarget = targets.card,
                        leftTarget = if (hasSecondaryAction) targets.secondaryAction else null,
                        rightTarget = null,
                        onDown = onDownFromActions,
                    ),
            )
            if (hasSecondaryAction) {
                FocusButton(
                    text = "الغاء",
                    onClick = onCancelDownload,
                    primary = false,
                    compact = true,
                    outlined = true,
                    modifier = Modifier
                        .weight(.58f)
                        .detailsProTvActionExit(
                            isTv = isTv,
                            requester = targets.secondaryAction,
                            upTarget = targets.card,
                            leftTarget = null,
                            rightTarget = targets.primaryAction,
                            onDown = onDownFromActions,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DetailsProRelatedRow(
    title: String,
    items: List<ContentItem>,
    isTv: Boolean,
    cardWidthDp: Int,
    horizontalPaddingDp: Int,
    requesters: List<FocusRequester>,
    upRequester: FocusRequester?,
    onUpOverride: (() -> Boolean)? = null,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isTv) 14.dp else 10.dp, bottom = if (isTv) 14.dp else 10.dp),
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = if (isTv) 22.sp else 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = horizontalPaddingDp.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 9.dp),
            contentPadding = PaddingValues(horizontal = horizontalPaddingDp.dp, vertical = 7.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "${item.type}:${item.id}" },
            ) { index, item ->
                val requester = requesters[index]
                val leftTarget = requesters.getOrNull(index + 1)
                val rightTarget = requesters.getOrNull(index - 1)
                val navigationModifier = Modifier
                    .width(cardWidthDp.dp)
                    .focusRequester(requester)
                    .focusProperties {
                        up = upRequester ?: FocusRequester.Cancel
                        left = leftTarget ?: FocusRequester.Cancel
                        right = rightTarget ?: FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        if (!isTv || event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    if (onUpOverride != null) {
                                        onUpOverride()
                                    } else {
                                        upRequester?.let { runCatching { it.requestFocus() } }
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    leftTarget?.let { runCatching { it.requestFocus() } }
                                    true
                                }
                                Key.DirectionRight -> {
                                    rightTarget?.let { runCatching { it.requestFocus() } }
                                    true
                                }
                                Key.DirectionDown -> true
                                else -> false
                            }
                        }
                    }

                if (item.type == ContentType.SERIES) {
                    SeriesPosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = navigationModifier,
                        onLongClick = { onToggleFavorite(item) },
                    )
                } else {
                    CompactPosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = navigationModifier,
                        onLongClick = { onToggleFavorite(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsProInformationPanel(
    title: String,
    details: ContentDetails?,
    isTv: Boolean,
    horizontalPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier.padding(
            start = horizontalPaddingDp.dp,
            end = horizontalPaddingDp.dp,
            top = 8.dp,
            bottom = if (isTv) 20.dp else 16.dp,
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isTv) .78f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = .82f))
                .border(1.dp, colors.line.copy(alpha = .72f), RoundedCornerShape(14.dp))
                .padding(horizontal = if (isTv) 18.dp else 15.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                color = colors.text,
                fontSize = if (isTv) 18.sp else 17.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            details?.releaseDate?.takeIf(String::isNotBlank)?.let {
                DetailsProInformationLine("تاريخ العرض", it, isTv)
            }
            details?.director?.takeIf(String::isNotBlank)?.let {
                DetailsProInformationLine("الاخراج", it, isTv)
            }
            details?.cast?.takeIf(String::isNotBlank)?.let {
                DetailsProInformationLine("البطولة", it, isTv)
            }
        }
    }
}

@Composable
private fun DetailsProInformationLine(
    label: String,
    value: String,
    isTv: Boolean,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.goldBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(if (isTv) 88.dp else 84.dp),
        )
        Text(
            text = value,
            color = colors.textMuted,
            fontSize = if (isTv) 11.sp else 10.sp,
            lineHeight = if (isTv) 17.sp else 16.sp,
            maxLines = if (isTv) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsProDownloadProgress(
    download: OfflineDownload,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val percent = (download.progress * 100).toInt().coerceIn(0, 100)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = .05f))
            .border(1.dp, colors.line.copy(alpha = .35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = detailsProDownloadState(download),
                color = colors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$percent%",
                color = colors.goldBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = .13f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(download.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.goldBright),
            )
        }
    }
}

private fun Modifier.detailsProTvTarget(
    isTv: Boolean,
    requester: FocusRequester,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onBlurred: (() -> Unit)? = null,
): Modifier {
    if (!isTv) return this
    return this
        .focusRequester(requester)
        .onFocusChanged { state ->
            if (state.isFocused) onFocused?.invoke() else onBlurred?.invoke()
        }
        .focusProperties {
            up = upTarget ?: FocusRequester.Cancel
            down = downTarget ?: FocusRequester.Cancel
            left = leftTarget ?: FocusRequester.Cancel
            right = rightTarget ?: FocusRequester.Cancel
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.DirectionUp -> {
                        upTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    Key.DirectionDown -> {
                        downTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    Key.DirectionLeft -> {
                        leftTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    Key.DirectionRight -> {
                        rightTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    else -> false
                }
            }
        }
}

private fun Modifier.detailsProTvActionExit(
    isTv: Boolean,
    requester: FocusRequester,
    upTarget: FocusRequester,
    leftTarget: FocusRequester?,
    rightTarget: FocusRequester?,
    onDown: () -> Boolean,
): Modifier {
    if (!isTv) return this
    return this
        .focusRequester(requester)
        .focusProperties {
            up = upTarget
            down = FocusRequester.Cancel
            left = leftTarget ?: FocusRequester.Cancel
            right = rightTarget ?: FocusRequester.Cancel
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.DirectionUp -> {
                        runCatching { upTarget.requestFocus() }
                        true
                    }
                    Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    Key.DirectionLeft -> {
                        leftTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    Key.DirectionRight -> {
                        rightTarget?.let { runCatching { it.requestFocus() } }
                        true
                    }
                    else -> false
                }
            }
        }
}

private fun Context.detailsProMovieTechnicalMetadata(movieId: Int): DetailsProMovieTechnicalMetadata {
    val prefs = applicationContext.getSharedPreferences(
        DETAILS_PRO_MOVIE_METADATA_PREFS,
        Context.MODE_PRIVATE,
    )
    return DetailsProMovieTechnicalMetadata(
        quality = prefs.getString("movie:$movieId:quality", null)
            ?.trim()
            ?.takeIf(String::isNotBlank),
        durationMs = prefs.getLong("movie:$movieId:duration_ms", 0L).takeIf { it > 0L },
    )
}

private fun HistoryEntry.detailsProWatchProgress(): Float? {
    if (positionMs <= 0L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat())
        .coerceIn(0f, 1f)
        .takeIf { it < .95f }
}

private fun HistoryEntry.detailsProCompleted(): Boolean =
    durationMs > 0L && positionMs.toDouble() / durationMs.toDouble() >= .95

private fun detailsProHasInformation(details: ContentDetails?): Boolean =
    !details?.releaseDate.isNullOrBlank() ||
        !details?.director.isNullOrBlank() ||
        !details?.cast.isNullOrBlank()

private fun detailsProRating(raw: String?): String? {
    val value = raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
    return String.format(Locale.US, "%.1f", value)
}

private fun detailsProMovieDuration(durationMs: Long?): String? {
    val minutes = durationMs?.takeIf { it > 0L }?.div(60_000L) ?: return null
    if (minutes <= 0L) return null
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours > 0L && remainder > 0L -> String.format(Locale.US, "%dh %02dm", hours, remainder)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        else -> String.format(Locale.US, "%dm", minutes)
    }
}

private fun detailsProParseDuration(raw: String?): Long? {
    val clean = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val parts = clean.split(':').map(String::trim)
    val seconds = when (parts.size) {
        3 -> {
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
            hours * 3_600L + minutes * 60L + seconds
        }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].substringBefore('.').toLongOrNull() ?: return null
            minutes * 60L + seconds
        }
        else -> return null
    }
    return seconds.takeIf { it > 0L }?.times(1_000L)
}

private fun detailsProEpisodeDuration(raw: String?): String? {
    val durationMs = detailsProParseDuration(raw) ?: return raw?.trim()?.takeIf(String::isNotBlank)
    return detailsProMovieDuration(durationMs)
}

private fun detailsProUsefulEpisodeTitle(episode: Episode): String? {
    val title = episode.title.trim().takeIf(String::isNotBlank) ?: return null
    val normalized = title.lowercase(Locale.ROOT)
    val generic = listOf(
        "الحلقة ${episode.episodeNumber}",
        "episode ${episode.episodeNumber}",
        "ep ${episode.episodeNumber}",
    ).any { normalized == it.lowercase(Locale.ROOT) }
    return title.takeUnless { generic }
}

private fun detailsProMovieDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ايقاف ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف التحميل"
    OfflineStatus.FAILED -> "↻ اعادة التحميل"
    null -> "↓ تحميل الفيلم"
}

private fun detailsProEpisodeDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "حذف التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ايقاف ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف"
    OfflineStatus.FAILED -> "↻ اعادة"
    null -> "↓ تحميل"
}

private fun detailsProDownloadState(download: OfflineDownload): String = when (download.status) {
    OfflineStatus.COMPLETED -> "تم التحميل"
    OfflineStatus.QUEUED -> "في قائمة الانتظار"
    OfflineStatus.CHECKING -> "جاري فحص الحجم"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا"
    OfflineStatus.WAITING_SCHEDULE -> "مجدول للتحميل"
    OfflineStatus.WAITING_NETWORK -> "بانتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار مساحة"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun detailsProFormatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

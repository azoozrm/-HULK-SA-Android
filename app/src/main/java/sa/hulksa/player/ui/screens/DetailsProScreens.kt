package sa.hulksa.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.data.HomeHeroMetadataStore
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
import sa.hulksa.player.ui.components.movieRecentElapsedText
import sa.hulksa.player.ui.components.movieRecentTotalText
import sa.hulksa.player.ui.components.OrderedNumberWordInline
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.MoviesCatalogBoxedCard
import sa.hulksa.player.ui.components.SeriesCatalogBoxedCard
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

private const val DETAILS_PRO_FOCUS_DELAY_MS = 90L
private const val DETAILS_PRO_GRID_FOCUS_DELAY_MS = 28L

private data class DetailsProMovieTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

internal fun seriesDetailsActionHeightDp(): Int = 50

internal fun seriesNotificationButtonLabel(enabled: Boolean, isTv: Boolean): String = when {
    enabled -> "التنبيهات مفعلة"
    isTv -> "نبهني عند نزول حلقة جديدة"
    else -> "تنبيهات الحلقات"
}

internal fun seriesNotificationButtonTextSizeSp(isTv: Boolean): Int? = if (isTv) null else 12

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
    onRetryDetails: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metadataStore = remember(context) { HomeHeroMetadataStore.get(context) }
    val metadataOwner = metadataStore.currentOwner()
    val metrics = detailsProMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, isTv)
    val technical = remember(item.id, metadataOwner) {
        val cached = metadataStore.cached(metadataOwner, ContentType.MOVIE, item.id)
        DetailsProMovieTechnicalMetadata(cached.quality, cached.durationMs)
    }
    val progress = movieResumeProgress(
        positionMs = historyEntry?.positionMs ?: 0L,
        durationMs = historyEntry?.durationMs ?: 0L,
    )
    val resumePosition = historyEntry?.positionMs?.takeIf { progress != null }
    val movieHeroHeightDp = movieCompactHeroHeightDp(adaptiveUi.screenHeightDp)
    val detailsErrorRetryRequester = remember(item.id) { FocusRequester() }
    var detailsErrorRetryFocused by remember(item.id) { mutableStateOf(false) }
    val detailsNetworkUsable by rememberUsableNetworkState()
    val detailsOffline = errorMessage != null && !detailsNetworkUsable
    val detailsErrorCopy = moviesDetailsErrorCopy(
        offline = detailsOffline,
        serverMessage = errorMessage,
    )
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl

    val backRequester = remember(item.id) { FocusRequester() }
    val playRequester = remember(item.id) { FocusRequester() }
    val favoriteRequester = remember(item.id) { FocusRequester() }
    val downloadRequester = remember(item.id) { FocusRequester() }
    val tabRequesters = remember(item.id) { MovieDetailsTab.entries.associateWith { FocusRequester() } }
    var selectedTab by rememberSaveable(item.id) { mutableStateOf(MovieDetailsTab.STORY) }
    val relatedKeys = relatedItems.map { "${it.type}:${it.id}" }
    val relatedRequesters = remember(relatedKeys) { List(relatedItems.size) { FocusRequester() } }
    var heroReturnRequester by remember(item.id) { mutableStateOf(playRequester) }
    val pageListState = rememberLazyListState()
    val pageScrollScope = rememberCoroutineScope()
    var pageScrollJob by remember(item.id) { mutableStateOf<Job?>(null) }
    var tabRevealTarget by remember(item.id) { mutableStateOf<MovieDetailsTab?>(null) }
    var tabRevealRequestId by remember(item.id) { mutableIntStateOf(0) }
    val selectTab: (MovieDetailsTab) -> Unit = { tab ->
        selectedTab = tab
        if (tab == MovieDetailsTab.INFORMATION || tab == MovieDetailsTab.RELATED) {
            tabRevealTarget = tab
            tabRevealRequestId += 1
        }
    }
    // One parent reveal owner: after an explicit selection has been laid out, scroll the parent
    // page so the tab row and the complete selected section are visible when they fit. Obsolete
    // rapid selections cancel the previous animation; metadata refresh never re-triggers it.
    LaunchedEffect(tabRevealRequestId) {
        val target = tabRevealTarget ?: return@LaunchedEffect
        if (target != selectedTab) return@LaunchedEffect
        withFrameNanos { }
        val layout = pageListState.layoutInfo
        val sectionKey = movieDetailsSectionItemKey(target, tvPolished = false)
        val section = layout.visibleItemsInfo.firstOrNull { it.key == sectionKey }
        if (
            movieDetailsSectionNeedsReveal(
                sectionPresent = section != null,
                sectionTop = section?.offset ?: 0,
                sectionBottom = section?.let { it.offset + it.size } ?: 0,
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
            )
        ) {
            // One cancellable scroll owner for both the selection reveal and the read scroll, so
            // a manual D-pad step always takes precedence over an in-flight reveal animation.
            pageScrollJob?.cancel()
            pageScrollJob = pageScrollScope.launch {
                pageListState.animateScrollToItem(
                    movieDetailsTabsItemIndex(hasError = errorMessage != null),
                )
            }
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage == null && detailsErrorRetryFocused) {
            // Notice removal while its Retry is focused restores the selected tab.
            withFrameNanos { }
            runCatching { tabRequesters.getValue(selectedTab).requestFocus() }
        }
    }
    // Usable D-pad reading path for overflowing Story/Information panels: the selected tab keeps
    // focus and steps the parent page, so long content stays reachable without field-by-field
    // focus stops or a second vertical scroller. Related keeps its existing card route.
    val handleSelectedTabScrollKey: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val layout = pageListState.layoutInfo
            val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
            val step = (viewportHeight * 3 / 4).coerceAtLeast(1)
            when (event.key) {
                Key.DirectionDown -> {
                    if (selectedTab == MovieDetailsTab.RELATED) {
                        false
                    } else {
                        val sectionKey = movieDetailsSectionItemKey(selectedTab, tvPolished = false)
                        val section = layout.visibleItemsInfo.firstOrNull { it.key == sectionKey }
                        if (
                            movieDetailsPanelNeedsMoreScroll(
                                sectionPresent = section != null,
                                sectionBottom = section?.let { it.offset + it.size } ?: 0,
                                viewportEnd = layout.viewportEndOffset,
                            )
                        ) {
                            pageScrollJob?.cancel()
                            pageScrollJob = pageScrollScope.launch {
                                pageListState.scrollBy(step.toFloat())
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                Key.DirectionUp -> {
                    val atPageTop = pageListState.firstVisibleItemIndex == 0 &&
                        pageListState.firstVisibleItemScrollOffset == 0
                    if (atPageTop) {
                        false
                    } else {
                        pageScrollJob?.cancel()
                        pageScrollJob = pageScrollScope.launch {
                            pageListState.scrollBy(-step.toFloat())
                        }
                        true
                    }
                }
                else -> false
            }
        }
    }

    var showDownloadPanel by remember(item.id) { mutableStateOf(false) }
    var restoreDownloadFocus by remember(item.id) { mutableStateOf(false) }
    val closeDownloadPanel: () -> Unit = {
        showDownloadPanel = false
        restoreDownloadFocus = true
    }
    // Stale panel content (job removed or completed) dismisses without leaving an orphan window.
    LaunchedEffect(download?.downloadId, download?.status, item.id) {
        if (showDownloadPanel && (download == null || download.status == OfflineStatus.COMPLETED)) {
            showDownloadPanel = false
            restoreDownloadFocus = true
        }
    }
    // Panel dismissal/actions return focus to the same permanent download control; if the control
    // became disabled (completed), fall back to the adjacent favorite action instead of losing it.
    LaunchedEffect(showDownloadPanel) {
        if (!showDownloadPanel && restoreDownloadFocus) {
            withFrameNanos { }
            val restored = runCatching { downloadRequester.requestFocus() }.getOrDefault(false)
            if (!restored) {
                runCatching { favoriteRequester.requestFocus() }
            }
            restoreDownloadFocus = false
        }
    }

    LaunchedEffect(item.id, isTv) {
        if (isTv) {
            delay(DETAILS_PRO_FOCUS_DELAY_MS)
            runCatching { playRequester.requestFocus() }
        }
    }

    LazyColumn(
        state = pageListState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = metrics.verticalPaddingDp.dp),
    ) {
        item(key = "movie_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(movieHeroHeightDp.dp)
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
                    Spacer(Modifier.weight(1f))
                    BrandBadge(Modifier.size(if (isTv) 52.dp else 44.dp))
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = metrics.horizontalPaddingDp.dp,
                            end = metrics.horizontalPaddingDp.dp,
                            bottom = metrics.verticalPaddingDp.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.name,
                        color = colors.text,
                        fontSize = metrics.titleSizeSp.sp,
                        lineHeight = (metrics.titleSizeSp + 5).sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(.94f),
                    )
                    Spacer(Modifier.height(8.dp))
                    MovieDetailsHeroMetadataRow(
                        item = item,
                        genre = details?.genre ?: item.genre,
                        durationLabel = detailsProDurationLabel(
                            technical.durationMs ?: detailsProParseDuration(details?.duration),
                        ),
                        qualityLabel = technical.quality,
                        ratingLabel = detailsProRatingLabel(item.rating),
                        isTv = isTv,
                        modifier = Modifier.fillMaxWidth(.96f),
                    )
                    if (progress != null && historyEntry != null) {
                        Spacer(Modifier.height(10.dp))
                        MovieInlineResumeStrip(
                            positionMs = historyEntry.positionMs,
                            durationMs = historyEntry.durationMs,
                            progress = progress,
                            isTv = false,
                            modifier = Modifier.fillMaxWidth(if (metrics.wideLayout) .78f else 1f),
                        )
                    }
                    Spacer(Modifier.height(if (metrics.compactHeight) 9.dp else 13.dp))
                    MovieDetailsActionsBar(
                        isTv = isTv,
                        rowFraction = if (metrics.wideLayout) .78f else 1f,
                        minimumActionHeightDp = movieActionHeightDp(
                            isTv = isTv,
                            compactHeight = metrics.compactHeight,
                        ),
                        resumePositionMs = resumePosition,
                        isFavorite = isFavorite,
                        download = download,
                        playRequester = playRequester,
                        favoriteRequester = favoriteRequester,
                        downloadRequester = downloadRequester,
                        upRequester = null,
                        tabsDownRequester = if (errorMessage != null) {
                            detailsErrorRetryRequester
                        } else {
                            tabRequesters.getValue(MovieDetailsTab.STORY)
                        },
                        onActionFocused = { heroReturnRequester = it },
                        onPlay = onPlay,
                        onToggleFavorite = onToggleFavorite,
                        onDownload = onDownload,
                        onOpenDownloadPanel = { showDownloadPanel = true },
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
                MoviesErrorNotice(
                    title = detailsErrorCopy.title,
                    body = detailsErrorCopy.body,
                    onRetry = onRetryDetails,
                    isTv = false,
                    networkFailure = detailsOffline,
                    retryRequester = detailsErrorRetryRequester,
                    onRetryFocusChanged = { detailsErrorRetryFocused = it },
                    onRetryUp = {
                        runCatching { heroReturnRequester.requestFocus() }.getOrDefault(false)
                    },
                    onRetryDown = {
                        runCatching { tabRequesters.getValue(selectedTab).requestFocus() }.getOrDefault(false)
                    },
                    modifier = Modifier.padding(
                        horizontal = metrics.horizontalPaddingDp.dp,
                        vertical = 8.dp,
                    ),
                )
            }
        }

        item(key = "movie_tabs") {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = .08f)),
                )
                MovieDetailsTabRow(
                    selected = selectedTab,
                    onSelect = selectTab,
                    requesters = tabRequesters,
                    upTarget = if (errorMessage != null) detailsErrorRetryRequester else heroReturnRequester,
                    downTargets = mapOf(MovieDetailsTab.RELATED to relatedRequesters.firstOrNull()),
                    isTv = isTv,
                    modifier = Modifier.padding(vertical = 4.dp),
                    onSelectedTabScrollKey = handleSelectedTabScrollKey,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = .08f)),
                )
            }
        }

        when (selectedTab) {
            MovieDetailsTab.STORY -> item(key = "movie_story") {
                MovieDetailsStoryContent(
                    plot = details?.plot ?: item.plot,
                    isTv = isTv,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                )
            }
            MovieDetailsTab.INFORMATION -> item(key = "movie_information") {
                MovieDetailsInfoGrid(
                    item = item,
                    details = details,
                    durationLabel = detailsProDurationLabel(
                        technical.durationMs ?: detailsProParseDuration(details?.duration),
                    ),
                    qualityLabel = technical.quality,
                    ratingLabel = detailsProRatingLabel(item.rating),
                    releaseYearLabel = details?.releaseDate,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    isTv = isTv,
                )
            }
            MovieDetailsTab.RELATED -> item(key = "movie_related") {
                if (relatedItems.isEmpty()) {
                    MovieDetailsEmptyTabMessage("لا توجد افلام مشابهة متاحة", isTv)
                } else {
                    DetailsProRelatedRow(
                        title = "",
                        showTitle = false,
                        items = relatedItems,
                        isTv = isTv,
                        cardWidthDp = metrics.relatedCardWidthDp,
                        horizontalPaddingDp = metrics.horizontalPaddingDp,
                        requesters = relatedRequesters,
                        upRequester = tabRequesters.getValue(MovieDetailsTab.RELATED),
                        isFavorite = isRelatedFavorite,
                        onToggleFavorite = onToggleRelatedFavorite,
                        onOpen = onOpenRelated,
                    )
                }
            }
        }
    }

    if (showDownloadPanel && download != null && download.status != OfflineStatus.COMPLETED) {
        MovieDownloadPanelDialog(
            download = download,
            isTv = isTv,
            onPauseResumeRetry = {
                onDownload()
                closeDownloadPanel()
            },
            onCancel = {
                onCancelDownload()
                closeDownloadPanel()
            },
            onDismiss = closeDownloadPanel,
        )
    }
}

internal fun detailsProDurationLabel(durationMs: Long?): String? {
    val totalMinutes = durationMs?.takeIf { it > 0L }?.div(60_000L) ?: return null
    if (totalMinutes <= 0L) return null
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        else -> String.format(Locale.US, "%dm", minutes)
    }
}

internal fun detailsProRatingLabel(raw: String?): String? {
    val value = raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
    return String.format(Locale.US, "%.1f", value)
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
    onRetryDetails: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metrics = detailsProMetrics(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, isTv)
    val seriesHorizontalPaddingDp = if (isTv) metrics.horizontalPaddingDp else 4
    val gridState = rememberLazyGridState()
    val navigationScope = rememberCoroutineScope()
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    val metadataOwner = metadataStore.currentOwner()
    var technicalMetadata by remember(series.id, metadataOwner) {
        mutableStateOf(metadataStore.cached(metadataOwner, series.id))
    }

    LaunchedEffect(series.id, metadataOwner, metadataStore) {
        val owner = metadataOwner ?: return@LaunchedEffect
        val loaded = metadataStore.metadata(owner, series.id)
        metadataStore.publishIfCurrent(owner) { technicalMetadata = loaded }
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
    val completedCount = remember(orderedEpisodes, historyByKey) {
        orderedEpisodes.count { episode ->
            historyByKey["SERIES:${episode.id}"]?.detailsProCompleted() == true
        }
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
    val heroEpisode = targetEpisode ?: resumePair?.first ?: orderedEpisodes.firstOrNull()
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl

    val backRequester = remember(series.id) { FocusRequester() }
    val playRequester = remember(series.id) { FocusRequester() }
    val favoriteRequester = remember(series.id) { FocusRequester() }
    val notificationRequester = remember(series.id) { FocusRequester() }
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
    val tabRequesters = remember(series.id) {
        SeriesDetailsTab.entries.associateWith { FocusRequester() }
    }
    val detailsErrorRetryRequester = remember(series.id) { FocusRequester() }
    var detailsErrorRetryFocused by remember(series.id) { mutableStateOf(false) }
    val focusRestoration = remember(series.id) { SeriesFocusRestorationOwner() }
    val detailsNetworkUsable by rememberUsableNetworkState()
    val detailsOffline = errorMessage != null && !detailsNetworkUsable
    val detailsErrorCopy = moviesDetailsErrorCopy(
        offline = detailsOffline,
        serverMessage = errorMessage,
        mediaLabel = "المسلسل",
    )
    var selectedTab by rememberSaveable(series.id) {
        mutableStateOf(
            if (targetEpisode != null) SeriesDetailsTab.EPISODES else SeriesDetailsTab.STORY,
        )
    }
    // Hero actions hand DOWN to the selected details tab; the Episodes tab itself hands DOWN to
    // its season selector/episode grid.
    val heroDownTarget = tabRequesters.getValue(selectedTab)
    val episodesDownTarget = selectedSeasonRequester ?: firstEpisodeRequester
    var pageScrollJob by remember(series.id) { mutableStateOf<Job?>(null) }
    var tabRevealTarget by remember(series.id) { mutableStateOf<SeriesDetailsTab?>(null) }
    var tabRevealRequestId by remember(series.id) { mutableIntStateOf(0) }
    val selectTab: (SeriesDetailsTab) -> Unit = { tab ->
        selectedTab = tab
        focusRestoration.invalidate()
        if (seriesDetailsTabSelectionNeedsReveal(tab)) {
            tabRevealTarget = tab
            tabRevealRequestId += 1
        }
    }
    val seriesTabsIndex = seriesDetailsTabsItemIndex(errorMessage != null)
    val episodeStartGridIndex = seriesTabsIndex + 2
    // One parent reveal owner: after an explicit tab selection has been laid out, scroll the page
    // so the selected section starts visible. Obsolete rapid selections cancel the previous
    // animation; metadata refresh never re-triggers it.
    LaunchedEffect(tabRevealRequestId) {
        val target = tabRevealTarget ?: return@LaunchedEffect
        if (target != selectedTab) return@LaunchedEffect
        withFrameNanos { }
        val layout = gridState.layoutInfo
        val sectionKey = seriesDetailsSectionItemKey(target, tvPolished = false)
        val section = layout.visibleItemsInfo.firstOrNull { it.key == sectionKey }
        val needsReveal = section == null ||
            section.offset.y < layout.viewportStartOffset ||
            section.offset.y + section.size.height > layout.viewportEndOffset
        if (needsReveal) {
            pageScrollJob?.cancel()
            pageScrollJob = navigationScope.launch {
                gridState.animateScrollToItem(seriesTabsIndex)
            }
        }
    }
    // A focused Retry whose notice disappears restores one attached surviving target through the
    // existing page owners: the selected tab when it is still attached, otherwise the primary
    // watch action. The request carries a generation token: a new error, a tab selection, a new
    // user key, Retry regaining focus or leaving the route invalidates it, so the restore job can
    // never focus a stale target after the error state or ownership changed.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            focusRestoration.invalidate()
            return@LaunchedEffect
        }
        if (!detailsErrorRetryFocused) return@LaunchedEffect
        detailsErrorRetryFocused = false
        val token = focusRestoration.begin()
        pageScrollJob?.cancel()
        pageScrollJob = navigationScope.launch {
            seriesDetailsRestoreFocus(
                isCurrent = { focusRestoration.isCurrent(token) },
                reveal = {
                    val tabsIndex = seriesTabsIndex
                    if (gridState.layoutInfo.visibleItemsInfo.none { it.index == tabsIndex }) {
                        gridState.scrollToItem(tabsIndex)
                        snapshotFlow {
                            gridState.layoutInfo.visibleItemsInfo.any { it.index == tabsIndex }
                        }.first { it }
                        withFrameNanos { }
                    }
                },
                requests = listOf(
                    { tabRequesters.getValue(selectedTab).requestFocus() },
                    { playRequester.requestFocus() },
                    { heroReturnRequester.requestFocus() },
                ),
            )
        }
    }
    // Usable D-pad reading path for the Story and Information panels: the selected tab keeps focus
    // and steps the parent page, so long content stays reachable. The first UP from the two
    // informational tabs hands off to the primary watch action in one reveal/focus transition.
    val handleSelectedTabScrollKey: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            // A newer user key invalidates any pending restore/handoff before it can focus.
            focusRestoration.invalidate()
            val layout = gridState.layoutInfo
            val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
            val step = (viewportHeight * 3 / 4).coerceAtLeast(1)
            when (event.key) {
                Key.DirectionDown -> when (selectedTab) {
                    SeriesDetailsTab.EPISODES, SeriesDetailsTab.RELATED -> false
                    else -> {
                        val sectionKey = seriesDetailsSectionItemKey(selectedTab, tvPolished = false)
                        val section = layout.visibleItemsInfo.firstOrNull { it.key == sectionKey }
                        val needsMoreScroll = section == null ||
                            section.offset.y + section.size.height > layout.viewportEndOffset
                        if (needsMoreScroll) {
                            pageScrollJob?.cancel()
                            pageScrollJob = navigationScope.launch {
                                gridState.scrollBy(step.toFloat())
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                Key.DirectionUp -> {
                    if (errorMessage == null && seriesDetailsTabUpHandsOffToWatchAction(selectedTab)) {
                        val token = focusRestoration.begin()
                        pageScrollJob?.cancel()
                        pageScrollJob = navigationScope.launch {
                            seriesDetailsRestoreFocus(
                                isCurrent = { focusRestoration.isCurrent(token) },
                                reveal = {
                                    if (gridState.layoutInfo.visibleItemsInfo.none { it.index == 0 }) {
                                        gridState.scrollToItem(0)
                                        snapshotFlow {
                                            gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 }
                                        }.first { it }
                                        withFrameNanos { }
                                    }
                                },
                                requests = listOf(
                                    { playRequester.requestFocus() },
                                    { backRequester.requestFocus() },
                                ),
                            )
                        }
                        true
                    } else {
                        val atPageTop = gridState.firstVisibleItemIndex == 0 &&
                            gridState.firstVisibleItemScrollOffset == 0
                        if (atPageTop) {
                            false
                        } else {
                            pageScrollJob?.cancel()
                            pageScrollJob = navigationScope.launch {
                                gridState.scrollBy(-step.toFloat())
                            }
                            true
                        }
                    }
                }
                else -> false
            }
        }
    }

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
    LaunchedEffect(targetEpisode?.id) {
        if (targetEpisode != null && selectedTab != SeriesDetailsTab.EPISODES) {
            selectedTab = SeriesDetailsTab.EPISODES
        }
    }

    LaunchedEffect(targetEpisode?.id, selectedSeason, visibleEpisodes, selectedTab) {
        if (selectedTab != SeriesDetailsTab.EPISODES) return@LaunchedEffect
        val index = visibleEpisodes.indexOfFirst { it.id == targetEpisode?.id }
        if (index >= 0) {
            delay(DETAILS_PRO_GRID_FOCUS_DELAY_MS)
            requestEpisodeAt(index)
        }
    }

    LaunchedEffect(series.id, isTv, heroEpisode?.id) {
        if (isTv && heroEpisode != null) {
            delay(DETAILS_PRO_FOCUS_DELAY_MS)
            runCatching { playRequester.requestFocus() }
        }
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
                        .height(movieCompactHeroHeightDp(adaptiveUi.screenHeightDp).dp)
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
                        Spacer(Modifier.weight(1f))
                        BrandBadge(Modifier.size(if (isTv) 52.dp else 44.dp))
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = metrics.horizontalPaddingDp.dp,
                                end = metrics.horizontalPaddingDp.dp,
                                bottom = metrics.verticalPaddingDp.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = series.name,
                            color = colors.text,
                            fontSize = metrics.titleSizeSp.sp,
                            lineHeight = (metrics.titleSizeSp + 5).sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(.94f),
                        )
                        Spacer(Modifier.height(8.dp))
                        SeriesDetailsHeroMetadataRow(
                            series = series,
                            genre = details?.genre ?: series.genre,
                            qualityLabel = technicalMetadata.quality,
                            ratingLabel = detailsProRatingLabel(series.rating),
                            episodeCount = orderedEpisodes.size,
                            seasonCount = technicalMetadata.seasonCount ?: seasons.size,
                            isTv = false,
                            modifier = Modifier.fillMaxWidth(.96f),
                        )
                        if (resumePair != null) {
                            Spacer(Modifier.height(10.dp))
                            MovieInlineResumeStrip(
                                positionMs = resumePair.second.positionMs,
                                durationMs = resumePair.second.durationMs,
                                progress = resumePair.third,
                                isTv = false,
                                modifier = Modifier.fillMaxWidth(if (metrics.wideLayout) .78f else 1f),
                            )
                        }
                        Spacer(Modifier.height(if (metrics.compactHeight) 9.dp else 13.dp))
                        SeriesDetailsActionsBar(
                            isTv = false,
                            rowFraction = if (metrics.wideLayout) .78f else 1f,
                            minimumActionHeightDp = movieActionHeightDp(
                                isTv = false,
                                compactHeight = metrics.compactHeight,
                            ),
                            resumePositionMs = resumePair?.second?.positionMs,
                            isFavorite = isFavorite,
                            notificationsEnabled = notificationsEnabled,
                            notificationToggleAvailable = notificationToggleAvailable,
                            playRequester = playRequester,
                            favoriteRequester = favoriteRequester,
                            notificationRequester = notificationRequester,
                            upRequester = null,
                            tabsDownRequester = heroDownTarget,
                            onActionFocused = {
                                heroReturnRequester = it
                                seriesDetailsNoteNewFocusOwner(focusRestoration)
                            },
                            onPlay = { heroEpisode?.let(onPlay) },
                            onToggleFavorite = onToggleFavorite,
                            onToggleNotifications = onToggleNotifications,
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
                item(
                    key = "series_error",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    MoviesErrorNotice(
                        title = detailsErrorCopy.title,
                        body = detailsErrorCopy.body,
                        onRetry = onRetryDetails,
                        isTv = false,
                        networkFailure = detailsOffline,
                        retryRequester = detailsErrorRetryRequester,
                        onRetryFocusChanged = { focused ->
                            detailsErrorRetryFocused = focused
                            if (focused) focusRestoration.invalidate()
                        },
                        onRetryUp = {
                            runCatching { heroReturnRequester.requestFocus() }.getOrDefault(false)
                        },
                        onRetryDown = {
                            runCatching { tabRequesters.getValue(selectedTab).requestFocus() }
                                .getOrDefault(false)
                        },
                        modifier = Modifier.padding(
                            horizontal = seriesHorizontalPaddingDp.dp,
                            vertical = 8.dp,
                        ),
                    )
                }
            }

            item(
                key = "series_tabs",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = .08f)),
                    )
                    SeriesDetailsTabRow(
                        selected = selectedTab,
                        onSelect = selectTab,
                        requesters = tabRequesters,
                        upTarget = heroReturnRequester,
                        downTargets = mapOf(
                            SeriesDetailsTab.EPISODES to episodesDownTarget,
                            SeriesDetailsTab.RELATED to relatedRequesters.firstOrNull(),
                        ),
                        isTv = false,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onSelectedTabScrollKey = handleSelectedTabScrollKey,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = .08f)),
                    )
                }
            }

            when (selectedTab) {
                SeriesDetailsTab.STORY -> item(
                    key = "series_story",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    MovieDetailsStoryContent(
                        plot = details?.plot ?: series.plot,
                        isTv = false,
                        horizontalPaddingDp = metrics.horizontalPaddingDp,
                        emptyMessage = "لا يوجد وصف متاح لهذا المسلسل",
                    )
                }

                SeriesDetailsTab.INFORMATION -> item(
                    key = "series_info",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SeriesDetailsInfoGrid(
                        series = series,
                        details = details,
                        seasonCount = technicalMetadata.seasonCount ?: seasons.size,
                        episodeCount = orderedEpisodes.size,
                        qualityLabel = technicalMetadata.quality,
                        ratingLabel = detailsProRatingLabel(series.rating),
                        horizontalPaddingDp = seriesHorizontalPaddingDp,
                        isTv = false,
                    )
                }

                SeriesDetailsTab.RELATED -> item(
                    key = "series_related",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    if (relatedItems.isEmpty()) {
                        MovieDetailsEmptyTabMessage("لا توجد مسلسلات مشابهة متاحة", isTv = false)
                    } else {
                        DetailsProRelatedRow(
                            title = "",
                            showTitle = false,
                            items = relatedItems,
                            isTv = false,
                            cardWidthDp = metrics.relatedCardWidthDp,
                            horizontalPaddingDp = metrics.horizontalPaddingDp,
                            requesters = relatedRequesters,
                            upRequester = tabRequesters.getValue(SeriesDetailsTab.RELATED),
                            isFavorite = isRelatedFavorite,
                            onToggleFavorite = onToggleRelatedFavorite,
                            onOpen = onOpenRelated,
                        )
                    }
                }

                SeriesDetailsTab.EPISODES -> {
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
                            errorMessage = null,
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
                            highlighted = targetEpisode?.id == episode.id,
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
                                    false
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsProMoviePills(
    item: ContentItem,
    details: ContentDetails?,
    technical: DetailsProMovieTechnicalMetadata,
    compact: Boolean,
) {
    val values = buildList {
        detailsProRating(item.rating)?.let { add("★ $it") }
        (details?.genre ?: item.genre)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { add(it.take(27)) }
        technical.quality?.takeIf(String::isNotBlank)?.let(::add)
        detailsProMovieDuration(technical.durationMs ?: detailsProParseDuration(details?.duration))?.let(::add)
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 4,
    ) {
        values.forEach { DetailsProPill(it, compact = compact) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (completedEpisodes > 0) {
                        OrderedNumberWordInline(
                            count = completedEpisodes,
                            word = "مكتملة",
                            fontSizeSp = 10,
                            lineHeightSp = 12,
                            textColor = colors.textMuted,
                        )
                        Text("من", color = colors.textMuted, fontSize = 10.sp)
                    }
                    OrderedNumberWordInline(
                        count = totalEpisodes,
                        word = "حلقة",
                        fontSizeSp = 10,
                        lineHeightSp = 12,
                        textColor = colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
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
                        // The accepted focus frame must cover the entire season button without any
                        // focus scale growing it past the LazyRow viewport and clipping its corners.
                        scaleOnFocus = false,
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
    highlighted: Boolean,
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
    val progress = historyEntry?.detailsProWatchProgress()
    val completed = historyEntry?.detailsProCompleted() == true
    val cancelCaption = download?.let {
        if (it.status == OfflineStatus.COMPLETED) "حذف" else "الغاء"
    }
    EpisodeBoxedCard(
        episodeId = episode.id,
        identityLabel = "S${episode.season} · E${episode.episodeNumber}",
        title = "الحلقة ${episode.episodeNumber}",
        subtitle = detailsProUsefulEpisodeTitle(episode),
        statusLabel = when {
            completed -> "تمت المشاهدة"
            progress != null && historyEntry != null -> "اكمل المشاهدة"
            else -> null
        },
        statusElapsedText = if (!completed && progress != null && historyEntry != null) {
            movieRecentElapsedText(historyEntry.positionMs)
        } else {
            null
        },
        statusTotalText = if (!completed && progress != null && historyEntry != null) {
            movieRecentTotalText(historyEntry.durationMs)
        } else {
            null
        },
        durationLabel = detailsProEpisodeDuration(episode.duration),
        progress = progress,
        completed = completed,
        artworkUrl = episode.posterUrl?.takeIf(String::isNotBlank) ?: fallbackBackdrop,
        download = download,
        downloadCaption = episodeDownloadControlCaption(download),
        downloadEnabled = download?.status != OfflineStatus.COMPLETED,
        cancelCaption = cancelCaption,
        isTv = isTv,
        highlighted = highlighted,
        cardRequester = targets.card,
        actionRequester = targets.primaryAction,
        cardLinks = EpisodeFocusLinks(
            up = upTarget,
            down = targets.primaryAction,
            left = leftTarget,
            right = rightTarget,
        ),
        actionLinks = EpisodeFocusLinks(
            up = targets.card,
        ),
        cancelRequester = if (cancelCaption != null) targets.secondaryAction else null,
        cancelLinks = EpisodeFocusLinks(),
        onActionDown = onDownFromActions,
        onPlay = onPlay,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        modifier = modifier,
    )
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
    showTitle: Boolean = true,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if (showTitle) if (isTv) 14.dp else 10.dp else 6.dp,
                bottom = if (isTv) 14.dp else 10.dp,
            ),
    ) {
        if (showTitle) {
            Text(
                text = title,
                color = colors.text,
                fontSize = if (isTv) 22.sp else 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = horizontalPaddingDp.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
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
                    // Similar Series uses the same approved boxed catalog card as the Series grid
                    // (square artwork, stable title/footer, truthful rating/season metadata).
                    SeriesCatalogBoxedCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = navigationModifier,
                        onLongClick = { onToggleFavorite(item) },
                    )
                } else {
                    MoviesCatalogBoxedCard(
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

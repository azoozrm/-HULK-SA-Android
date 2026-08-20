package sa.hulksa.player.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import sa.hulksa.player.data.SeriesCardMetadataStore
import sa.hulksa.player.data.SeriesCardTechnicalMetadata
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
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
import kotlin.math.roundToInt

private const val DETAILS_TV_POLISH_MOVIE_PREFS = "movie_card_verified_metadata"
private const val DETAILS_TV_POLISH_FOCUS_DELAY_MS = 110L

private data class DetailsTvPolishMetrics(
    val horizontalPaddingDp: Int,
    val heroHeightDp: Int,
    val posterWidthDp: Int,
    val titleSizeSp: Int,
    val safeHeaderDp: Int,
    val relatedWidthDp: Int,
    val episodeColumns: Int,
)

private data class DetailsTvMovieTechnical(
    val quality: String? = null,
    val durationMs: Long? = null,
)

private fun detailsTvPolishMetrics(widthDp: Int, heightDp: Int): DetailsTvPolishMetrics {
    val width = widthDp.coerceAtLeast(1)
    val height = heightDp.coerceAtLeast(1)
    return DetailsTvPolishMetrics(
        horizontalPaddingDp = (width / 34f).roundToInt().coerceIn(24, 46),
        heroHeightDp = (height * .64f).roundToInt().coerceIn(390, 570),
        posterWidthDp = (width * .16f).roundToInt().coerceIn(150, 210),
        titleSizeSp = when {
            width >= 1500 -> 40
            width >= 1100 -> 36
            else -> 31
        },
        safeHeaderDp = (height * .115f).roundToInt().coerceIn(70, 92),
        relatedWidthDp = (width * .14f).roundToInt().coerceIn(126, 168),
        episodeColumns = when {
            width >= 1600 -> 5
            width >= 1120 -> 4
            else -> 3
        },
    )
}

@Composable
fun MovieDetailsProPolishedScreen(
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
    if (!isTv) {
        MovieDetailsProScreen(
            item = item,
            details = details,
            isLoading = isLoading,
            errorMessage = errorMessage,
            isTv = false,
            isFavorite = isFavorite,
            download = download,
            historyEntry = historyEntry,
            relatedItems = relatedItems,
            isRelatedFavorite = isRelatedFavorite,
            onBack = onBack,
            onPlay = onPlay,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onToggleFavorite = onToggleFavorite,
            onToggleRelatedFavorite = onToggleRelatedFavorite,
            onOpenRelated = onOpenRelated,
        )
        return
    }

    MovieDetailsProTvPolished(
        item = item,
        details = details,
        isLoading = isLoading,
        errorMessage = errorMessage,
        isFavorite = isFavorite,
        download = download,
        historyEntry = historyEntry,
        relatedItems = relatedItems,
        isRelatedFavorite = isRelatedFavorite,
        onBack = onBack,
        onPlay = onPlay,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onToggleFavorite = onToggleFavorite,
        onToggleRelatedFavorite = onToggleRelatedFavorite,
        onOpenRelated = onOpenRelated,
    )
}

@Composable
private fun MovieDetailsProTvPolished(
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
    val adaptive = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metrics = detailsTvPolishMetrics(adaptive.screenWidthDp, adaptive.screenHeightDp)
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl
    val technical = remember(item.id) { context.detailsTvMovieTechnical(item.id) }
    val progress = historyEntry?.detailsTvWatchProgress()
    val movieResumeHeroExtraDp = if (progress != null && historyEntry != null) 34 else 0
    val playRequester = remember(item.id) { FocusRequester() }
    val favoriteRequester = remember(item.id) { FocusRequester() }
    val downloadRequester = remember(item.id) { FocusRequester() }
    val cancelRequester = remember(item.id) { FocusRequester() }
    val backRequester = remember(item.id) { FocusRequester() }
    val relatedKeys = relatedItems.map { "${it.type}:${it.id}" }
    val relatedRequesters = remember(relatedKeys) { List(relatedItems.size) { FocusRequester() } }
    val firstBelowRequester = relatedRequesters.firstOrNull()
    val downloadFocusable = download?.status != OfflineStatus.COMPLETED
    val favoriteLeftTarget = when {
        downloadFocusable -> downloadRequester
        download != null -> cancelRequester
        else -> FocusRequester.Cancel
    }

    LaunchedEffect(item.id) {
        delay(DETAILS_TV_POLISH_FOCUS_DELAY_MS)
        runCatching { playRequester.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item(key = "movie_tv_polished_hero") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((metrics.heroHeightDp + movieResumeHeroExtraDp).dp)
                    .background(Color(0xFF080906)),
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
                            .size(210.dp)
                            .graphicsLayer { alpha = .16f },
                    )
                }
                DetailsTvHeroScrim()

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandBadge(Modifier.size(52.dp))
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        text = "رجوع",
                        onClick = onBack,
                        primary = false,
                        outlined = true,
                        compact = true,
                        modifier = Modifier
                            .focusRequester(backRequester)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = playRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = metrics.horizontalPaddingDp.dp,
                            end = metrics.horizontalPaddingDp.dp,
                            top = metrics.safeHeaderDp.dp,
                            bottom = 22.dp,
                        ),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text("فيلم", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = metrics.titleSizeSp.sp,
                            lineHeight = (metrics.titleSizeSp + 5).sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(.90f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            technical.quality?.let { DetailsTvPill(it) }
                            detailsTvDuration(technical.durationMs ?: detailsTvParseDurationMs(details?.duration))?.let {
                                DetailsTvPill(it)
                            }
                            detailsTvRating(item.rating)?.let { DetailsTvPill("★ $it") }
                            item.year?.trim()?.takeIf(String::isNotBlank)?.let { DetailsTvPill(it) }
                        }
                        (details?.genre ?: item.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = genre,
                                color = colors.goldBright.copy(alpha = .92f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val plot = details?.plot ?: item.plot
                        if (!plot.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = plot,
                                color = Color(0xFFE7E3D9),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(.94f),
                            )
                        }
                        if (progress != null && historyEntry != null) {
                            Spacer(Modifier.height(8.dp))
                            DetailsTvProgress(
                                progress = progress,
                                label = "متابعة من ${detailsTvFormatTime(historyEntry.positionMs)}",
                                modifier = Modifier.fillMaxWidth(.94f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            FocusButton(
                                text = if (progress != null && historyEntry != null) {
                                    "▶ متابعة المشاهدة"
                                } else {
                                    "▶ ابدا المشاهدة"
                                },
                                onClick = onPlay,
                                compact = true,
                                modifier = Modifier
                                    .weight(1.12f)
                                    .focusRequester(playRequester)
                                    .focusProperties {
                                        up = backRequester
                                        down = firstBelowRequester ?: FocusRequester.Cancel
                                        left = favoriteRequester
                                        right = FocusRequester.Cancel
                                    },
                            )
                            FocusButton(
                                text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                                onClick = onToggleFavorite,
                                primary = false,
                                outlined = true,
                                compact = true,
                                modifier = Modifier
                                    .weight(.86f)
                                    .focusRequester(favoriteRequester)
                                    .focusProperties {
                                        up = backRequester
                                        down = firstBelowRequester ?: FocusRequester.Cancel
                                        right = playRequester
                                        left = favoriteLeftTarget
                                    },
                            )
                            FocusButton(
                                text = detailsTvMovieDownloadLabel(download),
                                onClick = onDownload,
                                primary = false,
                                outlined = true,
                                compact = true,
                                enabled = downloadFocusable,
                                modifier = Modifier
                                    .weight(.90f)
                                    .focusRequester(downloadRequester)
                                    .focusProperties {
                                        up = backRequester
                                        down = firstBelowRequester ?: FocusRequester.Cancel
                                        right = favoriteRequester
                                        left = if (download != null) cancelRequester else FocusRequester.Cancel
                                    },
                            )
                            if (download != null) {
                                FocusButton(
                                    text = if (download.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                                    onClick = onCancelDownload,
                                    primary = false,
                                    outlined = true,
                                    compact = true,
                                    modifier = Modifier
                                        .weight(.58f)
                                        .focusRequester(cancelRequester)
                                        .focusProperties {
                                            up = backRequester
                                            down = firstBelowRequester ?: FocusRequester.Cancel
                                            right = if (downloadFocusable) downloadRequester else favoriteRequester
                                            left = FocusRequester.Cancel
                                        },
                                )
                            }
                        }
                    }

                    DetailsTvPoster(
                        posterUrl = item.posterUrl,
                        title = item.name,
                        widthDp = metrics.posterWidthDp,
                    )
                }

                if (isLoading) {
                    LoadingRing(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = .42f), RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        label = "جاري تجهيز التفاصيل…",
                    )
                }
            }
        }

        if (errorMessage != null) {
            item(key = "movie_tv_polished_error") {
                ErrorNotice(
                    errorMessage,
                    Modifier.padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 10.dp),
                )
            }
        }

        if (detailsTvHasInformation(details)) {
            item(key = "movie_tv_polished_info") {
                DetailsTvInformationPanel(
                    title = "معلومات الفيلم",
                    details = details,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    widthFraction = .74f,
                    compact = true,
                )
            }
        }

        if (download != null && download.status != OfflineStatus.COMPLETED) {
            item(key = "movie_tv_polished_download") {
                DetailsTvDownloadProgress(
                    download = download,
                    modifier = Modifier.padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 8.dp),
                )
            }
        }

        if (relatedItems.isNotEmpty()) {
            item(key = "movie_tv_polished_related") {
                DetailsTvRelatedMovies(
                    title = "اعمال مشابهة",
                    items = relatedItems,
                    widthDp = metrics.relatedWidthDp,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    requesters = relatedRequesters,
                    upRequester = playRequester,
                    isFavorite = isRelatedFavorite,
                    onToggleFavorite = onToggleRelatedFavorite,
                    onOpen = onOpenRelated,
                )
            }
        }
    }
}

@Composable
fun SeriesDetailsProPolishedScreen(
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
    if (!isTv) {
        SeriesDetailsProScreen(
            series = series,
            details = details,
            episodes = episodes,
            isLoading = isLoading,
            errorMessage = errorMessage,
            isTv = false,
            isFavorite = isFavorite,
            notificationsEnabled = notificationsEnabled,
            notificationToggleAvailable = notificationToggleAvailable,
            targetEpisodeId = targetEpisodeId,
            targetSeason = targetSeason,
            targetEpisodeNumber = targetEpisodeNumber,
            downloads = downloads,
            history = history,
            relatedItems = relatedItems,
            isRelatedFavorite = isRelatedFavorite,
            onBack = onBack,
            onPlay = onPlay,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onToggleFavorite = onToggleFavorite,
            onToggleNotifications = onToggleNotifications,
            onToggleRelatedFavorite = onToggleRelatedFavorite,
            onOpenRelated = onOpenRelated,
        )
        return
    }

    SeriesDetailsProTvPolished(
        series = series,
        details = details,
        episodes = episodes,
        isLoading = isLoading,
        errorMessage = errorMessage,
        isFavorite = isFavorite,
        notificationsEnabled = notificationsEnabled,
        notificationToggleAvailable = notificationToggleAvailable,
        targetEpisodeId = targetEpisodeId,
        targetSeason = targetSeason,
        targetEpisodeNumber = targetEpisodeNumber,
        downloads = downloads,
        history = history,
        relatedItems = relatedItems,
        isRelatedFavorite = isRelatedFavorite,
        onBack = onBack,
        onPlay = onPlay,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onToggleFavorite = onToggleFavorite,
        onToggleNotifications = onToggleNotifications,
        onToggleRelatedFavorite = onToggleRelatedFavorite,
        onOpenRelated = onOpenRelated,
    )
}

@Composable
private fun SeriesDetailsProTvPolished(
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
    val adaptive = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metrics = detailsTvPolishMetrics(adaptive.screenWidthDp, adaptive.screenHeightDp)
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    var technical by remember(series.id) { mutableStateOf(SeriesCardTechnicalMetadata()) }
    LaunchedEffect(series.id, metadataStore) { technical = metadataStore.metadata(series.id) }

    val ordered = remember(episodes) {
        episodes.sortedWith(compareBy(Episode::season, Episode::episodeNumber, Episode::id))
    }
    val seasons = remember(ordered) { ordered.map(Episode::season).filter { it > 0 }.distinct() }
    val historyByKey = remember(history) { history.associateBy(HistoryEntry::key) }
    val resumePair = remember(ordered, historyByKey) {
        ordered.mapNotNull { episode ->
            val entry = historyByKey["SERIES:${episode.id}"] ?: return@mapNotNull null
            val progress = entry.detailsTvWatchProgress() ?: return@mapNotNull null
            Triple(episode, entry, progress)
        }.maxByOrNull { it.second.updatedAtEpochMs }
    }
    val targetEpisode = remember(ordered, targetEpisodeId, targetSeason, targetEpisodeNumber) {
        if (targetEpisodeId != null) {
            ordered.firstOrNull { it.id == targetEpisodeId }
        } else {
            ordered.firstOrNull {
                targetSeason != null &&
                    targetEpisodeNumber != null &&
                    it.season == targetSeason &&
                    it.episodeNumber == targetEpisodeNumber
            }
        }
    }
    var selectedSeason by rememberSaveable(series.id, targetEpisodeId, targetSeason, targetEpisodeNumber) {
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
    val visibleEpisodes = remember(ordered, selectedSeason) {
        if (selectedSeason == 0) ordered else ordered.filter { it.season == selectedSeason }
    }
    val completedCount = remember(ordered, historyByKey) {
        ordered.count { historyByKey["SERIES:${it.id}"]?.detailsTvCompleted() == true }
    }
    val currentEpisode = targetEpisode ?: resumePair?.first ?: ordered.firstOrNull()
    val currentIndex = ordered.indexOfFirst { it.id == currentEpisode?.id }
    val nextEpisode = if (currentEpisode != null) ordered.getOrNull(currentIndex + 1) else null
    val backdrop = details?.backdropUrl ?: series.backdropUrl ?: series.posterUrl
    val seriesResumeHeroExtraDp = (if (resumePair != null) 34 else 0) + 64

    val backRequester = remember(series.id) { FocusRequester() }
    val playRequester = remember(series.id) { FocusRequester() }
    val nextRequester = remember(series.id) { FocusRequester() }
    val favoriteRequester = remember(series.id) { FocusRequester() }
    val notificationRequester = remember(series.id) { FocusRequester() }
    val seasonRequesters = remember(series.id, seasons) { List(seasons.size) { FocusRequester() } }
    val episodeKeys = visibleEpisodes.map(Episode::id)
    val episodeCardRequesters = remember(series.id, selectedSeason, episodeKeys) {
        List(visibleEpisodes.size) { FocusRequester() }
    }
    val episodeActionRequesters = remember(series.id, selectedSeason, episodeKeys) {
        List(visibleEpisodes.size) { FocusRequester() }
    }
    val relatedKeys = relatedItems.map { "${it.type}:${it.id}" }
    val relatedRequesters = remember(series.id, relatedKeys) { List(relatedItems.size) { FocusRequester() } }
    val firstEpisodeRequester = episodeCardRequesters.firstOrNull()
    val firstSeasonRequester = seasonRequesters.firstOrNull()
    val heroDownTarget = firstSeasonRequester ?: firstEpisodeRequester
    val listState = rememberLazyListState()

    LaunchedEffect(series.id, targetEpisode?.id) {
        if (targetEpisode == null) {
            delay(DETAILS_TV_POLISH_FOCUS_DELAY_MS)
            if (currentEpisode != null) runCatching { playRequester.requestFocus() }
        }
    }
    LaunchedEffect(targetEpisode?.id, selectedSeason, visibleEpisodes, metrics.episodeColumns) {
        val targetIndex = visibleEpisodes.indexOfFirst { it.id == targetEpisode?.id }
        if (targetIndex >= 0) {
            val episodeRowStartIndex = 2 +
                (if (errorMessage != null) 1 else 0) +
                (if (detailsTvHasInformation(details)) 1 else 0)
            listState.scrollToItem(episodeRowStartIndex + targetIndex / metrics.episodeColumns)
            delay(DETAILS_TV_POLISH_FOCUS_DELAY_MS)
            episodeCardRequesters.getOrNull(targetIndex)?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item(key = "series_tv_polished_hero") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((metrics.heroHeightDp + seriesResumeHeroExtraDp).dp)
                    .background(Color(0xFF080906)),
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
                            .size(210.dp)
                            .graphicsLayer { alpha = .16f },
                    )
                }
                DetailsTvHeroScrim()

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandBadge(Modifier.size(52.dp))
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        text = "رجوع",
                        onClick = onBack,
                        primary = false,
                        outlined = true,
                        compact = true,
                        modifier = Modifier
                            .focusRequester(backRequester)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = playRequester
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = metrics.horizontalPaddingDp.dp,
                            end = metrics.horizontalPaddingDp.dp,
                            top = metrics.safeHeaderDp.dp,
                            bottom = 22.dp,
                        ),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                        Text("مسلسل", color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = series.name,
                            color = Color.White,
                            fontSize = metrics.titleSizeSp.sp,
                            lineHeight = (metrics.titleSizeSp + 5).sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(.90f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            technical.quality?.takeIf(String::isNotBlank)?.let { DetailsTvPill(it) }
                            val seasonCount = technical.seasonCount ?: seasons.size.takeIf { it > 0 }
                            seasonCount?.let { DetailsTvPill("$it موسم") }
                            if (ordered.isNotEmpty()) DetailsTvPill("${ordered.size} حلقة")
                            detailsTvRating(series.rating)?.let { DetailsTvPill("★ $it") }
                            if (completedCount > 0) DetailsTvPill("✓ $completedCount مكتملة")
                        }
                        (details?.genre ?: series.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                genre,
                                color = colors.goldBright.copy(alpha = .92f),
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
                                color = Color(0xFFE7E3D9),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(.94f),
                            )
                        }
                        if (resumePair != null) {
                            Spacer(Modifier.height(8.dp))
                            DetailsTvProgress(
                                progress = resumePair.third,
                                label = "متابعة من ${detailsTvFormatTime(resumePair.second.positionMs)}",
                                modifier = Modifier.fillMaxWidth(.94f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                FocusButton(
                                    text = if (resumePair != null) "▶ متابعة المشاهدة" else "▶ ابدا المشاهدة",
                                    onClick = { currentEpisode?.let(onPlay) },
                                    enabled = currentEpisode != null,
                                    compact = true,
                                    scaleOnFocus = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(seriesDetailsActionHeightDp().dp)
                                        .focusRequester(playRequester)
                                        .focusProperties {
                                            up = backRequester
                                            down = favoriteRequester
                                            right = FocusRequester.Cancel
                                            left = nextRequester
                                        },
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
                                        .height(seriesDetailsActionHeightDp().dp)
                                        .focusRequester(nextRequester)
                                        .focusProperties {
                                            up = backRequester
                                            down = notificationRequester
                                            right = playRequester
                                            left = FocusRequester.Cancel
                                        },
                                )
                            }
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
                                            up = playRequester
                                            down = heroDownTarget ?: FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                            left = notificationRequester
                                        },
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
                                        .height(seriesDetailsActionHeightDp().dp)
                                        .focusRequester(notificationRequester)
                                        .focusProperties {
                                            up = nextRequester
                                            down = heroDownTarget ?: FocusRequester.Cancel
                                            right = favoriteRequester
                                            left = FocusRequester.Cancel
                                        },
                                )
                            }
                        }
                    }

                    DetailsTvPoster(
                        posterUrl = series.posterUrl,
                        title = series.name,
                        widthDp = metrics.posterWidthDp,
                    )
                }

                if (isLoading) {
                    LoadingRing(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = .42f), RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        label = "جاري تجهيز تفاصيل المسلسل…",
                    )
                }
            }
        }

        if (errorMessage != null) {
            item(key = "series_tv_polished_error") {
                ErrorNotice(
                    errorMessage,
                    Modifier.padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 10.dp),
                )
            }
        }

        if (detailsTvHasInformation(details)) {
            item(key = "series_tv_polished_info") {
                DetailsTvInformationPanel(
                    title = "معلومات المسلسل",
                    details = details,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    widthFraction = .74f,
                    compact = true,
                )
            }
        }

        item(key = "series_tv_polished_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = metrics.horizontalPaddingDp.dp,
                        end = metrics.horizontalPaddingDp.dp,
                        top = 10.dp,
                        bottom = 6.dp,
                    ),
            ) {
                Text("الحلقات", color = colors.text, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(
                    if (completedCount > 0) "$completedCount مكتملة من ${ordered.size}" else "${ordered.size} حلقة",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
                resumePair?.let { resume ->
                    Spacer(Modifier.height(7.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = AbsoluteAlignment.CenterRight,
                    ) {
                        val shape = RoundedCornerShape(8.dp)
                        Text(
                            text = "الحلقة الحالية : S${resume.first.season} E${resume.first.episodeNumber}  •  ${detailsTvFormatTime(resume.second.positionMs)}",
                            color = colors.goldBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(shape)
                                .background(colors.surface.copy(alpha = .84f))
                                .border(1.dp, colors.gold.copy(alpha = .48f), shape)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                if (seasons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                            val requester = seasonRequesters[index]
                            val episodeDown = firstEpisodeRequester?.let {
                                episodeCardRequesters.getOrNull(index.coerceAtMost((metrics.episodeColumns - 1).coerceAtLeast(0))) ?: it
                            }
                            FocusButton(
                                text = "الموسم $season",
                                onClick = { selectedSeason = season },
                                primary = selectedSeason == season,
                                outlined = selectedSeason != season,
                                compact = true,
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .focusProperties {
                                        up = FocusRequester.Default
                                        down = episodeDown ?: FocusRequester.Default
                                        right = if (index > 0) seasonRequesters[index - 1] else FocusRequester.Cancel
                                        left = if (index < seasonRequesters.lastIndex) seasonRequesters[index + 1] else FocusRequester.Cancel
                                    },
                            )
                        }
                    }
                }
            }
        }

        val rows = visibleEpisodes.chunked(metrics.episodeColumns)
        rows.forEachIndexed { rowIndex, rowEpisodes ->
            item(key = "series_tv_polished_episode_row_${selectedSeason}_$rowIndex") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowEpisodes.forEachIndexed { columnIndex, episode ->
                        val absoluteIndex = rowIndex * metrics.episodeColumns + columnIndex
                        val cardRequester = episodeCardRequesters[absoluteIndex]
                        val actionRequester = episodeActionRequesters[absoluteIndex]
                        val download = downloads.firstOrNull { it.historyKey == "SERIES:${episode.id}" }
                        val historyEntry = historyByKey["SERIES:${episode.id}"]
                        val rightCard = if (columnIndex > 0) episodeCardRequesters.getOrNull(absoluteIndex - 1) else null
                        val leftCard = if (columnIndex < rowEpisodes.lastIndex) episodeCardRequesters.getOrNull(absoluteIndex + 1) else null
                        val rightAction = if (columnIndex > 0) episodeActionRequesters.getOrNull(absoluteIndex - 1) else null
                        val leftAction = if (columnIndex < rowEpisodes.lastIndex) episodeActionRequesters.getOrNull(absoluteIndex + 1) else null
                        val upCard = if (rowIndex > 0) {
                            episodeCardRequesters.getOrNull(absoluteIndex - metrics.episodeColumns)
                                ?: seasonRequesters.getOrNull(columnIndex.coerceAtMost(seasonRequesters.lastIndex.coerceAtLeast(0)))
                                ?: playRequester
                        } else {
                            seasonRequesters.getOrNull(columnIndex.coerceAtMost(seasonRequesters.lastIndex.coerceAtLeast(0)))
                                ?: playRequester
                        }
                        val nextRow = rows.getOrNull(rowIndex + 1)
                        val nextRowCard = nextRow?.let { nextEpisodes ->
                            val nextColumn = columnIndex.coerceAtMost(nextEpisodes.lastIndex)
                            episodeCardRequesters.getOrNull((rowIndex + 1) * metrics.episodeColumns + nextColumn)
                        }
                        val relatedDown = if (nextRowCard == null && relatedRequesters.isNotEmpty()) {
                            relatedRequesters[columnIndex.coerceAtMost(relatedRequesters.lastIndex)]
                        } else {
                            null
                        }
                        DetailsTvEpisodeUnit(
                            episode = episode,
                            highlighted = targetEpisode?.id == episode.id,
                            fallbackArtwork = backdrop,
                            historyEntry = historyEntry,
                            download = download,
                            modifier = Modifier.weight(1f),
                            cardRequester = cardRequester,
                            actionRequester = actionRequester,
                            leftCard = leftCard,
                            rightCard = rightCard,
                            leftAction = leftAction,
                            rightAction = rightAction,
                            upCard = upCard,
                            downCard = nextRowCard ?: relatedDown,
                            onPlay = { onPlay(episode) },
                            onDownload = { onDownload(episode) },
                            onCancelDownload = { onCancelDownload(episode) },
                        )
                    }
                    repeat(metrics.episodeColumns - rowEpisodes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (relatedItems.isNotEmpty()) {
            item(key = "series_tv_polished_related") {
                DetailsTvRelatedSeries(
                    title = "مسلسلات مشابهة",
                    items = relatedItems,
                    widthDp = metrics.relatedWidthDp,
                    horizontalPaddingDp = metrics.horizontalPaddingDp,
                    requesters = relatedRequesters,
                    upRequester = episodeCardRequesters.lastOrNull() ?: firstSeasonRequester ?: playRequester,
                    isFavorite = isRelatedFavorite,
                    onToggleFavorite = onToggleRelatedFavorite,
                    onOpen = onOpenRelated,
                )
            }
        }
    }
}

@Composable
private fun DetailsTvEpisodeUnit(
    episode: Episode,
    highlighted: Boolean,
    fallbackArtwork: String?,
    historyEntry: HistoryEntry?,
    download: OfflineDownload?,
    modifier: Modifier,
    cardRequester: FocusRequester,
    actionRequester: FocusRequester,
    leftCard: FocusRequester?,
    rightCard: FocusRequester?,
    leftAction: FocusRequester?,
    rightAction: FocusRequester?,
    upCard: FocusRequester,
    downCard: FocusRequester?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember(episode.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "tvEpisodePolishScale")
    val progress = historyEntry?.detailsTvWatchProgress()
    val completed = historyEntry?.detailsTvCompleted() == true
    val artwork = episode.posterUrl?.takeIf(String::isNotBlank) ?: fallbackArtwork
    val shape = RoundedCornerShape(12.dp)
    val cancelRequester = remember(episode.id, download?.downloadId) { FocusRequester() }
    val actionEnabled = download?.status != OfflineStatus.COMPLETED
    val cardDownTarget = if (actionEnabled) actionRequester else cancelRequester

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 12.dp.toPx() else 0f
                }
                .clip(shape)
                .background(Color(0xFF15160F))
                .border(
                    if (focused) 3.dp else if (highlighted) 2.dp else 1.dp,
                    if (focused || highlighted) colors.goldBright else colors.line.copy(alpha = .42f),
                    shape,
                )
                .focusRequester(cardRequester)
                .focusProperties {
                    up = upCard
                    down = cardDownTarget
                    left = leftCard ?: FocusRequester.Cancel
                    right = rightCard ?: FocusRequester.Cancel
                }
                .onFocusChanged { focused = it.isFocused }
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
                BrandLogo(Modifier.align(Alignment.Center).size(58.dp).graphicsLayer { alpha = .3f })
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .50f to Color.Transparent,
                        .76f to Color.Black.copy(alpha = .62f),
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
            )
            DetailsTvPill(
                text = "S${episode.season} · E${episode.episodeNumber}",
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            detailsTvEpisodeDuration(episode.duration)?.let { duration ->
                DetailsTvPill(
                    text = duration,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "الحلقة ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                detailsTvUsefulEpisodeTitle(episode)?.let { title ->
                    Text(
                        title,
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (completed) {
                    Text("✓ تمت المشاهدة", color = colors.goldBright, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                } else if (progress != null && historyEntry != null) {
                    Text(
                        "استكمال ${detailsTvFormatTime(historyEntry.positionMs)}",
                        color = colors.goldBright,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (progress != null || completed) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = .20f)),
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
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            DetailsTvMiniAction(
                text = detailsTvEpisodeDownloadLabel(download),
                onClick = onDownload,
                requester = actionRequester,
                modifier = Modifier.weight(1f),
                upTarget = cardRequester,
                downTarget = downCard,
                leftTarget = if (download != null) cancelRequester else leftAction,
                rightTarget = rightAction,
                enabled = actionEnabled,
            )
            if (download != null) {
                DetailsTvMiniAction(
                    text = if (download.status == OfflineStatus.COMPLETED) "حذف" else "الغاء",
                    onClick = onCancelDownload,
                    requester = cancelRequester,
                    modifier = Modifier.width(60.dp),
                    upTarget = cardRequester,
                    downTarget = downCard,
                    rightTarget = if (actionEnabled) actionRequester else rightAction,
                    leftTarget = leftAction,
                )
            }
        }
    }
}

@Composable
private fun DetailsTvMiniAction(
    text: String,
    onClick: () -> Unit,
    requester: FocusRequester,
    modifier: Modifier,
    upTarget: FocusRequester,
    downTarget: FocusRequester?,
    leftTarget: FocusRequester? = null,
    rightTarget: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(if (focused) colors.gold.copy(alpha = .22f) else colors.surfaceRaised.copy(alpha = .76f))
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.goldBright else colors.line.copy(alpha = .55f), shape)
            .focusRequester(requester)
            .focusProperties {
                up = upTarget
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
                            runCatching { upTarget.requestFocus() }
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
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsTvResumeStrip(
    progress: Float,
    label: String,
    horizontalPaddingDp: Int,
) {
    DetailsTvProgress(
        progress = progress,
        label = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPaddingDp.dp, vertical = 6.dp),
    )
}

@Composable
private fun DetailsTvInformationPanel(
    title: String,
    details: ContentDetails?,
    horizontalPaddingDp: Int,
    widthFraction: Float = 1f,
    compact: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val items = buildList {
        details?.releaseDate?.takeIf(String::isNotBlank)?.let { add("تاريخ العرض" to it) }
        details?.director?.takeIf(String::isNotBlank)?.let { add("الاخراج" to it) }
        details?.cast?.takeIf(String::isNotBlank)?.let { add("البطولة" to it) }
    }
    if (items.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPaddingDp.dp, vertical = 6.dp),
        contentAlignment = AbsoluteAlignment.CenterRight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction.coerceIn(.55f, 1f))
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface.copy(alpha = .82f))
                .border(1.dp, colors.gold.copy(alpha = .40f), RoundedCornerShape(12.dp))
                .padding(
                    horizontal = if (compact) 12.dp else 14.dp,
                    vertical = if (compact) 7.dp else 9.dp,
                ),
        ) {
            Text(
                title,
                color = colors.text,
                fontSize = if (compact) 15.sp else 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { (label, value) ->
                    DetailsTvInfoItem(label, value, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailsTvInfoItem(label: String, value: String, modifier: Modifier) {
    val colors = LocalHulkColors.current
    Column(modifier) {
        Text(label, color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(1.dp))
        Text(
            value,
            color = colors.textMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = if (label == "البطولة") 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsTvRelatedMovies(
    title: String,
    items: List<ContentItem>,
    widthDp: Int,
    horizontalPaddingDp: Int,
    requesters: List<FocusRequester>,
    upRequester: FocusRequester,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp)) {
        Text(
            title,
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = horizontalPaddingDp.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPaddingDp.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                CompactPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    onLongClick = { onToggleFavorite(item) },
                    modifier = Modifier
                        .width(widthDp.dp)
                        .focusRequester(requesters[index])
                        .focusProperties {
                            up = upRequester
                            down = FocusRequester.Cancel
                            right = if (index > 0) requesters[index - 1] else FocusRequester.Cancel
                            left = if (index < requesters.lastIndex) requesters[index + 1] else FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

@Composable
private fun DetailsTvRelatedSeries(
    title: String,
    items: List<ContentItem>,
    widthDp: Int,
    horizontalPaddingDp: Int,
    requesters: List<FocusRequester>,
    upRequester: FocusRequester,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp)) {
        Text(
            title,
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = horizontalPaddingDp.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPaddingDp.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                SeriesPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    onLongClick = { onToggleFavorite(item) },
                    modifier = Modifier
                        .width(widthDp.dp)
                        .focusRequester(requesters[index])
                        .focusProperties {
                            up = upRequester
                            down = FocusRequester.Cancel
                            right = if (index > 0) requesters[index - 1] else FocusRequester.Cancel
                            left = if (index < requesters.lastIndex) requesters[index + 1] else FocusRequester.Cancel
                        },
                )
            }
        }
    }
}

@Composable
private fun DetailsTvHeroScrim() {
    val colors = LocalHulkColors.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .22f),
                .38f to Color.Black.copy(alpha = .18f),
                .72f to Color.Black.copy(alpha = .60f),
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
                    colors.background.copy(alpha = .93f),
                ),
            ),
        ),
    )
}

@Composable
private fun DetailsTvPoster(posterUrl: String?, title: String, widthDp: Int) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(1.dp, colors.gold.copy(alpha = .58f), shape),
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
            BrandLogo(Modifier.fillMaxSize().padding(25.dp).graphicsLayer { alpha = .52f })
        }
    }
}

@Composable
private fun DetailsTvPill(text: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = .72f))
            .border(1.dp, colors.gold.copy(alpha = .40f), shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DetailsTvProgress(progress: Float, label: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(modifier) {
        Text(label, color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = .18f)),
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
        }
    }
}

@Composable
private fun DetailsTvDownloadProgress(download: OfflineDownload, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    val percent = (download.progress * 100).toInt().coerceIn(0, 100)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = .75f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(detailsTvDownloadState(download), color = colors.textMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("$percent%", color = colors.goldBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(download.progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
        }
    }
}

private fun Context.detailsTvMovieTechnical(movieId: Int): DetailsTvMovieTechnical {
    val prefs = applicationContext.getSharedPreferences(DETAILS_TV_POLISH_MOVIE_PREFS, Context.MODE_PRIVATE)
    return DetailsTvMovieTechnical(
        quality = prefs.getString("movie:$movieId:quality", null)?.trim()?.takeIf(String::isNotBlank),
        durationMs = prefs.getLong("movie:$movieId:duration_ms", 0L).takeIf { it > 0L },
    )
}

private fun detailsTvHasInformation(details: ContentDetails?): Boolean =
    !details?.releaseDate.isNullOrBlank() || !details?.director.isNullOrBlank() || !details?.cast.isNullOrBlank()

private fun HistoryEntry.detailsTvWatchProgress(): Float? {
    if (positionMs <= 0L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

private fun HistoryEntry.detailsTvCompleted(): Boolean =
    durationMs > 0L && positionMs.toDouble() / durationMs.toDouble() >= .95

private fun detailsTvRating(raw: String?): String? {
    val value = raw?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
    return String.format(Locale.US, "%.1f", value)
}

private fun detailsTvParseDurationMs(raw: String?): Long? {
    val clean = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val parts = clean.split(':').map(String::trim)
    val seconds = when (parts.size) {
        3 -> {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].substringBefore('.').toLongOrNull() ?: return null
            h * 3600L + m * 60L + s
        }
        2 -> {
            val m = parts[0].toLongOrNull() ?: return null
            val s = parts[1].substringBefore('.').toLongOrNull() ?: return null
            m * 60L + s
        }
        else -> return null
    }
    return seconds.takeIf { it > 0L }?.times(1000L)
}

private fun detailsTvDuration(durationMs: Long?): String? {
    val total = durationMs?.takeIf { it > 0L }?.div(60_000L) ?: return null
    val hours = total / 60L
    val minutes = total % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        total > 0L -> String.format(Locale.US, "%dm", total)
        else -> null
    }
}

private fun detailsTvEpisodeDuration(raw: String?): String? =
    detailsTvDuration(detailsTvParseDurationMs(raw)) ?: raw?.trim()?.takeIf(String::isNotBlank)

private fun detailsTvUsefulEpisodeTitle(episode: Episode): String? {
    val title = episode.title.trim().takeIf(String::isNotBlank) ?: return null
    val normalized = title.lowercase(Locale.ROOT)
    val generic = listOf(
        "الحلقة ${episode.episodeNumber}",
        "episode ${episode.episodeNumber}",
        "ep ${episode.episodeNumber}",
    ).any { normalized == it.lowercase(Locale.ROOT) }
    return title.takeUnless { generic }
}

private fun detailsTvMovieDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ تحميل ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف التحميل"
    OfflineStatus.FAILED -> "↻ اعادة التحميل"
    null -> "↓ تحميل الفيلم"
}

private fun detailsTvEpisodeDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ محمل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف"
    OfflineStatus.FAILED -> "↻ اعادة"
    null -> "↓ تحميل"
}

private fun detailsTvDownloadState(download: OfflineDownload): String = when (download.status) {
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

private fun detailsTvFormatTime(ms: Long): String {
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

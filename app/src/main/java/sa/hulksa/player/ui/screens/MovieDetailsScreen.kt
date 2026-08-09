package sa.hulksa.player.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
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
import java.util.Locale

private const val MOVIE_DETAILS_METADATA_PREFS = "movie_card_verified_metadata"

private data class MovieDetailsTechnicalMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
)

@Composable
fun MovieDetailsScreen(
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
    val context = LocalContext.current
    val technicalMetadata = remember(item.id) { context.movieDetailsTechnicalMetadata(item.id) }
    val compactRating = compactMovieDetailsRating(item.rating)
    val compactDuration = compactMovieDetailsDuration(
        technicalMetadata.durationMs ?: parseMovieDetailsDurationMs(details?.duration),
    )
    val displayYear = item.year
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { year -> item.name.contains(year, ignoreCase = true) }
    val playFocusRequester = remember(item.id) { FocusRequester() }
    LaunchedEffect(item.id, isTv) {
        if (isTv) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }
    val relatedFavoriteOverrides = remember(item.id) { mutableStateMapOf<String, Boolean>() }
    val relatedIsFavorite: (ContentItem) -> Boolean = { related ->
        relatedFavoriteOverrides["${related.type.name}:${related.id}"] ?: isRelatedFavorite(related)
    }
    val toggleRelatedFavorite: (ContentItem) -> Unit = { related ->
        val key = "${related.type.name}:${related.id}"
        relatedFavoriteOverrides[key] = !relatedIsFavorite(related)
        onToggleRelatedFavorite(related)
    }
    val backdrop = details?.backdropUrl ?: item.backdropUrl ?: item.posterUrl
    val progress = historyEntry?.watchProgress()
    val resumePosition = historyEntry?.positionMs?.takeIf { progress != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(510.dp)
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
                    BrandLogo(Modifier.align(Alignment.Center).size(230.dp).graphicsLayer { alpha = .18f })
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .34f),
                            .46f to Color.Black.copy(alpha = .38f),
                            .72f to Color.Black.copy(alpha = .62f),
                            1f to colors.background,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.background.copy(alpha = .94f),
                                Color.Black.copy(alpha = .64f),
                                Color.Black.copy(alpha = .18f),
                            ),
                        ),
                    ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                        .padding(horizontal = if (isTv) 30.dp else 12.dp, vertical = if (isTv) 24.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandBadge(Modifier.size(if (isTv) 54.dp else 49.dp))
                    Spacer(Modifier.weight(1f))
                    FocusButton("رجوع", onBack, primary = false, compact = true)
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = if (isTv) 38.dp else 18.dp,
                            end = if (isTv) 38.dp else 18.dp,
                            bottom = if (isTv) 30.dp else 22.dp,
                        ),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("فيلم", color = colors.goldBright.copy(alpha = .9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = if (isTv) 40.sp else 26.sp,
                            lineHeight = if (isTv) 46.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            technicalMetadata.quality?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                            compactDuration?.let { InfoPill(it) }
                            compactRating?.let { InfoPill("★ $it") }
                            displayYear?.let { InfoPill(it) }
                        }
                        (details?.genre ?: item.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                            Spacer(Modifier.height(7.dp))
                            Text(
                                genre,
                                color = Color.White.copy(alpha = .78f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val plot = details?.plot ?: item.plot
                        if (!plot.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = plot,
                                color = Color(0xFFE7E3D8),
                                fontSize = if (isTv) 14.sp else 12.sp,
                                lineHeight = if (isTv) 21.sp else 19.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (progress != null && historyEntry != null) {
                            Spacer(Modifier.height(12.dp))
                            WatchProgress(
                                progress = progress,
                                label = "شاهدت ${(progress * 100).toInt()}%  •  المتابعة من ${detailsFormatTime(historyEntry.positionMs)}",
                            )
                        }
                        Spacer(Modifier.height(15.dp))
                        if (isTv) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FocusButton(
                                    text = if (resumePosition != null) {
                                        "▶ استكمال · ${detailsFormatTime(resumePosition)}"
                                    } else {
                                        "▶ مشاهدة الفيلم"
                                    },
                                    onClick = onPlay,
                                    modifier = Modifier
                                        .weight(1.12f)
                                        .focusRequester(playFocusRequester),
                                )
                                FocusButton(
                                    text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                                    onClick = onToggleFavorite,
                                    primary = false,
                                    modifier = Modifier.weight(1f),
                                )
                                FocusButton(
                                    text = movieDownloadLabel(download),
                                    onClick = onDownload,
                                    primary = false,
                                    enabled = download?.status != OfflineStatus.COMPLETED,
                                    compact = true,
                                    modifier = Modifier.weight(.86f),
                                )
                            }
                            if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
                                Spacer(Modifier.height(8.dp))
                                MovieDownloadProgress(download)
                            }
                            if (download != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    FocusButton(
                                        text = if (download.status == OfflineStatus.COMPLETED) "حذف التحميل" else "الغاء التحميل",
                                        onClick = onCancelDownload,
                                        primary = false,
                                        compact = true,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FocusButton(
                                    text = if (resumePosition != null) {
                                        "▶ استكمال · ${detailsFormatTime(resumePosition)}"
                                    } else {
                                        "▶ مشاهدة الفيلم"
                                    },
                                    onClick = onPlay,
                                    compact = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(playFocusRequester),
                                )
                                FocusButton(
                                    text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                                    onClick = onToggleFavorite,
                                    primary = false,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
                                Spacer(Modifier.height(8.dp))
                                MovieDownloadProgress(download)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                FocusButton(
                                    text = movieDownloadLabel(download),
                                    onClick = onDownload,
                                    primary = false,
                                    enabled = download?.status != OfflineStatus.COMPLETED,
                                    compact = true,
                                )
                                if (download != null) {
                                    FocusButton(
                                        text = if (download.status == OfflineStatus.COMPLETED) "حذف التحميل" else "الغاء التحميل",
                                        onClick = onCancelDownload,
                                        primary = false,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }

                    MoviePoster(
                        posterUrl = item.posterUrl,
                        title = item.name,
                        isTv = isTv,
                        modifier = Modifier.width(if (isTv) 184.dp else 126.dp),
                    )
                }

                if (isLoading) {
                    LoadingRing(
                        modifier = Modifier.align(Alignment.TopStart).padding(top = if (isTv) 30.dp else 18.dp, start = if (isTv) 102.dp else 78.dp),
                    )
                }
            }
        }

        if (errorMessage != null) {
            item {
                ErrorNotice(
                    errorMessage,
                    Modifier.padding(horizontal = if (isTv) 38.dp else 18.dp, vertical = 10.dp),
                )
            }
        }

        if (hasMovieInformation(details)) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (isTv) 38.dp else 18.dp,
                            end = if (isTv) 38.dp else 18.dp,
                            top = if (isTv) 6.dp else 8.dp,
                            bottom = if (isTv) 14.dp else 20.dp,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    MovieInformation(
                        details = details,
                        isTv = isTv,
                        modifier = if (isTv) Modifier.fillMaxWidth(.72f) else Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (relatedItems.isNotEmpty()) {
            item {
                Column(
                    Modifier.padding(bottom = if (isTv) 38.dp else 24.dp),
                ) {
                    Text(
                        "اعمال مشابهة",
                        color = colors.text,
                        fontSize = if (isTv) 23.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = if (isTv) 38.dp else 18.dp),
                    )
                    Spacer(Modifier.height(if (isTv) 9.dp else 12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (isTv) 38.dp else 18.dp, vertical = 6.dp),
                    ) {
                        items(relatedItems, key = ContentItem::id) { related ->
                            CompactPosterCard(
                                item = related,
                                isFavorite = relatedIsFavorite(related),
                                onClick = { onOpenRelated(related) },
                                modifier = Modifier.width(if (isTv) 142.dp else 112.dp),
                                onLongClick = { toggleRelatedFavorite(related) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePoster(
    posterUrl: String?,
    title: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 18.dp else 14.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(1.dp, colors.gold.copy(alpha = .55f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(posterUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.fillMaxSize().padding(24.dp).graphicsLayer { alpha = .55f })
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))),
        )
    }
}

@Composable
private fun MovieDownloadProgress(download: OfflineDownload) {
    val colors = LocalHulkColors.current
    val percent = (download.progress * 100).toInt().coerceIn(0, 100)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = .52f))
            .border(1.dp, colors.line.copy(alpha = .55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    else -> "جاري تحميل الفيلم"
                },
                color = colors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text("$percent%", color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(download.progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
        }
        if (download.totalBytes > 0L) {
            Spacer(Modifier.height(5.dp))
            Text(
                "${movieFormatBytes(download.bytesDownloaded)} / ${movieFormatBytes(download.totalBytes)}",
                color = colors.textMuted,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WatchProgress(progress: Float, label: String) {
    val colors = LocalHulkColors.current
    Column(Modifier.fillMaxWidth(.72f)) {
        Text(label, color = colors.goldBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .16f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
        }
    }
}

@Composable
private fun MovieInformation(
    details: ContentDetails?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = .82f))
            .border(1.dp, colors.line.copy(alpha = .8f), RoundedCornerShape(14.dp))
            .padding(horizontal = if (isTv) 18.dp else 16.dp, vertical = if (isTv) 14.dp else 16.dp),
    ) {
        Text(
            "معلومات الفيلم",
            color = colors.text,
            fontSize = if (isTv) 18.sp else 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(if (isTv) 8.dp else 12.dp))
        details?.releaseDate?.takeIf(String::isNotBlank)?.let { DetailLine("تاريخ العرض", it, isTv) }
        details?.director?.takeIf(String::isNotBlank)?.let { DetailLine("الاخراج", it, isTv) }
        details?.cast?.takeIf(String::isNotBlank)?.let { DetailLine("البطولة", it, isTv) }
    }
}

@Composable
private fun DetailLine(label: String, value: String, isTv: Boolean) {
    val colors = LocalHulkColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (isTv) 3.dp else 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = colors.goldBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(if (isTv) 84.dp else 90.dp),
        )
        Text(
            value,
            color = colors.textMuted,
            fontSize = 11.sp,
            lineHeight = if (isTv) 16.sp else 18.sp,
            maxLines = if (isTv) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Context.movieDetailsTechnicalMetadata(movieId: Int): MovieDetailsTechnicalMetadata {
    val prefs = applicationContext.getSharedPreferences(MOVIE_DETAILS_METADATA_PREFS, Context.MODE_PRIVATE)
    val quality = prefs.getString("movie:$movieId:quality", null)
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val durationMs = prefs.getLong("movie:$movieId:duration_ms", 0L)
        .takeIf { it > 0L }
    return MovieDetailsTechnicalMetadata(quality = quality, durationMs = durationMs)
}

private fun compactMovieDetailsRating(raw: String?): String? {
    val value = raw
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?: return null
    return String.format(Locale.US, "%.1f", value)
}

private fun compactMovieDetailsDuration(durationMs: Long?): String? {
    val totalMinutes = durationMs
        ?.takeIf { it > 0L }
        ?.div(60_000L)
        ?: return null
    if (totalMinutes <= 0L) return null
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dh", hours)
        else -> String.format(Locale.US, "%dm", minutes)
    }
}

private fun parseMovieDetailsDurationMs(raw: String?): Long? {
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

private fun HistoryEntry.watchProgress(): Float? {
    if (positionMs < 30_000L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

private fun hasMovieInformation(details: ContentDetails?): Boolean =
    !details?.director.isNullOrBlank() || !details?.cast.isNullOrBlank() || !details?.releaseDate.isNullOrBlank()

private fun movieDownloadLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "✓ تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "⏸ ايقاف التحميل ${(download.progress * 100).toInt()}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "▶ استئناف التحميل"
    OfflineStatus.FAILED -> "↻ اعادة التحميل"
    null -> "↓ تحميل الفيلم"
}

private fun movieFormatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> String.format(Locale.US, "%.0f MB", safe / (1024.0 * 1024.0))
        safe >= 1024L -> String.format(Locale.US, "%.0f KB", safe / 1024.0)
        else -> "$safe B"
    }
}

private fun detailsFormatTime(ms: Long): String {
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

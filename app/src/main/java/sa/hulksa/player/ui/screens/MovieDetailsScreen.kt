package sa.hulksa.player.ui.screens

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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
                    .height(if (isTv) 510.dp else 510.dp)
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
                            0f to Color.Black.copy(alpha = .25f),
                            .48f to Color.Black.copy(alpha = .28f),
                            1f to colors.background,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(colors.background.copy(alpha = .97f), Color.Black.copy(alpha = .55f), Color.Transparent),
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
                    BrandBadge(Modifier.size(if (isTv) 64.dp else 49.dp))
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
                            bottom = if (isTv) 34.dp else 22.dp,
                        ),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("فيلم", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = if (isTv) 42.sp else 26.sp,
                            lineHeight = if (isTv) 49.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            item.year?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                            item.rating?.takeIf(String::isNotBlank)?.let { InfoPill("★ $it") }
                            details?.duration?.takeIf(String::isNotBlank)?.let { InfoPill(it) }
                            item.containerExtension?.takeIf(String::isNotBlank)?.let { InfoPill(it.uppercase()) }
                        }
                        (details?.genre ?: item.genre)?.takeIf { !it.isNullOrBlank() }?.let { genre ->
                            Spacer(Modifier.height(8.dp))
                            Text(genre, color = colors.goldBright.copy(alpha = .9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val plot = details?.plot ?: item.plot
                        if (!plot.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = plot,
                                color = Color(0xFFD8D4C9),
                                fontSize = if (isTv) 14.sp else 12.sp,
                                lineHeight = if (isTv) 22.sp else 19.sp,
                                maxLines = if (isTv) 4 else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (progress != null && historyEntry != null) {
                            Spacer(Modifier.height(15.dp))
                            WatchProgress(
                                progress = progress,
                                label = "شاهدت ${(progress * 100).toInt()}%  •  المتابعة من ${detailsFormatTime(historyEntry.positionMs)}",
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FocusButton(
                                text = if (resumePosition != null) "▶ استكمال" else "▶ مشاهدة الفيلم",
                                onClick = onPlay,
                                compact = !isTv,
                                modifier = Modifier.weight(1f),
                            )
                            FocusButton(
                                text = if (isFavorite) "★ في قائمتي" else "+ قائمتي",
                                onClick = onToggleFavorite,
                                primary = false,
                                compact = !isTv,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
                            Spacer(Modifier.height(9.dp))
                            MovieDownloadProgress(download)
                        }
                        Spacer(Modifier.height(9.dp))
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

                    MoviePoster(
                        posterUrl = item.posterUrl,
                        title = item.name,
                        isTv = isTv,
                        modifier = Modifier.width(if (isTv) 178.dp else 126.dp),
                    )
                }

                if (isLoading) {
                    LoadingRing(
                        modifier = Modifier.align(Alignment.TopStart).padding(top = if (isTv) 30.dp else 18.dp, start = if (isTv) 112.dp else 78.dp),
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
                MovieInformation(
                    details = details,
                    modifier = Modifier.padding(
                        start = if (isTv) 38.dp else 18.dp,
                        end = if (isTv) 38.dp else 18.dp,
                        top = 8.dp,
                        bottom = 20.dp,
                    ),
                )
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
                    Spacer(Modifier.height(12.dp))
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
            .border(2.dp, colors.gold.copy(alpha = .45f), shape),
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = .46f))
            .border(1.dp, colors.line.copy(alpha = .65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
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
            Text("$percent%", color = colors.goldBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .16f))) {
            Box(Modifier.fillMaxWidth(download.progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.goldBright))
        }
        if (download.totalBytes > 0L) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${movieFormatBytes(download.bytesDownloaded)} / ${movieFormatBytes(download.totalBytes)}",
                color = colors.textMuted,
                fontSize = 10.sp,
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
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .18f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
        }
    }
}

@Composable
private fun MovieInformation(details: ContentDetails?, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = .88f))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(if (details?.cast.isNullOrBlank()) 16.dp else 20.dp),
    ) {
        Text("معلومات الفيلم", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        details?.releaseDate?.takeIf(String::isNotBlank)?.let { DetailLine("تاريخ العرض", it) }
        details?.director?.takeIf(String::isNotBlank)?.let { DetailLine("الاخراج", it) }
        details?.cast?.takeIf(String::isNotBlank)?.let { DetailLine("البطولة", it) }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = LocalHulkColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = colors.goldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
        Text(value, color = colors.textMuted, fontSize = 11.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
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
        safe >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.0f MB", safe / (1024.0 * 1024.0))
        safe >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", safe / 1024.0)
        else -> "$safe B"
    }
}

private fun detailsFormatTime(ms: Long): String {
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

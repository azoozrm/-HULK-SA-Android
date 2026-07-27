#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path,old,new,label,count=1):
 p=root/path; s=p.read_text(encoding='utf-8')
 if new in s:return
 if old not in s:raise SystemExit(f'missing {label}')
 p.write_text(s.replace(old,new,count),encoding='utf-8')

movie='app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt'
rep(movie,'import androidx.compose.runtime.Composable\n','import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.mutableStateMapOf\nimport androidx.compose.runtime.remember\n','movie optimistic imports')
rep(movie,'''    onToggleFavorite: () -> Unit,
    onOpenRelated: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current''','''    onToggleFavorite: () -> Unit,
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
    }''','movie related favorite wiring')
rep(movie,'''                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {''','''                        if (download != null && download.status != OfflineStatus.COMPLETED && download.status != OfflineStatus.FAILED) {
                            Spacer(Modifier.height(9.dp))
                            MovieDownloadProgress(download)
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {''','movie download progress placement')
rep(movie,'''                                isFavorite = isRelatedFavorite(related),
                                onClick = { onOpenRelated(related) },
                                modifier = Modifier.width(if (isTv) 142.dp else 112.dp),''','''                                isFavorite = relatedIsFavorite(related),
                                onClick = { onOpenRelated(related) },
                                modifier = Modifier.width(if (isTv) 142.dp else 112.dp),
                                onLongClick = { toggleRelatedFavorite(related) },''','movie related long press')
rep(movie,'''@Composable
private fun WatchProgress(progress: Float, label: String) {''','''@Composable
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
private fun WatchProgress(progress: Float, label: String) {''','movie download progress component')
rep(movie,'''private fun detailsFormatTime(ms: Long): String {''','''private fun movieFormatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.0f MB", safe / (1024.0 * 1024.0))
        safe >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", safe / 1024.0)
        else -> "$safe B"
    }
}

private fun detailsFormatTime(ms: Long): String {''','movie byte formatter')

series='app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt'
rep(series,'import androidx.compose.runtime.mutableIntStateOf\n','import androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateMapOf\n','series optimistic import')
rep(series,'''    onToggleFavorite: () -> Unit,
    onOpenRelated: (ContentItem) -> Unit,
) {
    val colors = LocalHulkColors.current''','''    onToggleFavorite: () -> Unit,
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
    }''','series related favorite wiring')
rep(series,'''                        isFavorite = isRelatedFavorite,
                        onOpen = onOpenRelated,
                        isTv = isTv,''','''                        isFavorite = relatedIsFavorite,
                        onToggleFavorite = toggleRelatedFavorite,
                        onOpen = onOpenRelated,
                        isTv = isTv,''','series related row wiring')
rep(series,'''private fun RelatedSeriesRow(
    items: List<ContentItem>,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    isTv: Boolean,
) {''','''private fun RelatedSeriesRow(
    items: List<ContentItem>,
    isFavorite: (ContentItem) -> Boolean,
    onToggleFavorite: (ContentItem) -> Unit,
    onOpen: (ContentItem) -> Unit,
    isTv: Boolean,
) {''','series row callback')
rep(series,'''                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (isTv) 142.dp else 112.dp),''','''                    onClick = { onOpen(item) },
                    modifier = Modifier.width(if (isTv) 142.dp else 112.dp),
                    onLongClick = { onToggleFavorite(item) },''','series related long press')

app='app/src/main/java/sa/hulksa/player/ui/HulkApp.kt'
rep(app,'''                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onOpenRelated = viewModel::open,''','''                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onToggleRelatedFavorite = { related ->
                                val wasFavorite = viewModel.isFavorite(related)
                                viewModel.toggleFavorite(related)
                                notify(if (wasFavorite) "تمت ازالة ${related.name} من المفضلة" else "تمت اضافة ${related.name} الى المفضلة")
                            },
                            onOpenRelated = viewModel::open,''','movie app related callback')
rep(app,'''                            onToggleFavorite = { viewModel.toggleFavorite(series) },
                            onOpenRelated = viewModel::open,''','''                            onToggleFavorite = { viewModel.toggleFavorite(series) },
                            onToggleRelatedFavorite = { related ->
                                val wasFavorite = viewModel.isFavorite(related)
                                viewModel.toggleFavorite(related)
                                notify(if (wasFavorite) "تمت ازالة ${related.name} من المفضلة" else "تمت اضافة ${related.name} الى المفضلة")
                            },
                            onOpenRelated = viewModel::open,''','series app related callback')
print('Prepared v0.9.3.17 details favorites and movie download progress')

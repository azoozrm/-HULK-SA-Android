#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "source")


def replace_once(relative: str, old: str, new: str) -> None:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(relative: str, old: str, new: str, minimum: int = 1) -> None:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0 and new in text:
        return
    if count < minimum:
        raise RuntimeError(f"Expected at least {minimum} replacements in {path}, found {count}")
    path.write_text(text.replace(old, new), encoding="utf-8")


# Downloader compile/cancellation safeguards.
replace_once(
    "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
    "import kotlinx.coroutines.isActive\n",
    "import kotlinx.coroutines.isActive\n",
)
replace_once(
    "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
    "jobs.values.count(Job::isActive)",
    "jobs.values.count { it.isActive }",
)
replace_once(
    "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
    '''            } catch (error: IOException) {
                if (networkConstraintMessage() != null) {''',
    '''            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (networkConstraintMessage() != null) {''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt",
    '''    private fun currentCoroutineContextBlockingCheck() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Download paused")
        calls.values.firstOrNull { it.isCanceled() }?.let { cancelled ->
            if (cancelled.isCanceled()) throw CancellationException("Download paused")
        }
    }''',
    '''    private fun currentCoroutineContextBlockingCheck() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Download paused")
    }''',
)

# Faster, complete polling and pause/resume actions in the ViewModel.
replace_once(
    "app/src/main/java/sa/hulksa/player/HulkViewModel.kt",
    '''                val hasActive = downloads.any {
                    it.status == OfflineStatus.QUEUED ||
                        it.status == OfflineStatus.DOWNLOADING ||
                        it.status == OfflineStatus.PAUSED
                }''',
    '''                val hasActive = downloads.any {
                    it.status == OfflineStatus.QUEUED ||
                        it.status == OfflineStatus.CHECKING ||
                        it.status == OfflineStatus.DOWNLOADING ||
                        it.status == OfflineStatus.PAUSED ||
                        it.status == OfflineStatus.WAITING_NETWORK ||
                        it.status == OfflineStatus.WAITING_STORAGE
                }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/HulkViewModel.kt",
    '''    fun retryDownload(item: OfflineDownload): String {
        val activeSession = session ?: return "سجل الدخول أولا لإعادة التحميل."
        downloadRepository.remove(item.downloadId)
        val request = repository.playback(
            activeSession,
            HistoryEntry(
                key = item.historyKey,
                title = item.title,
                posterUrl = item.posterUrl,
                streamKind = item.streamKind,
                streamId = item.streamId,
                extension = item.extension,
                isLive = false,
                positionMs = 0L,
                durationMs = 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return enqueueDownload(request, item.seriesTitle, item.season, item.episodeNumber)
    }''',
    '''    fun retryDownload(item: OfflineDownload): String = when (item.status) {
        OfflineStatus.COMPLETED -> "التحميل مكتمل وجاهز للتشغيل."
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> {
            mutableState.update { it.copy(downloads = downloadRepository.pause(item.downloadId)) }
            "تم إيقاف التحميل مؤقتا."
        }
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> {
            if (downloadRepository.resume(item.downloadId)) {
                mutableState.update { it.copy(downloads = downloadRepository.downloads()) }
                "جارٍ استكمال التحميل من آخر نقطة."
            } else {
                rebuildDownload(item)
            }
        }
        OfflineStatus.FAILED -> {
            if (downloadRepository.resume(item.downloadId)) {
                mutableState.update { it.copy(downloads = downloadRepository.downloads()) }
                "جارٍ إعادة المحاولة من آخر نقطة."
            } else {
                rebuildDownload(item)
            }
        }
    }

    private fun rebuildDownload(item: OfflineDownload): String {
        val activeSession = session ?: return "سجل الدخول أولا لإعادة التحميل."
        downloadRepository.remove(item.downloadId)
        val request = repository.playback(
            activeSession,
            HistoryEntry(
                key = item.historyKey,
                title = item.title,
                posterUrl = item.posterUrl,
                streamKind = item.streamKind,
                streamId = item.streamId,
                extension = item.extension,
                isLive = false,
                positionMs = 0L,
                durationMs = 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return enqueueDownload(request, item.seriesTitle, item.season, item.episodeNumber)
    }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/HulkViewModel.kt",
    '"بدأ تحميل ${result.item.title}."',
    '"بدأ فحص حجم ${result.item.title} والمساحة المتاحة."',
)

# Download page: complete state handling, clearer card, speed and ETA.
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '''    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED
    }''',
    '''    val active = downloads.count {
        it.status == OfflineStatus.QUEUED ||
            it.status == OfflineStatus.CHECKING ||
            it.status == OfflineStatus.DOWNLOADING ||
            it.status == OfflineStatus.PAUSED ||
            it.status == OfflineStatus.WAITING_NETWORK ||
            it.status == OfflineStatus.WAITING_STORAGE
    }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    "columns = GridCells.Adaptive(if (isTv) 340.dp else 285.dp)",
    "columns = GridCells.Adaptive(if (isTv) 390.dp else 300.dp)",
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    ".height(if (isTv) 184.dp else 164.dp)",
    ".height(if (isTv) 218.dp else 204.dp)",
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '''                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "إعادة المحاولة",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    else -> Text(
                        downloadStatusLabel(item.status),
                        color = colors.goldBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                    )
                }''',
    '''                when (item.status) {
                    OfflineStatus.COMPLETED -> FocusButton(
                        "تشغيل",
                        { onPlay(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    OfflineStatus.FAILED -> FocusButton(
                        "إعادة المحاولة",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    OfflineStatus.PAUSED,
                    OfflineStatus.WAITING_NETWORK,
                    OfflineStatus.WAITING_STORAGE,
                    -> FocusButton(
                        "استئناف",
                        { onRetry(item) },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    OfflineStatus.QUEUED,
                    OfflineStatus.CHECKING,
                    OfflineStatus.DOWNLOADING,
                    -> FocusButton(
                        "إيقاف مؤقت",
                        { onRetry(item) },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '''@Composable
private fun DownloadProgress(item: OfflineDownload) {
    val colors = LocalHulkColors.current
    val percent = (item.progress * 100).toInt()
    val label = when {
        item.status == OfflineStatus.COMPLETED -> formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))
        item.totalBytes > 0L -> "$percent%  •  ${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> downloadStatusLabel(item.status)
    }
    Text(label, color = colors.textMuted, fontSize = 8.sp, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) {
        val progress = when (item.status) {
            OfflineStatus.COMPLETED -> 1f
            else -> item.progress
        }
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
    }
}''',
    '''@Composable
private fun DownloadProgress(item: OfflineDownload) {
    val colors = LocalHulkColors.current
    val targetProgress = if (item.status == OfflineStatus.COMPLETED) 1f else item.progress
    val progress by animateFloatAsState(targetProgress, label = "downloadProgress")
    val percent = (targetProgress * 100).toInt()
    val sizeLine = when {
        item.status == OfflineStatus.COMPLETED ->
            "${formatBytes(item.totalBytes.coerceAtLeast(item.bytesDownloaded))}  •  ${item.storageLabel}"
        item.totalBytes > 0L ->
            "$percent%  •  ${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)}"
        item.bytesDownloaded > 0L -> formatBytes(item.bytesDownloaded)
        else -> downloadStatusLabel(item.status)
    }
    Text(sizeLine, color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
    Spacer(Modifier.height(3.dp))
    when {
        item.status == OfflineStatus.DOWNLOADING && item.bytesPerSecond > 0L -> Text(
            "${formatBytes(item.bytesPerSecond)}/ث  •  المتبقي ${formatEta(item.etaSeconds)}",
            color = colors.goldBright,
            fontSize = 9.sp,
            maxLines = 1,
        )
        !item.errorMessage.isNullOrBlank() -> Text(
            item.errorMessage,
            color = if (item.status == OfflineStatus.FAILED) Color(0xFFFF9B8E) else colors.textMuted,
            fontSize = 8.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            "${downloadStatusLabel(item.status)}  •  ${item.storageLabel}",
            color = colors.textMuted,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
    }
}''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt",
    '''private fun downloadStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "بانتظار بدء التحميل"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا بسبب الشبكة"
    OfflineStatus.COMPLETED -> "اكتمل التحميل"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun formatBytes(bytes: Long): String {''',
    '''private fun downloadStatusLabel(status: OfflineStatus): String = when (status) {
    OfflineStatus.QUEUED -> "في قائمة الانتظار"
    OfflineStatus.CHECKING -> "جاري فحص الحجم والمساحة"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "متوقف مؤقتا"
    OfflineStatus.WAITING_NETWORK -> "بانتظار عودة الشبكة"
    OfflineStatus.WAITING_STORAGE -> "بانتظار وحدة التخزين"
    OfflineStatus.COMPLETED -> "اكتمل وتم التحقق"
    OfflineStatus.FAILED -> "تعذر التحميل"
}

private fun formatEta(seconds: Long): String {
    if (seconds < 0L) return "يُحسب..."
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return when {
        minutes >= 60L -> "${minutes / 60L} س ${minutes % 60L} د"
        minutes > 0L -> "$minutes د $remainingSeconds ث"
        else -> "$remainingSeconds ث"
    }
}

private fun formatBytes(bytes: Long): String {''',
)

# Detail screens must understand every stable downloader state.
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/MovieDetailsScreen.kt",
    '''                        OfflineStatus.DOWNLOADING,
                        OfflineStatus.QUEUED,
                        OfflineStatus.PAUSED,
                        -> "↓ جاري التحميل"
                        OfflineStatus.FAILED -> "↻ إعادة التحميل"''',
    '''                        OfflineStatus.QUEUED,
                        OfflineStatus.CHECKING,
                        OfflineStatus.DOWNLOADING,
                        -> "↓ جاري التحميل"
                        OfflineStatus.PAUSED,
                        OfflineStatus.WAITING_NETWORK,
                        OfflineStatus.WAITING_STORAGE,
                        -> "⏸ بانتظار الاستكمال"
                        OfflineStatus.FAILED -> "↻ إعادة التحميل"''',
)
replace_once(
    "app/src/main/java/sa/hulksa/player/ui/screens/SeriesScreen.kt",
    '''                OfflineStatus.DOWNLOADING,
                OfflineStatus.QUEUED,
                OfflineStatus.PAUSED,
                -> "↓ جاري التحميل"
                OfflineStatus.FAILED -> "↻ إعادة التحميل"''',
    '''                OfflineStatus.QUEUED,
                OfflineStatus.CHECKING,
                OfflineStatus.DOWNLOADING,
                -> "↓ جاري التحميل"
                OfflineStatus.PAUSED,
                OfflineStatus.WAITING_NETWORK,
                OfflineStatus.WAITING_STORAGE,
                -> "⏸ بانتظار الاستكمال"
                OfflineStatus.FAILED -> "↻ إعادة التحميل"''',
)

print("HULK SA v0.6.3 source patch applied successfully")

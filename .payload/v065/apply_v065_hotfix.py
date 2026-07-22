#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "project")


def replace_once(relative: str, old: str, new: str) -> None:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/build.gradle.kts",
    'versionCode = 15\n        versionName = "0.6.5"',
    'versionCode = 16\n        versionName = "0.6.5.1"',
)

main = "app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"
replace_once(
    main,
    'if (settings.scheduleMode == DownloadScheduleMode.NIGHT) "الجدولة  02:00" else "الجدولة  الآن"',
    'if (settings.scheduleMode == DownloadScheduleMode.NIGHT) "الجدولة 02:00" else "الجدولة الآن"',
)
replace_once(
    main,
    '''            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 410.dp else 310.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = OfflineDownload::downloadId) { item ->
                    DownloadCard(item, isTv, onPlay, onDelete, onRetry, onCyclePriority)
                }
            }''',
    '''            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                horizontalAlignment = Alignment.Start,
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = OfflineDownload::downloadId) { item ->
                    DownloadCard(
                        item = item,
                        isTv = isTv,
                        onPlay = onPlay,
                        onDelete = onDelete,
                        onRetry = onRetry,
                        onCyclePriority = onCyclePriority,
                        modifier = Modifier
                            .widthIn(max = if (isTv) 720.dp else 520.dp)
                            .fillMaxWidth(),
                    )
                }
            }''',
)
replace_once(
    main,
    '''private fun DownloadCard(
    item: OfflineDownload,
    isTv: Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
) {''',
    '''private fun DownloadCard(
    item: OfflineDownload,
    isTv: Boolean,
    onPlay: (OfflineDownload) -> Unit,
    onDelete: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onCyclePriority: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
) {''',
)
replace_once(
    main,
    '''    Row(
        modifier = Modifier
            .fillMaxWidth()''',
    '''    Row(
        modifier = modifier
            .fillMaxWidth()''',
)
replace_once(
    main,
    '''            val episodeMeta = listOfNotNull(
                item.season?.let { "الموسم $it" },
                item.episodeNumber?.let { "الحلقة $it" },
            ).joinToString(" • ")''',
    '''            val episodeMeta = item.season?.let { "الموسم $it" }.orEmpty()''',
)

repo = "app/src/main/java/sa/hulksa/player/data/DownloadRepository.kt"
replace_once(
    repo,
    '''                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count''',
    '''                        if (totalBytes > 0L && downloaded >= totalBytes) break
                        val bytesToRead = if (totalBytes > 0L) {
                            minOf(buffer.size.toLong(), totalBytes - downloaded).toInt()
                        } else {
                            buffer.size
                        }
                        if (bytesToRead <= 0) break
                        val count = input.read(buffer, 0, bytesToRead)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count''',
)
replace_once(
    repo,
    '''            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (networkConstraintMessage() != null) {''',
    '''            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (finalizeCompletedFileAfterTransportError(downloadId)) return
                if (networkConstraintMessage() != null) {''',
)
replace_once(
    repo,
    '''    private fun executeDownloadCall(downloadId: Long, url: String, offset: Long, useRange: Boolean): Response {''',
    '''    private fun finalizeCompletedFileAfterTransportError(downloadId: Long): Boolean {
        val current = item(downloadId) ?: return false
        val expectedBytes = current.totalBytes.takeIf { it > 0L } ?: return false
        val target = storageTarget(current) ?: return false
        val fileName = current.fileName ?: return false
        val finalFile = File(target.directory, fileName)
        if (finalFile.exists() && finalFile.length() == expectedBytes) {
            markCompleted(downloadId, finalFile, expectedBytes, current.supportsRange ?: false)
            return true
        }
        val partFile = File(target.directory, "$fileName.part")
        if (!partFile.exists() || partFile.length() != expectedBytes) return false
        return runCatching {
            finalizePart(
                downloadId = downloadId,
                partFile = partFile,
                finalFile = finalFile,
                expectedBytes = expectedBytes,
                supportsRange = current.supportsRange ?: false,
            )
            true
        }.getOrDefault(false)
    }

    private fun executeDownloadCall(downloadId: Long, url: String, offset: Long, useRange: Boolean): Response {''',
)

print("HULK SA v0.6.5.1 download hotfix applied")

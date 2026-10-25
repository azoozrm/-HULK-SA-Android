package sa.hulksa.player.data

import java.io.File
import java.security.MessageDigest
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

/**
 * Deterministic, path-safe identity for a download file. The history key keeps Movie and Series
 * entries distinct even when their sanitized title, numeric id and extension are otherwise equal.
 */
internal fun buildDownloadFileName(
    historyKey: String,
    title: String,
    streamId: Int,
    extension: String,
): String {
    val safeTitle = title
        .replace(Regex("[^\\p{L}\\p{N}._ -]+"), "")
        .trim()
        .replace(Regex("\\s+"), "_")
        .take(96)
        .ifBlank { "hulk_content" }
    val identity = historyKey.trim().ifBlank { "content:$streamId" }
    val identityDigest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        .take(12)
    val identityKind = identity
        .substringBefore(':', missingDelimiterValue = identity)
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(16)
        .ifBlank { "content" }
    return "${safeTitle}_${identityKind}_${streamId}_$identityDigest.$extension"
}

internal fun downloadPhysicalPathKeys(item: OfflineDownload): Set<String> {
    val paths = linkedSetOf<String>()
    val storagePath = item.storagePath?.trim().orEmpty()
    val fileName = item.fileName?.trim().orEmpty()
    if (storagePath.isNotEmpty() && fileName.isNotEmpty()) {
        val finalPath = File(storagePath, fileName).absolutePath
        paths += finalPath
        paths += "$finalPath.part"
    }
    downloadFilePathFromUri(item.localUri)?.let { localPath ->
        val finalPath = File(localPath).absolutePath
        paths += finalPath
        paths += "$finalPath.part"
    }
    return paths
}

internal fun downloadFilePathFromUri(uri: String?): String? {
    val trimmed = uri?.trim().orEmpty()
    if (!trimmed.startsWith("file:", ignoreCase = true)) return null
    return runCatching { java.net.URI(trimmed).path }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
}

internal fun sharedDownloadIdentityIds(records: List<OfflineDownload>): Set<Long> {
    val ownersByPath = mutableMapOf<String, MutableSet<Long>>()
    records.forEach { item ->
        downloadPhysicalPathKeys(item).forEach { path ->
            ownersByPath.getOrPut(path) { linkedSetOf() }.add(item.downloadId)
        }
    }
    return ownersByPath.values
        .asSequence()
        .filter { it.size > 1 }
        .flatten()
        .toSet()
}

/**
 * A legacy index can contain distinct logical downloads that were stored under one physical path.
 * Ownership of that original file cannot be guessed, so every ambiguous record is moved to its own
 * deterministic future path and left in the existing retryable FAILED state. The original file is
 * never renamed, overwritten or deleted here; no content is claimed as recovered.
 */
internal fun reconcileSharedDownloadPaths(
    records: List<OfflineDownload>,
    localUriForFile: (File) -> String,
): List<OfflineDownload> {
    val ambiguousIds = sharedDownloadIdentityIds(records)
    if (ambiguousIds.isEmpty()) return records
    return records.map { item ->
        if (item.downloadId !in ambiguousIds) {
            item
        } else {
            val fileName = buildDownloadFileName(
                historyKey = item.historyKey,
                title = item.title,
                streamId = item.streamId,
                extension = item.extension,
            )
            val storagePath = item.storagePath?.trim()?.takeIf(String::isNotEmpty)
            item.copy(
                fileName = fileName,
                localUri = storagePath?.let { localUriForFile(File(it, fileName)) },
                supportsRange = null,
                status = OfflineStatus.FAILED,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                bytesPerSecond = 0L,
                etaSeconds = -1L,
                retryCount = 0,
                integrityVerified = false,
                errorMessage = "تم فصل مسار تحميل قديم مشترك بأمان. أعد المحاولة لتنزيل نسخة مستقلة.",
            )
        }
    }
}

internal fun downloadPathIsOwnedByAnotherRecord(
    records: List<OfflineDownload>,
    item: OfflineDownload,
    path: File,
): Boolean {
    val normalized = path.absolutePath
    return records.any { other ->
        other.downloadId != item.downloadId && normalized in downloadPhysicalPathKeys(other)
    }
}

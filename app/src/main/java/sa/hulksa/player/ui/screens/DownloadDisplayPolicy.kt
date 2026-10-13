package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.ui.components.stripTerminalDecoration

internal data class DownloadDisplayIdentity(
    val title: String,
    val metadata: String,
)

/**
 * Canonical display projection for one download record: the work name appears exactly once.
 * Episode records keep the persisted `seriesTitle` as the primary title and the season/episode
 * identity on its own metadata line; the raw composite request title is never appended.
 */
internal fun downloadDisplayIdentity(item: OfflineDownload): DownloadDisplayIdentity {
    val seriesTitle = item.seriesTitle?.takeIf { it.isNotBlank() }
    val title = seriesTitle ?: item.title
    val metadata = if (item.streamKind == "movie") {
        "فلم"
    } else {
        listOfNotNull(
            item.season?.let { "الموسم $it" },
            item.episodeNumber?.let { "الحلقة $it" },
        ).joinToString("  •  ")
    }
    return DownloadDisplayIdentity(title = title, metadata = metadata)
}

/**
 * Display rule for app-authored download feedback: strip terminal decoration and normalize only
 * alef hamza/madda to `ا`. `ئ/ؤ/ء` are preserved; operation outcomes and stored records are not
 * rewritten.
 */
internal fun downloadNoticeDisplayText(raw: String): String = scopedNoticeDisplayText(raw)

/**
 * Shared display normalization for app-authored in-app action notices outside stored content
 * names: terminal decoration is stripped and only alef hamza/madda becomes `ا`.
 */
internal fun scopedNoticeDisplayText(raw: String): String =
    stripTerminalDecoration(raw)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')

/**
 * The wide horizontal row is the accepted TV composition. A non-TV window uses it only when the
 * measured usable width can hold the artwork, a readable title column, the telemetry column and
 * the measured primary action without clipping.
 */
internal fun downloadListUsesHorizontalRow(
    isTv: Boolean,
    availableWidthDp: Float,
    primaryActionWidthDp: Float,
): Boolean {
    if (isTv) return true
    return availableWidthDp >= DOWNLOAD_HORIZONTAL_ROW_FIXED_WIDTH_DP + primaryActionWidthDp
}

/** Fixed columns of the horizontal row excluding the measured primary action width. */
internal const val DOWNLOAD_HORIZONTAL_ROW_FIXED_WIDTH_DP = 703f

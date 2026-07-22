package sa.hulksa.player.model

data class Credentials(
    val username: String,
    val password: String,
)

data class PortalConfig(
    val baseUrl: String,
    val source: Source,
) {
    enum class Source { REMOTE, COMPILED }
}

data class AccountInfo(
    val username: String,
    val status: String,
    val expiresAtEpochSeconds: Long?,
    val activeConnections: Int,
    val maxConnections: Int,
    val isTrial: Boolean,
)

enum class ContentType {
    LIVE,
    MOVIE,
    SERIES,
}

data class Category(
    val id: String,
    val name: String,
    val type: ContentType,
)

data class ContentItem(
    val id: Int,
    val name: String,
    val categoryId: String,
    val type: ContentType,
    val posterUrl: String?,
    val rating: String?,
    val year: String?,
    val containerExtension: String?,
    val nowPlaying: String? = null,
    val addedAtEpochSeconds: Long? = null,
    val plot: String? = null,
    val genre: String? = null,
    val backdropUrl: String? = null,
)

data class Episode(
    val id: Int,
    val title: String,
    val season: Int,
    val episodeNumber: Int,
    val containerExtension: String,
    val posterUrl: String?,
    val duration: String?,
)

data class PlaybackRequest(
    val title: String,
    val posterUrl: String?,
    val candidates: List<String>,
    val isLive: Boolean,
    val historyKey: String,
    val streamKind: String,
    val streamId: Int,
    val extension: String,
    val resumePositionMs: Long = 0L,
)

data class HistoryEntry(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val streamKind: String,
    val streamId: Int,
    val extension: String,
    val isLive: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
)

enum class OfflineStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class OfflineDownload(
    val downloadId: Long,
    val historyKey: String,
    val title: String,
    val posterUrl: String?,
    val streamKind: String,
    val streamId: Int,
    val extension: String,
    val seriesTitle: String? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    val status: OfflineStatus = OfflineStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val localUri: String? = null,
    val failureReason: Int? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

data class ContentDetails(
    val plot: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val releaseDate: String? = null,
    val backdropUrl: String? = null,
)

data class SeriesBundle(
    val details: ContentDetails,
    val episodes: List<Episode>,
)

data class AuthenticatedSession(
    val portal: PortalConfig,
    val credentials: Credentials,
    val account: AccountInfo,
)

data class Catalog(
    val categories: List<Category>,
    val items: List<ContentItem>,
)

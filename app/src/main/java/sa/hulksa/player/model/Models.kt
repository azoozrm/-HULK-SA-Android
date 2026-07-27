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
    val introEndMs: Long? = null,
    val creditsStartMs: Long? = null,
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
    CHECKING,
    DOWNLOADING,
    PAUSED,
    WAITING_SCHEDULE,
    WAITING_NETWORK,
    WAITING_STORAGE,
    COMPLETED,
    FAILED,
}

enum class DownloadScheduleMode {
    NOW,
    NIGHT,
}

data class DownloadSettings(
    val wifiOnly: Boolean = false,
    val scheduleMode: DownloadScheduleMode = DownloadScheduleMode.NOW,
    val concurrentDownloads: Int = 2,
)

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
    val sourceCandidates: List<String> = emptyList(),
    val fileName: String? = null,
    val storagePath: String? = null,
    val storageLabel: String = "التخزين الداخلي",
    val supportsRange: Boolean? = null,
    val status: OfflineStatus = OfflineStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val localUri: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val integrityVerified: Boolean = false,
    val priority: Int = 0,
    val queuePosition: Int = 0,
    val scheduledAtEpochMs: Long = 0L,
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

enum class CapabilityStatus {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED,
    UNSTABLE,
}

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

data class DiagnosticEndpoint(
    val name: String,
    val kind: String,
    val success: Boolean,
    val latencyMs: Long,
    val httpCode: Int?,
    val itemCount: Int?,
    val details: String,
)

data class CapabilityFinding(
    val id: String,
    val title: String,
    val status: CapabilityStatus,
    val details: String,
    val evidence: String,
)

data class DiagnosticIssue(
    val id: String,
    val title: String,
    val severity: DiagnosticSeverity,
    val details: String,
    val action: String,
)

data class FeatureRecommendation(
    val title: String,
    val readiness: String,
    val priority: Int,
    val reason: String,
)

data class ServerDiagnosticsReport(
    val generatedAtEpochMs: Long,
    val durationMs: Long,
    val portalHost: String,
    val portalScheme: String,
    val serverTimezone: String?,
    val serverProtocol: String?,
    val serverReportedHost: String?,
    val overallScore: Int,
    val overallStatus: String,
    val averageApiLatencyMs: Long,
    val bestSampleThroughputMbps: Double,
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val categoryCount: Int,
    val availableStorageBytes: Long,
    val networkSummary: String,
    val deviceSummary: String,
    val endpoints: List<DiagnosticEndpoint>,
    val capabilities: List<CapabilityFinding>,
    val issues: List<DiagnosticIssue>,
    val recommendations: List<FeatureRecommendation>,
)

data class DiagnosticsState(
    val isRunning: Boolean = false,
    val progress: Int = 0,
    val stage: String = "لم يبدأ الفحص",
    val report: ServerDiagnosticsReport? = null,
    val errorMessage: String? = null,
)

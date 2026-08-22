package sa.hulksa.player.tv

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind

internal const val TV_PROGRAM_PROVIDER_PREFIX = "hulk:v230:"
private const val COMPLETED_RATIO = 0.92
private const val DEFAULT_MAX_PROGRAMS = 20
private const val FIRST_PROGRESS_SYNC_MS = 5_000L
private const val MEANINGFUL_PROGRESS_MS = 12_000L
private const val PROFILE_SCOPE_TOKEN_BYTES = 12
private val PROFILE_SCOPE_TOKEN_PATTERN = Regex("^[0-9a-f]{24}$")

data class TvProfileScope(
    val accountId: String,
    val profileId: String,
    val profileKind: ProfileKind,
) {
    /** Opaque, deterministic scope used in provider IDs and internal program deep links. */
    val providerScopeId: String = tvProfileProviderScopeId(accountId, profileId)
}

internal fun tvProfileProviderScopeId(accountId: String, profileId: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(
        "${accountId.trim()}\u0000${profileId.trim()}".toByteArray(StandardCharsets.UTF_8),
    )
    val hex = "0123456789abcdef"
    return buildString(PROFILE_SCOPE_TOKEN_BYTES * 2) {
        digest.take(PROFILE_SCOPE_TOKEN_BYTES).forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}

internal fun isValidTvProfileScopeId(value: String?): Boolean =
    value != null && PROFILE_SCOPE_TOKEN_PATTERN.matches(value)

enum class TvContinueWatchingType {
    MOVIE,
    EPISODE,
}

enum class TvProgramArtworkAspectRatio {
    LANDSCAPE_16_9,
}

data class TvContinueWatchingItem(
    val scope: TvProfileScope,
    val providerId: String,
    val type: TvContinueWatchingType,
    val title: String,
    val episodeTitle: String?,
    val landscapeImageUrl: String?,
    val artworkAspectRatio: TvProgramArtworkAspectRatio,
    val contentId: Int,
    val seriesId: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    val deepLinkUri: String,
    val weight: Int,
)

object TvContinueWatchingMapper {
    fun map(
        scope: TvProfileScope,
        history: List<HistoryEntry>,
        verifiedKidsContentKeys: Set<String>,
        suppressedProviderIds: Set<String> = emptySet(),
        landscapeArtworkByContentKey: Map<String, String> = emptyMap(),
        maxPrograms: Int = DEFAULT_MAX_PROGRAMS,
    ): List<TvContinueWatchingItem> {
        if (scope.accountId.isBlank() || scope.profileId.isBlank() || maxPrograms <= 0) {
            return emptyList()
        }

        return history.asSequence()
            .filter(::isTvContinueWatchingEligible)
            .mapNotNull { entry ->
                entry.toCandidate(
                    scope = scope,
                    verifiedKidsContentKeys = verifiedKidsContentKeys,
                    landscapeArtworkByContentKey = landscapeArtworkByContentKey,
                )
            }
            .filterNot { it.providerId in suppressedProviderIds }
            .sortedByDescending(TvContinueWatchingItem::updatedAtEpochMs)
            .distinctBy(TvContinueWatchingItem::providerId)
            .take(maxPrograms.coerceAtMost(DEFAULT_MAX_PROGRAMS))
            .toList()
            .mapIndexed { index, item -> item.copy(weight = DEFAULT_MAX_PROGRAMS - index) }
    }
}

internal fun isTvContinueWatchingEligible(entry: HistoryEntry): Boolean =
    !entry.isLive &&
        entry.positionMs > 0L &&
        entry.durationMs > 0L &&
        entry.positionMs < entry.durationMs &&
        entry.positionMs.toDouble() / entry.durationMs < COMPLETED_RATIO

internal fun shouldSyncTvProgress(
    lastSyncedPositionMs: Long,
    currentPositionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L || currentPositionMs <= 0L) return false
    val lastSyncedCompleted = lastSyncedPositionMs > 0L &&
        lastSyncedPositionMs.toDouble() / durationMs >= COMPLETED_RATIO
    val currentCompleted = currentPositionMs.toDouble() / durationMs >= COMPLETED_RATIO
    if (currentCompleted) return !lastSyncedCompleted
    if (currentPositionMs >= durationMs) return false
    if (lastSyncedPositionMs < FIRST_PROGRESS_SYNC_MS) {
        return currentPositionMs >= FIRST_PROGRESS_SYNC_MS
    }
    return abs(currentPositionMs - lastSyncedPositionMs) >= MEANINGFUL_PROGRESS_MS
}

private fun HistoryEntry.toCandidate(
    scope: TvProfileScope,
    verifiedKidsContentKeys: Set<String>,
    landscapeArtworkByContentKey: Map<String, String>,
): TvContinueWatchingItem? {
    return when (streamKind.trim().lowercase()) {
        "movie" -> {
            if (streamId <= 0 || key != "MOVIE:$streamId" || title.isBlank()) return null
            if (
                scope.profileKind == ProfileKind.KIDS &&
                "MOVIE:$streamId" !in verifiedKidsContentKeys
            ) return null
            val providerId = "${TV_PROGRAM_PROVIDER_PREFIX}${scope.providerScopeId}:movie:$streamId"
            TvContinueWatchingItem(
                scope = scope,
                providerId = providerId,
                type = TvContinueWatchingType.MOVIE,
                title = title,
                episodeTitle = null,
                landscapeImageUrl = landscapeArtworkByContentKey["MOVIE:$streamId"]
                    ?.takeIf(String::isNotBlank)
                    ?: posterUrl,
                artworkAspectRatio = TvProgramArtworkAspectRatio.LANDSCAPE_16_9,
                contentId = streamId,
                seriesId = null,
                seasonNumber = null,
                episodeNumber = null,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAtEpochMs = updatedAtEpochMs,
                deepLinkUri = TvDeepLinkRouter.uri(
                    TvDeepLinkTarget.Movie(
                        movieId = streamId,
                        resumePlayback = true,
                        profileScopeId = scope.providerScopeId,
                    ),
                ),
                weight = 0,
            )
        }
        "series" -> {
            val parentSeriesId = parentContentId?.takeIf { it > 0 } ?: return null
            if (streamId <= 0 || key != "SERIES:$streamId" || title.isBlank()) return null
            if (
                scope.profileKind == ProfileKind.KIDS &&
                "SERIES:$parentSeriesId" !in verifiedKidsContentKeys
            ) return null
            val providerId =
                "${TV_PROGRAM_PROVIDER_PREFIX}${scope.providerScopeId}:episode:$parentSeriesId:$streamId"
            TvContinueWatchingItem(
                scope = scope,
                providerId = providerId,
                type = TvContinueWatchingType.EPISODE,
                title = seriesTitle?.takeIf { it.isNotBlank() } ?: title,
                episodeTitle = episodeTitle?.takeIf { it.isNotBlank() } ?: title,
                landscapeImageUrl = landscapeArtworkByContentKey["SERIES:$parentSeriesId"]
                    ?.takeIf(String::isNotBlank)
                    ?: posterUrl,
                artworkAspectRatio = TvProgramArtworkAspectRatio.LANDSCAPE_16_9,
                contentId = streamId,
                seriesId = parentSeriesId,
                seasonNumber = season,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAtEpochMs = updatedAtEpochMs,
                deepLinkUri = TvDeepLinkRouter.uri(
                    TvDeepLinkTarget.Episode(
                        seriesId = parentSeriesId,
                        episodeId = streamId,
                        resumePlayback = true,
                        profileScopeId = scope.providerScopeId,
                    ),
                ),
                weight = 0,
            )
        }
        else -> null
    }
}

data class ExistingTvProgram(
    val id: Long,
    val providerId: String,
)

data class TvProgramUpsert(
    val existingId: Long?,
    val item: TvContinueWatchingItem,
)

data class TvProgramSyncPlan(
    val upserts: List<TvProgramUpsert>,
    val deleteIds: Set<Long>,
)

enum class TvProfilePublicationPhase {
    CLEAR,
    PUBLISH,
}

/** Pure fail-closed contract used when a session/profile changes around a provider sync. */
internal fun planTvProfilePublication(
    previouslyPublishedScopeId: String?,
    activeScopeId: String?,
    hasSession: Boolean,
    profileResolved: Boolean,
    kidsVerificationRequired: Boolean,
    kidsVerified: Boolean,
): List<TvProfilePublicationPhase> {
    if (
        !hasSession ||
        !profileResolved ||
        activeScopeId == null ||
        !isValidTvProfileScopeId(activeScopeId) ||
        (kidsVerificationRequired && !kidsVerified)
    ) {
        return listOf(TvProfilePublicationPhase.CLEAR)
    }
    return if (
        previouslyPublishedScopeId != null &&
        previouslyPublishedScopeId != activeScopeId
    ) {
        listOf(TvProfilePublicationPhase.CLEAR, TvProfilePublicationPhase.PUBLISH)
    } else {
        listOf(TvProfilePublicationPhase.PUBLISH)
    }
}

/** Pure reconciliation contract used by both the app channel and Watch Next. */
internal fun planTvProgramSync(
    existing: List<ExistingTvProgram>,
    desired: List<TvContinueWatchingItem>,
): TvProgramSyncPlan {
    val desiredByProviderId = desired.distinctBy(TvContinueWatchingItem::providerId)
    val existingByProviderId = existing
        .filter { it.id >= 0L }
        .groupBy(ExistingTvProgram::providerId)
    val desiredIds = desiredByProviderId.mapTo(linkedSetOf(), TvContinueWatchingItem::providerId)
    val deleteIds = linkedSetOf<Long>()

    existingByProviderId.forEach { (providerId, programs) ->
        if (providerId !in desiredIds) {
            programs.mapTo(deleteIds, ExistingTvProgram::id)
        } else {
            programs.sortedBy(ExistingTvProgram::id).drop(1).mapTo(deleteIds, ExistingTvProgram::id)
        }
    }

    val upserts = desiredByProviderId.map { item ->
        val existingId = existingByProviderId[item.providerId]
            .orEmpty()
            .sortedBy(ExistingTvProgram::id)
            .firstOrNull()
            ?.id
        TvProgramUpsert(existingId = existingId, item = item)
    }
    return TvProgramSyncPlan(upserts = upserts, deleteIds = deleteIds)
}

internal fun isTvPlatformSupported(
    isTelevisionDevice: Boolean,
    sdkInt: Int,
    hasTvProvider: Boolean,
): Boolean = isTelevisionDevice && sdkInt >= 26 && hasTvProvider

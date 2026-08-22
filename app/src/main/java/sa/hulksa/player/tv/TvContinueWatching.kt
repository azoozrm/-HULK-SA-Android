package sa.hulksa.player.tv

import kotlin.math.abs
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind

internal const val TV_PROGRAM_PROVIDER_PREFIX = "hulk:v230:"
private const val COMPLETED_RATIO = 0.92
private const val DEFAULT_MAX_PROGRAMS = 20
private const val MEANINGFUL_PROGRESS_MS = 60_000L
private const val PROGRESS_BUCKETS = 20

data class TvProfileScope(
    val accountId: String,
    val profileId: String,
    val profileKind: ProfileKind,
)

enum class TvContinueWatchingType {
    MOVIE,
    EPISODE,
}

data class TvContinueWatchingItem(
    val scope: TvProfileScope,
    val providerId: String,
    val type: TvContinueWatchingType,
    val title: String,
    val episodeTitle: String?,
    val posterUrl: String?,
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
        maxPrograms: Int = DEFAULT_MAX_PROGRAMS,
    ): List<TvContinueWatchingItem> {
        if (scope.accountId.isBlank() || scope.profileId.isBlank() || maxPrograms <= 0) {
            return emptyList()
        }

        return history.asSequence()
            .filter(::isTvContinueWatchingEligible)
            .mapNotNull { entry -> entry.toCandidate(scope, verifiedKidsContentKeys) }
            .filterNot { it.providerId in suppressedProviderIds }
            .sortedWith(
                compareBy<TvContinueWatchingItem> {
                    if (it.type == TvContinueWatchingType.MOVIE) 0 else 1
                }.thenByDescending(TvContinueWatchingItem::updatedAtEpochMs),
            )
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
    previousPositionMs: Long,
    currentPositionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L || currentPositionMs <= 0L) return false
    val previousEligible = previousPositionMs > 0L &&
        previousPositionMs < durationMs &&
        previousPositionMs.toDouble() / durationMs < COMPLETED_RATIO
    val currentEligible = currentPositionMs < durationMs &&
        currentPositionMs.toDouble() / durationMs < COMPLETED_RATIO
    if (previousEligible != currentEligible) return true
    if (!currentEligible) return false
    if (abs(currentPositionMs - previousPositionMs) >= MEANINGFUL_PROGRESS_MS) return true

    fun bucket(positionMs: Long): Int =
        ((positionMs.coerceAtLeast(0L).toDouble() / durationMs) * PROGRESS_BUCKETS).toInt()
    return bucket(previousPositionMs) != bucket(currentPositionMs)
}

private fun HistoryEntry.toCandidate(
    scope: TvProfileScope,
    verifiedKidsContentKeys: Set<String>,
): TvContinueWatchingItem? = when (streamKind.trim().lowercase()) {
    "movie" -> {
        if (streamId <= 0 || key != "MOVIE:$streamId" || title.isBlank()) return null
        if (
            scope.profileKind == ProfileKind.KIDS &&
            "MOVIE:$streamId" !in verifiedKidsContentKeys
        ) return null
        val providerId = "${TV_PROGRAM_PROVIDER_PREFIX}movie:$streamId"
        TvContinueWatchingItem(
            scope = scope,
            providerId = providerId,
            type = TvContinueWatchingType.MOVIE,
            title = title,
            episodeTitle = null,
            posterUrl = posterUrl,
            contentId = streamId,
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deepLinkUri = TvDeepLinkRouter.uri(
                TvDeepLinkTarget.Movie(streamId, resumePlayback = true),
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
        val providerId = "${TV_PROGRAM_PROVIDER_PREFIX}episode:$parentSeriesId:$streamId"
        TvContinueWatchingItem(
            scope = scope,
            providerId = providerId,
            type = TvContinueWatchingType.EPISODE,
            title = seriesTitle?.takeIf { it.isNotBlank() } ?: title,
            episodeTitle = episodeTitle?.takeIf { it.isNotBlank() } ?: title,
            posterUrl = posterUrl,
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
                ),
            ),
            weight = 0,
        )
    }
    else -> null
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

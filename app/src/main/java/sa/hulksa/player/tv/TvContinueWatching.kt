package sa.hulksa.player.tv

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.security.isCredentialBearingIptvUrl

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
    val description: String,
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

internal data class TvEpisodeProgramMetadata(
    val seriesId: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
)

internal fun TvContinueWatchingItem.officialEpisodeMetadata(): TvEpisodeProgramMetadata? {
    if (type != TvContinueWatchingType.EPISODE) return null
    val stableSeriesId = seriesId?.takeIf { it > 0 } ?: return null
    return TvEpisodeProgramMetadata(
        seriesId = stableSeriesId.toString(),
        seasonNumber = seasonNumber?.takeIf { it > 0 },
        episodeNumber = episodeNumber?.takeIf { it > 0 },
        episodeTitle = episodeTitle?.takeIf(String::isNotBlank),
    )
}

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
                description = formatTvContinueWatchingDescription(
                    type = TvContinueWatchingType.MOVIE,
                    positionMs = positionMs,
                    durationMs = durationMs,
                ),
                landscapeImageUrl = selectTvProgramArtwork(
                    landscapeUrl = landscapeArtworkByContentKey["MOVIE:$streamId"],
                    posterUrl = posterUrl,
                ),
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
            val programTitle = tvSeriesProgramTitle()
            val validSeasonNumber = season?.takeIf { it > 0 }
            val validEpisodeNumber = episodeNumber?.takeIf { it > 0 }
            TvContinueWatchingItem(
                scope = scope,
                providerId = providerId,
                type = TvContinueWatchingType.EPISODE,
                title = programTitle,
                episodeTitle = sanitizeTvEpisodeTitle(
                    seriesTitle = programTitle,
                    rawEpisodeTitle = episodeTitle,
                ),
                description = formatTvContinueWatchingDescription(
                    type = TvContinueWatchingType.EPISODE,
                    positionMs = positionMs,
                    durationMs = durationMs,
                ),
                landscapeImageUrl = selectTvProgramArtwork(
                    landscapeUrl = landscapeArtworkByContentKey["SERIES:$parentSeriesId"],
                    posterUrl = posterUrl,
                ),
                artworkAspectRatio = TvProgramArtworkAspectRatio.LANDSCAPE_16_9,
                contentId = streamId,
                seriesId = parentSeriesId,
                seasonNumber = validSeasonNumber,
                episodeNumber = validEpisodeNumber,
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

internal fun formatTvContinueWatchingDescription(
    type: TvContinueWatchingType,
    positionMs: Long,
    durationMs: Long,
): String {
    val parts = mutableListOf(TV_CONTINUE_WATCHING_LABEL)
    if (type == TvContinueWatchingType.MOVIE) parts += "فيلم"
    parts += "تم الوصول إلى ${formatTvPlaybackTime(positionMs, roundUp = false)}"
    val remaining = formatTvPlaybackTime(
        valueMs = (durationMs - positionMs).coerceAtLeast(0L),
        roundUp = true,
    )
    parts += "المتبقي $remaining"
    return parts.joinToString(" • ")
}

private fun HistoryEntry.tvSeriesProgramTitle(): String {
    seriesTitle?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    return title.substringBefore(" · ").trim().takeIf(String::isNotEmpty) ?: title.trim()
}

internal fun sanitizeTvEpisodeTitle(
    seriesTitle: String,
    rawEpisodeTitle: String?,
): String? {
    var candidate = rawEpisodeTitle?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val cleanSeriesTitle = seriesTitle.trim()
    if (candidate.equals(cleanSeriesTitle, ignoreCase = true)) return null
    if (
        cleanSeriesTitle.isNotEmpty() &&
        candidate.startsWith(cleanSeriesTitle, ignoreCase = true)
    ) {
        val suffix = candidate.substring(cleanSeriesTitle.length)
        if (suffix.isEmpty() || suffix.first().isTvMetadataSeparator()) {
            candidate = suffix.trimTvMetadataSeparators()
        }
    }
    candidate = LATIN_EPISODE_PREFIX.replaceFirst(candidate, "").trimTvMetadataSeparators()
    candidate = ARABIC_EPISODE_PREFIX.replaceFirst(candidate, "").trimTvMetadataSeparators()
    if (candidate.isEmpty() || candidate.equals(cleanSeriesTitle, ignoreCase = true)) return null
    val compact = candidate.lowercase(Locale.ROOT).filterNot { it.isTvMetadataSeparator() }
    return candidate.takeUnless {
        compact.matches(NUMBERING_ONLY_PATTERN) || compact.matches(ARABIC_NUMBERING_ONLY_PATTERN)
    }
}

private fun Char.isTvMetadataSeparator(): Boolean =
    isWhitespace() || this in "-–—·•|:._/\\"

private fun String.trimTvMetadataSeparators(): String = trim { it.isTvMetadataSeparator() }

private val LATIN_EPISODE_PREFIX = Regex(
    """(?i)^(?:s(?:eason)?\s*\d+\s*[.\-_:]?\s*e(?:pisode)?\s*\d+|(?:episode|ep|e)\s*\d+|s(?:eason)?\s*\d+)""",
)
private val ARABIC_EPISODE_PREFIX = Regex(
    """^(?:(?:الموسم\s*\d+\s*[-–—·•|:]?\s*)?الحلقة\s*\d+|الموسم\s*\d+)""",
)
private val NUMBERING_ONLY_PATTERN = Regex(
    """(?:s(?:eason)?\d+e(?:pisode)?\d+|(?:episode|ep|e)\d+|(?:season|s)\d+|\d+)""",
)
private val ARABIC_NUMBERING_ONLY_PATTERN = Regex(
    """(?:الموسم\d+(?:الحلقة\d+)?|الحلقة\d+)""",
)

private fun formatTvPlaybackTime(valueMs: Long, roundUp: Boolean): String {
    if (valueMs <= 0L) return "0د"
    val minuteMs = 60_000L
    val totalMinutes = if (roundUp) {
        valueMs / minuteMs + if (valueMs % minuteMs == 0L) 0L else 1L
    } else {
        valueMs / minuteMs
    }
    if (totalMinutes <= 0L) return "أقل من دقيقة"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours <= 0L -> "${minutes}د"
        minutes <= 0L -> "${hours}س"
        else -> "${hours}س ${minutes}د"
    }
}

internal fun selectTvProgramArtwork(
    landscapeUrl: String?,
    posterUrl: String?,
): String? = sequenceOf(landscapeUrl, posterUrl)
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .firstOrNull(::isSafeTvProgramArtworkUrl)

internal fun isSafeTvProgramArtworkUrl(rawUrl: String): Boolean = runCatching {
    val uri = URI(rawUrl)
    val schemeAllowed =
        uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
    val sensitiveQuery = uri.rawQuery.orEmpty().lowercase(Locale.ROOT).let { query ->
        listOf("access_code=", "token=").any(query::contains)
    }
    schemeAllowed &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        !sensitiveQuery &&
        !isCredentialBearingIptvUrl(rawUrl)
}.getOrDefault(false)

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

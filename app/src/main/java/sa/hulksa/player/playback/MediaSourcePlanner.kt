package sa.hulksa.player.playback

import sa.hulksa.player.model.PlaybackRequest
import java.net.URI
import java.util.Locale

internal const val MAX_AUTOMATIC_SOURCE_CANDIDATES = 4

internal enum class PlannedSourceType {
    HLS,
    MPEG_TS,
    PROGRESSIVE,
    LOCAL,
}

internal data class PlannedMediaSource(
    val candidateIndex: Int,
    val uri: String,
    val type: PlannedSourceType,
    val containerMimeType: String?,
)

internal data class MediaSourcePlan(
    val candidates: List<PlannedMediaSource>,
    val rejectedCandidateIndices: Set<Int>,
) {
    val firstCandidateIndex: Int?
        get() = candidates.firstOrNull()?.candidateIndex

    fun candidate(index: Int): PlannedMediaSource? =
        candidates.firstOrNull { it.candidateIndex == index }

    fun nextCandidateIndex(afterIndex: Int): Int? {
        val currentPosition = candidates.indexOfFirst { it.candidateIndex == afterIndex }
        return when {
            currentPosition < 0 -> firstCandidateIndex
            currentPosition >= candidates.lastIndex -> null
            else -> candidates[currentPosition + 1].candidateIndex
        }
    }
}

/**
 * Keeps provider-supplied ordering, validates every URI, and adds only Media3 routing hints. It
 * never invents another protocol or rewrites an authenticated URL.
 */
internal fun planMediaSources(request: PlaybackRequest): MediaSourcePlan {
    val accepted = mutableListOf<PlannedMediaSource>()
    val rejected = mutableSetOf<Int>()
    val seen = mutableSetOf<String>()

    request.candidates.forEachIndexed { index, rawCandidate ->
        if (request.isLive && accepted.size >= MAX_AUTOMATIC_SOURCE_CANDIDATES) {
            rejected += index
            return@forEachIndexed
        }
        val candidate = rawCandidate.trim()
        if (candidate.isEmpty() || !seen.add(candidate)) {
            rejected += index
            return@forEachIndexed
        }

        val parsed = runCatching { URI(candidate) }.getOrNull()
        if (!isPlayableUri(parsed)) {
            rejected += index
            return@forEachIndexed
        }

        val type = inferSourceType(
            uri = parsed!!,
            requestExtension = request.extension,
            isLive = request.isLive,
        )
        accepted += PlannedMediaSource(
            candidateIndex = index,
            uri = candidate,
            type = type,
            containerMimeType = when (type) {
                PlannedSourceType.HLS -> "application/x-mpegURL"
                PlannedSourceType.MPEG_TS -> "video/mp2t"
                PlannedSourceType.PROGRESSIVE,
                PlannedSourceType.LOCAL,
                -> null
            },
        )
    }

    return MediaSourcePlan(
        candidates = accepted,
        rejectedCandidateIndices = rejected,
    )
}

private fun isPlayableUri(uri: URI?): Boolean {
    uri ?: return false
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    return when (scheme) {
        "http", "https" -> !uri.host.isNullOrBlank()
        "file", "content" -> uri.schemeSpecificPart.isNotBlank()
        else -> false
    }
}

private fun inferSourceType(
    uri: URI,
    requestExtension: String,
    isLive: Boolean,
): PlannedSourceType {
    val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
    if (scheme == "file" || scheme == "content") return PlannedSourceType.LOCAL

    val pathExtension = uri.path
        .orEmpty()
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    val normalizedExtension = pathExtension.ifBlank {
        requestExtension.trim().trimStart('.').lowercase(Locale.ROOT)
    }
    return when {
        normalizedExtension == "m3u8" -> PlannedSourceType.HLS
        isLive && (normalizedExtension == "ts" || normalizedExtension == "mpegts") -> {
            PlannedSourceType.MPEG_TS
        }
        else -> PlannedSourceType.PROGRESSIVE
    }
}

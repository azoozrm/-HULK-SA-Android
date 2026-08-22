package sa.hulksa.player.tv

import java.net.URI
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.ProfileKind

sealed interface TvDeepLinkTarget {
    data object Home : TvDeepLinkTarget

    data class Movie(
        val movieId: Int,
        val resumePlayback: Boolean = false,
        val profileScopeId: String? = null,
    ) : TvDeepLinkTarget

    data class Series(val seriesId: Int) : TvDeepLinkTarget

    data class Episode(
        val seriesId: Int,
        val episodeId: Int,
        val resumePlayback: Boolean = false,
        val profileScopeId: String? = null,
    ) : TvDeepLinkTarget
}

/**
 * Strict, non-sensitive URI contract used by Android TV programs.
 *
 * Only positive content identifiers, the fixed `resume=true` signal, and an optional opaque
 * profile-scope digest are accepted. Raw account/profile identifiers, reseller data, hosts and
 * credentials never form part of a URI.
 */
object TvDeepLinkRouter {
    private const val SCHEME = "hulksa"
    private const val RESUME_PARAMETER = "resume=true"
    private const val SCOPE_PARAMETER_PREFIX = "scope="
    private const val MAX_URI_LENGTH = 256

    fun parse(rawUri: String?): TvDeepLinkTarget? {
        val raw = rawUri
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_URI_LENGTH }
            ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (
            uri.isOpaque ||
            !uri.scheme.equals(SCHEME, ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.fragment != null
        ) return null

        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath
            .orEmpty()
            .split('/')
            .filter(String::isNotEmpty)
        val query = parseQuery(uri.rawQuery) ?: return null

        return when (host) {
            "home" -> TvDeepLinkTarget.Home.takeIf {
                segments.isEmpty() && !query.resumePlayback && query.profileScopeId == null
            }
            "movie" -> segments.singlePositiveId()?.let { movieId ->
                TvDeepLinkTarget.Movie(
                    movieId = movieId,
                    resumePlayback = query.resumePlayback,
                    profileScopeId = query.profileScopeId,
                )
            }
            "series" -> segments.singlePositiveId()
                ?.takeIf { !query.resumePlayback && query.profileScopeId == null }
                ?.let { TvDeepLinkTarget.Series(it) }
            "episode" -> segments.twoPositiveIds()?.let { (seriesId, episodeId) ->
                TvDeepLinkTarget.Episode(
                    seriesId = seriesId,
                    episodeId = episodeId,
                    resumePlayback = query.resumePlayback,
                    profileScopeId = query.profileScopeId,
                )
            }
            else -> null
        }
    }

    fun uri(target: TvDeepLinkTarget): String = when (target) {
        TvDeepLinkTarget.Home -> "$SCHEME://home"
        is TvDeepLinkTarget.Movie -> buildString {
            append("$SCHEME://movie/${target.movieId}")
            appendPlaybackQuery(target.resumePlayback, target.profileScopeId)
        }
        is TvDeepLinkTarget.Series -> "$SCHEME://series/${target.seriesId}"
        is TvDeepLinkTarget.Episode -> buildString {
            append("$SCHEME://episode/${target.seriesId}/${target.episodeId}")
            appendPlaybackQuery(target.resumePlayback, target.profileScopeId)
        }
    }

    private fun parseQuery(rawQuery: String?): ParsedTvDeepLinkQuery? {
        if (rawQuery == null) return ParsedTvDeepLinkQuery()
        val parts = rawQuery.split('&')
        if (parts.isEmpty() || parts.any(String::isBlank) || parts.distinct().size != parts.size) {
            return null
        }
        val resume = RESUME_PARAMETER in parts
        val scopeParts = parts.filter { it.startsWith(SCOPE_PARAMETER_PREFIX) }
        if (scopeParts.size > 1) return null
        val scope = scopeParts.singleOrNull()?.removePrefix(SCOPE_PARAMETER_PREFIX)
        if (resume != (scope != null) || (scope != null && !isValidTvProfileScopeId(scope))) {
            return null
        }
        if (parts.any { it != RESUME_PARAMETER && it !in scopeParts }) return null
        return ParsedTvDeepLinkQuery(resumePlayback = resume, profileScopeId = scope)
    }

    private fun StringBuilder.appendPlaybackQuery(
        resumePlayback: Boolean,
        profileScopeId: String?,
    ) {
        require(profileScopeId == null || isValidTvProfileScopeId(profileScopeId))
        require(resumePlayback == (profileScopeId != null))
        if (!resumePlayback) return
        append("?$RESUME_PARAMETER")
        profileScopeId?.let { append("&$SCOPE_PARAMETER_PREFIX$it") }
    }
}

private data class ParsedTvDeepLinkQuery(
    val resumePlayback: Boolean = false,
    val profileScopeId: String? = null,
)

private fun List<String>.singlePositiveId(): Int? =
    singleOrNull()?.positiveContentId()

private fun List<String>.twoPositiveIds(): Pair<Int, Int>? {
    if (size != 2) return null
    val first = this[0].positiveContentId() ?: return null
    val second = this[1].positiveContentId() ?: return null
    return first to second
}

private fun String.positiveContentId(): Int? =
    takeIf { it.isNotEmpty() && all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

sealed interface TvDeepLinkResolution {
    data object OpenHome : TvDeepLinkResolution
    data class AwaitCatalog(val type: ContentType) : TvDeepLinkResolution
    data class OpenMovie(val item: ContentItem, val resumePlayback: Boolean) : TvDeepLinkResolution
    data class OpenSeries(val item: ContentItem) : TvDeepLinkResolution
    data class OpenEpisode(
        val series: ContentItem,
        val episodeId: Int,
        val resumePlayback: Boolean,
    ) : TvDeepLinkResolution
    data object MissingContent : TvDeepLinkResolution
    data object BlockedForKids : TvDeepLinkResolution
    data object StaleProfile : TvDeepLinkResolution
}

enum class TvDeepLinkDispatchDecision {
    WAIT_FOR_SESSION_RESTORATION,
    SHOW_LOGIN,
    WAIT_FOR_PROFILE,
    DISPATCH,
}

/** Pure gate for activity/session/profile readiness before a TV deep link is resolved. */
internal fun decideTvDeepLinkDispatch(
    sessionRestorationComplete: Boolean,
    hasSession: Boolean,
    profileResolved: Boolean,
    kidsVerificationRequired: Boolean,
    kidsVerified: Boolean,
): TvDeepLinkDispatchDecision = when {
    !sessionRestorationComplete -> TvDeepLinkDispatchDecision.WAIT_FOR_SESSION_RESTORATION
    !hasSession -> TvDeepLinkDispatchDecision.SHOW_LOGIN
    !profileResolved || (kidsVerificationRequired && !kidsVerified) ->
        TvDeepLinkDispatchDecision.WAIT_FOR_PROFILE
    else -> TvDeepLinkDispatchDecision.DISPATCH
}

internal fun resolveTvDeepLink(
    target: TvDeepLinkTarget,
    movieCatalog: Catalog?,
    seriesCatalog: Catalog?,
    history: List<HistoryEntry>,
    profileKind: ProfileKind,
    verifiedKidsContentKeys: Set<String>,
    activeProfileScopeId: String? = null,
): TvDeepLinkResolution {
    val targetScopeId = when (target) {
        is TvDeepLinkTarget.Movie -> target.profileScopeId
        is TvDeepLinkTarget.Episode -> target.profileScopeId
        else -> null
    }
    if (targetScopeId != null && targetScopeId != activeProfileScopeId) {
        return TvDeepLinkResolution.StaleProfile
    }
    return when (target) {
        TvDeepLinkTarget.Home -> TvDeepLinkResolution.OpenHome
        is TvDeepLinkTarget.Movie -> {
            val catalog = movieCatalog
                ?: return TvDeepLinkResolution.AwaitCatalog(ContentType.MOVIE)
            val movie = catalog.items.firstOrNull {
                it.type == ContentType.MOVIE && it.id == target.movieId
            } ?: return TvDeepLinkResolution.MissingContent
            if (!isTvContentAllowed(profileKind, verifiedKidsContentKeys, movie)) {
                return TvDeepLinkResolution.BlockedForKids
            }
            if (
                target.resumePlayback && history.none {
                    it.streamKind.equals("movie", ignoreCase = true) &&
                        it.key == "MOVIE:${movie.id}" &&
                        it.streamId == movie.id &&
                        isTvContinueWatchingEligible(it)
                }
            ) return TvDeepLinkResolution.MissingContent
            TvDeepLinkResolution.OpenMovie(movie, target.resumePlayback)
        }
        is TvDeepLinkTarget.Series -> {
            val catalog = seriesCatalog
                ?: return TvDeepLinkResolution.AwaitCatalog(ContentType.SERIES)
            val series = catalog.items.firstOrNull {
                it.type == ContentType.SERIES && it.id == target.seriesId
            } ?: return TvDeepLinkResolution.MissingContent
            if (!isTvContentAllowed(profileKind, verifiedKidsContentKeys, series)) {
                return TvDeepLinkResolution.BlockedForKids
            }
            TvDeepLinkResolution.OpenSeries(series)
        }
        is TvDeepLinkTarget.Episode -> {
            val catalog = seriesCatalog
                ?: return TvDeepLinkResolution.AwaitCatalog(ContentType.SERIES)
            val series = catalog.items.firstOrNull {
                it.type == ContentType.SERIES && it.id == target.seriesId
            } ?: return TvDeepLinkResolution.MissingContent
            if (!isTvContentAllowed(profileKind, verifiedKidsContentKeys, series)) {
                return TvDeepLinkResolution.BlockedForKids
            }
            if (
                target.resumePlayback && history.none {
                    it.streamKind.equals("series", ignoreCase = true) &&
                        it.key == "SERIES:${target.episodeId}" &&
                        it.parentContentId == series.id &&
                        it.streamId == target.episodeId &&
                        isTvContinueWatchingEligible(it)
                }
            ) return TvDeepLinkResolution.MissingContent
            TvDeepLinkResolution.OpenEpisode(
                series = series,
                episodeId = target.episodeId,
                resumePlayback = target.resumePlayback,
            )
        }
    }
}

internal fun findTvDeepLinkEpisode(episodes: List<Episode>, episodeId: Int): Episode? =
    episodes.firstOrNull { it.id == episodeId }

private fun isTvContentAllowed(
    profileKind: ProfileKind,
    verifiedKidsContentKeys: Set<String>,
    item: ContentItem,
): Boolean = profileKind != ProfileKind.KIDS ||
    "${item.type.name}:${item.id}" in verifiedKidsContentKeys

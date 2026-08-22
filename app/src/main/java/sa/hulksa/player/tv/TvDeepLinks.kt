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
    ) : TvDeepLinkTarget

    data class Series(val seriesId: Int) : TvDeepLinkTarget

    data class Episode(
        val seriesId: Int,
        val episodeId: Int,
        val resumePlayback: Boolean = false,
    ) : TvDeepLinkTarget
}

/**
 * Strict, non-sensitive URI contract used by Android TV programs.
 *
 * Only positive content identifiers and the fixed `resume=true` signal are accepted. Account,
 * profile, reseller, host and credential data never form part of a URI.
 */
object TvDeepLinkRouter {
    private const val SCHEME = "hulksa"
    private const val RESUME_QUERY = "resume=true"
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
        val resume = when (uri.rawQuery) {
            null -> false
            RESUME_QUERY -> true
            else -> return null
        }

        return when (host) {
            "home" -> TvDeepLinkTarget.Home.takeIf { segments.isEmpty() && !resume }
            "movie" -> segments.singlePositiveId()?.let { movieId ->
                TvDeepLinkTarget.Movie(movieId, resumePlayback = resume)
            }
            "series" -> segments.singlePositiveId()
                ?.takeIf { !resume }
                ?.let { TvDeepLinkTarget.Series(it) }
            "episode" -> segments.twoPositiveIds()?.let { (seriesId, episodeId) ->
                TvDeepLinkTarget.Episode(
                    seriesId = seriesId,
                    episodeId = episodeId,
                    resumePlayback = resume,
                )
            }
            else -> null
        }
    }

    fun uri(target: TvDeepLinkTarget): String = when (target) {
        TvDeepLinkTarget.Home -> "$SCHEME://home"
        is TvDeepLinkTarget.Movie -> buildString {
            append("$SCHEME://movie/${target.movieId}")
            if (target.resumePlayback) append("?$RESUME_QUERY")
        }
        is TvDeepLinkTarget.Series -> "$SCHEME://series/${target.seriesId}"
        is TvDeepLinkTarget.Episode -> buildString {
            append("$SCHEME://episode/${target.seriesId}/${target.episodeId}")
            if (target.resumePlayback) append("?$RESUME_QUERY")
        }
    }
}

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
}

internal fun resolveTvDeepLink(
    target: TvDeepLinkTarget,
    movieCatalog: Catalog?,
    seriesCatalog: Catalog?,
    history: List<HistoryEntry>,
    profileKind: ProfileKind,
    verifiedKidsContentKeys: Set<String>,
): TvDeepLinkResolution {
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

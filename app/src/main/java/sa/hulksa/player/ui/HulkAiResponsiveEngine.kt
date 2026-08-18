package sa.hulksa.player.ui

import java.util.PriorityQueue
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry

private const val HULK_AI_RECENT_POOL_PER_TYPE = 700
private const val HULK_AI_RATED_POOL_PER_TYPE = 420
private const val HULK_AI_QUERY_POOL_PER_TYPE = 260
private const val HULK_AI_PINNED_HISTORY_LIMIT = 80

/**
 * Bounds very large IPTV catalogs before the full explainable HULK AI ranker runs.
 *
 * This keeps recommendation latency predictable on Android TV while preserving only real
 * provider items. The bounded pool keeps profile-pinned titles, newest content, highest rated
 * content, and items whose real title/genre/year text matches useful request tokens.
 */
internal fun buildResponsiveHulkAiQuerySuggestions(
    rawQuery: String,
    movies: List<ContentItem>,
    series: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
    limit: Int = 24,
): HulkAiQueryResult {
    val pinnedKeys = hulkAiPinnedKeys(history, favorites)
    val queryTokens = responsiveHulkAiTokens(rawQuery)

    val boundedMovies = responsiveHulkAiCandidates(
        source = movies,
        pinnedKeys = pinnedKeys,
        queryTokens = queryTokens,
    )
    val boundedSeries = responsiveHulkAiCandidates(
        source = series,
        pinnedKeys = pinnedKeys,
        queryTokens = queryTokens,
    )

    return buildHulkAiQuerySuggestions(
        rawQuery = rawQuery,
        movies = boundedMovies,
        series = boundedSeries,
        history = history,
        favorites = favorites,
        limit = limit,
    )
}

private data class ResponsiveCandidate(
    val item: ContentItem,
    val score: Long,
)

private fun responsiveHulkAiCandidates(
    source: List<ContentItem>,
    pinnedKeys: Set<String>,
    queryTokens: List<String>,
): List<ContentItem> {
    if (source.size <= HULK_AI_RECENT_POOL_PER_TYPE + HULK_AI_RATED_POOL_PER_TYPE) {
        return source
    }

    val recent = PriorityQueue<ResponsiveCandidate>(
        compareBy<ResponsiveCandidate> { it.score }.thenBy { it.item.id },
    )
    val rated = PriorityQueue<ResponsiveCandidate>(
        compareBy<ResponsiveCandidate> { it.score }.thenBy { it.item.id },
    )
    val queryMatches = PriorityQueue<ResponsiveCandidate>(
        compareBy<ResponsiveCandidate> { it.score }.thenBy { it.item.id },
    )
    val pinned = ArrayList<ContentItem>()

    source.forEach { item ->
        if (item.type != ContentType.MOVIE && item.type != ContentType.SERIES) return@forEach
        val key = "${item.type.name}:${item.id}"
        if (key in pinnedKeys) pinned += item

        offerBounded(
            queue = recent,
            candidate = ResponsiveCandidate(item, item.addedAtEpochSeconds ?: 0L),
            limit = HULK_AI_RECENT_POOL_PER_TYPE,
        )

        val rating = item.rating?.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 0.0
        val ratedScore = (rating * 1_000_000_000L).toLong() +
            ((item.addedAtEpochSeconds ?: 0L) % 1_000_000_000L)
        offerBounded(
            queue = rated,
            candidate = ResponsiveCandidate(item, ratedScore),
            limit = HULK_AI_RATED_POOL_PER_TYPE,
        )

        if (queryTokens.isNotEmpty()) {
            val searchable = normalizeSearchText(
                listOfNotNull(item.name, item.genre, item.year, item.plot)
                    .joinToString(" "),
            )
            val matches = queryTokens.count(searchable::contains)
            if (matches > 0) {
                val queryScore = matches * 10_000_000_000L + ratedScore
                offerBounded(
                    queue = queryMatches,
                    candidate = ResponsiveCandidate(item, queryScore),
                    limit = HULK_AI_QUERY_POOL_PER_TYPE,
                )
            }
        }
    }

    return buildList {
        addAll(pinned)
        addAll(recent.toSortedCandidates())
        addAll(rated.toSortedCandidates())
        addAll(queryMatches.toSortedCandidates())
    }.distinctBy { "${it.type.name}:${it.id}" }
}

private fun offerBounded(
    queue: PriorityQueue<ResponsiveCandidate>,
    candidate: ResponsiveCandidate,
    limit: Int,
) {
    if (queue.size < limit) {
        queue += candidate
        return
    }
    val weakest = queue.peek() ?: return
    if (candidate.score > weakest.score) {
        queue.poll()
        queue += candidate
    }
}

private fun PriorityQueue<ResponsiveCandidate>.toSortedCandidates(): List<ContentItem> =
    toList()
        .sortedWith(compareByDescending<ResponsiveCandidate> { it.score }.thenBy { it.item.id })
        .map(ResponsiveCandidate::item)

private fun responsiveHulkAiTokens(rawQuery: String): List<String> {
    val normalized = normalizeSearchText(rawQuery)
    return normalized
        .split(' ')
        .filter { token ->
            token.length >= 3 && token !in RESPONSIVE_AI_STOP_WORDS
        }
        .distinct()
        .take(6)
}

private val RESPONSIVE_AI_STOP_WORDS = setOf(
    "رشح", "رشحلي", "اقترح", "ابي", "ابغى", "اريد", "هولك", "فيلم", "افلام",
    "مسلسل", "مسلسلات", "محتوى", "جديد", "جديده", "حديث", "احدث", "افضل",
    "عالي", "التقييم", "تقييم", "movie", "movies", "series", "show", "hulk",
)

private fun hulkAiPinnedKeys(
    history: List<HistoryEntry>,
    favorites: Set<String>,
): Set<String> = linkedSetOf<String>().apply {
    addAll(favorites)
    history
        .asSequence()
        .filterNot(HistoryEntry::isLive)
        .sortedByDescending(HistoryEntry::updatedAtEpochMs)
        .take(HULK_AI_PINNED_HISTORY_LIMIT)
        .forEach { entry ->
            when (entry.streamKind.lowercase()) {
                "movie" -> add("MOVIE:${entry.streamId}")
                "series" -> entry.parentContentId?.let { add("SERIES:$it") }
            }
        }
}
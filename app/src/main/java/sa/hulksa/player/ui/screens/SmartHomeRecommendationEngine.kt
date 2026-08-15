package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import java.util.Locale

internal data class SmartHomeRecommendationResult(
    val continueWatching: List<HistoryEntry>,
    val lastLive: HistoryEntry?,
    val becauseYouWatched: List<ContentItem>,
    val suggested: List<ContentItem>,
    val personalizedLive: List<ContentItem>,
    val popularMovies: List<ContentItem>,
    val popularSeries: List<ContentItem>,
    val featuredCandidates: List<ContentItem>,
)

internal fun buildSmartHomeRecommendations(
    movies: List<ContentItem>,
    series: List<ContentItem>,
    live: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
): SmartHomeRecommendationResult {
    val orderedHistory = history.sortedWith(
        compareByDescending<HistoryEntry> { it.updatedAtEpochMs }
            .thenBy { it.key },
    )
    val continueWatching = orderedHistory.asSequence()
        .filter { it.smartHomeResumable() }
        .distinctBy { it.smartHomeResumeGroupKey() }
        .take(18)
        .toList()
    val lastLive = orderedHistory.firstOrNull { it.isLive }

    val movieById = movies.associateBy(ContentItem::id)
    val seriesById = series.associateBy(ContentItem::id)
    val seriesByName = series.associateBy { it.name.smartHomeNormalized() }

    fun resolveHistoryItem(entry: HistoryEntry): ContentItem? = when (entry.streamKind) {
        "movie" -> movieById[entry.streamId]
        "series" -> {
            entry.parentContentId?.let(seriesById::get)
                ?: entry.seriesTitle?.smartHomeNormalized()?.let(seriesByName::get)
                ?: entry.title.substringBefore("·").smartHomeNormalized().let(seriesByName::get)
        }
        else -> null
    }

    val historySeeds = orderedHistory.asSequence()
        .filterNot { it.isLive }
        .mapNotNull(::resolveHistoryItem)
        .distinctBy { it.smartHomeKey() }
        .take(32)
        .toList()
    fun isFavorite(item: ContentItem): Boolean = item.smartHomeKey() in favorites
    val favoriteSeeds = (movies + series).filter(::isFavorite)

    val categoryWeights = mutableMapOf<String, Int>()
    val genreWeights = mutableMapOf<String, Int>()
    val typeWeights = mutableMapOf<ContentType, Int>()

    fun addSignal(item: ContentItem, weight: Int) {
        categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + weight
        item.smartHomeGenres().forEach { genre ->
            genreWeights[genre] = (genreWeights[genre] ?: 0) + weight
        }
        typeWeights[item.type] = (typeWeights[item.type] ?: 0) + weight
    }

    historySeeds.forEachIndexed { index, item ->
        addSignal(item, (96 - index * 3).coerceAtLeast(18))
    }
    favoriteSeeds.forEach { item -> addSignal(item, 120) }

    val movieFreshness = movies.withIndex().associate { (index, item) -> item.smartHomeKey() to smartHomeFreshness(index) }
    val seriesFreshness = series.withIndex().associate { (index, item) -> item.smartHomeKey() to smartHomeFreshness(index) }
    val watchedKeys = historySeeds.mapTo(hashSetOf()) { it.smartHomeKey() }
    val affinityScores = mutableMapOf<String, Int>()
    val totalScores = mutableMapOf<String, Int>()

    fun score(item: ContentItem): Int {
        val key = item.smartHomeKey()
        val categoryScore = (categoryWeights[item.categoryId] ?: 0) * 100
        val genreScore = item.smartHomeGenres().sumOf { genre -> (genreWeights[genre] ?: 0) * 34 }
        val affinityScore = categoryScore + genreScore
        affinityScores[key] = affinityScore
        val typeScore = (typeWeights[item.type] ?: 0) * 6
        val ratingScore = ((item.rating?.toDoubleOrNull() ?: 0.0) * 90.0).toInt()
        val freshnessScore = when (item.type) {
            ContentType.MOVIE -> movieFreshness[key] ?: 0
            ContentType.SERIES -> seriesFreshness[key] ?: 0
            ContentType.LIVE -> 0
        }
        return (affinityScore + typeScore + ratingScore + freshnessScore).also { totalScores[key] = it }
    }

    val candidatePool = (movies + series).asSequence()
        .filterNot { it.smartHomeKey() in watchedKeys }
        .onEach(::score)
        .sortedWith(
            compareByDescending<ContentItem> { totalScores[it.smartHomeKey()] ?: 0 }
                .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.addedAtEpochSeconds ?: 0L }
                .thenBy { it.smartHomeKey() },
        )
        .toList()

    val becauseCandidates = candidatePool
        .filter { (affinityScores[it.smartHomeKey()] ?: 0) > 0 }
        .take(180)
    val becauseYouWatched = smartHomeDiversify(
        candidates = becauseCandidates,
        baseScores = totalScores,
        limit = 14,
    )
    val becauseKeys = becauseYouWatched.mapTo(hashSetOf()) { it.smartHomeKey() }
    val suggestedCandidates = candidatePool.asSequence()
        .filterNot { it.smartHomeKey() in becauseKeys }
        .take(220)
        .toList()
    val suggested = smartHomeDiversify(
        candidates = suggestedCandidates,
        baseScores = totalScores,
        limit = 24,
    )

    val popularMovies = movies.sortedWith(
        compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { it.addedAtEpochSeconds ?: 0L }
            .thenBy { it.id },
    ).take(22)
    val popularSeries = series.sortedWith(
        compareByDescending<ContentItem> { it.rating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { it.addedAtEpochSeconds ?: 0L }
            .thenBy { it.id },
    ).take(22)

    val featuredCandidates = sequenceOf(
        becauseYouWatched,
        suggested,
        popularMovies,
        popularSeries,
        movies,
        series,
    ).flatten()
        .filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
        .distinctBy { it.smartHomeKey() }
        .take(8)
        .toList()

    val liveById = live.associateBy(ContentItem::id)
    val viewedLive = orderedHistory.asSequence()
        .filter { it.isLive }
        .mapNotNull { liveById[it.streamId] }
        .take(30)
        .toList()
    val liveCategoryWeights = mutableMapOf<String, Int>()
    viewedLive.forEachIndexed { index, item ->
        val weight = (60 - index * 2).coerceAtLeast(6)
        liveCategoryWeights[item.categoryId] = (liveCategoryWeights[item.categoryId] ?: 0) + weight
    }
    live.filter(::isFavorite).forEach { item ->
        liveCategoryWeights[item.categoryId] = (liveCategoryWeights[item.categoryId] ?: 0) + 90
    }
    val viewedLiveIds = viewedLive.mapTo(hashSetOf(), ContentItem::id)
    val personalizedLive = live.sortedWith(
        compareByDescending<ContentItem> { item ->
            (if (isFavorite(item)) 20_000 else 0) +
                (liveCategoryWeights[item.categoryId] ?: 0) * 100 +
                (if (item.id in viewedLiveIds) 500 else 0) +
                (if (!item.nowPlaying.isNullOrBlank()) 80 else 0)
        }
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.id },
    )

    return SmartHomeRecommendationResult(
        continueWatching = continueWatching,
        lastLive = lastLive,
        becauseYouWatched = becauseYouWatched,
        suggested = suggested,
        personalizedLive = personalizedLive,
        popularMovies = popularMovies,
        popularSeries = popularSeries,
        featuredCandidates = featuredCandidates,
    )
}

private fun smartHomeDiversify(
    candidates: List<ContentItem>,
    baseScores: Map<String, Int>,
    limit: Int,
): List<ContentItem> {
    if (limit <= 0 || candidates.isEmpty()) return emptyList()
    val remaining = candidates.toMutableList()
    val selected = ArrayList<ContentItem>(minOf(limit, candidates.size))
    val categoryCounts = mutableMapOf<String, Int>()
    val typeCounts = mutableMapOf<ContentType, Int>()

    while (selected.size < limit && remaining.isNotEmpty()) {
        val best = remaining.maxWithOrNull(
            compareBy<ContentItem> { item ->
                val categoryPenalty = (categoryCounts[item.categoryId] ?: 0) * 520
                val typePenalty = (typeCounts[item.type] ?: 0) * 70
                (baseScores[item.smartHomeKey()] ?: 0) - categoryPenalty - typePenalty
            }
                .thenBy { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenBy { it.addedAtEpochSeconds ?: 0L }
                .thenByDescending { it.id },
        ) ?: break
        remaining.remove(best)
        selected += best
        categoryCounts[best.categoryId] = (categoryCounts[best.categoryId] ?: 0) + 1
        typeCounts[best.type] = (typeCounts[best.type] ?: 0) + 1
    }
    return selected
}

private fun smartHomeFreshness(index: Int): Int = (720 - index * 18).coerceAtLeast(0)

private fun ContentItem.smartHomeKey(): String = "${type.name}:$id"

private fun ContentItem.smartHomeGenres(): List<String> = genre.orEmpty()
    .split(',', '،', '/', '|')
    .map(String::smartHomeNormalized)
    .filter(String::isNotBlank)
    .distinct()

private fun HistoryEntry.smartHomeResumable(): Boolean =
    !isLive && positionMs > 0L && (durationMs <= 0L || positionMs.toDouble() / durationMs < .92)

private fun HistoryEntry.smartHomeResumeGroupKey(): String = when (streamKind) {
    "series" -> parentContentId?.let { "SERIES:$it" }
        ?: seriesTitle?.smartHomeNormalized()?.takeIf(String::isNotBlank)?.let { "SERIES_NAME:$it" }
        ?: "SERIES_ENTRY:${title.substringBefore("·").smartHomeNormalized()}"
    else -> key
}

private fun String.smartHomeNormalized(): String = trim().lowercase(Locale.ROOT)

package sa.hulksa.player.ui.screens

import sa.hulksa.player.data.withHomeHeroMetadataToken
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
    rotationSeed: Int = smartHomeRotationSeed(movies, series, history, favorites),
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

    val affinityCandidates = candidatePool
        .filter { (affinityScores[it.smartHomeKey()] ?: 0) > 0 }
        .take(160)
    val needsBecauseDiversification = affinityCandidates
        .groupingBy(ContentItem::categoryId)
        .eachCount()
        .values
        .any { count -> count > 4 }
    val becauseCandidates = if (needsBecauseDiversification) {
        (affinityCandidates + candidatePool.asSequence()
            .filter { (affinityScores[it.smartHomeKey()] ?: 0) <= 0 }
            .take(80)
            .toList())
            .distinctBy { it.smartHomeKey() }
    } else {
        affinityCandidates
    }
    val becauseYouWatched = smartHomeDiversify(
        candidates = becauseCandidates,
        baseScores = totalScores,
        limit = 14,
        maxPerCategory = 4,
    )
    val becauseKeys = becauseYouWatched.mapTo(hashSetOf()) { it.smartHomeKey() }
    val suggestedCandidates = candidatePool.asSequence()
        .filterNot { it.smartHomeKey() in becauseKeys }
        .take(220)
        .toList()
    val suggestedWindow = smartHomeRecommendationWindow(
        candidates = suggestedCandidates,
        rotationSeed = rotationSeed,
    )
    val minimumSuggestedPerType = 8
    val suggestedWindowKeys = suggestedWindow.mapTo(hashSetOf()) { it.smartHomeKey() }
    val hasSuggestedMovies = suggestedCandidates.any { it.type == ContentType.MOVIE }
    val hasSuggestedSeries = suggestedCandidates.any { it.type == ContentType.SERIES }
    val movieDeficit = if (hasSuggestedMovies && hasSuggestedSeries) {
        (minimumSuggestedPerType - suggestedWindow.count { it.type == ContentType.MOVIE }).coerceAtLeast(0)
    } else {
        0
    }
    val seriesDeficit = if (hasSuggestedMovies && hasSuggestedSeries) {
        (minimumSuggestedPerType - suggestedWindow.count { it.type == ContentType.SERIES }).coerceAtLeast(0)
    } else {
        0
    }
    val suggestedPool = (
        suggestedWindow +
            suggestedCandidates.asSequence()
                .filter { it.type == ContentType.MOVIE && it.smartHomeKey() !in suggestedWindowKeys }
                .take(movieDeficit)
                .toList() +
            suggestedCandidates.asSequence()
                .filter { it.type == ContentType.SERIES && it.smartHomeKey() !in suggestedWindowKeys }
                .take(seriesDeficit)
                .toList()
        )
        .distinctBy { it.smartHomeKey() }
    val diversifiedSuggested = smartHomeDiversify(
        candidates = suggestedPool,
        baseScores = totalScores,
        limit = minOf(72, suggestedPool.size),
        maxPerCategory = 3,
    )
    val suggested = smartHomeBalanceContentTypes(
        candidates = diversifiedSuggested + suggestedPool,
        limit = 24,
        minimumPerType = minimumSuggestedPerType,
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

    val rawFeaturedPool = sequenceOf(
        suggested,
        becauseYouWatched,
        popularMovies,
        popularSeries,
        movies,
        series,
    ).flatten()
        .filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
        .distinctBy { it.smartHomeKey() }
        .toList()

    // Cinema Mode keeps the hero fresh when enough alternatives exist. A watched title
    // can still be used as a fail-safe when the catalog is small, but it will not crowd
    // out undiscovered content on normal catalogs.
    val unwatchedFeaturedPool = rawFeaturedPool.filterNot { it.smartHomeKey() in watchedKeys }
    val freshFeaturedPool = if (unwatchedFeaturedPool.size >= 8) {
        unwatchedFeaturedPool
    } else {
        rawFeaturedPool
    }

    // Backdrops are the preferred cinematic surface. Poster-only artwork remains a
    // fallback for small/incomplete provider catalogs instead of producing an empty hero.
    val backdropFeaturedPool = freshFeaturedPool.filter { !it.backdropUrl.isNullOrBlank() }
    val cinemaFeaturedPool = if (backdropFeaturedPool.size >= 8) {
        backdropFeaturedPool
    } else {
        freshFeaturedPool
    }

    val heroScores = cinemaFeaturedPool.associate { item ->
        val recommendationScore = (totalScores[item.smartHomeKey()] ?: 0) / 6
        item.smartHomeKey() to (item.smartHomeHeroQualityScore() + recommendationScore)
    }
    val rankedFeatured = cinemaFeaturedPool.sortedWith(
        compareByDescending<ContentItem> { heroScores[it.smartHomeKey()] ?: 0 }
            .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { it.addedAtEpochSeconds ?: 0L }
            .thenBy { it.smartHomeKey() },
    )
    val diversifiedFeatured = smartHomeDiversify(
        candidates = rankedFeatured,
        baseScores = heroScores,
        limit = minOf(24, rankedFeatured.size),
        maxPerCategory = 2,
    )
    val featuredCandidates = smartHomeBalanceContentTypes(
        candidates = diversifiedFeatured + rankedFeatured,
        limit = 8,
        minimumPerType = 4,
    ).map(ContentItem::withHomeHeroMetadataToken)

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

private fun smartHomeBalanceContentTypes(
    candidates: List<ContentItem>,
    limit: Int,
    minimumPerType: Int,
): List<ContentItem> {
    if (limit <= 0 || candidates.isEmpty()) return emptyList()

    val unique = candidates.distinctBy { it.smartHomeKey() }
    val movieCandidates = unique.filter { it.type == ContentType.MOVIE }
    val seriesCandidates = unique.filter { it.type == ContentType.SERIES }
    if (movieCandidates.isEmpty() || seriesCandidates.isEmpty()) return unique.take(limit)

    val requiredEach = minOf(
        minimumPerType.coerceAtLeast(0),
        limit / 2,
        movieCandidates.size,
        seriesCandidates.size,
    )
    if (requiredEach <= 0) return unique.take(limit)

    val selected = ArrayList<ContentItem>(minOf(limit, unique.size))
    var movieIndex = 0
    var seriesIndex = 0
    val movieFirst = unique.firstOrNull()?.type != ContentType.SERIES

    repeat(requiredEach) {
        if (movieFirst) {
            selected += movieCandidates[movieIndex++]
            selected += seriesCandidates[seriesIndex++]
        } else {
            selected += seriesCandidates[seriesIndex++]
            selected += movieCandidates[movieIndex++]
        }
    }

    val selectedKeys = selected.mapTo(hashSetOf()) { it.smartHomeKey() }
    unique.asSequence()
        .filterNot { it.smartHomeKey() in selectedKeys }
        .take((limit - selected.size).coerceAtLeast(0))
        .forEach { selected += it }
    return selected.take(limit)
}

private fun smartHomeDiversify(
    candidates: List<ContentItem>,
    baseScores: Map<String, Int>,
    limit: Int,
    maxPerCategory: Int = Int.MAX_VALUE,
): List<ContentItem> {
    if (limit <= 0 || candidates.isEmpty()) return emptyList()
    val remaining = candidates.toMutableList()
    val selected = ArrayList<ContentItem>(minOf(limit, candidates.size))
    val categoryCounts = mutableMapOf<String, Int>()
    val typeCounts = mutableMapOf<ContentType, Int>()

    while (selected.size < limit && remaining.isNotEmpty()) {
        val underCategoryCap = remaining.filter { item ->
            (categoryCounts[item.categoryId] ?: 0) < maxPerCategory
        }
        val source = if (underCategoryCap.isNotEmpty()) underCategoryCap else remaining
        val best = source.maxWithOrNull(
            compareBy<ContentItem> { item ->
                val categoryPenalty = (categoryCounts[item.categoryId] ?: 0) * 1_600
                val typePenalty = (typeCounts[item.type] ?: 0) * 120
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

private fun smartHomeRecommendationWindow(
    candidates: List<ContentItem>,
    rotationSeed: Int,
): List<ContentItem> {
    if (candidates.size <= 32 || rotationSeed == 0) return candidates.take(120)
    val maxOffset = minOf(56, (candidates.size - 24).coerceAtLeast(0))
    if (maxOffset <= 0) return candidates.take(120)
    val step = 8
    val pageCount = (maxOffset / step) + 1
    val offset = Math.floorMod(rotationSeed, pageCount) * step
    return candidates.drop(offset).take(120)
}

private fun smartHomeRotationSeed(
    movies: List<ContentItem>,
    series: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
): Int {
    var result = 17
    history.sortedByDescending { it.updatedAtEpochMs }.take(8).forEach { entry ->
        result = 31 * result + entry.key.hashCode()
        result = 31 * result + entry.updatedAtEpochMs.hashCode()
        result = 31 * result + entry.positionMs.hashCode()
    }
    favorites.sorted().take(16).forEach { key -> result = 31 * result + key.hashCode() }
    (movies.take(4) + series.take(4)).forEach { item ->
        result = 31 * result + item.id
        result = 31 * result + (item.addedAtEpochSeconds ?: 0L).hashCode()
    }
    result = 31 * result + movies.size
    result = 31 * result + series.size
    return result
}

private fun ContentItem.smartHomeHeroQualityScore(): Int {
    val plotLength = plot?.trim().orEmpty().length
    val plotScore = when {
        plotLength >= 90 -> 3_200
        plotLength > 0 -> 1_900
        else -> 0
    }
    val ratingScore = (((rating?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 10.0)) * 140.0).toInt()
    return (if (!backdropUrl.isNullOrBlank()) 9_000 else 0) +
        plotScore +
        (if (!genre.isNullOrBlank()) 1_200 else 0) +
        (if (!year.isNullOrBlank()) 700 else 0) +
        ratingScore +
        (if (!posterUrl.isNullOrBlank()) 250 else 0)
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
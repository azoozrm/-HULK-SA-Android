package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import java.util.Locale

internal enum class HulkAiEvidenceType {
    FAVORITE_GENRE,
    RECENT_GENRE,
    FAVORITE_CATEGORY,
    RECENT_CATEGORY,
    HIGH_RATING,
    FRESH_CONTENT,
}

internal data class HulkAiEvidence(
    val type: HulkAiEvidenceType,
    val value: String,
)

internal data class HulkAiSuggestion(
    val item: ContentItem,
    val score: Int,
    val evidence: List<HulkAiEvidence>,
)

internal data class HulkAiResult(
    val suggestions: List<HulkAiSuggestion>,
    val preferredGenres: List<String>,
    val hasProfileSignals: Boolean,
)

/**
 * Local, explainable recommendation foundation for HULK AI.
 *
 * The engine uses only real catalog metadata plus the active profile's history/favorites.
 * It does not call an external AI service and never invents cast, genres, ratings or titles.
 */
internal fun buildHulkAiSuggestions(
    movies: List<ContentItem>,
    series: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
    limit: Int = 12,
): HulkAiResult {
    if (limit <= 0) return HulkAiResult(emptyList(), emptyList(), false)

    val catalog = (movies + series)
        .filter { it.type == ContentType.MOVIE || it.type == ContentType.SERIES }
        .distinctBy(ContentItem::hulkAiKey)
    if (catalog.isEmpty()) return HulkAiResult(emptyList(), emptyList(), false)

    val byKey = catalog.associateBy(ContentItem::hulkAiKey)
    val seriesByName = series
        .groupBy { it.name.hulkAiNormalized() }
        .mapValues { (_, items) -> items.first() }

    val favoriteItems = favorites.mapNotNull(byKey::get)
    val recentHistory = history
        .asSequence()
        .filterNot(HistoryEntry::isLive)
        .sortedByDescending(HistoryEntry::updatedAtEpochMs)
        .take(40)
        .toList()
    val recentItems = recentHistory.mapNotNull { entry ->
        when (entry.streamKind.lowercase(Locale.ROOT)) {
            "movie" -> byKey["MOVIE:${entry.streamId}"]
            "series" -> entry.parentContentId
                ?.let { byKey["SERIES:$it"] }
                ?: entry.seriesTitle
                    ?.hulkAiNormalized()
                    ?.let(seriesByName::get)
            else -> null
        }
    }

    val watchedKeys = recentItems.mapTo(linkedSetOf(), ContentItem::hulkAiKey)
    val favoriteKeys = favoriteItems.mapTo(linkedSetOf(), ContentItem::hulkAiKey)

    val favoriteGenreScores = linkedMapOf<String, Int>()
    val recentGenreScores = linkedMapOf<String, Int>()
    val favoriteCategoryScores = linkedMapOf<String, Int>()
    val recentCategoryScores = linkedMapOf<String, Int>()

    favoriteItems.forEach { item ->
        item.hulkAiGenres().forEach { genre -> favoriteGenreScores.increment(genre, 16) }
        favoriteCategoryScores.increment(item.categoryId.hulkAiNormalized(), 10)
    }
    recentItems.forEachIndexed { index, item ->
        val weight = (13 - (index / 4)).coerceAtLeast(4)
        item.hulkAiGenres().forEach { genre -> recentGenreScores.increment(genre, weight) }
        recentCategoryScores.increment(item.categoryId.hulkAiNormalized(), (weight - 2).coerceAtLeast(2))
    }

    val hasProfileSignals = favoriteItems.isNotEmpty() || recentItems.isNotEmpty()
    val preferredGenres = (favoriteGenreScores.keys + recentGenreScores.keys)
        .distinct()
        .sortedWith(
            compareByDescending<String> { (favoriteGenreScores[it] ?: 0) + (recentGenreScores[it] ?: 0) }
                .thenBy { it },
        )
        .take(6)

    val freshnessRank = catalog
        .sortedWith(
            compareByDescending<ContentItem> { it.addedAtEpochSeconds ?: 0L }
                .thenBy { it.hulkAiKey() },
        )
        .mapIndexed { index, item -> item.hulkAiKey() to index }
        .toMap()

    val discoveryPool = catalog.filterNot { item ->
        val key = item.hulkAiKey()
        key in watchedKeys || key in favoriteKeys
    }
    val candidatePool = if (discoveryPool.isNotEmpty()) discoveryPool else catalog

    val ranked = candidatePool.map { item ->
        val genres = item.hulkAiGenres()
        val category = item.categoryId.hulkAiNormalized()
        val evidence = mutableListOf<HulkAiEvidence>()

        val favoriteGenre = genres.maxByOrNull { favoriteGenreScores[it] ?: 0 }
            ?.takeIf { (favoriteGenreScores[it] ?: 0) > 0 }
        val recentGenre = genres.maxByOrNull { recentGenreScores[it] ?: 0 }
            ?.takeIf { (recentGenreScores[it] ?: 0) > 0 }
        val favoriteCategory = (favoriteCategoryScores[category] ?: 0).takeIf { it > 0 }
        val recentCategory = (recentCategoryScores[category] ?: 0).takeIf { it > 0 }

        favoriteGenre?.let { evidence += HulkAiEvidence(HulkAiEvidenceType.FAVORITE_GENRE, it) }
        recentGenre?.let { evidence += HulkAiEvidence(HulkAiEvidenceType.RECENT_GENRE, it) }
        favoriteCategory?.let { evidence += HulkAiEvidence(HulkAiEvidenceType.FAVORITE_CATEGORY, item.categoryId) }
        recentCategory?.let { evidence += HulkAiEvidence(HulkAiEvidenceType.RECENT_CATEGORY, item.categoryId) }

        val rating = item.rating?.toDoubleOrNull()?.coerceIn(0.0, 10.0)
        if (rating != null && rating >= 7.5) {
            evidence += HulkAiEvidence(HulkAiEvidenceType.HIGH_RATING, item.rating.orEmpty())
        }
        val freshRank = freshnessRank[item.hulkAiKey()] ?: catalog.size
        if (freshRank < 20) {
            evidence += HulkAiEvidence(HulkAiEvidenceType.FRESH_CONTENT, item.addedAtEpochSeconds?.toString().orEmpty())
        }

        val profileScore =
            genres.sumOf { (favoriteGenreScores[it] ?: 0) * 90 + (recentGenreScores[it] ?: 0) * 70 } +
                (favoriteCategoryScores[category] ?: 0) * 55 +
                (recentCategoryScores[category] ?: 0) * 40
        val ratingScore = ((rating ?: 0.0) * 90.0).toInt()
        val freshnessScore = (420 - freshRank * 16).coerceAtLeast(0)
        val metadataScore =
            (if (!item.posterUrl.isNullOrBlank()) 90 else 0) +
                (if (!item.backdropUrl.isNullOrBlank()) 120 else 0) +
                (if (!item.plot.isNullOrBlank()) 55 else 0) +
                (if (!item.year.isNullOrBlank()) 25 else 0)

        HulkAiSuggestion(
            item = item,
            score = profileScore + ratingScore + freshnessScore + metadataScore,
            evidence = evidence.distinct(),
        )
    }.sortedWith(
        compareByDescending<HulkAiSuggestion> { it.score }
            .thenByDescending { it.item.rating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { it.item.addedAtEpochSeconds ?: 0L }
            .thenBy { it.item.hulkAiKey() },
    )

    val diversified = diversifyHulkAi(ranked, limit)
    val balanced = balanceHulkAiTypes(diversified, ranked, limit)

    return HulkAiResult(
        suggestions = balanced.take(limit),
        preferredGenres = preferredGenres,
        hasProfileSignals = hasProfileSignals,
    )
}

private fun diversifyHulkAi(
    ranked: List<HulkAiSuggestion>,
    limit: Int,
): List<HulkAiSuggestion> {
    if (ranked.size <= limit) return ranked

    val selected = mutableListOf<HulkAiSuggestion>()
    val categoryCounts = mutableMapOf<String, Int>()
    val genreCounts = mutableMapOf<String, Int>()
    val maxPerCategory = if (limit <= 8) 2 else 3
    val maxPerGenre = if (limit <= 8) 3 else 4

    ranked.forEach { suggestion ->
        if (selected.size >= limit) return@forEach
        val category = suggestion.item.categoryId.hulkAiNormalized()
        val genre = suggestion.item.hulkAiGenres().firstOrNull().orEmpty()
        if ((categoryCounts[category] ?: 0) >= maxPerCategory) return@forEach
        if (genre.isNotBlank() && (genreCounts[genre] ?: 0) >= maxPerGenre) return@forEach
        selected += suggestion
        categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
        if (genre.isNotBlank()) genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
    }

    if (selected.size < limit) {
        val selectedKeys = selected.mapTo(hashSetOf()) { it.item.hulkAiKey() }
        ranked.forEach { suggestion ->
            if (selected.size >= limit) return@forEach
            if (selectedKeys.add(suggestion.item.hulkAiKey())) selected += suggestion
        }
    }
    return selected
}

private fun balanceHulkAiTypes(
    diversified: List<HulkAiSuggestion>,
    ranked: List<HulkAiSuggestion>,
    limit: Int,
): List<HulkAiSuggestion> {
    if (limit < 4) return diversified.take(limit)
    val movies = ranked.filter { it.item.type == ContentType.MOVIE }
    val series = ranked.filter { it.item.type == ContentType.SERIES }
    if (movies.size < 2 || series.size < 2) return diversified.take(limit)

    val selected = mutableListOf<HulkAiSuggestion>()
    val selectedKeys = linkedSetOf<String>()
    fun add(suggestion: HulkAiSuggestion) {
        if (selected.size < limit && selectedKeys.add(suggestion.item.hulkAiKey())) selected += suggestion
    }

    movies.take(2).forEach(::add)
    series.take(2).forEach(::add)
    diversified.forEach(::add)
    ranked.forEach(::add)

    val rankByKey = ranked.mapIndexed { index, suggestion -> suggestion.item.hulkAiKey() to index }.toMap()
    return selected.sortedBy { rankByKey[it.item.hulkAiKey()] ?: Int.MAX_VALUE }
}

private fun ContentItem.hulkAiKey(): String = "${type.name}:$id"

private fun ContentItem.hulkAiGenres(): List<String> = genre
    .orEmpty()
    .split(',', '،', '/', '|', ';', '•')
    .map(String::trim)
    .filter(String::isNotBlank)
    .map(String::hulkAiNormalized)
    .distinct()

private fun String.hulkAiNormalized(): String = trim().lowercase(Locale.ROOT)

private fun MutableMap<String, Int>.increment(key: String, amount: Int) {
    if (key.isBlank()) return
    this[key] = (this[key] ?: 0) + amount
}

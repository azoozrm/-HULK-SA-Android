package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import java.util.Locale

internal enum class SmartCollectionSource {
    PROFILE,
    GENRE,
    CURATED,
}

internal data class SmartCollection(
    val key: String,
    val title: String,
    val items: List<ContentItem>,
    val source: SmartCollectionSource,
)

internal fun buildSmartCollections(
    movies: List<ContentItem>,
    series: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
    limitPerCollection: Int = 18,
    maxGenreCollections: Int = 2,
): List<SmartCollection> {
    val limit = limitPerCollection.coerceAtLeast(1)
    val genreLimit = maxGenreCollections.coerceAtLeast(0)
    val catalog = (movies + series)
        .filter { it.type == ContentType.MOVIE || it.type == ContentType.SERIES }
        .distinctBy { it.smartCollectionKey() }
    if (catalog.isEmpty()) return emptyList()

    val movieById = movies.associateBy(ContentItem::id)
    val seriesById = series.associateBy(ContentItem::id)
    val seriesByName = series.associateBy { it.name.smartCollectionNormalized() }

    fun resolveHistoryItem(entry: HistoryEntry): ContentItem? = when (entry.streamKind.lowercase(Locale.ROOT)) {
        "movie" -> movieById[entry.streamId]
        "series" -> entry.parentContentId?.let(seriesById::get)
            ?: entry.seriesTitle?.smartCollectionNormalized()?.let(seriesByName::get)
            ?: entry.title.substringBefore("·").smartCollectionNormalized().let(seriesByName::get)
        else -> null
    }

    val recentHistory = history
        .sortedByDescending(HistoryEntry::updatedAtEpochMs)
        .asSequence()
        .filterNot { it.isLive }
        .mapNotNull(::resolveHistoryItem)
        .distinctBy { it.smartCollectionKey() }
        .take(40)
        .toList()
    val favoriteSeeds = catalog.filter { it.smartCollectionKey() in favorites }

    val categoryWeights = mutableMapOf<String, Int>()
    val genreWeights = mutableMapOf<String, Int>()
    val genreLabels = linkedMapOf<String, String>()

    fun addSignals(item: ContentItem, weight: Int) {
        categoryWeights[item.categoryId] = (categoryWeights[item.categoryId] ?: 0) + weight
        item.smartCollectionGenrePairs().forEach { (normalized, display) ->
            genreWeights[normalized] = (genreWeights[normalized] ?: 0) + weight
            genreLabels.putIfAbsent(normalized, display)
        }
    }

    recentHistory.forEachIndexed { index, item ->
        addSignals(item, (90 - index * 2).coerceAtLeast(18))
    }
    favoriteSeeds.forEach { item -> addSignals(item, 120) }

    val hasProfileSignals = categoryWeights.isNotEmpty() || genreWeights.isNotEmpty()
    val excludedKeys = buildSet {
        recentHistory.forEach { add(it.smartCollectionKey()) }
        addAll(favorites)
    }

    val freshnessByKey = catalog
        .sortedWith(
            compareByDescending<ContentItem> { it.addedAtEpochSeconds ?: 0L }
                .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenBy { it.smartCollectionKey() },
        )
        .mapIndexed { index, item -> item.smartCollectionKey() to (800 - index * 3).coerceAtLeast(0) }
        .toMap()

    fun profileScore(item: ContentItem): Int {
        val category = (categoryWeights[item.categoryId] ?: 0) * 150
        val genres = item.smartCollectionGenres().sumOf { genre -> (genreWeights[genre] ?: 0) * 70 }
        val rating = ((item.rating?.toDoubleOrNull() ?: 0.0) * 110.0).toInt()
        val freshness = freshnessByKey[item.smartCollectionKey()] ?: 0
        return category + genres + rating + freshness
    }

    fun ranked(type: ContentType): List<ContentItem> = catalog.asSequence()
        .filter { it.type == type }
        .filterNot { it.smartCollectionKey() in excludedKeys }
        .sortedWith(
            compareByDescending<ContentItem> { profileScore(it) }
                .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.addedAtEpochSeconds ?: 0L }
                .thenBy { it.smartCollectionKey() },
        )
        .take(limit)
        .toList()

    val result = mutableListOf<SmartCollection>()
    val movieCollection = ranked(ContentType.MOVIE)
    if (movieCollection.isNotEmpty()) {
        result += SmartCollection(
            key = "smart-movies",
            title = if (hasProfileSignals) "افلام مختارة لك" else "مختارات افلام",
            items = movieCollection,
            source = if (hasProfileSignals) SmartCollectionSource.PROFILE else SmartCollectionSource.CURATED,
        )
    }

    val seriesCollection = ranked(ContentType.SERIES)
    if (seriesCollection.isNotEmpty()) {
        result += SmartCollection(
            key = "smart-series",
            title = if (hasProfileSignals) "مسلسلات مختارة لك" else "مختارات مسلسلات",
            items = seriesCollection,
            source = if (hasProfileSignals) SmartCollectionSource.PROFILE else SmartCollectionSource.CURATED,
        )
    }

    val genreCandidates = if (genreWeights.isNotEmpty()) {
        genreWeights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    } else {
        val counts = mutableMapOf<String, Int>()
        catalog.forEach { item ->
            item.smartCollectionGenrePairs().forEach { (normalized, display) ->
                counts[normalized] = (counts[normalized] ?: 0) + 1
                genreLabels.putIfAbsent(normalized, display)
            }
        }
        counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    genreCandidates
        .asSequence()
        .mapNotNull { genre ->
            val items = catalog.asSequence()
                .filterNot { it.smartCollectionKey() in excludedKeys }
                .filter { genre in it.smartCollectionGenres() }
                .sortedWith(
                    compareByDescending<ContentItem> { profileScore(it) }
                        .thenByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
                        .thenByDescending { it.addedAtEpochSeconds ?: 0L }
                        .thenBy { it.smartCollectionKey() },
                )
                .take(limit)
                .toList()
            if (items.size < 4) {
                null
            } else {
                SmartCollection(
                    key = "genre-${genre.smartCollectionSlug()}",
                    title = "مختارات ${genreLabels[genre] ?: genre}",
                    items = items,
                    source = SmartCollectionSource.GENRE,
                )
            }
        }
        .distinctBy(SmartCollection::key)
        .take(genreLimit)
        .forEach(result::add)

    return result
}

private fun ContentItem.smartCollectionKey(): String = "${type.name}:$id"

private fun ContentItem.smartCollectionGenres(): List<String> =
    smartCollectionGenrePairs().map { it.first }

private fun ContentItem.smartCollectionGenrePairs(): List<Pair<String, String>> =
    genre.orEmpty()
        .split(',', '/', '|', ';', '•')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { display -> display.smartCollectionNormalized() to display }
        .filter { (normalized, _) -> normalized.isNotBlank() }
        .distinctBy { it.first }
        .toList()

private fun String.smartCollectionNormalized(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

private fun String.smartCollectionSlug(): String =
    smartCollectionNormalized()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        .trim('-')
        .ifBlank { "collection" }

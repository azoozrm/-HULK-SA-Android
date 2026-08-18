package sa.hulksa.player.ui

import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.screens.HulkAiEvidenceType
import sa.hulksa.player.ui.screens.buildHulkAiSuggestions
import java.util.Locale

internal enum class HulkAiQuerySignalType {
    CONTENT_TYPE,
    GENRE,
    YEAR,
    HIGH_RATING,
    RECENT,
    TEXT_MATCH,
    PROFILE,
}

internal data class HulkAiQuerySignal(
    val type: HulkAiQuerySignalType,
    val label: String,
)

internal data class HulkAiQuerySuggestion(
    val item: ContentItem,
    val score: Int,
    val signals: List<HulkAiQuerySignal>,
)

internal data class HulkAiQueryResult(
    val suggestions: List<HulkAiQuerySuggestion>,
    val understoodLabels: List<String>,
    val exactConstraintMatch: Boolean,
    val hasProfileSignals: Boolean,
)

private data class HulkAiParsedQuery(
    val contentType: ContentType?,
    val genres: Set<String>,
    val year: Int?,
    val wantsHighRating: Boolean,
    val wantsRecent: Boolean,
    val freeTokens: List<String>,
)

private val HULK_AI_TRIGGERS = listOf(
    "رشح لي",
    "رشحلي",
    "رشح",
    "اقترح لي",
    "اقترح",
    "ابي",
    "ابغى",
    "اريد",
    "هولك ai",
    "هولك اي",
    "hulk ai",
)

private val HULK_AI_STOP_WORDS = setOf(
    "لي", "ابي", "ابغى", "اريد", "رشح", "اقترح", "هولك", "ai", "اي",
    "فيلم", "افلام", "مسلسل", "مسلسلات", "محتوى", "شي", "شيء",
    "عن", "من", "في", "على", "مع", "يكون", "مره", "مرة",
    "جديد", "جديده", "حديث", "حديثه", "احدث", "الاحدث",
    "افضل", "الافضل", "ممتاز", "قوي", "عالي", "التقييم", "تقييم",
)

private val HULK_AI_GENRES = linkedMapOf(
    "action" to GenreAliases("اكشن", setOf("action", "اكشن")),
    "drama" to GenreAliases("دراما", setOf("drama", "دراما")),
    "comedy" to GenreAliases("كوميديا", setOf("comedy", "كوميدي", "كوميديا")),
    "horror" to GenreAliases("رعب", setOf("horror", "رعب", "مرعب")),
    "thriller" to GenreAliases("اثارة", setOf("thriller", "اثاره", "اثارة", "تشويق")),
    "crime" to GenreAliases("جريمة", setOf("crime", "جريمه", "جريمة", "جرائم")),
    "romance" to GenreAliases("رومانسي", setOf("romance", "romantic", "رومانسي", "رومانسيه", "رومانسية")),
    "sci-fi" to GenreAliases(
        "خيال علمي",
        setOf("sci-fi", "sci fi", "science fiction", "خيال علمي", "خيال"),
    ),
    "animation" to GenreAliases(
        "انيميشن",
        setOf("animation", "animated", "انيميشن", "انميشن", "رسوم متحركه", "رسوم متحركة", "كرتون"),
    ),
    "family" to GenreAliases("عائلي", setOf("family", "عائلي", "عائليه", "عائلية", "عائله", "عائلة")),
    "documentary" to GenreAliases("وثائقي", setOf("documentary", "وثائقي", "وثائقيه", "وثائقية")),
    "fantasy" to GenreAliases("فانتازيا", setOf("fantasy", "فانتازيا")),
    "adventure" to GenreAliases("مغامرات", setOf("adventure", "مغامره", "مغامرة", "مغامرات")),
    "mystery" to GenreAliases("غموض", setOf("mystery", "غموض")),
    "war" to GenreAliases("حربي", setOf("war", "حرب", "حربي")),
    "history" to GenreAliases("تاريخي", setOf("history", "historical", "تاريخ", "تاريخي")),
    "western" to GenreAliases("ويسترن", setOf("western", "ويسترن")),
)

private data class GenreAliases(
    val label: String,
    val aliases: Set<String>,
)

internal fun isHulkAiRequest(rawQuery: String): Boolean {
    val normalized = rawQuery.hulkAiNormalizeText()
    if (normalized.isBlank()) return false
    return HULK_AI_TRIGGERS.any { trigger ->
        normalized == trigger || normalized.startsWith("$trigger ")
    }
}

internal fun buildHulkAiQuerySuggestions(
    rawQuery: String,
    movies: List<ContentItem>,
    series: List<ContentItem>,
    history: List<HistoryEntry>,
    favorites: Set<String>,
    limit: Int = 24,
): HulkAiQueryResult {
    if (limit <= 0) return HulkAiQueryResult(emptyList(), emptyList(), true, false)

    val catalog = (movies + series)
        .filter { it.type == ContentType.MOVIE || it.type == ContentType.SERIES }
        .distinctBy(ContentItem::hulkAiQueryKey)
    if (catalog.isEmpty()) return HulkAiQueryResult(emptyList(), emptyList(), true, false)

    val parsed = parseHulkAiQuery(rawQuery)
    val foundation = buildHulkAiSuggestions(
        movies = movies,
        series = series,
        history = history,
        favorites = favorites,
        limit = minOf(catalog.size, 400),
    )
    val foundationByKey = foundation.suggestions.associateBy { it.item.hulkAiQueryKey() }

    val byKey = catalog.associateBy(ContentItem::hulkAiQueryKey)
    val seriesByName = series
        .groupBy { it.name.hulkAiNormalizeText() }
        .mapValues { (_, values) -> values.first() }
    val resolvedHistoryItems = history
        .asSequence()
        .filterNot(HistoryEntry::isLive)
        .sortedByDescending(HistoryEntry::updatedAtEpochMs)
        .take(60)
        .mapNotNull { entry ->
            when (entry.streamKind.lowercase(Locale.ROOT)) {
                "movie" -> byKey["MOVIE:${entry.streamId}"]
                "series" -> entry.parentContentId
                    ?.let { byKey["SERIES:$it"] }
                    ?: entry.seriesTitle
                        ?.hulkAiNormalizeText()
                        ?.let(seriesByName::get)
                else -> null
            }
        }
        .toList()
    val excludedKeys = linkedSetOf<String>().apply {
        addAll(favorites)
        resolvedHistoryItems.mapTo(this) { it.hulkAiQueryKey() }
    }

    var constrained = parsed.contentType
        ?.let { type -> catalog.filter { it.type == type } }
        ?: catalog
    if (constrained.isEmpty()) {
        return HulkAiQueryResult(
            suggestions = emptyList(),
            understoodLabels = understoodLabels(parsed),
            exactConstraintMatch = false,
            hasProfileSignals = foundation.hasProfileSignals,
        )
    }

    var exactConstraintMatch = true

    if (parsed.genres.isNotEmpty()) {
        val genreMatches = constrained.filter { item ->
            item.hulkAiCanonicalGenres().any(parsed.genres::contains)
        }
        if (genreMatches.isNotEmpty()) {
            constrained = genreMatches
        } else {
            exactConstraintMatch = false
        }
    }

    if (parsed.year != null) {
        val yearMatches = constrained.filter { it.year?.toIntOrNull() == parsed.year }
        if (yearMatches.isNotEmpty()) {
            constrained = yearMatches
        } else {
            exactConstraintMatch = false
        }
    }

    if (parsed.freeTokens.isNotEmpty()) {
        val textMatches = constrained.filter { item ->
            val searchable = item.hulkAiSearchableText()
            parsed.freeTokens.any(searchable::contains)
        }
        if (textMatches.isNotEmpty()) {
            constrained = textMatches
        } else if (parsed.genres.isEmpty() && parsed.year == null) {
            exactConstraintMatch = false
        }
    }

    val discoveryPool = constrained.filterNot { it.hulkAiQueryKey() in excludedKeys }
    if (discoveryPool.isNotEmpty()) constrained = discoveryPool

    val freshnessRank = catalog
        .sortedWith(
            compareByDescending<ContentItem> { it.addedAtEpochSeconds ?: 0L }
                .thenBy { it.hulkAiQueryKey() },
        )
        .mapIndexed { index, item -> item.hulkAiQueryKey() to index }
        .toMap()

    val ranked = constrained.map { item ->
        val key = item.hulkAiQueryKey()
        val foundationSuggestion = foundationByKey[key]
        val itemGenres = item.hulkAiCanonicalGenres()
        val matchedGenres = itemGenres.filter(parsed.genres::contains)
        val searchable = item.hulkAiSearchableText()
        val matchedTokens = parsed.freeTokens.filter(searchable::contains)
        val rating = item.rating?.toDoubleOrNull()?.coerceIn(0.0, 10.0)
        val freshRank = freshnessRank[key] ?: catalog.size
        val signals = mutableListOf<HulkAiQuerySignal>()

        parsed.contentType?.let {
            signals += HulkAiQuerySignal(
                HulkAiQuerySignalType.CONTENT_TYPE,
                if (it == ContentType.MOVIE) "فيلم" else "مسلسل",
            )
        }
        matchedGenres.forEach { genre ->
            signals += HulkAiQuerySignal(
                HulkAiQuerySignalType.GENRE,
                HULK_AI_GENRES[genre]?.label ?: genre,
            )
        }
        if (parsed.year != null && item.year?.toIntOrNull() == parsed.year) {
            signals += HulkAiQuerySignal(HulkAiQuerySignalType.YEAR, parsed.year.toString())
        }
        if (parsed.wantsHighRating && rating != null && rating >= 7.0) {
            signals += HulkAiQuerySignal(
                HulkAiQuerySignalType.HIGH_RATING,
                "تقييم ${item.rating}",
            )
        }
        if (parsed.wantsRecent && freshRank < 40) {
            signals += HulkAiQuerySignal(HulkAiQuerySignalType.RECENT, "اضافة حديثة")
        }
        matchedTokens.take(2).forEach { token ->
            signals += HulkAiQuerySignal(HulkAiQuerySignalType.TEXT_MATCH, token)
        }
        if (
            foundationSuggestion?.evidence?.any {
                it.type == HulkAiEvidenceType.FAVORITE_GENRE ||
                    it.type == HulkAiEvidenceType.RECENT_GENRE ||
                    it.type == HulkAiEvidenceType.FAVORITE_CATEGORY ||
                    it.type == HulkAiEvidenceType.RECENT_CATEGORY
            } == true
        ) {
            signals += HulkAiQuerySignal(HulkAiQuerySignalType.PROFILE, "حسب تفضيلاتك")
        }

        var score = (foundationSuggestion?.score ?: 0) / 2
        if (parsed.contentType != null) score += 2_500
        score += matchedGenres.size * 6_000
        if (parsed.year != null && item.year?.toIntOrNull() == parsed.year) score += 3_500
        score += matchedTokens.size * 1_400

        val normalizedRequest = stripHulkAiTrigger(rawQuery).hulkAiNormalizeText()
        if (normalizedRequest.length >= 3 && item.hulkAiSearchableText().contains(normalizedRequest)) {
            score += 1_600
        }

        if (parsed.wantsHighRating) {
            score += ((rating ?: 0.0) * 430.0).toInt()
            if (rating != null && rating >= 8.0) score += 1_600
        } else {
            score += ((rating ?: 0.0) * 70.0).toInt()
        }

        score += if (parsed.wantsRecent) {
            (2_600 - freshRank * 24).coerceAtLeast(0)
        } else {
            (500 - freshRank * 5).coerceAtLeast(0)
        }

        if (!item.posterUrl.isNullOrBlank()) score += 100
        if (!item.backdropUrl.isNullOrBlank()) score += 120
        if (!item.plot.isNullOrBlank()) score += 60

        HulkAiQuerySuggestion(
            item = item,
            score = score,
            signals = signals.distinct(),
        )
    }.sortedWith(
        compareByDescending<HulkAiQuerySuggestion> { it.score }
            .thenByDescending { it.item.rating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { it.item.addedAtEpochSeconds ?: 0L }
            .thenBy { it.item.hulkAiQueryKey() },
    )

    return HulkAiQueryResult(
        suggestions = balanceQueryTypes(ranked, parsed.contentType, limit),
        understoodLabels = understoodLabels(parsed),
        exactConstraintMatch = exactConstraintMatch,
        hasProfileSignals = foundation.hasProfileSignals,
    )
}

private fun balanceQueryTypes(
    ranked: List<HulkAiQuerySuggestion>,
    requestedType: ContentType?,
    limit: Int,
): List<HulkAiQuerySuggestion> {
    if (requestedType != null || limit < 6) return ranked.take(limit)

    val movies = ranked.filter { it.item.type == ContentType.MOVIE }
    val series = ranked.filter { it.item.type == ContentType.SERIES }
    if (movies.size < 2 || series.size < 2) return ranked.take(limit)

    val selected = mutableListOf<HulkAiQuerySuggestion>()
    val keys = linkedSetOf<String>()
    fun add(value: HulkAiQuerySuggestion) {
        if (selected.size < limit && keys.add(value.item.hulkAiQueryKey())) selected += value
    }

    movies.take(2).forEach(::add)
    series.take(2).forEach(::add)
    ranked.forEach(::add)

    val rankByKey = ranked
        .mapIndexed { index, suggestion -> suggestion.item.hulkAiQueryKey() to index }
        .toMap()
    return selected
        .sortedBy { rankByKey[it.item.hulkAiQueryKey()] ?: Int.MAX_VALUE }
        .take(limit)
}

private fun parseHulkAiQuery(rawQuery: String): HulkAiParsedQuery {
    val normalized = stripHulkAiTrigger(rawQuery).hulkAiNormalizeText()
    val contentType = when {
        containsAnyPhrase(normalized, setOf("مسلسل", "مسلسلات", "series", "show")) -> ContentType.SERIES
        containsAnyPhrase(normalized, setOf("فيلم", "افلام", "movie", "movies")) -> ContentType.MOVIE
        else -> null
    }
    val genres = HULK_AI_GENRES
        .filterValues { genre -> genre.aliases.any { alias -> normalized.containsAlias(alias) } }
        .keys
        .toSet()
    val year = Regex("""\b(?:19|20)\d{2}\b""")
        .find(normalized)
        ?.value
        ?.toIntOrNull()
    val wantsHighRating = containsAnyPhrase(
        normalized,
        setOf(
            "افضل", "الافضل", "ممتاز", "قوي", "عالي التقييم", "اعلى تقييم",
            "تقييم عالي", "best", "top rated", "high rated",
        ),
    )
    val wantsRecent = containsAnyPhrase(
        normalized,
        setOf("جديد", "جديده", "حديث", "حديثه", "احدث", "الاحدث", "new", "latest", "recent"),
    )

    val aliasTokens = HULK_AI_GENRES.values
        .flatMap { it.aliases }
        .flatMap { it.hulkAiNormalizeText().split(' ') }
        .filter(String::isNotBlank)
        .toSet()

    val freeTokens = normalized
        .replace(Regex("""[^\p{L}\p{N}\s-]"""), " ")
        .split(Regex("""\s+"""))
        .map(String::trim)
        .filter { token ->
            token.length >= 2 &&
                token !in HULK_AI_STOP_WORDS &&
                token !in aliasTokens &&
                (year == null || token.toIntOrNull() != year)
        }
        .distinct()
        .take(5)

    return HulkAiParsedQuery(
        contentType = contentType,
        genres = genres,
        year = year,
        wantsHighRating = wantsHighRating,
        wantsRecent = wantsRecent,
        freeTokens = freeTokens,
    )
}

private fun understoodLabels(query: HulkAiParsedQuery): List<String> = buildList {
    when (query.contentType) {
        ContentType.MOVIE -> add("افلام")
        ContentType.SERIES -> add("مسلسلات")
        else -> Unit
    }
    query.genres.forEach { genre ->
        add(HULK_AI_GENRES[genre]?.label ?: genre)
    }
    query.year?.let { add(it.toString()) }
    if (query.wantsHighRating) add("تقييم مرتفع")
    if (query.wantsRecent) add("احدث الاضافات")
    query.freeTokens.take(2).forEach(::add)
}.distinct()

private fun stripHulkAiTrigger(rawQuery: String): String {
    val trimmed = rawQuery.trim()
    val normalized = trimmed.hulkAiNormalizeText()
    val trigger = HULK_AI_TRIGGERS
        .sortedByDescending(String::length)
        .firstOrNull { normalized == it || normalized.startsWith("$it ") }
        ?: return trimmed

    val triggerTokenCount = trigger.split(' ').size
    val originalTokens = trimmed.split(Regex("""\s+"""))
    return if (originalTokens.size <= triggerTokenCount) {
        ""
    } else {
        originalTokens.drop(triggerTokenCount).joinToString(" ")
    }
}

private fun ContentItem.hulkAiCanonicalGenres(): Set<String> {
    val rawGenres = genre
        .orEmpty()
        .split(',', '،', '/', '|', ';', '•')
        .map(String::trim)
        .filter(String::isNotBlank)
    return HULK_AI_GENRES.mapNotNullTo(linkedSetOf()) { (canonical, aliases) ->
        if (
            rawGenres.any { raw ->
                val normalized = raw.hulkAiNormalizeText()
                aliases.aliases.any { alias -> normalized.containsAlias(alias) }
            }
        ) {
            canonical
        } else {
            null
        }
    }
}

private fun ContentItem.hulkAiSearchableText(): String =
    sequenceOf(name, year, genre, plot)
        .filterNotNull()
        .joinToString(" ")
        .hulkAiNormalizeText()

private fun ContentItem.hulkAiQueryKey(): String = "${type.name}:$id"

private fun String.hulkAiNormalizeText(): String =
    lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("""[\u064B-\u065F\u0670]"""), "")
        .trim()
        .replace(Regex("""\s+"""), " ")

private fun String.containsAlias(alias: String): Boolean {
    val normalizedAlias = alias.hulkAiNormalizeText()
    if (normalizedAlias.isBlank()) return false
    return this == normalizedAlias ||
        this.startsWith("$normalizedAlias ") ||
        this.endsWith(" $normalizedAlias") ||
        this.contains(" $normalizedAlias ") ||
        this.contains(normalizedAlias)
}

private fun containsAnyPhrase(value: String, phrases: Set<String>): Boolean =
    phrases.any { value.containsAlias(it) }

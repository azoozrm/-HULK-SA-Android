package sa.hulksa.player.ui.screens

import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import java.text.Normalizer
import java.util.Locale

internal data class ArabicCommentaryCatalogSource(
    val item: ContentItem,
    val categoryName: String?,
) {
    val key: String = "${item.id}|${item.categoryId}|${item.name}|${item.containerExtension.orEmpty()}"
}

internal enum class ArabicCommentaryFeedFamily { SPORTS, MAX, XTRA, EXTRA, PREMIUM }

internal data class ArabicCommentaryFeedKey(
    val family: ArabicCommentaryFeedFamily,
    val number: Int?,
    val qualifier: String,
) {
    val label: String = listOfNotNull(
        "beIN SPORTS",
        family.name.takeUnless { family == ArabicCommentaryFeedFamily.SPORTS },
        number?.toString(),
        qualifier.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT),
    ).joinToString(" ")
    val rowKey: String = "${family.name}|$number|$qualifier"
}

internal data class ArabicCommentaryFeed(
    val key: ArabicCommentaryFeedKey,
    val sources: List<ArabicCommentaryCatalogSource>,
) {
    private val searchTerms = (listOf(key.label) + sources.flatMap { source ->
        listOfNotNull(source.item.name, source.categoryName)
    }).map(::commentaryName)

    fun matches(words: List<String>): Boolean = words.all { word ->
        searchTerms.any { it.contains(word) }
    }
}

private val footballInSports = Regex("sp⚽\\uFE0F?rts?", RegexOption.IGNORE_CASE)
private val beInBrand = Regex("""\b(?:be[\W_]*in|بي[\W_]*ان)(?=sports?|[0-9]|\b)""")
private val adjacentNumberMarker = Regex(
    """\b(sports?|max|xtra|extra|premium|hevc|fhd|uhd|hd|sd)(?=[0-9])|(?<=[0-9])(hevc|xtra|extra|max|fhd|uhd|hd|sd)\b""",
)
private val nameWords = Regex("[\\p{L}\\p{N}]+")
private val qualityPhrases = Regex("""\b(full hd|ultra hd|h[ .]26[45]|\d+\s*fps)\b""")
private val standardDefinition = setOf("lq", "sd", "480p", "576p")
private val highDefinition = setOf("hd", "720p", "avc", "h264")
private val fullDefinitionOrHevc = setOf("fhd", "1080p", "hevc", "h265")
private val ultraHighDefinition = setOf("uhd", "4k", "8k", "2160p", "4320p")
private val qualityWords = standardDefinition + highDefinition + fullDefinitionOrHevc + ultraHighDefinition
private val namedFamilies = ArabicCommentaryFeedFamily.entries.drop(1).associateBy { it.name.lowercase(Locale.ROOT) }
private val sportsWords = setOf("sport", "sports", "سبورت", "سبورتس", "الرياضية", "رياضة")
private val arabicWords = setOf("ar", "ara", "arab", "arabic", "mena", "عربي", "عربية", "العربي", "العربية")
private val foreignWords = setOf(
    "en", "eng", "english", "uk", "us", "usa", "american", "ca", "canada", "canadian",
    "fr", "french", "france", "francais", "français", "es", "esp", "spanish", "espanol", "español",
    "tr", "turkish", "turkey", "türkiye", "turkce", "türkçe", "au", "australia", "australian", "aus", "nz",
    "id", "indonesia", "malaysia", "sg", "singapore", "th", "thailand", "thai", "philippines", "hongkong",
    "de", "german", "germany", "deutsch", "it", "italian", "italy", "pl", "polish", "poland",
    "nl", "dutch", "netherlands", "pt", "portuguese", "portugal", "br", "brazil", "greek", "greece",
    "russian", "russia", "chinese", "china", "japanese", "japan", "korean", "korea",
    "انجليزي", "انجليزية", "الانجليزية", "فرنسي", "فرنسية", "فرنسا", "اسباني", "تركي", "تركية",
)
private val nonSportWords = setOf(
    "news", "global", "movie", "movies", "cinema", "series", "kids", "gourmet", "drama",
    "اخبار", "الاخبار", "اخبارية", "الاخبارية", "افلام", "مسلسلات", "اطفال",
)

private fun commentaryName(raw: String): String {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(footballInSports, "sports")
    val plain = buildString(normalized.length) {
        normalized.forEach { character ->
            when (character) {
                'أ', 'إ', 'آ' -> append('ا')
                'ᴴ' -> append('h')
                'ᴰ' -> append('d')
                '+' -> append(" plus ")
                '&' -> append(" and ")
                else -> when {
                    character.category == CharCategory.FORMAT ||
                        character.category == CharCategory.NON_SPACING_MARK -> Unit
                    character.isDigit() -> append(character.digitToInt())
                    else -> append(character)
                }
            }
        }
    }
    val split = plain.replace(beInBrand, "bein ")
        .replace(adjacentNumberMarker) { " ${it.value} " }
    return nameWords.findAll(split).joinToString(" ") { it.value }
}

internal fun arabicCommentarySearchWords(query: String): List<String> =
    commentaryName(query).split(' ').filter(String::isNotEmpty)

private data class ClassifiedCommentarySource(
    val feed: ArabicCommentaryFeedKey,
    val source: ArabicCommentaryCatalogSource,
    val qualityRank: Int,
)

private fun classifySource(
    source: ArabicCommentaryCatalogSource,
    categoryWords: List<String>,
): ClassifiedCommentarySource? {
    val name = commentaryName(source.item.name)
    val words = name.split(' ').filter(String::isNotEmpty)
    if (words.any { it in foreignWords || it in nonSportWords }) return null
    if (words.none(arabicWords::contains) && categoryWords.any(foreignWords::contains) &&
        categoryWords.none(arabicWords::contains)
    ) return null

    val branded = "bein" in words
    val brandCategory = "bein" in categoryWords && categoryWords.none(nonSportWords::contains)
    if (!branded && !brandCategory) return null
    val hasSportsName = words.any(sportsWords::contains)
    val sportsCategory = categoryWords.any(sportsWords::contains)
    val formatNormalized = name.replace(qualityPhrases) {
        when (it.value) {
            "full hd" -> "fhd"
            "ultra hd" -> "uhd"
            "h 264" -> "h264"
            "h 265" -> "h265"
            else -> " "
        }
    }.split(' ')
    val identity = formatNormalized.filter { word ->
        word.isNotEmpty() && word != "bein" && word !in sportsWords &&
            word !in qualityWords && word !in arabicWords
    }
    val families = identity.mapNotNull(namedFamilies::get).distinct()
    val family = families.singleOrNull() ?: ArabicCommentaryFeedFamily.SPORTS
    val identityWords = identity.toMutableList()
    if (families.size == 1) identityWords.remove(family.name.lowercase(Locale.ROOT))
    val numberIndex = identityWords.indexOfFirst { it.toIntOrNull() != null }
    val number = if (numberIndex >= 0) identityWords.removeAt(numberIndex).toInt() else null
    if (!hasSportsName && !sportsCategory && categoryWords.any(nonSportWords::contains)) return null
    if (!hasSportsName && family == ArabicCommentaryFeedFamily.SPORTS && number == null &&
        !(branded && (brandCategory || sportsCategory) && identity.isEmpty())
    ) return null
    // A branded group can identify shorthand channel names, not arbitrary rows in that group.
    if (!branded && (identityWords.isNotEmpty() || !hasSportsName && number == null && families.isEmpty())) {
        return null
    }
    // A bare "beIN SPORTS 4K" can be its own feed. Strip format markers only when the name
    // identifies another channel/variant. Keep other qualifiers, including provider region tags.
    val qualifier = if (number == null && families.isEmpty() && identityWords.isEmpty()) {
        formatNormalized.filter(qualityWords::contains).joinToString(" ")
    } else {
        identityWords.joinToString(" ")
    }
    val formats = formatNormalized.filter(qualityWords::contains).ifEmpty { categoryWords }
    val qualityRank = when {
        formats.any(ultraHighDefinition::contains) -> 4
        formats.any(fullDefinitionOrHevc::contains) -> 3
        formats.any(highDefinition::contains) -> 1
        formats.any(standardDefinition::contains) -> 0
        else -> 2
    }
    return ClassifiedCommentarySource(ArabicCommentaryFeedKey(family, number, qualifier), source, qualityRank)
}

/** A projection of the account catalog. Original stream identifiers and names remain intact. */
internal fun arabicCommentaryFeeds(catalog: Catalog?, currentStreamId: Int): List<ArabicCommentaryFeed> {
    if (catalog == null) return emptyList()
    val categories = catalog.categories.filter { it.type == ContentType.LIVE }.associateBy { it.id }
    val categoryWords = categories.mapValues { (_, category) -> arabicCommentarySearchWords(category.name) }
    return catalog.items.asSequence()
        .filter { it.type == ContentType.LIVE && it.id != currentStreamId }
        .mapNotNull { item ->
            classifySource(
                ArabicCommentaryCatalogSource(item, categories[item.categoryId]?.name),
                categoryWords[item.categoryId].orEmpty(),
            )
        }
        .groupBy { it.feed }
        .map { (feed, variants) ->
            val sources = variants.sortedBy { it.qualityRank }.map { it.source }.distinctBy { it.key }
            ArabicCommentaryFeed(feed, sources)
        }
        .sortedWith(compareBy({ it.key.family }, { it.key.number ?: Int.MAX_VALUE }, { it.key.qualifier }))
}

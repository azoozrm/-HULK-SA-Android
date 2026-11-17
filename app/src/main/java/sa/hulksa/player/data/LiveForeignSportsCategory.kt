package sa.hulksa.player.data

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.Category
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

/** Stable app-only Live category ID; never a provider category ID. */
internal const val VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID = "virtual-foreign-sports"
internal const val VIRTUAL_FOREIGN_SPORTS_CATEGORY_NAME = "قنوات اجنبية رياضية"

internal const val MAX_LIVE_FOREIGN_SPORTS_ALIASES = 400

/**
 * Optional Operations override. It is intentionally a list of verified channel aliases only:
 * no hostname, port, URL, source identity or stream IDs. Matching happens against the active
 * account's already loaded Live catalog, so moving to another portal or stream ID keeps working.
 */
data class OperationsLiveForeignSportsConfig(
    val channelAliases: List<String>,
)

/** Resolved presentation state for the active catalog. */
data class LiveForeignSportsCategory(
    val category: Category,
    val channelIds: List<Int>,
)

private val SUPERSCRIPT_TO_PLAIN = mapOf(
    'ᵃ' to 'a', 'ᵇ' to 'b', 'ᶜ' to 'c', 'ᵈ' to 'd', 'ᵉ' to 'e', 'ᶠ' to 'f',
    'ᵍ' to 'g', 'ʰ' to 'h', 'ⁱ' to 'i', 'ʲ' to 'j', 'ᵏ' to 'k', 'ˡ' to 'l',
    'ᵐ' to 'm', 'ⁿ' to 'n', 'ᵒ' to 'o', 'ᵖ' to 'p', 'ʳ' to 'r', 'ˢ' to 's',
    'ᵗ' to 't', 'ᵘ' to 'u', 'ᵛ' to 'v', 'ʷ' to 'w', 'ˣ' to 'x', 'ʸ' to 'y',
    'ᶻ' to 'z',
    'ᴬ' to 'A', 'ᴮ' to 'B', 'ᶜ' to 'C', 'ᴰ' to 'D', 'ᴱ' to 'E', 'ᶠ' to 'F',
    'ᴳ' to 'G', 'ᴴ' to 'H', 'ᴵ' to 'I', 'ᴶ' to 'J', 'ᴷ' to 'K', 'ᴸ' to 'L',
    'ᴹ' to 'M', 'ᴺ' to 'N', 'ᴼ' to 'O', 'ᴾ' to 'P', 'ᴿ' to 'R', 'ˢ' to 'S',
    'ᵀ' to 'T', 'ᵁ' to 'U', 'ⱽ' to 'V', 'ᵂ' to 'W', 'ˣ' to 'X', 'ʸ' to 'Y',
    'ᶻ' to 'Z',
    '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
    '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
    '₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4',
    '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8', '₉' to '9',
)

private val DECORATIVE_SYMBOLS = setOf(
    '◉', '⚽', '⭐', '▶', '☪', '☾', '✟', '❆', '∎', '⎋', '▼', '●', '•', '✯', '☽',
)

/**
 * Exact-alias normalization: case, width, superscript letters/digits, decorative glyphs and
 * whitespace are normalized, while channel numbers and feed/quality words stay significant.
 * `+` becomes `plus` and `&` becomes `and` so distinct feeds do not collapse to the same alias.
 */
internal fun normalizeLiveForeignSportsAlias(raw: String): String {
    val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    val normalized = buildString(nfkc.length) {
        nfkc.forEach { character ->
            when {
                character == '\uFE0F' -> Unit
                character in DECORATIVE_SYMBOLS -> Unit
                character == '+' -> append(" plus ")
                character == '&' -> append(" and ")
                character in SUPERSCRIPT_TO_PLAIN -> append(SUPERSCRIPT_TO_PLAIN.getValue(character))
                else -> append(character)
            }
        }
    }
    return normalized
        .lowercase(Locale.ROOT)
        .replace(Regex("[^0-9a-z\\u0600-\\u06FF]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

/**
 * Matches verified aliases against the active Live catalog. The returned IDs are the actual
 * matched catalog item IDs, so playback, favorites and history use the original provider record.
 * A name match never depends on host, port, URL, or a previously recorded stream ID.
 */
internal fun resolveLiveForeignSportsCategory(
    aliases: List<String>?,
    catalog: Catalog?,
): LiveForeignSportsCategory? {
    if (aliases == null || catalog == null) return null
    val normalizedAliases = aliases
        .asSequence()
        .map(::normalizeLiveForeignSportsAlias)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
    if (normalizedAliases.isEmpty()) return null
    val aliasOrder = normalizedAliases.withIndex().associate { it.value to it.index }
    val matched = catalog.items.withIndex().mapNotNull { (position, item) ->
        if (item.type != ContentType.LIVE) return@mapNotNull null
        val aliasIndex = aliasOrder[normalizeLiveForeignSportsAlias(item.name)] ?: return@mapNotNull null
        Triple(aliasIndex, position, item)
    }.sortedWith(compareBy<Triple<Int, Int, ContentItem>>({ it.first }, { it.second }))
    val ordered = matched.map { it.third }.distinctBy(ContentItem::id)
    if (ordered.isEmpty()) return null
    return LiveForeignSportsCategory(
        category = Category(
            id = VIRTUAL_FOREIGN_SPORTS_CATEGORY_ID,
            name = VIRTUAL_FOREIGN_SPORTS_CATEGORY_NAME,
            type = ContentType.LIVE,
        ),
        channelIds = ordered.map(ContentItem::id),
    )
}

/**
 * Active aliases: a valid Operations override wins (including an explicit empty hide); otherwise
 * the last valid override for this install; otherwise the bundled verified list.
 */
internal fun liveForeignSportsAliasesFor(
    override: OperationsLiveForeignSportsConfig?,
    lastValidOverride: List<String>?,
): List<String> = override?.channelAliases ?: lastValidOverride ?: BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES

internal fun encodeLiveForeignSportsLastValid(aliases: List<String>): String =
    JSONArray().apply {
        aliases.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_LIVE_FOREIGN_SPORTS_ALIASES)
            .forEach(::put)
    }.toString()

internal fun decodeLiveForeignSportsLastValid(raw: String?): List<String>? = runCatching {
    if (raw == null) return null
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }.distinct().take(MAX_LIVE_FOREIGN_SPORTS_ALIASES)
}.getOrNull()

package sa.hulksa.player.data

import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

internal data class XtreamJsonLimit(
    val maxResponseBytes: Long,
    val maxUniqueItems: Int? = null,
)

/**
 * Hard limits for untrusted Xtream/player_api.php JSON.
 *
 * Authentication and category payloads are naturally small. Full catalogs are the largest valid
 * responses, so they receive a materially larger byte/item budget. Series details receive a larger
 * detail budget than VOD because they can contain nested seasons/episodes. Error bodies are only
 * sampled for classification and therefore use a small independent ceiling.
 */
internal object XtreamJsonLimits {
    private const val MIB = 1024L * 1024L

    val AUTH = XtreamJsonLimit(maxResponseBytes = 1L * MIB)
    val CATEGORIES = XtreamJsonLimit(maxResponseBytes = 2L * MIB, maxUniqueItems = 2_000)
    val CATALOG = XtreamJsonLimit(maxResponseBytes = 32L * MIB, maxUniqueItems = 75_000)
    val VOD_INFO = XtreamJsonLimit(maxResponseBytes = 4L * MIB)
    val SERIES_INFO = XtreamJsonLimit(maxResponseBytes = 16L * MIB)
    val SHORT_EPG = XtreamJsonLimit(maxResponseBytes = 2L * MIB)
    val DEFAULT_OBJECT = XtreamJsonLimit(maxResponseBytes = 4L * MIB)

    const val MAX_SERIES_SEASONS = 250
    const val MAX_SERIES_EPISODES = 10_000
    const val MAX_SHORT_EPG_ITEMS = 256
    const val MAX_ERROR_BODY_BYTES = 64L * 1024L

    fun forAction(action: String?): XtreamJsonLimit = when (action) {
        null -> AUTH
        "get_live_categories", "get_vod_categories", "get_series_categories" -> CATEGORIES
        "get_live_streams", "get_vod_streams", "get_series" -> CATALOG
        "get_vod_info" -> VOD_INFO
        "get_series_info" -> SERIES_INFO
        "get_short_epg" -> SHORT_EPG
        else -> DEFAULT_OBJECT
    }
}

internal sealed class XtreamJsonGuardException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class PayloadTooLarge(
        val maxBytes: Long,
        val observedOrDeclaredBytes: Long,
    ) : XtreamJsonGuardException(
        "Xtream JSON payload exceeded $maxBytes bytes (observed/declared $observedOrDeclaredBytes)",
    )

    data object EmptyBody : XtreamJsonGuardException("Xtream JSON response body was empty")

    class InvalidJson(cause: Throwable? = null) :
        XtreamJsonGuardException("Xtream response was not valid JSON for the expected root type", cause)

    class TooManyItems(
        val scope: String,
        val maxItems: Int,
    ) : XtreamJsonGuardException("Xtream $scope exceeded $maxItems unique items")
}

internal object BoundedJsonResponseReader {
    private const val READ_CHUNK_BYTES = 8L * 1024L

    /**
     * Reads at most maxBytes + one sentinel byte before failing. OkHttp's transparent gzip handling
     * exposes the decompressed response body through source(), so this ceiling applies to the JSON
     * bytes that would otherwise be materialized as a String, not merely the compressed wire size.
     */
    fun readUtf8(
        body: ResponseBody?,
        maxBytes: Long,
        allowEmpty: Boolean = false,
    ): String {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        if (body == null) {
            if (allowEmpty) return ""
            throw XtreamJsonGuardException.EmptyBody
        }

        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            throw XtreamJsonGuardException.PayloadTooLarge(maxBytes, declaredLength)
        }

        val source = body.source()
        val buffer = Buffer()
        var totalBytes = 0L
        while (true) {
            val remaining = maxBytes - totalBytes
            val readLimit = minOf(READ_CHUNK_BYTES, remaining + 1L)
            val read = source.read(buffer, readLimit)
            if (read == -1L) break
            totalBytes += read
            if (totalBytes > maxBytes) {
                throw XtreamJsonGuardException.PayloadTooLarge(maxBytes, totalBytes)
            }
        }

        if (totalBytes == 0L) {
            if (allowEmpty) return ""
            throw XtreamJsonGuardException.EmptyBody
        }

        val text = buffer.readString(Charsets.UTF_8)
        if (!allowEmpty && text.isBlank()) throw XtreamJsonGuardException.EmptyBody
        return text
    }

    fun readResponse(response: Response, successLimit: XtreamJsonLimit): String = readUtf8(
        body = response.body,
        maxBytes = if (response.isSuccessful) {
            successLimit.maxResponseBytes
        } else {
            XtreamJsonLimits.MAX_ERROR_BODY_BYTES
        },
        allowEmpty = !response.isSuccessful,
    )
}

internal object XtreamJsonParser {
    fun parseObject(text: String): JSONObject {
        rejectObviousNonJson(text)
        return try {
            JSONObject(text)
        } catch (error: Exception) {
            throw XtreamJsonGuardException.InvalidJson(error)
        }
    }

    fun parseArray(text: String): JSONArray {
        rejectObviousNonJson(text)
        return try {
            JSONArray(text)
        } catch (error: Exception) {
            throw XtreamJsonGuardException.InvalidJson(error)
        }
    }

    private fun rejectObviousNonJson(text: String) {
        val first = text.firstOrNull { !it.isWhitespace() }
            ?: throw XtreamJsonGuardException.EmptyBody
        if (first == '<') throw XtreamJsonGuardException.InvalidJson()
    }
}

internal fun JSONArray.forEachUniqueObject(
    maxUniqueItems: Int,
    scope: String,
    keyOf: (JSONObject) -> String?,
    block: (JSONObject) -> Unit,
) {
    val seen = HashSet<String>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        val key = keyOf(item)?.trim()?.takeIf(String::isNotEmpty) ?: continue
        if (!seen.add(key)) continue
        if (seen.size > maxUniqueItems) {
            throw XtreamJsonGuardException.TooManyItems(scope, maxUniqueItems)
        }
        block(item)
    }
}

internal fun JSONArray.requireUniqueObjectLimit(
    maxUniqueItems: Int,
    scope: String,
    keyOf: (JSONObject) -> String?,
): JSONArray {
    forEachUniqueObject(maxUniqueItems, scope, keyOf) { }
    return this
}

internal data class XtreamSeriesEpisodeJson(
    val seasonKey: String,
    val indexInSeason: Int,
    val episode: JSONObject,
)

internal fun JSONObject.boundedSeriesEpisodeObjects(): List<XtreamSeriesEpisodeJson> {
    requireRootSeasonsLimit()
    val rawEpisodes = opt("episodes")
    return when (rawEpisodes) {
        is JSONObject -> collectEpisodeMap(rawEpisodes)
        is JSONArray -> collectFlatEpisodes(rawEpisodes)
        is String -> {
            val clean = rawEpisodes.trim()
            when {
                clean.startsWith("{") -> collectEpisodeMap(XtreamJsonParser.parseObject(clean))
                clean.startsWith("[") -> collectFlatEpisodes(XtreamJsonParser.parseArray(clean))
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
}

private fun JSONObject.requireRootSeasonsLimit() {
    val seasons = when (val rawSeasons = opt("seasons")) {
        is JSONArray -> rawSeasons
        is String -> rawSeasons.trim()
            .takeIf { it.startsWith("[") }
            ?.let(XtreamJsonParser::parseArray)
        else -> null
    } ?: return

    if (seasons.length() > XtreamJsonLimits.MAX_SERIES_SEASONS) {
        throw XtreamJsonGuardException.TooManyItems(
            scope = "series seasons",
            maxItems = XtreamJsonLimits.MAX_SERIES_SEASONS,
        )
    }
}

private fun collectEpisodeMap(episodes: JSONObject): List<XtreamSeriesEpisodeJson> {
    val result = ArrayList<XtreamSeriesEpisodeJson>()
    val seenEpisodeKeys = HashSet<String>()
    val keys = episodes.keys()
    var seasonCount = 0
    while (keys.hasNext()) {
        val seasonKey = keys.next()
        seasonCount += 1
        if (seasonCount > XtreamJsonLimits.MAX_SERIES_SEASONS) {
            throw XtreamJsonGuardException.TooManyItems(
                scope = "series seasons",
                maxItems = XtreamJsonLimits.MAX_SERIES_SEASONS,
            )
        }
        val entries = when (val value = episodes.opt(seasonKey)) {
            is JSONArray -> value
            is String -> value.trim().takeIf { it.startsWith("[") }
                ?.let(XtreamJsonParser::parseArray)
            else -> null
        } ?: continue
        appendEpisodes(result, seenEpisodeKeys, entries, seasonKey)
    }
    return result
}

private fun collectFlatEpisodes(entries: JSONArray): List<XtreamSeriesEpisodeJson> {
    val result = ArrayList<XtreamSeriesEpisodeJson>()
    val seenEpisodeKeys = HashSet<String>()
    val seenSeasons = HashSet<String>()
    for (index in 0 until entries.length()) {
        val episode = entries.optJSONObject(index) ?: continue
        val seasonKey = episode.optString("season").trim().takeIf(String::isNotEmpty)
            ?: episode.optString("season_number").trim().takeIf(String::isNotEmpty)
            ?: "1"
        if (seenSeasons.add(seasonKey) && seenSeasons.size > XtreamJsonLimits.MAX_SERIES_SEASONS) {
            throw XtreamJsonGuardException.TooManyItems(
                scope = "series seasons",
                maxItems = XtreamJsonLimits.MAX_SERIES_SEASONS,
            )
        }
        appendEpisode(result, seenEpisodeKeys, episode, seasonKey, index)
    }
    return result
}

private fun appendEpisodes(
    result: MutableList<XtreamSeriesEpisodeJson>,
    seenEpisodeKeys: MutableSet<String>,
    entries: JSONArray,
    seasonKey: String,
) {
    for (index in 0 until entries.length()) {
        val episode = entries.optJSONObject(index) ?: continue
        appendEpisode(result, seenEpisodeKeys, episode, seasonKey, index)
    }
}

private fun appendEpisode(
    result: MutableList<XtreamSeriesEpisodeJson>,
    seenEpisodeKeys: MutableSet<String>,
    episode: JSONObject,
    seasonKey: String,
    index: Int,
) {
    val rawId = episode.optString("id").trim().takeIf {
        it.isNotEmpty() && !it.equals("null", ignoreCase = true)
    }
    val normalizedId = rawId?.toIntOrNull()?.toString() ?: rawId
    val uniqueKey = normalizedId?.let { "id:$it" } ?: "position:$seasonKey:$index"
    if (!seenEpisodeKeys.add(uniqueKey)) return
    if (result.size >= XtreamJsonLimits.MAX_SERIES_EPISODES) {
        throw XtreamJsonGuardException.TooManyItems(
            scope = "series episodes",
            maxItems = XtreamJsonLimits.MAX_SERIES_EPISODES,
        )
    }
    result += XtreamSeriesEpisodeJson(
        seasonKey = seasonKey,
        indexInSeason = index,
        episode = episode,
    )
}

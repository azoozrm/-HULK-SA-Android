package sa.hulksa.player.data

import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.BuildConfig

sealed interface FootballFetchResult {
    data class Success(
        val snapshot: FootballFeedSnapshot,
        val rawJson: String,
    ) : FootballFetchResult

    data class Failure(
        val error: FootballFeedError,
        val retryAfterSeconds: Long? = null,
    ) : FootballFetchResult
}

/**
 * First-party feed client. The endpoint is supplied by the optional Operations discovery object;
 * provider credentials never reach the app. Redirects are not followed so the pinned first-party
 * path cannot become a different destination.
 */
open class FootballFeedClient(
    client: OkHttpClient = defaultFootballHttpClient(),
) {
    private val effectiveClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    open suspend fun fetch(
        discovery: FootballFeedDiscovery,
        dayKey: String,
    ): FootballFetchResult = withContext(Dispatchers.IO) {
        val endpoint = runCatching { URI(discovery.feedUrl) }.getOrNull()
        if (
            endpoint == null ||
            !endpoint.scheme.equals("https", ignoreCase = true) ||
            endpoint.host.isNullOrBlank() ||
            endpoint.userInfo != null ||
            endpoint.rawPath != FOOTBALL_FEED_REQUEST_PATH
        ) {
            return@withContext FootballFetchResult.Failure(FootballFeedError.NOT_CONFIGURED)
        }

        runCatching {
            val request = Request.Builder()
                .url(
                    endpoint.toASCIIString().let { base ->
                        base + "?day=" + java.net.URLEncoder.encode(dayKey, "UTF-8")
                    },
                )
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "NEO4K/${BuildConfig.VERSION_NAME} Football")
                .build()
            effectiveClient.executeCancellable(request) { response ->
                val retryAfter = parseFootballRetryAfterHeader(
                    response.header("Retry-After"),
                    System.currentTimeMillis(),
                )
                if (response.code == 429 || response.code == 503) {
                    return@executeCancellable FootballFetchResult.Failure(
                        error = if (response.code == 429) {
                            FootballFeedError.RATE_LIMITED
                        } else {
                            FootballFeedError.UNAVAILABLE
                        },
                        retryAfterSeconds = retryAfter,
                    )
                }
                if (response.code in 300..399 || !response.isSuccessful) {
                    return@executeCancellable FootballFetchResult.Failure(
                        FootballFeedError.UNAVAILABLE,
                        retryAfter,
                    )
                }
                val body = response.body ?: return@executeCancellable FootballFetchResult.Failure(
                    FootballFeedError.INVALID_RESPONSE,
                )
                if (body.contentLength() > MAX_FEED_BYTES) {
                    return@executeCancellable FootballFetchResult.Failure(
                        FootballFeedError.INVALID_RESPONSE,
                    )
                }
                val raw = readBounded(body) ?: return@executeCancellable FootballFetchResult.Failure(
                    FootballFeedError.INVALID_RESPONSE,
                )
                val snapshot = parseFootballFeed(raw, dayKey, retryAfter)
                    ?: return@executeCancellable FootballFetchResult.Failure(
                        FootballFeedError.INVALID_RESPONSE,
                    )
                FootballFetchResult.Success(snapshot, raw)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            FootballFetchResult.Failure(FootballFeedError.UNAVAILABLE)
        }
    }

    companion object {
        private const val MAX_FEED_BYTES = 512L * 1_024L

        fun defaultFootballHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        private fun readBounded(body: ResponseBody): String? {
            val output = ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FEED_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

private const val FOOTBALL_FEED_REQUEST_PATH = "/hulk-operations/api/app/v1/sports/"

fun parseFootballRetryAfterHeader(value: String?, nowEpochMs: Long): Long? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val seconds = trimmed.toLongOrNull()
    if (seconds != null) {
        return seconds.coerceIn(1L, 86_400L)
    }
    val parsed = runCatching {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            isLenient = false
        }.parse(trimmed)
    }.getOrNull() ?: return null
    return ((parsed.time - nowEpochMs + 999L) / 1_000L).coerceIn(1L, 86_400L)
}

fun parseFootballFeed(
    rawJson: String,
    requestedDay: String,
    retryAfterSeconds: Long? = null,
): FootballFeedSnapshot? = runCatching {
    val root = JSONObject(rawJson)
    require(root.getInt("schemaVersion") == FOOTBALL_FEED_SCHEMA_VERSION)
    val day = root.getString("day").trim()
    require(day == requestedDay)
    val generatedAt = root.getLong("generatedAt").takeIf { it > 0L }?.times(1_000L)
        ?: return@runCatching null
    val cachedAt = root.optNullableLong("cachedAt")?.times(1_000L) ?: generatedAt
    val refreshSeconds = root.optInt("refreshSeconds", FOOTBALL_DEFAULT_REFRESH_SECONDS)
        .coerceIn(FOOTBALL_MIN_REFRESH_SECONDS, FOOTBALL_MAX_REFRESH_SECONDS)
    val stale = root.optBoolean("stale", false)
    val payloadRetryAfter = root.optNullableLong("retryAfterSeconds")

    val matches = buildList {
        val array = root.optJSONArray("matches") ?: JSONArray()
        for (index in 0 until array.length()) {
            parseFootballMatch(array.optJSONObject(index))?.let(::add)
        }
    }
    val reconciled = reconcileFootballMatches(matches)
    val teams = buildList {
        val array = root.optJSONArray("teams") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) add(FootballTeam(id, name))
        }
        addAll(reconciled.flatMap { listOf(it.homeTeam, it.awayTeam) })
    }.distinctBy(FootballTeam::id)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    FootballFeedSnapshot(
        dayKey = day,
        generatedAtEpochMs = generatedAt,
        cachedAtEpochMs = cachedAt,
        stale = stale,
        retryAfterSeconds = payloadRetryAfter ?: retryAfterSeconds,
        refreshSeconds = refreshSeconds,
        matches = reconciled,
        teams = teams,
    )
}.getOrNull()

private fun parseFootballMatch(value: JSONObject?): FootballMatch? = runCatching {
    requireNotNull(value)
    val eventId = value.optString("providerEventId").trim().ifBlank {
        value.optString("id").trim().removePrefix("evt:")
    }
    require(eventId.isNotEmpty())
    val aliases = value.optJSONArray("aliases").strings()
    val replacedBy = value.optNullableString("replacedBy")
    val competitionObject = value.optJSONObject("competition")
    val competition = FootballCompetition(
        id = competitionObject?.optString("id")?.trim().orEmpty().ifBlank { "unknown" },
        name = competitionObject?.optString("name")?.trim().orEmpty().ifBlank { "مسابقة" },
        country = competitionObject?.optNullableString("country"),
    )
    val home = parseFootballTeam(value.optJSONObject("homeTeam")) ?: return@runCatching null
    val away = parseFootballTeam(value.optJSONObject("awayTeam")) ?: return@runCatching null
    require(home.id != away.id)
    val kickoffRaw = value.optLong("kickoffAt", 0L)
    require(kickoffRaw > 0L)
    val kickoff = if (kickoffRaw < 10_000_000_000L) kickoffRaw * 1_000L else kickoffRaw
    FootballMatch(
        providerEventId = eventId,
        aliases = aliases.filterNot { it == eventId }.distinct(),
        replacedBy = replacedBy,
        competition = competition,
        homeTeam = home,
        awayTeam = away,
        kickoffEpochMs = kickoff,
        status = FootballMatchStatus.fromWire(value.optString("status")),
        homeScore = value.optNullableInt("homeScore"),
        awayScore = value.optNullableInt("awayScore"),
        minute = value.optNullableInt("minute")?.takeIf { it >= 0 },
        carriedOver = value.optBoolean("carriedOver", false),
    )
}.getOrNull()

private fun parseFootballTeam(value: JSONObject?): FootballTeam? = runCatching {
    requireNotNull(value)
    val id = value.optString("id").trim()
    val name = value.optString("name").trim()
    require(id.isNotEmpty() && name.isNotEmpty())
    FootballTeam(id, name)
}.getOrNull()

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeUnless { it.isBlank() || it.equals("null", true) }
}

private fun JSONObject.optNullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key).takeIf { it > 0L }
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

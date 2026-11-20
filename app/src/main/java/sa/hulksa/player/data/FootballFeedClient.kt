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

sealed interface FootballTeamCatalogFetchResult {
    data class Success(
        val teams: List<FootballTeam>,
        val rawJson: String,
        val stale: Boolean = false,
        val cachedAtEpochMs: Long = 0L,
        val retryAfterSeconds: Long? = null,
        val policyVersion: String? = null,
    ) : FootballTeamCatalogFetchResult

    data class Failure(
        val retryAfterSeconds: Long? = null,
    ) : FootballTeamCatalogFetchResult
}

data class FootballTeamCatalogPayload(
    val teams: List<FootballTeam>,
    val stale: Boolean,
    val cachedAtEpochMs: Long,
    val policyVersion: String? = null,
    val allowlistHash: String? = null,
)

const val FOOTBALL_CATALOG_POLICY_VERSION = "r04-allowlist-27"

/** Verified default-policy catalog published by R04 production before policyVersion existed. */
const val FOOTBALL_CATALOG_ALLOWLIST_HASH =
    "76e0f987c6294264cb1a8998a7480adfb4d7d2372def703f7bf3ef144d48f94c"

/** A policy identity is either the exact verified hash or, for older payloads, the current version. */
fun footballCatalogPolicyIdentityValid(
    policyVersion: String?,
    allowlistHash: String?,
): Boolean = if (allowlistHash != null) {
    allowlistHash == FOOTBALL_CATALOG_ALLOWLIST_HASH
} else {
    policyVersion == FOOTBALL_CATALOG_POLICY_VERSION
}

fun footballCatalogMembershipValid(teams: List<FootballTeam>): Boolean =
    teams.all { team -> team.competitionIds.any { it in FOOTBALL_ALLOWED_COMPETITION_IDS } }

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
                val snapshot = parseFootballFeed(raw, dayKey, retryAfter, enforceAllowlist = true)
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

    open suspend fun fetchTeams(discovery: FootballFeedDiscovery): FootballTeamCatalogFetchResult =
        withContext(Dispatchers.IO) {
            val endpoint = runCatching { URI(discovery.feedUrl + TEAM_CATALOG_PATH) }.getOrNull()
            if (
                endpoint == null ||
                !endpoint.scheme.equals("https", ignoreCase = true) ||
                endpoint.host.isNullOrBlank() ||
                endpoint.userInfo != null ||
                endpoint.rawPath != FOOTBALL_TEAM_CATALOG_REQUEST_PATH
            ) {
                return@withContext FootballTeamCatalogFetchResult.Failure()
            }
            runCatching {
                val request = Request.Builder()
                    .url(endpoint.toASCIIString())
                    .get()
                    .header("Accept", "application/json")
                    .header("User-Agent", "NEO4K/${BuildConfig.VERSION_NAME} Football")
                    .build()
                effectiveClient.executeCancellable(request) { response ->
                    val retryAfter = parseFootballRetryAfterHeader(
                        response.header("Retry-After"),
                        System.currentTimeMillis(),
                    )
                    if (response.code == 429 || response.code == 503 || !response.isSuccessful) {
                        return@executeCancellable FootballTeamCatalogFetchResult.Failure(retryAfter)
                    }
                    val body = response.body
                        ?: return@executeCancellable FootballTeamCatalogFetchResult.Failure(retryAfter)
                    if (body.contentLength() > MAX_FEED_BYTES) {
                        return@executeCancellable FootballTeamCatalogFetchResult.Failure(retryAfter)
                    }
                    val raw = readBounded(body)
                        ?: return@executeCancellable FootballTeamCatalogFetchResult.Failure(retryAfter)
                    val payload = parseFootballTeamCatalogPayload(raw)
                        ?: return@executeCancellable FootballTeamCatalogFetchResult.Failure(retryAfter)
                    FootballTeamCatalogFetchResult.Success(
                        teams = payload.teams,
                        rawJson = raw,
                        stale = payload.stale,
                        cachedAtEpochMs = payload.cachedAtEpochMs,
                        retryAfterSeconds = retryAfter,
                        policyVersion = payload.policyVersion,
                    )
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                FootballTeamCatalogFetchResult.Failure()
            }
        }

    companion object {
        private const val TEAM_CATALOG_PATH = "teams/"
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

private const val FOOTBALL_TEAM_CATALOG_REQUEST_PATH = "/hulk-operations/api/app/v1/sports/teams/"

fun parseFootballTeamCatalog(rawJson: String): List<FootballTeam>? =
    parseFootballTeamCatalogPayload(rawJson)?.teams

fun parseFootballTeamCatalogPayload(rawJson: String): FootballTeamCatalogPayload? = runCatching {
    val root = JSONObject(rawJson)
    require(root.getInt("schemaVersion") == FOOTBALL_FEED_SCHEMA_VERSION)
    require(root.has("complete") && root.get("complete") is Boolean && root.getBoolean("complete"))
    val array = root.optJSONArray("teams") ?: return@runCatching null
    val teams = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return@runCatching null
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return@runCatching null
            add(FootballTeam(
                id = id,
                name = name,
                arabicName = item.optNullableString("arabicName"),
                logoUrl = item.optNullableString("logoUrl"),
                country = item.optNullableString("country"),
                competitionIds = item.optJSONArray("competitionIds").strings().toSet(),
                nationalTeam = item.optBoolean("nationalTeam", false),
                aliases = item.optJSONArray("aliases").strings(),
            ))
        }
    }.distinctBy(FootballTeam::id)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    require(footballCatalogMembershipValid(teams))
    val policyVersion = root.optNullableString("policyVersion")
    val allowlistHash = root.optNullableString("allowlistHash")
    require(footballCatalogPolicyIdentityValid(policyVersion, allowlistHash))
    val cachedAt = root.optNullableLong("cachedAt")?.times(1_000L)
        ?: root.optNullableLong("generatedAt")?.times(1_000L)
        ?: 0L
    FootballTeamCatalogPayload(
        teams = teams,
        stale = root.optBoolean("stale", false),
        cachedAtEpochMs = cachedAt,
        policyVersion = policyVersion,
        allowlistHash = allowlistHash,
    )
}.getOrNull()

fun parseFootballFeed(
    rawJson: String,
    requestedDay: String,
    retryAfterSeconds: Long? = null,
    enforceAllowlist: Boolean = false,
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
        // A missing or wrong-typed matches field is not an authoritative empty day. A non-empty
        // array whose every row is malformed is equally invalid; only a real array (including an
        // explicitly empty one) may produce a snapshot.
        val array = root.optJSONArray("matches") ?: return@runCatching null
        for (index in 0 until array.length()) {
            parseFootballMatch(array.optJSONObject(index))?.let(::add)
        }
        if (array.length() > 0 && isEmpty()) return@runCatching null
    }
    val reconciled = reconcileFootballMatches(mergeFootballMatchRows(matches))
    val kickoffHistory = buildList {
        val array = root.optJSONArray("kickoffHistory") ?: JSONArray()
        for (index in 0 until array.length()) {
            parseFootballMatch(array.optJSONObject(index))?.let(::add)
        }
    }.let { history ->
        reconcileFootballMatches(mergeFootballMatchRows(history))
    }
    val reminderCandidates = buildList {
        val array = root.optJSONArray("reminderCandidates") ?: JSONArray()
        for (index in 0 until array.length()) {
            parseFootballMatch(array.optJSONObject(index))?.let(::add)
        }
    }.let { reminders ->
        reconcileFootballMatches(mergeFootballMatchRows(reminders))
    }
    val teams = buildList {
        val array = root.optJSONArray("teams") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                add(FootballTeam(
                    id = id,
                    name = name,
                    arabicName = item.optNullableString("arabicName"),
                    logoUrl = item.optNullableString("logoUrl"),
                    country = item.optNullableString("country"),
                    competitionIds = item.optJSONArray("competitionIds").strings().toSet(),
                    nationalTeam = item.optBoolean("nationalTeam", false),
                    aliases = item.optJSONArray("aliases").strings(),
                ))
            }
        }
        addAll(reconciled.flatMap { listOf(it.homeTeam, it.awayTeam) })
        addAll(reminderCandidates.flatMap { listOf(it.homeTeam, it.awayTeam) })
    }.distinctBy(FootballTeam::id)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

    FootballFeedSnapshot(
        dayKey = day,
        generatedAtEpochMs = generatedAt,
        cachedAtEpochMs = cachedAt,
        stale = stale,
        retryAfterSeconds = payloadRetryAfter ?: retryAfterSeconds,
        refreshSeconds = refreshSeconds,
        matches = reconciled,
        teams = teams,
        kickoffHistory = kickoffHistory,
        reminderCandidates = reminderCandidates,
    ).let { snapshot ->
        if (enforceAllowlist) filterFootballSnapshotForAllowlist(snapshot) else snapshot
    }
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
        arabicName = competitionObject?.optNullableString("arabicName"),
        logoUrl = competitionObject?.optNullableString("logoUrl"),
        priority = competitionObject?.optInt("priority", 50) ?: 50,
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
        startedAtEpochMs = value.optNullableLong("startedAt")?.let { seconds ->
            if (seconds < 10_000_000_000L) seconds * 1_000L else seconds
        },
        providerUpdatedAtEpochMs = value.optNullableLong("providerUpdatedAt")?.let { seconds ->
            if (seconds < 10_000_000_000L) seconds * 1_000L else seconds
        },
        broadcasters = value.optJSONArray("broadcasters").strings()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct(),
    )
}.getOrNull()

private fun parseFootballTeam(value: JSONObject?): FootballTeam? = runCatching {
    requireNotNull(value)
    val id = value.optString("id").trim()
    val name = value.optString("name").trim()
    require(id.isNotEmpty() && name.isNotEmpty())
    val competitionIds = value.optJSONArray("competitionIds").strings().toSet()
    FootballTeam(
        id = id,
        name = name,
        arabicName = value.optNullableString("arabicName"),
        logoUrl = value.optNullableString("logoUrl"),
        country = value.optNullableString("country"),
        competitionIds = competitionIds,
        nationalTeam = value.optBoolean("nationalTeam", false),
        aliases = value.optJSONArray("aliases").strings(),
    )
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

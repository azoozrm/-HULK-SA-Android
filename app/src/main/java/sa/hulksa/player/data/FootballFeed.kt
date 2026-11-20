package sa.hulksa.player.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val FOOTBALL_RIYADH_TIMEZONE = "Asia/Riyadh"
const val FOOTBALL_FEED_SCHEMA_VERSION = 1
const val FOOTBALL_LIVE_CARRY_OVER_MS = 6L * 60L * 60L * 1_000L
const val FOOTBALL_KICKOFF_CONFIRM_GRACE_MS = 90L * 1_000L
const val FOOTBALL_KICKOFF_CATCH_UP_WINDOW_MS = 24L * 60L * 60L * 1_000L
const val FOOTBALL_MAX_KICKOFF_NOTIFICATIONS_PER_PROFILE = 200
const val FOOTBALL_DEFAULT_REFRESH_SECONDS = 60
const val FOOTBALL_MIN_REFRESH_SECONDS = 30
const val FOOTBALL_MAX_REFRESH_SECONDS = 300

private val footballTimeZone: TimeZone get() = TimeZone.getTimeZone(FOOTBALL_RIYADH_TIMEZONE)

enum class FootballMatchStatus(val wireName: String) {
    UPCOMING("upcoming"),
    LIVE("live"),
    FINISHED("finished"),
    CANCELLED("cancelled"),
    POSTPONED("postponed"),
    UNRESOLVED("unresolved"),
    ;

    val isStarted: Boolean get() = this == LIVE || this == FINISHED

    companion object {
        fun fromWire(value: String?): FootballMatchStatus = when (value?.trim()?.lowercase()) {
            "live", "inprogress", "in_progress" -> LIVE
            "finished", "ft", "ended" -> FINISHED
            "cancelled", "canceled", "abandoned", "awarded", "walkover" -> CANCELLED
            "postponed", "suspended" -> POSTPONED
            "upcoming", "notstarted", "scheduled" -> UPCOMING
            else -> UNRESOLVED
        }
    }
}

fun footballMatchStatusRank(status: FootballMatchStatus): Int = when (status) {
    FootballMatchStatus.FINISHED -> 4
    FootballMatchStatus.LIVE -> 3
    FootballMatchStatus.UPCOMING -> 1
    else -> 0
}

/**
 * Merge duplicate schedule/live rows by verified provider id. The first row owns fixture
 * metadata; a started row supplies the fresher status/score/minute. Aliases are unioned so a
 * replacement identity remains stable.
 */
fun mergeFootballMatchRows(rows: List<FootballMatch>): List<FootballMatch> {
    val merged = linkedMapOf<String, FootballMatch>()
    rows.forEach { row ->
        val existing = merged[row.providerEventId]
        if (existing == null) {
            merged[row.providerEventId] = row
            return@forEach
        }
        val existingRank = footballMatchStatusRank(existing.status)
        val rowRank = footballMatchStatusRank(row.status)
        val rowHasDetail = row.minute != null || row.homeScore != null || row.awayScore != null
        val existingHasDetail = existing.minute != null || existing.homeScore != null || existing.awayScore != null
        val existingUpdated = existing.providerUpdatedAtEpochMs ?: 0L
        val rowUpdated = row.providerUpdatedAtEpochMs ?: 0L
        val newerCancellation = rowUpdated > existingUpdated && rowUpdated > 0L &&
            row.status in setOf(FootballMatchStatus.CANCELLED, FootballMatchStatus.POSTPONED)
        val newerFinalScore = rowUpdated > existingUpdated && rowUpdated > 0L &&
            row.status == FootballMatchStatus.FINISHED &&
            existing.status == FootballMatchStatus.FINISHED && rowHasDetail
        val existingNewerCancellation = existingUpdated > rowUpdated && existingUpdated > 0L &&
            existing.status in setOf(FootballMatchStatus.CANCELLED, FootballMatchStatus.POSTPONED)
        val takeRow = when {
            newerCancellation || newerFinalScore -> true
            existingNewerCancellation -> false
            rowRank > existingRank -> true
            rowRank < existingRank -> false
            row.status == FootballMatchStatus.LIVE && existing.status == FootballMatchStatus.LIVE -> {
                val rowMinute = row.minute ?: -1
                val existingMinute = existing.minute ?: -1
                rowMinute > existingMinute ||
                    (rowMinute == existingMinute && (rowHasDetail || existingHasDetail))
            }
            row.status == FootballMatchStatus.FINISHED && existing.status == FootballMatchStatus.FINISHED ->
                !existingHasDetail && rowHasDetail
            else -> false
        }
        val fresher = if (takeRow) {
            existing.copy(
                status = row.status,
                // A fresher status row can omit a verified score; never erase it with null.
                homeScore = row.homeScore ?: existing.homeScore,
                awayScore = row.awayScore ?: existing.awayScore,
                minute = row.minute,
                startedAtEpochMs = row.startedAtEpochMs ?: existing.startedAtEpochMs,
                providerUpdatedAtEpochMs = row.providerUpdatedAtEpochMs
                    ?: existing.providerUpdatedAtEpochMs,
                replacedBy = row.replacedBy ?: existing.replacedBy,
            )
        } else if (existing.status in setOf(
                FootballMatchStatus.FINISHED,
                FootballMatchStatus.CANCELLED,
                FootballMatchStatus.POSTPONED,
            )
        ) {
            // A newer cancellation/postponement can own the status while an older final row
            // still supplies the confirmed score.
            existing.copy(
                homeScore = existing.homeScore ?: row.homeScore,
                awayScore = existing.awayScore ?: row.awayScore,
            )
        } else {
            existing
        }
        val competition = if (
            (existing.competition.name.isBlank() || existing.competition.name == "مسابقة") &&
            row.competition.name.isNotBlank() && row.competition.name != "مسابقة"
        ) {
            row.competition
        } else {
            existing.competition
        }
        merged[row.providerEventId] = fresher.copy(
            competition = competition,
            aliases = (existing.aliases + row.aliases).distinct(),
            // Channels belong to a fixture instant. A provider id reused for a new kickoff must
            // not inherit the previous assignment.
            broadcasters = (
                existing.broadcasters +
                    row.broadcasters.takeIf { row.kickoffEpochMs == existing.kickoffEpochMs }.orEmpty()
                ).distinct(),
            providerUpdatedAtEpochMs = maxOf(
                existing.providerUpdatedAtEpochMs ?: 0L,
                row.providerUpdatedAtEpochMs ?: 0L,
            ).takeIf { it > 0L },
        )
    }
    return merged.values.toList()
}

data class FootballCompetition(
    val id: String,
    val name: String,
    val country: String? = null,
    val arabicName: String? = null,
    val logoUrl: String? = null,
    val priority: Int = 50,
) {
    val displayName: String get() = arabicName?.takeIf { it.isNotBlank() } ?: name
}

data class FootballTeam(
    val id: String,
    val name: String,
    val arabicName: String? = null,
    val logoUrl: String? = null,
    val country: String? = null,
    val competitionIds: Set<String> = emptySet(),
    val nationalTeam: Boolean = false,
    val aliases: List<String> = emptyList(),
) {
    val displayName: String get() = arabicName?.takeIf { it.isNotBlank() } ?: name
    val searchNames: List<String>
        get() = (listOf(name, arabicName.orEmpty()) + aliases)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
}

data class FootballMatch(
    val providerEventId: String,
    val aliases: List<String> = emptyList(),
    val replacedBy: String? = null,
    val competition: FootballCompetition,
    val homeTeam: FootballTeam,
    val awayTeam: FootballTeam,
    val kickoffEpochMs: Long,
    val status: FootballMatchStatus,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    val carriedOver: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val providerUpdatedAtEpochMs: Long? = null,
    val broadcasters: List<String> = emptyList(),
) {
    val stableIdentity: String get() = "football:$providerEventId"
    val eventIds: Set<String> get() = (aliases + providerEventId).toSet()

    fun involvesTeam(teamId: String): Boolean =
        homeTeam.id == teamId || awayTeam.id == teamId

    fun opponentOf(teamId: String): FootballTeam? = when (teamId) {
        homeTeam.id -> awayTeam
        awayTeam.id -> homeTeam
        else -> null
    }

    fun scoreFor(teamId: String): Int? = when (teamId) {
        homeTeam.id -> homeScore
        awayTeam.id -> awayScore
        else -> null
    }
}

data class FootballFeedSnapshot(
    val dayKey: String,
    val generatedAtEpochMs: Long,
    val cachedAtEpochMs: Long,
    val stale: Boolean,
    val retryAfterSeconds: Long?,
    val refreshSeconds: Int,
    val matches: List<FootballMatch>,
    val teams: List<FootballTeam>,
    val kickoffHistory: List<FootballMatch> = emptyList(),
    val reminderCandidates: List<FootballMatch> = emptyList(),
    /** Device receipt time of this accepted record; provider [cachedAtEpochMs] stays separate. */
    val fetchedAtEpochMs: Long = 0L,
) {
    val isEmptyDay: Boolean get() = matches.isEmpty() && !stale
}

enum class FootballAlertType(val wireName: String) {
    KICKOFF("kickoff"),
    REMINDER_15("reminder15"),
}

data class FootballMatchAlertPreferences(
    val kickoff: Boolean = false,
    val reminder15: Boolean = false,
    val manuallyTracked: Boolean = false,
    val trackedAtEpochMs: Long = 0L,
)

data class FootballFavoriteTeam(
    val teamId: String,
    val teamName: String,
    val selectedAtEpochMs: Long,
    val team: FootballTeam? = null,
)

data class FootballKickoffNotification(
    val id: String,
    val accountId: String,
    val profileId: String,
    val eventId: String,
    val eventIds: Set<String>,
    val competitionName: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val kickoffEpochMs: Long,
    val startedAtEpochMs: Long,
    val detectedAtEpochMs: Long,
    val alertType: FootballAlertType = FootballAlertType.KICKOFF,
    val read: Boolean,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: FootballMatchStatus,
)

data class FootballInboxState(
    val notifications: List<FootballKickoffNotification> = emptyList(),
    /** Legacy kickoff-only tombstones retained so old persisted deletes keep suppressing starts. */
    val suppressedEventIds: Set<String> = emptySet(),
    /** Canonical fixture/alert/account/profile tombstones for independent kickoff/reminder deletes. */
    val suppressedAlerts: Set<String> = emptySet(),
    val clearedThroughEpochMs: Long = 0L,
) {
    val unreadCount: Int get() = notifications.count { !it.read }
}

/**
 * Last failed attempt for one selected Riyadh day. It is kept separately from the last-good
 * [FootballFeedSnapshot] so an unavailable or rate-limited refresh never rewrites real rows or
 * masquerades as a valid empty day.
 */
data class FootballDayFailure(
    val error: FootballFeedError,
    val attemptedAtEpochMs: Long,
    val retryAtEpochMs: Long = 0L,
    /** Request start; a same-day success accepted after this point owns the result. */
    val startedAtEpochMs: Long = 0L,
)

data class FootballFeedUiState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val snapshot: FootballFeedSnapshot? = null,
    val favoriteTeams: List<FootballFavoriteTeam> = emptyList(),
    val knownTeams: List<FootballTeam> = emptyList(),
    /** Non-null after a complete policy-verified catalog, including a valid empty catalog. */
    val authoritativeCatalog: List<FootballTeam>? = null,
    /** Discovery source that owns [authoritativeCatalog]; null before any authority exists. */
    val authoritativeCatalogSourceKey: String? = null,
    val hideScores: Boolean = false,
    val matchAlerts: Map<String, FootballMatchAlertPreferences> = emptyMap(),
    val daySnapshots: Map<String, FootballFeedSnapshot> = emptyMap(),
    val loadingDays: Set<String> = emptySet(),
    val dayFailures: Map<String, FootballDayFailure> = emptyMap(),
    val error: FootballFeedError? = null,
    val lastAttemptEpochMs: Long = 0L,
    /** One shared Home/day Retry-After deadline; UI eligibility must use this value. */
    val nextRetryAtEpochMs: Long = 0L,
) {
    val favoriteTeam: FootballFavoriteTeam? get() = favoriteTeams.firstOrNull()
    val favoriteTeamIds: Set<String> get() = favoriteTeams.mapTo(linkedSetOf(), FootballFavoriteTeam::teamId)
    val matches: List<FootballMatch> get() = snapshot?.matches.orEmpty()
    val allMatches: List<FootballMatch>
        get() = buildList {
            snapshot?.let { addAll(it.matches); addAll(it.kickoffHistory); addAll(it.reminderCandidates) }
            daySnapshots.values.forEach { addAll(it.matches) }
        }.distinctBy(FootballMatch::stableIdentity)
    val stale: Boolean get() = snapshot?.stale == true
    val emptyDay: Boolean get() = snapshot?.isEmptyDay == true
    val dayKey: String? get() = snapshot?.dayKey
}

enum class FootballFeedError {
    NOT_CONFIGURED,
    UNAVAILABLE,
    RATE_LIMITED,
    INVALID_RESPONSE,
}

data class FootballFeedDiscovery(
    val feedUrl: String,
    val refreshSeconds: Int,
)

data class FootballKickoffGeneration(
    val inbox: FootballInboxState,
    val added: List<FootballKickoffNotification>,
)

/** R04 owner-approved available competition allowlist; optional 53/47 stay disabled. */
val FOOTBALL_ALLOWED_COMPETITION_IDS: Set<String> = setOf(
    "17", "7", "8", "83", "90", "1", "3", "4", "5", "6",
    "39", "40", "41", "42", "43", "44", "29",
    "27", "68", "66", "30", "61", "58", "59", "60", "62", "63",
)

fun filterFootballSnapshotForAllowlist(snapshot: FootballFeedSnapshot): FootballFeedSnapshot {
    fun filtered(rows: List<FootballMatch>): List<FootballMatch> =
        rows.filter { it.competition.id in FOOTBALL_ALLOWED_COMPETITION_IDS }
    val matches = filtered(snapshot.matches)
    val history = filtered(snapshot.kickoffHistory)
    val reminders = filtered(snapshot.reminderCandidates)
    val referenced = (matches + history + reminders)
        .flatMapTo(linkedSetOf()) { listOf(it.homeTeam.id, it.awayTeam.id) }
    val teams = snapshot.teams.filter { team ->
        team.id in referenced || team.competitionIds.any { it in FOOTBALL_ALLOWED_COMPETITION_IDS }
    }
    return snapshot.copy(
        matches = matches,
        teams = teams,
        kickoffHistory = history,
        reminderCandidates = reminders,
    )
}

fun footballRiyadhDayKey(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = footballTimeZone
    }.format(Date(epochMs))

fun footballRiyadhDayStartEpochMs(dayKey: String): Long? {
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = footballTimeZone
            isLenient = false
        }.parse(dayKey)
    }.getOrNull() ?: return null
    return parsed.time.takeIf { it > 0L }
}

fun footballRiyadhDayEndEpochMs(dayKey: String): Long? =
    footballRiyadhDayStartEpochMs(dayKey)?.plus(24L * 60L * 60L * 1_000L)

fun footballRiyadhTimeLabel(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = footballTimeZone
    }.format(Date(epochMs))

fun footballRiyadhDayLabel(dayKey: String, nowEpochMs: Long): String = when (dayKey) {
    footballRiyadhDayKey(nowEpochMs) -> "اليوم"
    footballRiyadhDayKey(nowEpochMs + 24L * 60L * 60L * 1_000L) -> "غدا"
    footballRiyadhDayKey(nowEpochMs - 24L * 60L * 60L * 1_000L) -> "الامس"
    else -> dayKey
}


/**
 * Resolve `replaced_by` links into one stable canonical row and keep the old provider id as an
 * alias. A postponed row whose replacement is outside the window is not displayed on the old day.
 */
fun reconcileFootballMatches(matches: List<FootballMatch>): List<FootballMatch> {
    val byId = matches.associateBy(FootballMatch::providerEventId)
    val aliases = mutableMapOf<String, MutableSet<String>>()
    val canonicalById = mutableMapOf<String, String>()
    val replaceable = mutableSetOf<String>()
    matches.forEach { match ->
        var current = match
        val visited = mutableSetOf<String>()
        val collected = linkedSetOf<String>()
        var dropped = false
        while (true) {
            val replacementId = current.replacedBy ?: break
            if (!visited.add(replacementId)) break
            val replacement = byId[replacementId]
            if (replacement == null) {
                // The replacement moved outside this window; do not keep the old fixture row.
                dropped = true
                break
            }
            collected += current.providerEventId
            replaceable += current.providerEventId
            current = replacement
        }
        if (dropped) return@forEach
        canonicalById[match.providerEventId] = current.providerEventId
        if (current.providerEventId != match.providerEventId || collected.isNotEmpty()) {
            aliases.getOrPut(current.providerEventId) { linkedSetOf() } += collected
        }
    }

    val result = mutableListOf<FootballMatch>()
    val seen = mutableSetOf<String>()
    matches.forEach { match ->
        val canonicalId = canonicalById[match.providerEventId] ?: return@forEach
        if (match.providerEventId in replaceable && match.providerEventId != canonicalId) {
            return@forEach
        }
        val canonical = byId[canonicalId] ?: return@forEach
        if (!seen.add(canonicalId)) return@forEach
        result += canonical.copy(
            aliases = (canonical.aliases + aliases[canonicalId].orEmpty())
                .filterNot { it == canonicalId }
                .distinct(),
        )
    }
    return result
}

/**
 * Client fallback matching the server rule: keep the Riyadh day plus matches already live at
 * midnight (and a bounded finished-result window).
 */
fun footballMatchesForDay(matches: List<FootballMatch>, dayKey: String): List<FootballMatch> {
    val dayStart = footballRiyadhDayStartEpochMs(dayKey) ?: return emptyList()
    val dayEnd = dayStart + 24L * 60L * 60L * 1_000L
    return matches.mapNotNull { match ->
        val sameDay = match.kickoffEpochMs >= dayStart && match.kickoffEpochMs < dayEnd
        if (sameDay) return@mapNotNull match.copy(carriedOver = false)
        val carried = match.status == FootballMatchStatus.LIVE &&
            match.kickoffEpochMs >= dayStart - FOOTBALL_LIVE_CARRY_OVER_MS
        if (carried && match.kickoffEpochMs < dayStart) {
            match.copy(carriedOver = true)
        } else {
            null
        }
    }.let(::reconcileFootballMatches)
}

fun footballMatchSortOrder(status: FootballMatchStatus): Int = when (status) {
    FootballMatchStatus.LIVE -> 0
    FootballMatchStatus.UPCOMING -> 1
    FootballMatchStatus.FINISHED -> 2
    FootballMatchStatus.POSTPONED -> 3
    FootballMatchStatus.CANCELLED -> 4
    FootballMatchStatus.UNRESOLVED -> 5
}

/**
 * Favorite-team fixtures lead. The rest follow competition priority (Saudi, Gulf/Arab, Europe,
 * other) and then live/upcoming/results inside each tier.
 */
fun orderFootballMatches(
    matches: List<FootballMatch>,
    favoriteTeamIds: Set<String>,
): List<FootballMatch> =
    matches.distinctBy(FootballMatch::stableIdentity).sortedWith(
        compareBy<FootballMatch> {
            if (favoriteTeamIds.any(it::involvesTeam)) 0 else 1
        }
            .thenBy { it.competition.priority }
            .thenBy { footballMatchSortOrder(it.status) }
            .thenBy(FootballMatch::kickoffEpochMs)
            .thenBy(FootballMatch::providerEventId),
    )

fun orderFootballMatches(matches: List<FootballMatch>, favoriteTeamId: String?): List<FootballMatch> =
    orderFootballMatches(matches, favoriteTeamId?.let(::setOf).orEmpty())

/**
 * Merge metadata for one stable provider ID. A later verified row fills missing Arabic/crest/
 * country data and unions memberships/aliases instead of being discarded by a first-entry
 * `distinctBy`.
 */
fun mergeFootballTeam(existing: FootballTeam, incoming: FootballTeam): FootballTeam = existing.copy(
    name = existing.name.takeIf { it.isNotBlank() } ?: incoming.name,
    arabicName = existing.arabicName?.takeIf { it.isNotBlank() }
        ?: incoming.arabicName?.takeIf { it.isNotBlank() },
    logoUrl = existing.logoUrl?.takeIf { it.isNotBlank() }
        ?: incoming.logoUrl?.takeIf { it.isNotBlank() },
    country = existing.country?.takeIf { it.isNotBlank() }
        ?: incoming.country?.takeIf { it.isNotBlank() },
    competitionIds = existing.competitionIds + incoming.competitionIds,
    nationalTeam = existing.nationalTeam || incoming.nationalTeam,
    aliases = (existing.aliases + incoming.aliases + listOf(existing.name, incoming.name))
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct(),
)

/** Full merged team catalog; callers bound only their presentation, never this search source. */
fun mergeFootballTeamLists(vararg lists: List<FootballTeam>): List<FootballTeam> {
    val merged = linkedMapOf<String, FootballTeam>()
    lists.asSequence().flatten().forEach { team ->
        val existing = merged[team.id]
        merged[team.id] = if (existing == null) team else mergeFootballTeam(existing, team)
    }
    return merged.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
}

/** A fresh filtered catalog replaces obsolete catalog-derived teams but keeps real match/favorite identities. */
fun reconcileFootballCatalogTeams(
    authoritativeCatalog: List<FootballTeam>,
    matches: List<FootballMatch>,
    favorites: List<FootballFavoriteTeam>,
): List<FootballTeam> = mergeFootballTeamLists(
    authoritativeCatalog,
    footballTeamsFromMatches(matches, favorites),
)

/**
 * Applies the catalog authority to every later feed/day publication. Once a complete verified
 * catalog exists, an older in-flight publication may add real match/favorite identities but can
 * never restore a removed catalog-derived team. An authoritative empty catalog is valid.
 */
fun reconcileFootballKnownTeams(
    authoritativeCatalog: List<FootballTeam>?,
    currentKnownTeams: List<FootballTeam>,
    publishedTeams: List<FootballTeam>,
    matches: List<FootballMatch>,
    favorites: List<FootballFavoriteTeam>,
): List<FootballTeam> = if (authoritativeCatalog != null) {
    reconcileFootballCatalogTeams(authoritativeCatalog, matches, favorites)
} else {
    mergeFootballTeamLists(currentKnownTeams, publishedTeams)
}

fun footballTeamsFromMatches(
    matches: List<FootballMatch>,
    favoriteTeams: List<FootballFavoriteTeam> = emptyList(),
): List<FootballTeam> = mergeFootballTeamLists(
    matches.flatMap { listOf(it.homeTeam, it.awayTeam) } +
        favoriteTeams.map { favorite ->
            favorite.team ?: FootballTeam(favorite.teamId, favorite.teamName)
        },
)

fun normalizeFootballSearchText(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace('\u0623', '\u0627')
    .replace('\u0625', '\u0627')
    .replace('\u0622', '\u0627')
    .replace('\u0640', ' ')
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")

fun filterFootballTeams(teams: List<FootballTeam>, query: String): List<FootballTeam> {
    val normalized = normalizeFootballSearchText(query)
    if (normalized.isBlank()) return teams
    return teams.filter { team ->
        team.searchNames.any { name -> normalizeFootballSearchText(name).contains(normalized) }
    }
}

/** Stale feed/inbox responses are valid only for the exact account/profile + request generation. */
internal fun footballOwnerStillCurrent(
    expected: AccountProfileStateScope,
    active: AccountProfileStateScope?,
    expectedGeneration: Long,
    currentGeneration: Long,
): Boolean = active == expected && expectedGeneration == currentGeneration

/** Catalog authority is account/profile-scoped and bound to the active first-party discovery URL. */
internal fun footballCatalogOwnerKey(
    scope: AccountProfileStateScope,
    feedUrl: String,
): String = "${scope.accountId}\u0000${scope.profileId}\u0000$feedUrl"

/**
 * Durable order for an accepted catalog write. The session identifies the app process; the
 * generation is monotonic for the life of that process and never resets with an owner, profile or
 * discovery-URL change. A process restart starts a new session, so no write from the previous
 * process can still be running.
 */
internal data class FootballCatalogWriteToken(
    val sessionId: String,
    val generation: Long,
)

internal object FootballCatalogWriteOrder {
    private val sessionId: String = java.util.UUID.randomUUID().toString()
    private val nextGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    fun next(): FootballCatalogWriteToken =
        FootballCatalogWriteToken(sessionId, nextGeneration.incrementAndGet())
}

/**
 * One authority owner for the verified team catalog.
 *
 * The durable `teams.json` cache is bootstrap-only: it may seed an owner that has no authority yet,
 * but it can never replace an established catalog, including a valid empty one. A network response
 * can replace authority only when it belongs to the latest catalog request for the current owner,
 * is not stale over an existing authority, and carries a provider cache time that is not older.
 * A response that completes after a newer request started is obsolete and is dropped before both
 * persistence and publication. Access is confined to the main dispatcher by the ViewModel.
 */
internal class FootballCatalogAuthority {
    data class Snapshot(
        val teams: List<FootballTeam>,
        val cachedAtEpochMs: Long,
        val writeToken: FootballCatalogWriteToken,
    )

    private var ownerKey: String? = null
    private var latestRequestId: Long = 0L
    private var publishedRequestId: Long = 0L
    private var current: Snapshot? = null

    fun snapshot(ownerKey: String): Snapshot? =
        current.takeIf { this.ownerKey == ownerKey }

    /** Adopts a new owner only when no authority exists for it; an established authority wins. */
    fun bootstrap(
        ownerKey: String,
        teams: List<FootballTeam>,
        cachedAtEpochMs: Long,
    ): Snapshot? {
        adoptOwner(ownerKey)
        if (current != null) return null
        return Snapshot(
            teams,
            cachedAtEpochMs.coerceAtLeast(0L),
            FootballCatalogWriteOrder.next(),
        ).also { current = it }
    }

    fun beginRequest(ownerKey: String): Long {
        adoptOwner(ownerKey)
        latestRequestId += 1
        return latestRequestId
    }

    /** Returns the accepted snapshot, or null when the response must not be persisted/published. */
    fun publish(
        ownerKey: String,
        requestId: Long,
        teams: List<FootballTeam>,
        cachedAtEpochMs: Long,
        stale: Boolean,
    ): Snapshot? {
        if (this.ownerKey != ownerKey) return null
        if (requestId != latestRequestId || requestId <= publishedRequestId) return null
        val established = current
        if (stale && established != null) return null
        // Provider cache times are only comparable when both are known. A missing provider time
        // stays unknown instead of being replaced by the device receipt clock.
        if (
            established != null &&
            cachedAtEpochMs > 0L &&
            established.cachedAtEpochMs > 0L &&
            cachedAtEpochMs < established.cachedAtEpochMs
        ) {
            return null
        }
        return Snapshot(
            teams,
            cachedAtEpochMs.coerceAtLeast(0L),
            FootballCatalogWriteOrder.next(),
        ).also {
            current = it
            publishedRequestId = requestId
        }
    }

    /**
     * Production gate for a catalog response: stale/obsolete responses never reach the durable
     * cache. The accepted snapshot is persisted before it is published to the UI.
     */
    suspend fun publishAndPersist(
        ownerKey: String,
        requestId: Long,
        teams: List<FootballTeam>,
        cachedAtEpochMs: Long,
        stale: Boolean,
        persist: suspend (Snapshot) -> Unit,
    ): Snapshot? {
        val accepted = publish(ownerKey, requestId, teams, cachedAtEpochMs, stale) ?: return null
        persist(accepted)
        // A persistence block can outlive its request (blocking IO or cancellation). Only the
        // request that still owns the published authority may continue to the UI.
        return accepted.takeIf {
            this.ownerKey == ownerKey && requestId == publishedRequestId
        }
    }

    fun reset() {
        ownerKey = null
        latestRequestId = 0L
        publishedRequestId = 0L
        current = null
    }

    private fun adoptOwner(ownerKey: String) {
        if (this.ownerKey == ownerKey) return
        this.ownerKey = ownerKey
        latestRequestId = 0L
        publishedRequestId = 0L
        current = null
    }
}

/**
 * Applies a catalog authority snapshot to the current football presentation without touching the
 * owner's favorites or real match teams.
 */
internal fun FootballFeedUiState.withCatalogAuthority(
    snapshot: FootballCatalogAuthority.Snapshot,
    sourceKey: String,
): FootballFeedUiState = copy(
    authoritativeCatalog = snapshot.teams,
    authoritativeCatalogSourceKey = sourceKey,
    knownTeams = reconcileFootballCatalogTeams(
        snapshot.teams,
        allMatches,
        favoriteTeams,
    ),
)

/**
 * Production publication gate shared by the feed and day paths. A snapshot for the current source
 * wins. A state that still belongs to a previous discovery source is cleared so the old catalog
 * cannot survive a URL switch; an ordinary owner with no authority keeps its known-team list.
 */
internal fun FootballFeedUiState.withCatalogSource(
    snapshot: FootballCatalogAuthority.Snapshot?,
    sourceKey: String,
): FootballFeedUiState = when {
    snapshot != null -> withCatalogAuthority(snapshot, sourceKey)
    authoritativeCatalogSourceKey != null && authoritativeCatalogSourceKey != sourceKey ->
        withCatalogSourceCleared()
    else -> this
}

/** Drops a catalog that belongs to another discovery source while keeping real fixture/favorites. */
internal fun FootballFeedUiState.withCatalogSourceCleared(): FootballFeedUiState = copy(
    authoritativeCatalog = null,
    authoritativeCatalogSourceKey = null,
    knownTeams = footballTeamsFromMatches(allMatches, favoriteTeams),
)

/**
 * A discovery URL change invalidates every public result from the old source in memory. Private
 * favorites/hide-scores/alerts stay owned by their profile store.
 */
fun footballClearedForDiscoveryChange(state: FootballFeedUiState): FootballFeedUiState =
    state.withCatalogSourceCleared().copy(
        snapshot = null,
        daySnapshots = emptyMap(),
        dayFailures = emptyMap(),
        knownTeams = emptyList(),
        error = null,
        loading = false,
        lastAttemptEpochMs = 0L,
        nextRetryAtEpochMs = 0L,
    )

fun footballKickoffNotificationId(
    accountId: String,
    profileId: String,
    eventId: String,
): String = "kickoff|$accountId|$profileId|$eventId"

fun footballAlertNotificationId(
    accountId: String,
    profileId: String,
    eventId: String,
    alertType: FootballAlertType,
): String = when (alertType) {
    FootballAlertType.KICKOFF -> footballKickoffNotificationId(accountId, profileId, eventId)
    FootballAlertType.REMINDER_15 -> "reminder15|$accountId|$profileId|$eventId"
}

fun footballAlertSuppressionKey(
    accountId: String,
    profileId: String,
    eventId: String,
    alertType: FootballAlertType,
): String = "alert|${alertType.wireName}|$accountId|$profileId|$eventId"

/**
 * A tombstone is bound to one alert type and profile. Legacy `suppressedEventIds` values remain
 * kickoff-only so deleting a reminder can never silence the independent start alert.
 */
fun footballAlertIsSuppressed(
    inbox: FootballInboxState,
    accountId: String,
    profileId: String,
    match: FootballMatch,
    alertType: FootballAlertType,
): Boolean {
    if (alertType == FootballAlertType.KICKOFF && match.eventIds.any { it in inbox.suppressedEventIds }) {
        return true
    }
    return match.eventIds.any { eventId ->
        footballAlertSuppressionKey(accountId, profileId, eventId, alertType) in inbox.suppressedAlerts
    }
}

/**
 * Replacement aliases can leave several preference rows for one fixture. The latest explicit row
 * wins; an old canonical ID can never override a newly saved all-off choice.
 */
fun resolveFootballAlertPreferences(
    preferences: Map<String, FootballMatchAlertPreferences>,
    match: FootballMatch,
): FootballMatchAlertPreferences? = preferences.entries
    .asSequence()
    .filter { (eventId, _) -> eventId in match.eventIds }
    .maxByOrNull { (_, value) -> value.trackedAtEpochMs }
    ?.value

/**
 * One canonical preference row per fixture. Saving unchanged values preserves the earlier
 * eligibility timestamp; a changed or new preference receives the current save time.
 */
fun upsertFootballAlertPreferences(
    current: Map<String, FootballMatchAlertPreferences>,
    match: FootballMatch,
    preferences: FootballMatchAlertPreferences,
    nowEpochMs: Long,
): Map<String, FootballMatchAlertPreferences> {
    val existing = resolveFootballAlertPreferences(current, match)
    val unchanged = existing != null &&
        existing.kickoff == preferences.kickoff &&
        existing.reminder15 == preferences.reminder15 &&
        existing.manuallyTracked == preferences.manuallyTracked
    val canonical = preferences.copy(
        trackedAtEpochMs = if (unchanged) {
            existing?.trackedAtEpochMs ?: preferences.trackedAtEpochMs
        } else {
            nowEpochMs
        },
    )
    return current.toMutableMap().apply {
        keys.removeAll(match.eventIds)
        put(match.providerEventId, canonical)
    }
}

fun resolveFootballMatch(
    current: FootballMatch,
    candidates: Iterable<FootballMatch>,
): FootballMatch {
    val matching = candidates.filter { candidate ->
        candidate.stableIdentity == current.stableIdentity ||
            candidate.eventIds.any { it in current.eventIds }
    }
    val chosen = matching.maxWithOrNull(
        compareBy<FootballMatch> { it.providerUpdatedAtEpochMs ?: 0L }
            .thenBy { footballMatchStatusRank(it.status) },
    ) ?: current
    // Broadcaster assignments belong to the actual fixture instant. Aliases may resolve a
    // replacement/reschedule for display, but only observations with the same provider identity
    // and kickoff may transport channel assignments to the chosen row.
    val sameFixtureObservations = matching.filter {
        it.stableIdentity == chosen.stableIdentity &&
            it.kickoffEpochMs == chosen.kickoffEpochMs
    }
    val broadcasterSources = buildList {
        if (
            chosen.stableIdentity == current.stableIdentity &&
            chosen.kickoffEpochMs == current.kickoffEpochMs
        ) {
            addAll(current.broadcasters)
        }
        sameFixtureObservations.forEach { addAll(it.broadcasters) }
    }
    return chosen.copy(broadcasters = broadcasterSources.distinct())
}

fun footballEffectiveHideScores(
    loaded: Boolean,
    stateAvailable: Boolean,
    stateHideScores: Boolean,
): Boolean = if (stateAvailable) stateHideScores else loaded

fun footballSnapshotCacheFresh(
    snapshot: FootballFeedSnapshot?,
    nowEpochMs: Long,
    refreshSeconds: Int,
): Boolean {
    if (snapshot == null || snapshot.stale) return false
    val ttlMs = refreshSeconds.coerceAtLeast(1) * 1_000L
    val observedAt = snapshot.fetchedAtEpochMs.takeIf { it > 0L } ?: snapshot.cachedAtEpochMs
    return nowEpochMs - observedAt in 0 until ttlMs
}

/**
 * A cached record outside its refresh window is last-good data. Mark it stale for display instead
 * of letting an expired non-stale flag keep hiding a failed or rate-limited refresh.
 */
fun footballSnapshotForDisplay(
    snapshot: FootballFeedSnapshot,
    nowEpochMs: Long,
    refreshSeconds: Int,
): FootballFeedSnapshot = if (
    snapshot.stale || footballSnapshotCacheFresh(snapshot, nowEpochMs, refreshSeconds)
) {
    snapshot
} else {
    snapshot.copy(stale = true)
}

/**
 * A response owns its Retry-After, but another route's active cooldown must not be shortened or
 * erased. The returned deadline is monotonic until it is actually elapsed.
 */
fun footballRetryAtAfterSuccess(
    attemptedAtEpochMs: Long,
    retryAfterSeconds: Long?,
    currentRetryAtEpochMs: Long,
): Long {
    val active = currentRetryAtEpochMs.takeIf { it > attemptedAtEpochMs } ?: 0L
    val requested = retryAfterSeconds
        ?.takeIf { it > 0L }
        ?.let { attemptedAtEpochMs + it * 1_000L }
        ?: 0L
    return maxOf(active, requested)
}

/** A failure cannot shorten an active shared cooldown; a longer Retry-After extends it. */
fun footballRetryAtAfterFailure(
    attemptedAtEpochMs: Long,
    retryAfterSeconds: Long?,
    currentRetryAtEpochMs: Long,
): Long {
    val active = currentRetryAtEpochMs.takeIf { it > attemptedAtEpochMs } ?: 0L
    val requested = retryAfterSeconds
        ?.takeIf { it > 0L }
        ?.let { attemptedAtEpochMs + it * 1_000L }
        ?: 0L
    return maxOf(active, requested)
}

/**
 * One shared retry deadline used by Home and per-day refresh. Time is injectable so the exact
 * backoff/transport eligibility can be tested without sleeping.
 */
class FootballRetryDeadline(
    private val now: () -> Long = System::currentTimeMillis,
) {
    var retryAtEpochMs: Long = 0L
        private set

    fun canAttempt(): Boolean = now() >= retryAtEpochMs

    fun onSuccess(retryAfterSeconds: Long?) {
        retryAtEpochMs = footballRetryAtAfterSuccess(now(), retryAfterSeconds, retryAtEpochMs)
    }

    fun onFailure(retryAfterSeconds: Long?) {
        retryAtEpochMs = footballRetryAtAfterFailure(now(), retryAfterSeconds, retryAtEpochMs)
    }

    fun reset() {
        retryAtEpochMs = 0L
    }
}

/**
 * One start decision for a day request. An in-flight job owns the day, a passive selection may use
 * a fresh cache, and an active shared cooldown blocks even an explicit retry from duplicating
 * transport. Only one press can pass this gate into a real fetch.
 */
internal fun footballShouldStartDayRefresh(
    force: Boolean,
    active: Boolean,
    cacheFresh: Boolean,
    canAttempt: Boolean,
): Boolean = when {
    active -> false
    !force && cacheFresh -> false
    !canAttempt -> false
    else -> true
}

/**
 * A Home publication replaces today's feed fields but owns no other day's failure. Only a real
 * success for that day may clear its entry, and an older/stale Home row must not replace a newer
 * or fresher day authority.
 */
fun footballPublishedFeedState(
    previous: FootballFeedUiState,
    published: FootballFeedUiState,
    successfulDayKey: String? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    requestedDayKey: String? = null,
    publicDayKey: String? = null,
): FootballFeedUiState {
    val publishedSnapshot = published.snapshot
    val dayKey = publishedSnapshot?.dayKey ?: requestedDayKey
    val effectivePublicDayKey = publicDayKey ?: requestedDayKey ?: publishedSnapshot?.dayKey
        ?: footballRiyadhDayKey(nowEpochMs)
    val existing = dayKey?.let { previous.daySnapshots[it] }
    val accepted = publishedSnapshot != null &&
        publishedSnapshot.dayKey == dayKey &&
        footballDayCandidateAccepted(existing, publishedSnapshot, nowEpochMs)
    val acceptedSnapshot = if (accepted) {
        footballAcceptedDaySnapshot(existing, publishedSnapshot!!, nowEpochMs)
    } else {
        null
    }
    val mergedDays = when {
        dayKey == null -> previous.daySnapshots
        acceptedSnapshot != null -> previous.daySnapshots + (dayKey to acceptedSnapshot)
        else -> previous.daySnapshots
    }
    val mergedFailures = if (successfulDayKey == null || !accepted) {
        previous.dayFailures
    } else {
        previous.dayFailures - successfulDayKey
    }
    return published.copy(
        snapshot = footballDisplaySnapshotForDay(
            daySnapshots = mergedDays,
            dayFailures = mergedFailures,
            dayKey = effectivePublicDayKey,
            nowEpochMs = nowEpochMs,
            allowOtherDayFallback = true,
        ),
        daySnapshots = mergedDays,
        dayFailures = mergedFailures,
    )
}

/**
 * The one public projection for a requested day. Today/exact accepted data wins, including valid
 * empty; another day is only a disclosed stale fallback when the requested day has no record.
 * Failure/age changes the display copy; raw provider/receipt freshness is never mutated.
 */
fun footballWithDayFailure(
    state: FootballFeedUiState,
    dayKey: String,
    failure: FootballDayFailure,
    nowEpochMs: Long,
    retryAtEpochMs: Long,
): FootballFeedUiState {
    val snapshot = state.daySnapshots[dayKey]
    if (!footballFailureOwnsResult(snapshot, failure)) {
        return state.copy(nextRetryAtEpochMs = retryAtEpochMs)
    }
    val failures = state.dayFailures + (dayKey to failure)
    return state.copy(
        snapshot = footballDisplaySnapshotForDay(
            daySnapshots = state.daySnapshots,
            dayFailures = failures,
            dayKey = state.snapshot?.dayKey ?: footballRiyadhDayKey(nowEpochMs),
            nowEpochMs = nowEpochMs,
            allowOtherDayFallback = true,
        ),
        dayFailures = failures,
        nextRetryAtEpochMs = retryAtEpochMs,
    )
}

fun footballEffectiveDayFailure(
    daySnapshots: Map<String, FootballFeedSnapshot>,
    dayFailures: Map<String, FootballDayFailure>,
    dayKey: String,
): FootballDayFailure? {
    val failure = dayFailures[dayKey] ?: return null
    val snapshot = daySnapshots[dayKey]
    return failure.takeIf {
        snapshot == null || footballFailureOwnsResult(snapshot, failure)
    }
}

fun footballDisplaySnapshotForDay(
    daySnapshots: Map<String, FootballFeedSnapshot>,
    dayFailures: Map<String, FootballDayFailure>,
    dayKey: String,
    nowEpochMs: Long,
    allowOtherDayFallback: Boolean = false,
): FootballFeedSnapshot? {
    val exact = daySnapshots[dayKey]
    val fallback = if (allowOtherDayFallback && exact == null) {
        daySnapshots.values
            .filter { it.dayKey < dayKey }
            .maxByOrNull { it.cachedAtEpochMs }
            ?.copy(stale = true)
    } else {
        null
    }
    val chosen = exact ?: fallback ?: return null
    val failure = dayFailures[dayKey]
    return if (failure != null && footballFailureOwnsResult(chosen, failure)) {
        chosen.copy(stale = true)
    } else {
        footballSnapshotForDisplay(chosen, nowEpochMs, chosen.refreshSeconds)
    }
}

/** True when a failure is newer than the current accepted same-day row (or owns no row). */
fun footballFailureOwnsResult(
    snapshot: FootballFeedSnapshot?,
    failure: FootballDayFailure,
): Boolean {
    if (failure.startedAtEpochMs <= 0L || snapshot == null) return true
    if (snapshot.stale) return true
    return snapshot.fetchedAtEpochMs < failure.startedAtEpochMs
}

/**
 * One same-day acceptance rule for memory and durable cache. A fresh accepted record beats an older
 * stale observation; an expired record yields to a newer last-good row. Equal/newer provider time
 * wins within the same freshness class.
 */
fun footballDayCandidateAccepted(
    existing: FootballFeedSnapshot?,
    candidate: FootballFeedSnapshot,
    nowEpochMs: Long,
): Boolean {
    if (existing == null) return true
    val existingFresh = footballSnapshotCacheFresh(existing, nowEpochMs, existing.refreshSeconds)
    val candidateFresh = !candidate.stale &&
        footballSnapshotCacheFresh(candidate, nowEpochMs, candidate.refreshSeconds)
    return when {
        candidate.cachedAtEpochMs > existing.cachedAtEpochMs ->
            !(existingFresh && candidate.stale)
        candidate.cachedAtEpochMs < existing.cachedAtEpochMs -> false
        else -> {
            // Equal provider time: only an unchanged fresh network revalidation may renew receipt.
            // A different or older payload never replaces accepted content on a tie.
            val sameContent = candidate.copy(
                fetchedAtEpochMs = existing.fetchedAtEpochMs,
            ) == existing
            sameContent &&
                candidateFresh &&
                candidate.fetchedAtEpochMs > existing.fetchedAtEpochMs
        }
    }
}

data class FootballDayAcceptance(
    val accepted: Boolean,
    val snapshot: FootballFeedSnapshot,
)

/** Shared acceptance result, including an unchanged fresh revalidation that renews receipt. */
fun footballDayAcceptance(
    existing: FootballFeedSnapshot?,
    candidate: FootballFeedSnapshot,
    nowEpochMs: Long,
): FootballDayAcceptance {
    val accepted = footballDayCandidateAccepted(existing, candidate, nowEpochMs)
    return FootballDayAcceptance(
        accepted = accepted,
        snapshot = if (accepted) {
            footballAcceptedDaySnapshot(existing, candidate, nowEpochMs)
        } else {
            checkNotNull(existing)
        },
    )
}

fun footballWithClearedDayFailure(
    state: FootballFeedUiState,
    dayKey: String,
    nowEpochMs: Long,
): FootballFeedUiState {
    if (dayKey !in state.dayFailures) return state
    val failures = state.dayFailures - dayKey
    return state.copy(
        snapshot = footballDisplaySnapshotForDay(
            daySnapshots = state.daySnapshots,
            dayFailures = failures,
            dayKey = state.snapshot?.dayKey ?: footballRiyadhDayKey(nowEpochMs),
            nowEpochMs = nowEpochMs,
            allowOtherDayFallback = true,
        ),
        dayFailures = failures,
    )
}

fun footballAcceptedDaySnapshot(
    existing: FootballFeedSnapshot?,
    candidate: FootballFeedSnapshot,
    nowEpochMs: Long,
): FootballFeedSnapshot = when {
    !footballDayCandidateAccepted(existing, candidate, nowEpochMs) -> checkNotNull(existing)
    existing != null &&
        candidate.cachedAtEpochMs == existing.cachedAtEpochMs &&
        candidate.copy(fetchedAtEpochMs = existing.fetchedAtEpochMs) == existing ->
        existing.copy(fetchedAtEpochMs = candidate.fetchedAtEpochMs)
    else -> candidate
}

/** Provider/server observation freshness; app receipt time alone is not an observation. */
fun footballSnapshotObservationFresh(
    snapshot: FootballFeedSnapshot,
    nowEpochMs: Long,
): Boolean {
    if (snapshot.stale) return false
    val observedAt = snapshot.cachedAtEpochMs.takeIf { it > 0L } ?: return false
    val maxAge = maxOf(
        FOOTBALL_ALERT_OBSERVATION_MAX_AGE_MS,
        snapshot.refreshSeconds.coerceAtLeast(1) * 2_000L,
    )
    return nowEpochMs - observedAt in 0..maxAge
}

const val FOOTBALL_ALERT_OBSERVATION_MAX_AGE_MS: Long = 10L * 60L * 1_000L

fun footballKickoffNotificationFor(
    accountId: String,
    profileId: String,
    match: FootballMatch,
    detectedAtEpochMs: Long,
    startedAtEpochMs: Long? = null,
    alertType: FootballAlertType = FootballAlertType.KICKOFF,
): FootballKickoffNotification = FootballKickoffNotification(
    id = footballAlertNotificationId(accountId, profileId, match.providerEventId, alertType),
    accountId = accountId,
    profileId = profileId,
    eventId = match.providerEventId,
    eventIds = match.eventIds,
    competitionName = match.competition.displayName,
    homeTeamName = match.homeTeam.displayName,
    awayTeamName = match.awayTeam.displayName,
    kickoffEpochMs = match.kickoffEpochMs,
    startedAtEpochMs = startedAtEpochMs ?: match.startedAtEpochMs ?: match.kickoffEpochMs,
    detectedAtEpochMs = detectedAtEpochMs,
    alertType = alertType,
    read = false,
    homeScore = match.homeScore,
    awayScore = match.awayScore,
    status = match.status,
)

/**
 * Confirm a kickoff from a fresh provider transition or a provider-confirmed started status whose
 * actual kickoff timestamp is safely in the past. Clock passage alone never creates an inbox item.
 */
fun generateFootballMatchAlerts(
    accountId: String,
    profileId: String,
    favoriteTeams: List<FootballFavoriteTeam>,
    preferences: Map<String, FootballMatchAlertPreferences>,
    previous: FootballFeedSnapshot?,
    current: FootballFeedSnapshot,
    inbox: FootballInboxState,
    nowEpochMs: Long,
    catchUpOnly: Boolean = false,
    previousObservedInSession: Boolean = false,
    previousObservedAtEpochMs: Long = 0L,
): FootballKickoffGeneration {
    if (accountId.isBlank() || profileId.isBlank()) {
        return FootballKickoffGeneration(inbox, emptyList())
    }
    val working = inbox.notifications.associateBy { it.id }.toMutableMap()
    val added = mutableListOf<FootballKickoffNotification>()
    val candidates = mergeFootballMatchRows(
        current.kickoffHistory + current.matches + current.reminderCandidates,
    )
    val previousMatches = mergeFootballMatchRows(
        previous?.kickoffHistory.orEmpty() +
            previous?.matches.orEmpty() +
            previous?.reminderCandidates.orEmpty(),
    )
    val historyCutoff = nowEpochMs - FOOTBALL_KICKOFF_CATCH_UP_WINDOW_MS
    val observationFresh = footballSnapshotObservationFresh(current, nowEpochMs)

    fun preferenceFor(match: FootballMatch): FootballMatchAlertPreferences? =
        resolveFootballAlertPreferences(preferences, match)

    fun alertFor(
        match: FootballMatch,
        alertType: FootballAlertType,
    ): FootballKickoffNotification? = working.values.firstOrNull { notification ->
        notification.alertType == alertType && notification.eventIds.any { it in match.eventIds }
    }

    if (observationFresh) {
        candidates
            .asSequence()
            .filter { match ->
                val preference = preferenceFor(match)
                val followed = favoriteTeams.any { match.involvesTeam(it.teamId) }
                val kickoff = preference?.kickoff ?: followed
                val hasExisting = working.values.any { notification ->
                    notification.eventIds.any { it in match.eventIds }
                }
                kickoff || (preference?.reminder15 == true) ||
                    (preference?.manuallyTracked == true) || hasExisting
            }
            .sortedBy(FootballMatch::kickoffEpochMs)
            .forEach { match ->
                val preference = preferenceFor(match)
                val followedTeams = favoriteTeams.filter { match.involvesTeam(it.teamId) }
                val qualifyingTimes = buildList {
                    followedTeams.mapNotNullTo(this) { favorite ->
                        favorite.selectedAtEpochMs.takeIf { it > 0L }
                    }
                    preference?.trackedAtEpochMs?.takeIf { it > 0L }?.let(::add)
                }
                // Any earlier qualifying subscription is enough; a later selection of the
                // opposing team must not invalidate it.
                val relevantSince = qualifyingTimes.minOrNull() ?: 0L
                val kickoffEnabled = preference?.kickoff ?: followedTeams.isNotEmpty()
                val reminderEnabled = preference?.reminder15 == true
                val prior = previousMatches.firstOrNull { candidate ->
                    candidate.eventIds.any { it in match.eventIds }
                }
                val freshTransition = previousObservedInSession &&
                    previousObservedAtEpochMs >= relevantSince &&
                    prior != null && !prior.status.isStarted && match.status.isStarted
                val providerStart = (match.startedAtEpochMs ?: match.kickoffEpochMs)
                    .coerceAtMost(nowEpochMs)
                val providerObservedAt = match.providerUpdatedAtEpochMs
                    ?.takeIf { it > 0L }
                    ?: current.cachedAtEpochMs
                val providerTimestampConfirmed = providerStart > 0L &&
                    providerStart + FOOTBALL_KICKOFF_CONFIRM_GRACE_MS <= nowEpochMs &&
                    providerObservedAt >= providerStart
                val confirmedStart = when {
                    freshTransition -> nowEpochMs
                    providerTimestampConfirmed -> providerStart
                    else -> null
                }

                val existingKickoff = alertFor(match, FootballAlertType.KICKOFF)
                if (existingKickoff != null) {
                    // Keep delivered history alive and current even if the fixture is later
                    // cancelled, rescheduled or temporarily absent from the selected day.
                    working[existingKickoff.id] = existingKickoff.copy(
                        eventIds = existingKickoff.eventIds + match.eventIds,
                        competitionName = match.competition.displayName,
                        homeTeamName = match.homeTeam.displayName,
                        awayTeamName = match.awayTeam.displayName,
                        homeScore = match.homeScore ?: existingKickoff.homeScore,
                        awayScore = match.awayScore ?: existingKickoff.awayScore,
                        status = match.status,
                        kickoffEpochMs = match.kickoffEpochMs,
                        startedAtEpochMs = existingKickoff.startedAtEpochMs
                            .takeIf { it > 0L } ?: match.startedAtEpochMs ?: confirmedStart
                            ?: existingKickoff.kickoffEpochMs,
                    )
                }
                if (
                    kickoffEnabled &&
                    !footballAlertIsSuppressed(
                        inbox,
                        accountId,
                        profileId,
                        match,
                        FootballAlertType.KICKOFF,
                    ) &&
                    match.status.isStarted && confirmedStart != null &&
                    confirmedStart >= historyCutoff && confirmedStart >= relevantSince &&
                    confirmedStart > inbox.clearedThroughEpochMs &&
                    (!catchUpOnly || confirmedStart >= relevantSince)
                ) {
                    if (existingKickoff == null) {
                        val notification = footballKickoffNotificationFor(
                            accountId = accountId,
                            profileId = profileId,
                            match = match,
                            detectedAtEpochMs = nowEpochMs,
                            startedAtEpochMs = confirmedStart,
                            alertType = FootballAlertType.KICKOFF,
                        )
                        working[notification.id] = notification
                        added += notification
                    }
                }

                val reminderAt = match.kickoffEpochMs - 15 * 60_000L
                val existingReminder = alertFor(match, FootballAlertType.REMINDER_15)
                if (existingReminder != null) {
                    // Rescheduling updates one canonical reminder item; read/delete ownership is
                    // never reset by a refresh.
                    working[existingReminder.id] = existingReminder.copy(
                        eventIds = existingReminder.eventIds + match.eventIds,
                        competitionName = match.competition.displayName,
                        homeTeamName = match.homeTeam.displayName,
                        awayTeamName = match.awayTeam.displayName,
                        kickoffEpochMs = match.kickoffEpochMs,
                        startedAtEpochMs = reminderAt,
                        status = match.status,
                        homeScore = null,
                        awayScore = null,
                    )
                } else if (
                    reminderEnabled &&
                    !footballAlertIsSuppressed(
                        inbox,
                        accountId,
                        profileId,
                        match,
                        FootballAlertType.REMINDER_15,
                    ) &&
                    match.status == FootballMatchStatus.UPCOMING &&
                    match.kickoffEpochMs > nowEpochMs &&
                    reminderAt <= nowEpochMs
                ) {
                    val notification = footballKickoffNotificationFor(
                        accountId = accountId,
                        profileId = profileId,
                        match = match,
                        detectedAtEpochMs = nowEpochMs,
                        startedAtEpochMs = reminderAt,
                        alertType = FootballAlertType.REMINDER_15,
                    )
                    working[notification.id] = notification
                    added += notification
                }
            }
    }

    val retained = working.values
        .filter { notification ->
            val startedAt = notification.startedAtEpochMs.takeIf { it > 0L }
                ?: notification.kickoffEpochMs
            startedAt >= historyCutoff
        }
        .sortedByDescending(FootballKickoffNotification::detectedAtEpochMs)
        .take(FOOTBALL_MAX_KICKOFF_NOTIFICATIONS_PER_PROFILE)
    return FootballKickoffGeneration(
        inbox = inbox.copy(notifications = retained),
        added = added,
    )
}

/** Single-team compatibility wrapper retained for focused kickoff tests and old callers. */
fun generateFootballKickoffNotifications(
    accountId: String,
    profileId: String,
    favoriteTeam: FootballFavoriteTeam,
    previous: FootballFeedSnapshot?,
    current: FootballFeedSnapshot,
    inbox: FootballInboxState,
    nowEpochMs: Long,
    catchUpOnly: Boolean = false,
    previousObservedInSession: Boolean = previous != null,
    previousObservedAtEpochMs: Long = 0L,
): FootballKickoffGeneration = generateFootballMatchAlerts(
    accountId = accountId,
    profileId = profileId,
    favoriteTeams = if (favoriteTeam.teamId.isBlank()) emptyList() else listOf(favoriteTeam),
    preferences = emptyMap(),
    previous = previous,
    current = current,
    inbox = inbox,
    nowEpochMs = nowEpochMs,
    catchUpOnly = catchUpOnly,
    previousObservedInSession = previousObservedInSession,
    previousObservedAtEpochMs = previousObservedAtEpochMs,
)

fun markFootballKickoffRead(
    inbox: FootballInboxState,
    notificationId: String,
): FootballInboxState = inbox.copy(
    notifications = inbox.notifications.map { notification ->
        if (notification.id == notificationId) notification.copy(read = true) else notification
    },
)

fun markAllFootballKickoffsRead(inbox: FootballInboxState): FootballInboxState =
    inbox.copy(notifications = inbox.notifications.map { it.copy(read = true) })

fun deleteFootballKickoff(
    inbox: FootballInboxState,
    notificationId: String,
): FootballInboxState {
    val removed = inbox.notifications.firstOrNull { it.id == notificationId } ?: return inbox
    val tombstones = removed.eventIds.mapTo(linkedSetOf()) { eventId ->
        footballAlertSuppressionKey(
            removed.accountId,
            removed.profileId,
            eventId,
            removed.alertType,
        )
    }
    return inbox.copy(
        notifications = inbox.notifications.filterNot { it.id == notificationId },
        suppressedEventIds = if (removed.alertType == FootballAlertType.KICKOFF) {
            inbox.suppressedEventIds + removed.eventIds
        } else {
            inbox.suppressedEventIds
        },
        suppressedAlerts = inbox.suppressedAlerts + tombstones,
    )
}

/** Clear-all records independent tombstones per alert type and keeps the legacy kickoff watermark. */
fun clearFootballKickoffs(inbox: FootballInboxState): FootballInboxState {
    if (inbox.notifications.isEmpty()) return inbox
    val clearedThrough = inbox.notifications.maxOfOrNull { notification ->
        notification.startedAtEpochMs.takeIf { it > 0L } ?: notification.kickoffEpochMs
    } ?: 0L
    return inbox.copy(
        notifications = emptyList(),
        suppressedEventIds = inbox.suppressedEventIds +
            inbox.notifications
                .filter { it.alertType == FootballAlertType.KICKOFF }
                .flatMapTo(linkedSetOf(), FootballKickoffNotification::eventIds),
        suppressedAlerts = inbox.suppressedAlerts +
            inbox.notifications.flatMapTo(linkedSetOf()) { notification ->
                notification.eventIds.map { eventId ->
                    footballAlertSuppressionKey(
                        notification.accountId,
                        notification.profileId,
                        eventId,
                        notification.alertType,
                    )
                }
            },
        clearedThroughEpochMs = maxOf(inbox.clearedThroughEpochMs, clearedThrough),
    )
}

sealed interface FootballForegroundRefreshDecision {
    data object WaitForWake : FootballForegroundRefreshDecision
    data class WaitForDelay(val delayMs: Long) : FootballForegroundRefreshDecision
    data object RefreshNow : FootballForegroundRefreshDecision
}

/**
 * One foreground due-check for the server-owned TTL and Retry-After window. Background, missing
 * owner/config, Kids and an active request wait for a real lifecycle/config/team wake instead of
 * polling.
 */
fun decideFootballForegroundRefresh(
    foreground: Boolean,
    enabled: Boolean,
    authenticated: Boolean,
    profileReady: Boolean,
    kidsProfile: Boolean,
    requestActive: Boolean,
    refreshSeconds: Int,
    nowEpochMs: Long,
    lastAttemptEpochMs: Long,
    retryAfterEpochMs: Long,
): FootballForegroundRefreshDecision {
    if (!foreground || !enabled || !authenticated || !profileReady || kidsProfile) {
        return FootballForegroundRefreshDecision.WaitForWake
    }
    if (requestActive) {
        return FootballForegroundRefreshDecision.WaitForDelay(1_000L)
    }
    val ttlMs = refreshSeconds.coerceIn(FOOTBALL_MIN_REFRESH_SECONDS, FOOTBALL_MAX_REFRESH_SECONDS) * 1_000L
    val dueAt = maxOf(
        if (lastAttemptEpochMs > 0L) lastAttemptEpochMs + ttlMs else nowEpochMs,
        retryAfterEpochMs,
    )
    return if (dueAt <= nowEpochMs) {
        FootballForegroundRefreshDecision.RefreshNow
    } else {
        FootballForegroundRefreshDecision.WaitForDelay(dueAt - nowEpochMs)
    }
}

fun footballKickoffBadgeCount(inbox: FootballInboxState): Int = inbox.unreadCount

/** Football is external, unfiltered content; Kids profiles keep the existing fail-closed policy. */
fun footballFeedAllowedForProfile(kind: sa.hulksa.player.model.ProfileKind): Boolean =
    kind != sa.hulksa.player.model.ProfileKind.KIDS

fun footballKickoffHeadline(notification: FootballKickoffNotification): String =
    "${notification.homeTeamName} × ${notification.awayTeamName}"

fun footballKickoffDetail(
    notification: FootballKickoffNotification,
    hideScores: Boolean = false,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    val remainingMinutes = ((notification.kickoffEpochMs - nowEpochMs) / 60_000L)
        .let { if (notification.kickoffEpochMs > nowEpochMs) it + 1L else it }
    val reminderPending = notification.alertType == FootballAlertType.REMINDER_15 &&
        notification.status == FootballMatchStatus.UPCOMING
    val status = when {
        reminderPending && remainingMinutes in 1..15 ->
            "تبدأ بعد $remainingMinutes دقيقة"
        reminderPending && remainingMinutes > 15 ->
            "تبدأ ${footballRiyadhTimeLabel(notification.kickoffEpochMs)}"
        reminderPending ->
            "موعد البدء ${footballRiyadhTimeLabel(notification.kickoffEpochMs)}"
        notification.status == FootballMatchStatus.LIVE -> "مباشر"
        notification.status == FootballMatchStatus.FINISHED -> "انتهت"
        notification.status == FootballMatchStatus.POSTPONED -> "مؤجلة"
        notification.status == FootballMatchStatus.CANCELLED -> "ملغاة"
        notification.status == FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
        else -> "لم تبدا"
    }
    val score = if (!hideScores && notification.alertType == FootballAlertType.KICKOFF &&
        notification.status.isStarted
    ) {
        " • ${notification.homeScore ?: "-"} - ${notification.awayScore ?: "-"}"
    } else {
        ""
    }
    return "${notification.competitionName} • ${footballRiyadhTimeLabel(notification.kickoffEpochMs)} • $status$score"
}

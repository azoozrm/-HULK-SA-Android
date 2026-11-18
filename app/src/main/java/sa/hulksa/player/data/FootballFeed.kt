package sa.hulksa.player.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val FOOTBALL_RIYADH_TIMEZONE = "Asia/Riyadh"
const val FOOTBALL_FEED_SCHEMA_VERSION = 1
const val FOOTBALL_LIVE_CARRY_OVER_MS = 6L * 60L * 60L * 1_000L
const val FOOTBALL_RESULT_CARRY_OVER_MS = 3L * 60L * 60L * 1_000L
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

data class FootballCompetition(
    val id: String,
    val name: String,
    val country: String? = null,
)

data class FootballTeam(
    val id: String,
    val name: String,
)

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
) {
    val isEmptyDay: Boolean get() = matches.isEmpty() && !stale
}

data class FootballFavoriteTeam(
    val teamId: String,
    val teamName: String,
    val selectedAtEpochMs: Long,
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
    val detectedAtEpochMs: Long,
    val read: Boolean,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: FootballMatchStatus,
)

data class FootballInboxState(
    val notifications: List<FootballKickoffNotification> = emptyList(),
    val suppressedEventIds: Set<String> = emptySet(),
    val clearedThroughEpochMs: Long = 0L,
) {
    val unreadCount: Int get() = notifications.count { !it.read }
}

data class FootballFeedUiState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val snapshot: FootballFeedSnapshot? = null,
    val favoriteTeam: FootballFavoriteTeam? = null,
    val knownTeams: List<FootballTeam> = emptyList(),
    val error: FootballFeedError? = null,
    val lastAttemptEpochMs: Long = 0L,
) {
    val matches: List<FootballMatch> get() = snapshot?.matches.orEmpty()
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
    footballRiyadhDayKey(nowEpochMs - 24L * 60L * 60L * 1_000L) -> "امس"
    else -> dayKey
}

private fun FootballMatch.isOnOrCarriedInto(dayKey: String): Boolean {
    val dayStart = footballRiyadhDayStartEpochMs(dayKey) ?: return false
    val dayEnd = dayStart + 24L * 60L * 60L * 1_000L
    if (kickoffEpochMs >= dayStart && kickoffEpochMs < dayEnd) return true
    if (status == FootballMatchStatus.LIVE &&
        kickoffEpochMs >= dayStart - FOOTBALL_LIVE_CARRY_OVER_MS &&
        kickoffEpochMs < dayStart
    ) {
        return true
    }
    return status == FootballMatchStatus.FINISHED &&
        kickoffEpochMs >= dayStart - FOOTBALL_RESULT_CARRY_OVER_MS &&
        kickoffEpochMs < dayStart
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
            match.kickoffEpochMs >= dayStart - FOOTBALL_LIVE_CARRY_OVER_MS ||
            match.status == FootballMatchStatus.FINISHED &&
            match.kickoffEpochMs >= dayStart - FOOTBALL_RESULT_CARRY_OVER_MS
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

/** Favorite-team fixtures lead; live then upcoming then results inside each group. */
fun orderFootballMatches(matches: List<FootballMatch>, favoriteTeamId: String?): List<FootballMatch> =
    matches.distinctBy(FootballMatch::stableIdentity).sortedWith(
        compareBy<FootballMatch> { if (favoriteTeamId != null && it.involvesTeam(favoriteTeamId)) 0 else 1 }
            .thenBy { footballMatchSortOrder(it.status) }
            .thenBy(FootballMatch::kickoffEpochMs)
            .thenBy(FootballMatch::providerEventId),
    )

fun footballTeamsFromMatches(
    matches: List<FootballMatch>,
    favoriteTeam: FootballFavoriteTeam? = null,
): List<FootballTeam> = buildList {
    addAll(matches.flatMap { listOf(it.homeTeam, it.awayTeam) })
    favoriteTeam?.let { add(FootballTeam(it.teamId, it.teamName)) }
}.distinctBy(FootballTeam::id)
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

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
    return teams.filter { normalizeFootballSearchText(it.name).contains(normalized) }
}

/** Stale feed/inbox responses are valid only for the exact account/profile + request generation. */
internal fun footballOwnerStillCurrent(
    expected: AccountProfileStateScope,
    active: AccountProfileStateScope?,
    expectedGeneration: Long,
    currentGeneration: Long,
): Boolean = active == expected && expectedGeneration == currentGeneration

fun footballKickoffNotificationId(
    accountId: String,
    profileId: String,
    eventId: String,
): String = "kickoff|$accountId|$profileId|$eventId"

fun footballKickoffNotificationFor(
    accountId: String,
    profileId: String,
    match: FootballMatch,
    detectedAtEpochMs: Long,
): FootballKickoffNotification = FootballKickoffNotification(
    id = footballKickoffNotificationId(accountId, profileId, match.providerEventId),
    accountId = accountId,
    profileId = profileId,
    eventId = match.providerEventId,
    eventIds = match.eventIds,
    competitionName = match.competition.name,
    homeTeamName = match.homeTeam.name,
    awayTeamName = match.awayTeam.name,
    kickoffEpochMs = match.kickoffEpochMs,
    detectedAtEpochMs = detectedAtEpochMs,
    read = false,
    homeScore = match.homeScore,
    awayScore = match.awayScore,
    status = match.status,
)

/**
 * Confirm a kickoff from a fresh provider transition or a provider-confirmed started status whose
 * actual kickoff timestamp is safely in the past. Clock passage alone never creates an inbox item.
 */
fun generateFootballKickoffNotifications(
    accountId: String,
    profileId: String,
    favoriteTeam: FootballFavoriteTeam,
    previous: FootballFeedSnapshot?,
    current: FootballFeedSnapshot,
    inbox: FootballInboxState,
    nowEpochMs: Long,
    catchUpOnly: Boolean = false,
): FootballKickoffGeneration {
    if (accountId.isBlank() || profileId.isBlank() || favoriteTeam.teamId.isBlank()) {
        return FootballKickoffGeneration(inbox, emptyList())
    }
    val working = inbox.notifications.associateBy { it.id }.toMutableMap()
    val added = mutableListOf<FootballKickoffNotification>()

    current.matches
        .asSequence()
        .filter { it.involvesTeam(favoriteTeam.teamId) }
        .filter { it.status.isStarted }
        .filter { it.kickoffEpochMs in 1..nowEpochMs }
        .filter { it.kickoffEpochMs >= nowEpochMs - FOOTBALL_KICKOFF_CATCH_UP_WINDOW_MS }
        .filter { it.kickoffEpochMs >= favoriteTeam.selectedAtEpochMs }
        .filter { it.kickoffEpochMs > inbox.clearedThroughEpochMs }
        .filter { match -> match.eventIds.none { it in inbox.suppressedEventIds } }
        .sortedBy(FootballMatch::kickoffEpochMs)
        .forEach { match ->
            val prior = previous?.matches.orEmpty().firstOrNull { candidate ->
                candidate.eventIds.any { it in match.eventIds }
            }
            val freshTransition = !current.stale &&
                prior != null &&
                !prior.status.isStarted &&
                match.status.isStarted
            val trustworthyTimestamp = match.kickoffEpochMs + FOOTBALL_KICKOFF_CONFIRM_GRACE_MS <= nowEpochMs
            if (!catchUpOnly && !freshTransition && !trustworthyTimestamp) return@forEach

            val existing = working.values.firstOrNull { it.eventIds.any { id -> id in match.eventIds } }
            if (existing != null) {
                // A stale last-good snapshot must never regress a fresher live score/status.
                if (current.stale) return@forEach
                working[existing.id] = existing.copy(
                    eventIds = existing.eventIds + match.eventIds,
                    competitionName = match.competition.name,
                    homeTeamName = match.homeTeam.name,
                    awayTeamName = match.awayTeam.name,
                    homeScore = match.homeScore,
                    awayScore = match.awayScore,
                    status = match.status,
                )
                return@forEach
            }
            val notification = footballKickoffNotificationFor(
                accountId = accountId,
                profileId = profileId,
                match = match,
                detectedAtEpochMs = nowEpochMs,
            )
            working[notification.id] = notification
            added += notification
        }

    val retained = working.values
        .sortedByDescending(FootballKickoffNotification::detectedAtEpochMs)
        .take(FOOTBALL_MAX_KICKOFF_NOTIFICATIONS_PER_PROFILE)
    return FootballKickoffGeneration(
        inbox = inbox.copy(notifications = retained),
        added = added,
    )
}

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
    return inbox.copy(
        notifications = inbox.notifications.filterNot { it.id == notificationId },
        suppressedEventIds = inbox.suppressedEventIds + removed.eventIds,
    )
}

/** Clear-all records the newest cleared kickoff as a watermark so a resync cannot re-add it. */
fun clearFootballKickoffs(inbox: FootballInboxState): FootballInboxState {
    val clearedThrough = inbox.notifications.maxOfOrNull(FootballKickoffNotification::kickoffEpochMs)
        ?: return inbox.copy(notifications = emptyList())
    return inbox.copy(
        notifications = emptyList(),
        suppressedEventIds = inbox.suppressedEventIds +
            inbox.notifications.flatMapTo(linkedSetOf(), FootballKickoffNotification::eventIds),
        clearedThroughEpochMs = maxOf(inbox.clearedThroughEpochMs, clearedThrough),
    )
}

fun footballKickoffBadgeCount(inbox: FootballInboxState): Int = inbox.unreadCount

/** Football is external, unfiltered content; Kids profiles keep the existing fail-closed policy. */
fun footballFeedAllowedForProfile(kind: sa.hulksa.player.model.ProfileKind): Boolean =
    kind != sa.hulksa.player.model.ProfileKind.KIDS

fun footballKickoffHeadline(notification: FootballKickoffNotification): String =
    "${notification.homeTeamName} × ${notification.awayTeamName}"

fun footballKickoffDetail(notification: FootballKickoffNotification): String {
    val status = when (notification.status) {
        FootballMatchStatus.LIVE -> "مباشر"
        FootballMatchStatus.FINISHED -> "انتهت"
        FootballMatchStatus.POSTPONED -> "مؤجلة"
        FootballMatchStatus.CANCELLED -> "ملغاة"
        FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
        FootballMatchStatus.UPCOMING -> "انطلاق ${footballRiyadhTimeLabel(notification.kickoffEpochMs)}"
    }
    val score = if (notification.status.isStarted) {
        " • ${notification.homeScore ?: "-"} - ${notification.awayScore ?: "-"}"
    } else {
        ""
    }
    return "${notification.competitionName} • ${footballRiyadhTimeLabel(notification.kickoffEpochMs)} • $status$score"
}

package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Public last-good football feed cache. The feed is account-independent; the favorite team and
 * kickoff inbox are account/profile-scoped in [FootballProfileStateStore].
 */
class FootballFeedCacheStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(requestedDay: String): FootballFeedSnapshot? {
        val exact = readDay(requestedDay)
        if (exact != null) return exact
        val lastDay = preferences.getString(KEY_LAST_DAY, null)?.trim().orEmpty()
        if (lastDay.isBlank() || lastDay == requestedDay) return null
        return readDay(lastDay)?.let { snapshot ->
            snapshot.copy(stale = true, dayKey = lastDay)
        }
    }

    @Synchronized
    fun save(dayKey: String, rawJson: String) {
        if (dayKey.isBlank() || rawJson.isBlank()) return
        val editor = preferences.edit()
            .putString(dayKeyKey(dayKey), rawJson)
            .putString(KEY_LAST_DAY, dayKey)
        prune(editor, dayKey)
        editor.commit()
    }

    @Synchronized
    fun lastAttemptEpochMs(): Long = preferences.getLong(KEY_LAST_ATTEMPT, 0L)

    @Synchronized
    fun recordAttempt(epochMs: Long) {
        preferences.edit().putLong(KEY_LAST_ATTEMPT, epochMs).apply()
    }

    private fun readDay(dayKey: String): FootballFeedSnapshot? {
        val raw = preferences.getString(dayKeyKey(dayKey), null) ?: return null
        return parseFootballFeed(raw, dayKey)
    }

    private fun prune(editor: android.content.SharedPreferences.Editor, keepDay: String) {
        val known = preferences.getStringSet(KEY_KNOWN_DAYS, emptySet()).orEmpty()
            .toMutableSet()
        known += keepDay
        val retained = known.sortedDescending().take(MAX_CACHED_DAYS).toMutableSet()
        known.filterNot { it in retained }.forEach { editor.remove(dayKeyKey(it)) }
        editor.putStringSet(KEY_KNOWN_DAYS, retained)
    }

    private fun dayKeyKey(dayKey: String): String = "day:$dayKey"

    private companion object {
        const val PREFERENCES_NAME = "hulk_football_feed_cache_v1"
        const val KEY_LAST_DAY = "last_day"
        const val KEY_KNOWN_DAYS = "known_days"
        const val KEY_LAST_ATTEMPT = "last_attempt"
        const val MAX_CACHED_DAYS = 3
    }
}

data class FootballTeamCatalogCache(
    val teams: List<FootballTeam>,
    val fetchedAtEpochMs: Long,
)

class FootballTeamCatalogStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): FootballTeamCatalogCache? {
        val fetchedAt = preferences.getLong(KEY_FETCHED_AT, 0L)
        val raw = preferences.getString(KEY_RAW, null) ?: return null
        val teams = parseFootballTeamCatalog(raw) ?: return null
        return FootballTeamCatalogCache(teams, fetchedAt)
    }

    @Synchronized
    fun save(rawJson: String, fetchedAtEpochMs: Long) {
        if (fetchedAtEpochMs <= 0L || parseFootballTeamCatalog(rawJson) == null) return
        preferences.edit()
            .putString(KEY_RAW, rawJson)
            .putLong(KEY_FETCHED_AT, fetchedAtEpochMs)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_football_team_catalog_v1"
        const val KEY_RAW = "raw"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}

/**
 * Multiple favorite teams, score visibility, per-fixture alert preferences and one kickoff inbox
 * per resolved account/profile. Reads fail closed until both owners exist; writes use the exact
 * scope captured by the caller so a stale response cannot land in another profile.
 */
internal class FootballProfileStateStore(context: Context) {
    private val state = AccountProfileStateStore(
        context = context.applicationContext,
        basePreferencesName = PREFERENCES_NAME,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )

    fun activeScope(): AccountProfileStateScope? = state.activeScope()

    fun favoriteTeam(): FootballFavoriteTeam? = activeScope()?.let(::favoriteTeams)?.firstOrNull()

    fun favoriteTeam(scope: AccountProfileStateScope): FootballFavoriteTeam? =
        favoriteTeams(scope).firstOrNull()

    fun favoriteTeams(scope: AccountProfileStateScope): List<FootballFavoriteTeam> {
        val encoded = state.read(scope, KEY_FAVORITE_TEAMS)
        if (encoded != null) return decodeFootballFavoriteTeams(encoded)
        val legacyRaw = state.read(scope, KEY_FAVORITE_TEAM) ?: return emptyList()
        val migrated = migratedFootballFavoriteTeams(null, legacyRaw)
        if (migrated.isEmpty()) return emptyList()
        state.write(scope, KEY_FAVORITE_TEAMS, encodeFootballFavoriteTeams(migrated))
        state.remove(scope.accountId, scope.profileId, KEY_FAVORITE_TEAM)
        return migrated
    }

    fun saveFavoriteTeams(teams: List<FootballFavoriteTeam>) {
        activeScope()?.let { saveFavoriteTeams(it, teams) }
    }

    fun saveFavoriteTeams(scope: AccountProfileStateScope, teams: List<FootballFavoriteTeam>) {
        state.write(scope, KEY_FAVORITE_TEAMS, encodeFootballFavoriteTeams(teams))
    }

    fun saveFavoriteTeam(team: FootballFavoriteTeam) {
        activeScope()?.let { saveFavoriteTeams(it, listOf(team)) }
    }

    fun saveFavoriteTeam(scope: AccountProfileStateScope, team: FootballFavoriteTeam) {
        saveFavoriteTeams(scope, listOf(team))
    }

    fun clearFavoriteTeams() {
        activeScope()?.let { clearFavoriteTeams(it) }
    }

    fun clearFavoriteTeams(scope: AccountProfileStateScope) {
        state.remove(scope.accountId, scope.profileId, KEY_FAVORITE_TEAMS, KEY_FAVORITE_TEAM)
    }

    fun hideScores(scope: AccountProfileStateScope): Boolean =
        state.read(scope, KEY_HIDE_SCORES) == "1"

    fun setHideScores(scope: AccountProfileStateScope, hide: Boolean) {
        state.write(scope, KEY_HIDE_SCORES, if (hide) "1" else "0")
    }

    fun matchAlerts(scope: AccountProfileStateScope): Map<String, FootballMatchAlertPreferences> =
        state.read(scope, KEY_MATCH_ALERTS)?.let(::decodeFootballMatchAlerts).orEmpty()

    fun saveMatchAlerts(
        scope: AccountProfileStateScope,
        alerts: Map<String, FootballMatchAlertPreferences>,
    ) {
        state.write(scope, KEY_MATCH_ALERTS, encodeFootballMatchAlerts(alerts))
    }

    fun inboxState(): FootballInboxState =
        state.read(KEY_INBOX)?.let(::decodeFootballInboxState) ?: FootballInboxState()

    fun inboxState(scope: AccountProfileStateScope): FootballInboxState =
        state.read(scope, KEY_INBOX)?.let(::decodeFootballInboxState) ?: FootballInboxState()

    fun knownTeams(scope: AccountProfileStateScope): List<FootballTeam> =
        state.read(scope, KEY_KNOWN_TEAMS)?.let(::decodeFootballTeams).orEmpty()

    fun saveKnownTeams(scope: AccountProfileStateScope, teams: List<FootballTeam>) {
        state.write(scope, KEY_KNOWN_TEAMS, encodeFootballTeams(teams))
    }

    fun saveInboxState(inbox: FootballInboxState) {
        state.write(KEY_INBOX, encodeFootballInboxState(inbox))
    }

    fun saveInboxState(scope: AccountProfileStateScope, inbox: FootballInboxState) {
        state.write(scope, KEY_INBOX, encodeFootballInboxState(inbox))
    }

    fun removeProfile(accountId: String, profileId: String) {
        state.remove(
            accountId,
            profileId,
            KEY_FAVORITE_TEAM,
            KEY_FAVORITE_TEAMS,
            KEY_HIDE_SCORES,
            KEY_MATCH_ALERTS,
            KEY_INBOX,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_football_profile_state_v1"
        const val KEY_FAVORITE_TEAM = "favorite_team"
        const val KEY_FAVORITE_TEAMS = "favorite_teams"
        const val KEY_HIDE_SCORES = "hide_scores"
        const val KEY_MATCH_ALERTS = "match_alerts"
        const val KEY_INBOX = "inbox"
        const val KEY_KNOWN_TEAMS = "known_teams"
    }
}

internal fun encodeFootballTeams(teams: List<FootballTeam>): String = JSONArray().apply {
    teams.distinctBy(FootballTeam::id).forEach { team ->
        put(JSONObject().put("id", team.id).put("name", team.name))
    }
}.toString()

internal fun decodeFootballTeams(raw: String?): List<FootballTeam> = runCatching {
    val array = JSONArray(raw ?: return@runCatching emptyList<FootballTeam>())
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) add(FootballTeam(id, name))
        }
    }
}.getOrElse { emptyList() }

internal fun encodeFootballFavoriteTeam(team: FootballFavoriteTeam): String = JSONObject()
    .put("teamId", team.teamId)
    .put("teamName", team.teamName)
    .put("selectedAtEpochMs", team.selectedAtEpochMs)
    .toString()

internal fun decodeFootballFavoriteTeam(raw: String?): FootballFavoriteTeam? = runCatching {
    val root = JSONObject(raw ?: return@runCatching null)
    val teamId = root.optString("teamId").trim()
    val teamName = root.optString("teamName").trim()
    require(teamId.isNotEmpty() && teamName.isNotEmpty())
    FootballFavoriteTeam(
        teamId = teamId,
        teamName = teamName,
        selectedAtEpochMs = root.optLong("selectedAtEpochMs", 0L).coerceAtLeast(0L),
    )
}.getOrNull()

/**
 * Plural favorites win. When absent, the legacy single-favorite value is adopted exactly once with
 * its original selection timestamp so a later migration cannot reset kickoff eligibility.
 */
internal fun migratedFootballFavoriteTeams(
    pluralRaw: String?,
    legacyRaw: String?,
): List<FootballFavoriteTeam> {
    if (pluralRaw != null) return decodeFootballFavoriteTeams(pluralRaw)
    val legacy = legacyRaw?.let(::decodeFootballFavoriteTeam) ?: return emptyList()
    return listOf(legacy)
}

internal fun encodeFootballFavoriteTeams(teams: List<FootballFavoriteTeam>): String =
    JSONArray().apply {
        teams.distinctBy(FootballFavoriteTeam::teamId).forEach { team ->
            put(JSONObject()
                .put("teamId", team.teamId)
                .put("teamName", team.teamName)
                .put("selectedAtEpochMs", team.selectedAtEpochMs))
        }
    }.toString()

internal fun decodeFootballFavoriteTeams(raw: String?): List<FootballFavoriteTeam> = runCatching {
    val array = JSONArray(raw ?: return@runCatching emptyList<FootballFavoriteTeam>())
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val teamId = item.optString("teamId").trim()
            val teamName = item.optString("teamName").trim()
            if (teamId.isEmpty() || teamName.isEmpty()) continue
            add(
                FootballFavoriteTeam(
                    teamId = teamId,
                    teamName = teamName,
                    selectedAtEpochMs = item.optLong("selectedAtEpochMs", 0L).coerceAtLeast(0L),
                ),
            )
        }
    }.distinctBy(FootballFavoriteTeam::teamId)
}.getOrElse { emptyList() }

internal fun encodeFootballMatchAlerts(
    alerts: Map<String, FootballMatchAlertPreferences>,
): String = JSONObject().apply {
    alerts.forEach { (eventId, preferences) ->
        put(eventId, JSONObject()
            .put("kickoff", preferences.kickoff)
            .put("reminder15", preferences.reminder15)
            .put("manuallyTracked", preferences.manuallyTracked)
            .put("trackedAtEpochMs", preferences.trackedAtEpochMs))
    }
}.toString()

internal fun decodeFootballMatchAlerts(raw: String?): Map<String, FootballMatchAlertPreferences> =
    runCatching {
        val root = JSONObject(raw ?: return@runCatching emptyMap<String, FootballMatchAlertPreferences>())
        buildMap {
            root.keys().forEach { eventId ->
                val item = root.optJSONObject(eventId) ?: return@forEach
                put(eventId, FootballMatchAlertPreferences(
                    kickoff = item.optBoolean("kickoff", false),
                    reminder15 = item.optBoolean("reminder15", false),
                    manuallyTracked = item.optBoolean("manuallyTracked", false),
                    trackedAtEpochMs = item.optLong("trackedAtEpochMs", 0L).coerceAtLeast(0L),
                ))
            }
        }
    }.getOrElse { emptyMap() }

internal fun encodeFootballInboxState(inbox: FootballInboxState): String = JSONObject()
    .put("notifications", JSONArray().apply {
        inbox.notifications.forEach { notification ->
            put(JSONObject()
                .put("id", notification.id)
                .put("accountId", notification.accountId)
                .put("profileId", notification.profileId)
                .put("eventId", notification.eventId)
                .put("eventIds", JSONArray(notification.eventIds.toList()))
                .put("competitionName", notification.competitionName)
                .put("homeTeamName", notification.homeTeamName)
                .put("awayTeamName", notification.awayTeamName)
                .put("kickoffEpochMs", notification.kickoffEpochMs)
                .put("startedAtEpochMs", notification.startedAtEpochMs)
                .put("detectedAtEpochMs", notification.detectedAtEpochMs)
                .put("alertType", notification.alertType.wireName)
                .put("read", notification.read)
                .put("homeScore", notification.homeScore ?: JSONObject.NULL)
                .put("awayScore", notification.awayScore ?: JSONObject.NULL)
                .put("status", notification.status.wireName))
        }
    })
    .put("suppressedEventIds", JSONArray(inbox.suppressedEventIds.toList()))
    .put("suppressedAlerts", JSONArray(inbox.suppressedAlerts.toList()))
    .put("clearedThroughEpochMs", inbox.clearedThroughEpochMs)
    .toString()

internal fun decodeFootballInboxState(raw: String?): FootballInboxState = runCatching {
    val root = JSONObject(raw ?: return@runCatching null)
    val notifications = buildList {
        val array = root.optJSONArray("notifications") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val eventId = item.optString("eventId").trim()
            val accountId = item.optString("accountId").trim()
            val profileId = item.optString("profileId").trim()
            if (id.isEmpty() || eventId.isEmpty() || accountId.isEmpty() || profileId.isEmpty()) continue
            add(
                FootballKickoffNotification(
                    id = id,
                    accountId = accountId,
                    profileId = profileId,
                    eventId = eventId,
                    eventIds = item.optJSONArray("eventIds").strings().toSet() + eventId,
                    competitionName = item.optString("competitionName").trim().ifBlank { "مسابقة" },
                    homeTeamName = item.optString("homeTeamName").trim(),
                    awayTeamName = item.optString("awayTeamName").trim(),
                    kickoffEpochMs = item.optLong("kickoffEpochMs", 0L),
                    startedAtEpochMs = item.optLong("startedAtEpochMs", 0L)
                        .takeIf { it > 0L }
                        ?: item.optLong("kickoffEpochMs", 0L),
                    detectedAtEpochMs = item.optLong("detectedAtEpochMs", 0L),
                    alertType = when (item.optString("alertType")) {
                        FootballAlertType.REMINDER_15.wireName -> FootballAlertType.REMINDER_15
                        else -> FootballAlertType.KICKOFF
                    },
                    read = item.optBoolean("read", false),
                    homeScore = item.nullableInt("homeScore"),
                    awayScore = item.nullableInt("awayScore"),
                    status = FootballMatchStatus.fromWire(item.optString("status")),
                ),
            )
        }
    }
    val suppressed = root.optJSONArray("suppressedEventIds").strings().toSet()
    val suppressedAlerts = root.optJSONArray("suppressedAlerts").strings().toSet()
    FootballInboxState(
        notifications = notifications
            .filter { it.kickoffEpochMs > 0L }
            .distinctBy(FootballKickoffNotification::id)
            .sortedByDescending(FootballKickoffNotification::detectedAtEpochMs)
            .take(FOOTBALL_MAX_KICKOFF_NOTIFICATIONS_PER_PROFILE),
        suppressedEventIds = suppressed,
        suppressedAlerts = suppressedAlerts,
        clearedThroughEpochMs = root.optLong("clearedThroughEpochMs", 0L).coerceAtLeast(0L),
    )
}.getOrNull() ?: FootballInboxState()

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
        }
    }
}

private fun JSONObject.nullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

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

/**
 * One favorite team and one kickoff inbox per resolved account/profile. Reads fail closed until
 * both owners exist; writes use the exact scope captured by the caller so a stale response cannot
 * land in another profile.
 */
internal class FootballProfileStateStore(context: Context) {
    private val state = AccountProfileStateStore(
        context = context.applicationContext,
        basePreferencesName = PREFERENCES_NAME,
        legacyPolicy = LegacyProfileStatePolicy.CLEAR,
    )

    fun activeScope(): AccountProfileStateScope? = state.activeScope()

    fun favoriteTeam(): FootballFavoriteTeam? =
        state.read(KEY_FAVORITE_TEAM)?.let(::decodeFootballFavoriteTeam)

    fun favoriteTeam(scope: AccountProfileStateScope): FootballFavoriteTeam? =
        state.read(scope, KEY_FAVORITE_TEAM)?.let(::decodeFootballFavoriteTeam)

    fun saveFavoriteTeam(team: FootballFavoriteTeam) {
        state.write(KEY_FAVORITE_TEAM, encodeFootballFavoriteTeam(team))
    }

    fun saveFavoriteTeam(scope: AccountProfileStateScope, team: FootballFavoriteTeam) {
        state.write(scope, KEY_FAVORITE_TEAM, encodeFootballFavoriteTeam(team))
    }

    fun clearFavoriteTeam() {
        state.remove(KEY_FAVORITE_TEAM)
    }

    fun clearFavoriteTeam(scope: AccountProfileStateScope) {
        state.remove(scope.accountId, scope.profileId, KEY_FAVORITE_TEAM)
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
        state.remove(accountId, profileId, KEY_FAVORITE_TEAM, KEY_INBOX)
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_football_profile_state_v1"
        const val KEY_FAVORITE_TEAM = "favorite_team"
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
                .put("detectedAtEpochMs", notification.detectedAtEpochMs)
                .put("read", notification.read)
                .put("homeScore", notification.homeScore ?: JSONObject.NULL)
                .put("awayScore", notification.awayScore ?: JSONObject.NULL)
                .put("status", notification.status.wireName))
        }
    })
    .put("suppressedEventIds", JSONArray(inbox.suppressedEventIds.toList()))
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
                    detectedAtEpochMs = item.optLong("detectedAtEpochMs", 0L),
                    read = item.optBoolean("read", false),
                    homeScore = item.nullableInt("homeScore"),
                    awayScore = item.nullableInt("awayScore"),
                    status = FootballMatchStatus.fromWire(item.optString("status")),
                ),
            )
        }
    }
    val suppressed = root.optJSONArray("suppressedEventIds").strings().toSet()
    FootballInboxState(
        notifications = notifications
            .filter { it.kickoffEpochMs > 0L }
            .distinctBy(FootballKickoffNotification::id)
            .sortedByDescending(FootballKickoffNotification::detectedAtEpochMs)
            .take(FOOTBALL_MAX_KICKOFF_NOTIFICATIONS_PER_PROFILE),
        suppressedEventIds = suppressed,
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

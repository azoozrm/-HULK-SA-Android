package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.security.persistableExternalUrlOrNull

internal data class UserLibrarySnapshot(
    val favorites: Set<String>,
    val history: List<HistoryEntry>,
)

internal suspend fun <T> runUserLibraryStartupOffMain(operation: () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal suspend fun <T> runUserLibraryProgressPersistenceOffMain(operation: suspend () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal fun userLibraryProgressWriteAllowed(
    attemptCurrent: Boolean,
    sameAuthenticatedSession: Boolean,
    expectedAccountId: String,
    activeAccountId: String?,
    expectedProfileId: String,
    activeProfileId: String,
): Boolean =
    attemptCurrent &&
        sameAuthenticatedSession &&
        expectedAccountId == activeAccountId &&
        expectedProfileId == activeProfileId

internal fun resumePositionForHistory(entries: List<HistoryEntry>, key: String): Long {
    val entry = entries.firstOrNull { it.key == key } ?: return 0L
    return entry.positionMs.takeIf {
        entry.durationMs <= 0L || entry.positionMs.toDouble() / entry.durationMs < COMPLETED_RATIO
    }?.coerceAtLeast(0L) ?: 0L
}

class UserLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val preferences: SharedPreferences
        get() = accountScope.preferences(PREFERENCES_NAME)
    private val profileStore = ProfileStore(appContext)
    private val kidsContentFilterStore = KidsContentFilterStore(appContext)

    fun favorites(): Set<String> = favorites(
        preferences = preferences,
        profile = profileStore.activeProfile(),
    )

    private fun favorites(
        preferences: SharedPreferences,
        profile: UserProfile,
    ): Set<String> {
        val stored = preferences
            .getStringSet(profileKey(profile.id, KEY_FAVORITES), emptySet())
            .orEmpty()
            .toSet()
        if (profile.kind != ProfileKind.KIDS) return stored
        val allowed = kidsContentFilterStore.allowedKeys()
        return stored.filterTo(linkedSetOf()) { it in allowed }
    }

    fun toggle(item: ContentItem): Set<String> {
        if (isActiveKidsProfile() && !kidsContentFilterStore.isAllowed(item)) return favorites()
        val key = keyFor(item)
        val updated = favorites().toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        preferences.edit().putStringSet(activeKey(KEY_FAVORITES), updated).apply()
        return updated
    }

    fun replaceFavorites(favorites: Set<String>) {
        val safeFavorites = if (isActiveKidsProfile()) {
            val allowed = kidsContentFilterStore.allowedKeys()
            favorites.filterTo(linkedSetOf()) { it in allowed }
        } else {
            favorites.toSet()
        }
        preferences.edit().putStringSet(activeKey(KEY_FAVORITES), safeFavorites).apply()
    }

    fun isFavorite(item: ContentItem, favorites: Set<String>): Boolean = keyFor(item) in favorites

    fun keyFor(item: ContentItem): String = "${item.type.name}:${item.id}"

    fun history(): List<HistoryEntry> = history(
        preferences = preferences,
        profile = profileStore.activeProfile(),
    )

    private fun history(
        preferences: SharedPreferences,
        profile: UserProfile,
        canRepair: () -> Boolean = { true },
    ): List<HistoryEntry> = runCatching {
        val historyKey = profileKey(profile.id, KEY_HISTORY)
        val raw = preferences.getString(historyKey, null) ?: return emptyList()
        val safeRaw = sanitizeHistoryJson(raw)
        if (safeRaw != raw) {
            if (!canRepair()) return emptyList()
            preferences.edit().putString(historyKey, safeRaw).commit()
        }
        val array = JSONArray(safeRaw)
        val decoded = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    HistoryEntry(
                        key = item.getString("key"),
                        title = item.getString("title"),
                        posterUrl = persistableExternalUrlOrNull(
                            item.optString("poster").takeUnless(String::isBlank),
                        ),
                        streamKind = item.getString("kind"),
                        streamId = item.getInt("id"),
                        extension = item.optString("extension", "mp4"),
                        isLive = item.optBoolean("live", false),
                        positionMs = item.optLong("position", 0L),
                        durationMs = item.optLong("duration", 0L),
                        updatedAtEpochMs = item.optLong("updated", 0L),
                        seriesTitle = item.optString("seriesTitle").takeUnless { it.isBlank() },
                        season = item.optInt("season").takeIf { item.has("season") && !item.isNull("season") },
                        episodeNumber = item.optInt("episodeNumber").takeIf {
                            item.has("episodeNumber") && !item.isNull("episodeNumber")
                        },
                        episodeTitle = item.optString("episodeTitle").takeUnless { it.isBlank() },
                        parentContentId = item.optInt("parentContentId").takeIf {
                            item.has("parentContentId") && !item.isNull("parentContentId") && it > 0
                        },
                    ),
                )
            }
        }.sortedByDescending(HistoryEntry::updatedAtEpochMs)
        if (profile.kind != ProfileKind.KIDS) return decoded
        val allowed = kidsContentFilterStore.allowedKeys()
        decoded.filter { entry -> isAllowedKidsHistoryEntry(allowed, entry) }
    }.getOrDefault(emptyList())

    /**
     * Runs the account-scoped legacy migration and initial library decode away
     * from the ViewModel constructor. The fixed account/profile owner prevents
     * a late startup result from being reused after a scope switch.
     */
    internal suspend fun initializeForProfile(
        expectedAccountId: String,
        profile: UserProfile,
    ): UserLibrarySnapshot? = runUserLibraryStartupOffMain {
        if (!matchesOwner(expectedAccountId, profile.id)) return@runUserLibraryStartupOffMain null
        val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        migrateLegacyLibraryIfNeeded(scopedPreferences)
        if (!matchesOwner(expectedAccountId, profile.id)) return@runUserLibraryStartupOffMain null
        val snapshot = UserLibrarySnapshot(
            favorites = favorites(scopedPreferences, profile),
            history = history(scopedPreferences, profile),
        )
        snapshot.takeIf { matchesOwner(expectedAccountId, profile.id) }
    }

    fun recordStart(request: PlaybackRequest): List<HistoryEntry> {
        if (isActiveKidsProfile() && !kidsContentFilterStore.isAllowed(request)) return history()
        val previous = history().firstOrNull { it.key == request.historyKey }
        val entry = HistoryEntry(
            key = request.historyKey,
            title = request.title,
            posterUrl = persistableExternalUrlOrNull(request.posterUrl),
            streamKind = request.streamKind,
            streamId = request.streamId,
            extension = request.extension,
            isLive = request.isLive,
            positionMs = previous?.positionMs ?: request.resumePositionMs,
            durationMs = previous?.durationMs ?: 0L,
            updatedAtEpochMs = System.currentTimeMillis(),
            seriesTitle = request.seriesTitle ?: previous?.seriesTitle,
            season = request.season ?: previous?.season,
            episodeNumber = request.episodeNumber ?: previous?.episodeNumber,
            episodeTitle = request.episodeTitle ?: previous?.episodeTitle,
            parentContentId = request.parentContentId ?: previous?.parentContentId,
        )
        return saveHistory(listOf(entry) + history().filterNot { it.key == entry.key })
    }

    /**
     * Persists one Player progress update away from the caller thread. The caller supplies its
     * monotonic attempt and this method verifies the authenticated account/profile owner again
     * immediately before each repair or write, so a cancelled or replaced owner cannot mutate
     * its old library after the switch.
     */
    internal suspend fun updateProgressForOwner(
        expectedOwner: AuthenticatedSessionOwner,
        expectedProfileId: String,
        request: PlaybackRequest,
        positionMs: Long,
        durationMs: Long,
        isCurrentAttempt: () -> Boolean,
    ): List<HistoryEntry>? = runUserLibraryProgressPersistenceOffMain {
        currentCoroutineContext().ensureActive()
        fun canWrite(): Boolean = userLibraryProgressWriteAllowed(
            attemptCurrent = isCurrentAttempt(),
            sameAuthenticatedSession = AuthenticatedSessionRegistry.isCurrent(expectedOwner),
            expectedAccountId = expectedOwner.accountId,
            activeAccountId = accountScope.activeAccountId(),
            expectedProfileId = expectedProfileId,
            activeProfileId = profileStore.activeProfileId(),
        )

        if (!canWrite()) return@runUserLibraryProgressPersistenceOffMain null
        val profile = profileStore.activeProfile()
        if (profile.id != expectedProfileId || !canWrite()) return@runUserLibraryProgressPersistenceOffMain null
        val scopedPreferences = accountScope.preferences(PREFERENCES_NAME, expectedOwner.accountId)
        if (!canWrite()) return@runUserLibraryProgressPersistenceOffMain null

        val currentHistory = history(
            preferences = scopedPreferences,
            profile = profile,
            canRepair = ::canWrite,
        )
        currentCoroutineContext().ensureActive()
        if (!canWrite()) return@runUserLibraryProgressPersistenceOffMain null
        if (profile.kind == ProfileKind.KIDS && !kidsContentFilterStore.isAllowed(request)) {
            return@runUserLibraryProgressPersistenceOffMain currentHistory
        }

        val previous = currentHistory.firstOrNull { it.key == request.historyKey }
        val entry = HistoryEntry(
            key = request.historyKey,
            title = request.title,
            posterUrl = persistableExternalUrlOrNull(request.posterUrl),
            streamKind = request.streamKind,
            streamId = request.streamId,
            extension = request.extension,
            isLive = request.isLive,
            positionMs = if (request.isLive) 0L else positionMs.coerceAtLeast(0L),
            durationMs = if (request.isLive) 0L else durationMs.coerceAtLeast(0L),
            updatedAtEpochMs = System.currentTimeMillis(),
            seriesTitle = request.seriesTitle ?: previous?.seriesTitle,
            season = request.season ?: previous?.season,
            episodeNumber = request.episodeNumber ?: previous?.episodeNumber,
            episodeTitle = request.episodeTitle ?: previous?.episodeTitle,
            parentContentId = request.parentContentId ?: previous?.parentContentId,
        )
        val normalized = normalizeHistory(
            entries = listOf(entry) + currentHistory.filterNot { it.key == entry.key },
            profile = profile,
        )
        currentCoroutineContext().ensureActive()
        if (!canWrite()) return@runUserLibraryProgressPersistenceOffMain null
        scopedPreferences.edit()
            .putString(profileKey(profile.id, KEY_HISTORY), encodeHistory(normalized))
            .apply()
        normalized
    }

    fun removeHistory(key: String): List<HistoryEntry> {
        val current = history()
        if (current.none { it.key == key }) return current
        return saveHistory(current.filterNot { it.key == key })
    }

    fun resumePosition(key: String): Long {
        return resumePositionForHistory(history(), key)
    }

    fun clearHistory(): List<HistoryEntry> {
        preferences.edit().remove(activeKey(KEY_HISTORY)).apply()
        return emptyList()
    }

    private fun saveHistory(entries: List<HistoryEntry>): List<HistoryEntry> {
        val profile = profileStore.activeProfile()
        val normalized = normalizeHistory(entries, profile)
        preferences.edit().putString(profileKey(profile.id, KEY_HISTORY), encodeHistory(normalized)).apply()
        return normalized
    }

    private fun normalizeHistory(
        entries: List<HistoryEntry>,
        profile: UserProfile,
    ): List<HistoryEntry> {
        val scopedEntries = if (profile.kind == ProfileKind.KIDS) {
            val allowed = kidsContentFilterStore.allowedKeys()
            entries.filter { entry -> isAllowedKidsHistoryEntry(allowed, entry) }
        } else {
            entries
        }
        val normalized = scopedEntries
            .map { entry -> entry.copy(posterUrl = persistableExternalUrlOrNull(entry.posterUrl)) }
            .distinctBy(HistoryEntry::key)
            .sortedByDescending(HistoryEntry::updatedAtEpochMs)
            .take(MAX_HISTORY)
        return normalized
    }

    private fun encodeHistory(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("title", entry.title)
                    .put("poster", persistableExternalUrlOrNull(entry.posterUrl).orEmpty())
                    .put("kind", entry.streamKind)
                    .put("id", entry.streamId)
                    .put("extension", entry.extension)
                    .put("live", entry.isLive)
                    .put("position", entry.positionMs)
                    .put("duration", entry.durationMs)
                    .put("updated", entry.updatedAtEpochMs)
                    .apply {
                        entry.seriesTitle?.let { put("seriesTitle", it) }
                        entry.season?.let { put("season", it) }
                        entry.episodeNumber?.let { put("episodeNumber", it) }
                        entry.episodeTitle?.let { put("episodeTitle", it) }
                        entry.parentContentId?.let { put("parentContentId", it) }
                    },
            )
        }
        return array.toString()
    }

    private fun sanitizeHistoryJson(raw: String): String = runCatching {
        val array = JSONArray(raw)
        var changed = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val poster = item.optString("poster").takeIf(String::isNotBlank) ?: continue
            if (persistableExternalUrlOrNull(poster) == null) {
                item.put("poster", "")
                changed = true
            }
        }
        if (changed) array.toString() else raw
    }.getOrElse {
        if (containsCredentialBearingIptvMaterial(raw)) "[]" else raw
    }

    private fun isActiveKidsProfile(): Boolean =
        profileStore.activeProfile().kind == ProfileKind.KIDS

    private fun activeKey(baseKey: String): String =
        profileKey(profileStore.activeProfileId(), baseKey)

    private fun profileKey(profileId: String, baseKey: String): String =
        "profile:$profileId:$baseKey"

    private fun matchesOwner(expectedAccountId: String, expectedProfileId: String): Boolean =
        accountScope.activeAccountId() == expectedAccountId &&
            profileStore.activeProfileId() == expectedProfileId

    private fun migrateLegacyLibraryIfNeeded(preferences: SharedPreferences) {
        val primaryProfileId = ProfileStore.PRIMARY_PROFILE_ID
        val scopedFavoritesKey = profileKey(primaryProfileId, KEY_FAVORITES)
        val scopedHistoryKey = profileKey(primaryProfileId, KEY_HISTORY)
        val plan = profileLibraryMigrationPlan(
            migrationAlreadyComplete = preferences.getBoolean(KEY_PROFILE_SCOPE_MIGRATION_V1, false),
            scopedFavoritesExist = preferences.contains(scopedFavoritesKey),
            legacyFavoritesExist = preferences.contains(KEY_FAVORITES),
            scopedHistoryExists = preferences.contains(scopedHistoryKey),
            legacyHistoryExists = preferences.contains(KEY_HISTORY),
        )
        if (!plan.markMigrationComplete) return

        val editor = preferences.edit()
        if (plan.copyLegacyFavorites) {
            editor.putStringSet(
                scopedFavoritesKey,
                preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet(),
            )
        }
        if (preferences.contains(KEY_HISTORY)) {
            preferences.getString(KEY_HISTORY, null)?.let { rawHistory ->
                val safeHistory = sanitizeHistoryJson(rawHistory)
                if (plan.copyLegacyHistory) editor.putString(scopedHistoryKey, safeHistory)
                if (safeHistory != rawHistory) editor.putString(KEY_HISTORY, safeHistory)
            }
        }

        // Keep non-sensitive legacy keys intact as rollback safety.
        editor.putBoolean(KEY_PROFILE_SCOPE_MIGRATION_V1, true).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_user_library"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val KEY_PROFILE_SCOPE_MIGRATION_V1 = "profile_scope_migration_v1"
        const val MAX_HISTORY = 100
    }
}

private const val COMPLETED_RATIO = .92

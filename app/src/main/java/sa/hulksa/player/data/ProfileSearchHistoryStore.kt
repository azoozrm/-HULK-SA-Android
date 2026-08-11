package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ProfileSearchHistoryEntry(
    val query: String,
    val updatedAtEpochMs: Long,
)

class ProfileSearchHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val profileStore = ProfileStore(appContext)

    fun recent(limit: Int = MAX_ENTRIES): List<ProfileSearchHistoryEntry> =
        recentForProfile(profileStore.activeProfileId(), limit)

    fun recentQueries(limit: Int = MAX_ENTRIES): List<String> =
        recent(limit).map(ProfileSearchHistoryEntry::query)

    fun record(rawQuery: String): List<ProfileSearchHistoryEntry> {
        val query = normalizeQuery(rawQuery) ?: return recent()
        val activeProfileId = profileStore.activeProfileId()
        val updated = listOf(
            ProfileSearchHistoryEntry(
                query = query,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        ) + recentForProfile(activeProfileId, MAX_ENTRIES)
            .filterNot { it.query.equals(query, ignoreCase = true) }

        return saveForProfile(activeProfileId, updated)
    }

    fun remove(rawQuery: String): List<ProfileSearchHistoryEntry> {
        val query = normalizeQuery(rawQuery) ?: return recent()
        val activeProfileId = profileStore.activeProfileId()
        return saveForProfile(
            profileId = activeProfileId,
            entries = recentForProfile(activeProfileId, MAX_ENTRIES)
                .filterNot { it.query.equals(query, ignoreCase = true) },
        )
    }

    fun clear(): List<ProfileSearchHistoryEntry> {
        preferences.edit()
            .remove(profileKey(profileStore.activeProfileId()))
            .apply()
        return emptyList()
    }

    fun removeProfileHistory(profileId: String) {
        if (profileId.isBlank()) return
        preferences.edit().remove(profileKey(profileId)).apply()
    }

    private fun recentForProfile(
        profileId: String,
        limit: Int,
    ): List<ProfileSearchHistoryEntry> {
        val safeLimit = limit.coerceIn(0, MAX_ENTRIES)
        if (safeLimit == 0) return emptyList()

        return runCatching {
            val raw = preferences.getString(profileKey(profileId), null)
                ?: return emptyList()
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val query = normalizeQuery(item.optString(KEY_QUERY)) ?: continue
                    add(
                        ProfileSearchHistoryEntry(
                            query = query,
                            updatedAtEpochMs = item.optLong(KEY_UPDATED_AT, 0L),
                        ),
                    )
                }
            }
                .distinctBy { it.query.lowercase() }
                .sortedByDescending(ProfileSearchHistoryEntry::updatedAtEpochMs)
                .take(safeLimit)
        }.getOrDefault(emptyList())
    }

    private fun saveForProfile(
        profileId: String,
        entries: List<ProfileSearchHistoryEntry>,
    ): List<ProfileSearchHistoryEntry> {
        val normalized = entries
            .mapNotNull { entry ->
                normalizeQuery(entry.query)?.let { query ->
                    ProfileSearchHistoryEntry(
                        query = query,
                        updatedAtEpochMs = entry.updatedAtEpochMs,
                    )
                }
            }
            .distinctBy { it.query.lowercase() }
            .sortedByDescending(ProfileSearchHistoryEntry::updatedAtEpochMs)
            .take(MAX_ENTRIES)

        if (normalized.isEmpty()) {
            preferences.edit().remove(profileKey(profileId)).apply()
            return emptyList()
        }

        val array = JSONArray()
        normalized.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_QUERY, entry.query)
                    .put(KEY_UPDATED_AT, entry.updatedAtEpochMs),
            )
        }
        preferences.edit().putString(profileKey(profileId), array.toString()).apply()
        return normalized
    }

    private fun normalizeQuery(raw: String): String? {
        val normalized = raw
            .trim()
            .replace(WHITESPACE_REGEX, " ")
            .take(MAX_QUERY_LENGTH)
            .trim()
        return normalized.takeIf(String::isNotBlank)
    }

    private fun profileKey(profileId: String): String =
        "profile:$profileId:$KEY_ENTRIES"

    private companion object {
        const val PREFERENCES_NAME = "hulk_profile_search_history_v1"
        const val KEY_ENTRIES = "entries"
        const val KEY_QUERY = "query"
        const val KEY_UPDATED_AT = "updated_at"
        const val MAX_ENTRIES = 12
        const val MAX_QUERY_LENGTH = 120
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}

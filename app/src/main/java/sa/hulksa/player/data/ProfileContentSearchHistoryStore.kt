package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.security.persistableExternalUrlOrNull

data class ProfileContentSearchHistoryEntry(
    val contentId: Int,
    val contentType: ContentType,
    val title: String,
    val posterUrl: String?,
    val year: String?,
    val updatedAtEpochMs: Long,
) {
    val stableKey: String
        get() = "${contentType.name}:$contentId"
}

/**
 * Profile-scoped search history that stores only real catalog selections.
 *
 * Raw keystrokes and arbitrary text are intentionally never persisted. Re-selecting
 * the same movie/series/channel only moves the existing entry to the top.
 */
class ProfileContentSearchHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val stateStore = AccountProfileStateStore(
        context = appContext,
        basePreferencesName = PREFERENCES_NAME,
        legacyPolicy = LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT,
    )

    init {
        AccountProfileStateStore.clearLegacyOnce(
            context = appContext,
            basePreferencesName = LEGACY_RAW_QUERY_PREFERENCES_NAME,
        )
    }

    fun recent(limit: Int = MAX_ENTRIES): List<ProfileContentSearchHistoryEntry> {
        val scope = stateStore.activeScope() ?: return emptyList()
        return recentForScope(scope, limit)
    }

    fun record(item: ContentItem): List<ProfileContentSearchHistoryEntry> {
        val title = normalizeTitle(item.name) ?: return recent()
        val scope = stateStore.activeScope() ?: return emptyList()
        val entry = ProfileContentSearchHistoryEntry(
            contentId = item.id,
            contentType = item.type,
            title = title,
            posterUrl = persistableExternalUrlOrNull(item.posterUrl),
            year = item.year?.trim()?.takeIf(String::isNotBlank),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val updated = listOf(entry) + recentForScope(scope, MAX_ENTRIES)
            .filterNot { it.stableKey == entry.stableKey }
        return saveForScope(scope, updated)
    }

    fun remove(contentType: ContentType, contentId: Int): List<ProfileContentSearchHistoryEntry> {
        val scope = stateStore.activeScope() ?: return emptyList()
        val key = "${contentType.name}:$contentId"
        return saveForScope(
            scope = scope,
            entries = recentForScope(scope, MAX_ENTRIES)
                .filterNot { it.stableKey == key },
        )
    }

    fun clear(): List<ProfileContentSearchHistoryEntry> {
        stateStore.remove(KEY_ENTRIES)
        return emptyList()
    }

    fun removeProfileHistory(accountId: String, profileId: String) =
        stateStore.remove(accountId, profileId, KEY_ENTRIES)

    private fun recentForScope(
        scope: AccountProfileStateScope,
        limit: Int,
    ): List<ProfileContentSearchHistoryEntry> {
        val safeLimit = limit.coerceIn(0, MAX_ENTRIES)
        if (safeLimit == 0) return emptyList()

        return runCatching {
            val raw = stateStore.read(scope, KEY_ENTRIES) ?: return emptyList()
            val safeRaw = sanitizeEntriesJson(raw)
            if (safeRaw != raw) stateStore.write(scope, KEY_ENTRIES, safeRaw)
            val array = JSONArray(safeRaw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val contentId = item.optInt(KEY_CONTENT_ID, -1)
                    if (contentId < 0) continue
                    val contentType = runCatching {
                        ContentType.valueOf(item.optString(KEY_CONTENT_TYPE))
                    }.getOrNull() ?: continue
                    val title = normalizeTitle(item.optString(KEY_TITLE)) ?: continue
                    add(
                        ProfileContentSearchHistoryEntry(
                            contentId = contentId,
                            contentType = contentType,
                            title = title,
                            posterUrl = persistableExternalUrlOrNull(
                                item.optString(KEY_POSTER_URL)
                                    .trim()
                                    .takeIf(String::isNotBlank),
                            ),
                            year = item.optString(KEY_YEAR)
                                .trim()
                                .takeIf(String::isNotBlank),
                            updatedAtEpochMs = item.optLong(KEY_UPDATED_AT, 0L),
                        ),
                    )
                }
            }
                .distinctBy(ProfileContentSearchHistoryEntry::stableKey)
                .sortedByDescending(ProfileContentSearchHistoryEntry::updatedAtEpochMs)
                .take(safeLimit)
        }.getOrDefault(emptyList())
    }

    private fun saveForScope(
        scope: AccountProfileStateScope,
        entries: List<ProfileContentSearchHistoryEntry>,
    ): List<ProfileContentSearchHistoryEntry> {
        val normalized = entries
            .mapNotNull { entry ->
                val title = normalizeTitle(entry.title) ?: return@mapNotNull null
                entry.copy(
                    title = title,
                    posterUrl = persistableExternalUrlOrNull(entry.posterUrl),
                )
            }
            .distinctBy(ProfileContentSearchHistoryEntry::stableKey)
            .sortedByDescending(ProfileContentSearchHistoryEntry::updatedAtEpochMs)
            .take(MAX_ENTRIES)

        if (normalized.isEmpty()) {
            stateStore.remove(scope.accountId, scope.profileId, KEY_ENTRIES)
            return emptyList()
        }

        stateStore.write(scope, KEY_ENTRIES, encodeEntries(normalized))
        return normalized
    }

    private fun encodeEntries(entries: List<ProfileContentSearchHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(KEY_CONTENT_ID, entry.contentId)
                    .put(KEY_CONTENT_TYPE, entry.contentType.name)
                    .put(KEY_TITLE, entry.title)
                    .put(KEY_POSTER_URL, persistableExternalUrlOrNull(entry.posterUrl).orEmpty())
                    .put(KEY_YEAR, entry.year.orEmpty())
                    .put(KEY_UPDATED_AT, entry.updatedAtEpochMs),
            )
        }
        return array.toString()
    }

    private fun sanitizeEntriesJson(raw: String): String = runCatching {
        val array = JSONArray(raw)
        var changed = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val poster = item.optString(KEY_POSTER_URL).takeIf(String::isNotBlank) ?: continue
            if (persistableExternalUrlOrNull(poster) == null) {
                item.put(KEY_POSTER_URL, "")
                changed = true
            }
        }
        if (changed) array.toString() else raw
    }.getOrElse {
        if (containsCredentialBearingIptvMaterial(raw)) "[]" else raw
    }

    private fun normalizeTitle(raw: String): String? {
        val normalized = raw
            .trim()
            .replace(WHITESPACE_REGEX, " ")
            .take(MAX_TITLE_LENGTH)
            .trim()
        return normalized.takeIf(String::isNotBlank)
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_profile_content_search_history_v2"
        const val LEGACY_RAW_QUERY_PREFERENCES_NAME = "hulk_profile_search_history_v1"
        const val KEY_ENTRIES = "entries"
        const val KEY_CONTENT_ID = "content_id"
        const val KEY_CONTENT_TYPE = "content_type"
        const val KEY_TITLE = "title"
        const val KEY_POSTER_URL = "poster_url"
        const val KEY_YEAR = "year"
        const val KEY_UPDATED_AT = "updated_at"
        const val MAX_ENTRIES = 12
        const val MAX_TITLE_LENGTH = 160
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}

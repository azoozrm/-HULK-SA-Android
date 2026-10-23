package sa.hulksa.player.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
import sa.hulksa.player.security.persistableExternalUrlOrNull

internal const val USER_LIBRARY_PREFERENCES_BASE = "hulk_user_library"

internal enum class HistoryDocumentState {
    MISSING,
    VALID,
    PARTIAL_DAMAGE,
    UNREADABLE,
    STORAGE_FAILURE,
}

internal data class HistoryDocumentRead(
    val entries: List<HistoryEntry>,
    val state: HistoryDocumentState,
    val safeRawDocument: String?,
    val requiresPrivacyRewrite: Boolean,
)

internal fun readHistoryDocument(
    preferences: SharedPreferences,
    historyKey: String,
): HistoryDocumentRead {
    val raw = try {
        preferences.getString(historyKey, null)
    } catch (_: Exception) {
        return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.STORAGE_FAILURE,
            safeRawDocument = null,
            requiresPrivacyRewrite = false,
        )
    }
    if (!runCatching { preferences.contains(historyKey) }.getOrDefault(raw != null) || raw == null) {
        val unsafeLegacy = hasUnsafeLegacyMigrationMarker(
            preferences,
            USER_LIBRARY_PREFERENCES_BASE,
        )
        return HistoryDocumentRead(
            entries = emptyList(),
            state = if (unsafeLegacy) {
                HistoryDocumentState.UNREADABLE
            } else {
                HistoryDocumentState.MISSING
            },
            safeRawDocument = null,
            requiresPrivacyRewrite = false,
        )
    }
    if (raw.isBlank()) {
        return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.UNREADABLE,
            safeRawDocument = null,
            requiresPrivacyRewrite = false,
        )
    }
    // Readability is decided from the original document, never from a sanitizer result.
    runCatching { JSONArray(raw) }.getOrNull()
        ?: return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.UNREADABLE,
            safeRawDocument = null,
            requiresPrivacyRewrite = false,
        )
    val safeRaw = sanitizeHistoryDocumentJson(raw)
    val array = runCatching { JSONArray(safeRaw) }.getOrNull()
        ?: return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.UNREADABLE,
            safeRawDocument = null,
            requiresPrivacyRewrite = false,
        )
    if (array.length() == 0) {
        return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.VALID,
            safeRawDocument = safeRaw,
            requiresPrivacyRewrite = safeRaw != raw,
        )
    }
    val entries = mutableListOf<HistoryEntry>()
    var damaged = false
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index)
        if (item == null) {
            damaged = true
            continue
        }
        val decoded = runCatching { decodeHistoryEntry(item) }.getOrNull()
        if (decoded == null) {
            damaged = true
        } else {
            entries += decoded
        }
    }
    if (entries.isEmpty()) {
        return HistoryDocumentRead(
            entries = emptyList(),
            state = HistoryDocumentState.UNREADABLE,
            safeRawDocument = safeRaw,
            requiresPrivacyRewrite = safeRaw != raw,
        )
    }
    return HistoryDocumentRead(
        entries = entries.sortedByDescending(HistoryEntry::updatedAtEpochMs),
        state = if (damaged) HistoryDocumentState.PARTIAL_DAMAGE else HistoryDocumentState.VALID,
        safeRawDocument = safeRaw,
        requiresPrivacyRewrite = safeRaw != raw,
    )
}

private val DEFAULT_HISTORY_FINAL_WRITE_LOCK = Any()

internal fun writeHistoryDocument(
    preferences: SharedPreferences,
    historyKey: String,
    entries: List<HistoryEntry>,
    read: HistoryDocumentRead,
    canWrite: () -> Boolean = { true },
    finalWriteLock: Any = DEFAULT_HISTORY_FINAL_WRITE_LOCK,
): Boolean {
    if (
        read.state == HistoryDocumentState.UNREADABLE ||
        read.state == HistoryDocumentState.STORAGE_FAILURE
    ) {
        return false
    }
    if (read.state == HistoryDocumentState.PARTIAL_DAMAGE) {
        val raw = read.safeRawDocument ?: return false
        if (!preserveDamagedPersistedDocument(preferences, historyKey, raw)) return false
    }
    if (!canWrite()) return false
    return synchronized(finalWriteLock) {
        if (!canWrite()) return@synchronized false
        runCatching {
            preferences.edit()
                .putString(historyKey, encodeHistoryDocument(entries))
                .apply()
            true
        }.getOrDefault(false)
    }
}

internal fun encodeHistoryDocument(entries: List<HistoryEntry>): String {
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

internal fun isStructurallyValidHistoryDocument(raw: String): Boolean =
    runCatching { JSONArray(raw) }.isSuccess

internal fun sanitizeHistoryDocumentJson(raw: String): String = runCatching {
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
    // Invalid top-level history stays unreadable and is never rewritten as valid empty.
    raw
}

private fun decodeHistoryEntry(item: JSONObject): HistoryEntry = HistoryEntry(
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
)

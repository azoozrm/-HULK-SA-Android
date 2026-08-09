package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PlaybackRequest

class UserLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("hulk_user_library", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    fun toggle(item: ContentItem): Set<String> {
        val key = keyFor(item)
        val updated = favorites().toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return updated
    }

    fun replaceFavorites(favorites: Set<String>) {
        preferences.edit().putStringSet(KEY_FAVORITES, favorites.toSet()).apply()
    }

    fun isFavorite(item: ContentItem, favorites: Set<String>): Boolean = keyFor(item) in favorites

    fun keyFor(item: ContentItem): String = "${item.type.name}:${item.id}"

    fun history(): List<HistoryEntry> = runCatching {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    HistoryEntry(
                        key = item.getString("key"),
                        title = item.getString("title"),
                        posterUrl = item.optString("poster").takeUnless { it.isBlank() },
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
                    ),
                )
            }
        }.sortedByDescending(HistoryEntry::updatedAtEpochMs)
    }.getOrDefault(emptyList())

    fun recordStart(request: PlaybackRequest): List<HistoryEntry> {
        val previous = history().firstOrNull { it.key == request.historyKey }
        val entry = HistoryEntry(
            key = request.historyKey,
            title = request.title,
            posterUrl = request.posterUrl,
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
        )
        return saveHistory(listOf(entry) + history().filterNot { it.key == entry.key })
    }

    fun updateProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long): List<HistoryEntry> {
        val previous = history().firstOrNull { it.key == request.historyKey }
        val entry = HistoryEntry(
            key = request.historyKey,
            title = request.title,
            posterUrl = request.posterUrl,
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
        )
        return saveHistory(listOf(entry) + history().filterNot { it.key == entry.key })
    }

    fun removeHistory(key: String): List<HistoryEntry> {
        val current = history()
        if (current.none { it.key == key }) return current
        return saveHistory(current.filterNot { it.key == key })
    }

    fun resumePosition(key: String): Long {
        val entry = history().firstOrNull { it.key == key } ?: return 0L
        if (entry.durationMs > 0L && entry.positionMs.toDouble() / entry.durationMs >= COMPLETED_RATIO) return 0L
        return entry.positionMs
    }

    fun clearHistory(): List<HistoryEntry> {
        preferences.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }

    private fun saveHistory(entries: List<HistoryEntry>): List<HistoryEntry> {
        val normalized = entries
            .distinctBy(HistoryEntry::key)
            .sortedByDescending(HistoryEntry::updatedAtEpochMs)
            .take(MAX_HISTORY)
        val array = JSONArray()
        normalized.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("title", entry.title)
                    .put("poster", entry.posterUrl.orEmpty())
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
                    },
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
        return normalized
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 100
        const val COMPLETED_RATIO = .92
    }
}

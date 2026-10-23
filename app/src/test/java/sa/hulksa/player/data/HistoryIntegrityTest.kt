package sa.hulksa.player.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.HistoryEntry

class HistoryIntegrityTest {
    @Test
    fun `healthy entries survive an incomplete entry across a new playback write and restart`() {
        val preferences = FakeSharedPreferences()
        val damagedRaw = JSONArray()
            .put(historyJson(key = "movie:1", title = "Movie 1", id = 1, position = 5_000L))
            .put(JSONObject().put("key", "series:2"))
            .put(
                historyJson(
                    key = "series:3",
                    title = "Episode 3",
                    id = 3,
                    position = 12_000L,
                    seriesTitle = "Series",
                    season = 2,
                    episode = 4,
                    parentContentId = 30,
                ),
            )
            .toString()
        preferences.edit().putString(HISTORY_KEY, damagedRaw).apply()

        val read = readHistoryDocument(preferences, HISTORY_KEY)
        assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)
        assertEquals(listOf("series:3", "movie:1"), read.entries.map(HistoryEntry::key))

        val newEntry = HistoryEntry(
            key = "movie:4",
            title = "Movie 4",
            posterUrl = null,
            streamKind = "movie",
            streamId = 4,
            extension = "mp4",
            isLive = false,
            positionMs = 1_000L,
            durationMs = 0L,
            updatedAtEpochMs = 99_000L,
        )
        val merged = listOf(newEntry) + read.entries
        assertTrue(writeHistoryDocument(preferences, HISTORY_KEY, merged, read))
        assertTrue(
            preferences.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(HISTORY_KEY))
            },
        )

        val restarted = readHistoryDocument(preferences.restart(), HISTORY_KEY)
        assertEquals(HistoryDocumentState.VALID, restarted.state)
        assertEquals(listOf("movie:4", "series:3", "movie:1"), restarted.entries.map(HistoryEntry::key))
        val series = restarted.entries.first { it.key == "series:3" }
        assertEquals(12_000L, series.positionMs)
        assertEquals("Series", series.seriesTitle)
        assertEquals(2, series.season)
        assertEquals(4, series.episodeNumber)
        assertEquals(30, series.parentContentId)
    }

    @Test
    fun `progress update keeps healthy resume data when another entry is damaged`() {
        val preferences = FakeSharedPreferences()
        val raw = JSONArray()
            .put(historyJson(key = "movie:1", title = "Movie 1", id = 1, position = 3_000L))
            .put(JSONObject().put("key", "bad"))
            .put(historyJson(key = "series:9", title = "Episode 9", id = 9, position = 44_000L))
            .toString()
        preferences.edit().putString(HISTORY_KEY, raw).apply()
        val read = readHistoryDocument(preferences, HISTORY_KEY)
        assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)

        val updated = read.entries.first { it.key == "series:9" }.copy(
            positionMs = 55_000L,
            durationMs = 120_000L,
            updatedAtEpochMs = 123_000L,
        )
        val merged = listOf(updated) + read.entries.filterNot { it.key == updated.key }
        assertTrue(writeHistoryDocument(preferences, HISTORY_KEY, merged, read))

        val restarted = readHistoryDocument(preferences.restart(), HISTORY_KEY)
        assertEquals(55_000L, restarted.entries.first { it.key == "series:9" }.positionMs)
        assertEquals(120_000L, restarted.entries.first { it.key == "series:9" }.durationMs)
        assertEquals(3_000L, restarted.entries.first { it.key == "movie:1" }.positionMs)
    }

    @Test
    fun `unreadable history is never rewritten by later writes`() {
        val preferences = FakeSharedPreferences()
        val raw = "[{\"key\":"
        preferences.edit().putString(HISTORY_KEY, raw).apply()

        val read = readHistoryDocument(preferences, HISTORY_KEY)
        assertEquals(HistoryDocumentState.UNREADABLE, read.state)
        val entry = HistoryEntry(
            key = "movie:1",
            title = "Movie 1",
            posterUrl = null,
            streamKind = "movie",
            streamId = 1,
            extension = "mp4",
            isLive = false,
            positionMs = 0L,
            durationMs = 0L,
            updatedAtEpochMs = 1L,
        )
        assertFalse(writeHistoryDocument(preferences, HISTORY_KEY, listOf(entry), read))
        assertEquals(raw, preferences.durableValue(HISTORY_KEY))

        val restartedPreferences = preferences.restart()
        val restarted = readHistoryDocument(restartedPreferences, HISTORY_KEY)
        assertEquals(HistoryDocumentState.UNREADABLE, restarted.state)
        assertFalse(writeHistoryDocument(restartedPreferences, HISTORY_KEY, listOf(entry), restarted))
        assertEquals(raw, restartedPreferences.durableValue(HISTORY_KEY))
        assertTrue(
            restartedPreferences.durableKeys().none {
                it.startsWith(persistedRecoveryKeyPrefix(HISTORY_KEY))
            },
        )
    }

    @Test
    fun `missing and valid empty history documents remain writable`() {
        val missing = FakeSharedPreferences()
        assertEquals(HistoryDocumentState.MISSING, readHistoryDocument(missing, HISTORY_KEY).state)

        val empty = FakeSharedPreferences()
        empty.edit().putString(HISTORY_KEY, "[]").apply()
        val read = readHistoryDocument(empty, HISTORY_KEY)
        assertEquals(HistoryDocumentState.VALID, read.state)
        assertTrue(writeHistoryDocument(empty, HISTORY_KEY, emptyList(), read))
    }

    @Test
    fun `recovery ring eviction cannot falsely confirm an evicted payload`() {
        val preferences = FakeSharedPreferences()
        fun partialRaw(position: Long): String = JSONArray()
            .put(historyJson(key = "movie:1", title = "Movie 1", id = 1, position = position))
            .put(JSONObject().put("key", "bad"))
            .toString()

        fun repair(raw: String) {
            preferences.edit().putString(HISTORY_KEY, raw).apply()
            val read = readHistoryDocument(preferences, HISTORY_KEY)
            assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)
            assertTrue(writeHistoryDocument(preferences, HISTORY_KEY, read.entries, read))
        }

        val a = partialRaw(1_000L)
        val b = partialRaw(2_000L)
        val c = partialRaw(3_000L)
        repair(a)
        repair(b)
        repair(c)
        repair(a)

        val slots = preferences.durableSnapshot()
            .filterKeys {
                it.startsWith(persistedRecoveryKeyPrefix(HISTORY_KEY)) &&
                    !it.endsWith("__next_slot__")
            }
        assertEquals(2, slots.size)
        assertTrue("Expected the recurrent payload to occupy a current recovery slot", a in slots.values)
    }

    @Test
    fun `failed recovery preservation rejects the history repair`() {
        val preferences = FakeSharedPreferences()
        val raw = JSONArray()
            .put(historyJson(key = "movie:1", title = "Movie 1", id = 1, position = 3_000L))
            .put(JSONObject().put("key", "bad"))
            .toString()
        preferences.edit().putString(HISTORY_KEY, raw).apply()
        val read = readHistoryDocument(preferences, HISTORY_KEY)
        assertEquals(HistoryDocumentState.PARTIAL_DAMAGE, read.state)

        preferences.commitResult = false
        assertFalse(writeHistoryDocument(preferences, HISTORY_KEY, read.entries, read))
        assertEquals(raw, preferences.durableValue(HISTORY_KEY))

        preferences.commitResult = true
        assertTrue(writeHistoryDocument(preferences, HISTORY_KEY, read.entries, read))
        assertNotNull(preferences.durableValue(HISTORY_KEY))
        assertTrue(
            preferences.durableKeys().any {
                it.startsWith(persistedRecoveryKeyPrefix(HISTORY_KEY))
            },
        )
    }

    private fun historyJson(
        key: String,
        title: String,
        id: Int,
        position: Long,
        seriesTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        parentContentId: Int? = null,
    ): JSONObject = JSONObject()
        .put("key", key)
        .put("title", title)
        .put("poster", "")
        .put("kind", if (key.startsWith("movie")) "movie" else "series")
        .put("id", id)
        .put("extension", "mp4")
        .put("live", false)
        .put("position", position)
        .put("duration", 0L)
        .put("updated", position)
        .apply {
            seriesTitle?.let { put("seriesTitle", it) }
            season?.let { put("season", it) }
            episode?.let { put("episodeNumber", it) }
            parentContentId?.let { put("parentContentId", it) }
        }

    private companion object {
        const val HISTORY_KEY = "profile:kids-1:history"
    }
}

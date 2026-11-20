package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballR059StoreTest {

    private val day = "2026-11-20"

    private fun json(
        cachedAtSeconds: Long,
        stale: Boolean,
        matches: String = "[]",
    ) = """{"schemaVersion":1,"day":"$day","generatedAt":$cachedAtSeconds,"cachedAt":$cachedAtSeconds,"stale":$stale,"matches":$matches,"teams":[]}"""

    private fun oneMatch() =
        """[{"id":"evt:1","providerEventId":"1","competition":{"id":"17","name":"Saudi Pro League"},"homeTeam":{"id":"258","name":"Al-Ahli"},"awayTeam":{"id":"267","name":"Al-Nassr"},"kickoffAt":1795195800,"status":"upcoming"}]"""


    @Test
    fun `older fresh-labelled response cannot replace newer fresh empty`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_120L, stale = false), "src", 1_000L)
        now = 1_001L
        store.save(day, json(1_795_136_100L, stale = false, matches = oneMatch()), "src", 1_001L)

        val reopened = FootballFeedCacheStore(context) { now }.load(day, "src")
        assertFalse(reopened!!.stale)
        assertTrue(reopened.matches.isEmpty())
    }


    @Test
    fun `unchanged fresh revalidation renews receipt but keeps content`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_000L, stale = false), "src", 1_000L)
        now = 2_000L
        store.save(day, json(1_795_136_000L, stale = false), "src", 2_000L)

        val reopened = FootballFeedCacheStore(context) { now }.loadExact(day, "src")
        assertFalse(reopened!!.stale)
        assertTrue(reopened.matches.isEmpty())
        assertEquals(2_000L, reopened.fetchedAtEpochMs)
    }

    @Test
    fun `different or older payload never wins on a tied timestamp`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_000L, stale = false), "src", 1_000L)
        now = 2_000L
        store.save(day, json(1_795_136_000L, stale = false, matches = oneMatch()), "src", 2_000L)
        now = 3_000L
        store.save(day, json(1_795_135_900L, stale = false, matches = oneMatch()), "src", 3_000L)

        val reopened = FootballFeedCacheStore(context) { now }.loadExact(day, "src")
        assertFalse(reopened!!.stale)
        assertTrue(reopened.matches.isEmpty())
        assertEquals(1_000L, reopened.fetchedAtEpochMs)
    }

    @Test
    fun `older stale response cannot replace a fresh empty durable record`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_120L, stale = false), "owner|src", 1_000L)
        now = 1_001L
        store.save(day, json(1_795_136_100L, stale = true, matches = oneMatch()), "owner|src", 1_001L)

        val reopened = FootballFeedCacheStore(context) { now }.load(day, "owner|src")
        assertFalse(reopened!!.stale)
        assertTrue(reopened.matches.isEmpty())
        assertEquals(1_795_136_120_000L, reopened.cachedAtEpochMs)
    }

    @Test
    fun `newer stale last-good replaces an expired empty record`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_000L, stale = false), "owner|src", 1_000L)
        now = 1_000_000L
        store.save(day, json(1_795_136_120L, stale = true, matches = oneMatch()), "owner|src", now)

        val reopened = FootballFeedCacheStore(context) { now }.load(day, "owner|src")
        assertTrue(reopened!!.stale)
        assertEquals(1, reopened.matches.size)
    }

    @Test
    fun `two sources remain isolated when the older source finishes late`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_120L, stale = false), "src-b", 1_000L)
        now = 1_001L
        store.save(day, json(1_795_136_100L, stale = true, matches = oneMatch()), "src-a", 1_001L)

        val reopened = FootballFeedCacheStore(context) { now }
        val b = reopened.load(day, "src-b")
        val a = reopened.load(day, "src-a")
        assertFalse(b!!.stale)
        assertTrue(b.matches.isEmpty())
        assertTrue(a!!.stale)
        assertEquals(1, a.matches.size)
    }

    @Test
    fun `legacy record with a documented source migrates to its namespace`() {
        val now = 2_000L
        val context = TestAppContext()
        val prefs = context.getSharedPreferences("hulk_football_feed_cache_v1", 0) as FakeSharedPreferences
        prefs.edit()
            .putString("day:$day", json(1_795_136_120L, stale = false))
            .putString("source:$day", "acct\u0000prof\u0000src")
            .putLong("fetched:$day", 1_500L)
            .commit()

        val loaded = FootballFeedCacheStore(context) { now }.load(day, "src")
        assertFalse(loaded!!.stale)
        assertTrue(loaded.matches.isEmpty())
        assertTrue(prefs.durableKeys().any { it.startsWith("day:") && it.endsWith(":$day") })
    }

    @Test
    fun `source-less legacy record is preserved but not attached to a host`() {
        val context = TestAppContext()
        val raw = json(1_795_136_120L, stale = false)
        val prefs = context.getSharedPreferences("hulk_football_feed_cache_v1", 0) as FakeSharedPreferences
        prefs.edit().putString("day:$day", raw).commit()

        assertNull(FootballFeedCacheStore(context).load(day, "https://neo4ksa.com/feed"))
        assertEquals(raw, prefs.durableValue("day:$day"))
    }


    @Test
    fun `prior fallback skips an ambiguous newer candidate`() {
        val day18 = "2026-11-18"
        val day19 = "2026-11-19"
        val day20 = "2026-11-20"
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { 1_000L }
        store.save(
            day18,
            json(1_795_100_000L, stale = false, matches = oneMatch())
                .replace("\"day\":\"$day\"", "\"day\":\"$day18\""),
            "src",
            1_000L,
        )
        val prefs = context.getSharedPreferences("hulk_football_feed_cache_v1", 0) as FakeSharedPreferences
        prefs.edit()
            .putString(
                "day:$day19",
                json(1_795_110_000L, stale = false, matches = oneMatch())
                    .replace("\"day\":\"$day\"", "\"day\":\"$day19\""),
            )
            .putString("last_day", day19)
            .commit()

        val loaded = FootballFeedCacheStore(context) { 1_000L }.load(day20, "src")
        assertEquals(day18, loaded!!.dayKey)
        assertTrue(loaded.stale)
    }

    @Test
    fun `exact valid empty suppresses prior fallback`() {
        val day18 = "2026-11-18"
        val day20 = "2026-11-20"
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { 1_000L }
        store.save(day18, json(1_795_100_000L, stale = false, matches = oneMatch()), "src", 1_000L)
        store.save(day20, json(1_795_130_000L, stale = false), "src", 1_000L)

        val loaded = FootballFeedCacheStore(context) { 1_000L }.load(day20, "src")
        assertFalse(loaded!!.stale)
        assertTrue(loaded.matches.isEmpty())
        assertEquals(day20, loaded.dayKey)
    }

    @Test
    fun `source change owns a new durable record`() {
        var now = 1_000L
        val context = TestAppContext()
        val store = FootballFeedCacheStore(context) { now }
        store.save(day, json(1_795_136_120L, stale = false), "owner|src-a", 1_000L)
        assertNull(FootballFeedCacheStore(context) { now }.load(day, "owner|src-b"))

        store.save(day, json(1_795_136_100L, stale = true, matches = oneMatch()), "owner|src-b", 1_001L)
        val loaded = FootballFeedCacheStore(context) { now }.load(day, "owner|src-b")
        assertTrue(loaded!!.stale)
        assertEquals(1, loaded.matches.size)
    }
}

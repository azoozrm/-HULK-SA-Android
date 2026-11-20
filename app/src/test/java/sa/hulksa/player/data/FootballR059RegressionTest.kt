package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballR059RegressionTest {

    private val day = "2026-11-20"

    private fun feedJson(matches: String) =
        """{"schemaVersion":1,"day":"$day","generatedAt":1795195800,"stale":false,"matches":$matches,"teams":[]}"""

    private fun matchJson() =
        """[{"id":"evt:1","providerEventId":"1","competition":{"id":"17","name":"Saudi Pro League"},
        "homeTeam":{"id":"258","name":"Al-Ahli"},"awayTeam":{"id":"267","name":"Al-Nassr"},
        "kickoffAt":1795195800,"status":"upcoming"}]"""

    private fun match(id: String) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "Saudi Pro League", arabicName = "دوري روشن السعودي"),
        homeTeam = FootballTeam("258", "Al-Ahli", arabicName = "الاهلي"),
        awayTeam = FootballTeam("267", "Al-Nassr", arabicName = "النصر"),
        kickoffEpochMs = 1_795_195_800_000L,
        status = FootballMatchStatus.UPCOMING,
    )

    private fun snapshot(cachedAt: Long, stale: Boolean, matches: List<FootballMatch> = emptyList()) =
        FootballFeedSnapshot(
            dayKey = day,
            generatedAtEpochMs = cachedAt,
            cachedAtEpochMs = cachedAt,
            stale = stale,
            retryAfterSeconds = null,
            refreshSeconds = 60,
            matches = matches,
            teams = emptyList(),
        )

    @Test
    fun `missing or wrong-typed matches is not an authoritative empty day`() {
        assertNull(parseFootballFeed("""{"schemaVersion":1,"day":"$day","generatedAt":1795195800}""", day))
        assertNull(parseFootballFeed(feedJson("{}"), day))
        assertNull(parseFootballFeed(feedJson("null"), day))
        assertNull(parseFootballFeed(feedJson("[null]"), day))
        assertNull(parseFootballFeed(feedJson("[{}]"), day))
    }

    @Test
    fun `a real empty array is a valid authoritative empty day`() {
        val parsed = parseFootballFeed(feedJson("[]"), day)
        assertNotNull(parsed)
        assertTrue(parsed!!.matches.isEmpty())
        assertFalse(parsed.stale)
    }

    @Test
    fun `a valid match row still parses`() {
        val parsed = parseFootballFeed(feedJson(matchJson()), day)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.matches.size)
        assertEquals("1", parsed.matches.single().providerEventId)
    }

    @Test
    fun `429 retry deadline blocks transport and unlocks exactly at expiry`() {
        var now = 1_000_000L
        var transportCalls = 0
        val gate = FootballRetryDeadline { now }
        fun attempt() {
            if (gate.canAttempt()) transportCalls++
        }

        now = 1_010_000L
        gate.onFailure(30L) // Retry-After 30 s
        attempt()
        assertEquals(0, transportCalls) // T+10, inside backoff
        now = 1_039_999L
        attempt()
        assertEquals(0, transportCalls)
        now = 1_040_000L
        attempt()
        assertEquals(1, transportCalls) // exactly one eligible request
    }

    @Test
    fun `stale 200 keeps its own deadline and a fresh response cannot erase it early`() {
        var now = 2_000_000L
        val gate = FootballRetryDeadline { now }
        now = 2_010_000L
        gate.onSuccess(30L)
        assertEquals(2_040_000L, gate.retryAtEpochMs)
        now = 2_020_000L
        gate.onSuccess(null)
        assertFalse(gate.canAttempt())
        assertEquals(2_040_000L, gate.retryAtEpochMs)
        now = 2_040_000L
        assertTrue(gate.canAttempt())
    }


    @Test
    fun `one explicit retry starts while active or cooling down starts none`() {
        assertFalse(footballShouldStartDayRefresh(force = true, active = true, cacheFresh = false, canAttempt = true))
        assertFalse(footballShouldStartDayRefresh(force = true, active = false, cacheFresh = true, canAttempt = false))
        assertTrue(footballShouldStartDayRefresh(force = true, active = false, cacheFresh = true, canAttempt = true))
        assertFalse(footballShouldStartDayRefresh(force = false, active = false, cacheFresh = true, canAttempt = true))
    }

    @Test
    fun `a shorter Retry-After cannot shorten the shared deadline`() {
        var now = 3_000_000L
        val gate = FootballRetryDeadline { now }
        now = 3_010_000L
        gate.onFailure(60L)
        now = 3_020_000L
        gate.onFailure(10L)
        assertEquals(3_070_000L, gate.retryAtEpochMs)
    }


    @Test
    fun `a failure older than the accepted row cannot stale it`() {
        val accepted = snapshot(cachedAt = 2_000L, stale = false).copy(fetchedAtEpochMs = 2_000L)
        val olderFailure = FootballDayFailure(
            error = FootballFeedError.UNAVAILABLE,
            attemptedAtEpochMs = 1_500L,
            startedAtEpochMs = 1_000L,
        )
        val newerFailure = olderFailure.copy(startedAtEpochMs = 3_000L)
        assertFalse(footballFailureOwnsResult(accepted, olderFailure))
        assertTrue(footballFailureOwnsResult(accepted, newerFailure))
        assertTrue(footballFailureOwnsResult(null, newerFailure))
        assertTrue(footballFailureOwnsResult(accepted.copy(stale = true), olderFailure))
    }


    @Test
    fun `Home rail uses the accepted empty instead of a rejected stale candidate`() {
        val accepted = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 1_000L)
        val lateStale = snapshot(
            cachedAt = 980L,
            stale = true,
            matches = listOf(match("late")),
        ).copy(fetchedAtEpochMs = 1_001L)
        val previous = FootballFeedUiState(daySnapshots = mapOf(day to accepted))
        val published = FootballFeedUiState(available = true, snapshot = lateStale)

        val merged = footballPublishedFeedState(previous, published, requestedDayKey = day, nowEpochMs = 1_001L)

        assertEquals(accepted, merged.snapshot)
        assertEquals(0, merged.matches.size)
        assertFalse(merged.stale)
    }

    @Test
    fun `Home failure without a disk baseline keeps a Center-accepted day visible`() {
        val accepted = snapshot(
            cachedAt = 1_000L,
            stale = false,
            matches = listOf(match("center")),
        ).copy(fetchedAtEpochMs = 1_000L)
        val previous = FootballFeedUiState(daySnapshots = mapOf(day to accepted))
        val published = FootballFeedUiState(
            available = true,
            snapshot = null,
            error = FootballFeedError.UNAVAILABLE,
        )

        val merged = footballPublishedFeedState(previous, published, requestedDayKey = day, nowEpochMs = 1_001L)

        assertEquals(accepted, merged.snapshot)
        assertEquals(1, merged.matches.size)
    }

    @Test
    fun `next-day fallback stays under its own day`() {
        val yesterday = "2026-11-19"
        val fallback = snapshot(cachedAt = 1_000L, stale = true).copy(
            dayKey = yesterday,
            fetchedAtEpochMs = 1_000L,
        )
        val published = FootballFeedUiState(available = true, snapshot = fallback)

        val merged = footballPublishedFeedState(
            previous = FootballFeedUiState(),
            published = published,
            requestedDayKey = day,
            nowEpochMs = 1_001L,
        )

        assertEquals(yesterday, merged.snapshot!!.dayKey)
        assertTrue(yesterday in merged.daySnapshots)
        assertFalse(day in merged.daySnapshots)
    }


    @Test
    fun `discovery change clears public A results but keeps private preferences`() {
        val favorite = FootballFavoriteTeam("258", "Al-Ahli", 1_000L)
        val old = snapshot(cachedAt = 2_000L, stale = false).copy(fetchedAtEpochMs = 2_000L)
        val state = FootballFeedUiState(
            snapshot = old,
            favoriteTeams = listOf(favorite),
            hideScores = true,
            daySnapshots = mapOf(day to old),
            dayFailures = mapOf(day to FootballDayFailure(FootballFeedError.UNAVAILABLE, 1L, 2L)),
            nextRetryAtEpochMs = 5_000L,
        )

        val cleared = footballClearedForDiscoveryChange(state)

        assertEquals(null, cleared.snapshot)
        assertTrue(cleared.daySnapshots.isEmpty())
        assertTrue(cleared.dayFailures.isEmpty())
        assertEquals(0L, cleared.nextRetryAtEpochMs)
        assertEquals(listOf(favorite), cleared.favoriteTeams)
        assertTrue(cleared.hideScores)
    }





    @Test
    fun `unchanged fresh network revalidation clears a superseded failure`() {
        val existing = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 900L)
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = 950L,
            retryAtEpochMs = 0L,
            startedAtEpochMs = 940L,
        )
        val previous = FootballFeedUiState(
            snapshot = existing,
            daySnapshots = mapOf(day to existing),
            dayFailures = mapOf(day to failure),
        )
        val revalidated = existing.copy(fetchedAtEpochMs = 1_100L)
        val published = FootballFeedUiState(available = true, snapshot = revalidated)

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            successfulDayKey = day,
            requestedDayKey = day,
            nowEpochMs = 1_100L,
        )

        assertFalse(merged.stale)
        assertTrue(merged.matches.isEmpty())
        assertFalse(day in merged.dayFailures)
        assertEquals(1_100L, merged.daySnapshots[day]!!.fetchedAtEpochMs)
    }

    @Test
    fun `different payload on tied timestamp keeps failure and rows`() {
        val existing = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 900L)
        val failure = FootballDayFailure(FootballFeedError.RATE_LIMITED, 950L, 0L, 940L)
        val previous = FootballFeedUiState(
            snapshot = existing,
            daySnapshots = mapOf(day to existing),
            dayFailures = mapOf(day to failure),
        )
        val conflicting = existing.copy(
            fetchedAtEpochMs = 1_100L,
            matches = listOf(match("conflict")),
        )
        val published = FootballFeedUiState(available = true, snapshot = conflicting)

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            successfulDayKey = day,
            requestedDayKey = day,
            nowEpochMs = 1_100L,
        )

        assertTrue(merged.matches.isEmpty())
        assertTrue(merged.stale)
        assertEquals(failure, merged.dayFailures[day])
    }


    @Test
    fun `Center tie revalidation carries explicit acceptance and renews receipt`() {
        val existing = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 900L)
        val revalidated = existing.copy(fetchedAtEpochMs = 1_100L)

        val result = footballDayAcceptance(existing, revalidated, 1_100L)

        assertTrue(result.accepted)
        assertEquals(1_100L, result.snapshot.fetchedAtEpochMs)
        assertTrue(result.snapshot.matches.isEmpty())
        assertFalse(result.snapshot.stale)
    }

    @Test
    fun `Center tied conflicting payload is not accepted`() {
        val existing = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 900L)
        val conflicting = existing.copy(
            fetchedAtEpochMs = 1_100L,
            matches = listOf(match("conflict")),
        )
        val result = footballDayAcceptance(existing, conflicting, 1_100L)
        assertFalse(result.accepted)
        assertEquals(existing, result.snapshot)
    }

    @Test
    fun `accepted revalidation clears the failure and passive effective failure`() {
        val existing = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 900L)
        val failure = FootballDayFailure(FootballFeedError.RATE_LIMITED, 950L, 0L, 940L)
        val state = FootballFeedUiState(
            snapshot = existing.copy(stale = true),
            daySnapshots = mapOf(day to existing),
            dayFailures = mapOf(day to failure),
        )
        val acceptance = footballDayAcceptance(existing, existing.copy(fetchedAtEpochMs = 1_100L), 1_100L)
        assertTrue(acceptance.accepted)
        val cleared = footballWithClearedDayFailure(state, day, 1_100L)

        assertFalse(day in cleared.dayFailures)
        assertFalse(cleared.snapshot!!.stale)
        assertEquals(null, footballEffectiveDayFailure(cleared.daySnapshots, cleared.dayFailures, day))
    }

    @Test
    fun `display failure never mutates the raw accepted record`() {
        val raw = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 998L)
        val state = FootballFeedUiState(snapshot = raw, daySnapshots = mapOf(day to raw))
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = 1_000L,
            retryAtEpochMs = 61_000L,
            startedAtEpochMs = 999L,
        )

        val result = footballWithDayFailure(state, day, failure, 1_000L, 61_000L)

        assertEquals(raw, result.daySnapshots[day])
        assertFalse(result.daySnapshots[day]!!.stale)
        assertTrue(result.snapshot!!.stale)
        assertEquals(failure, result.dayFailures[day])
    }

    @Test
    fun `superseded failure keeps its deadline without taking the rows`() {
        val raw = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 1_000L)
        val state = FootballFeedUiState(snapshot = raw, daySnapshots = mapOf(day to raw))
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = 1_000L,
            retryAtEpochMs = 61_000L,
            startedAtEpochMs = 999L,
        )

        val result = footballWithDayFailure(state, day, failure, 1_000L, 61_000L)

        assertEquals(raw, result.snapshot)
        assertEquals(61_000L, result.nextRetryAtEpochMs)
        assertTrue(day !in result.dayFailures)
    }

    @Test
    fun `older fresh-labelled publication cannot replace accepted fresh empty`() {
        val accepted = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 998L)
        val olderFresh = snapshot(
            cachedAt = 980L,
            stale = false,
            matches = listOf(match("older")),
        ).copy(fetchedAtEpochMs = 1_001L)
        val failure = FootballDayFailure(
            error = FootballFeedError.RATE_LIMITED,
            attemptedAtEpochMs = 1_001L,
            retryAtEpochMs = 61_000L,
            startedAtEpochMs = 999L,
        )
        val previous = FootballFeedUiState(
            daySnapshots = mapOf(day to accepted),
            dayFailures = mapOf(day to failure),
        )
        val published = FootballFeedUiState(available = true, snapshot = olderFresh)

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            requestedDayKey = day,
            nowEpochMs = 1_001L,
        )

        assertEquals(accepted, merged.daySnapshots[day])
        assertTrue(merged.matches.isEmpty())
        assertTrue(merged.stale)
        assertEquals(failure, merged.dayFailures[day])
    }

    @Test
    fun `Home yesterday fallback cannot replace accepted today`() {
        val todayEmpty = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 1_000L)
        val yesterday = snapshot(
            cachedAt = 900L,
            stale = true,
            matches = listOf(match("yesterday")),
        ).copy(dayKey = "2026-11-19", fetchedAtEpochMs = 1_001L)
        val previous = FootballFeedUiState(daySnapshots = mapOf(day to todayEmpty))
        val published = FootballFeedUiState(available = true, snapshot = yesterday)

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            requestedDayKey = day,
            nowEpochMs = 1_001L,
        )

        assertEquals(day, merged.snapshot!!.dayKey)
        assertTrue(merged.matches.isEmpty())
        assertFalse(merged.stale)
    }

    @Test
    fun `Center fresh empty recovery clears Home stale rows and failure`() {
        val staleRows = snapshot(cachedAt = 900L, stale = true, matches = listOf(match("old")))
        val failure = FootballDayFailure(FootballFeedError.UNAVAILABLE, 950L, 0L, 800L)
        val previous = FootballFeedUiState(
            daySnapshots = mapOf(day to staleRows),
            dayFailures = mapOf(day to failure),
        )
        val freshEmpty = snapshot(cachedAt = 1_000L, stale = false).copy(fetchedAtEpochMs = 1_000L)
        val published = FootballFeedUiState(available = true, snapshot = freshEmpty)

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            successfulDayKey = day,
            requestedDayKey = day,
            nowEpochMs = 1_001L,
        )

        assertTrue(merged.matches.isEmpty())
        assertFalse(merged.stale)
        assertFalse(day in merged.dayFailures)
    }

    @Test
    fun `Home success clears only its requested day failure`() {
        val todayFailure = FootballDayFailure(FootballFeedError.UNAVAILABLE, 1L, 0L)
        val tomorrowFailure = FootballDayFailure(FootballFeedError.RATE_LIMITED, 2L, 9L)
        val previous = FootballFeedUiState(
            dayFailures = mapOf(day to todayFailure, "2026-11-21" to tomorrowFailure),
        )
        val published = FootballFeedUiState(
            available = true,
            snapshot = snapshot(cachedAt = 5_000L, stale = false),
        )

        val merged = footballPublishedFeedState(previous, published, successfulDayKey = day)

        assertFalse(day in merged.dayFailures)
        assertEquals(tomorrowFailure, merged.dayFailures["2026-11-21"])
    }


    @Test
    fun `rejected older Home success does not clear the current failure`() {
        val failure = FootballDayFailure(FootballFeedError.RATE_LIMITED, 1L, 99_000L)
        val previous = FootballFeedUiState(
            daySnapshots = mapOf(day to snapshot(cachedAt = 9_000L, stale = false)),
            dayFailures = mapOf(day to failure),
        )
        val published = FootballFeedUiState(
            available = true,
            snapshot = snapshot(cachedAt = 8_000L, stale = false),
        )

        val merged = footballPublishedFeedState(
            previous = previous,
            published = published,
            successfulDayKey = day,
            nowEpochMs = 9_000L,
        )

        assertEquals(failure, merged.dayFailures[day])
        assertEquals(9_000L, merged.daySnapshots[day]!!.cachedAtEpochMs)
    }

    @Test
    fun `Home old stale row cannot replace a newer fresh day authority`() {
        val existing = snapshot(cachedAt = 9_000L, stale = false)
        val olderStale = snapshot(cachedAt = 8_000L, stale = true)
        assertEquals(existing, footballAcceptedDaySnapshot(existing, olderStale, 9_000L))
    }

    @Test
    fun `Home fresh empty recovery replaces a stale day`() {
        val stale = snapshot(cachedAt = 9_000L, stale = true)
        val freshEmpty = snapshot(cachedAt = 10_000L, stale = false)
        assertEquals(freshEmpty, footballAcceptedDaySnapshot(stale, freshEmpty, 10_000L))
    }
}

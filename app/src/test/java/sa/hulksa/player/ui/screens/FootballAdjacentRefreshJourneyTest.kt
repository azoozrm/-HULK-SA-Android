package sa.hulksa.player.ui.screens

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFeedError
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.footballDayAcceptance

class FootballAdjacentRefreshJourneyTest {
    private val today = "2026-11-20"
    private val tomorrow = "2026-11-21"
    private val start = 1_000_000L

    private fun snapshot(
        fetchedAt: Long,
        cachedAt: Long = fetchedAt,
        stale: Boolean = false,
    ) = FootballFeedSnapshot(
        dayKey = tomorrow,
        generatedAtEpochMs = cachedAt,
        cachedAtEpochMs = cachedAt,
        stale = stale,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = emptyList(),
        teams = emptyList(),
        fetchedAtEpochMs = fetchedAt,
    )

    private inner class Journey(
        exactSnapshot: FootballFeedSnapshot? = snapshot(start),
        selectedDay: String = tomorrow,
    ) {
        private val scheduler = FootballAdjacentDayScheduler(selectedDay)
        private val wakeUp = Channel<Unit>()
        var now = start
        var input = FootballAdjacentDayInputs(today, exactSnapshot, null, false, 0L, 60)
        val requests = mutableListOf<Pair<Long, Boolean>>()
        val waits = mutableListOf<Long?>()

        suspend fun run() = scheduler.run(
            readInputs = { input },
            awaitChangeOrTimeout = { _, waitMs ->
                waits += waitMs
                wakeUp.receive()
            },
            requestRefresh = { force -> requests += now to force },
            now = { now },
        )

        suspend fun wake(at: Long = now) {
            now = at
            wakeUp.send(Unit)
            yield()
        }

        fun accept(candidate: FootballFeedSnapshot) {
            input = input.copy(
                exactSnapshot = footballDayAcceptance(input.exactSnapshot, candidate, now).snapshot,
                loading = false,
                failure = null,
            )
        }
    }

    @Test
    fun `same time stale response preserves the exact record and spaces the next attempt`() = runBlocking {
        val journey = Journey()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        assertEquals(60_000L, journey.waits.last())

        journey.wake(start + 60_000L)
        assertEquals(listOf(1_060_000L to false), journey.requests)
        journey.input = journey.input.copy(loading = true)
        journey.wake()
        assertEquals(null, journey.waits.last())

        journey.accept(snapshot(journey.now + 200L, cachedAt = start, stale = true))
        journey.wake(journey.now + 200L)
        assertEquals(start, journey.input.exactSnapshot?.fetchedAtEpochMs)
        assertEquals(59_800L, journey.waits.last())

        journey.wake(start + 119_999L)
        assertEquals(1L, journey.waits.last())
        assertEquals(1, journey.requests.size)

        journey.wake(start + 120_000L)
        assertEquals(listOf(1_060_000L to false, 1_120_000L to false), journey.requests)
        journey.accept(snapshot(journey.now))
        journey.wake()
        assertEquals(1_120_000L, journey.input.exactSnapshot?.fetchedAtEpochMs)
        assertEquals(60_000L, journey.waits.last())
        job.cancelAndJoin()
    }

    @Test
    fun `unchanged state after a refused start still re-arms the same tab`() = runBlocking {
        val journey = Journey()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }

        journey.wake(start + 60_000L)
        assertEquals(listOf(1_060_000L to false), journey.requests)
        assertEquals(60_000L, journey.waits.last())

        journey.wake(start + 120_000L)
        assertEquals(listOf(1_060_000L to false, 1_120_000L to false), journey.requests)
        job.cancelAndJoin()
    }

    @Test
    fun `new discovery ttl takes effect without changing tabs`() = runBlocking {
        val journey = Journey()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        journey.input = journey.input.copy(discoveryRefreshSeconds = 120)
        journey.wake(start + 60_050L)
        assertEquals(emptyList<Pair<Long, Boolean>>(), journey.requests)
        assertEquals(59_950L, journey.waits.last())

        journey.input = journey.input.copy(discoveryRefreshSeconds = 30)
        journey.wake()
        assertEquals(listOf(1_060_050L to false), journey.requests)
        assertEquals(30_000L, journey.waits.last())
        job.cancelAndJoin()
    }

    @Test
    fun `cleared exact record waits for the shared cooldown then loads on the same tab`() = runBlocking {
        val journey = Journey()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        journey.input = journey.input.copy(
            exactSnapshot = null,
            discoveryRefreshSeconds = 120,
            retryAtEpochMs = start + 90_000L,
        )
        journey.wake()
        assertEquals(90_000L, journey.waits.last())
        assertEquals(0, journey.requests.size)

        journey.wake(start + 90_000L)
        assertEquals(listOf(1_090_000L to false), journey.requests)
        assertEquals(120_000L, journey.waits.last())
        job.cancelAndJoin()
    }

    @Test
    fun `failure uses the later shared deadline and resumes without another interval`() = runBlocking {
        val journey = Journey(exactSnapshot = null)
        journey.input = journey.input.copy(
            failure = FootballDayFailure(FootballFeedError.UNAVAILABLE, start, start + 40_000L),
            retryAtEpochMs = start + 90_000L,
        )
        val paused = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        assertEquals(90_000L, journey.waits.last())
        paused.cancelAndJoin()
        journey.now = start + 100_000L
        assertEquals(0, journey.requests.size)

        val resumed = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        assertEquals(listOf(1_100_000L to true), journey.requests)
        assertEquals(60_000L, journey.waits.last())
        resumed.cancelAndJoin()
    }

    @Test
    fun `switching to today or disabling discovery does not start another adjacent request`() = runBlocking {
        val journey = Journey()
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        waiting.cancelAndJoin()

        val todayJourney = Journey(exactSnapshot = null, selectedDay = today)
        val todayJob = launch(start = CoroutineStart.UNDISPATCHED) { todayJourney.run() }
        assertEquals(null, todayJourney.waits.last())
        assertEquals(0, todayJourney.requests.size)
        todayJob.cancelAndJoin()

        journey.input = journey.input.copy(exactSnapshot = null, discoveryRefreshSeconds = null)
        val disabled = launch(start = CoroutineStart.UNDISPATCHED) { journey.run() }
        assertEquals(null, journey.waits.last())
        assertEquals(0, journey.requests.size)
        disabled.cancelAndJoin()
    }
}

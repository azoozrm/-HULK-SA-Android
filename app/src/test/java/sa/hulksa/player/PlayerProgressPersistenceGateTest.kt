package sa.hulksa.player

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.resumePositionForHistory
import sa.hulksa.player.data.runUserLibraryProgressPersistenceOffMain
import sa.hulksa.player.data.userLibraryProgressWriteAllowed
import sa.hulksa.player.data.UserLibraryHistoryMutationGate
import sa.hulksa.player.model.HistoryEntry

class PlayerProgressPersistenceGateTest {
    @Test
    fun `progress persistence json work runs away from the Player caller thread`() = runBlocking {
        val callerThreadId = Thread.currentThread().id
        val calls = AtomicInteger(0)
        var persistenceThreadId = callerThreadId

        val result = runUserLibraryProgressPersistenceOffMain {
            calls.incrementAndGet()
            persistenceThreadId = Thread.currentThread().id
            "persisted"
        }

        assertEquals("persisted", result)
        assertEquals(1, calls.get())
        assertNotEquals(callerThreadId, persistenceThreadId)
    }

    @Test
    fun `progress persistence keeps cancellation structured`() = runBlocking {
        var cancellationObserved = false

        try {
            runUserLibraryProgressPersistenceOffMain<Unit> {
                throw CancellationException("progress persistence cancelled")
            }
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
    }

    @Test
    fun `rapid out of order progress keeps only the newest attempt writable`() {
        val gate = UserLibraryHistoryMutationGate()
        val older = gate.beginProgress()
        val newer = gate.beginProgress()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
        assertFalse(
            userLibraryProgressWriteAllowed(
                attemptCurrent = gate.isCurrent(older),
                sameAuthenticatedSession = true,
                expectedAccountId = "account-a",
                activeAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeProfileId = "profile-a",
            ),
        )
        assertTrue(
            userLibraryProgressWriteAllowed(
                attemptCurrent = gate.isCurrent(newer),
                sameAuthenticatedSession = true,
                expectedAccountId = "account-a",
                activeAccountId = "account-a",
                expectedProfileId = "profile-a",
                activeProfileId = "profile-a",
            ),
        )
    }

    @Test
    fun `profile switch rejects a late write for the prior profile`() {
        val gate = UserLibraryHistoryMutationGate()
        val priorProfile = gate.beginProgress()
        gate.invalidateProgress()

        assertFalse(
            userLibraryProgressWriteAllowed(
                attemptCurrent = gate.isCurrent(priorProfile),
                sameAuthenticatedSession = true,
                expectedAccountId = "account-a",
                activeAccountId = "account-a",
                expectedProfileId = "adult",
                activeProfileId = "kids",
            ),
        )
    }

    @Test
    fun `logout or session replacement rejects stale progress writes`() {
        val gate = UserLibraryHistoryMutationGate()
        val stale = gate.beginProgress()

        assertFalse(
            userLibraryProgressWriteAllowed(
                attemptCurrent = gate.isCurrent(stale),
                sameAuthenticatedSession = true,
                expectedAccountId = "account-a",
                activeAccountId = null,
                expectedProfileId = "adult",
                activeProfileId = "adult",
            ),
        )
        assertFalse(
            userLibraryProgressWriteAllowed(
                attemptCurrent = gate.isCurrent(stale),
                sameAuthenticatedSession = false,
                expectedAccountId = "account-a",
                activeAccountId = "account-a",
                expectedProfileId = "adult",
                activeProfileId = "adult",
            ),
        )
    }

    @Test
    fun `latest accepted progress remains resumable`() {
        val newest = history(positionMs = 81_000L, durationMs = 120_000L)
        val stale = history(positionMs = 12_000L, durationMs = 120_000L)

        assertEquals(81_000L, resumePositionForHistory(listOf(newest, stale), newest.key))
    }

    @Test
    fun `in flight progress cannot restore history after clear`() {
        val gate = UserLibraryHistoryMutationGate()
        val progress = gate.beginProgress()
        var persistedHistory = listOf(history(positionMs = 10_000L, durationMs = 120_000L))

        gate.beginHistoryMutation()
        persistedHistory = emptyList()
        val written = gate.writeIfCurrent(progress) { persistedHistory = listOf(history(70_000L, 120_000L)) }

        assertEquals(null, written)
        assertTrue(persistedHistory.isEmpty())
    }

    @Test
    fun `in flight progress cannot restore a removed entry`() {
        val gate = UserLibraryHistoryMutationGate()
        val progress = gate.beginProgress()
        var persistedHistory = listOf(history(positionMs = 10_000L, durationMs = 120_000L))

        gate.beginHistoryMutation()
        persistedHistory = persistedHistory.filterNot { it.key == "MOVIE:1" }
        val written = gate.writeIfCurrent(progress) { persistedHistory = listOf(history(70_000L, 120_000L)) }

        assertEquals(null, written)
        assertTrue(persistedHistory.none { it.key == "MOVIE:1" })
    }

    @Test
    fun `in flight progress cannot overwrite a newer record start`() {
        val gate = UserLibraryHistoryMutationGate()
        val progress = gate.beginProgress()
        var persistedHistory = listOf(history(positionMs = 10_000L, durationMs = 120_000L))
        val newer = history(positionMs = 0L, durationMs = 0L).copy(key = "EPISODE:2")

        gate.beginHistoryMutation()
        persistedHistory = listOf(newer)
        val written = gate.writeIfCurrent(progress) { persistedHistory = listOf(history(70_000L, 120_000L)) }

        assertEquals(null, written)
        assertEquals(listOf(newer), persistedHistory)
    }

    @Test
    fun `progress after a history mutation remains writable and resumable`() {
        val gate = UserLibraryHistoryMutationGate()
        gate.beginHistoryMutation()
        val progress = gate.beginProgress()
        val persisted = gate.writeIfCurrent(progress) { history(81_000L, 120_000L) }

        assertEquals(81_000L, resumePositionForHistory(listOf(requireNotNull(persisted)), "MOVIE:1"))
    }

    private fun history(positionMs: Long, durationMs: Long): HistoryEntry = HistoryEntry(
        key = "MOVIE:1",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 1,
        extension = "mp4",
        isLive = false,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = positionMs,
    )
}

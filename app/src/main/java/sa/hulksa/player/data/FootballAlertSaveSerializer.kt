package sa.hulksa.player.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes read-modify-write updates of the per-profile match-alert map. Two quick saves used to
 * read the same snapshot and overwrite each other; the mutex makes each update observe the previous
 * write while keeping the existing inbox/alert encoding and profile scope untouched.
 */
internal class FootballAlertSaveSerializer {
    private val mutex = Mutex()

    suspend fun update(
        read: suspend () -> Map<String, FootballMatchAlertPreferences>,
        write: suspend (Map<String, FootballMatchAlertPreferences>) -> Unit,
        match: FootballMatch,
        preferences: FootballMatchAlertPreferences,
        nowEpochMs: Long,
    ): Map<String, FootballMatchAlertPreferences> = mutex.withLock {
        val updated = upsertFootballAlertPreferences(
            current = read(),
            match = match,
            preferences = preferences,
            nowEpochMs = nowEpochMs,
        )
        write(updated)
        updated
    }
}

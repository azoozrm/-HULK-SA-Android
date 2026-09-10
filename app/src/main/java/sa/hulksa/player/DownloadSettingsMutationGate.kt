package sa.hulksa.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps only the newest Downloads settings intent eligible for durable storage. */
internal class DownloadSettingsMutationGate {
    data class Attempt internal constructor(val generation: Long)

    private var generation = 0L
    private var activeGeneration: Long? = null
    private val persistenceMutex = Mutex()

    @Synchronized
    fun begin(): Attempt {
        generation += 1L
        activeGeneration = generation
        return Attempt(generation)
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = activeGeneration == attempt.generation

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }

    suspend fun <T> writeIfCurrent(attempt: Attempt, write: () -> T): T? =
        persistenceMutex.withLock {
            if (isCurrent(attempt)) write() else null
        }
}

/** Serializes every priority intent for one download without sharing resume ownership. */
internal class DownloadPriorityMutationQueue {
    data class Attempt internal constructor(val generation: Long)

    private val mutex = Mutex()
    private var generation = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun begin(): Attempt {
        generation += 1L
        activeGeneration = generation
        return Attempt(generation)
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = activeGeneration == attempt.generation

    @Synchronized
    fun invalidate() {
        generation += 1L
        activeGeneration = null
    }

    suspend fun <T> write(write: () -> T): T = mutex.withLock { write() }
}

package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType

internal data class CatalogLoadResult(
    val full: Catalog,
    val home: Catalog,
)

/**
 * Owns catalog request generation, the single active catalog pipeline and the published loading
 * set.
 *
 * Requests are admitted to a pending map and executed by one runner. The runner never overlaps
 * two full catalogs; the latest user-selected pending type is selected next, while the currently
 * active load is always allowed to finish. Background Home types remain pending and are resumed
 * after the foreground type.
 *
 * A successful session replacement invalidates obsolete ownership before new requests start:
 * the active load and all pending requests are cancelled, loading is settled, and any late result
 * checks both the request generation and the current session before it may publish or clear
 * loading. A stale completion therefore cannot suppress a newer request or clear its indicator.
 */
internal class CatalogRequestOwner(
    private val scope: CoroutineScope,
    private val load: suspend (AuthenticatedSession, ContentType) -> CatalogLoadResult,
    private val hasLoaded: (ContentType) -> Boolean,
    private val isSessionCurrent: (AuthenticatedSession) -> Boolean,
    private val onLoadingChanged: (Set<ContentType>) -> Unit,
    private val onLoaded: (ContentType, CatalogLoadResult, Set<ContentType>) -> Unit,
    private val onFailure: (ContentType, Throwable, Set<ContentType>) -> Unit,
) {
    private data class PendingRequest(
        val session: AuthenticatedSession,
        val type: ContentType,
        val requestGeneration: Long,
    )

    private val lock = Any()
    // Held across the entire load/compaction operation. Unlike a runner-generation slot, this
    // survives cancellation: a non-cooperative parser keeps the resource until it physically
    // returns, so a replacement generation cannot overlap it.
    private val pipeline = Mutex()
    private var generation = 0L
    private var runner: Job? = null
    private var runnerToken = 0L
    private var activeRequest: PendingRequest? = null
    private val pending = linkedMapOf<ContentType, PendingRequest>()
    private val loading = linkedSetOf<ContentType>()
    private var foregroundType: ContentType? = null

    fun request(
        session: AuthenticatedSession,
        type: ContentType,
        force: Boolean,
    ): Boolean {
        val loadingSnapshot: Set<ContentType>
        val startRunner: Boolean
        synchronized(lock) {
            if (
                !catalogRequestShouldStart(
                    hasLoadedCatalog = hasLoaded(type),
                    force = force,
                    inFlight = type in loading,
                )
            ) return false

            pending[type] = PendingRequest(
                session = session,
                type = type,
                requestGeneration = generation,
            )
            loading += type
            loadingSnapshot = loading.toSet()
            startRunner = runner == null
            if (startRunner) {
                runnerToken += 1L
                val token = runnerToken
                runner = scope.launch(start = CoroutineStart.LAZY) { runLoop(token) }
            }
        }
        try {
            onLoadingChanged(loadingSnapshot)
        } finally {
            if (startRunner) runner?.start()
        }
        return true
    }

    /**
     * The latest user-selected type becomes the next pending catalog. It never interrupts the
     * active catalog; if that type is already active or loaded this is a no-op for the queue.
     */
    fun prioritize(type: ContentType?) {
        synchronized(lock) {
            foregroundType = type
        }
    }

    /**
     * Settles obsolete requests for a new session, then loads the types the current destination
     * needs. Cached types are left to the caller's loaded predicate and are not re-fetched.
     */
    fun replaceSession(
        session: AuthenticatedSession,
        requiredTypes: Set<ContentType>,
        foregroundType: ContentType? = null,
    ) {
        invalidate()
        synchronized(lock) {
            this.foregroundType = foregroundType
        }
        requiredTypes.forEach { type ->
            request(session = session, type = type, force = false)
        }
    }

    fun invalidate() {
        val cancelledRunner: Job?
        synchronized(lock) {
            generation += 1L
            runnerToken += 1L
            cancelledRunner = runner
            runner = null
            activeRequest = null
            pending.clear()
            loading.clear()
            foregroundType = null
        }
        cancelledRunner?.cancel()
        onLoadingChanged(emptySet())
    }

    internal fun loadingTypes(): Set<ContentType> = synchronized(lock) { loading.toSet() }

    private suspend fun runLoop(token: Long) {
        try {
            while (true) {
                val hasPending = synchronized(lock) {
                    if (runnerToken != token) return
                    if (pending.isEmpty()) {
                        // Atomic with the pending check: a concurrent request either observes
                        // this null and starts a new runner, or appends before this lock and is
                        // selected.
                        runner = null
                        return
                    }
                    true
                }
                if (!hasPending) return

                var exitAfterPermit = false
                pipeline.withLock {
                    // A pending request is committed only after the physical permit is held.
                    // A late foreground change during the wait therefore still selects the
                    // latest type when execution actually becomes available.
                    val request = synchronized(lock) {
                        if (runnerToken != token) {
                            exitAfterPermit = true
                            null
                        } else if (pending.isEmpty()) {
                            null
                        } else {
                            val nextType = selectNextPendingLocked()
                            val next = pending.remove(nextType)
                            activeRequest = next
                            next
                        }
                    }
                    if (request != null) {
                        try {
                            val result = load(request.session, request.type)
                            publishLoaded(
                                session = request.session,
                                type = request.type,
                                requestGeneration = request.requestGeneration,
                                result = result,
                            )
                        } catch (cancelled: CancellationException) {
                            // Ownership loss is settled by invalidate(); a cancellation must
                            // never clear a newer request's loading indicator.
                            throw cancelled
                        } catch (error: Throwable) {
                            publishFailure(
                                session = request.session,
                                type = request.type,
                                requestGeneration = request.requestGeneration,
                                error = error,
                            )
                        } finally {
                            synchronized(lock) {
                                if (activeRequest === request) activeRequest = null
                            }
                        }
                    }
                }
                if (exitAfterPermit) return
            }
        } finally {
            synchronized(lock) {
                if (runnerToken == token) runner = null
            }
        }
    }

    private fun selectNextPendingLocked(): ContentType {
        val foreground = foregroundType
        if (foreground != null && pending.containsKey(foreground)) return foreground
        return pending.keys.first()
    }

    private fun publishLoaded(
        session: AuthenticatedSession,
        type: ContentType,
        requestGeneration: Long,
        result: CatalogLoadResult,
    ) {
        val remaining = synchronized(lock) {
            if (!canPublish(requestGeneration, session)) return
            loading.remove(type)
            loading.toSet()
        }
        onLoaded(type, result, remaining)
    }

    private fun publishFailure(
        session: AuthenticatedSession,
        type: ContentType,
        requestGeneration: Long,
        error: Throwable,
    ) {
        val remaining = synchronized(lock) {
            if (!canPublish(requestGeneration, session)) return
            loading.remove(type)
            loading.toSet()
        }
        onFailure(type, error, remaining)
    }

    private fun canPublish(
        requestGeneration: Long,
        session: AuthenticatedSession,
    ): Boolean = catalogPublicationAllowed(
        requestGeneration = requestGeneration,
        currentGeneration = generation,
        sessionCurrent = isSessionCurrent(session),
    )
}

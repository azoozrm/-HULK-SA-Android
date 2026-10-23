package sa.hulksa.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType

internal data class CatalogLoadResult(
    val full: Catalog,
    val home: Catalog,
)

/**
 * Owns catalog request generation, per-type jobs and the published loading set.
 *
 * A successful session replacement invalidates obsolete ownership before new requests start:
 * old jobs are cancelled, their loading types are settled, and any late result checks both the
 * request generation and the current session before it may publish or clear loading. A stale
 * completion therefore cannot suppress a newer request or clear its indicator.
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
    private val lock = Any()
    private var generation = 0L
    private val jobs = mutableMapOf<ContentType, Job>()
    private val loading = linkedSetOf<ContentType>()

    fun request(
        session: AuthenticatedSession,
        type: ContentType,
        force: Boolean,
    ): Boolean {
        val job: Job
        val loadingSnapshot: Set<ContentType>
        synchronized(lock) {
            if (
                !catalogRequestShouldStart(
                    hasLoadedCatalog = hasLoaded(type),
                    force = force,
                    inFlight = jobs[type]?.isActive == true,
                )
            ) return false

            val requestGeneration = generation
            job = scope.launch(start = CoroutineStart.LAZY) {
                execute(
                    session = session,
                    type = type,
                    requestGeneration = requestGeneration,
                )
            }
            jobs[type] = job
            loading += type
            loadingSnapshot = loading.toSet()
        }
        onLoadingChanged(loadingSnapshot)
        job.start()
        return true
    }

    /**
     * Settles obsolete requests for a new session, then loads the types the current destination
     * needs. Cached types are left to the caller's loaded predicate and are not re-fetched.
     */
    fun replaceSession(
        session: AuthenticatedSession,
        requiredTypes: Set<ContentType>,
    ) {
        invalidate()
        requiredTypes.forEach { type ->
            request(session = session, type = type, force = false)
        }
    }

    fun invalidate() {
        val cancelledJobs: List<Job>
        synchronized(lock) {
            generation += 1L
            cancelledJobs = jobs.values.toList()
            jobs.clear()
            loading.clear()
        }
        cancelledJobs.forEach(Job::cancel)
        onLoadingChanged(emptySet())
    }

    internal fun loadingTypes(): Set<ContentType> = synchronized(lock) { loading.toSet() }

    private suspend fun execute(
        session: AuthenticatedSession,
        type: ContentType,
        requestGeneration: Long,
    ) {
        try {
            val result = load(session, type)
            publishLoaded(
                session = session,
                type = type,
                requestGeneration = requestGeneration,
                result = result,
            )
        } catch (cancelled: CancellationException) {
            // Ownership loss is already settled by invalidate(); a cancellation must never
            // clear a newer request's loading indicator.
            throw cancelled
        } catch (error: Throwable) {
            publishFailure(
                session = session,
                type = type,
                requestGeneration = requestGeneration,
                error = error,
            )
        }
    }

    private fun publishLoaded(
        session: AuthenticatedSession,
        type: ContentType,
        requestGeneration: Long,
        result: CatalogLoadResult,
    ) {
        val remaining = synchronized(lock) {
            if (!canPublish(requestGeneration, session)) return
            jobs.remove(type)
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
            jobs.remove(type)
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

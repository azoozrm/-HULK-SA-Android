package sa.hulksa.player

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * R09: foreground priority must reorder only pending catalog work. It must never overlap two
 * full-catalog pipelines or cancel/restart the active one.
 */
class CatalogRequestOwnerPriorityTest {
    @Test
    fun `latest selected pending Live runs before queued Series after active Movie`() = runBlocking {
        val harness = Harness()
        harness.owner.request(harness.session, ContentType.MOVIE, force = false)
        harness.owner.request(harness.session, ContentType.SERIES, force = false)
        harness.owner.request(harness.session, ContentType.LIVE, force = false)
        harness.awaitCalls(ContentType.MOVIE)

        harness.owner.prioritize(ContentType.LIVE)
        harness.complete(ContentType.MOVIE, movie = 1)

        harness.awaitCalls(ContentType.MOVIE, ContentType.LIVE)
        assertEquals(1, harness.maxActive.get())
        harness.complete(ContentType.LIVE, live = 3)

        harness.awaitCalls(ContentType.MOVIE, ContentType.LIVE, ContentType.SERIES)
        harness.complete(ContentType.SERIES, series = 2)

        harness.awaitSettled()
        assertEquals(
            listOf(ContentType.MOVIE, ContentType.LIVE, ContentType.SERIES),
            harness.calls.map { it.second },
        )
        assertEquals(1, harness.maxActive.get())
        assertEquals(3, harness.loaded.size)
        harness.scope.cancel()
    }

    @Test
    fun `active Series is allowed to finish when Live is selected and is not restarted`() = runBlocking {
        val harness = Harness()
        harness.owner.request(harness.session, ContentType.MOVIE, force = false)
        harness.owner.request(harness.session, ContentType.SERIES, force = false)
        harness.owner.request(harness.session, ContentType.LIVE, force = false)
        harness.awaitCalls(ContentType.MOVIE)
        harness.complete(ContentType.MOVIE, movie = 1)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES)

        harness.owner.prioritize(ContentType.LIVE)
        delay(50L)
        assertEquals(
            "the active Series must not be cancelled or restarted",
            listOf(ContentType.MOVIE, ContentType.SERIES),
            harness.calls.map { it.second },
        )
        assertTrue(harness.cancelled.isEmpty())

        harness.complete(ContentType.SERIES, series = 2)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        harness.complete(ContentType.LIVE, live = 3)
        harness.awaitSettled()
        assertEquals(1, harness.maxActive.get())
        harness.scope.cancel()
    }

    @Test
    fun `latest rapid selection wins the next pending slot without duplicate fetch`() = runBlocking {
        val harness = Harness()
        harness.owner.request(harness.session, ContentType.MOVIE, force = false)
        harness.owner.request(harness.session, ContentType.SERIES, force = false)
        harness.owner.request(harness.session, ContentType.LIVE, force = false)
        harness.awaitCalls(ContentType.MOVIE)

        harness.owner.prioritize(ContentType.LIVE)
        harness.owner.prioritize(ContentType.SERIES)
        harness.complete(ContentType.MOVIE, movie = 1)

        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES)
        harness.complete(ContentType.SERIES, series = 2)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        harness.complete(ContentType.LIVE, live = 3)
        harness.awaitSettled()

        assertEquals(
            listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            harness.calls.map { it.second },
        )
        assertEquals(1, harness.calls.count { it.second == ContentType.LIVE })
        assertEquals(1, harness.calls.count { it.second == ContentType.SERIES })
        harness.scope.cancel()
    }

    @Test
    fun `loaded destination reuses its catalog and is not requested again`() = runBlocking {
        val harness = Harness()
        harness.loaded[ContentType.MOVIE] = result(movie = 7)

        assertFalse(harness.owner.request(harness.session, ContentType.MOVIE, force = false))
        assertTrue(harness.calls.isEmpty())
        assertTrue(harness.owner.request(harness.session, ContentType.SERIES, force = false))
        harness.awaitCalls(ContentType.SERIES)
        harness.complete(ContentType.SERIES, series = 8)
        harness.awaitSettled()
        assertEquals(listOf(ContentType.SERIES), harness.calls.map { it.second })
        harness.scope.cancel()
    }

    @Test
    fun `entry and force overlap stay coalesced while one pipeline is active`() = runBlocking {
        val harness = Harness()
        assertTrue(harness.owner.request(harness.session, ContentType.LIVE, force = false))
        harness.awaitCalls(ContentType.LIVE)
        assertFalse(harness.owner.request(harness.session, ContentType.LIVE, force = false))
        assertFalse(harness.owner.request(harness.session, ContentType.LIVE, force = true))
        assertEquals(1, harness.calls.size)

        harness.complete(ContentType.LIVE, live = 1)
        harness.awaitSettled()
        assertTrue(harness.owner.request(harness.session, ContentType.LIVE, force = true))
        harness.awaitCalls(ContentType.LIVE, ContentType.LIVE)
        harness.complete(ContentType.LIVE, live = 2)
        harness.awaitSettled()
        assertEquals(2, harness.calls.size)
        harness.scope.cancel()
    }

    @Test
    fun `one pipeline is active and deferred background work completes`() = runBlocking {
        val harness = Harness()
        harness.owner.request(harness.session, ContentType.MOVIE, force = false)
        harness.owner.request(harness.session, ContentType.SERIES, force = false)
        harness.owner.request(harness.session, ContentType.LIVE, force = false)
        harness.awaitCalls(ContentType.MOVIE)
        harness.complete(ContentType.MOVIE, movie = 1)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES)
        harness.complete(ContentType.SERIES, series = 2)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        harness.complete(ContentType.LIVE, live = 3)
        harness.awaitSettled()
        assertEquals(1, harness.maxActive.get())
        assertEquals(3, harness.loaded.size)
        assertTrue(harness.owner.loadingTypes().isEmpty())
        harness.scope.cancel()
    }


    @Test
    fun `late foreground selection wins after the physical pipeline is released`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val oldSession = session()
        val newSession = session()
        var currentSession: AuthenticatedSession = oldSession
        val calls = CopyOnWriteArrayList<Pair<AuthenticatedSession, ContentType>>()
        val newCalls = CopyOnWriteArrayList<ContentType>()
        val loaded = ConcurrentHashMap<ContentType, CatalogLoadResult>()
        val oldEntered = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val newEntered = CompletableDeferred<ContentType>()
        val releaseNew = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()

        val owner = CatalogRequestOwner(
            scope = scope,
            load = { session, type ->
                calls += session to type
                val now = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, now) }
                try {
                    if (session === oldSession) {
                        oldEntered.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (cancelled: CancellationException) {
                            withContext(NonCancellable) {
                                oldCancelled.complete(Unit)
                                releaseOld.await()
                            }
                        }
                        result(movie = 1)
                    } else {
                        newCalls += type
                        newEntered.complete(type)
                        releaseNew.await()
                        when (type) {
                            ContentType.MOVIE -> result(movie = 2)
                            ContentType.SERIES -> result(series = 3)
                            ContentType.LIVE -> result(live = 4)
                        }
                    }
                } finally {
                    active.decrementAndGet()
                }
            },
            hasLoaded = { loaded.containsKey(it) },
            isSessionCurrent = { it === currentSession },
            onLoadingChanged = {},
            onLoaded = { type, result, _ -> loaded[type] = result },
            onFailure = { _, _, _ -> },
        )

        owner.request(oldSession, ContentType.MOVIE, force = false)
        oldEntered.await()
        currentSession = newSession
        owner.replaceSession(
            newSession,
            setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
        )
        oldCancelled.await()

        // The replacement runner is waiting for the physical permit. The latest of several
        // priority changes must decide the first type once the permit is released.
        owner.prioritize(ContentType.LIVE)
        owner.prioritize(ContentType.SERIES)
        owner.prioritize(ContentType.LIVE)

        assertFalse(
            "replacement generation must not enter while the cancelled loader still holds the pipeline",
            newEntered.isCompleted,
        )
        assertEquals(1, maxActive.get())

        releaseOld.complete(Unit)
        val firstNewType = withTimeout(5_000L) { newEntered.await() }
        assertEquals(
            "the latest foreground type must be selected after the permit is acquired",
            ContentType.LIVE,
            firstNewType,
        )
        releaseNew.complete(Unit)
        withTimeout(5_000L) {
            while (owner.loadingTypes().isNotEmpty()) delay(5L)
        }

        assertEquals(
            listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES),
            newCalls.toList(),
        )
        assertEquals(2, loaded.getValue(ContentType.MOVIE).full.items.single().id)
        assertEquals(3, loaded.getValue(ContentType.SERIES).full.items.single().id)
        assertEquals(4, loaded.getValue(ContentType.LIVE).full.items.single().id)
        assertEquals(
            listOf(oldSession to ContentType.MOVIE, newSession to ContentType.LIVE),
            calls.take(2),
        )
        assertEquals(1, maxActive.get())
        scope.cancel()
    }

    @Test
    fun `request after an idle handoff restarts the runner and drains both types`() = runBlocking {
        val harness = Harness()
        harness.owner.request(harness.session, ContentType.MOVIE, force = false)
        harness.awaitCalls(ContentType.MOVIE)
        harness.complete(ContentType.MOVIE, movie = 1)
        harness.awaitSettled()

        harness.owner.request(harness.session, ContentType.SERIES, force = false)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES)
        harness.complete(ContentType.SERIES, series = 2)
        harness.awaitSettled()
        harness.owner.request(harness.session, ContentType.LIVE, force = false)
        harness.awaitCalls(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
        harness.complete(ContentType.LIVE, live = 3)
        harness.awaitSettled()

        assertEquals(
            listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE),
            harness.calls.map { it.second },
        )
        assertEquals(1, harness.maxActive.get())
        harness.scope.cancel()
    }

    private class Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = session()
        val calls = CopyOnWriteArrayList<Pair<AuthenticatedSession, ContentType>>()
        val cancelled = CopyOnWriteArrayList<ContentType>()
        val loaded = ConcurrentHashMap<ContentType, CatalogLoadResult>()
        val gates = ConcurrentHashMap<ContentType, CompletableDeferred<CatalogLoadResult>>()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val owner = CatalogRequestOwner(
            scope = scope,
            load = { session, type ->
                val gate = gates.computeIfAbsent(type) { CompletableDeferred() }
                calls += session to type
                val now = active.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, now) }
                try {
                    gate.await()
                } catch (cancelledError: CancellationException) {
                    cancelled += type
                    throw cancelledError
                } finally {
                    active.decrementAndGet()
                }
            },
            hasLoaded = { loaded.containsKey(it) },
            isSessionCurrent = { it === session },
            onLoadingChanged = {},
            onLoaded = { type, result, _ -> loaded[type] = result },
            onFailure = { _, _, _ -> },
        )

        fun complete(type: ContentType, movie: Int? = null, series: Int? = null, live: Int? = null) {
            gates.getValue(type).complete(result(movie = movie, series = series, live = live))
        }

        suspend fun awaitCalls(vararg expected: ContentType) {
            withTimeout(8_000L) {
                while (calls.map { it.second } != expected.toList()) delay(5L)
            }
        }

        suspend fun awaitSettled() {
            withTimeout(8_000L) {
                while (owner.loadingTypes().isNotEmpty()) delay(5L)
            }
        }
    }

    private companion object {
        fun session(): AuthenticatedSession = AuthenticatedSession(
            portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-TEST-CODE", "user", "pass"),
            account = AccountInfo("user", "Active", Long.MAX_VALUE, 0, 1, false),
        )

        fun result(
            movie: Int? = null,
            series: Int? = null,
            live: Int? = null,
        ): CatalogLoadResult {
            val items = buildList {
                movie?.let { add(item(it, ContentType.MOVIE)) }
                series?.let { add(item(it, ContentType.SERIES)) }
                live?.let { add(item(it, ContentType.LIVE)) }
            }
            val catalog = Catalog(emptyList(), items)
            return CatalogLoadResult(full = catalog, home = catalog)
        }

        fun item(id: Int, type: ContentType): ContentItem = ContentItem(
            id = id,
            name = "${type.name} $id",
            categoryId = "test",
            type = type,
            posterUrl = null,
            rating = null,
            year = null,
            containerExtension = "mp4",
        )
    }
}

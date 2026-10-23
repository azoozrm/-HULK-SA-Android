package sa.hulksa.player

import java.io.File
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
 * R02: a successful account reauthentication must hand catalog ownership to the new session.
 * Old requests may not publish or clear a newer request's loading; the new session must load the
 * current destination automatically and settle loadingTypes.
 */
class CatalogRequestOwnerTest {
    @Test
    fun `successful account refresh hands catalog ownership to the current destination`() {
        val source = File("src/main/java/sa/hulksa/player/HulkViewModel.kt").readText()
        val success = source
            .substringAfter("is AccountRefreshOutcome.Success ->")
            .substringBefore("is AccountRefreshOutcome.Failure ->")

        assertTrue(success.contains("catalogRequestOwner.replaceSession("))
        assertTrue(success.contains("catalogTypesForDestination(mutableState.value.destination)"))
        assertFalse(success.contains("clearCatalogMemory()"))
    }

    @Test
    fun `session replacement cancels old Home loads and publishes only the current session`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session1 = session()
        val session2 = session()
        var currentSession: AuthenticatedSession = session1
        val loaded = Collections.synchronizedMap(mutableMapOf<ContentType, CatalogLoadResult>())
        val failures = CopyOnWriteArrayList<Throwable>()
        val calls = CopyOnWriteArrayList<Pair<AuthenticatedSession, ContentType>>()
        val gates = Collections.synchronizedMap(
            mutableMapOf<Pair<Int, ContentType>, CompletableDeferred<CatalogLoadResult>>(),
        )

        val owner = CatalogRequestOwner(
            scope = scope,
            load = { session, type ->
                calls += session to type
                val gate = synchronized(gates) {
                    gates.getOrPut(sessionKey(session, type)) { CompletableDeferred() }
                }
                try {
                    gate.await()
                } catch (cancelled: CancellationException) {
                    // One obsolete type deliberately completes after cancellation to prove the
                    // generation/session guard still prevents publication and loading cleanup.
                    if (session !== session1 || type != ContentType.MOVIE) throw cancelled
                    result(movie = 900)
                }
            },
            hasLoaded = { loaded[it] != null },
            isSessionCurrent = { it === currentSession },
            onLoadingChanged = {},
            onLoaded = { type, result, _ -> loaded[type] = result },
            onFailure = { _, error, _ -> failures += error },
        )

        owner.request(session1, ContentType.MOVIE, force = false)
        owner.request(session1, ContentType.SERIES, force = false)
        owner.request(session1, ContentType.LIVE, force = false)
        awaitUntil { calls.size == 3 && owner.loadingTypes() == HOME_TYPES }
        assertEquals(HOME_TYPES, owner.loadingTypes())

        currentSession = session2
        owner.replaceSession(session2, HOME_TYPES)
        awaitUntil {
            calls.count { (session, _) -> session === session2 } == 3 &&
                owner.loadingTypes() == HOME_TYPES
        }

        // Release the held cold-Home responses from the old session.
        completeOldGate(gates, session1, ContentType.MOVIE, result(movie = 900))
        completeOldGate(gates, session1, ContentType.SERIES, result(series = 901))
        completeOldGate(gates, session1, ContentType.LIVE, result(live = 902))
        awaitUntil { calls.count { (session, _) -> session === session1 } == 3 }
        delay(150L)

        assertTrue("obsolete results must not publish", loaded.isEmpty())
        assertEquals("new request loading must remain settled", HOME_TYPES, owner.loadingTypes())
        assertTrue("obsolete failures must stay silent", failures.isEmpty())

        completeNewGate(gates, session2, ContentType.MOVIE, result(movie = 1))
        completeNewGate(gates, session2, ContentType.SERIES, result(series = 2))
        completeNewGate(gates, session2, ContentType.LIVE, result(live = 3))

        awaitUntil { loaded.size == 3 && owner.loadingTypes().isEmpty() }
        assertEquals(listOf(3), loaded.getValue(ContentType.LIVE).full.items.map(ContentItem::id))
        assertEquals(1, loaded.getValue(ContentType.MOVIE).full.items.single().id)
        assertEquals(2, loaded.getValue(ContentType.SERIES).full.items.single().id)
        assertTrue(owner.loadingTypes().isEmpty())
        assertEquals(6, calls.size)
        scope.cancel()
    }

    @Test
    fun `cached Movie and Series are preserved across session replacement`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session1 = session()
        val session2 = session()
        var currentSession: AuthenticatedSession = session1
        val loaded = Collections.synchronizedMap(
            mutableMapOf(
                ContentType.MOVIE to result(movie = 10),
                ContentType.SERIES to result(series = 11),
            ),
        )
        val calls = CopyOnWriteArrayList<Pair<AuthenticatedSession, ContentType>>()
        val gate = CompletableDeferred<CatalogLoadResult>()

        val owner = CatalogRequestOwner(
            scope = scope,
            load = { session, type ->
                calls += session to type
                gate.await()
            },
            hasLoaded = { loaded[it] != null },
            isSessionCurrent = { it === currentSession },
            onLoadingChanged = {},
            onLoaded = { type, result, _ -> loaded[type] = result },
            onFailure = { _, _, _ -> },
        )

        currentSession = session2
        owner.replaceSession(session2, HOME_TYPES)
        awaitUntil { calls.any { (session, _) -> session === session2 } }
        assertEquals(setOf(ContentType.LIVE), owner.loadingTypes())
        assertEquals(1, calls.size)
        assertEquals(ContentType.LIVE, calls.single().second)
        assertSame(session2, calls.single().first)

        gate.complete(result(live = 12))
        awaitUntil { loaded.size == 3 && owner.loadingTypes().isEmpty() }
        assertEquals(10, loaded.getValue(ContentType.MOVIE).full.items.single().id)
        assertEquals(11, loaded.getValue(ContentType.SERIES).full.items.single().id)
        assertEquals(12, loaded.getValue(ContentType.LIVE).full.items.single().id)
        scope.cancel()
    }

    @Test
    fun `obsolete failure cannot clear a newer request or surface an error`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session1 = session()
        val session2 = session()
        var currentSession: AuthenticatedSession = session1
        val failures = CopyOnWriteArrayList<Throwable>()
        val oldGate = CompletableDeferred<CatalogLoadResult>()
        val newGate = CompletableDeferred<CatalogLoadResult>()
        var loadedLive: CatalogLoadResult? = null

        val owner = CatalogRequestOwner(
            scope = scope,
            load = { session, _ ->
                if (session === session1) {
                    try {
                        oldGate.await()
                    } catch (cancelled: CancellationException) {
                        throw IllegalStateException("obsolete transport failure", cancelled)
                    }
                } else {
                    newGate.await()
                }
            },
            hasLoaded = { loadedLive != null },
            isSessionCurrent = { it === currentSession },
            onLoadingChanged = {},
            onLoaded = { _, result, _ -> loadedLive = result },
            onFailure = { _, error, _ -> failures += error },
        )

        owner.request(session1, ContentType.LIVE, force = false)
        awaitUntil { owner.loadingTypes() == setOf(ContentType.LIVE) }
        currentSession = session2
        owner.replaceSession(session2, setOf(ContentType.LIVE))
        awaitUntil { owner.loadingTypes() == setOf(ContentType.LIVE) }

        oldGate.completeExceptionally(IllegalStateException("obsolete transport failure"))
        delay(150L)

        assertTrue("obsolete failure must not surface", failures.isEmpty())
        assertEquals("obsolete failure must not clear the new loading", setOf(ContentType.LIVE), owner.loadingTypes())

        val published = result(live = 44)
        newGate.complete(published)
        awaitUntil { loadedLive === published && owner.loadingTypes().isEmpty() }
        assertTrue(failures.isEmpty())
        assertSame(published, loadedLive)
        scope.cancel()
    }

    private fun completeOldGate(
        gates: MutableMap<Pair<Int, ContentType>, CompletableDeferred<CatalogLoadResult>>,
        session: AuthenticatedSession,
        type: ContentType,
        result: CatalogLoadResult,
    ) {
        synchronized(gates) { gates.getValue(sessionKey(session, type)).complete(result) }
    }

    private fun completeNewGate(
        gates: MutableMap<Pair<Int, ContentType>, CompletableDeferred<CatalogLoadResult>>,
        session: AuthenticatedSession,
        type: ContentType,
        result: CatalogLoadResult,
    ) {
        synchronized(gates) { gates.getValue(sessionKey(session, type)).complete(result) }
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(8_000L) {
            while (!predicate()) delay(10L)
        }
    }

    private fun sessionKey(
        session: AuthenticatedSession,
        type: ContentType,
    ): Pair<Int, ContentType> = System.identityHashCode(session) to type

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("HULK-TEST-CODE", "user", "pass"),
        account = AccountInfo("user", "Active", Long.MAX_VALUE, 0, 1, false),
    )

    private fun result(
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

    private fun item(id: Int, type: ContentType): ContentItem = ContentItem(
        id = id,
        name = "$type $id",
        categoryId = "category-$id",
        type = type,
        posterUrl = "https://artwork.example.test/$type-$id.jpg",
        rating = "8.0",
        year = null,
        containerExtension = if (type == ContentType.LIVE) "m3u8" else "mp4",
        genre = "Drama",
        addedAtEpochSeconds = 1_000L + id,
    )

    private companion object {
        val HOME_TYPES = setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
    }
}

package sa.hulksa.player

import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.ProviderAdmission
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * Production catalog-owner recovery checks. A real CatalogRequestOwner runs the real pending/
 * runner path with the real admission policy; only the clock and the provider loader are virtual.
 */
class ProviderRateLimitRecoveryTest {
    private class FakeClock(
        startNs: Long = System.currentTimeMillis() * 1_000_000L,
    ) {
        private val nanos = AtomicLong(startNs)
        fun nowNs(): Long = nanos.get()
        fun nowMs(): Long = nowNs() / 1_000_000L
        fun advanceNs(delta: Long) {
            nanos.addAndGet(delta)
        }
    }

    private class Harness(
        private val failSeries: (Int) -> Boolean,
        private val retryAfter: String? = "10",
        private val controllableSleep: Boolean = false,
    ) {
        val clock = FakeClock()
        val sleepGate = CompletableDeferred<Unit>()
        @Volatile
        var sleepEntered = false

        val admission = ProviderAdmission(
            nowNs = clock::nowNs,
            nowWallMs = clock::nowMs,
            sleepNs = { nanos ->
                sleepEntered = true
                if (controllableSleep) sleepGate.await()
                clock.advanceNs(nanos)
                yield()
            },
            ownerIsCurrent = { true },
        )

        val session = AuthenticatedSession(
            portal = PortalConfig("https://provider.example:8443/live", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("CODE", "user", "pass"),
            account = AccountInfo("user", "Active", null, 1, 1, false),
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val loads = Collections.synchronizedList(mutableListOf<Pair<ContentType, Long>>())
        val published = Collections.synchronizedList(mutableListOf<ContentType>())
        val failures = Collections.synchronizedList(mutableListOf<ContentType>())
        @Volatile
        var recoveryScheduled = false
        private var seriesAttempts = 0

        lateinit var owner: CatalogRequestOwner

        init {
            owner = CatalogRequestOwner(
                scope = scope,
                load = { activeSession, type ->
                    synchronized(this) {
                        loads += type to clock.nowNs()
                        if (type == ContentType.SERIES) seriesAttempts += 1
                        val attempt = seriesAttempts
                        if (type == ContentType.SERIES && failSeries(attempt)) {
                            if (retryAfter != null) {
                                admission.onRateLimited(
                                    activeSession,
                                    null,
                                    retryAfter,
                                    clock.nowMs(),
                                )
                            }
                            throw XtreamException.ServiceBlocked(429, "cloudflare")
                        }
                    }
                    val empty = Catalog(emptyList(), emptyList())
                    CatalogLoadResult(full = empty, home = empty)
                },
                hasLoaded = { false },
                isSessionCurrent = { it === session },
                onLoadingChanged = {},
                onLoaded = { type, _, _ -> published += type },
                onFailure = { type, error, _ ->
                    failures += type
                    recoveryScheduled =
                        owner.scheduleRateLimitedRecovery(session, type, error) || recoveryScheduled
                },
                admission = admission,
            )
        }

        fun close() {
            scope.cancel()
        }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    @Test
    fun `one real recovery publishes the rejected type after the server deadline`() = runBlocking {
        val harness = Harness(failSeries = { it == 1 })
        try {
            harness.owner.request(harness.session, ContentType.SERIES, force = false)
            awaitUntil { ContentType.SERIES in harness.published }

            val seriesLoads = harness.loads.filter { it.first == ContentType.SERIES }
            assertEquals(2, seriesLoads.size)
            assertTrue(
                seriesLoads[1].second - seriesLoads[0].second >= 10_000_000_000L,
            )
            assertEquals(listOf(ContentType.SERIES), harness.published.toList())
            assertTrue(harness.recoveryScheduled)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `repeated 429 terminates after one bounded recovery`() = runBlocking {
        val harness = Harness(failSeries = { true })
        try {
            harness.owner.request(harness.session, ContentType.SERIES, force = false)
            awaitUntil { harness.failures.size >= 2 }
            delay(200)
            assertEquals(2, harness.failures.size)
            assertEquals(2, harness.loads.size)

            harness.clock.advanceNs(60_000_000_000L)
            delay(200)
            assertEquals("no endless recovery loop", 2, harness.loads.size)
        } finally {
            harness.close()
        }
    }

    @Test
    fun `missing retry-after does not schedule automatic recovery`() = runBlocking {
        val harness = Harness(failSeries = { true }, retryAfter = null)
        try {
            harness.owner.request(harness.session, ContentType.SERIES, force = false)
            awaitUntil { harness.failures.size == 1 }
            delay(200)
            assertEquals(1, harness.loads.size)
            assertFalse(harness.recoveryScheduled)
            assertTrue(harness.published.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `owner invalidation cancels the waiting recovery`() = runBlocking {
        val harness = Harness(failSeries = { it == 1 }, controllableSleep = true)
        try {
            harness.owner.request(harness.session, ContentType.SERIES, force = false)
            awaitUntil { harness.sleepEntered }
            harness.owner.invalidate()
            harness.sleepGate.complete(Unit)
            delay(200)

            assertEquals(1, harness.loads.size)
            assertTrue(harness.published.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun `successful catalogs are preserved and only the rejected type recovers`() = runBlocking {
        val harness = Harness(failSeries = { it == 1 })
        try {
            harness.owner.request(harness.session, ContentType.MOVIE, force = false)
            harness.owner.request(harness.session, ContentType.SERIES, force = false)
            awaitUntil {
                ContentType.MOVIE in harness.published && ContentType.SERIES in harness.published
            }

            assertEquals(
                1,
                harness.loads.count { it.first == ContentType.MOVIE },
            )
            assertEquals(
                2,
                harness.loads.count { it.first == ContentType.SERIES },
            )
            assertEquals(0, harness.loads.count { it.first == ContentType.LIVE })
        } finally {
            harness.close()
        }
    }

    @Test
    fun `startup scan gate can be extended across recovery without releasing`() {
        val gate = StartupScanPriorityGate()
        val owner = Any()
        assertTrue(gate.begin(owner, setOf(ContentType.MOVIE, ContentType.SERIES)))
        assertFalse(gate.settle(owner, ContentType.MOVIE))

        gate.hold(owner, setOf(ContentType.SERIES))
        assertTrue(gate.isHeld(owner))
        assertEquals(setOf(ContentType.SERIES), gate.pendingTypes(owner))

        assertTrue(gate.settle(owner, ContentType.SERIES))
        assertFalse(gate.isHeld(owner))
    }

    @Test
    fun `viewmodel wires recovery scheduling and scan priority hold`() {
        val source = File("src/main/java/sa/hulksa/player/HulkViewModel.kt").readText()
        val block = source
            .substringAfter("onFailure = { type, error, remaining ->")
            .take(2_000)
        assertTrue(block.contains("catalogRequestOwner.scheduleRateLimitedRecovery"))
        assertTrue(block.contains("startupScanPriorityGate.hold"))
        assertTrue(
            block.indexOf("scheduleRateLimitedRecovery") <
                block.indexOf("startupScanPriorityGate.hold"),
        )
    }
}

package sa.hulksa.player.data

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * Production-path R30 checks: the real XtreamClient, its real OkHttp interceptor and the real
 * 429 response headers. Only the monotonic/wall clock is virtual, so the "did it dispatch early?"
 * question is answered deterministically without provider traffic or wall-clock waiting.
 */
class ProviderRetryAfterNetworkTest {
    private lateinit var server: MockWebServer

    private class FakeClock(
        startNs: Long = System.currentTimeMillis() * 1_000_000L,
    ) {
        private val nanos = AtomicLong(startNs)
        fun nowNs(): Long = nanos.get()
        fun nowMs(): Long = nowNs() / 1_000_000L
        suspend fun sleep(delta: Long) {
            nanos.addAndGet(delta)
            yield()
        }
    }

    private class Arrival(val action: String, val atMs: Long)

    private lateinit var clock: FakeClock
    private lateinit var admission: ProviderAdmission
    private val arrivals = Collections.synchronizedList(mutableListOf<Arrival>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        clock = FakeClock()
        admission = ProviderAdmission(
            nowNs = clock::nowNs,
            nowWallMs = clock::nowMs,
            sleepNs = clock::sleep,
            ownerIsCurrent = { true },
        )
        arrivals.clear()
    }

    @After
    fun tearDown() {
        admission.onAdmissionGranted = null
        ProviderRequestTrace.sink = null
        runCatching { server.shutdown() }
    }

    private fun session(): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "user", "pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )

    private fun dispatch(body: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                arrivals += Arrival(action, clock.nowMs())
                return body(request)
            }
        }
    }

    private fun arrivalsFor(action: String): List<Long> =
        synchronized(arrivals) { arrivals.filter { it.action == action }.map { it.atMs } }

    @Test
    fun `actual 429 retry-after blocks the next new provider dispatch`() = runBlocking {
        val first = AtomicBoolean(true)
        dispatch {
            if (first.getAndSet(false)) {
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"error":"limited"}""")
            } else {
                MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        assertNotNull(admission.cooldownDeadlineNs(session))

        client.contentDetails(session, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue(
            "new dispatch ${times.last()} - ${times.first()} must honor Retry-After: 10",
            times.last() - times.first() >= 10_000L,
        )
    }

    @Test
    fun `http date retry-after blocks the next dispatch`() = runBlocking {
        val first = AtomicBoolean(true)
        dispatch {
            if (first.getAndSet(false)) {
                val date = ZonedDateTime.now()
                    .plusSeconds(7)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME)
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", date)
                    .setBody("""{"error":"limited"}""")
            } else {
                MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        client.contentDetails(session, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue(times.last() - times.first() >= 6_000L)
    }

    @Test
    fun `missing retry-after does not invent a delay`() = runBlocking {
        val first = AtomicBoolean(true)
        dispatch {
            if (first.getAndSet(false)) {
                MockResponse().setResponseCode(429).setBody("""{"error":"limited"}""")
            } else {
                MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        client.contentDetails(session, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue(times.last() - times.first() < 1_000L)
    }

    @Test
    fun `sibling cancellation does not lose the 429 deadline`() = runBlocking {
        dispatch { request ->
            when (request.requestUrl?.queryParameter("action")) {
                "get_vod_categories" -> MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"error":"limited"}""")
                "get_vod_streams" -> MockResponse()
                    .setResponseCode(200)
                    .setBody("""[{"stream_id":1,"name":"one"}]""")
                    .setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                "get_vod_info" -> MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"info":{"name":"ok"}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.catalog(session, ContentType.MOVIE) }
        assertNotNull(admission.cooldownDeadlineNs(session))

        client.contentDetails(session, 3)
        val infoAt = arrivalsFor("get_vod_info").single()
        val catalogAt = arrivalsFor("get_vod_categories").single()
        assertTrue(infoAt - catalogAt >= 10_000L)
    }

    @Test
    fun `diagnostics failure cannot change admission behavior`() = runBlocking {
        ProviderRequestTrace.sink = { throw IllegalStateException("diagnostics unavailable") }
        val first = AtomicBoolean(true)
        dispatch {
            if (first.getAndSet(false)) {
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"error":"limited"}""")
            } else {
                MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        client.contentDetails(session, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue(times.last() - times.first() >= 10_000L)
    }

    @Test
    fun `ordinary success without 429 keeps immediate dispatch`() = runBlocking {
        dispatch { MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""") }
        val client = XtreamClient(admission = admission)
        val session = session()

        client.contentDetails(session, 1)
        client.contentDetails(session, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue(times.last() - times.first() < 1_000L)
    }

    @Test
    fun `series card metadata dispatch also honors the shared deadline`() = runBlocking {
        val firstSeries = AtomicBoolean(true)
        dispatch { request ->
            when (request.requestUrl?.queryParameter("action")) {
                "get_vod_info" -> MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "9")
                    .setBody("""{"error":"limited"}""")
                "get_series_info" -> {
                    if (firstSeries.getAndSet(false)) {
                        MockResponse().setResponseCode(200).setBody("""{"info":{"name":"s"}}""")
                    } else {
                        MockResponse().setResponseCode(200).setBody("""{"info":{"name":"s"}}""")
                    }
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = XtreamClient(admission = admission)
        val metadata = SeriesCardMetadataClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        metadata.fetch(session, 9)

        val seriesAt = arrivalsFor("get_series_info").single()
        val infoAt = arrivalsFor("get_vod_info").single()
        assertTrue(seriesAt - infoAt >= 9_000L)
    }


    @Test
    fun `simultaneous catalog and scan dispatches after cooldown are spaced`() = runBlocking {
        val first = AtomicBoolean(true)
        dispatch { request ->
            val action = request.requestUrl?.queryParameter("action").orEmpty()
            if (first.getAndSet(false) && action == "get_vod_info") {
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"error":"limited"}""")
            } else {
                when (action) {
                    "get_live_categories" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"category_id":"1","category_name":"All"}]""")
                    "get_live_streams" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"stream_id":1,"name":"one"}]""")
                    "get_series_info" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"info":{"name":"s"}}""")
                    else -> MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
                }
            }
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        runCatching { client.contentDetails(session, 1) }
        val deadline = requireNotNull(admission.cooldownDeadlineNs(session))
        val grants = Collections.synchronizedList(mutableListOf<Long>())
        admission.onAdmissionGranted = { grants += it }
        listOf(
            async { runCatching { client.catalog(session, ContentType.LIVE) } },
            async { runCatching { client.seriesBundle(session, 1) } },
        ).awaitAll()

        val afterLimit = synchronized(arrivals) {
            arrivals.filter { it.action != "get_vod_info" }.map { it.action }
        }
        assertTrue(afterLimit.size >= 3)
        val grantTimes = synchronized(grants) { grants.sorted() }
        assertEquals(3, grantTimes.size)
        assertTrue(grantTimes.first() >= deadline)
        grantTimes.zipWithNext().forEach { (left, right) ->
            assertTrue("release burst at $left -> $right", right - left >= 250_000_000L)
        }
    }

    @Test
    fun `another account on the same provider is not blocked`() = runBlocking {
        val first = AtomicBoolean(true)
        dispatch { request ->
            if (first.getAndSet(false)) {
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "10")
                    .setBody("""{"error":"limited"}""")
            } else {
                MockResponse().setResponseCode(200).setBody("""{"info":{"name":"ok"}}""")
            }
        }
        val client = XtreamClient(admission = admission)
        val blockedAccount = session()
        val otherAccount = AuthenticatedSession(
            portal = blockedAccount.portal,
            credentials = Credentials("CODE-2", "other-user", "pass-2"),
            account = AccountInfo("other-user", "Active", null, 1, 1, false),
        )

        runCatching { client.contentDetails(blockedAccount, 1) }
        client.contentDetails(otherAccount, 2)

        val times = arrivalsFor("get_vod_info")
        assertTrue(times.size >= 2)
        assertTrue("another account must not inherit the deadline", times.last() - times.first() < 1_000L)
    }

    @Test
    fun `stale session response cannot modify the current owner deadline`() {
        val current = session()
        AuthenticatedSessionRegistry.update(
            current,
            AccountSessionMetadata(
                accountId = "acct",
                username = current.credentials.username,
                portalBaseUrl = current.portal.baseUrl,
                authenticatedAtEpochMs = 0L,
                expiresAtEpochSeconds = null,
                status = "Active",
                installationId = "install",
                sessionId = "session-2",
            ),
        )
        dispatch {
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "10")
                .setBody("""{"error":"limited"}""")
        }
        try {
            val stale = AuthenticatedSession(
                portal = current.portal,
                credentials = Credentials("OLD", "old-user", "old-pass"),
                account = AccountInfo("old-user", "Active", null, 1, 1, false),
            )
            val client = XtreamClient(admission = admission)

            runCatching { runBlocking { client.contentDetails(stale, 1) } }

            assertNull(admission.cooldownDeadlineNs(stale))
            assertNull(admission.cooldownDeadlineNs(current))
            assertEquals(1, arrivalsFor("get_vod_info").size)
        } finally {
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `429 without a usable body still records the deadline`() {
        dispatch {
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "4")
                .setBody("")
        }
        val client = XtreamClient(admission = admission)
        val session = session()

        assertThrows(XtreamException.Http::class.java) {
            runBlocking { client.contentDetails(session, 1) }
        }
        assertNotNull(admission.cooldownDeadlineNs(session))
    }
}

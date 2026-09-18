package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresenceClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `start sends exact version-one shape and parses 201`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"sessionId":"$SESSION_ID","presenceToken":"$TOKEN","heartbeatSeconds":60,"onlineTtlSeconds":180,"serverTimeEpochSeconds":1770000000}""",
            ),
        )
        val result = client().start(config(), snapshot())

        assertTrue(result is PresenceStartResult.Accepted)
        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/presence/start/", request.path)
        assertEquals("POST", request.method)
        assertFalse(request.headers.names().contains("Authorization"))
        val json = JSONObject(request.body.readUtf8())
        assertEquals(
            setOf(
                "contractVersion",
                "sessionId",
                "installationId",
                "accessCode",
                "iptvUsername",
                "iptvPassword",
                "host",
                "authenticatedAtEpochMs",
                "device",
                "app",
            ),
            json.keysSet(),
        )
        assertEquals(1, json.getInt("contractVersion"))
        assertEquals("TEST-CODE", json.getString("accessCode"))
        assertEquals("synthetic-user", json.getString("iptvUsername"))
        assertEquals("synthetic-password", json.getString("iptvPassword"))
        assertEquals("PHONE", json.getJSONObject("device").getString("platformClass"))
        assertEquals("0.9.3.20", json.getJSONObject("app").getString("versionName"))
    }

    @Test
    fun `heartbeat and end carry bearer and exact ownership body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = client()

        assertEquals(PresenceCommandResult.Accepted, client.heartbeat(config(), SESSION_ID, TOKEN))
        assertEquals(
            PresenceCommandResult.Accepted,
            client.end(config(), SESSION_ID, TOKEN, PresenceEndReason.ACCOUNT_REPLACED),
        )

        val heartbeat = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/presence/heartbeat/", heartbeat.path)
        assertEquals("Bearer $TOKEN", heartbeat.getHeader("Authorization"))
        assertEquals(setOf("sessionId"), JSONObject(heartbeat.body.readUtf8()).keysSet())
        val end = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/control-center/api/app/v1/presence/end/", end.path)
        assertEquals("Bearer $TOKEN", end.getHeader("Authorization"))
        assertEquals("ACCOUNT_REPLACED", JSONObject(end.body.readUtf8()).getString("reason"))
    }

    @Test
    fun `malformed oversized and deterministic failures are terminal`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("not-json"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("x".repeat(17 * 1_024)))
        server.enqueue(MockResponse().setResponseCode(409).setBody("{}"))
        val client = client()

        assertEquals(PresenceStartResult.TerminalFailure, client.start(config(), snapshot()))
        assertEquals(PresenceStartResult.TerminalFailure, client.start(config(), snapshot()))
        assertEquals(
            PresenceCommandResult.TerminalFailure,
            client.heartbeat(config(), SESSION_ID, TOKEN),
        )
        assertEquals(
            PresenceStartResult.TerminalFailure,
            client.start(config(), snapshot().copy(iptvPassword = "x".repeat(513))),
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `network and server failures are transient without automatic retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val client = client(
            OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
        )
        val offlineServer = MockWebServer()
        offlineServer.start()
        val offlineConfig = config().copy(
            baseUrl = offlineServer.url("/control-center/api/app/v1/presence/").toString(),
        )
        offlineServer.shutdown()

        assertEquals(PresenceStartResult.TransientFailure, client.start(offlineConfig, snapshot()))
        assertEquals(PresenceStartResult.TransientFailure, client.start(config(), snapshot()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `transport timeout is a transient presence-only failure`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = client(
            OkHttpClient.Builder()
                .callTimeout(100, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build(),
        )

        assertEquals(PresenceStartResult.TransientFailure, client.start(config(), snapshot()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cancelling coroutine cancels in-flight presence request`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val cancelled = CountDownLatch(1)
        val observed = CompletableDeferred<Throwable>()
        val client = client(
            OkHttpClient.Builder()
                .eventListener(
                    object : EventListener() {
                        override fun canceled(call: Call) {
                            cancelled.countDown()
                        }
                    },
                )
                .build(),
        )
        val job = launch(Dispatchers.Default) {
            try {
                client.start(config(), snapshot())
            } catch (error: Throwable) {
                observed.complete(error)
                throw error
            }
        }

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancel()
        assertTrue(cancelled.await(5, TimeUnit.SECONDS))
        assertTrue(withTimeout(5_000L) { observed.await() } is CancellationException)
        withTimeout(5_000L) { job.join() }
    }

    private fun client(okHttpClient: OkHttpClient = PresenceClient.defaultPresenceHttpClient()) =
        PresenceClient(okHttpClient)

    private fun config() = OperationsPresenceConfig(
        baseUrl = server.url("/control-center/api/app/v1/presence/").toString(),
        heartbeatSeconds = 60,
        onlineTtlSeconds = 180,
    )

    private fun snapshot() = PresenceSessionSnapshot(
        sessionId = SESSION_ID,
        installationId = INSTALLATION_ID,
        accessCode = "TEST-CODE",
        iptvUsername = "synthetic-user",
        iptvPassword = "synthetic-password",
        host = "https://iptv.invalid:443",
        authenticatedAtEpochMs = 1_770_000_000_000L,
        device = PresenceDeviceMetadata(
            platformClass = PresencePlatformClass.PHONE,
            manufacturer = "Synthetic",
            model = "Test Device",
            androidRelease = "15",
            sdkInt = 35,
        ),
        app = PresenceAppMetadata(versionName = "0.9.3.20", versionCode = 64),
    )

    private companion object {
        const val SESSION_ID = "11111111-1111-4111-8111-111111111111"
        const val INSTALLATION_ID = "22222222-2222-4222-8222-222222222222"
        val TOKEN = "t".repeat(43)
    }
}

private fun JSONObject.keysSet(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
}

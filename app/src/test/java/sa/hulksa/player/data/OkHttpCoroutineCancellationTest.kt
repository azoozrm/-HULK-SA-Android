package sa.hulksa.player.data

import java.io.IOException
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
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class OkHttpCoroutineCancellationTest {
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
    fun successfulRequestReturnsNormallyAndResponseIsClosed(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("payload"))
        val client = OkHttpClient()
        lateinit var capturedResponse: Response

        val status = client.executeCancellable(
            Request.Builder().url(server.url("/success")).build(),
        ) { response ->
            capturedResponse = response
            response.code
        }

        assertEquals(200, status)
        assertThrows(IllegalStateException::class.java) {
            capturedResponse.body!!.source().readByte()
        }
    }

    @Test
    fun realIOExceptionRemainsIOExceptionWhenCoroutineIsActive() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build()

        assertThrows(IOException::class.java) {
            runBlocking {
                client.executeCancellable(
                    Request.Builder().url(server.url("/disconnect")).build(),
                ) { response ->
                    response.body?.string().orEmpty()
                }
            }
        }
    }

    @Test
    fun coroutineCancellationCancelsUnderlyingOkHttpCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val transportCancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        transportCancelled.countDown()
                    }
                },
            )
            .build()
        val observedFailure = CompletableDeferred<Throwable>()

        val job = launch(Dispatchers.Default) {
            try {
                client.executeCancellable(
                    Request.Builder().url(server.url("/slow")).build(),
                ) { response ->
                    response.body?.string().orEmpty()
                }
            } catch (error: Throwable) {
                observedFailure.complete(error)
                throw error
            }
        }

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancel()

        assertTrue("OkHttp EventListener did not observe Call.cancel()", transportCancelled.await(5, TimeUnit.SECONDS))
        val error = withTimeout(5_000L) { observedFailure.await() }
        assertTrue("Cancellation must remain coroutine cancellation", error is CancellationException)
        withTimeout(5_000L) { job.join() }
    }

    @Test
    fun xtreamCancellationDoesNotBecomeNetworkFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val observedFailure = CompletableDeferred<Throwable>()
        val client = XtreamClient()
        val portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE)
        val credentials = Credentials("TEST-CODE", "user", "pass")

        val job = launch(Dispatchers.Default) {
            try {
                client.authenticate(portal, credentials)
            } catch (error: Throwable) {
                observedFailure.complete(error)
                throw error
            }
        }

        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancel()

        val error = withTimeout(5_000L) { observedFailure.await() }
        assertTrue("Xtream cancellation was mapped as a domain/network failure", error is CancellationException)
        withTimeout(5_000L) { job.join() }
    }

    @Test
    fun xtreamRealTransportFailureRemainsNetworkFailure() {
        val offlineServer = MockWebServer()
        offlineServer.start()
        val offlineBaseUrl = offlineServer.url("/").toString()
        offlineServer.shutdown()
        val client = XtreamClient()
        val portal = PortalConfig(offlineBaseUrl, PortalConfig.Source.ACCESS_CODE)
        val credentials = Credentials("TEST-CODE", "user", "pass")

        assertThrows(XtreamException.Network::class.java) {
            runBlocking { client.authenticate(portal, credentials) }
        }
    }

    @Test
    fun xtreamOversizedBodyBehaviorRemainsBounded() {
        val oversizedPadding = "x".repeat((XtreamJsonLimits.AUTH.maxResponseBytes + 128L).toInt())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"padding\":\"$oversizedPadding\"}"),
        )
        val client = XtreamClient()
        val portal = PortalConfig(server.url("/").toString(), PortalConfig.Source.ACCESS_CODE)
        val credentials = Credentials("TEST-CODE", "user", "pass")

        assertThrows(XtreamException.PayloadLimitExceeded::class.java) {
            runBlocking { client.authenticate(portal, credentials) }
        }
    }
}

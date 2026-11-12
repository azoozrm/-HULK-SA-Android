package sa.hulksa.player.data

import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.CredentialBase64Codec
import sa.hulksa.player.security.CredentialKeyStore
import sa.hulksa.player.security.CredentialVault

/**
 * R24 controlled account-switch reproduction. These tests drive the real repository, the real
 * Xtream client, the real account store/registry and the real series-card metadata store against
 * two isolated local fake provider endpoints. Only the reseller resolver is adapted: its mapping
 * from synthetic access code to endpoint is injected so no real provider is contacted.
 */
class AccountSwitchIsolationTest {
    private lateinit var providerA: FakeProvider
    private lateinit var providerB: FakeProvider
    private lateinit var fixture: SwitchFixture
    private lateinit var metadataStore: SeriesCardMetadataStore

    @Before
    fun setUp() {
        providerA = FakeProvider(name = "A", height = 1080)
        providerB = FakeProvider(name = "B", height = 480)
        providerA.start()
        providerB.start()
        fixture = SwitchFixture(providerA, providerB)
        metadataStore = SeriesCardMetadataStore.get(fixture.context)
    }

    @After
    fun tearDown() {
        AuthenticatedSessionRegistry.clear()
        providerA.shutdown()
        providerB.shutdown()
    }

    @Test
    fun `same series id uses owner scoped cache and credentials across account switch`() = runBlocking {
        val a = fixture.login("CODE-A", "user-a", "pass-a")
        val ownerA = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(providerA.baseUrl, a.portal.baseUrl)

        val first = metadataStore.metadata(ownerA, 9200, requirePresentation = true, surface = ProviderSurface.HERO)
        assertEquals("FHD", first.quality)
        assertEquals("PLOT-A", first.plot)

        val b = fixture.login("CODE-B", "user-b", "pass-b")
        val ownerB = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(providerB.baseUrl, b.portal.baseUrl)

        val second = metadataStore.metadata(ownerB, 9200, requirePresentation = true, surface = ProviderSurface.HERO)
        assertEquals("SD", second.quality)
        assertEquals("PLOT-B", second.plot)
        assertNotNull(metadataStore.cached(ownerB, 9200).quality)
        assertTrue(providerA.seriesRequests().all { it.user == "user-a" && it.password == "pass-a" })
        assertTrue(providerB.seriesRequests().all { it.user == "user-b" && it.password == "pass-b" })
        assertEquals(1, providerA.seriesRequests().count { it.seriesId == 9200 })
        assertEquals(1, providerB.seriesRequests().count { it.seriesId == 9200 })
    }

    @Test
    fun `saved login restores the same portal and credentials after registry loss`() = runBlocking {
        fixture.login("CODE-A", "user-a", "pass-a")
        val storedOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(providerA.baseUrl, storedOwner.session.portal.baseUrl)

        // Process-restart equivalent for the registry: durable account metadata and vault remain.
        AuthenticatedSessionRegistry.clear()
        assertNull(AuthenticatedSessionRegistry.currentOwner())

        val restored = withTimeout(10_000) { fixture.repository.currentAuthenticatedSession() }
        assertNotNull(restored)
        assertEquals(providerA.baseUrl, restored!!.portal.baseUrl)
        assertEquals("user-a", restored.credentials.username)
        val restoredOwner = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(storedOwner.accountId, restoredOwner.accountId)
        assertEquals(storedOwner.providerId, restoredOwner.providerId)
        assertEquals(0, providerB.seriesRequests().size)
        assertTrue(providerB.authRequests().isEmpty())
    }

    @Test
    fun `different portal or user never shares catalog or metadata keys`() {
        val hostA = "https://provider-a.example:8443/live"
        val sameUserOtherHost = stableAccountId("https://provider-b.example:8443/live", "subscriber")
        val sameHostOtherUser = stableAccountId(hostA, "other-subscriber")
        val schemeVariant = stableAccountId("http://provider-a.example:8443/live", "subscriber")
        val portVariant = stableAccountId("https://provider-a.example:9443/live", "subscriber")
        val basePathVariant = stableAccountId("https://provider-a.example:8443/other", "subscriber")
        val baseline = stableAccountId(hostA, "subscriber")

        assertNotEquals(baseline, sameUserOtherHost)
        assertNotEquals(baseline, sameHostOtherUser)
        assertNotEquals(baseline, schemeVariant)
        assertNotEquals(baseline, portVariant)
        assertNotEquals(baseline, basePathVariant)
        assertNotEquals(
            CatalogSnapshotKey(baseline, baseline, ContentType.SERIES),
            CatalogSnapshotKey(sameUserOtherHost, sameUserOtherHost, ContentType.SERIES),
        )
        assertNotEquals(
            HomeBootstrapKey(baseline, baseline, "primary"),
            HomeBootstrapKey(sameUserOtherHost, sameUserOtherHost, "primary"),
        )
    }

    @Test
    fun `queued old owner request must not reach its provider after account switch`() = runBlocking {
        val a = fixture.login("CODE-A", "user-a", "pass-a")
        val ownerA = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(providerA.baseUrl, a.portal.baseUrl)
        providerA.delaySeries(9301, 700)
        providerA.delaySeries(9302, 700)

        withTimeout(15_000) {
            coroutineScope {
                val first = async { metadataStore.metadata(ownerA, 9301, requirePresentation = true) }
                val second = async { metadataStore.metadata(ownerA, 9302, requirePresentation = true) }
                waitUntil { providerA.seriesRequestCount() == 2 }

                val queued = async { metadataStore.metadata(ownerA, 9303, requirePresentation = true) }
                delay(150)

                // Normal account replacement: the process registry now belongs to B.
                fixture.login("CODE-B", "user-b", "pass-b")
                waitUntil { providerB.authRequests().isNotEmpty() }

                first.await()
                second.await()
                queued.await()
            }
        }

        assertEquals(
            "a queued request captured by the old owner must not be issued after replacement",
            0,
            providerA.seriesRequests().count { it.seriesId == 9303 },
        )
    }

    @Test
    fun `canceling one waiter keeps the shared request single flight`() = runBlocking {
        val b = fixture.login("CODE-B", "user-b", "pass-b")
        val ownerB = requireNotNull(AuthenticatedSessionRegistry.currentOwner())
        assertEquals(providerB.baseUrl, b.portal.baseUrl)
        providerB.delaySeries(9400, 1_400)

        withTimeout(15_000) {
            coroutineScope {
                val first = async { metadataStore.metadata(ownerB, 9400, requirePresentation = true) }
                val second = async { metadataStore.metadata(ownerB, 9400, requirePresentation = true) }
                waitUntil { providerB.seriesRequestCount() == 1 }

                first.cancel()
                try {
                    first.await()
                } catch (_: CancellationException) {
                    // The canceled waiter is expected; the shared provider request must survive.
                }
                delay(1_100) // exceed the store's existing retry cooldown

                val third = async { metadataStore.metadata(ownerB, 9400, requirePresentation = true) }
                second.await()
                third.await()
            }
        }

        assertEquals(1, providerB.seriesRequestCount())
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) error("condition not reached")
            delay(20)
        }
    }

    private class SwitchFixture(
        private val providerA: FakeProvider,
        private val providerB: FakeProvider,
    ) {
        val context = TestAppContext()
        private val scopePreferences = FakeSharedPreferences()
        private val sessionPreferences = FakeSharedPreferences()
        private val scoped = mutableMapOf<String, FakeSharedPreferences>()
        private val scope = AccountScopeStore(scopePreferences) { name ->
            scoped.getOrPut(name) { FakeSharedPreferences() }
        }
        private val sessionStore = AccountSessionStore(sessionPreferences, scope)
        private val vault = CredentialVault(
            preferences = FakeSharedPreferences(),
            keyStore = SyntheticKeyStore(),
            base64 = SyntheticBase64,
        )
        private val resolver = object : PortalResolver() {
            override suspend fun resolve(accessCode: String): PortalConfig = when (accessCode) {
                "CODE-A" -> PortalConfig(providerA.baseUrl, PortalConfig.Source.ACCESS_CODE)
                "CODE-B" -> PortalConfig(providerB.baseUrl, PortalConfig.Source.ACCESS_CODE)
                else -> error("unexpected synthetic access code")
            }
        }
        val repository = HulkRepository(
            context = context,
            vault = vault,
            accountSessionStore = sessionStore,
            suspendDownloadOwner = {},
            portalResolver = resolver,
            client = XtreamClient(),
        )

        suspend fun login(code: String, user: String, pass: String): AuthenticatedSession =
            repository.login(Credentials(code, user, pass), remember = true)
    }

    private data class RequestRecord(
        val action: String?,
        val user: String?,
        val password: String?,
        val seriesId: Int?,
    )

    private class FakeProvider(
        private val name: String,
        private val height: Int,
    ) {
        val server = MockWebServer()
        val baseUrl: String get() = server.url("/").toString().trimEnd('/')
        private val requests = java.util.Collections.synchronizedList(mutableListOf<RequestRecord>())
        private val delays = java.util.concurrent.ConcurrentHashMap<Int, Long>()

        fun start() = server.start()

        fun shutdown() = runCatching { server.shutdown() }.let { }

        fun delaySeries(seriesId: Int, millis: Long) {
            delays[seriesId] = millis
        }

        fun authRequests(): List<RequestRecord> = synchronized(requests) {
            requests.filter { it.action == null }
        }

        fun seriesRequests(): List<RequestRecord> = synchronized(requests) {
            requests.filter { it.action == "get_series_info" }
        }

        fun seriesRequestCount(): Int = seriesRequests().size

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl
                    val action = url?.queryParameter("action")
                    val user = url?.queryParameter("username")
                    val password = url?.queryParameter("password")
                    val seriesId = url?.queryParameter("series_id")?.toIntOrNull()
                    requests += RequestRecord(action, user, password, seriesId)
                    val delayMs = seriesId?.let { delays[it] } ?: 0L
                    val body = when (action) {
                        null -> """{"user_info":{"username":"${user.orEmpty()}","status":"Active","auth":1,"exp_date":"1893456000"}}"""
                        "get_series_info" -> seriesBody()
                        "get_vod_categories", "get_series_categories", "get_live_categories" ->
                            """[{"category_id":"1","category_name":"Cat"}]"""
                        "get_vod_streams" -> """[{"stream_id":1,"name":"Movie $name","category_id":"1"}]"""
                        "get_series" -> """[{"series_id":1,"name":"Series $name","category_id":"1"}]"""
                        "get_live_streams" -> """[{"stream_id":1,"name":"Live $name","category_id":"1"}]"""
                        else -> "{}"
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                        .apply {
                            if (delayMs > 0L) setBodyDelay(delayMs, TimeUnit.MILLISECONDS)
                        }
                }
            }
        }

        private fun seriesBody(): String = """
            {
              "info":{"plot":"PLOT-$name","genre":"GENRE-$name","cover":"cover-$name.jpg"},
              "episodes":{"1":[{"id":"1","title":"E1","season":"1","episode_num":"1","info":{"video":{"height":$height}}}]}
            }
        """.trimIndent()
    }

    private class SyntheticKeyStore : CredentialKeyStore {
        private var key: SecretKey? = null
        override fun exists(alias: String): Boolean = key != null
        override fun get(alias: String): SecretKey? = key
        override fun create(alias: String): SecretKey =
            SecretKeySpec(ByteArray(32) { 7 }, "AES").also { key = it }
        override fun delete(alias: String) {
            key = null
        }
    }

    private object SyntheticBase64 : CredentialBase64Codec {
        override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        override fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
    }
}

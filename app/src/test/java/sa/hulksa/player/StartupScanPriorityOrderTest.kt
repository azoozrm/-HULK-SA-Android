package sa.hulksa.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * R23 production-order integration: the real [CatalogRequestOwner] terminal callbacks drive the
 * real [StartupScanPriorityGate], and scan admission is modelled exactly like the ViewModel's
 * automatic branch. It asserts call order, not a helper Boolean.
 */
class StartupScanPriorityOrderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val owner = Any()
    private val required = setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
    private val gate = StartupScanPriorityGate()
    private val completions = required.associateWith { CompletableDeferred<Unit>() }
    private val failTypes = mutableSetOf<ContentType>()
    private val started = mutableListOf<ContentType>()
    private val admissions = mutableListOf<NotificationScanTrigger>()
    private var pending: NotificationScanTrigger? = null

    private val session = AuthenticatedSession(
        portal = PortalConfig("http://127.0.0.1:1/", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE", "user", "pass"),
        account = AccountInfo("user", "Active", null, 1, 1, false),
    )

    private val catalogOwner = CatalogRequestOwner(
        scope = scope,
        load = { _, type ->
            synchronized(started) { started += type }
            completions.getValue(type).await()
            if (type in failTypes) throw IllegalStateException("controlled failure")
            CatalogLoadResult(
                full = Catalog(categories = emptyList(), items = emptyList()),
                home = Catalog(categories = emptyList(), items = emptyList()),
            )
        },
        hasLoaded = { false },
        isSessionCurrent = { true },
        onLoadingChanged = {},
        onLoaded = { type, _, _ -> settle(type) },
        onFailure = { type, _, _ -> settle(type) },
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun begin() {
        gate.begin(owner, required)
    }

    private fun requestAll() {
        required.forEach { catalogOwner.request(session, it, force = false) }
    }

    /** Same decision the ViewModel applies at the top of scanSubscribedSeries. */
    private fun requestScan(trigger: NotificationScanTrigger) {
        if (trigger.isAutomaticStartupScan && gate.isHeld(owner)) {
            pending = coalesceNotificationScanTrigger(pending, trigger)
            return
        }
        admissions += trigger
    }

    private fun settle(type: ContentType) {
        if (gate.settle(owner, type)) {
            pending?.let { admissions += it }
            pending = null
        }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    @Test
    fun `automatic scan waits for every type and drains exactly once`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.APP_START)
        requestAll()

        completions.getValue(ContentType.MOVIE).complete(Unit)
        awaitCondition { synchronized(started) { ContentType.SERIES in started } }
        assertTrue("scan admitted before SERIES settled", admissions.isEmpty())

        completions.getValue(ContentType.SERIES).complete(Unit)
        awaitCondition { synchronized(started) { ContentType.LIVE in started } }
        assertTrue("scan admitted before LIVE settled", admissions.isEmpty())

        completions.getValue(ContentType.LIVE).complete(Unit)
        awaitCondition { admissions.isNotEmpty() }
        assertEquals(listOf(NotificationScanTrigger.APP_START), admissions)
    }

    @Test
    fun `partial failure releases the held automatic scan`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.APP_RESUME)
        requestAll()
        failTypes += ContentType.SERIES

        completions.getValue(ContentType.MOVIE).complete(Unit)
        awaitCondition { synchronized(started) { ContentType.SERIES in started } }
        assertTrue(admissions.isEmpty())
        completions.getValue(ContentType.SERIES).complete(Unit)
        awaitCondition { synchronized(started) { ContentType.LIVE in started } }
        assertTrue(admissions.isEmpty())
        completions.getValue(ContentType.LIVE).complete(Unit)
        awaitCondition { admissions.isNotEmpty() }
        assertEquals(listOf(NotificationScanTrigger.APP_RESUME), admissions)
    }

    @Test
    fun `all failures release instead of disabling notifications`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.APP_START)
        requestAll()
        failTypes += required

        completions.values.forEach { it.complete(Unit) }
        awaitCondition { admissions.isNotEmpty() }
        assertEquals(1, admissions.size)
        assertTrue(!gate.isHeldForAnyOwner())
    }

    @Test
    fun `duplicate startup triggers coalesce into one drain`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.APP_START)
        requestScan(NotificationScanTrigger.APP_RESUME)
        assertEquals(NotificationScanTrigger.APP_START, pending)
        requestAll()

        completions.values.forEach { it.complete(Unit) }
        awaitCondition { admissions.isNotEmpty() }
        assertEquals(listOf(NotificationScanTrigger.APP_START), admissions)
    }

    @Test
    fun `fresh no-fetch decisions settle types without a request`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.LIBRARY_REFRESH)
        catalogOwner.request(session, ContentType.MOVIE, force = false)
        settle(ContentType.SERIES)
        settle(ContentType.LIVE)
        assertTrue(admissions.isEmpty())

        completions.getValue(ContentType.MOVIE).complete(Unit)
        awaitCondition { admissions.isNotEmpty() }
        assertEquals(listOf(NotificationScanTrigger.LIBRARY_REFRESH), admissions)
    }

    @Test
    fun `manual triggers bypass the gate`() {
        begin()
        requestScan(NotificationScanTrigger.MANUAL_REFRESH)
        requestScan(NotificationScanTrigger.NOTIFICATION_CENTER)
        assertEquals(
            listOf(NotificationScanTrigger.MANUAL_REFRESH, NotificationScanTrigger.NOTIFICATION_CENTER),
            admissions,
        )
    }

    @Test
    fun `a stale owner settlement cannot release the current round`() {
        val stale = Any()
        val current = Any()
        gate.begin(stale, required)
        gate.begin(current, required)
        gate.settle(stale, ContentType.MOVIE)
        gate.settle(stale, ContentType.SERIES)
        gate.settle(stale, ContentType.LIVE)
        assertTrue(gate.isHeld(current))
    }

    @Test
    fun `cancelled round does not admit stale automatic work`() = runBlocking {
        begin()
        requestScan(NotificationScanTrigger.APP_START)
        requestAll()
        gate.invalidate()
        completions.values.forEach { it.complete(Unit) }
        delay(100)
        assertTrue(admissions.isEmpty())
    }
}

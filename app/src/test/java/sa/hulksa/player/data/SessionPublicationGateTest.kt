package sa.hulksa.player.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class SessionPublicationGateTest {
    @Before
    fun setUp() {
        AuthenticatedSessionRegistry.clear()
    }

    @After
    fun tearDown() {
        AuthenticatedSessionRegistry.clear()
    }

    @Test
    fun `main publication is rejected when expiry passes during suspended work`() = runBlocking {
        var nowSeconds = 2_000L
        val session = session(expiry = 2_000L)
        val owner = owner(session, sessionId = "session-a")
        setCurrent(owner)
        var mainPublished = false
        val gate = SessionPublicationGate(nowEpochSeconds = { nowSeconds })
        val workReleased = CompletableDeferred<Unit>()

        val publication = async {
            workReleased.await()
            gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
                attemptCurrent = { true },
            ) {
                mainPublished = true
            }
        }

        nowSeconds = 2_001L
        workReleased.complete(Unit)
        val result = publication.await()

        assertEquals(SessionPublicationResult.Expired, result)
        assertFalse("no transient MAIN publication", mainPublished)
    }

    @Test
    fun `refresh success is rejected when expiry passes while refresh publication is suspended`() = runBlocking {
        var nowSeconds = 2_000L
        val session = session(expiry = 2_000L)
        val owner = owner(session, sessionId = "session-a")
        setCurrent(owner)
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { nowSeconds })
        val workReleased = CompletableDeferred<Unit>()

        val publication = async {
            workReleased.await()
            gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
            ) {
                successNotification = true
            }
        }

        nowSeconds = 2_001L
        workReleased.complete(Unit)
        val result = publication.await()

        assertEquals(SessionPublicationResult.Expired, result)
        assertFalse("no successful-refresh notification", successNotification)
    }

    @Test
    fun `publication serializes ownership against a concurrent replacement`() = runBlocking {
        val session = session(expiry = 9_000L)
        val owner = owner(session, sessionId = "session-a")
        setCurrent(owner)
        val publishEntered = CountDownLatch(1)
        val releasePublish = CountDownLatch(1)
        val replacementDone = CountDownLatch(1)
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })
        var published = false

        val publication = async(Dispatchers.Default) {
            gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
            ) {
                published = true
                publishEntered.countDown()
                releasePublish.await(5, TimeUnit.SECONDS)
                Unit
            }
        }
        assertTrue(publishEntered.await(5, TimeUnit.SECONDS))

        val newer = owner(session(expiry = 9_000L, username = "newer"), sessionId = "session-b")
        val replacement = thread {
            setCurrent(newer)
            replacementDone.countDown()
        }
        assertFalse(
            "a newer owner must not replace ownership during publication",
            replacementDone.await(300, TimeUnit.MILLISECONDS),
        )

        releasePublish.countDown()
        assertEquals(SessionPublicationResult.Published(Unit), publication.await())
        assertTrue(published)
        assertTrue(replacementDone.await(5, TimeUnit.SECONDS))
        replacement.join(5_000L)
        assertEquals(newer.sessionId, AuthenticatedSessionRegistry.currentOwner()?.sessionId)
    }

    @Test
    fun `expired cleanup does not reset visible state after a newer publication`() = runBlocking {
        var epoch = 0L
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var visibleStateReset = false
        val cleanupGate = SessionOwnershipCleanupGate(
            ownershipEpoch = { epoch },
            logoutIfOwnedBy = {
                cleanupEntered.complete(Unit)
                releaseCleanup.await()
                true
            },
        )
        val expectedOwner = owner(session(expiry = 2_000L), sessionId = "session-a")

        val cleanup = async(Dispatchers.Default) {
            cleanupGate.clearIfOwnerStillCurrent(expectedOwner) {
                visibleStateReset = true
                epoch += 1
            }
        }
        cleanupEntered.await()
        epoch += 1 // a newer session publishes while cleanup is delayed
        releaseCleanup.complete(Unit)

        assertFalse(cleanup.await())
        assertFalse("B's visible state must not be reset", visibleStateReset)
    }

    @Test
    fun `current expired cleanup resets visible state once`() = runBlocking {
        var epoch = 0L
        var resets = 0
        val cleanupGate = SessionOwnershipCleanupGate(
            ownershipEpoch = { epoch },
            logoutIfOwnedBy = { true },
        )
        val expectedOwner = owner(session(expiry = 2_000L), sessionId = "session-a")

        assertTrue(
            cleanupGate.clearIfOwnerStillCurrent(expectedOwner) {
                resets += 1
                epoch += 1
            },
        )

        assertEquals(1, resets)
        assertEquals(1L, epoch)
    }

    @Test
    fun `refresh success is rejected when a newer owner replaced the session`() {
        val session = session(expiry = 9_000L)
        val expected = owner(session, sessionId = "session-a")
        val newer = owner(session(expiry = 9_000L, username = "newer"), sessionId = "session-b")
        setCurrent(newer)
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = expected,
        ) {
            successNotification = true
        }

        assertEquals(SessionPublicationResult.Stale, result)
        assertFalse("no successful-refresh notification", successNotification)
    }

    @Test
    fun `refresh success is rejected when the current owner is already expired`() {
        val session = session(expiry = 2_000L)
        val owner = owner(session, sessionId = "session-a")
        setCurrent(owner)
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = owner,
        ) {
            successNotification = true
        }

        assertEquals(SessionPublicationResult.Expired, result)
        assertFalse("no successful-refresh notification", successNotification)
    }

    @Test
    fun `valid future and unlimited sessions still publish`() {
        listOf(9_000L, null).forEachIndexed { index, expiry ->
            val session = session(expiry = expiry, username = "user-$index")
            val owner = owner(session, sessionId = "session-$index")
            setCurrent(owner)
            val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })
            var notifications = 0

            val result = gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
            ) {
                notifications += 1
                "published-$index"
            }

            assertEquals(
                SessionPublicationResult.Published("published-$index"),
                result,
            )
            assertEquals(1, notifications)
        }
    }

    @Test
    fun `invalidated attempt never publishes even while owner is unchanged`() {
        val session = session(expiry = 9_000L)
        val owner = owner(session, sessionId = "session-a")
        setCurrent(owner)
        var published = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = owner,
            attemptCurrent = { false },
        ) {
            published = true
        }

        assertEquals(SessionPublicationResult.Stale, result)
        assertFalse(published)
    }

    private fun setCurrent(owner: AuthenticatedSessionOwner) {
        AuthenticatedSessionRegistry.update(
            session = owner.session,
            metadata = AccountSessionMetadata(
                accountId = owner.accountId,
                username = owner.session.credentials.username,
                portalBaseUrl = owner.session.portal.baseUrl,
                authenticatedAtEpochMs = 1_000L,
                expiresAtEpochSeconds = owner.session.account.expiresAtEpochSeconds,
                status = owner.session.account.status,
                installationId = "installation-a",
                sessionId = owner.sessionId,
            ),
        )
    }

    private fun owner(session: AuthenticatedSession, sessionId: String): AuthenticatedSessionOwner =
        AuthenticatedSessionOwner(
            accountId = "account-a",
            providerId = stableAccountId(session.portal.baseUrl, session.credentials.username),
            sessionId = sessionId,
            session = session,
        )

    private fun session(expiry: Long?, username: String = "alpha"): AuthenticatedSession =
        AuthenticatedSession(
            portal = PortalConfig("https://provider.example.test", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("CODE-$username", username, "secret-$username"),
            account = AccountInfo(username, "Active", expiry, 0, 1, false),
        )
}

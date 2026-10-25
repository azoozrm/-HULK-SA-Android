package sa.hulksa.player.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class SessionPublicationGateTest {
    @Test
    fun `main publication is rejected when expiry passes during suspended work`() = runBlocking {
        var nowSeconds = 2_000L
        val session = session(expiry = 2_000L)
        val owner = owner(session, sessionId = "session-a")
        var mainPublished = false
        val gate = SessionPublicationGate(nowEpochSeconds = { nowSeconds })
        val workReleased = CompletableDeferred<Unit>()

        val publication = async {
            workReleased.await()
            gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
                currentOwner = { owner },
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
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { nowSeconds })
        val workReleased = CompletableDeferred<Unit>()

        val publication = async {
            workReleased.await()
            gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
                currentOwner = { owner },
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
    fun `refresh success is rejected when a newer owner replaced the session`() {
        val session = session(expiry = 9_000L)
        val expected = owner(session, sessionId = "session-a")
        val newer = owner(session(expiry = 9_000L, username = "newer"), sessionId = "session-b")
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = expected,
            currentOwner = { newer },
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
        var successNotification = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = owner,
            currentOwner = { owner },
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
            val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })
            var notifications = 0

            val result = gate.publishIfCurrent(
                authenticated = session,
                expectedOwner = owner,
                currentOwner = { owner },
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
        var published = false
        val gate = SessionPublicationGate(nowEpochSeconds = { 2_000L })

        val result = gate.publishIfCurrent(
            authenticated = session,
            expectedOwner = owner,
            currentOwner = { owner },
            attemptCurrent = { false },
        ) {
            published = true
        }

        assertEquals(SessionPublicationResult.Stale, result)
        assertFalse(published)
    }

    private fun owner(session: AuthenticatedSession, sessionId: String): AuthenticatedSessionOwner =
        AuthenticatedSessionOwner(
            accountId = "account-a",
            providerId = "provider-a",
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

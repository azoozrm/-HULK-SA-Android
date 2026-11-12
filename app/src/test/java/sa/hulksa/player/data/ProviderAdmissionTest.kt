package sa.hulksa.player.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

/**
 * Deterministic R30 admission policy checks. The fake clock makes every wait exact; no wall-clock
 * sleep, provider, device or diagnostics backend participates.
 */
class ProviderAdmissionTest {
    private class FakeClock(
        startNs: Long = System.currentTimeMillis() * 1_000_000L,
    ) {
        private val nanos = AtomicLong(startNs)
        fun nowNs(): Long = nanos.get()
        fun nowMs(): Long = nowNs() / 1_000_000L
        fun advanceNs(delta: Long) {
            nanos.addAndGet(delta)
        }
        suspend fun sleep(delta: Long) {
            nanos.addAndGet(delta)
            yield()
        }
    }

    private fun admission(
        clock: FakeClock,
        current: () -> AuthenticatedSessionOwner? = { null },
    ) = ProviderAdmission(
        nowNs = clock::nowNs,
        nowWallMs = clock::nowMs,
        sleepNs = clock::sleep,
        ownerIsCurrent = { it === current() },
    )

    private fun session(
        host: String = "https://provider-a.example:8443/live",
        user: String = "subscriber-a",
    ) = AuthenticatedSession(
        portal = PortalConfig(host, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("CODE-$user", user, "pass-$user"),
        account = AccountInfo(user, "Active", null, 1, 1, false),
    )

    @Test
    fun `new dispatch after a 429 waits for the full retry-after deadline`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val capturedAt = clock.nowNs()
        assertTrue(policy.onRateLimited(session, null, "10", clock.nowMs()))

        // R29 counterexample: the next observations are 374 ms and 614 ms after the response.
        clock.advanceNs(374_000_000)
        policy.awaitAdmission(session, null)
        val firstGrant = clock.nowNs()
        clock.advanceNs(240_000_000)
        policy.awaitAdmission(session, null)
        val secondGrant = clock.nowNs()

        assertTrue(firstGrant - capturedAt >= 10_000_000_000L)
        assertTrue(secondGrant - firstGrant >= 250_000_000L)
    }

    @Test
    fun `http date retry-after is converted to a monotonic deadline`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val responseAt = clock.nowMs() / 1_000L * 1_000L
        val date = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(responseAt + 8_000L),
            ZoneOffset.UTC,
        ).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(8_000L, ProviderRetryAfter.delayMillis(date, responseAt))
        val capturedAt = clock.nowNs()
        assertTrue(policy.onRateLimited(session, null, date, responseAt))

        policy.awaitAdmission(session, null)

        assertTrue(clock.nowNs() - capturedAt >= 8_000_000_000L)
    }

    @Test
    fun `missing invalid negative and overflow retry-after invent no delay`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val now = clock.nowMs()
        listOf(null, "", "abc", "-1", "86401", "99999999999999999999").forEach { value ->
            assertNull(ProviderRetryAfter.delayMillis(value, now))
            assertTrue(!policy.onRateLimited(session, null, value, now))
        }
        assertNull(policy.cooldownDeadlineNs(session))

        val before = clock.nowNs()
        policy.awaitAdmission(session, null)
        assertEquals(before, clock.nowNs())
    }

    @Test
    fun `concurrent responses extend the deadline and never shorten it`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val start = clock.nowNs()
        assertTrue(policy.onRateLimited(session, null, "5", clock.nowMs()))
        clock.advanceNs(2_000_000_000L)
        assertTrue(policy.onRateLimited(session, null, "12", clock.nowMs()))
        val extended = policy.cooldownDeadlineNs(session)
        assertTrue(policy.onRateLimited(session, null, "1", clock.nowMs()))

        assertEquals(extended, policy.cooldownDeadlineNs(session))
        policy.awaitAdmission(session, null)
        assertTrue(clock.nowNs() - start >= 14_000_000_000L)
    }

    @Test
    fun `provider and account are isolated`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val a = session()
        val otherAccount = session(user = "subscriber-b")
        val otherProvider = session(host = "https://provider-b.example/live")
        assertTrue(policy.onRateLimited(a, null, "10", clock.nowMs()))

        val before = clock.nowNs()
        policy.awaitAdmission(otherAccount, null)
        policy.awaitAdmission(otherProvider, null)
        assertEquals(before, clock.nowNs())
    }

    @Test
    fun `stale owner response is ignored and stale waiter is cancelled`() = runBlocking {
        val clock = FakeClock()
        val session = session()
        val stale = AuthenticatedSessionOwner("a", "p", "s1", session)
        val current = AuthenticatedSessionOwner("a", "p", "s2", session)
        var active: AuthenticatedSessionOwner? = current
        val policy = admission(clock) { active }

        assertTrue(!policy.onRateLimited(session, stale, "10", clock.nowMs()))
        assertNull(policy.cooldownDeadlineNs(session))

        active = stale
        assertTrue(policy.onRateLimited(session, stale, "10", clock.nowMs()))
        active = current

        assertThrows(CancellationException::class.java) {
            runBlocking { policy.awaitAdmission(session, stale) }
        }
        Unit
    }

    @Test
    fun `release after a cooldown is spaced instead of a burst`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val start = clock.nowNs()
        assertTrue(policy.onRateLimited(session, null, "1", clock.nowMs()))
        clock.advanceNs(1_000_000_000L)

        val grants = Collections.synchronizedList(mutableListOf<Long>())
        (1..3).map {
            async(Dispatchers.Default) {
                policy.awaitAdmission(session, null)
                grants += clock.nowNs()
            }
        }.awaitAll()

        val sorted = grants.sorted()
        assertEquals(3, sorted.size)
        assertTrue(sorted[0] >= start + 1_000_000_000L)
        assertTrue(sorted[1] - sorted[0] >= 250_000_000L)
        assertTrue(sorted[2] - sorted[1] >= 250_000_000L)
    }

    @Test
    fun `session replaced before request build is stale and cannot update`() {
        val current = session(user = "current-user")
        val stale = session(user = "old-user")
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
        try {
            val staleBinding = providerAdmissionBinding(stale)
            assertTrue(staleBinding.stale)
            assertNull(staleBinding.owner)

            val currentBinding = providerAdmissionBinding(current)
            assertFalse(currentBinding.stale)
            assertTrue(currentBinding.owner != null)
        } finally {
            AuthenticatedSessionRegistry.clear()
        }
    }

    @Test
    fun `without a 429 admission never waits`() = runBlocking {
        val clock = FakeClock()
        val policy = admission(clock)
        val session = session()
        val before = clock.nowNs()
        policy.awaitAdmission(session, null)
        policy.awaitAdmission(session, null)
        assertEquals(before, clock.nowNs())
    }
}

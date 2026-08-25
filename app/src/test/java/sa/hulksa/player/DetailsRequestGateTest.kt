package sa.hulksa.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentType

class DetailsRequestGateTest {
    private fun key(
        type: ContentType,
        contentId: Int,
        accountId: String = "account-a",
        profileId: String = "profile-a",
    ) = DetailsRequestGate.Key(
        type = type,
        contentId = contentId,
        accountId = accountId,
        profileId = profileId,
    )

    @Test
    fun `opening a new item makes the previous request stale`() {
        val gate = DetailsRequestGate()
        val first = gate.begin(key(ContentType.MOVIE, contentId = 10))
        val second = gate.begin(key(ContentType.MOVIE, contentId = 11))

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `explicit navigation invalidation rejects a late completion`() {
        val gate = DetailsRequestGate()
        val request = gate.begin(key(ContentType.SERIES, contentId = 22))

        gate.invalidate()

        assertFalse(gate.isCurrent(request))
    }

    @Test
    fun `same content restarted still owns a new generation`() {
        val gate = DetailsRequestGate()
        val requestKey = key(ContentType.SERIES, contentId = 22)
        val first = gate.begin(requestKey)
        val restarted = gate.begin(requestKey)

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(restarted))
    }

    @Test
    fun `profile identity is part of request ownership`() {
        val gate = DetailsRequestGate()
        val request = gate.begin(key(ContentType.MOVIE, contentId = 10))

        assertFalse(
            gate.isCurrentForContext(
                token = request,
                accountId = "account-a",
                profileId = "profile-b",
            ),
        )
        assertTrue(
            gate.isCurrentForContext(
                token = request,
                accountId = "account-a",
                profileId = "profile-a",
            ),
        )
    }

    @Test
    fun `reauthenticated session for same logical account keeps request current`() {
        val gate = DetailsRequestGate()
        val logicalAccountFromSessionA = "account-a"
        val request = gate.begin(
            key(
                type = ContentType.MOVIE,
                contentId = 10,
                accountId = logicalAccountFromSessionA,
            ),
        )
        val logicalAccountFromSessionB = "account-a"

        assertTrue(
            gate.isCurrentForContext(
                token = request,
                accountId = logicalAccountFromSessionB,
                profileId = "profile-a",
            ),
        )
    }

    @Test
    fun `actual account replacement makes request stale`() {
        val gate = DetailsRequestGate()
        val request = gate.begin(
            key(ContentType.SERIES, contentId = 22, accountId = "account-a"),
        )

        assertFalse(
            gate.isCurrentForContext(
                token = request,
                accountId = "account-b",
                profileId = "profile-a",
            ),
        )
    }

    @Test
    fun `logout makes request context stale even before late completion`() {
        val gate = DetailsRequestGate()
        val request = gate.begin(key(ContentType.MOVIE, contentId = 10))

        assertFalse(
            gate.isCurrentForContext(
                token = request,
                accountId = null,
                profileId = "profile-a",
            ),
        )
    }
}

package sa.hulksa.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentType

class DetailsRequestGateTest {
    @Test
    fun `opening a new item makes the previous request stale`() {
        val gate = DetailsRequestGate()
        val first = gate.begin(
            DetailsRequestGate.Key(ContentType.MOVIE, contentId = 10, profileId = "profile-a"),
        )
        val second = gate.begin(
            DetailsRequestGate.Key(ContentType.MOVIE, contentId = 11, profileId = "profile-a"),
        )

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `explicit navigation invalidation rejects a late completion`() {
        val gate = DetailsRequestGate()
        val request = gate.begin(
            DetailsRequestGate.Key(ContentType.SERIES, contentId = 22, profileId = "profile-a"),
        )

        gate.invalidate()

        assertFalse(gate.isCurrent(request))
    }

    @Test
    fun `same content restarted still owns a new generation`() {
        val gate = DetailsRequestGate()
        val key = DetailsRequestGate.Key(ContentType.SERIES, contentId = 22, profileId = "profile-a")
        val first = gate.begin(key)
        val restarted = gate.begin(key)

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(restarted))
    }

    @Test
    fun `profile identity is part of request ownership`() {
        val gate = DetailsRequestGate()
        val oldProfile = gate.begin(
            DetailsRequestGate.Key(ContentType.MOVIE, contentId = 10, profileId = "profile-a"),
        )
        val newProfile = gate.begin(
            DetailsRequestGate.Key(ContentType.MOVIE, contentId = 10, profileId = "profile-b"),
        )

        assertFalse(gate.isCurrent(oldProfile))
        assertTrue(gate.isCurrent(newProfile))
    }
}

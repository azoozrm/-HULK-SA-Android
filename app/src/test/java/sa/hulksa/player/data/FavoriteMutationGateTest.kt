package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteMutationGateTest {
    @Test
    fun `same item duplicate is rejected while another legitimate item is admitted`() {
        var now = 1_000L
        val gate = FavoriteMutationGate(nowMs = { now })

        val first = gate.admit("MOVIE:1")
        assertNotNull(first)
        assertNull(gate.admit("MOVIE:1"))
        assertNotNull("a different card remains a legitimate action", gate.admit("MOVIE:2"))

        gate.finish(checkNotNull(first))
        now += 699L
        assertNull(gate.admit("MOVIE:1"))

        now += 1L
        assertNotNull(gate.admit("MOVIE:1"))
    }

    @Test
    fun `finishing one action allows a later legitimate repeat after the throttle window`() {
        var now = 5_000L
        val gate = FavoriteMutationGate(nowMs = { now })
        val first = checkNotNull(gate.admit("SERIES:7"))
        gate.finish(first)

        now += FAVORITE_TOGGLE_THROTTLE_MS
        val second = checkNotNull(gate.admit("SERIES:7"))
        gate.finish(second)

        assertEquals("SERIES:7", second.key)
    }
}

package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentType

class StartupScanPriorityGateTest {
    private val owner = Any()

    @Test
    fun `automatic triggers are gated and manual triggers are not`() {
        assertTrue(NotificationScanTrigger.APP_START.isAutomaticStartupScan)
        assertTrue(NotificationScanTrigger.APP_RESUME.isAutomaticStartupScan)
        assertTrue(NotificationScanTrigger.PROFILE_SWITCH.isAutomaticStartupScan)
        assertTrue(NotificationScanTrigger.LIBRARY_REFRESH.isAutomaticStartupScan)
        assertFalse(NotificationScanTrigger.MANUAL_REFRESH.isAutomaticStartupScan)
        assertFalse(NotificationScanTrigger.NOTIFICATION_CENTER.isAutomaticStartupScan)
        assertFalse(NotificationScanTrigger.MASTER_REENABLE.isAutomaticStartupScan)
    }

    @Test
    fun `release happens exactly once when every required type settles`() {
        val gate = StartupScanPriorityGate()
        assertTrue(gate.begin(owner, setOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)))

        assertFalse(gate.settle(owner, ContentType.MOVIE))
        assertFalse(gate.settle(owner, ContentType.SERIES))
        assertTrue(gate.settle(owner, ContentType.LIVE))
        assertFalse(gate.settle(owner, ContentType.LIVE))
        assertFalse(gate.isHeld(owner))
    }

    @Test
    fun `a stale owner can never release or join the current owner round`() {
        val gate = StartupScanPriorityGate()
        val stale = Any()
        val current = Any()
        assertTrue(gate.begin(stale, setOf(ContentType.MOVIE)))
        gate.invalidate(stale)
        assertTrue(gate.begin(current, setOf(ContentType.MOVIE)))

        assertFalse(gate.settle(stale, ContentType.MOVIE))
        assertTrue(gate.isHeld(current))
        assertTrue(gate.settle(current, ContentType.MOVIE))
    }

    @Test
    fun `no-fetch decisions settle types without any request`() {
        val gate = StartupScanPriorityGate()
        assertTrue(gate.begin(owner, setOf(ContentType.MOVIE, ContentType.SERIES)))
        assertFalse(gate.settle(owner, ContentType.MOVIE))
        assertTrue(gate.settle(owner, ContentType.SERIES))
        assertFalse(gate.isHeldForAnyOwner())
    }

    @Test
    fun `invalidate drops held work without releasing it`() {
        val gate = StartupScanPriorityGate()
        assertTrue(gate.begin(owner, setOf(ContentType.MOVIE)))
        gate.invalidate()
        assertFalse(gate.isHeldForAnyOwner())
        assertFalse(gate.settle(owner, ContentType.MOVIE))
        assertEquals(emptySet<ContentType>(), gate.pendingTypes(owner))
    }
}

package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.data.OperationsUpdateDecision

class HomeMessagePresentationStateTest {
    @Test
    fun `ownership publishes and clears exact identities`() {
        val state = HomeMessagePresentationState()
        state.publish(
            announcementId = "message-a",
            optionalUpdateVersionCode = 90,
            persistentAnnouncementId = "message-a",
        )
        assertEquals("message-a", state.ownedAnnouncementId)
        assertEquals(90, state.ownedOptionalUpdateVersionCode)
        assertEquals("message-a", state.ownedPersistentAnnouncementId)

        state.clear()
        assertNull(state.ownedAnnouncementId)
        assertNull(state.ownedOptionalUpdateVersionCode)
        assertNull(state.ownedPersistentAnnouncementId)
    }

    @Test
    fun `automatic announcement is suppressed while home owns the same id`() {
        assertNull(
            nextAutoAnnouncementId(
                currentAutoId = null,
                eligibleAnnouncementId = "message-a",
                homeOwnsAnnouncement = true,
            ),
        )
        assertEquals(
            "message-a",
            nextAutoAnnouncementId(
                currentAutoId = null,
                eligibleAnnouncementId = "message-a",
                homeOwnsAnnouncement = false,
            ),
        )
    }

    @Test
    fun `latch never retracts an already presented announcement`() {
        assertEquals(
            "message-a",
            nextAutoAnnouncementId(
                currentAutoId = "message-a",
                eligibleAnnouncementId = "message-a",
                homeOwnsAnnouncement = true,
            ),
        )
        assertEquals(
            "message-b",
            nextAutoAnnouncementId(
                currentAutoId = "message-a",
                eligibleAnnouncementId = "message-b",
                homeOwnsAnnouncement = false,
            ),
        )
        assertNull(
            nextAutoAnnouncementId(
                currentAutoId = "message-a",
                eligibleAnnouncementId = null,
                homeOwnsAnnouncement = false,
            ),
        )
    }

    @Test
    fun `optional update latch respects decision and ownership`() {
        assertNull(
            nextAutoUpdateVersionCode(
                currentAutoVersionCode = null,
                updateDecision = OperationsUpdateDecision.OPTIONAL,
                optionalVersionCode = 90,
                homeOwnsOptionalUpdate = true,
            ),
        )
        assertEquals(
            90,
            nextAutoUpdateVersionCode(
                currentAutoVersionCode = null,
                updateDecision = OperationsUpdateDecision.OPTIONAL,
                optionalVersionCode = 90,
                homeOwnsOptionalUpdate = false,
            ),
        )
        assertEquals(
            90,
            nextAutoUpdateVersionCode(
                currentAutoVersionCode = 90,
                updateDecision = OperationsUpdateDecision.OPTIONAL,
                optionalVersionCode = 90,
                homeOwnsOptionalUpdate = true,
            ),
        )
        assertNull(
            nextAutoUpdateVersionCode(
                currentAutoVersionCode = 90,
                updateDecision = OperationsUpdateDecision.NONE,
                optionalVersionCode = 90,
                homeOwnsOptionalUpdate = false,
            ),
        )
    }
}

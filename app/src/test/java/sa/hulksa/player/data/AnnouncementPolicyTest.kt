package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPolicyTest {
    private val now = 1_770_000_000L

    @Test
    fun enabledAnnouncementIsEligible() {
        assertEquals(listOf("MSG-001"), eligible(listOf(announcement())).map(OperationsAnnouncement::id))
    }

    @Test
    fun disabledAnnouncementIsExcluded() {
        assertTrue(eligible(listOf(announcement(enabled = false))).isEmpty())
    }

    @Test
    fun scheduledAnnouncementDoesNotShowEarly() {
        assertTrue(eligible(listOf(announcement(startsAt = now + 60L))).isEmpty())
    }

    @Test
    fun expiredAnnouncementIsExcluded() {
        assertTrue(eligible(listOf(announcement(endsAt = now))).isEmpty())
    }

    @Test
    fun tvTargetOnlyShowsOnTv() {
        val message = announcement(target = OperationsAnnouncementTarget.TV)

        assertEquals(1, eligible(listOf(message), isTv = true).size)
        assertTrue(eligible(listOf(message), isTv = false).isEmpty())
    }

    @Test
    fun mobileTargetOnlyShowsOnMobile() {
        val message = announcement(target = OperationsAnnouncementTarget.MOBILE)

        assertEquals(1, eligible(listOf(message), isTv = false).size)
        assertTrue(eligible(listOf(message), isTv = true).isEmpty())
    }

    @Test
    fun acknowledgedShowOnceMessageDoesNotRepeat() {
        assertTrue(
            eligible(
                announcements = listOf(announcement(showOnce = true)),
                acknowledged = setOf("MSG-001"),
            ).isEmpty(),
        )
    }

    @Test
    fun duplicateMessageIdIsCollapsed() {
        assertEquals(1, eligible(listOf(announcement(), announcement())).size)
    }

    private fun eligible(
        announcements: List<OperationsAnnouncement>,
        isTv: Boolean = false,
        acknowledged: Set<String> = emptySet(),
    ) = eligibleOperationsAnnouncements(
        announcements = announcements,
        currentVersionCode = 64,
        isTv = isTv,
        nowEpochSeconds = now,
        acknowledgedMessageIds = acknowledged,
        presentedMessageIds = emptySet(),
    )

    private fun announcement(
        enabled: Boolean = true,
        target: OperationsAnnouncementTarget = OperationsAnnouncementTarget.ALL,
        showOnce: Boolean = true,
        startsAt: Long = now - 60L,
        endsAt: Long? = now + 60L,
    ) = OperationsAnnouncement(
        id = "MSG-001",
        enabled = enabled,
        title = "رسالة",
        message = "نص الرسالة",
        severity = OperationsAnnouncementSeverity.INFO,
        target = target,
        showOnce = showOnce,
        persistent = false,
        minimumVersionCode = null,
        maximumVersionCode = null,
        startsAtEpochSeconds = startsAt,
        endsAtEpochSeconds = endsAt,
    )
}

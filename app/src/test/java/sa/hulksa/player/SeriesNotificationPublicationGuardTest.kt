package sa.hulksa.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesNotificationPublicationGuardTest {
    private fun allowed(
        sameAuthenticatedSession: Boolean = true,
        detailsGenerationCurrent: Boolean = true,
        activeAccountId: String? = "account-a",
        activeProfileId: String = "profile-a",
        screen: HulkScreen = HulkScreen.SERIES,
        selectedSeriesId: Int? = 42,
    ): Boolean = seriesNotificationPublicationAllowed(
        sameAuthenticatedSession = sameAuthenticatedSession,
        detailsGenerationCurrent = detailsGenerationCurrent,
        expectedAccountId = "account-a",
        activeAccountId = activeAccountId,
        expectedProfileId = "profile-a",
        activeProfileId = activeProfileId,
        screen = screen,
        expectedSeriesId = 42,
        selectedSeriesId = selectedSeriesId,
    )

    @Test
    fun exactOwnerAndDetailsGenerationMayPublish() {
        assertTrue(allowed())
    }

    @Test
    fun logoutLoginRejectsOldCompletionEvenWhenNumericSeriesIdMatches() {
        assertFalse(allowed(sameAuthenticatedSession = false))
    }

    @Test
    fun backAndReopenSameSeriesRejectsPreviousDetailsGeneration() {
        assertFalse(allowed(detailsGenerationCurrent = false))
    }

    @Test
    fun accountProfileOrNavigationReplacementRejectsPublication() {
        assertFalse(allowed(activeAccountId = "account-b"))
        assertFalse(allowed(activeProfileId = "profile-b"))
        assertFalse(allowed(screen = HulkScreen.MAIN))
        assertFalse(allowed(selectedSeriesId = 99))
    }
}

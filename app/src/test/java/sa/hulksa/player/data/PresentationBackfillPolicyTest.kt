package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The movie and series stores consume the same presentation-backfill policy: only a successful
 * validated payload may settle the attempt, so a failed backfill on a complete legacy cache
 * stays eligible for a later natural retry after the existing cooldown, and a legacy marker alone
 * never settles.
 */
class PresentationBackfillPolicyTest {
    @Test
    fun `failed fetch never settles and keeps the backfill eligible`() {
        val settled = presentationSettlesAfterFetch(fetchSucceeded = false)
        assertFalse(settled)
        assertTrue(
            presentationBackfillShouldRun(
                technicalComplete = true,
                requirePresentation = true,
                hasPresentation = false,
                presentationSettled = settled,
            ),
        )
        assertFalse(MovieCardTechnicalMetadata().succeeded)
        assertFalse(SeriesCardTechnicalMetadata().succeeded)
    }

    @Test
    fun `successful payload settles even when optional fields are absent`() {
        val settled = presentationSettlesAfterFetch(fetchSucceeded = true)
        assertTrue(settled)
        assertFalse(
            presentationBackfillShouldRun(
                technicalComplete = true,
                requirePresentation = true,
                hasPresentation = false,
                presentationSettled = settled,
            ),
        )
    }

    @Test
    fun `complete presentation and non-hero callers never trigger the backfill`() {
        assertFalse(
            presentationBackfillShouldRun(
                technicalComplete = true,
                requirePresentation = true,
                hasPresentation = true,
                presentationSettled = false,
            ),
        )
        assertFalse(
            presentationBackfillShouldRun(
                technicalComplete = true,
                requirePresentation = false,
                hasPresentation = false,
                presentationSettled = false,
            ),
        )
        assertFalse(
            presentationBackfillShouldRun(
                technicalComplete = false,
                requirePresentation = true,
                hasPresentation = false,
                presentationSettled = false,
            ),
        )
    }
}

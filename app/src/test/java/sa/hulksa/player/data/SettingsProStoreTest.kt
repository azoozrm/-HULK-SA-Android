package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.ContentType

class SettingsProStoreTest {
    @Test
    fun seekStepRejectsUnsupportedValuesAndCyclesDeterministically() {
        assertEquals(10, normalizedSeekStepSeconds(7))
        assertEquals(15, nextSeekStepSeconds(10))
        assertEquals(30, nextSeekStepSeconds(15))
        assertEquals(10, nextSeekStepSeconds(30))
    }

    @Test
    fun ratingsAcceptOnlyOneThroughFive() {
        assertNull(normalizedUserRating(null))
        assertNull(normalizedUserRating(0))
        assertNull(normalizedUserRating(6))
        assertEquals(1, normalizedUserRating(1))
        assertEquals(5, normalizedUserRating(5))
    }

    @Test
    fun ratingKeyIsProfileAndContentScoped() {
        assertEquals(
            "rating:profile_a:MOVIE:42",
            userRatingPreferenceKey("profile_a", ContentType.MOVIE, 42),
        )
        assertEquals(
            "rating:profile_b:SERIES:42",
            userRatingPreferenceKey("profile_b", ContentType.SERIES, 42),
        )
    }
}

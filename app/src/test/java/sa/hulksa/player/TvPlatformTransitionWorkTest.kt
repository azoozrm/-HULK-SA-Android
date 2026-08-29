package sa.hulksa.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlatformTransitionWorkTest {
    @Test
    fun `current profile work may apply`() {
        assertTrue(
            isCurrentTvPlatformWork(
                currentGeneration = 7L,
                expectedGeneration = 7L,
                sessionStillCurrent = true,
                currentProfileId = "profile-a",
                expectedProfileId = "profile-a",
            ),
        )
    }

    @Test
    fun `stale generation cannot apply`() {
        assertFalse(
            isCurrentTvPlatformWork(
                currentGeneration = 8L,
                expectedGeneration = 7L,
                sessionStillCurrent = true,
                currentProfileId = "profile-a",
                expectedProfileId = "profile-a",
            ),
        )
    }

    @Test
    fun `stale profile cannot apply`() {
        assertFalse(
            isCurrentTvPlatformWork(
                currentGeneration = 7L,
                expectedGeneration = 7L,
                sessionStillCurrent = true,
                currentProfileId = "profile-b",
                expectedProfileId = "profile-a",
            ),
        )
    }

    @Test
    fun `logged out session cannot apply`() {
        assertFalse(
            isCurrentTvPlatformWork(
                currentGeneration = 7L,
                expectedGeneration = 7L,
                sessionStillCurrent = false,
                currentProfileId = "profile-a",
                expectedProfileId = "profile-a",
            ),
        )
    }
}

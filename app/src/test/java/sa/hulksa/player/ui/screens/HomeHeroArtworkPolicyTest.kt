package sa.hulksa.player.ui.screens

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroArtworkPolicyTest {
    @Test
    fun `only genuinely wide decoded artwork qualifies for the wide hero`() {
        assertTrue(homeHeroArtworkIsLandscape(1920f, 1080f))
        assertTrue(homeHeroArtworkIsLandscape(1300f, 1000f))
        assertFalse(homeHeroArtworkIsLandscape(1000f, 1000f))
        assertFalse(homeHeroArtworkIsLandscape(900f, 1600f))
        assertFalse(homeHeroArtworkIsLandscape(0f, 1080f))
        assertFalse(homeHeroArtworkIsLandscape(Float.NaN, 1080f))
    }

    @Test
    fun `artwork candidates keep owner order and drop blanks and duplicates`() {
        assertEquals(
            listOf("owned.jpg", "backdrop.jpg", "poster.jpg"),
            homeHeroArtworkCandidates(
                metadataArtwork = "owned.jpg",
                itemBackdrop = "backdrop.jpg",
                itemPoster = "poster.jpg",
            ),
        )
        assertEquals(
            listOf("backdrop.jpg", "poster.jpg"),
            homeHeroArtworkCandidates(
                metadataArtwork = null,
                itemBackdrop = "backdrop.jpg",
                itemPoster = "poster.jpg",
            ),
        )
        assertEquals(
            listOf("same.jpg"),
            homeHeroArtworkCandidates(
                metadataArtwork = "same.jpg",
                itemBackdrop = " same.jpg ",
                itemPoster = "  ",
            ),
        )
        assertTrue(
            homeHeroArtworkCandidates(null, null, null).isEmpty(),
        )
    }

    @Test
    fun `physical-left artwork alignment never mirrors under rtl`() {
        val size = IntSize(100, 100)
        val space = IntSize(600, 400)
        val ltr = heroArtworkPhysicalLeftAlignment.align(size, space, LayoutDirection.Ltr)
        val rtl = heroArtworkPhysicalLeftAlignment.align(size, space, LayoutDirection.Rtl)
        assertEquals(IntOffset(0, 150), ltr)
        assertEquals(ltr, rtl)
    }

    @Test
    fun `retained portrait survives later failed candidates while landscape wins`() {
        var portrait = HeroArtworkSelectionState()
        portrait = heroArtworkOnLoaded(portrait, 0, isLandscape = false, candidateCount = 3)
        assertEquals(0, portrait.retainedPortraitIndex)
        assertEquals(1, portrait.candidateIndex)
        portrait = heroArtworkOnFailed(portrait, 1, candidateCount = 3)
        portrait = heroArtworkOnFailed(portrait, 2, candidateCount = 3)
        assertTrue(portrait.exhausted)
        assertEquals(0, portrait.displayIndex)

        var landscape = HeroArtworkSelectionState()
        landscape = heroArtworkOnLoaded(landscape, 0, isLandscape = false, candidateCount = 2)
        landscape = heroArtworkOnLoaded(landscape, 1, isLandscape = true, candidateCount = 2)
        assertEquals(1, landscape.landscapeIndex)
        assertEquals(1, landscape.displayIndex)

        var allFailed = HeroArtworkSelectionState()
        allFailed = heroArtworkOnFailed(allFailed, 0, candidateCount = 2)
        allFailed = heroArtworkOnFailed(allFailed, 1, candidateCount = 2)
        assertTrue(allFailed.exhausted)
        assertNull(allFailed.displayIndex)
    }

    @Test
    fun `stale and settled artwork callbacks are ignored`() {
        val current = HeroArtworkSelectionState(candidateIndex = 1)
        assertSame(current, heroArtworkOnLoaded(current, 0, isLandscape = true, candidateCount = 3))
        assertSame(current, heroArtworkOnFailed(current, 0, candidateCount = 3))

        val settled = HeroArtworkSelectionState(landscapeIndex = 0)
        assertSame(settled, heroArtworkOnLoaded(settled, 0, isLandscape = false, candidateCount = 2))
    }
}

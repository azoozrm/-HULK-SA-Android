package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeApprovedVisualPolicyTest {
    @Test
    fun `wide approach artwork backdrops stay cinematic while poster fallback preserves subjects`() {
        assertEquals(
            HomeHeroArtworkMode.WIDE_BACKDROP,
            homeHeroArtworkMode(
                backdropUrl = "https://example.com/wide.jpg",
                posterUrl = "https://example.com/poster.jpg",
            ),
        )
        assertEquals(
            HomeHeroArtworkMode.VERTICAL_POSTER,
            homeHeroArtworkMode(backdropUrl = null, posterUrl = "https://example.com/poster.jpg"),
        )
        assertEquals(
            HomeHeroArtworkMode.VERTICAL_POSTER,
            homeHeroArtworkMode(backdropUrl = "  ", posterUrl = "https://example.com/poster.jpg"),
        )
        assertEquals(
            HomeHeroArtworkMode.BRAND_MARK,
            homeHeroArtworkMode(backdropUrl = null, posterUrl = null),
        )
        assertEquals(
            HomeHeroArtworkMode.BRAND_MARK,
            homeHeroArtworkMode(backdropUrl = " ", posterUrl = ""),
        )
    }

    @Test
    fun `hero copy column follows the approved forty percent tv budget`() {
        assertEquals(0.40f, cinemaHeroCopyWidthFraction(isTv = true, isPortraitPhone = false), 0.0001f)
        assertEquals(0.94f, cinemaHeroCopyWidthFraction(isTv = false, isPortraitPhone = true), 0.0001f)
        assertEquals(0.62f, cinemaHeroCopyWidthFraction(isTv = false, isPortraitPhone = false), 0.0001f)
    }
}

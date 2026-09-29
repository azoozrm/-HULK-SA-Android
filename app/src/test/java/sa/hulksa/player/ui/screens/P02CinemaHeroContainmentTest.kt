package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P02CinemaHeroContainmentTest {
    @Test
    fun `compact tv hero restores premium proportion and keeps the first landscape row visible`() {
        val hero = cinemaHeroHeightDp(isTv = true, isPortraitPhone = false, screenHeightDp = 540)

        assertEquals(280f, hero, 0.001f)
        // hero + 16dp gap + the complete 209dp Continue Watching section item (36dp heading zone
        // plus the 173dp landscape row) stays inside the 540dp window.
        assertTrue(hero + 16f + 209f <= 540f)
    }

    @Test
    fun `tv hero scales with the window and stays bounded`() {
        assertEquals(331.2f, cinemaHeroHeightDp(isTv = true, isPortraitPhone = false, screenHeightDp = 720), 0.001f)
        assertEquals(380f, cinemaHeroHeightDp(isTv = true, isPortraitPhone = false, screenHeightDp = 1080), 0.001f)
    }

    @Test
    fun `phone hero uses a taller portrait proportion with a deterministic landscape value`() {
        assertEquals(360f, cinemaHeroHeightDp(isTv = false, isPortraitPhone = true, screenHeightDp = 720), 0.001f)
        assertEquals(400f, cinemaHeroHeightDp(isTv = false, isPortraitPhone = true, screenHeightDp = 900), 0.001f)
        assertEquals(280f, cinemaHeroHeightDp(isTv = false, isPortraitPhone = false, screenHeightDp = 360), 0.001f)
    }

    @Test
    fun `hero text budget always reserves the pinned action row and the tv top overlay`() {
        listOf(280f, 331.2f, 380f).forEach { hero ->
            val budget = cinemaHeroTextBudgetDp(heroHeightDp = hero, isTv = true)
            val occupied = budget + 64f + 18f + 44f + 10f
            assertEquals(hero, occupied, 0.001f)
        }
        val phoneBudget = cinemaHeroTextBudgetDp(heroHeightDp = 400f, isTv = false)
        assertEquals(400f, phoneBudget + 14f + 44f + 10f, 0.001f)
    }

    @Test
    fun `a two line compact title drops the synopsis so the ctas stay inside the hero`() {
        assertEquals(
            0,
            cinemaHeroSynopsisMaxLines(
                heroHeightDp = 280f,
                isTv = true,
                titleLineCount = 2,
                titleLineHeightDp = 44f,
            ),
        )
        assertEquals(
            2,
            cinemaHeroSynopsisMaxLines(
                heroHeightDp = 280f,
                isTv = true,
                titleLineCount = 1,
                titleLineHeightDp = 44f,
            ),
        )
        assertEquals(
            2,
            cinemaHeroSynopsisMaxLines(
                heroHeightDp = 331.2f,
                isTv = true,
                titleLineCount = 2,
                titleLineHeightDp = 44f,
            ),
        )
    }

    @Test
    fun `tv overscan safe inset follows the 5 percent plus 4dp rule on the 4dp grid`() {
        assertEquals(52f, tvOverscanSafeInsetDp(960), 0.001f)
        assertEquals(32f, tvOverscanSafeInsetDp(540), 0.001f)
        assertEquals(68f, tvOverscanSafeInsetDp(1280), 0.001f)
        assertEquals(40f, tvOverscanSafeInsetDp(720), 0.001f)
        assertEquals(60f, tvOverscanSafeInsetDp(1080), 0.001f)
    }

    @Test
    fun `home row cursor accounts for the hero block and the phone services row`() {
        assertEquals(1, homeRowCursorStart(isTv = true))
        assertEquals(2, homeRowCursorStart(isTv = false))
    }

    @Test
    fun `larger font scales reduce synopsis lines deterministically`() {
        assertEquals(
            0,
            cinemaHeroSynopsisMaxLines(
                heroHeightDp = 280f,
                isTv = true,
                titleLineCount = 2,
                titleLineHeightDp = 44f,
                fontScale = 1.5f,
            ),
        )
        assertEquals(
            1,
            cinemaHeroSynopsisMaxLines(
                heroHeightDp = 331.2f,
                isTv = true,
                titleLineCount = 2,
                titleLineHeightDp = 44f,
                fontScale = 1.2f,
            ),
        )
    }
}

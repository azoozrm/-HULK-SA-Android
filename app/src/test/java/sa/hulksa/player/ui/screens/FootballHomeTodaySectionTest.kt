package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract for the owner's Home Today-only row and its non-Home Center entry. */
class FootballHomeTodaySectionTest {

    private fun mainShellSource(): String {
        val candidates = listOf(
            File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
            File("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"),
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `Home sports section has no fallback, actions or synthetic state cards`() {
        val source = mainShellSource()
        val section = source
            .substringAfter("private fun HomeFootballSection(")
            .substringBefore("\n@Composable\nprivate fun ")
        assertFalse(section.contains("allowOtherDayFallback"))
        assertFalse(section.contains("عرض الكل"))
        assertFalse(section.contains("ادارة فرقي"))
        assertFalse(section.contains("FootballFeedStatusCard"))
        assertFalse(section.contains("FootballEmptyDayCard"))
        assertTrue(section.contains("HomeSectionHeadingText(\"مباريات اليوم\""))
    }

    @Test
    fun `Home row is composed only for exact Today matches`() {
        val source = mainShellSource()
        assertTrue(source.contains("footballHomeTodayMatches("))
        assertTrue(source.contains("state.footballFeed.available && homeFootballMatches.isNotEmpty()"))
        assertTrue(source.contains("if (homeFootballMatches.isNotEmpty()) return@LaunchedEffect"))
    }

    @Test
    fun `football center remains an existing navigation destination`() {
        val main = mainShellSource()
        val mobile = listOf(
            File("src/main/java/sa/hulksa/player/ui/StableMobileBottomNavigation.kt"),
            File("app/src/main/java/sa/hulksa/player/ui/StableMobileBottomNavigation.kt"),
        ).first { it.isFile }.readText()
        assertTrue(main.contains("MainDestination.FOOTBALL, Icons.Rounded.SportsSoccer, \"المباريات\""))
        assertTrue(mobile.contains("MainDestination.FOOTBALL, Icons.Rounded.SportsSoccer, \"المباريات\""))
        assertTrue(main.contains("if (state.destination == MainDestination.FOOTBALL)"))
    }
}

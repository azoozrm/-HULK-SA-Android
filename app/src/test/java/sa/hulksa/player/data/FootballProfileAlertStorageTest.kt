package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballProfileAlertStorageTest {

    private fun match(id: String) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "League"),
        homeTeam = FootballTeam("h$id", "Home"),
        awayTeam = FootballTeam("a$id", "Away"),
        kickoffEpochMs = 1_000_000L,
        status = FootballMatchStatus.UPCOMING,
    )

    @Test
    fun `alerts survive a new store instance and stay profile-scoped`() {
        val context = TestAppContext()
        val account = AccountScopeStore(context)
        assertTrue(account.bind("account-a"))
        val profiles = ProfileStore(context)
        val first = profiles.createProfile("First") ?: error("first profile")
        val second = profiles.createProfile("Second") ?: error("second profile")
        profiles.setActiveProfile(first.id)

        val firstStore = FootballProfileStateStore(context)
        val firstScope = firstStore.activeScope() ?: error("first scope")
        firstStore.saveMatchAlerts(
            firstScope,
            mapOf("a" to FootballMatchAlertPreferences(kickoff = true)),
        )

        val reopened = FootballProfileStateStore(context)
        assertEquals(
            true,
            reopened.matchAlerts(firstScope).getValue("a").kickoff,
        )

        profiles.setActiveProfile(second.id)
        val secondStore = FootballProfileStateStore(context)
        val secondScope = secondStore.activeScope() ?: error("second scope")
        secondStore.saveMatchAlerts(
            secondScope,
            mapOf("b" to FootballMatchAlertPreferences(reminder15 = true)),
        )
        assertTrue(secondStore.matchAlerts(secondScope).containsKey("b"))
        assertTrue(!secondStore.matchAlerts(secondScope).containsKey("a"))
        assertEquals(
            true,
            FootballProfileStateStore(context).matchAlerts(firstScope).getValue("a").kickoff,
        )
        assertNotNull(match("a"))
    }
}

package sa.hulksa.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballAlertSaveSerializerTest {

    private fun match(id: String) = FootballMatch(
        providerEventId = id,
        competition = FootballCompetition("17", "League"),
        homeTeam = FootballTeam("h$id", "Home"),
        awayTeam = FootballTeam("a$id", "Away"),
        kickoffEpochMs = 1_000_000L,
        status = FootballMatchStatus.UPCOMING,
    )

    @Test
    fun `two quick saves for different matches both survive`() = runBlocking {
        val serializer = FootballAlertSaveSerializer()
        var stored = emptyMap<String, FootballMatchAlertPreferences>()
        suspend fun read() = stored
        suspend fun write(value: Map<String, FootballMatchAlertPreferences>) {
            delay(50L)
            stored = value
        }

        val first = async(Dispatchers.Default) {
            serializer.update(
                read = ::read,
                write = ::write,
                match = match("a"),
                preferences = FootballMatchAlertPreferences(kickoff = true),
                nowEpochMs = 1L,
            )
        }
        val second = async(Dispatchers.Default) {
            serializer.update(
                read = ::read,
                write = ::write,
                match = match("b"),
                preferences = FootballMatchAlertPreferences(reminder15 = true),
                nowEpochMs = 2L,
            )
        }
        first.await()
        second.await()
        assertEquals(setOf("a", "b"), stored.keys)
        assertTrue(stored.getValue("a").kickoff)
        assertTrue(stored.getValue("b").reminder15)
    }

    @Test
    fun `successive saves for the same match keep the latest values`() = runBlocking {
        val serializer = FootballAlertSaveSerializer()
        var stored = emptyMap<String, FootballMatchAlertPreferences>()
        serializer.update(
            read = { stored },
            write = { stored = it },
            match = match("a"),
            preferences = FootballMatchAlertPreferences(kickoff = true, reminder15 = false),
            nowEpochMs = 1L,
        )
        serializer.update(
            read = { stored },
            write = { stored = it },
            match = match("a"),
            preferences = FootballMatchAlertPreferences(kickoff = false, reminder15 = true),
            nowEpochMs = 2L,
        )
        assertEquals(1, stored.size)
        assertEquals(false, stored.getValue("a").kickoff)
        assertEquals(true, stored.getValue("a").reminder15)
    }
}

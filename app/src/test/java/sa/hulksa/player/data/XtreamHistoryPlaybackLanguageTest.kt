package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.model.PortalConfig

class XtreamHistoryPlaybackLanguageTest {

    @Test
    fun `history replay forwards explicit original language and keeps legacy entries unknown`() {
        val session = AuthenticatedSession(
            portal = PortalConfig(
                baseUrl = "https://portal.example.test/",
                source = PortalConfig.Source.ACCESS_CODE,
            ),
            credentials = Credentials(
                accessCode = "synthetic-code",
                username = "synthetic-user",
                password = "synthetic-password",
            ),
            account = AccountInfo(
                username = "synthetic-user",
                status = "Active",
                expiresAtEpochSeconds = null,
                activeConnections = 1,
                maxConnections = 1,
                isTrial = false,
            ),
        )
        val client = XtreamClient()

        val audioPreference = """{"identity":"ar-main","language":"ar","ordinal":1}"""
        val known = client.playback(
            session,
            historyEntry(
                originalLanguage = "ar",
                savedAudioPreference = audioPreference,
                savedSubtitlePreference = "off",
            ),
        )
        assertEquals("ar", known.originalLanguage)
        assertEquals(audioPreference, known.savedAudioPreference)
        assertEquals("off", known.savedSubtitlePreference)

        val legacy = client.playback(session, historyEntry(originalLanguage = null))
        assertNull(legacy.originalLanguage)
        assertNull(legacy.savedAudioPreference)
        assertNull(legacy.savedSubtitlePreference)
    }

    private fun historyEntry(
        originalLanguage: String?,
        savedAudioPreference: String? = null,
        savedSubtitlePreference: String? = null,
    ): HistoryEntry = HistoryEntry(
        key = "MOVIE:7",
        title = "Synthetic movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 7,
        extension = "mp4",
        isLive = false,
        positionMs = 12_000L,
        durationMs = 120_000L,
        updatedAtEpochMs = 1L,
        originalLanguage = originalLanguage,
        savedAudioPreference = savedAudioPreference,
        savedSubtitlePreference = savedSubtitlePreference,
    )
}

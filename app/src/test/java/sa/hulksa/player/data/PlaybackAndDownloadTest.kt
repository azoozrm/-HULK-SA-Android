package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.DownloadScheduleMode
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.ui.screens.relativeChannelIndex

class PlaybackAndDownloadTest {
    @Test
    fun livePlaybackUsesOnlyTheServerPreferredSource() {
        val session = AuthenticatedSession(
            portal = PortalConfig("http://example.test:8080", PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "demo", "secret"),
            account = AccountInfo("demo", "Active", null, 0, 1, false),
        )
        val channel = ContentItem(
            id = 77,
            name = "Demo TV",
            categoryId = "1",
            type = ContentType.LIVE,
            posterUrl = null,
            rating = null,
            year = null,
            containerExtension = "ts",
        )

        val request = XtreamClient().playback(session, channel)

        assertEquals(1, request.candidates.size)
        assertTrue(request.candidates.single().endsWith("/live/demo/secret/77.ts"))
    }

    @Test
    fun livePlaybackAddsOnlyProviderAdvertisedFallbackFormats() {
        val session = session(allowedFormats = linkedSetOf("ts", "m3u8"))
        val request = XtreamClient().playback(session, liveChannel(extension = "ts"))

        assertEquals(2, request.candidates.size)
        assertTrue(request.candidates[0].endsWith("/live/demo/secret/77.ts"))
        assertTrue(request.candidates[1].endsWith("/live/demo/secret/77.m3u8"))
    }

    @Test
    fun providerPreferredHlsStaysFirstAndTsIsBoundedFallback() {
        val session = session(allowedFormats = linkedSetOf("ts", "m3u8"))
        val request = XtreamClient().playback(session, liveChannel(extension = "m3u8"))

        assertTrue(request.candidates[0].endsWith("/live/demo/secret/77.m3u8"))
        assertTrue(request.candidates[1].endsWith("/live/demo/secret/77.ts"))
    }

    @Test
    fun allowedOutputFormatsParserRejectsUnknownProtocolsAndDeduplicatesAliases() {
        assertEquals(
            linkedSetOf("ts", "m3u8"),
            parseAllowedLiveOutputFormats(JSONArray(listOf("mpegts", "m3u8", "rtmp", "ts"))),
        )
    }

    @Test
    fun nextAndPreviousChannelDirectionsAreNotReversed() {
        assertEquals(3, relativeChannelIndex(currentIndex = 2, delta = 1, size = 5))
        assertEquals(1, relativeChannelIndex(currentIndex = 2, delta = -1, size = 5))
        assertEquals(0, relativeChannelIndex(currentIndex = 4, delta = 1, size = 5))
        assertEquals(4, relativeChannelIndex(currentIndex = 0, delta = -1, size = 5))
    }

    @Test
    fun downloadSettingsKeepSafeDefaults() {
        val settings = DownloadSettings()
        assertEquals(false, settings.wifiOnly)
        assertEquals(DownloadScheduleMode.NOW, settings.scheduleMode)
        assertEquals(2, settings.concurrentDownloads)
    }

    @Test
    fun offlineProgressIsCalculatedAndClamped() {
        val item = OfflineDownload(
            downloadId = 1L,
            historyKey = "MOVIE:1",
            title = "Demo",
            posterUrl = null,
            streamKind = "movie",
            streamId = 1,
            extension = "mp4",
            bytesDownloaded = 75L,
            totalBytes = 100L,
        )

        assertEquals(.75f, item.progress, 0.001f)
        assertEquals(1f, item.copy(bytesDownloaded = 120L).progress, 0.001f)
        assertEquals(0f, item.copy(totalBytes = -1L).progress, 0.001f)
    }

    private fun session(allowedFormats: Set<String>) = AuthenticatedSession(
        portal = PortalConfig("http://example.test:8080", PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", "demo", "secret"),
        account = AccountInfo("demo", "Active", null, 0, 1, false),
        allowedLiveOutputFormats = allowedFormats,
    )

    private fun liveChannel(extension: String) = ContentItem(
        id = 77,
        name = "Demo TV",
        categoryId = "1",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = extension,
    )
}

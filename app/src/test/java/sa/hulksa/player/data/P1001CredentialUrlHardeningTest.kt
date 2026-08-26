package sa.hulksa.player.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.PortalConfig
import sa.hulksa.player.security.REDACTED_IPTV_URL
import sa.hulksa.player.security.isCredentialBearingIptvUrl
import sa.hulksa.player.security.persistableExternalUrlOrNull
import sa.hulksa.player.security.redactCredentialBearingUrl
import sa.hulksa.player.tv.TvDeepLinkRouter
import sa.hulksa.player.tv.TvDeepLinkTarget
import sa.hulksa.player.tv.isSafeTvProgramArtworkUrl

class P1001CredentialUrlHardeningTest {
    @Test
    fun `redaction catches player api credentials`() {
        val raw = "http://provider.test/player_api.php?username=alpha&password=secret&action=get_live_streams"

        assertTrue(isCredentialBearingIptvUrl(raw))
        assertEquals(REDACTED_IPTV_URL, redactCredentialBearingUrl(raw))
    }

    @Test
    fun `redaction catches live movie and series credential paths`() {
        listOf(
            "http://provider.test/live/alpha/secret/10.ts",
            "http://provider.test/movie/alpha/secret/20.mp4",
            "http://provider.test/series/alpha/secret/30.mkv",
        ).forEach { raw ->
            assertTrue(raw, isCredentialBearingIptvUrl(raw))
            assertEquals(REDACTED_IPTV_URL, redactCredentialBearingUrl(raw))
        }
    }

    @Test
    fun `strict URL classification allows normal CDN media routes`() {
        listOf(
            "https://cdn.example.test/movie/123/poster.jpg",
            "https://cdn.example.test/series/show/cover.jpg",
            "https://cdn.example.test/live/channel/logo.png",
            "https://cdn.example.test/posters/movie/123.jpg",
        ).forEach { raw ->
            assertFalse(raw, isCredentialBearingIptvUrl(raw))
            assertEquals(raw, persistableExternalUrlOrNull(raw))
        }
    }

    @Test
    fun `strict URL classification still blocks Xtream media credentials`() {
        listOf(
            "http://provider.test/movie/alpha/secret/55.mp4",
            "http://provider.test/series/alpha/secret/77.mkv",
            "http://provider.test/live/alpha/secret/99.ts",
            "http://provider.test/player_api.php?username=alpha&password=secret",
        ).forEach { raw ->
            assertTrue(raw, isCredentialBearingIptvUrl(raw))
            assertEquals(null, persistableExternalUrlOrNull(raw))
        }
    }

    @Test
    fun `redaction inspects percent encoded credential material`() {
        assertTrue(
            isCredentialBearingIptvUrl(
                "http://provider.test/player_api.php?username%3Dalpha%26password%3Dsecret",
            ),
        )
        assertTrue(
            isCredentialBearingIptvUrl(
                "http://provider.test%2Fmovie%2Falpha%2Fsecret%2F20.mp4",
            ),
        )
    }

    @Test
    fun `download persistence removes credential sources but keeps stable identity and file metadata`() {
        val raw = """[
            {
              "downloadId": 42,
              "historyKey": "MOVIE:55",
              "title": "Movie",
              "posterUrl": "http://provider.test/movie/alpha/secret/55.jpg",
              "streamKind": "movie",
              "streamId": 55,
              "extension": "mp4",
              "sourceCandidates": ["http://provider.test/movie/alpha/secret/55.mp4"],
              "sourceUrl": "http://provider.test/movie/alpha/secret/55.mp4",
              "fileName": "Movie_55.mp4",
              "storagePath": "/storage/emulated/0/Android/data/app/files/Movies",
              "localUri": "file:///storage/emulated/0/Android/data/app/files/Movies/Movie_55.mp4",
              "errorMessage": "failed http://provider.test/movie/alpha/secret/55.mp4"
            }
        ]""".trimIndent()

        val sanitized = sanitizePersistedDownloadJson(raw).orEmpty()
        val record = JSONArray(sanitized).getJSONObject(0)

        assertFalse(record.has("sourceCandidates"))
        assertFalse(record.has("sourceUrl"))
        assertTrue(record.isNull("posterUrl"))
        assertEquals(REDACTED_IPTV_URL, record.getString("errorMessage"))
        assertEquals("movie", record.getString("streamKind"))
        assertEquals(55, record.getInt("streamId"))
        assertEquals("mp4", record.getString("extension"))
        assertEquals("Movie_55.mp4", record.getString("fileName"))
        assertTrue("username=" !in sanitized.lowercase())
        assertTrue("password=" !in sanitized.lowercase())
        assertTrue("/movie/alpha/secret/" !in sanitized.lowercase())
    }

    @Test
    fun `malformed legacy download metadata with credential material fails closed`() {
        val malformed = "not-json http://provider.test/series/alpha/secret/99.mkv"
        assertEquals("[]", sanitizePersistedDownloadJson(malformed))
    }

    @Test
    fun `http download URL is rebuilt only at runtime from current authenticated session`() {
        val session = session("http://provider.test:8080", "alpha", "secret-new")
        val record = downloadRecord()

        val sources = downloadRuntimeSourceCandidates(
            record = record,
            expectedAccountId = "account-a",
            session = session,
            metadata = metadata("account-a", "alpha", "http://provider.test:8080"),
        )

        assertEquals(listOf("http://provider.test:8080/movie/alpha/secret-new/55.mp4"), sources)
    }

    @Test
    fun `https download URL is rebuilt only at runtime from current authenticated session`() {
        val session = session("https://provider.test", "alpha", "secret-new")

        val sources = downloadRuntimeSourceCandidates(
            record = downloadRecord(),
            expectedAccountId = "account-a",
            session = session,
            metadata = metadata("account-a", "alpha", "https://provider.test"),
        )

        assertEquals(listOf("https://provider.test/movie/alpha/secret-new/55.mp4"), sources)
    }

    @Test
    fun `download identity from account A cannot resolve under account B`() {
        val session = session("https://provider.test", "alpha", "secret")

        assertTrue(
            downloadRuntimeSourceCandidates(
                record = downloadRecord(),
                expectedAccountId = "account-a",
                session = session,
                metadata = metadata("account-b", "alpha", "https://provider.test"),
            ).isEmpty(),
        )
    }

    @Test
    fun `session replacement rebuilds with new credentials instead of old persisted URL`() {
        val record = downloadRecord()
        val oldSession = session("https://provider.test", "alpha", "old-secret")
        val newSession = session("https://provider.test", "alpha", "new-secret")
        val metadata = metadata("account-a", "alpha", "https://provider.test")

        val oldUrl = downloadRuntimeSourceCandidates(record, "account-a", oldSession, metadata).single()
        val newUrl = downloadRuntimeSourceCandidates(record, "account-a", newSession, metadata).single()

        assertTrue(oldUrl.contains("/old-secret/"))
        assertTrue(newUrl.contains("/new-secret/"))
        assertFalse(newUrl.contains("/old-secret/"))
    }

    @Test
    fun `work plan contains identity only and no transport URL field`() {
        val plan = durableDownloadWorkPlan(
            accountId = "account-a",
            downloadId = 42L,
            wifiOnly = false,
            scheduledAtEpochMs = 0L,
            nowEpochMs = 1L,
        )
        val serialized = plan.toString().lowercase()

        assertEquals("account-a", plan.accountId)
        assertEquals(42L, plan.downloadId)
        assertFalse("http://" in serialized)
        assertFalse("https://" in serialized)
        assertFalse("username=" in serialized)
        assertFalse("password=" in serialized)
    }

    @Test
    fun `tv internal deep link contains identity only and artwork uses strict credential classification`() {
        val deepLink = TvDeepLinkRouter.uri(TvDeepLinkTarget.Movie(movieId = 55))

        assertEquals("hulksa://movie/55", deepLink)
        assertFalse(isCredentialBearingIptvUrl(deepLink))
        assertTrue(isSafeTvProgramArtworkUrl("https://cdn.example.test/movie/123/poster.jpg"))
        assertFalse(isSafeTvProgramArtworkUrl("http://provider.test/movie/alpha/secret/55.jpg"))
        assertTrue(isSafeTvProgramArtworkUrl("https://cdn.example.test/posters/55.jpg"))
    }

    private fun downloadRecord(): OfflineDownload = OfflineDownload(
        downloadId = 42L,
        historyKey = "MOVIE:55",
        title = "Movie",
        posterUrl = null,
        streamKind = "movie",
        streamId = 55,
        extension = "mp4",
        sourceCandidates = emptyList(),
    )

    private fun session(baseUrl: String, username: String, password: String): AuthenticatedSession =
        AuthenticatedSession(
            portal = PortalConfig(baseUrl, PortalConfig.Source.ACCESS_CODE),
            credentials = Credentials("HULK-ABCD-EFGH-JKMN-PQRS", username, password),
            account = AccountInfo(username, "Active", null, 0, 1, false),
        )

    private fun metadata(
        accountId: String,
        username: String,
        portalBaseUrl: String,
    ): AccountSessionMetadata = AccountSessionMetadata(
        accountId = accountId,
        username = username,
        portalBaseUrl = portalBaseUrl,
        authenticatedAtEpochMs = 1L,
        expiresAtEpochSeconds = Long.MAX_VALUE,
        status = "Active",
        installationId = "installation",
        sessionId = "session",
    )
}

package sa.hulksa.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.data.HulkRepository
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.PlaybackRequest

@RunWith(AndroidJUnit4::class)
class ProductionAuthenticatedSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = instrumentation.targetContext

    @Test
    fun authenticatedProductionCatalogDetailsAndStreamsAreReachable() = runBlocking {
        assumeTrue(
            "Production E2E smoke is opt-in only",
            arguments.getString(ARG_ENABLED) == "true",
        )

        val username = requireSecretArgument(ARG_USERNAME)
        val password = requireSecretArgument(ARG_PASSWORD)
        val repository = HulkRepository(context)

        try {
            withTimeout(180_000L) {
                val session = repository.login(
                    credentials = Credentials(username = username, password = password),
                    remember = false,
                )

                assertTrue(session.account.status.equals("Active", ignoreCase = true))
                assertEquals(BuildConfig.PORTAL_URL.trimEnd('/'), session.portal.baseUrl.trimEnd('/'))
                assertFalse(session.account.username.isBlank())

                val liveCatalog = repository.catalog(session, ContentType.LIVE)
                val movieCatalog = repository.catalog(session, ContentType.MOVIE)
                val seriesCatalog = repository.catalog(session, ContentType.SERIES)

                assertTrue("Production live catalog is empty", liveCatalog.items.isNotEmpty())
                assertTrue("Production movie catalog is empty", movieCatalog.items.isNotEmpty())
                assertTrue("Production series catalog is empty", seriesCatalog.items.isNotEmpty())

                val movie = movieCatalog.items.first()
                repository.contentDetails(session, movie.id)

                var seriesAndEpisode: Pair<ContentItem, Episode>? = null
                for (series in seriesCatalog.items.take(MAX_SERIES_PROBES)) {
                    val bundle = try {
                        repository.seriesBundle(session, series.id)
                    } catch (_: Exception) {
                        null
                    }
                    val episode = bundle?.episodes?.firstOrNull()
                    if (episode != null) {
                        seriesAndEpisode = series to episode
                        break
                    }
                }
                assertTrue(
                    "No production series with a reachable episode was found in the bounded smoke sample",
                    seriesAndEpisode != null,
                )

                val liveRequests = liveCatalog.items
                    .asSequence()
                    .take(MAX_STREAM_PROBES)
                    .map { repository.playback(session, it) }
                    .toList()
                val movieRequests = movieCatalog.items
                    .asSequence()
                    .take(MAX_STREAM_PROBES)
                    .map { repository.playback(session, it) }
                    .toList()
                val episodeRequests = seriesAndEpisode?.let { (series, episode) ->
                    listOf(repository.playback(session, series, episode))
                }.orEmpty()

                assertAtLeastOneReadableStream("live", liveRequests)
                assertAtLeastOneReadableStream("movie", movieRequests)
                assertAtLeastOneReadableStream("episode", episodeRequests)
            }
        } finally {
            repository.logout()
        }
    }

    private fun requireSecretArgument(name: String): String {
        val value = arguments.getString(name).orEmpty()
        assertTrue("Required protected instrumentation argument is missing: $name", value.isNotBlank())
        return value
    }

    private fun assertAtLeastOneReadableStream(
        label: String,
        requests: List<PlaybackRequest>,
    ) {
        assertTrue("No $label playback request was produced", requests.isNotEmpty())
        val readable = requests.any { playback ->
            playback.candidates.any(::probeReadableBytes)
        }
        assertTrue("No $label stream returned readable bytes in the bounded smoke sample", readable)
    }

    private fun probeReadableBytes(url: String): Boolean {
        if (url.isBlank()) return false
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
            .header("User-Agent", "HULK-SA-E2E/${BuildConfig.VERSION_NAME}")
            .build()

        return try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val buffer = ByteArray(PROBE_BYTES)
                body.byteStream().read(buffer) > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val ARG_ENABLED = "hulkProductionE2e"
        const val ARG_USERNAME = "hulkE2eUsername"
        const val ARG_PASSWORD = "hulkE2ePassword"
        const val MAX_STREAM_PROBES = 4
        const val MAX_SERIES_PROBES = 6
        const val PROBE_BYTES = 4096

        val streamClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

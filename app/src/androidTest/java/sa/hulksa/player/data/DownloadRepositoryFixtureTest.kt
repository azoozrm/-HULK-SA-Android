package sa.hulksa.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.model.PlaybackRequest
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryFixtureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var server: MockWebServer
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        context.getSharedPreferences("hulk_downloads", Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        repository = DownloadRepository(context)
    }

    @After
    fun tearDown() {
        repository.downloads().forEach { repository.remove(it.downloadId) }
        context.getSharedPreferences("hulk_downloads", Context.MODE_PRIVATE).edit().clear().commit()
        server.shutdown()
    }

    @Test
    fun productionRepositoryTransfersPositiveBytesAndFinalFileMatchesFixture() {
        val payload = ByteArray(1024 * 1024) { index -> (index % 251).toByte() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.getHeader("Range") == "bytes=0-0") {
                    MockResponse()
                        .setResponseCode(206)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Range", "bytes 0-0/${payload.size}")
                        .setBody(Buffer().write(payload, 0, 1))
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", payload.size)
                        .setBody(Buffer().write(payload))
                }
            }
        }
        server.start()
        val request = PlaybackRequest(
            title = "quality-fixture",
            posterUrl = null,
            candidates = listOf(server.url("/vod.bin").toString()),
            isLive = false,
            historyKey = "QUALITY:DOWNLOAD:1",
            streamKind = "movie",
            streamId = 900001,
            extension = "bin",
        )

        val started = repository.enqueue(request) as DownloadRepository.EnqueueResult.Started
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var observedPositiveBytes = false
        var current = started.item
        while (System.nanoTime() < deadline) {
            current = repository.downloads().first { it.downloadId == started.item.downloadId }
            observedPositiveBytes = observedPositiveBytes || current.bytesDownloaded > 0L
            if (current.status == OfflineStatus.COMPLETED || current.status == OfflineStatus.FAILED) break
            Thread.sleep(100)
        }

        assertTrue("the production repository never transferred a positive byte", observedPositiveBytes)
        assertEquals(current.errorMessage, OfflineStatus.COMPLETED, current.status)
        assertEquals(payload.size.toLong(), current.bytesDownloaded)
        assertEquals(payload.size.toLong(), current.totalBytes)
        assertTrue(current.integrityVerified)
        val file = File(requireNotNull(android.net.Uri.parse(requireNotNull(current.localUri)).path))
        assertTrue(file.isFile)
        assertArrayEquals(sha256(payload), sha256(file.readBytes()))
        assertTrue(server.requestCount >= 2)
    }

    private fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)
}

package sa.hulksa.player.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.AccountInfo
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.Credentials
import sa.hulksa.player.model.PortalConfig

class EpgXmltvClientTest {
    private val now = 1_794_924_000L // 2026-11-17T14:00:00Z

    @Test
    fun loadsGzipXmltvFromTheSessionScopedEndpoint() {
        val server = MockWebServer()
        server.start()
        try {
            val providerHost = server.url("/").host
            val xml = xmltv(
                generatorUrl = providerHost,
                programmes = """
                    <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="SkySports1.uk">
                      <title>Live Match</title>
                    </programme>
                """.trimIndent(),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                    .setHeader("Content-Encoding", "gzip")
                    .setBody(gzip(xml)),
            )
            val client = EpgXmltvClient(
                nowEpochSeconds = { now },
                providerHost = providerHost,
            )
            val session = session(
                baseUrl = server.url("/").toString(),
                username = "synthetic-user",
            )

            val snapshot = runBlocking {
                client.load(session, setOf("SkySports1.uk", "skysports1.uk"))
            }

            assertEquals(1, snapshot.schedule.programmesByChannel.size)
            assertEquals(
                "Live Match",
                snapshot.schedule.programmesByChannel.getValue("SkySports1.uk").single().title,
            )
            val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/xmltv.php", recorded.requestUrl!!.encodedPath)
            assertEquals("synthetic-user", recorded.requestUrl!!.queryParameter("username"))
            assertEquals("gzip", recorded.getHeader("Accept-Encoding"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun hulkSessionIssuesZeroEpgRequests() {
        val server = MockWebServer()
        server.start()
        try {
            // A response is available, but the gate must reject before any socket request.
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(gzip(xmltv(programmes = ""))),
            )
            val client = EpgXmltvClient(
                nowEpochSeconds = { now },
                providerHost = server.url("/").host,
            )
            val hulk = session(baseUrl = "http://stotre5.online:8080")

            assertThrows(EpgException.ProviderNotVerified::class.java) {
                runBlocking { client.load(hulk, setOf("SkySports1.uk")) }
            }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun documentIdentityMismatchIsRejectedWithoutPublishingAProgramme() {
        val server = MockWebServer()
        server.start()
        try {
            val providerHost = server.url("/").host
            val foreignXml = xmltv(
                generatorName = "HULK",
                generatorUrl = providerHost,
                programmes = """
                    <programme start="20261117150000 +0100" stop="20261117160000 +0100" channel="SkySports1.uk">
                      <title>Foreign programme</title>
                    </programme>
                """.trimIndent(),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(foreignXml))
            val client = EpgXmltvClient(
                nowEpochSeconds = { now },
                providerHost = providerHost,
            )
            val session = session(baseUrl = server.url("/").toString())

            assertThrows(EpgException.ProviderMismatch::class.java) {
                runBlocking { client.load(session, setOf("SkySports1.uk")) }
            }
            assertTrue(server.requestCount == 1)
        } finally {
            server.shutdown()
        }
    }

    private fun session(
        baseUrl: String,
        username: String = "synthetic-user",
    ): AuthenticatedSession = AuthenticatedSession(
        portal = PortalConfig(baseUrl, PortalConfig.Source.ACCESS_CODE),
        credentials = Credentials(
            accessCode = "SYNTHETIC-CODE",
            username = username,
            password = "SYNTHETIC-PASSWORD",
        ),
        account = AccountInfo(
            username = username,
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 1,
            maxConnections = 2,
            isTrial = false,
        ),
    )

    private fun gzip(value: String): Buffer {
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { sink ->
            sink.writeUtf8(value)
        }
        return compressed
    }

    private fun xmltv(
        generatorName: String = NeoEpgProviderIdentity.XMLTV_GENERATOR,
        generatorUrl: String = NeoEpgProviderIdentity.PROVIDER_HOST,
        programmes: String,
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n")
        append("<tv generator-info-name=\"$generatorName\" generator-info-url=\"$generatorUrl\">\n")
        append("<channel id=\"SkySports1.uk\"><display-name>Sky Sports 1</display-name></channel>\n")
        append(programmes)
        append("\n</tv>")
    }
}

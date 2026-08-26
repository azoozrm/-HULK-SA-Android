package sa.hulksa.player.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSource
import okio.GzipSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamJsonHardeningTest {
    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    @Test
    fun smallValidAuthJsonParsesNormally() {
        val text = """{"user_info":{"auth":1,"status":"Active"}}"""
        val body = text.toResponseBody(JSON)

        val read = BoundedJsonResponseReader.readUtf8(body, XtreamJsonLimits.AUTH.maxResponseBytes)
        val root = XtreamJsonParser.parseObject(read)

        assertEquals(1, root.getJSONObject("user_info").getInt("auth"))
    }

    @Test
    fun validJsonWithTextPlainContentTypeRemainsCompatible() {
        val body = """[{"stream_id":7}]""".toResponseBody(TEXT)

        val array = XtreamJsonParser.parseArray(
            BoundedJsonResponseReader.readUtf8(body, XtreamJsonLimits.CATALOG.maxResponseBytes),
        )

        assertEquals(7, array.getJSONObject(0).getInt("stream_id"))
    }

    @Test
    fun smallValidCatalogJsonParsesNormally() {
        val text = """[{"stream_id":1,"name":"One"},{"stream_id":2,"name":"Two"}]"""
        val body = text.toResponseBody(JSON)

        val array = XtreamJsonParser.parseArray(
            BoundedJsonResponseReader.readUtf8(body, XtreamJsonLimits.CATALOG.maxResponseBytes),
        )
        array.requireUniqueObjectLimit(2, "test catalog") { item ->
            item.optString("stream_id").toIntOrNull()?.toString()
        }

        assertEquals(2, array.length())
    }

    @Test
    fun declaredBodyLargerThanLimitIsRejectedBeforeMaterialization() {
        val body = "x".repeat(129).toResponseBody(TEXT)

        val error = assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonResponseReader.readUtf8(body, 128)
        }

        assertEquals(128L, error.maxBytes)
        assertEquals(129L, error.observedOrDeclaredBytes)
    }

    @Test
    fun missingContentLengthStillStopsAtByteLimit() {
        val body = unknownLengthBody("x".repeat(1_024))

        val error = assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonResponseReader.readUtf8(body, 64)
        }

        assertEquals(65L, error.observedOrDeclaredBytes)
    }

    @Test
    fun incorrectLowContentLengthStillStopsAtByteLimit() {
        val body = declaredLengthBody("x".repeat(1_024), declaredLength = 1L)

        val error = assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonResponseReader.readUtf8(body, 64)
        }

        assertEquals(65L, error.observedOrDeclaredBytes)
    }

    @Test
    fun emptyBodyHasExplicitFailure() {
        assertThrows(XtreamJsonGuardException.EmptyBody::class.java) {
            BoundedJsonResponseReader.readUtf8("".toResponseBody(JSON), 1_024)
        }
    }

    @Test
    fun malformedJsonHasTypedFailure() {
        assertThrows(XtreamJsonGuardException.InvalidJson::class.java) {
            XtreamJsonParser.parseObject("{broken")
        }
    }

    @Test
    fun wrongJsonRootHasTypedFailure() {
        assertThrows(XtreamJsonGuardException.InvalidJson::class.java) {
            XtreamJsonParser.parseObject("[]")
        }
        assertThrows(XtreamJsonGuardException.InvalidJson::class.java) {
            XtreamJsonParser.parseArray("{}")
        }
    }

    @Test
    fun htmlErrorPageIsNotAcceptedAsCatalogJson() {
        assertThrows(XtreamJsonGuardException.InvalidJson::class.java) {
            XtreamJsonParser.parseArray("<html><body>blocked</body></html>")
        }
    }

    @Test
    fun tooManyUniqueCatalogItemsAreRejected() {
        val array = JSONArray(
            """[
                {"stream_id":1},
                {"stream_id":2},
                {"stream_id":3}
            ]""".trimIndent(),
        )

        assertThrows(XtreamJsonGuardException.TooManyItems::class.java) {
            array.requireUniqueObjectLimit(2, "test catalog") { item ->
                item.optString("stream_id").toIntOrNull()?.toString()
            }
        }
    }

    @Test
    fun duplicateIdsDoNotConsumeUniqueItemLimit() {
        val array = JSONArray()
        repeat(1_000) { array.put(JSONObject().put("stream_id", 1)) }
        array.put(JSONObject().put("stream_id", 2))
        val seen = mutableListOf<Int>()

        array.forEachUniqueObject(2, "test catalog", { item ->
            item.optString("stream_id").toIntOrNull()?.toString()
        }) { item ->
            seen += item.getInt("stream_id")
        }

        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun seriesEpisodeLimitIsEnforced() {
        val episodes = JSONArray()
        repeat(XtreamJsonLimits.MAX_SERIES_EPISODES + 1) { index ->
            episodes.put(JSONObject().put("id", index + 1))
        }
        val root = JSONObject().put("episodes", JSONObject().put("1", episodes))

        val error = assertThrows(XtreamJsonGuardException.TooManyItems::class.java) {
            root.boundedSeriesEpisodeObjects()
        }

        assertEquals(XtreamJsonLimits.MAX_SERIES_EPISODES, error.maxItems)
    }

    @Test
    fun nestedSeriesSeasonLimitIsEnforced() {
        val episodes = JSONObject()
        repeat(XtreamJsonLimits.MAX_SERIES_SEASONS + 1) { index ->
            episodes.put(
                (index + 1).toString(),
                JSONArray().put(JSONObject().put("id", index + 1)),
            )
        }
        val root = JSONObject().put("episodes", episodes)

        val error = assertThrows(XtreamJsonGuardException.TooManyItems::class.java) {
            root.boundedSeriesEpisodeObjects()
        }

        assertEquals(XtreamJsonLimits.MAX_SERIES_SEASONS, error.maxItems)
    }

    @Test
    fun rootSeriesSeasonsArrayLimitIsEnforced() {
        val seasons = JSONArray()
        repeat(XtreamJsonLimits.MAX_SERIES_SEASONS + 1) { index ->
            seasons.put(JSONObject().put("season_number", index + 1))
        }
        val root = JSONObject().put("seasons", seasons).put("episodes", JSONObject())

        val error = assertThrows(XtreamJsonGuardException.TooManyItems::class.java) {
            root.boundedSeriesEpisodeObjects()
        }

        assertEquals(XtreamJsonLimits.MAX_SERIES_SEASONS, error.maxItems)
    }

    @Test
    fun hugeNestedSeriesBodyStopsAtByteCeilingBeforeJsonParse() {
        val nested = """{"episodes":{"1":[{"id":1,"info":{"plot":"${"x".repeat(4_096)}"}}]}}"""
        val body = unknownLengthBody(nested)

        val error = assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonResponseReader.readUtf8(body, 512)
        }

        assertEquals(513L, error.observedOrDeclaredBytes)
    }

    @Test
    fun hugeHttpErrorBodyUsesIndependentSmallLimit() {
        val response = response(
            code = 500,
            body = "x".repeat(XtreamJsonLimits.MAX_ERROR_BODY_BYTES.toInt() + 1).toResponseBody(TEXT),
        )

        val error = assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonResponseReader.readResponse(response, XtreamJsonLimits.CATALOG)
        }

        assertEquals(XtreamJsonLimits.MAX_ERROR_BODY_BYTES, error.maxBytes)
    }

    @Test
    fun normalSeriesProviderPayloadPreservesSeasonAndEpisodeData() {
        val root = XtreamJsonParser.parseObject(
            """{
                "info":{"name":"Series"},
                "episodes":{
                    "1":[{"id":"101","season":"1","episode_num":"1"}],
                    "2":[{"id":"201","season":"2","episode_num":"1"}]
                }
            }""".trimIndent(),
        )

        val episodes = root.boundedSeriesEpisodeObjects()

        assertEquals(2, episodes.size)
        assertEquals("1", episodes[0].seasonKey)
        assertEquals("101", episodes[0].episode.getString("id"))
        assertEquals("2", episodes[1].seasonKey)
    }

    @Test
    fun transparentGzipIsBoundedByDecompressedJsonBytes() {
        val payload = """{"value":"${"x".repeat(32_000)}"}"""
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { sink ->
            sink.writeUtf8(payload)
        }
        val mockServer = MockWebServer().also {
            server = it
            it.start()
        }
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Encoding", "gzip")
                .setBody(compressed),
        )

        val response = OkHttpClient().newCall(
            Request.Builder().url(mockServer.url("/player_api.php")).build(),
        ).execute()

        response.use {
            assertTrue(it.body?.contentLength() == -1L)
            assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
                BoundedJsonResponseReader.readUtf8(it.body, 1_024)
            }
        }
    }

    private fun unknownLengthBody(text: String): ResponseBody = declaredLengthBody(text, -1L)

    private fun declaredLengthBody(text: String, declaredLength: Long): ResponseBody = object : ResponseBody() {
        private val buffer = Buffer().writeUtf8(text)

        override fun contentType() = TEXT

        override fun contentLength(): Long = declaredLength

        override fun source(): BufferedSource = buffer
    }

    private fun response(code: Int, body: ResponseBody): Response = Response.Builder()
        .request(Request.Builder().url("http://localhost/player_api.php").build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .message("test")
        .code(code)
        .body(body)
        .build()

    private companion object {
        val JSON = "application/json".toMediaType()
        val TEXT = "text/plain".toMediaType()
    }
}

package sa.hulksa.player.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import sa.hulksa.player.model.ContentType

class XtreamCatalogItemReaderTest {
    private val client = XtreamClient()

    @Test
    fun movieFieldsMatchLegacyParser() {
        val json = """
            [
              {
                "num": 1,
                "name": "Movie One",
                "stream_id": 101,
                "stream_icon": "https://cdn.example/poster.jpg",
                "rating": "7.2",
                "rating_5based": 3.6,
                "year": "2024",
                "added": "1700000000",
                "category_id": "12",
                "container_extension": "mp4",
                "plot": "Plot",
                "genre": "Drama",
                "backdrop_path": ["https://cdn.example/back.jpg", "https://cdn.example/back2.jpg"]
              },
              {
                "stream_id": "102",
                "name": "Movie Two",
                "stream_icon": "/relative/poster.jpg",
                "rating": 8.1,
                "rating_5based": "4.1",
                "year": 2023,
                "added": 1700000001,
                "category_id": 13,
                "container_extension": null,
                "plot": null,
                "genre": "Action",
                "backdrop_path": "[\"/string-back.jpg\"]"
              }
            ]
        """.trimIndent()

        assertEquivalent(json, ContentType.MOVIE)
    }

    @Test
    fun seriesFieldsMatchLegacyParser() {
        val json = """
            [
              {
                "series_id": 201,
                "name": "Series One",
                "cover": "/cover.jpg",
                "rating": "6.5",
                "rating_5based": 3.2,
                "year": "2022",
                "last_modified": "1700000002",
                "added": "1690000000",
                "category_id": "5",
                "plot": "Series plot",
                "genre": "Drama",
                "backdrop_path": ["/b1.jpg", "/b2.jpg"]
              },
              {
                "series_id": "202",
                "name": "Series Two",
                "cover": "https://cdn.example/cover2.jpg",
                "rating": null,
                "rating_5based": 4.0,
                "last_modified": null,
                "added": 1690000001,
                "category_id": 6
              }
            ]
        """.trimIndent()

        assertEquivalent(json, ContentType.SERIES)
    }

    @Test
    fun liveFieldsMatchLegacyParser() {
        val json = """
            [
              {
                "stream_id": 301,
                "name": "Channel One",
                "stream_icon": "https://cdn.example/live.png",
                "epg_channel_id": "epg-1",
                "added": "1700000003",
                "category_id": "7",
                "container_extension": "m3u8",
                "tv_archive": 1
              },
              {
                "stream_id": "302",
                "name": "Channel Two",
                "stream_icon": null,
                "epg_channel_id": null,
                "added": null
              }
            ]
        """.trimIndent()

        assertEquivalent(json, ContentType.LIVE)
    }

    @Test
    fun androidPlatformNullNumericAndQuotedSemantics() {
        val fixture = """[{"stream_id":1,"name":null,"category_id":1e3,"rating":4.50}]"""
        val item = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(fixture),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("null", item.name)
        assertEquals("1000.0", item.categoryId)
        assertEquals("4.5", item.rating)

        val quoted = """[{"stream_id":2,"name":"null","category_id":"1e3","rating":"4.50"}]"""
        val quotedItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(quoted),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("null", quotedItem.name)
        assertEquals("1e3", quotedItem.categoryId)
        assertEquals("4.50", quotedItem.rating)

        val missing = """[{"stream_id":3}]"""
        val missingItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(missing),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("بدون اسم", missingItem.name)
        assertEquals("0", missingItem.categoryId)
        assertEquals(null, missingItem.rating)

        val nulls = """[{"stream_id":4,"name":null,"category_id":null,"rating":null,"rating_5based":4.5}]"""
        val nullItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(nulls),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("null", nullItem.name)
        assertEquals("null", nullItem.categoryId)
        assertEquals("4.5", nullItem.rating)
    }

    @Test
    fun androidPlatformNumberCoercionMatchesAosp() {
        val json = """
            [
              {"stream_id": 10, "category_id": 1e3},
              {"stream_id": 11, "category_id": 4.50},
              {"stream_id": 12, "category_id": 1.0},
              {"stream_id": 13, "category_id": 01},
              {"stream_id": 14, "category_id": 0x10},
              {"stream_id": 15, "category_id": +1},
              {"stream_id": 16, "category_id": 1.},
              {"stream_id": 17, "category_id": .5},
              {"stream_id": 18, "category_id": 1e},
              {"stream_id": 19, "category_id": -},
              {"stream_id": 20, "category_id": 08}
            ]
        """.trimIndent()
        val values = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(json),
            ContentType.MOVIE,
            PORTAL,
        ).map { it.categoryId }
        assertEquals(
            listOf("1000.0", "4.5", "1.0", "1", "16", "1", "1.0", "0.5", "1e", "-", "8.0"),
            values,
        )
    }

    @Test
    fun androidPlatformBomAndLegacySyntaxAreAccepted() {
        val bom = "\uFEFF[{\"stream_id\":21,\"name\":\"BOM\"}]"
        assertEquals(
            "BOM",
            client.parseCatalogItemsStreaming(Buffer().writeUtf8(bom), ContentType.MOVIE, PORTAL).single().name,
        )

        val legacy = "\uFEFF// comment\n[{'stream_id':22,'name':'single','category_id':1e3},]"
        val legacyItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(legacy),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("single", legacyItem.name)
        assertEquals("1000.0", legacyItem.categoryId)

        val unquoted = """[{stream_id:23; name:"unquoted"; category_id=1e3}]"""
        val unquotedItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(unquoted),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("unquoted", unquotedItem.name)
        assertEquals("1000.0", unquotedItem.categoryId)
    }

    @Test
    fun androidReferenceRawTextAcceptRejectFixtures() {
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,}]""")

        val omitted = parseIds("""[{"stream_id":1},,]""")
        assertEquals(listOf(1), omitted)

        assertGuard<XtreamJsonGuardException.InvalidJson>("""[}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"rating":1e309}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"unused":1e309}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"meta":{"x":1e309}}]""")

        assertEquals(listOf(1), parseIds("""[1e309,{"stream_id":1}]"""))
        assertEquals(listOf(1), parseIds("""[{"stream_id":1,"meta":[1e309]}]"""))
        assertEquals(listOf(1), parseIds("""[{"stream_id":1,"meta":{"a":[1e309]}}]"""))
        assertEquals(
            "بدون اسم",
            client.parseCatalogItemsStreaming(
                Buffer().writeUtf8("""[{"stream_id":1,"name":[1e309]}]"""),
                ContentType.MOVIE,
                PORTAL,
            ).single().name,
        )

        val commentedNested = """[{stream_id:1,meta:{/* } */a:1},name:'one'}]"""
        val commentedItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(commentedNested),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals(1, commentedItem.id)
        assertEquals("one", commentedItem.name)

        val legacyEscape = """[{"stream_id":1,"name":"a\qb"}]"""
        val escapedItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(legacyEscape),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("aqb", escapedItem.name)

        val quoteInLiteral = """[{"stream_id":1,"name":[a"b]}]"""
        val quoteItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(quoteInLiteral),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("""["a\"b"]""", quoteItem.name)

        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"name":[a/*x*/b]}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"name":[1/*x*/2]}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{"stream_id":1,"unused":/x}]""")
        assertGuard<XtreamJsonGuardException.InvalidJson>("""[{stream_id:1,{:foo}]""")

        val optionalArrow = """[{stream_id:>1,name:"one"}]"""
        val arrowItem = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(optionalArrow),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals(1, arrowItem.id)
        assertEquals("one", arrowItem.name)
    }

    @Test
    fun duplicatesAndInvalidIdsPreserveFirstValidOrder() {
        val json = """
            [
              {"stream_id": 1, "name": "one"},
              {"stream_id": "1", "name": "duplicate"},
              {"stream_id": "abc", "name": "invalid"},
              {"stream_id": 2, "name": "two"},
              {"name": "missing"},
              {"stream_id": 3, "name": "three"}
            ]
        """.trimIndent()

        assertEquivalent(json, ContentType.MOVIE)
        val streamed = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(json),
            ContentType.MOVIE,
            PORTAL,
        )
        assertEquals(listOf(1, 2, 3), streamed.map { it.id })
        assertEquals(listOf("one", "two", "three"), streamed.map { it.name })
    }

    @Test
    fun uniqueLimitRejectsTheNextUniqueButIgnoresDuplicatesAndInvalidIds() {
        val duplicateHeavy = buildString {
            append('[')
            repeat(1_000) { append("""{"stream_id":1},""") }
            append("""{"stream_id":"invalid"},""")
            append("""{"stream_id":2}]""")
        }
        BoundedJsonArrayItemReader(Buffer().writeUtf8(duplicateHeavy), 1_000_000L).use { reader ->
            val result = reader.readCatalogItems(
                type = ContentType.MOVIE,
                portalBaseUrl = PORTAL,
                maxUniqueItems = 2,
                scope = "test",
            )
            assertEquals(listOf(1, 2), result.map { it.id })
        }

        val threeUnique = """[{"stream_id":1},{"stream_id":2},{"stream_id":3}]"""
        BoundedJsonArrayItemReader(Buffer().writeUtf8(threeUnique), 1_000_000L).use { reader ->
            assertThrows(XtreamJsonGuardException.TooManyItems::class.java) {
                reader.readCatalogItems(
                    type = ContentType.MOVIE,
                    portalBaseUrl = PORTAL,
                    maxUniqueItems = 2,
                    scope = "test",
                )
            }
        }
    }

    @Test
    fun unicodeEscapesAndNestedFieldsMatchLegacyParser() {
        val json = """
            [
              {
                "stream_id": 1,
                "name": "فيلم \"خاص\" 🎬",
                "plot": "سطر\nجديد",
                "genre": "دراما",
                "backdrop_path": ["", "https://cdn.example/back.jpg"],
                "meta": {"nested": [1, {"x": "y"}], "quoted": "a,b]"}
              },
              {
                "stream_id": 2,
                "name": "Escaped \\ slash",
                "backdrop_path": "[\"\u0645\u0631\u062d\u0628\u0627\"]"
              }
            ]
        """.trimIndent()

        assertEquivalent(json, ContentType.MOVIE)
    }

    @Test
    fun omittedBackdropSlotIsSkippedWhileExplicitNullStopsSelection() {
        val omitted = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8("""[{"stream_id":1,"backdrop_path":[,"/ok.jpg"]}]"""),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("https://portal.example/ok.jpg", omitted.backdropUrl)

        val explicitNull = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8("""[{"stream_id":2,"backdrop_path":[null,"/ok.jpg"]}]"""),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertNull(explicitNull.backdropUrl)

        val trailingOmitted = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8("""[{"stream_id":3,"backdrop_path":["/first.jpg",]}]"""),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("https://portal.example/first.jpg", trailingOmitted.backdropUrl)
    }

    @Test
    fun stringifiedBackdropArrayOnlyUsesElementZero() {
        val emptyFirst = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(
                """[{"stream_id":1,"backdrop_path":"[\"\",\"/ok.jpg\"]"}]""",
            ),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertNull(emptyFirst.backdropUrl)

        val omittedFirst = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(
                """[{"stream_id":2,"backdrop_path":"[,\"/ok.jpg\"]"}]""",
            ),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertNull(omittedFirst.backdropUrl)

        val validFirst = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8(
                """[{"stream_id":3,"backdrop_path":"[\"/ok.jpg\",\"\"]"}]""",
            ),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("https://portal.example/ok.jpg", validFirst.backdropUrl)
    }

    @Test
    fun formFeedTerminatesUnquotedLiteralsButStaysValidInsideQuotes() {
        assertGuard<XtreamJsonGuardException.InvalidJson>(
            "[{stream_id:1,category_id:1\u000C}]",
        )
        assertGuard<XtreamJsonGuardException.InvalidJson>(
            "[{stream_id:1,unused:x\u000C}]",
        )
        assertGuard<XtreamJsonGuardException.InvalidJson>(
            "[{stream_id:1\u000C}]",
        )

        val quoted = client.parseCatalogItemsStreaming(
            Buffer().writeUtf8("[{\"stream_id\":1,\"name\":\"a\u000Cb\"}]"),
            ContentType.MOVIE,
            PORTAL,
        ).single()
        assertEquals("a\u000Cb", quoted.name)
    }

    @Test
    fun generatedMixedCatalogMatchesLegacyParser() {
        val random = Random(20261106)
        val array = JSONArray()
        repeat(200) { index ->
            val item = JSONObject()
            val id = if (index % 17 == 0) "invalid-$index" else (index % 150 + 1).toString()
            item.put("stream_id", if (index % 3 == 0) id else id.toIntOrNull() ?: id)
            if (random.nextInt(6) != 0) item.put("name", "value ${random.nextInt(1000)}")
            if (random.nextInt(5) != 0) {
                item.put("category_id", if (random.nextBoolean()) random.nextInt(100) else "cat-${random.nextInt(20)}")
            }
            if (random.nextInt(4) != 0) {
                item.put("stream_icon", if (random.nextBoolean()) "https://cdn.example/p.jpg" else "/relative/p.jpg")
            }
            if (random.nextInt(3) != 0) item.put("rating", randomRating(random))
            if (random.nextInt(3) != 0) item.put("rating_5based", randomRating(random))
            if (random.nextInt(3) != 0) item.put("year", randomNullableIntOrText(random))
            if (random.nextInt(3) != 0) item.put("container_extension", randomNullableText(random))
            if (random.nextInt(3) != 0) item.put("epg_channel_id", randomNullableIntOrText(random))
            if (random.nextInt(3) != 0) item.put("added", randomNullableIntOrText(random))
            if (random.nextInt(3) != 0) item.put("last_modified", randomNullableIntOrText(random))
            if (random.nextInt(3) != 0) item.put("plot", randomNullableText(random))
            if (random.nextInt(3) != 0) item.put("genre", randomNullableText(random))
            if (random.nextInt(3) != 0) item.put("backdrop_path", randomBackdrop(random))
            item.put("unknown", JSONObject().put("nested", JSONArray().put(index).put("x,y")))
            array.put(item)
        }
        assertEquivalent(array.toString(), ContentType.MOVIE)
    }

    private fun randomRating(random: Random): Any = when (random.nextInt(4)) {
        0 -> JSONObject.NULL
        1 -> "4.5"
        2 -> 4.5
        else -> 7
    }

    private fun randomNullableText(random: Random): Any = when (random.nextInt(3)) {
        0 -> JSONObject.NULL
        1 -> "value ${random.nextInt(1000)}"
        else -> "text"
    }

    private fun randomNullableIntOrText(random: Random): Any = when (random.nextInt(4)) {
        0 -> JSONObject.NULL
        1 -> random.nextInt(1000)
        2 -> "value ${random.nextInt(1000)}"
        else -> "2024"
    }

    private fun randomBackdrop(random: Random): Any = when (random.nextInt(5)) {
        0 -> JSONArray().put("https://cdn.example/back.jpg").put("second.jpg")
        1 -> JSONArray().put(JSONObject.NULL).put("")
        2 -> """["https://cdn.example/string.jpg"]"""
        3 -> "/relative/back.jpg"
        else -> JSONObject.NULL
    }

    @Test
    fun emptyWrongRootAndTruncatedInputFailClosed() {
        assertGuard<XtreamJsonGuardException.EmptyBody>("")
        assertGuard<XtreamJsonGuardException.EmptyBody>("   \n\t ")
        assertGuard<XtreamJsonGuardException.InvalidJson>("{}")
        assertGuard<XtreamJsonGuardException.InvalidJson>("null")
        assertGuard<XtreamJsonGuardException.InvalidJson>("[")
        assertGuard<XtreamJsonGuardException.InvalidJson>("[{\"stream_id\":1}")
        assertGuard<XtreamJsonGuardException.InvalidJson>("[{\"stream_id\":1},")
        assertGuard<XtreamJsonGuardException.InvalidJson>("<html><body>blocked</body></html>")

        assertEquals(emptyList<Int>(), parseIds("[]"))
        assertEquals(emptyList<Int>(), parseIds("[1,2]"))
        assertEquals(listOf(1), parseIds("[{\"stream_id\":1},]"))
        assertEquals(listOf(1), parseIds("[{\"stream_id\":1}] trailing"))
    }

    @Test
    fun shortAndChunkBoundaryWhitespacePrefixesReachEof() {
        for (size in listOf(0, 1, 10, 4_095, 4_096, 4_097, 8_191, 8_192, 8_193, 65_535, 65_536)) {
            val body = " ".repeat(size)
            BoundedJsonArrayItemReader(Buffer().writeUtf8(body), 1_000_000L).use { reader ->
                assertNull("size=$size", reader.peekFirstNonWhitespace(maxSteps = 64))
            }
        }
    }

    @Test
    fun prefixScanAndDrainCancellationStops() {
        val body = " ".repeat(10_000)
        BoundedJsonArrayItemReader(Buffer().writeUtf8(body), 1_000_000L).use { reader ->
            assertThrows(CancellationException::class.java) {
                reader.peekFirstNonWhitespace(
                    checkCancelled = { throw CancellationException("cancelled") },
                    maxSteps = 64,
                )
            }
        }
        BoundedJsonArrayItemReader(Buffer().writeUtf8(body), 1_000_000L).use { reader ->
            assertThrows(CancellationException::class.java) {
                reader.readChallengePrefix { throw CancellationException("cancelled") }
            }
        }
        BoundedJsonArrayItemReader(Buffer().writeUtf8(body), 1_000_000L).use { reader ->
            assertThrows(CancellationException::class.java) {
                reader.drain { throw CancellationException("cancelled") }
            }
        }
    }

    @Test
    fun declaredAndUnknownLengthBoundariesAreBounded() {
        val json = """[{"stream_id":1,"name":"one"}]"""
        val bytes = json.toByteArray(Charsets.UTF_8).size.toLong()

        BoundedJsonArrayItemReader(Buffer().writeUtf8(json), bytes).use { reader ->
            assertEquals(1, readIds(reader).size)
        }
        assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonArrayItemReader(Buffer().writeUtf8(json), bytes - 1L).use { reader ->
                readIds(reader)
            }
        }
        assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
            BoundedJsonArrayItemReader(
                Buffer().writeUtf8(json),
                maxBytes = bytes - 1L,
                declaredLength = bytes,
            ).use { reader ->
                readIds(reader)
            }
        }
    }

    @Test
    fun transparentGzipUsesDecompressedByteCeiling() {
        val payload = """[{"stream_id":1,"name":"${"x".repeat(32_000)}"}]"""
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { sink ->
            sink.writeUtf8(payload)
        }
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Content-Encoding", "gzip")
                    .setBody(compressed),
            )
            val response = OkHttpClient().newCall(
                Request.Builder().url(server.url("/player_api.php")).build(),
            ).execute()
            response.use {
                val body = it.body!!
                assertTrue(body.contentLength() == -1L)
                assertThrows(XtreamJsonGuardException.PayloadTooLarge::class.java) {
                    BoundedJsonArrayItemReader(
                        body.source(),
                        maxBytes = 1_024L,
                        declaredLength = body.contentLength(),
                    ).use { reader ->
                        readIds(reader)
                    }
                }
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun cancellationAfterItemsDoesNotReturnAPartialList() {
        val json = buildString {
            append('[')
            repeat(300) { index ->
                if (index > 0) append(',')
                append("""{"stream_id":$index}""")
            }
            append(']')
        }
        BoundedJsonArrayItemReader(Buffer().writeUtf8(json), 1_000_000L).use { reader ->
            var checks = 0
            assertThrows(CancellationException::class.java) {
                reader.readCatalogItems(
                    type = ContentType.MOVIE,
                    portalBaseUrl = PORTAL,
                    maxUniqueItems = 10_000,
                    scope = "test",
                    checkCancelled = {
                        checks += 1
                        if (checks >= 1) throw CancellationException("cancelled")
                    },
                )
            }
            assertEquals(1, checks)
        }
    }

    private fun assertEquivalent(json: String, type: ContentType) {
        val legacy = client.parseItems(JSONArray(json), type, PORTAL)
        val streamed = client.parseCatalogItemsStreaming(Buffer().writeUtf8(json), type, PORTAL)
        assertEquals(legacy, streamed)
    }

    private fun assertGuard(errorType: Class<out Throwable>, json: String) {
        assertThrows("raw=$json", errorType) {
            client.parseCatalogItemsStreaming(Buffer().writeUtf8(json), ContentType.MOVIE, PORTAL)
        }
    }

    private inline fun <reified T : Throwable> assertGuard(json: String) {
        assertGuard(T::class.java, json)
    }

    private fun parseIds(json: String): List<Int> =
        client.parseCatalogItemsStreaming(Buffer().writeUtf8(json), ContentType.MOVIE, PORTAL)
            .map { it.id }

    private fun readIds(reader: BoundedJsonArrayItemReader): List<Int> =
        reader.readCatalogItems(
            type = ContentType.MOVIE,
            portalBaseUrl = PORTAL,
            maxUniqueItems = 10,
            scope = "test",
        ).map { it.id }

    private companion object {
        const val PORTAL = "https://portal.example/base"
    }
}

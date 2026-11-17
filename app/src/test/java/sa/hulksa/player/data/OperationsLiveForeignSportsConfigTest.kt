package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationsLiveForeignSportsConfigTest {
    @Test
    fun `name-based override parses ordered aliases without host or stream ids`() {
        val parsed = parseOperationsConfig(
            configJson(
                """
                "liveForeignSports":{
                  "channelAliases":[
                    "WS TNT Sports 1",
                    "WS TNT Sports 2",
                    "WS Sky Sports Main Event"
                  ]
                }
                """,
            ),
        )

        assertNotNull(parsed)
        assertEquals(
            listOf("WS TNT Sports 1", "WS TNT Sports 2", "WS Sky Sports Main Event"),
            parsed!!.liveForeignSports?.channelAliases,
        )
        assertEquals(1, parsed.schemaVersion)
    }

    @Test
    fun `empty alias list is a valid explicit hide`() {
        val parsed = parseOperationsConfig(
            configJson(""""liveForeignSports":{"channelAliases":[]}"""),
        )

        assertNotNull(parsed)
        assertEquals(emptyList<String>(), parsed!!.liveForeignSports?.channelAliases)
    }

    @Test
    fun `whitespace-only aliases normalize to a valid explicit hide`() {
        val parsed = parseOperationsConfig(
            configJson(""""liveForeignSports":{"channelAliases":["","   "]}"""),
        )

        assertNotNull(parsed)
        assertEquals(emptyList<String>(), parsed!!.liveForeignSports?.channelAliases)
    }

    @Test
    fun `missing malformed or old host-based fields never reject the Operations payload`() {
        val payloads = listOf(
            configJson(""),
            configJson(""""liveForeignSports":{"channelAliases":"not-an-array"}"""),
            configJson(
                """
                "liveForeignSports":{
                  "sources":[{"sourceIdentity":"megasstore2020.online","channelIds":[1]}]
                }
                """,
            ),
        )

        for (payload in payloads) {
            val parsed = parseOperationsConfig(payload)
            assertNotNull(payload, parsed)
            assertNull(payload, parsed!!.liveForeignSports)
            assertEquals(1, parsed.schemaVersion)
        }
    }

    @Test
    fun `the verified bundled list is complete and contains exact aliases`() {
        assertEquals(233, BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES.size)
        assertTrue("WS TNT Sports 1" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue(
            "UK: TNT SPORTS 1 ᴴᴰ " in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES,
        )
        assertTrue(
            "DE: SKY SPORT AUSTRIA 7 ᴴᴰ (720P)" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES,
        )
        assertTrue("TR: TABII SPORT 1 ᴿᴬᵂ" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("ES M LIGA DE CAMPEONES 13 HD" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("PT| DAZN SPORT 6 FHD" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("Ziggo Sport Voetbal NL" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("NL: ESPN 1 ᴿᴬᵂ " in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("ESPN 4 HD NL" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("PL: ELEVEN SPORTS 1 ᴴᴰ" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("PL ELEVEN SPORTS 4 FHD" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("PL CANAL EXTRA 07" in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("RO| PRIMA SPORT HD" !in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("TRT 1" !in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("ES DAZN LA LIGA 2 FHD" !in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("RO| DIGI SPORT 1 FHD" !in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
        assertTrue("RO| DIGI SPORT 2 FHD" !in BUNDLED_LIVE_FOREIGN_SPORTS_ALIASES)
    }

    private fun configJson(extra: String): String {
        val optional = extra.trim().takeIf(String::isNotEmpty)?.let { ",\n$it" }.orEmpty()
        return """
            {
              "schemaVersion":1,
              "generatedAt":1770000000,
              "service":{"status":"OPERATIONAL"},
              "update":{
                "latestVersionCode":64,
                "latestVersionName":"0.9.3.20",
                "minimumSupportedVersionCode":64,
                "apkUrl":null,
                "apkSha256":null
              },
              "announcements":[],
              "features":{}$optional
            }
        """.trimIndent()
    }
}

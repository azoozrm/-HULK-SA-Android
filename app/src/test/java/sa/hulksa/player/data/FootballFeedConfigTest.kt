package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FootballFeedConfigTest {
    @Test
    fun `absent football discovery disables the section without changing operations`() {
        val parsed = checkNotNull(parseOperationsConfig(configJson()))

        assertNull(parsed.football)
        assertEquals(65, parsed.update.latestVersionCode)
        assertEquals(true, parsed.features.downloadsEnabled)
    }

    @Test
    fun `valid football discovery is canonical and bounded`() {
        val parsed = checkNotNull(
            parseOperationsConfig(
                configJson(
                    """"football":{"enabled":true,"feedUrl":"https://NEO4KSa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
                ),
            ),
        )

        assertNotNull(parsed.football)
        assertEquals(
            "https://neo4ksa.com/hulk-operations/api/app/v1/sports/",
            parsed.football?.feedUrl,
        )
        assertEquals(60, parsed.football?.refreshSeconds)
    }

    @Test
    fun `missing trailing slash is normalized to the reviewed route`() {
        val parsed = checkNotNull(
            parseOperationsConfig(
                configJson(
                    """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports","refreshSeconds":120},""",
                ),
            ),
        )

        assertEquals(
            "https://neo4ksa.com/hulk-operations/api/app/v1/sports/",
            parsed.football?.feedUrl,
        )
    }

    @Test
    fun `unsafe or malformed football discovery disables only the section`() {
        val invalidValues = listOf(
            """"football":{"enabled":false,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"http://neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://example.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/config/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com:8443/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://user@neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/?day=2026-11-19","refreshSeconds":60},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":10},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/","refreshSeconds":900},""",
            """"football":{"enabled":true,"feedUrl":"https://neo4ksa.com/hulk-operations/api/app/v1/sports/"},""",
            """"football":"malformed",""",
        )

        invalidValues.forEach { football ->
            val parsed = checkNotNull(parseOperationsConfig(configJson(football)))
            assertNull(parsed.football)
            assertEquals(65, parsed.update.latestVersionCode)
            assertEquals(true, parsed.features.downloadsEnabled)
        }
    }

    private fun configJson(football: String = "") = """
        {
          "schemaVersion":1,
          "generatedAt":1770000000,
          $football
          "service":{"status":"OPERATIONAL","message":null},
          "update":{
            "latestVersionCode":65,
            "latestVersionName":"0.9.3.21",
            "minimumSupportedVersionCode":64,
            "updateType":"OPTIONAL",
            "apkUrl":"https://neo4ksa.com/hulk-operations/releases/hulk-sa-65.apk",
            "apkSha256":"${"a".repeat(64)}",
            "releaseNotes":""
          },
          "announcements":[],
          "features":{"downloads_enabled":true}
        }
    """.trimIndent()
}

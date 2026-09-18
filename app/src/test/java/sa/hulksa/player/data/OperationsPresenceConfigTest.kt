package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationsPresenceConfigTest {
    @Test
    fun `absent presence is disabled without changing operations parsing`() {
        val parsed = checkNotNull(parseOperationsConfig(configJson()))

        assertNull(parsed.presence)
        assertEquals(OperationsServiceStatus.OPERATIONAL, parsed.service.status)
        assertEquals(65, parsed.update.latestVersionCode)
    }

    @Test
    fun `valid enabled discovery is accepted`() {
        val parsed = checkNotNull(
            parseOperationsConfig(
                configJson(
                    """"presence":{
                      "enabled":true,
                      "baseUrl":"https://hulksa.com/control-center/api/app/v1/presence/",
                      "heartbeatSeconds":60,
                      "onlineTtlSeconds":180
                    },""",
                ),
            ),
        )

        assertEquals(
            OperationsPresenceConfig(
                baseUrl = "https://hulksa.com/control-center/api/app/v1/presence/",
                heartbeatSeconds = 60,
                onlineTtlSeconds = 180,
            ),
            parsed.presence,
        )
    }

    @Test
    fun `disabled discovery is a no-op`() {
        val parsed = checkNotNull(
            parseOperationsConfig(configJson(""""presence":{"enabled":false},""")),
        )

        assertNull(parsed.presence)
    }

    @Test
    fun `invalid discovery fails closed without invalidating unrelated config`() {
        val invalidValues = listOf(
            """"presence":{"enabled":true,"baseUrl":"http://hulksa.com/control-center/api/app/v1/presence/","heartbeatSeconds":60,"onlineTtlSeconds":180},""",
            """"presence":{"enabled":true,"baseUrl":"https://example.com/control-center/api/app/v1/presence/","heartbeatSeconds":60,"onlineTtlSeconds":180},""",
            """"presence":{"enabled":true,"baseUrl":"https://hulksa.com/api/presence/","heartbeatSeconds":60,"onlineTtlSeconds":180},""",
            """"presence":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/presence/","heartbeatSeconds":14,"onlineTtlSeconds":180},""",
            """"presence":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/presence/","heartbeatSeconds":60,"onlineTtlSeconds":119},""",
            """"presence":"malformed",""",
        )

        invalidValues.forEach { presence ->
            val parsed = checkNotNull(parseOperationsConfig(configJson(presence)))
            assertNull(parsed.presence)
            assertEquals(65, parsed.update.latestVersionCode)
            assertEquals(true, parsed.features.downloadsEnabled)
        }
    }

    private fun configJson(presence: String = "") = """
        {
          "schemaVersion":1,
          "generatedAt":1770000000,
          $presence
          "service":{"status":"OPERATIONAL","message":null},
          "update":{
            "latestVersionCode":65,
            "latestVersionName":"0.9.3.21",
            "minimumSupportedVersionCode":64,
            "updateType":"OPTIONAL",
            "apkUrl":"https://hulksa.com/hulk-operations/releases/hulk-sa-65.apk",
            "apkSha256":"${"a".repeat(64)}",
            "releaseNotes":""
          },
          "announcements":[],
          "features":{"downloads_enabled":true}
        }
    """.trimIndent()
}

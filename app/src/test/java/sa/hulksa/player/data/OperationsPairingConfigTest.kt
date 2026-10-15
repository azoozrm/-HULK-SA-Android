package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationsPairingConfigTest {
    @Test
    fun `absent pairing is disabled without changing operations parsing`() {
        val parsed = checkNotNull(parseOperationsConfig(configJson()))

        assertNull(parsed.pairing)
        assertEquals(OperationsServiceStatus.OPERATIONAL, parsed.service.status)
        assertEquals(65, parsed.update.latestVersionCode)
        assertEquals(true, parsed.features.downloadsEnabled)
    }

    @Test
    fun `disabled pairing is a no-op`() {
        val parsed = checkNotNull(
            parseOperationsConfig(configJson(""""pairing":{"enabled":false,"baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing/"},""")),
        )

        assertNull(parsed.pairing)
    }

    @Test
    fun `valid enabled discovery is accepted canonically`() {
        val parsed = checkNotNull(
            parseOperationsConfig(
                configJson(""""pairing":{"enabled":true,"baseUrl":"https://HULKSa.com/control-center/api/app/v1/pairing/"},"""),
            ),
        )

        assertEquals(
            OperationsPairingConfig(baseUrl = "https://hulksa.com/control-center/api/app/v1/pairing/"),
            parsed.pairing,
        )
    }

    @Test
    fun `invalid or unsafe discovery disables only pairing`() {
        val invalidValues = listOf(
            """"pairing":{"enabled":true,"baseUrl":"http://hulksa.com/control-center/api/app/v1/pairing/"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://example.com/control-center/api/app/v1/pairing/"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/presence/"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://user@hulksa.com/control-center/api/app/v1/pairing/"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://hulksa.com:8443/control-center/api/app/v1/pairing/"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing/?x=1"},""",
            """"pairing":{"enabled":true,"baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing/#f"},""",
            """"pairing":{"enabled":"true","baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing/"},""",
            """"pairing":{"baseUrl":"https://hulksa.com/control-center/api/app/v1/pairing/"},""",
            """"pairing":"malformed",""",
        )

        invalidValues.forEach { pairing ->
            val parsed = checkNotNull(parseOperationsConfig(configJson(pairing)))
            assertNull(parsed.pairing)
            assertEquals(65, parsed.update.latestVersionCode)
            assertEquals(true, parsed.features.downloadsEnabled)
        }
    }

    private fun configJson(pairing: String = "") = """
        {
          "schemaVersion":1,
          "generatedAt":1770000000,
          $pairing
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

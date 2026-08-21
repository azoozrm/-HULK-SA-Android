package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagPolicyTest {
    @Test
    fun knownFlagOverridesSafeDefault() {
        val flags = OperationsFeatureFlags.fromRemote(mapOf("downloads_enabled" to false))

        assertFalse(flags.downloadsEnabled)
        assertTrue(flags.episodeNotificationsEnabled)
    }

    @Test
    fun unknownFlagIsIgnoredByParser() {
        val parsed = parseOperationsConfig(configJson("\"remote_code\":false"))

        assertNotNull(parsed)
        assertTrue(parsed!!.features.downloadsEnabled)
        assertTrue(parsed.features.liveTvProEnabled)
    }

    @Test
    fun missingFlagsUseSafeLocalDefaults() {
        val parsed = parseOperationsConfig(configJson(""))

        assertNotNull(parsed)
        assertTrue(parsed!!.features.downloadsEnabled)
        assertTrue(parsed.features.episodeNotificationsEnabled)
        assertTrue(parsed.features.smartRecommendationsEnabled)
        assertTrue(parsed.features.liveTvProEnabled)
    }

    @Test
    fun apiFailureUsesSafeLocalDefaults() {
        val defaults = OperationsFeatureFlags()

        assertTrue(defaults.downloadsEnabled)
        assertTrue(defaults.episodeNotificationsEnabled)
        assertTrue(defaults.smartRecommendationsEnabled)
        assertTrue(defaults.liveTvProEnabled)
    }

    private fun configJson(features: String): String {
        val featureBody = features.takeIf(String::isNotBlank).orEmpty()
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
              "features":{$featureBody}
            }
        """.trimIndent()
    }
}

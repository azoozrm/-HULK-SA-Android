package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun currentEqualLatestDoesNotPrompt() {
        assertEquals(
            OperationsUpdateDecision.NONE,
            evaluateOperationsUpdatePolicy(65, update(), OperationsConfigSource.NETWORK, 0L),
        )
    }

    @Test
    fun currentBelowLatestButSupportedIsOptional() {
        assertEquals(
            OperationsUpdateDecision.OPTIONAL,
            evaluateOperationsUpdatePolicy(64, update(), OperationsConfigSource.NETWORK, 0L),
        )
    }

    @Test
    fun currentBelowMinimumIsRequiredFromFreshNetwork() {
        assertEquals(
            OperationsUpdateDecision.REQUIRED,
            evaluateOperationsUpdatePolicy(63, update(), OperationsConfigSource.NETWORK, 0L),
        )
    }

    @Test
    fun malformedConfigIsRejected() {
        assertNull(parseOperationsConfig("{not-json"))
        assertNull(
            parseOperationsConfig(
                validConfigJson().replace("\"latestVersionCode\":65", "\"latestVersionCode\":0"),
            ),
        )
    }

    @Test
    fun unavailableApiWithoutCacheFailsOpen() {
        assertEquals(
            OperationsUpdateDecision.NONE,
            evaluateOperationsUpdatePolicy(63, null, OperationsConfigSource.DEFAULT, Long.MAX_VALUE),
        )
    }

    @Test
    fun recentCachedConfigCanPreserveRequiredUpdate() {
        assertEquals(
            OperationsUpdateDecision.REQUIRED,
            evaluateOperationsUpdatePolicy(
                63,
                update(),
                OperationsConfigSource.CACHE,
                OPERATIONS_MAX_FORCED_CACHE_AGE_MS - 1L,
            ),
        )
    }

    @Test
    fun staleCachedForcedUpdateIsDowngradedToOptional() {
        assertEquals(
            OperationsUpdateDecision.OPTIONAL,
            evaluateOperationsUpdatePolicy(
                63,
                update(),
                OperationsConfigSource.CACHE,
                OPERATIONS_MAX_FORCED_CACHE_AGE_MS + 1L,
            ),
        )
    }

    @Test
    fun offHostApkUrlIsNotInstallable() {
        assertEquals(
            OperationsUpdateDecision.NONE,
            evaluateOperationsUpdatePolicy(
                63,
                update().copy(apkUrl = "https://example.com/hulk-sa-65.apk"),
                OperationsConfigSource.NETWORK,
                0L,
            ),
        )
    }

    @Test
    fun clockRollbackDoesNotTrustCachedForcedUpdate() {
        assertEquals(
            OperationsUpdateDecision.OPTIONAL,
            evaluateOperationsUpdatePolicy(
                63,
                update(),
                OperationsConfigSource.CACHE,
                -1L,
            ),
        )
    }

    private fun update() = OperationsUpdateConfig(
        latestVersionCode = 65,
        latestVersionName = "0.9.3.21",
        minimumSupportedVersionCode = 64,
        apkUrl = "https://hulksa.com/hulk-operations/releases/hulk-sa-65.apk",
        apkSha256 = "a".repeat(64),
        releaseNotes = "تحسينات تشغيلية",
    )

    private fun validConfigJson() = """
        {
          "schemaVersion":1,
          "generatedAt":1770000000,
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
          "features":{}
        }
    """.trimIndent()
}

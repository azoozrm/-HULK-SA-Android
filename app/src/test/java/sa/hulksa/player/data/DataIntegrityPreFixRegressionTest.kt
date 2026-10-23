package sa.hulksa.player.data

import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.OfflineDownload

class DataIntegrityPreFixRegressionTest {
    @Test
    fun `current format ownerless download is not silently bound to primary`() {
        val ownerless = OfflineDownload(
            downloadId = 9L,
            historyKey = "movie:9",
            title = "Synthetic 9",
            posterUrl = null,
            streamKind = "movie",
            streamId = 9,
            extension = "mp4",
            ownerProfileIds = emptySet(),
        )

        val restored = reconcilePersistedDownloadOwnership(
            downloads = listOf(ownerless),
            useLegacyOwnership = false,
            legacyOwners = { setOf("profile-1") },
        )

        assertTrue(
            "A current-format record without owners must not be reassigned to the current profile",
            restored.single().ownerProfileIds.isEmpty(),
        )
    }
}

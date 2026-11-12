package sa.hulksa.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R32 wiring contracts that connect the new policies to the production ViewModel. */
class CatalogStartupWiringTest {
    private val viewModel = File("src/main/java/sa/hulksa/player/HulkViewModel.kt").readText()

    @Test
    fun `fresh snapshot reuse is wired into the restore path and settles the scan gate`() {
        val restore = viewModel
            .substringAfter("private suspend fun restoreCachedCatalogsUnderPermit(")
            .substringBefore("private fun flushPendingCatalogNetwork(")
        assertTrue(restore.contains("CatalogSnapshotFreshness.isFresh("))
        assertTrue(restore.contains("catalogNetworkOrderAfterRestore("))
        assertTrue(restore.contains("freshCatalogTypes += next"))
        assertTrue(restore.contains("settleStartupScanPriority(activeSession, type)"))

        val freshnessBlock = restore.substringAfter("val networkOrder =")
        assertTrue(freshnessBlock.contains("freshTypes.forEach"))
    }

    @Test
    fun `bootstrap data never marks a full catalog fresh`() {
        val publishBootstrap = viewModel
            .substringAfter("private fun publishHomeBootstrap(")
            .substringBefore("private suspend fun publishCachedCatalog(")
        assertFalse(publishBootstrap.contains("freshCatalogTypes"))
        assertFalse(publishBootstrap.contains("freshTypes"))

        val restore = viewModel
            .substringAfter("private suspend fun restoreCachedCatalogsUnderPermit(")
            .substringBefore("private fun flushPendingCatalogNetwork(")
        assertTrue(restore.contains("repository.cachedCatalog("))
        assertTrue(restore.contains("snapshot = snapshot"))
    }

    @Test
    fun `kids and incomplete profile eligibility bypass the freshness reuse`() {
        val restore = viewModel
            .substringAfter("private suspend fun restoreCachedCatalogs(")
            .substringBefore("private suspend fun restoreCachedCatalogsUnderPermit(")
        assertTrue(restore.contains("canUseGeneralCatalogCache("))
        assertTrue(restore.contains("flushPendingCatalogNetwork(activeSession)"))
    }

    @Test
    fun `manual force refresh bypasses restore freshness`() {
        val ensure = viewModel
            .substringAfter("private fun ensureCatalog(")
            .substringBefore("private fun requestCatalogNetwork(")
        assertTrue(ensure.contains("requestCatalogNetwork(activeSession, type, force = true)"))
    }

    @Test
    fun `forced admission no longer clears the failure before publication`() {
        val request = viewModel
            .substringAfter("private fun requestCatalogNetwork(")
            .substringBefore("private fun startCacheRestore(")
        assertFalse(request.contains("catalogFailures - type"))
        assertFalse(request.contains("catalogFailures = app"))
        assertTrue(request.contains("catalogRequestOwner.request(activeSession, type, force)"))

        val loaded = viewModel
            .substringAfter("onLoaded = { type, result, remaining ->")
            .substringBefore("onFailure = { type, error, remaining ->")
        assertTrue(loaded.contains("state.catalogFailures - type"))
    }

    @Test
    fun `startup catalog and metadata dispatch share the new gate lifecycle`() {
        val begin = viewModel
            .substringAfter("private fun beginStartupScanPriority(")
            .substringBefore("private fun settleStartupScanPriority(")
        assertTrue(begin.contains("providerDispatchGate.beginRound(owner)"))

        val settle = viewModel
            .substringAfter("private fun settleStartupScanPriority(")
            .substringBefore("private fun enqueueNotificationScanTrigger(")
        assertTrue(settle.contains("providerDispatchGate.endRound(owner)"))
    }
}

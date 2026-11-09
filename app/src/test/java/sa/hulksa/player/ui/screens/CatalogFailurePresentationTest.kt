package sa.hulksa.player.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.CatalogFailure
import sa.hulksa.player.model.ContentType

class CatalogFailurePresentationTest {
    @Test
    fun `scoped Home copy keeps every genuine type failure visible`() {
        val copy = scopedCatalogFailureCopy(
            mapOf(
                ContentType.SERIES to CatalogFailure("الخدمة رفضت الاتصال مؤقتا.", retryable = true),
                ContentType.LIVE to CatalogFailure("انتهت مهلة الاتصال.", retryable = true),
            ),
        )

        assertEquals("تعذر تحديث بعض المحتوى", copy?.title)
        assertTrue(copy?.body?.contains("المسلسلات: الخدمة رفضت الاتصال مؤقتا.") == true)
        assertTrue(copy?.body?.contains("البث المباشر: انتهت مهلة الاتصال.") == true)
    }

    @Test
    fun `healthy Home has no scoped notice`() {
        assertNull(scopedCatalogFailureCopy(emptyMap()))
    }

    @Test
    fun `Home retry action uses only eligible failed types`() {
        val source = File("src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt").readText()
        val block = source
            .substringAfter("val homeRetryAction")
            .substringBefore("// One Home restoration owner")
        assertTrue(block.contains("retryableHomeFailures.forEach(onRetryCatalog)"))
        assertFalse(block.contains("state.catalogFailures.keys.forEach"))
    }

    @Test
    fun `retry targets only eligible failed types`() {
        val failures = mapOf(
            ContentType.MOVIE to CatalogFailure("memory pressure", retryable = false),
            ContentType.SERIES to CatalogFailure("network timeout", retryable = true),
            ContentType.LIVE to CatalogFailure("provider blocked", retryable = true),
        )

        assertEquals(
            setOf(ContentType.SERIES, ContentType.LIVE),
            retryableCatalogFailureTypes(failures),
        )
        assertTrue(
            retryableCatalogFailureTypes(
                mapOf(ContentType.MOVIE to CatalogFailure("memory pressure", retryable = false)),
            ).isEmpty(),
        )
        assertTrue(retryableCatalogFailureTypes(emptyMap()).isEmpty())
    }
}

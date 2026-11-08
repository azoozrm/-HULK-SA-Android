package sa.hulksa.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.data.XtreamException
import sa.hulksa.player.model.ContentType

class FailurePresentationTest {

    @Test
    fun `memory pressure uses the accepted Arabic message without a plain retry`() {
        val error = OutOfMemoryError(
            "Failed to allocate a 32 byte allocation with 2060840 free bytes and 2012KB until OOM",
        )
        assertTrue(isMemoryPressureFailure(error))
        assertEquals(
            "تعذر تحميل المحتوى بسبب ضغط في ذاكرة الجهاز. اعد تشغيل التطبيق.",
            userFacingFailureMessage(error),
        )
        val state = HulkUiState().finishLoginFailure(error)
        assertEquals(
            "تعذر تحميل المحتوى بسبب ضغط في ذاكرة الجهاز. اعد تشغيل التطبيق.",
            state.errorMessage,
        )
        assertFalse(state.errorRetryable)
    }

    @Test
    fun `raw technical messages are replaced with the accepted Arabic fallback`() {
        val error = IllegalStateException("java.lang.IllegalStateException: r8-map-id-abc:12")
        assertEquals(
            "حدث خطا غير متوقع. حاول مرة اخرى",
            userFacingFailureMessage(error),
        )
        assertTrue(HulkUiState().finishLoginFailure(error).errorRetryable)
    }

    @Test
    fun `healthy destination keeps a background catalog failure scoped`() {
        val state = HulkUiState().applyCatalogFailure(
            type = ContentType.SERIES,
            failure = CatalogFailure("تعذر تحديث المسلسلات", retryable = true),
            remaining = setOf(ContentType.LIVE),
            hasUsableContent = true,
        )

        assertEquals(null, state.errorMessage)
        assertEquals("تعذر تحديث المسلسلات", state.catalogFailures[ContentType.SERIES]?.message)
        assertEquals(setOf(ContentType.LIVE), state.loadingTypes)
    }

    @Test
    fun `no usable catalog keeps the failure as a global retryable error after loading settles`() {
        val state = HulkUiState().applyCatalogFailure(
            type = ContentType.MOVIE,
            failure = CatalogFailure("انتهت مهلة الاتصال", retryable = false),
            remaining = emptySet(),
            hasUsableContent = false,
        )

        assertEquals("انتهت مهلة الاتصال", state.errorMessage)
        assertFalse(state.errorRetryable)
        assertEquals("انتهت مهلة الاتصال", state.catalogFailures[ContentType.MOVIE]?.message)
    }

    @Test
    fun `a failure while another catalog is still loading stays scoped until loading settles`() {
        val state = HulkUiState().applyCatalogFailure(
            type = ContentType.SERIES,
            failure = CatalogFailure("انتهت مهلة الاتصال", retryable = true),
            remaining = setOf(ContentType.MOVIE),
            hasUsableContent = false,
        )

        assertEquals(null, state.errorMessage)
        assertEquals("انتهت مهلة الاتصال", state.catalogFailures[ContentType.SERIES]?.message)
        assertEquals(setOf(ContentType.MOVIE), state.loadingTypes)
    }

    @Test
    fun `ordinary provider messages stay intact and retryable`() {
        val error = XtreamException.Network(java.io.IOException("timeout"))
        assertEquals("تحقق من اتصال الانترنت ثم حاول مرة اخرى.", userFacingFailureMessage(error))
        val state = HulkUiState().finishLoginFailure(error)
        assertTrue(state.errorRetryable)
    }
}

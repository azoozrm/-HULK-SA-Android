package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFeedbackPolicyTest {
    @Test
    fun `enqueue feedback keeps the real result and proposes truthful copy`() {
        assertEquals(
            DownloadFeedback("جاري تجهيز التحميل", DownloadFeedbackGlyph.PREPARING),
            downloadPreparingFeedback(),
        )
        assertEquals(
            DownloadFeedback("جاري بدء هذا التحميل", DownloadFeedbackGlyph.PREPARING),
            downloadDuplicatePendingFeedback(),
        )
        assertEquals(
            DownloadFeedback("المحتوى موجود في التنزيلات", DownloadFeedbackGlyph.QUEUED),
            downloadAlreadyQueuedFeedback(),
        )
        assertEquals(
            DownloadFeedback("المحتوى محمل بالفعل", DownloadFeedbackGlyph.COMPLETED),
            downloadAlreadyCompletedFeedback(),
        )
    }

    @Test
    fun `pause resume and retry feedback use their semantic glyphs`() {
        assertEquals(
            DownloadFeedback("تم ايقاف التحميل مؤقتا", DownloadFeedbackGlyph.PAUSE),
            downloadPausePersistedFeedback(),
        )
        assertEquals(
            DownloadFeedback("تعذر حفظ ايقاف التحميل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR),
            downloadPauseFailedFeedback(),
        )
        assertEquals(
            DownloadFeedback("جاري استئناف التحميل", DownloadFeedbackGlyph.RESUME),
            downloadResumeAcceptedFeedback(),
        )
        assertEquals(
            DownloadFeedback("جاري اعادة المحاولة", DownloadFeedbackGlyph.RETRY),
            downloadRetryAcceptedFeedback(),
        )
    }

    @Test
    fun `delete and cancel feedback are distinguished`() {
        assertEquals(
            DownloadFeedback("تم الغاء التحميل", DownloadFeedbackGlyph.CANCEL),
            downloadCancelConfirmedFeedback(),
        )
        assertEquals(
            DownloadFeedback("تم حذف التنزيل", DownloadFeedbackGlyph.DELETE),
            downloadDeletionConfirmedFeedback(),
        )
        assertEquals(
            DownloadFeedback("تعذر حذف التنزيل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR),
            downloadDeletionFailedFeedback(),
        )
    }

    @Test
    fun `settings feedback reports the real selected value`() {
        assertEquals(
            DownloadFeedback("تم تفعيل التحميل عبر واي فاي فقط", DownloadFeedbackGlyph.WIFI),
            downloadWifiFeedback(wifiOnly = true),
        )
        assertEquals(
            DownloadFeedback("تم السماح بالتحميل عبر جميع الشبكات", DownloadFeedbackGlyph.WIFI),
            downloadWifiFeedback(wifiOnly = false),
        )
        assertEquals(
            DownloadFeedback("عدد التحميلات المتزامنة الان 3", DownloadFeedbackGlyph.CONCURRENCY),
            downloadConcurrencyFeedback(3),
        )
        assertEquals(
            DownloadFeedback("تم رفع اولوية التحميل", DownloadFeedbackGlyph.PRIORITY),
            downloadPriorityFeedback(previousPriority = 0),
        )
        assertEquals(
            DownloadFeedback("تم خفض اولوية التحميل", DownloadFeedbackGlyph.PRIORITY),
            downloadPriorityFeedback(previousPriority = 1),
        )
        assertEquals(
            DownloadFeedback("تم ضبط اولوية التحميل على عادية", DownloadFeedbackGlyph.PRIORITY),
            downloadPriorityFeedback(previousPriority = -1),
        )
    }

    @Test
    fun `failure feedback never claims success`() {
        assertEquals(DownloadFeedbackGlyph.ERROR, downloadFeatureDisabledFeedback().glyph)
        assertEquals(DownloadFeedbackGlyph.ERROR, downloadSessionRequiredFeedback().glyph)
        assertEquals(
            DownloadFeedback("تغير المستخدم ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR),
            downloadOwnerChangedFeedback(),
        )
        assertEquals(
            DownloadFeedback("تعذر التحميل ، حاول مرة اخرى", DownloadFeedbackGlyph.ERROR),
            downloadGenericFailureFeedback(),
        )
    }

    @Test
    fun `all feedback copy is free of terminal punctuation`() {
        listOf(
            downloadFeatureDisabledFeedback(),
            downloadSessionRequiredFeedback(),
            downloadOwnerChangedFeedback(),
            downloadGenericFailureFeedback(),
            downloadOwnerFixFailedFeedback(),
            downloadTargetUnresolvedFeedback(isMovie = true),
            downloadTargetUnresolvedFeedback(isMovie = false),
            downloadPreparingFeedback(),
            downloadDuplicatePendingFeedback(),
            downloadAlreadyQueuedFeedback(),
            downloadAlreadyCompletedFeedback(),
            downloadPausePersistedFeedback(),
            downloadPauseFailedFeedback(),
            downloadResumeAcceptedFeedback(),
            downloadRetryAcceptedFeedback(),
            downloadCancelConfirmedFeedback(),
            downloadDeletionConfirmedFeedback(),
            downloadDeletionFailedFeedback(),
            downloadWifiFeedback(true),
            downloadWifiFeedback(false),
            downloadScheduleFeedback(true),
            downloadScheduleFeedback(false),
            downloadConcurrencyFeedback(1),
            downloadPriorityFeedback(0),
        ).forEach { feedback ->
            val last = feedback.message.lastOrNull()
            assertEquals("${feedback.message} ends without decoration", false, last == '.' || last == '\u2026')
        }
    }
}

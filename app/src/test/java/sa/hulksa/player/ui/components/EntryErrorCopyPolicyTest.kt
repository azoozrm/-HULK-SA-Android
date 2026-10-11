package sa.hulksa.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryErrorCopyPolicyTest {
    @Test
    fun theOwnerExampleLosesItsTerminalDot() {
        assertEquals("ادخل كود دخول صحيح", entryErrorDisplayText("ادخل كود دخول صحيح."))
    }

    @Test
    fun repeatedDotsAndEllipsisAndTrailingWhitespaceAreRemoved() {
        assertEquals("تعذر تسجيل الدخول", entryErrorDisplayText("تعذر تسجيل الدخول..."))
        assertEquals("تعذر تسجيل الدخول", entryErrorDisplayText("تعذر تسجيل الدخول\u2026"))
        assertEquals("تعذر تسجيل الدخول", entryErrorDisplayText("تعذر تسجيل الدخول. \u200F "))
    }

    @Test
    fun alreadyCleanCopyIsUnchanged() {
        assertEquals("تعذر تسجيل الدخول", entryErrorDisplayText("تعذر تسجيل الدخول"))
    }

    @Test
    fun meaningfulInteriorPunctuationAndValuesArePreserved() {
        assertEquals(
            "تعذر الاتصال بالسيرفر. حاول مرة اخرى",
            entryErrorDisplayText("تعذر الاتصال بالسيرفر. حاول مرة اخرى."),
        )
        assertEquals(
            "راجع hulksa.com ثم حاول",
            entryErrorDisplayText("راجع hulksa.com ثم حاول."),
        )
        assertEquals(
            "الاصدار 0.9.3.23 غير مدعوم",
            entryErrorDisplayText("الاصدار 0.9.3.23 غير مدعوم."),
        )
    }

    @Test
    fun punctuationOnlyFallsBackToTheExistingGenericError() {
        assertEquals(ENTRY_ERROR_FALLBACK_TEXT, entryErrorDisplayText("..."))
        assertEquals(ENTRY_ERROR_FALLBACK_TEXT, entryErrorDisplayText(" \u2026 "))
    }
}

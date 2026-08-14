package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSmartSearchPhoneticTest {
    @Test
    fun arabicTransliterationMatchesPrisonBreak() {
        assertEquals(0, crossScriptPhoneticRank("بريزون بريك", "prison break"))
    }

    @Test
    fun partialArabicTransliterationMatchesEnglishTitlePrefix() {
        assertEquals(1, crossScriptPhoneticRank("بريزون", "prison break"))
    }

    @Test
    fun commonArabicTransliterationsMatchAcrossScripts() {
        assertEquals(0, crossScriptPhoneticRank("بريكنق باد", "breaking bad"))
        assertEquals(0, crossScriptPhoneticRank("قيم اوف ثرونز", "game of thrones"))
    }

    @Test
    fun sameScriptDoesNotUseCrossScriptFallback() {
        assertNull(crossScriptPhoneticRank("prison break", "prison break"))
        assertNull(crossScriptPhoneticRank("بريزون بريك", "بريزون بريك"))
    }

    @Test
    fun unrelatedCrossScriptTextDoesNotMatch() {
        assertNull(crossScriptPhoneticRank("بريزون بريك", "the office"))
    }

    @Test
    fun fastNormalizerPreservesArabicAndDigitEquivalence() {
        assertEquals("بريزون بريك 123", normalizeSearchText("  بَرِيزُون---بريك ١٢٣  "))
        assertEquals("prison break", normalizeSearchText("Prison...BREAK"))
        assertEquals("ايمان وليد", normalizeSearchText("إيمان ـ وليد"))
    }
}

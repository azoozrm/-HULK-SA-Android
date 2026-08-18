package sa.hulksa.player.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.AccountInfo

class VoiceSearchFoundationTest {
    @Test
    fun arabicQueryUsesArabicRecognitionWithoutChangingSearchText() {
        assertEquals(
            "ar-SA",
            preferredVoiceSearchLanguageTag("بريزون بريك", "en-US"),
        )
    }

    @Test
    fun latinQueryUsesEnglishRecognition() {
        assertEquals(
            "en-US",
            preferredVoiceSearchLanguageTag("Prison Break", "ar-SA"),
        )
    }

    @Test
    fun emptyQueryStartsInArabicForSaudiVoiceSearch() {
        assertEquals("ar-SA", preferredVoiceSearchLanguageTag("", "ar-EG"))
        assertEquals("ar-SA", preferredVoiceSearchLanguageTag("", "en-GB"))
        assertEquals("ar-SA", preferredVoiceSearchLanguageTag("", "fr-FR"))
    }

    @Test
    fun recognizedTextRemainsEditableAndCasePreserved() {
        assertEquals(
            "Game Of Thrones",
            firstVoiceSearchTranscript(listOf("", "  Game Of Thrones  ", "ignored")),
        )
        assertNull(firstVoiceSearchTranscript(listOf("", "   ")))
        assertNull(firstVoiceSearchTranscript(null))
    }

    @Test
    fun supportedRemoteVoiceKeysAreScopedToKnownAssistKeys() {
        assertTrue(isVoiceSearchHardwareKey(KeyEvent.KEYCODE_SEARCH))
        assertTrue(isVoiceSearchHardwareKey(KeyEvent.KEYCODE_ASSIST))
        assertTrue(isVoiceSearchHardwareKey(KeyEvent.KEYCODE_VOICE_ASSIST))
        assertFalse(isVoiceSearchHardwareKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun voiceActionIsAvailableOnlyInsideAuthenticatedSearchDestination() {
        val account = AccountInfo(
            username = "subscriber",
            status = "Active",
            expiresAtEpochSeconds = null,
            activeConnections = 0,
            maxConnections = 1,
            isTrial = false,
        )
        assertTrue(
            isVoiceSearchDestination(
                HulkUiState(
                    screen = HulkScreen.MAIN,
                    account = account,
                    destination = MainDestination.SEARCH,
                ),
            ),
        )
        assertFalse(
            isVoiceSearchDestination(
                HulkUiState(
                    screen = HulkScreen.MAIN,
                    account = account,
                    destination = MainDestination.HOME,
                ),
            ),
        )
        assertFalse(
            isVoiceSearchDestination(
                HulkUiState(
                    screen = HulkScreen.LOGIN,
                    account = null,
                    destination = MainDestination.SEARCH,
                ),
            ),
        )
    }
}

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
import sa.hulksa.player.VoiceSearchOwner
import sa.hulksa.player.model.AccountInfo

class VoiceSearchFoundationTest {
    private fun owner(
        accountId: String = "account-a",
        sessionId: String = "session-a",
        profileId: String = "profile-a",
        contextGeneration: Long = 1L,
    ) = VoiceSearchOwner(
        accountId = accountId,
        sessionId = sessionId,
        profileId = profileId,
        contextGeneration = contextGeneration,
    )

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
    fun emptyQueryFallsBackToArabicOrEnglishDeviceLanguage() {
        assertEquals("ar-SA", preferredVoiceSearchLanguageTag("", "ar-EG"))
        assertEquals("en-US", preferredVoiceSearchLanguageTag("", "en-GB"))
        assertNull(preferredVoiceSearchLanguageTag("", "fr-FR"))
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

    @Test
    fun navigationAwayInvalidatesTranscriptEvenAfterReturningToSameSearchOwner() {
        val gate = VoiceSearchRequestGate()
        val searchOwner = owner()
        val request = gate.begin(searchOwner)
        val returnedSearchOwner = searchOwner.copy(contextGeneration = 2L)

        assertTrue(gate.isCurrent(request, searchOwner))
        assertTrue(gate.updateContext(returnedSearchOwner))

        assertFalse(gate.isCurrent(request, returnedSearchOwner))
    }

    @Test
    fun profileOverlayInvalidatesRequestWhileUnderlyingSearchStateIsUnchanged() {
        val gate = VoiceSearchRequestGate()
        val searchOwner = owner()
        val request = gate.begin(searchOwner)

        gate.updateContext(null)

        assertFalse(gate.isCurrent(request, searchOwner))
    }

    @Test
    fun accountProfileAndSessionReplacementEachInvalidatePendingVoiceWork() {
        listOf(
            owner(accountId = "account-b"),
            owner(profileId = "profile-b"),
            owner(sessionId = "session-b"),
        ).forEach { replacement ->
            val gate = VoiceSearchRequestGate()
            val original = owner()
            val request = gate.begin(original)

            gate.updateContext(replacement)

            assertFalse(gate.isCurrent(request, replacement))
        }
    }

    @Test
    fun newerRecognitionRequestRejectsOlderPartialAndFinalCallbacks() {
        val gate = VoiceSearchRequestGate()
        val owner = owner()
        val first = gate.begin(owner)
        val second = gate.begin(owner)

        assertFalse(gate.isCurrent(first, owner))
        assertTrue(gate.isCurrent(second, owner))
        gate.complete(second)
        assertFalse(gate.isCurrent(second, owner))
    }

    @Test
    fun lifecycleDestroyInvalidatesPendingRecognition() {
        val gate = VoiceSearchRequestGate()
        val owner = owner()
        val request = gate.begin(owner)

        gate.invalidate()

        assertFalse(gate.isCurrent(request, owner))
    }
}

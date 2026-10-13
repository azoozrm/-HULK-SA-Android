package sa.hulksa.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The affected voice outcomes must present the authored copy without terminal punctuation and
 * with the app-authored alef normalization (ا only), so the shared notice family never shows
 * drifted wording.
 */
class VoiceSearchFeedbackCopyTest {
    private val allCopy = listOf(
        VoiceSearchCopy.NO_MATCH,
        VoiceSearchCopy.MICROPHONE_DENIED,
        VoiceSearchCopy.PERMISSION_LAUNCH_FAILED,
        VoiceSearchCopy.RECOGNITION_UNAVAILABLE,
        VoiceSearchCopy.SERVICE_FAILED,
        VoiceSearchCopy.INSUFFICIENT_PERMISSION,
        VoiceSearchCopy.OTHER_FAILURE,
    )

    @Test
    fun voiceCopyHasNoTerminalPunctuation() {
        allCopy.forEach { message ->
            val trimmed = message.trim()
            assertFalse("trailing period: $message", trimmed.endsWith("."))
            assertFalse("trailing ellipsis: $message", trimmed.endsWith("…"))
            assertFalse("trailing Arabic full stop: $message", trimmed.endsWith("\u06D4"))
        }
    }

    @Test
    fun voiceCopyUsesNormalizedAlef() {
        allCopy.forEach { message ->
            assertFalse("contains أ: $message", message.contains('أ'))
            assertFalse("contains إ: $message", message.contains('إ'))
            assertFalse("contains آ: $message", message.contains('آ'))
        }
    }

    @Test
    fun noMatchCopyMatchesTheApprovedContractWording() {
        assertEquals(
            "لم يتم التعرف على صوت واضح ، حاول مرة اخرى",
            VoiceSearchCopy.NO_MATCH,
        )
    }
}

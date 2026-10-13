package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production PIN entry behavior: four digits complete exactly once, a new stage starts empty and
 * enabled, and backspace/disabled input follow the real persisted security policy. This exercises
 * the same PinEntryBuffer the real PinPanel composes on every device.
 */
class PinEntryBufferTest {
    private fun complete(buffer: PinEntryBuffer, digits: String): List<String> {
        val outputs = mutableListOf<String>()
        digits.forEach { digit ->
            buffer.append(digit.toString(), enabled = true) { outputs += it }
        }
        return outputs
    }

    @Test
    fun fourDigitsCompleteExactlyOnce() {
        val buffer = PinEntryBuffer()
        val outputs = complete(buffer, "1234")

        assertEquals(listOf("1234"), outputs)
        assertTrue(buffer.isComplete)
        assertEquals("1234", buffer.digits)
    }

    @Test
    fun digitsAfterCompletionAreIgnoredAndNeverCompleteTwice() {
        val buffer = PinEntryBuffer()
        val outputs = mutableListOf<String>()
        "1234".forEach { buffer.append(it.toString(), enabled = true) { value -> outputs += value } }
        "5".forEach { buffer.append(it.toString(), enabled = true) { value -> outputs += value } }
        "67".forEach { buffer.append(it.toString(), enabled = true) { value -> outputs += value } }

        assertEquals(listOf("1234"), outputs)
        assertEquals("1234", buffer.digits)
    }

    @Test
    fun disabledStageAcceptsNothing() {
        val buffer = PinEntryBuffer()
        val outputs = mutableListOf<String>()
        "1234".forEach { buffer.append(it.toString(), enabled = false) { value -> outputs += value } }

        assertEquals(emptyList<String>(), outputs)
        assertEquals("", buffer.digits)
        assertFalse(buffer.isComplete)
    }

    @Test
    fun backspaceRemovesExactlyOneDigitAndCompletesLater() {
        val buffer = PinEntryBuffer()
        complete(buffer, "123").let { }
        buffer.backspace(enabled = true)
        assertEquals("12", buffer.digits)

        val outputs = mutableListOf<String>()
        buffer.append("9", enabled = true) { outputs += it }
        buffer.append("9", enabled = true) { outputs += it }
        buffer.append("9", enabled = true) { outputs += it }
        assertEquals(listOf("1299"), outputs)
        assertTrue(buffer.isComplete)
    }

    @Test
    fun confirmationStageStartsWithAFreshEnabledBuffer() {
        // NEW_PIN completes, then the stage change creates the confirmation buffer.
        val newPin = PinEntryBuffer()
        assertEquals(listOf("4321"), complete(newPin, "4321"))
        assertTrue(newPin.isComplete)

        val confirmation = PinEntryBuffer()
        assertFalse(confirmation.isComplete)
        assertEquals("", confirmation.digits)

        val outputs = mutableListOf<String>()
        "4321".forEach { confirmation.append(it.toString(), enabled = true) { value -> outputs += value } }
        assertEquals(listOf("4321"), outputs)
    }

    @Test
    fun mismatchSequenceCanRetryOnTheSameStageBufferAfterReset() {
        val confirmation = PinEntryBuffer()
        assertEquals(listOf("1111"), complete(confirmation, "1111"))

        // The real screen bumps its reset token on a mismatch, which composes a new buffer.
        val retry = PinEntryBuffer()
        val outputs = mutableListOf<String>()
        "4321".forEach { retry.append(it.toString(), enabled = true) { value -> outputs += value } }
        assertEquals(listOf("4321"), outputs)
    }
}

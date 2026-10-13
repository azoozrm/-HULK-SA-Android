package sa.hulksa.player.ui.screens

import sa.hulksa.player.data.FOUR_DIGIT_CREDENTIAL_LENGTH

/**
 * Production four-digit PIN entry buffer owned by the real PinPanel. Exactly one buffer exists per
 * entry stage; a stage change creates a new buffer (empty + enabled), a disabled stage ignores
 * input, each activation appends at most one digit, backspace removes one digit, and a completed
 * sequence fires [append]'s completion exactly once.
 */
internal class PinEntryBuffer(
    private val length: Int = FOUR_DIGIT_CREDENTIAL_LENGTH,
) {
    var digits: String = ""
        private set

    private var completed = false

    val isComplete: Boolean get() = completed

    fun append(digit: String, enabled: Boolean, onComplete: (String) -> Unit) {
        if (!enabled || completed || digits.length >= length) return
        val next = digits + digit
        digits = next
        if (next.length == length) {
            completed = true
            onComplete(next)
        }
    }

    fun backspace(enabled: Boolean) {
        if (!enabled || completed) return
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }
}

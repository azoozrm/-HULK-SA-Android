package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ProfilePinCredentialPolicyTest {
    @Test
    fun pinFormatRequiresExactlyFourAsciiDigits() {
        assertTrue(isValidProfilePin("0123"))
        assertTrue(isValidProfilePin("9999"))
        assertFalse(isValidProfilePin("123"))
        assertFalse(isValidProfilePin("12345"))
        assertFalse(isValidProfilePin("12a4"))
        assertFalse(isValidProfilePin("١٢٣٤"))
    }

    @Test
    fun verifierIsDeterministicForSamePinSaltAndIterations() {
        val salt = ByteArray(16) { it.toByte() }
        val first = deriveProfilePinVerifier("2580", salt, 1_000)
        val second = deriveProfilePinVerifier("2580", salt, 1_000)

        assertTrue(MessageDigest.isEqual(first, second))
    }

    @Test
    fun verifierChangesForWrongPinOrDifferentSalt() {
        val salt = ByteArray(16) { it.toByte() }
        val otherSalt = ByteArray(16) { (it + 1).toByte() }
        val expected = deriveProfilePinVerifier("2580", salt, 1_000)
        val wrongPin = deriveProfilePinVerifier("2581", salt, 1_000)
        val wrongSalt = deriveProfilePinVerifier("2580", otherSalt, 1_000)

        assertFalse(MessageDigest.isEqual(expected, wrongPin))
        assertFalse(MessageDigest.isEqual(expected, wrongSalt))
    }
}

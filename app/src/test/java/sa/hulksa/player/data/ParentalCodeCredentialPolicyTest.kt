package sa.hulksa.player.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalCodeCredentialPolicyTest {
    @Test
    fun parentalCodeRequiresExactlyFourAsciiDigits() {
        assertTrue(isValidParentalCode("0123"))
        assertTrue(isValidParentalCode("9999"))
        assertFalse(isValidParentalCode("123"))
        assertFalse(isValidParentalCode("12345"))
        assertFalse(isValidParentalCode("12a4"))
        assertFalse(isValidParentalCode("١٢٣٤"))
    }

    @Test
    fun parentalVerifierUsesTheSharedSaltedPrimitiveWithoutSharingStorage() {
        val salt = ByteArray(16) { it.toByte() }
        val expected = deriveParentalCodeVerifier("1234", salt, 1_000)
        val same = deriveParentalCodeVerifier("1234", salt, 1_000)
        val wrong = deriveParentalCodeVerifier("5678", salt, 1_000)

        assertTrue(MessageDigest.isEqual(expected, same))
        assertFalse(MessageDigest.isEqual(expected, wrong))
    }

    @Test
    fun legacyKidsWithAdultPinRequiresProofThenExplicitParentalSetup() {
        assertEquals(
            LegacyParentalCodeMigrationDecision.REQUIRE_LEGACY_PARENT_PROOF_THEN_EXPLICIT_SETUP,
            legacyParentalCodeMigrationDecision(
                hadKidsProfiles = true,
                legacyPrimaryProfilePinAvailable = true,
            ),
        )
    }

    @Test
    fun legacyKidsWithoutUsableAdultPinFailsClosed() {
        assertEquals(
            LegacyParentalCodeMigrationDecision.FAIL_CLOSED_NO_USABLE_PARENT_PROOF,
            legacyParentalCodeMigrationDecision(
                hadKidsProfiles = true,
                legacyPrimaryProfilePinAvailable = false,
            ),
        )
    }

    @Test
    fun accountWithoutLegacyKidsNeverPromotesAdultPinToParentalCredential() {
        assertEquals(
            LegacyParentalCodeMigrationDecision.COMPLETE_WITHOUT_PARENTAL_CODE,
            legacyParentalCodeMigrationDecision(
                hadKidsProfiles = false,
                legacyPrimaryProfilePinAvailable = true,
            ),
        )
    }
}

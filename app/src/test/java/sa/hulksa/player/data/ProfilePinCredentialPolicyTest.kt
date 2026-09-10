package sa.hulksa.player.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun fourFailuresRemainAvailableAndFifthFailureEntersFirstBoundedLockout() {
        var state = FourDigitCredentialAttemptState()
        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) { index ->
            state = fourDigitCredentialStateAfterFailure(
                state = state,
                nowEpochMs = 1_000L + index,
                nowElapsedMs = 10_000L + index,
                currentBootCount = 4,
            )
            assertEquals(index + 1, state.failedAttempts)
            assertEquals(0L, state.lockoutDurationMs)
        }

        state = fourDigitCredentialStateAfterFailure(
            state = state,
            nowEpochMs = 2_000L,
            nowElapsedMs = 20_000L,
            currentBootCount = 4,
        )

        assertEquals(0, state.failedAttempts)
        assertEquals(1, state.lockoutLevel)
        assertEquals(FOUR_DIGIT_LOCKOUT_DURATIONS_MS.first(), state.lockoutDurationMs)
    }

    @Test
    fun sameBootLockoutUsesElapsedRealtimeAndIgnoresWallClockChanges() {
        val state = FourDigitCredentialAttemptState(
            failedAttempts = 0,
            lockoutLevel = 1,
            lockoutStartedEpochMs = 1_000_000L,
            lockoutStartedElapsedMs = 20_000L,
            lockoutDurationMs = 30_000L,
            lockoutBootCount = 12,
        )

        assertEquals(
            20_000L,
            fourDigitCredentialRemainingLockoutMs(
                state = state,
                nowEpochMs = 9_999_999_999L,
                nowElapsedMs = 30_000L,
                currentBootCount = 12,
            ),
        )
        assertEquals(
            20_000L,
            fourDigitCredentialRemainingLockoutMs(
                state = state,
                nowEpochMs = 1L,
                nowElapsedMs = 30_000L,
                currentBootCount = 12,
            ),
        )
    }

    @Test
    fun rebootReappliesCurrentCooldownAndForwardWallClockCannotBypassIt() {
        val state = FourDigitCredentialAttemptState(
            failedAttempts = 0,
            lockoutLevel = 2,
            lockoutStartedEpochMs = 1_000_000L,
            lockoutStartedElapsedMs = 50_000L,
            lockoutDurationMs = 120_000L,
            lockoutBootCount = 12,
        )
        val rebased = fourDigitCredentialStateForCurrentBoot(
            state = state,
            nowEpochMs = 99_999_999_999L,
            nowElapsedMs = 5_000L,
            currentBootCount = 13,
        )

        assertEquals(13, rebased.lockoutBootCount)
        assertEquals(5_000L, rebased.lockoutStartedElapsedMs)
        assertEquals(120_000L, rebased.lockoutDurationMs)
        assertEquals(
            120_000L,
            fourDigitCredentialRemainingLockoutMs(
                state = rebased,
                nowEpochMs = Long.MAX_VALUE,
                nowElapsedMs = 5_000L,
                currentBootCount = 13,
            ),
        )
        assertEquals(
            60_000L,
            fourDigitCredentialRemainingLockoutMs(
                state = rebased,
                nowEpochMs = Long.MAX_VALUE,
                nowElapsedMs = 65_000L,
                currentBootCount = 13,
            ),
        )
        assertEquals(
            0L,
            fourDigitCredentialRemainingLockoutMs(
                state = rebased,
                nowEpochMs = Long.MAX_VALUE,
                nowElapsedMs = 125_001L,
                currentBootCount = 13,
            ),
        )
    }

    @Test
    fun elapsedRealtimeRollbackWithoutBootCountConservativelyReappliesBoundedCooldown() {
        val state = FourDigitCredentialAttemptState(
            failedAttempts = 0,
            lockoutLevel = 1,
            lockoutStartedEpochMs = 1_000_000L,
            lockoutStartedElapsedMs = 90_000L,
            lockoutDurationMs = 30_000L,
            lockoutBootCount = null,
        )
        val rebased = fourDigitCredentialStateForCurrentBoot(
            state = state,
            nowEpochMs = Long.MAX_VALUE,
            nowElapsedMs = 2_000L,
            currentBootCount = null,
        )

        assertEquals(2_000L, rebased.lockoutStartedElapsedMs)
        assertEquals(
            30_000L,
            fourDigitCredentialRemainingLockoutMs(
                state = rebased,
                nowEpochMs = Long.MAX_VALUE,
                nowElapsedMs = 2_000L,
                currentBootCount = null,
            ),
        )
        assertEquals(
            0L,
            fourDigitCredentialRemainingLockoutMs(
                state = rebased,
                nowEpochMs = 1L,
                nowElapsedMs = 32_001L,
                currentBootCount = null,
            ),
        )
    }

    @Test
    fun repeatedLockoutsEscalateDeterministicallyAndRemainBounded() {
        var state = FourDigitCredentialAttemptState()
        FOUR_DIGIT_LOCKOUT_DURATIONS_MS.forEachIndexed { index, expectedDuration ->
            repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT) {
                state = fourDigitCredentialStateAfterFailure(
                    state = state.copy(lockoutDurationMs = 0L),
                    nowEpochMs = 10_000L + index,
                    nowElapsedMs = 20_000L + index,
                    currentBootCount = 1,
                )
            }
            assertEquals(expectedDuration, state.lockoutDurationMs)
        }

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT) {
            state = fourDigitCredentialStateAfterFailure(
                state = state.copy(lockoutDurationMs = 0L),
                nowEpochMs = 30_000L,
                nowElapsedMs = 40_000L,
                currentBootCount = 1,
            )
        }
        assertEquals(FOUR_DIGIT_LOCKOUT_DURATIONS_MS.last(), state.lockoutDurationMs)
    }
}

package sa.hulksa.player.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.hulksa.player.model.ProfileKind

@RunWith(AndroidJUnit4::class)
class FourDigitCredentialAttemptProtectionInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearTestState()
    }

    @After
    fun tearDown() {
        clearTestState()
    }

    @Test
    fun profilePinAttemptsLockPersistAcrossStoreRecreationAndSuccessResetsState() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val store = ProfilePinCredentialStore(context)
        assertTrue(store.setPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000"))
        }
        assertTrue(store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000"))
        }
        expectLocked {
            store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000")
        }

        val recreated = ProfilePinCredentialStore(context)
        expectLocked {
            recreated.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468")
        }

        expireProfileLockout(ProfileStore.PRIMARY_PROFILE_ID)
        assertTrue(ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000"))
        }
        assertTrue(ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "2468"))
    }

    @Test
    fun rebootMarkerReappliesCooldownWithoutWallClockBypass() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val store = ProfilePinCredentialStore(context)
        assertTrue(store.setPin(ProfileStore.PRIMARY_PROFILE_ID, "1357"))
        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000"))
        }
        expectLocked {
            store.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "0000")
        }

        val preferences = profilePreferences(ACCOUNT_A)
        val prefix = profileAttemptPrefix(ProfileStore.PRIMARY_PROFILE_ID)
        val duration = FOUR_DIGIT_LOCKOUT_DURATIONS_MS.first()
        val currentBootCount = currentBootCount()
        val editor = preferences.edit()
            // A wall clock moved far forward would have made the old persisted-wall deadline stale.
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS),
                1L,
            )
        if (currentBootCount != null) {
            editor
                .putInt(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT),
                    currentBootCount xor 1,
                )
                .putLong(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
                    SystemClock.elapsedRealtime(),
                )
        } else {
            editor
                .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))
                .putLong(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
                    SystemClock.elapsedRealtime() + duration,
                )
        }
        assertTrue(editor.commit())

        val locked = expectLocked {
            ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1357")
        }
        assertTrue(locked.retryAfterMs in 1..duration)

        if (currentBootCount != null) {
            assertEquals(
                currentBootCount,
                preferences.getInt(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT),
                    currentBootCount xor 1,
                ),
            )
        }
        val rebasedElapsed = preferences.getLong(
            credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
            -1L,
        )
        assertTrue(rebasedElapsed in 0..SystemClock.elapsedRealtime())

        // Moving only the persisted wall marker forward again cannot shorten the same-boot cooldown.
        assertTrue(
            preferences.edit()
                .putLong(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS),
                    1L,
                )
                .commit(),
        )
        expectLocked {
            ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1357")
        }
    }

    @Test
    fun parentalAndProfileBudgetsStayIndependentAcrossOwnersAndAccounts() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profiles = ProfileStore(context)
        val secondProfile = requireNotNull(profiles.createProfile("ثانوي", kind = ProfileKind.STANDARD))
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)
        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "1111"))
        assertTrue(profilePins.setPin(secondProfile.id, "2222"))
        assertTrue(parentalCodes.setCode("3333"))

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "9999"))
        }
        expectLocked {
            profilePins.verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "9999")
        }

        assertTrue(profilePins.verifyPin(secondProfile.id, "2222"))
        assertTrue(parentalCodes.verifyCode("3333"))

        repeat(FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT - 1) {
            assertFalse(parentalCodes.verifyCode("9999"))
        }
        expectLocked { parentalCodes.verifyCode("9999") }
        assertTrue(profilePins.verifyPin(secondProfile.id, "2222"))

        assertTrue(accountScope.bind(ACCOUNT_B))
        val otherAccountParentalCodes = ParentalCodeCredentialStore(context)
        assertTrue(otherAccountParentalCodes.setCode("4444"))
        assertTrue(otherAccountParentalCodes.verifyCode("4444"))

        assertTrue(accountScope.bind(ACCOUNT_A))
        expectLocked { ParentalCodeCredentialStore(context).verifyCode("3333") }
    }

    @Test
    fun malformedPersistentAttemptStateFailsClosedBeforeCredentialVerification() = runBlocking {
        val accountScope = AccountScopeStore(context)
        assertTrue(accountScope.bind(ACCOUNT_A))
        val profilePins = ProfilePinCredentialStore(context)
        val parentalCodes = ParentalCodeCredentialStore(context)
        assertTrue(profilePins.setPin(ProfileStore.PRIMARY_PROFILE_ID, "1357"))
        assertTrue(parentalCodes.setCode("2468"))

        val profilePreferences = profilePreferences(ACCOUNT_A)
        assertTrue(
            profilePreferences.edit()
                .putString(
                    credentialAttemptKey(
                        profileAttemptPrefix(ProfileStore.PRIMARY_PROFILE_ID),
                        ATTEMPT_KEY_FAILED_ATTEMPTS,
                    ),
                    "corrupt",
                )
                .commit(),
        )
        expectUnavailable {
            ProfilePinCredentialStore(context).verifyPin(ProfileStore.PRIMARY_PROFILE_ID, "1357")
        }

        val parentalPreferences = context.getSharedPreferences(
            accountScopedPreferencesName(ParentalCodeCredentialStore.PREFERENCES_NAME, ACCOUNT_A),
            Context.MODE_PRIVATE,
        )
        assertTrue(
            parentalPreferences.edit()
                .putString(
                    credentialAttemptKey(
                        ParentalCodeCredentialStore.ATTEMPT_PREFIX,
                        ATTEMPT_KEY_FAILED_ATTEMPTS,
                    ),
                    "corrupt",
                )
                .commit(),
        )
        expectUnavailable {
            ParentalCodeCredentialStore(context).verifyCode("2468")
        }
    }

    private fun expireProfileLockout(profileId: String) {
        val preferences = profilePreferences(ACCOUNT_A)
        val prefix = profileAttemptPrefix(profileId)
        val duration = FOUR_DIGIT_LOCKOUT_DURATIONS_MS.first()
        val editor = preferences.edit()
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS),
                1L,
            )
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
                (SystemClock.elapsedRealtime() - duration - 1_000L).coerceAtLeast(0L),
            )
        val bootCount = currentBootCount()
        if (bootCount != null) {
            editor.putInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT),
                bootCount,
            )
        } else {
            editor.remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))
        }
        assertTrue(editor.commit())
    }

    private fun profilePreferences(accountId: String) = context.getSharedPreferences(
        accountScopedPreferencesName(ProfilePinCredentialStore.PREFERENCES_NAME, accountId),
        Context.MODE_PRIVATE,
    )

    private fun profileAttemptPrefix(profileId: String): String =
        "profile:$profileId:attempt_protection"

    private fun currentBootCount(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()
    }

    private suspend fun expectLocked(block: suspend () -> Unit): FourDigitCredentialLockedException {
        try {
            block()
            fail("Expected FourDigitCredentialLockedException")
        } catch (locked: FourDigitCredentialLockedException) {
            assertTrue(locked.retryAfterMs > 0L)
            return locked
        }
        error("unreachable")
    }

    private suspend fun expectUnavailable(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected FourDigitCredentialProtectionUnavailableException")
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            Unit
        }
    }

    private fun clearTestState() {
        val baseNames = setOf(
            ACCOUNT_SCOPE_PREFERENCES,
            PROFILE_PREFERENCES,
            ProfilePinCredentialStore.PREFERENCES_NAME,
            PROFILE_SECURITY_METADATA_PREFERENCES,
            ParentalCodeCredentialStore.PREFERENCES_NAME,
        )
        val preferenceNames = baseNames.toMutableSet()
        listOf(ACCOUNT_A, ACCOUNT_B).forEach { accountId ->
            baseNames.forEach { baseName ->
                preferenceNames += accountScopedPreferencesName(baseName, accountId)
            }
        }
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        const val ACCOUNT_A = "credential-attempt-test-account-a"
        const val ACCOUNT_B = "credential-attempt-test-account-b"
        const val ACCOUNT_SCOPE_PREFERENCES = "hulk_account_scope_v1"
        const val PROFILE_PREFERENCES = "hulk_profiles_v1"
        const val PROFILE_SECURITY_METADATA_PREFERENCES = "hulk_profile_preferences_v1"
    }
}

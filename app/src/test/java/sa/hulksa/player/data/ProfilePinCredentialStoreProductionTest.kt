package sa.hulksa.player.data

import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.ui.ProfileSwitchAuthorization
import sa.hulksa.player.ui.profileSwitchAuthorization

class ProfilePinCredentialStoreProductionTest {
    @Test
    fun `real store verifies valid and wrong pin and preserves protection`() = runBlocking {
        val fixture = Fixture()

        assertTrue(fixture.store.setPin(PROFILE_ID, "1234"))
        assertTrue(fixture.store.hasPin(PROFILE_ID))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "1234"))
        assertFalse(fixture.store.verifyPin(PROFILE_ID, "9999"))
        assertEquals(
            ProfilePinCredentialStatus.Valid::class,
            fixture.store.credentialStatus(PROFILE_ID)::class,
        )
        assertTrue(fixture.store.hasPin(PROFILE_ID))
    }

    @Test
    fun `corrupt and metadata only credentials never verify and remain protected`() = runBlocking {
        val corrupt = Fixture()
        corrupt.credentialPreferences.edit()
            .putInt("profile:$PROFILE_ID:credential_version", 99)
            .putInt("profile:$PROFILE_ID:iterations", 1_000)
            .putString("profile:$PROFILE_ID:salt", base64(ByteArray(16) { 1 }))
            .putString("profile:$PROFILE_ID:verifier", base64(ByteArray(32) { 2 }))
            .apply()

        assertEquals(ProfilePinCredentialStatus.Corrupt, corrupt.store.credentialStatus(PROFILE_ID))
        assertTrue(corrupt.store.hasPin(PROFILE_ID))
        var corruptUnavailable = false
        try {
            runBlocking { corrupt.store.verifyPin(PROFILE_ID, "1234") }
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            corruptUnavailable = true
        }
        assertTrue(corruptUnavailable)
        assertRequiresPin(corrupt.store.credentialStatus(PROFILE_ID))

        val metadataOnly = Fixture()
        metadataOnly.profilePreferences.edit()
            .putBoolean("profile:$PROFILE_ID:pin_enabled", true)
            .putInt("profile:$PROFILE_ID:pin_credential_version", 1)
            .apply()
        assertEquals(ProfilePinCredentialStatus.Corrupt, metadataOnly.store.credentialStatus(PROFILE_ID))
        assertTrue(metadataOnly.store.hasPin(PROFILE_ID))
        var metadataUnavailable = false
        try {
            runBlocking { metadataOnly.store.verifyPin(PROFILE_ID, "1234") }
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            metadataUnavailable = true
        }
        assertTrue(metadataUnavailable)
        assertRequiresPin(metadataOnly.store.credentialStatus(PROFILE_ID))
    }

    @Test
    fun `verified change and clear transitions update credential and foundation`() = runBlocking {
        val fixture = Fixture()
        assertTrue(fixture.store.setPin(PROFILE_ID, "1234"))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "1234"))
        assertTrue(fixture.store.setPin(PROFILE_ID, "5678"))
        assertFalse(fixture.store.verifyPin(PROFILE_ID, "1234"))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "5678"))

        assertTrue(fixture.store.clearPin(PROFILE_ID))
        assertFalse(fixture.store.hasPin(PROFILE_ID))
        assertFalse(fixture.store.verifyPin(PROFILE_ID, "5678"))
        assertEquals(
            ProfilePinFoundation(enabled = false, credentialVersion = 0),
            fixture.profilePreferencesStore.pinFoundation(PROFILE_ID),
        )
    }

    @Test
    fun `cooldown decision is enforced through the real store verification path`() = runBlocking {
        val fixture = Fixture(attemptProtection = FakeAttemptProtection(lockAfterFailures = 2))
        assertTrue(fixture.store.setPin(PROFILE_ID, "1234"))

        assertFalse(fixture.store.verifyPin(PROFILE_ID, "9999"))
        var secondFailureLocked = false
        try {
            runBlocking { fixture.store.verifyPin(PROFILE_ID, "9999") }
        } catch (_: FourDigitCredentialLockedException) {
            secondFailureLocked = true
        }
        assertTrue(secondFailureLocked)

        var correctWhileLocked = false
        try {
            runBlocking { fixture.store.verifyPin(PROFILE_ID, "1234") }
        } catch (_: FourDigitCredentialLockedException) {
            correctWhileLocked = true
        }
        assertTrue(correctWhileLocked)
    }

    @Test
    fun `same profile pin in another account does not authorize this account`() = runBlocking {
        val first = Fixture(accountId = "account-a")
        val second = Fixture(accountId = "account-b")
        assertTrue(first.store.setPin(PROFILE_ID, "2468"))
        assertTrue(first.store.verifyPin(PROFILE_ID, "2468"))

        assertFalse(second.store.hasPin(PROFILE_ID))
        assertFalse(second.store.verifyPin(PROFILE_ID, "2468"))
    }

    private fun assertRequiresPin(status: ProfilePinCredentialStatus) {
        assertEquals(
            ProfileSwitchAuthorization.REQUIRE_TARGET_PIN,
            profileSwitchAuthorization(
                currentProfileId = "adult",
                currentProfileKind = ProfileKind.STANDARD,
                targetProfileId = PROFILE_ID,
                targetProfileKind = ProfileKind.KIDS,
                targetProtected = profilePinRequiresProtection(status),
                resolvedForSession = true,
                parentalCodeAvailable = false,
            ),
        )
    }

    private class Fixture(
        accountId: String = ACCOUNT_ID,
        attemptProtection: CredentialAttemptProtection = FakeAttemptProtection(),
    ) {
        val scopeState = FakeSharedPreferences()
        val profilePreferences = FakeSharedPreferences()
        val credentialPreferences = FakeSharedPreferences()
        private val otherPreferences = mutableMapOf<String, FakeSharedPreferences>()
        private val accountScope = AccountScopeStore(scopeState) { name ->
            when {
                name.startsWith("hulk_profiles_v1.account.") -> profilePreferences
                name.startsWith("hulk_profile_preferences_v1.account.") -> profilePreferences
                name.startsWith("hulk_profile_pin_credentials_v1.account.") -> credentialPreferences
                else -> otherPreferences.getOrPut(name) { FakeSharedPreferences() }
            }
        }
        private val profileStore = ProfileStore(accountScope) { name ->
            if (name == ProfileStore.PREFERENCES_NAME) profilePreferences else profilePreferences
        }
        val profilePreferencesStore = ProfilePreferencesStore(accountScope, profileStore)
        val store = ProfilePinCredentialStore(
            accountScope = accountScope,
            profilePreferencesStore = profilePreferencesStore,
            secureRandom = SecureRandom(),
            attemptProtection = attemptProtection,
            codec = PinCredentialCodec(
                encode = { bytes -> Base64.getEncoder().encodeToString(bytes) },
                decode = { raw -> runCatching { Base64.getDecoder().decode(raw) }.getOrNull() },
            ),
            scopedPreferencesForAccount = { credentialPreferences },
            cpuDispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.IO,
        )

        init {
            check(accountScope.bind(accountId))
            profilePreferences.edit()
                .putString(
                    "profiles",
                    JSONArray().put(
                        JSONObject()
                            .put("id", PROFILE_ID)
                            .put("displayName", "Kids")
                            .put("kind", "KIDS")
                            .put("avatarKey", "default")
                            .put("createdAtEpochMs", 1_000L)
                            .put("isPrimary", false),
                    ).toString(),
                )
                .putString("active_profile_id", PROFILE_ID)
                .putInt("schema_version", 1)
                .apply()
        }
    }

    private class FakeAttemptProtection(
        private val lockAfterFailures: Int = Int.MAX_VALUE,
    ) : CredentialAttemptProtection {
        private var failures = 0
        private var locked = false

        override fun check(
            preferences: SharedPreferences,
            prefix: String,
        ): FourDigitCredentialAttemptDecision =
            if (locked) {
                FourDigitCredentialAttemptDecision.Locked(30_000L)
            } else {
                FourDigitCredentialAttemptDecision.Allowed
            }

        override fun recordFailure(
            preferences: SharedPreferences,
            prefix: String,
        ): FourDigitCredentialAttemptDecision {
            failures += 1
            if (failures >= lockAfterFailures) {
                locked = true
                return FourDigitCredentialAttemptDecision.Locked(30_000L)
            }
            return FourDigitCredentialAttemptDecision.Allowed
        }

        override fun reset(preferences: SharedPreferences, prefix: String): Boolean {
            failures = 0
            locked = false
            return true
        }

        override fun removeFromEditor(
            editor: SharedPreferences.Editor,
            prefix: String,
        ): SharedPreferences.Editor = editor
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val PROFILE_ID = "kids-1"

        fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    }
}

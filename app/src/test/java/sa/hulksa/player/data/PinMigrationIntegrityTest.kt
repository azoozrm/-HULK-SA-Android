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
import org.junit.Assert.assertTrue
import org.junit.Test

class PinMigrationIntegrityTest {
    @Test
    fun `partial scoped credential is never rebuilt from obsolete legacy pin`() = runBlocking {
        val fixture = Fixture()
        fixture.seedProfiles()
        fixture.seedFoundation(ACCOUNT_A)
        fixture.seedCredential(fixture.legacyCredentials, "1234")

        assertTrue(fixture.store.hasPin(PROFILE_ID))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "1234"))
        assertTrue(fixture.store.setPin(PROFILE_ID, "5678"))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "5678"))
        assertFalse(fixture.store.verifyPin(PROFILE_ID, "1234"))

        val scoped = fixture.credentials(ACCOUNT_A)
        scoped.edit()
            .remove("profile:$PROFILE_ID:salt")
            .remove("profile:$PROFILE_ID:verifier")
            .remove("__account_scope_migrated_v1")
            .apply()

        assertTrue(fixture.store.hasPin(PROFILE_ID))
        assertEquals(ProfilePinCredentialStatus.Corrupt, fixture.store.credentialStatus(PROFILE_ID))
        assertFalse(scoped.contains("profile:$PROFILE_ID:salt") && scoped.contains("profile:$PROFILE_ID:verifier"))

        var oldPinUnavailable = false
        try {
            fixture.store.verifyPin(PROFILE_ID, "1234")
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            oldPinUnavailable = true
        }
        assertTrue(oldPinUnavailable)

        var newPinUnavailable = false
        try {
            fixture.store.verifyPin(PROFILE_ID, "5678")
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            newPinUnavailable = true
        }
        assertTrue(newPinUnavailable)

        val recreated = fixture.newStore()
        assertEquals(ProfilePinCredentialStatus.Corrupt, recreated.credentialStatus(PROFILE_ID))
        assertTrue(recreated.hasPin(PROFILE_ID))
    }

    @Test
    fun `other account credentials remain isolated while account A is partially corrupted`() = runBlocking {
        val fixture = Fixture()
        fixture.seedProfiles()
        fixture.seedFoundation(ACCOUNT_A)
        fixture.seedFoundation(ACCOUNT_B)
        fixture.seedCredential(fixture.credentials(ACCOUNT_B), "2468")

        var accountAUnavailable = false
        try {
            fixture.store.verifyPin(PROFILE_ID, "2468")
        } catch (_: FourDigitCredentialProtectionUnavailableException) {
            accountAUnavailable = true
        }
        assertTrue(accountAUnavailable)
        fixture.accountScope.bind(ACCOUNT_B)
        assertTrue(fixture.store.hasPin(PROFILE_ID))
        assertTrue(fixture.store.verifyPin(PROFILE_ID, "2468"))
        assertFalse(fixture.store.verifyPin(PROFILE_ID, "1234"))
    }

    private class Fixture {
        private val context = TestAppContext()
        val scopeState = context.getSharedPreferences("hulk_account_scope_v1", 0)
        val legacyCredentials = context.getSharedPreferences("hulk_profile_pin_credentials_v1", 0)
        val accountScope = AccountScopeStore(scopeState) { name ->
            context.getSharedPreferences(name, 0)
        }
        val profileStore = ProfileStore(accountScope) { name ->
            context.getSharedPreferences(name, 0)
        }
        val profilePreferencesStore = ProfilePreferencesStore(accountScope, profileStore)
        val store = newStore()

        init {
            check(accountScope.bind(ACCOUNT_A))
        }

        fun newStore(): ProfilePinCredentialStore = ProfilePinCredentialStore(
            accountScope = accountScope,
            profilePreferencesStore = profilePreferencesStore,
            secureRandom = SecureRandom(),
            attemptProtection = AllowedAttemptProtection,
            codec = PinCredentialCodec(
                encode = { bytes -> Base64.getEncoder().encodeToString(bytes) },
                decode = { raw -> runCatching { Base64.getDecoder().decode(raw) }.getOrNull() },
            ),
            scopedPreferencesForAccount = { accountId ->
                context.getSharedPreferences(
                    accountScopedPreferencesName(
                        ProfilePinCredentialStore.PREFERENCES_NAME,
                        accountId,
                    ),
                    0,
                )
            },
            cpuDispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.IO,
        )

        fun credentials(accountId: String): SharedPreferences =
            context.getSharedPreferences(
                accountScopedPreferencesName(
                    ProfilePinCredentialStore.PREFERENCES_NAME,
                    accountId,
                ),
                0,
            )

        fun seedProfiles() {
            listOf(ACCOUNT_A, ACCOUNT_B).forEach { accountId ->
                context.getSharedPreferences(
                    accountScopedPreferencesName(ProfileStore.PREFERENCES_NAME, accountId),
                    0,
                ).edit()
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

        fun seedFoundation(accountId: String) {
            context.getSharedPreferences(
                accountScopedPreferencesName("hulk_profile_preferences_v1", accountId),
                0,
            ).edit()
                .putBoolean("profile:$PROFILE_ID:pin_enabled", true)
                .putInt("profile:$PROFILE_ID:pin_credential_version", 1)
                .apply()
        }

        fun seedCredential(preferences: SharedPreferences, pin: String) {
            val salt = ByteArray(16) { 1 }
            val verifier = deriveProfilePinVerifier(pin, salt, 1_000)
            preferences.edit()
                .putInt("profile:$PROFILE_ID:credential_version", 1)
                .putInt("profile:$PROFILE_ID:iterations", 1_000)
                .putString("profile:$PROFILE_ID:salt", Base64.getEncoder().encodeToString(salt))
                .putString("profile:$PROFILE_ID:verifier", Base64.getEncoder().encodeToString(verifier))
                .apply()
        }
    }

    private object AllowedAttemptProtection : CredentialAttemptProtection {
        override fun check(
            preferences: SharedPreferences,
            prefix: String,
        ): FourDigitCredentialAttemptDecision = FourDigitCredentialAttemptDecision.Allowed

        override fun recordFailure(
            preferences: SharedPreferences,
            prefix: String,
        ): FourDigitCredentialAttemptDecision = FourDigitCredentialAttemptDecision.Allowed

        override fun reset(preferences: SharedPreferences, prefix: String): Boolean = true

        override fun removeFromEditor(
            editor: SharedPreferences.Editor,
            prefix: String,
        ): SharedPreferences.Editor = editor
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val PROFILE_ID = "kids-1"
    }
}

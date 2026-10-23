package sa.hulksa.player.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.ui.ProfileSwitchAuthorization
import sa.hulksa.player.ui.profileSwitchAuthorization

class ProfilePinIntegrityTest {
    @Test
    fun `valid protected credential is verified as valid`() {
        val preferences = FakeSharedPreferences()
        seedValid(preferences)

        val status = status(preferences, foundationEnabled = true)

        assertTrue(status is ProfilePinCredentialStatus.Valid)
        assertTrue(profilePinRequiresProtection(status))
        assertNotNull(profilePinVerificationCredential(status))
    }

    @Test
    fun `genuinely absent credential is the only unprotected state`() {
        val preferences = FakeSharedPreferences()
        val absent = status(preferences, foundationEnabled = false)

        assertEquals(ProfilePinCredentialStatus.Absent, absent)
        assertNull(profilePinVerificationCredential(absent))
        assertFalseProtection(absent)
    }

    @Test
    fun `enabled metadata with missing credential remains protected`() {
        val preferences = FakeSharedPreferences()
        val status = status(preferences, foundationEnabled = true)

        assertEquals(ProfilePinCredentialStatus.Corrupt, status)
        assertTrue(profilePinRequiresProtection(status))
        assertNull(profilePinVerificationCredential(status))
        assertRequiresPin(status)
    }

    @Test
    fun `residual credential version metadata also remains protected`() {
        val preferences = FakeSharedPreferences()
        val status = readProfilePinCredentialStatus(
            preferences = preferences,
            profileId = PROFILE_ID,
            foundation = ProfilePinFoundation(enabled = false, credentialVersion = 1),
            decodeBase64 = { raw -> runCatching { Base64.getDecoder().decode(raw) }.getOrNull() },
        )

        assertEquals(ProfilePinCredentialStatus.Corrupt, status)
        assertTrue(profilePinRequiresProtection(status))
        assertNull(profilePinVerificationCredential(status))
    }

    @Test
    fun `partial unsupported invalid and wrongly typed records remain protected`() {
        val partial = FakeSharedPreferences().apply {
            edit().putInt(KEY_VERSION, 1).putString(KEY_SALT, base64(ByteArray(16) { 1 })).apply()
        }
        val unsupported = FakeSharedPreferences().apply { seedValid(this) }
        unsupported.edit().putInt(KEY_VERSION, 99).apply()
        val invalidIterations = FakeSharedPreferences().apply { seedValid(this) }
        invalidIterations.edit().putInt(KEY_ITERATIONS, 0).apply()
        val invalidEncoding = FakeSharedPreferences().apply { seedValid(this) }
        invalidEncoding.edit().putString(KEY_SALT, "%%%not-base64%%%").apply()
        val wrongType = FakeSharedPreferences().apply {
            edit()
                .putString(KEY_VERSION, "1")
                .putString(KEY_ITERATIONS, "1000")
                .putString(KEY_SALT, base64(ByteArray(16) { 1 }))
                .putString(KEY_VERIFIER, base64(ByteArray(32) { 2 }))
                .apply()
        }

        listOf(partial, unsupported, invalidIterations, invalidEncoding, wrongType).forEach { prefs ->
            val status = status(prefs, foundationEnabled = false)
            assertEquals(ProfilePinCredentialStatus.Corrupt, status)
            assertTrue(profilePinRequiresProtection(status))
            assertNull(profilePinVerificationCredential(status))
            assertRequiresPin(status)
        }
    }

    @Test
    fun `corrupt protection never produces an unprotected authorization allow`() {
        val corruptStatuses = listOf(
            ProfilePinCredentialStatus.Corrupt,
        )
        corruptStatuses.forEach { status ->
            val authorization = profileSwitchAuthorization(
                currentProfileId = "adult-1",
                currentProfileKind = ProfileKind.STANDARD,
                targetProfileId = "kids-1",
                targetProfileKind = ProfileKind.KIDS,
                targetProtected = profilePinRequiresProtection(status),
                resolvedForSession = true,
                parentalCodeAvailable = false,
            )
            assertEquals(ProfileSwitchAuthorization.REQUIRE_TARGET_PIN, authorization)
        }

        val absentAuthorization = profileSwitchAuthorization(
            currentProfileId = "adult-1",
            currentProfileKind = ProfileKind.STANDARD,
            targetProfileId = "kids-1",
            targetProfileKind = ProfileKind.KIDS,
            targetProtected = profilePinRequiresProtection(ProfilePinCredentialStatus.Absent),
            resolvedForSession = true,
            parentalCodeAvailable = false,
        )
        assertEquals(ProfileSwitchAuthorization.ALLOW, absentAuthorization)
    }

    private fun status(
        preferences: FakeSharedPreferences,
        foundationEnabled: Boolean,
    ): ProfilePinCredentialStatus = readProfilePinCredentialStatus(
        preferences = preferences,
        profileId = PROFILE_ID,
        foundation = ProfilePinFoundation(
            enabled = foundationEnabled,
            credentialVersion = if (foundationEnabled) {
                ProfilePinCredentialStore.CURRENT_CREDENTIAL_VERSION
            } else {
                0
            },
        ),
        decodeBase64 = { raw -> runCatching { Base64.getDecoder().decode(raw) }.getOrNull() },
    )

    private fun assertRequiresPin(status: ProfilePinCredentialStatus) {
        val authorization = profileSwitchAuthorization(
            currentProfileId = "adult-1",
            currentProfileKind = ProfileKind.STANDARD,
            targetProfileId = "kids-1",
            targetProfileKind = ProfileKind.KIDS,
            targetProtected = profilePinRequiresProtection(status),
            resolvedForSession = true,
            parentalCodeAvailable = false,
        )
        assertEquals(ProfileSwitchAuthorization.REQUIRE_TARGET_PIN, authorization)
    }

    private fun assertFalseProtection(status: ProfilePinCredentialStatus) {
        val authorization = profileSwitchAuthorization(
            currentProfileId = "adult-1",
            currentProfileKind = ProfileKind.STANDARD,
            targetProfileId = "kids-1",
            targetProfileKind = ProfileKind.KIDS,
            targetProtected = profilePinRequiresProtection(status),
            resolvedForSession = true,
            parentalCodeAvailable = false,
        )
        assertEquals(ProfileSwitchAuthorization.ALLOW, authorization)
    }

    private fun seedValid(preferences: FakeSharedPreferences) {
        preferences.edit()
            .putInt(KEY_VERSION, ProfilePinCredentialStore.CURRENT_CREDENTIAL_VERSION)
            .putInt(KEY_ITERATIONS, 1_000)
            .putString(KEY_SALT, base64(ByteArray(16) { 1 }))
            .putString(KEY_VERIFIER, base64(ByteArray(32) { 2 }))
            .apply()
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val PROFILE_ID = "kids-1"
        val KEY_VERSION = "profile:$PROFILE_ID:${ProfilePinCredentialStore.KEY_VERSION}"
        val KEY_ITERATIONS = "profile:$PROFILE_ID:${ProfilePinCredentialStore.KEY_ITERATIONS}"
        val KEY_SALT = "profile:$PROFILE_ID:${ProfilePinCredentialStore.KEY_SALT}"
        val KEY_VERIFIER = "profile:$PROFILE_ID:${ProfilePinCredentialStore.KEY_VERIFIER}"
    }
}

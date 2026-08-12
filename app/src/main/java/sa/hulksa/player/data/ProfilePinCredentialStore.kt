package sa.hulksa.player.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal const val PROFILE_PIN_LENGTH = 4

internal fun isValidProfilePin(pin: String): Boolean =
    pin.length == PROFILE_PIN_LENGTH && pin.all { it in '0'..'9' }

internal fun deriveProfilePinVerifier(
    pin: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray {
    require(isValidProfilePin(pin)) { "Profile PIN must contain exactly four digits" }
    require(salt.isNotEmpty()) { "PIN salt must not be empty" }
    require(iterations > 0) { "PIN iteration count must be positive" }

    val password = pin.toCharArray()
    return try {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    } finally {
        password.fill('\u0000')
    }
}

/**
 * Local credential store for profile PIN protection.
 *
 * Raw PIN values are never persisted. Each profile receives an independent random salt and a
 * PBKDF2-HMAC-SHA256 verifier. ProfilePreferencesStore only keeps capability metadata; the actual
 * verifier remains in this dedicated store so profile-owned viewing preferences never contain
 * credential material.
 */
class ProfilePinCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val secureRandom = SecureRandom()

    fun hasPin(profileId: String): Boolean = load(profileId) != null

    @Synchronized
    fun setPin(profileId: String, pin: String): Boolean {
        val id = normalizeProfileId(profileId) ?: return false
        if (!isValidProfilePin(pin)) return false

        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val verifier = runCatching {
            deriveProfilePinVerifier(pin, salt, DEFAULT_ITERATIONS)
        }.getOrNull() ?: return false

        return preferences.edit()
            .putInt(key(id, KEY_VERSION), CURRENT_CREDENTIAL_VERSION)
            .putInt(key(id, KEY_ITERATIONS), DEFAULT_ITERATIONS)
            .putString(key(id, KEY_SALT), Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(key(id, KEY_VERIFIER), Base64.encodeToString(verifier, Base64.NO_WRAP))
            .commit()
    }

    fun verifyPin(profileId: String, pin: String): Boolean {
        if (!isValidProfilePin(pin)) return false
        val credential = load(profileId) ?: return false
        val candidate = runCatching {
            deriveProfilePinVerifier(pin, credential.salt, credential.iterations)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(credential.verifier, candidate)
    }

    @Synchronized
    fun clearPin(profileId: String): Boolean {
        val id = normalizeProfileId(profileId) ?: return false
        return preferences.edit()
            .remove(key(id, KEY_VERSION))
            .remove(key(id, KEY_ITERATIONS))
            .remove(key(id, KEY_SALT))
            .remove(key(id, KEY_VERIFIER))
            .commit()
    }

    private fun load(profileId: String): StoredPinCredential? {
        val id = normalizeProfileId(profileId) ?: return null
        val version = preferences.getInt(key(id, KEY_VERSION), 0)
        if (version != CURRENT_CREDENTIAL_VERSION) return null

        val iterations = preferences.getInt(key(id, KEY_ITERATIONS), 0)
        if (iterations <= 0) return null

        val salt = preferences.getString(key(id, KEY_SALT), null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val verifier = preferences.getString(key(id, KEY_VERIFIER), null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return StoredPinCredential(
            salt = salt,
            verifier = verifier,
            iterations = iterations,
        )
    }

    private fun normalizeProfileId(profileId: String): String? =
        profileId.trim().takeIf(String::isNotBlank)

    private fun key(profileId: String, suffix: String): String = "profile:$profileId:$suffix"

    private data class StoredPinCredential(
        val salt: ByteArray,
        val verifier: ByteArray,
        val iterations: Int,
    )

    companion object {
        const val CURRENT_CREDENTIAL_VERSION = 1
        const val DEFAULT_ITERATIONS = 120_000

        private const val PREFERENCES_NAME = "hulk_profile_pin_credentials_v1"
        private const val KEY_VERSION = "credential_version"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
    }
}

private const val ALGORITHM = "PBKDF2WithHmacSHA256"
private const val KEY_LENGTH_BITS = 256
private const val SALT_BYTES = 16

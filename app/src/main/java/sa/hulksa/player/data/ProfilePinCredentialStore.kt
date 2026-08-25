package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * PBKDF2-HMAC-SHA256 verifier. AccountScopeStore additionally isolates credentials belonging to
 * profiles that share the same local profile id across different accounts.
 *
 * PBKDF2 work is dispatched to [cpuDispatcher]. Blocking SharedPreferences commits, including the
 * matching PIN foundation metadata update, are dispatched to [ioDispatcher].
 */
class ProfilePinCredentialStore(
    context: Context,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val profilePreferencesStore = ProfilePreferencesStore(appContext)
    private val preferences: SharedPreferences
        get() = accountScope.preferences(PREFERENCES_NAME)
    private val secureRandom = SecureRandom()

    fun hasPin(profileId: String): Boolean = load(profileId) != null

    suspend fun setPin(profileId: String, pin: String): Boolean {
        val id = normalizeProfileId(profileId) ?: return false
        if (!isValidProfilePin(pin)) return false

        val credential = try {
            withContext(cpuDispatcher) {
                val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
                StoredPinCredential(
                    salt = salt,
                    verifier = deriveProfilePinVerifier(pin, salt, DEFAULT_ITERATIONS),
                    iterations = DEFAULT_ITERATIONS,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }

        return try {
            withContext(ioDispatcher) {
                var stored = false
                try {
                    stored = persistCredential(id, credential)
                    if (!stored) {
                        false
                    } else {
                        val metadata = profilePreferencesStore.setPinFoundation(
                            profileId = id,
                            enabled = true,
                            credentialVersion = CURRENT_CREDENTIAL_VERSION,
                        )
                        if (metadata == null) {
                            removeCredential(id)
                            false
                        } else {
                            true
                        }
                    }
                } catch (_: Exception) {
                    if (stored) runCatching { removeCredential(id) }
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    suspend fun verifyPin(profileId: String, pin: String): Boolean {
        if (!isValidProfilePin(pin)) return false

        val credential = try {
            withContext(ioDispatcher) { load(profileId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } ?: return false

        return try {
            withContext(cpuDispatcher) {
                val candidate = deriveProfilePinVerifier(
                    pin = pin,
                    salt = credential.salt,
                    iterations = credential.iterations,
                )
                MessageDigest.isEqual(credential.verifier, candidate)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearPin(profileId: String): Boolean {
        val id = normalizeProfileId(profileId) ?: return false
        return try {
            withContext(ioDispatcher) {
                try {
                    val cleared = removeCredential(id)
                    val metadata = profilePreferencesStore.setPinFoundation(
                        profileId = id,
                        enabled = false,
                        credentialVersion = 0,
                    )
                    cleared && metadata != null
                } catch (_: Exception) {
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    /**
     * Removes only the credential after the owning profile has already been deleted.
     * ProfilePreferencesStore cleanup is handled by the profile deletion path.
     */
    suspend fun clearCredential(profileId: String): Boolean {
        val id = normalizeProfileId(profileId) ?: return false
        return try {
            withContext(ioDispatcher) {
                try {
                    removeCredential(id)
                } catch (_: Exception) {
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    /**
     * Account-pinned variant used by asynchronous profile deletion cleanup so a later account
     * switch cannot redirect the credential removal into another subscriber scope.
     */
    suspend fun clearCredential(accountId: String, profileId: String): Boolean {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank) ?: return false
        val id = normalizeProfileId(profileId) ?: return false
        val scopedPreferences = appContext.getSharedPreferences(
            accountScopedPreferencesName(PREFERENCES_NAME, normalizedAccountId),
            Context.MODE_PRIVATE,
        )
        return try {
            withContext(ioDispatcher) {
                try {
                    removeCredential(id, scopedPreferences)
                } catch (_: Exception) {
                    false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    @Synchronized
    private fun persistCredential(
        profileId: String,
        credential: StoredPinCredential,
    ): Boolean = preferences.edit()
        .putInt(key(profileId, KEY_VERSION), CURRENT_CREDENTIAL_VERSION)
        .putInt(key(profileId, KEY_ITERATIONS), credential.iterations)
        .putString(key(profileId, KEY_SALT), Base64.encodeToString(credential.salt, Base64.NO_WRAP))
        .putString(
            key(profileId, KEY_VERIFIER),
            Base64.encodeToString(credential.verifier, Base64.NO_WRAP),
        )
        .commit()

    @Synchronized
    private fun removeCredential(
        profileId: String,
        targetPreferences: SharedPreferences = preferences,
    ): Boolean = targetPreferences.edit()
        .remove(key(profileId, KEY_VERSION))
        .remove(key(profileId, KEY_ITERATIONS))
        .remove(key(profileId, KEY_SALT))
        .remove(key(profileId, KEY_VERIFIER))
        .commit()

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

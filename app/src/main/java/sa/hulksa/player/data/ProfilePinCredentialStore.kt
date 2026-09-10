package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val FOUR_DIGIT_CREDENTIAL_LENGTH = 4
internal const val PROFILE_PIN_LENGTH = FOUR_DIGIT_CREDENTIAL_LENGTH

internal const val FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT = 5
internal val FOUR_DIGIT_LOCKOUT_DURATIONS_MS = longArrayOf(
    30_000L,
    120_000L,
    600_000L,
    1_800_000L,
)

internal const val ATTEMPT_KEY_FAILED_ATTEMPTS = "failed_attempts"
internal const val ATTEMPT_KEY_LOCKOUT_LEVEL = "lockout_level"
internal const val ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS = "lockout_started_epoch_ms"
internal const val ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS = "lockout_started_elapsed_ms"
internal const val ATTEMPT_KEY_LOCKOUT_DURATION_MS = "lockout_duration_ms"
internal const val ATTEMPT_KEY_LOCKOUT_BOOT_COUNT = "lockout_boot_count"

internal fun credentialAttemptKey(prefix: String, suffix: String): String = "$prefix:$suffix"

internal class FourDigitCredentialLockedException(
    val retryAfterMs: Long,
) : Exception("Four-digit credential verification is temporarily locked")

internal class FourDigitCredentialProtectionUnavailableException :
    Exception("Four-digit credential attempt protection is unavailable")

internal fun isValidFourDigitCredential(value: String): Boolean =
    value.length == FOUR_DIGIT_CREDENTIAL_LENGTH && value.all { it in '0'..'9' }

internal fun isValidProfilePin(pin: String): Boolean =
    isValidFourDigitCredential(pin)

internal fun deriveFourDigitCredentialVerifier(
    value: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray {
    require(isValidFourDigitCredential(value)) {
        "Credential must contain exactly four digits"
    }
    require(salt.isNotEmpty()) { "Credential salt must not be empty" }
    require(iterations > 0) { "Credential iteration count must be positive" }

    val password = value.toCharArray()
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

internal fun deriveProfilePinVerifier(
    pin: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray = deriveFourDigitCredentialVerifier(pin, salt, iterations)

internal data class SecureFourDigitCredentialSnapshot(
    val salt: ByteArray,
    val verifier: ByteArray,
    val iterations: Int,
)

internal data class FourDigitCredentialAttemptState(
    val failedAttempts: Int = 0,
    val lockoutLevel: Int = 0,
    val lockoutStartedEpochMs: Long = 0L,
    val lockoutStartedElapsedMs: Long = 0L,
    val lockoutDurationMs: Long = 0L,
    val lockoutBootCount: Int? = null,
)

internal fun fourDigitCredentialStateForCurrentBoot(
    state: FourDigitCredentialAttemptState,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    currentBootCount: Int?,
): FourDigitCredentialAttemptState {
    if (state.lockoutDurationMs <= 0L) return state

    val elapsedDelta = nowElapsedMs - state.lockoutStartedElapsedMs
    val sameBoot = when {
        state.lockoutBootCount != null && currentBootCount != null ->
            state.lockoutBootCount == currentBootCount
        state.lockoutBootCount == null && currentBootCount == null -> elapsedDelta >= 0L
        else -> false
    }
    if (sameBoot && elapsedDelta >= 0L) return state

    return state.copy(
        lockoutStartedEpochMs = nowEpochMs,
        lockoutStartedElapsedMs = nowElapsedMs,
        lockoutBootCount = currentBootCount,
    )
}

internal fun fourDigitCredentialRemainingLockoutMs(
    state: FourDigitCredentialAttemptState,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    currentBootCount: Int?,
): Long {
    if (state.lockoutDurationMs <= 0L) return 0L

    val currentBootState = fourDigitCredentialStateForCurrentBoot(
        state = state,
        nowEpochMs = nowEpochMs,
        nowElapsedMs = nowElapsedMs,
        currentBootCount = currentBootCount,
    )
    val elapsedDelta = (nowElapsedMs - currentBootState.lockoutStartedElapsedMs).coerceAtLeast(0L)
    return (currentBootState.lockoutDurationMs - elapsedDelta).coerceAtLeast(0L)
}

internal fun fourDigitCredentialStateAfterFailure(
    state: FourDigitCredentialAttemptState,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    currentBootCount: Int?,
): FourDigitCredentialAttemptState {
    val nextFailures = state.failedAttempts + 1
    if (nextFailures < FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT) {
        return state.copy(failedAttempts = nextFailures)
    }

    val durationIndex = state.lockoutLevel.coerceIn(0, FOUR_DIGIT_LOCKOUT_DURATIONS_MS.lastIndex)
    val durationMs = FOUR_DIGIT_LOCKOUT_DURATIONS_MS[durationIndex]
    val nextLevel = (durationIndex + 1).coerceAtMost(FOUR_DIGIT_LOCKOUT_DURATIONS_MS.lastIndex)
    return FourDigitCredentialAttemptState(
        failedAttempts = 0,
        lockoutLevel = nextLevel,
        lockoutStartedEpochMs = nowEpochMs,
        lockoutStartedElapsedMs = nowElapsedMs,
        lockoutDurationMs = durationMs,
        lockoutBootCount = currentBootCount,
    )
}

internal sealed interface FourDigitCredentialAttemptDecision {
    data object Allowed : FourDigitCredentialAttemptDecision
    data class Locked(val retryAfterMs: Long) : FourDigitCredentialAttemptDecision
    data object Unavailable : FourDigitCredentialAttemptDecision
}

internal class FourDigitCredentialAttemptProtection(
    private val context: Context,
) {
    fun check(
        preferences: SharedPreferences,
        prefix: String,
    ): FourDigitCredentialAttemptDecision {
        val state = readState(preferences, prefix) ?: return FourDigitCredentialAttemptDecision.Unavailable
        val nowEpochMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val currentBootState = fourDigitCredentialStateForCurrentBoot(
            state = state,
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs,
            currentBootCount = bootCount,
        )
        if (currentBootState != state && !persistState(preferences, prefix, currentBootState)) {
            return FourDigitCredentialAttemptDecision.Unavailable
        }
        val remaining = fourDigitCredentialRemainingLockoutMs(
            state = currentBootState,
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs,
            currentBootCount = bootCount,
        )
        return if (remaining > 0L) {
            FourDigitCredentialAttemptDecision.Locked(remaining)
        } else {
            FourDigitCredentialAttemptDecision.Allowed
        }
    }

    fun recordFailure(
        preferences: SharedPreferences,
        prefix: String,
    ): FourDigitCredentialAttemptDecision {
        val state = readState(preferences, prefix) ?: return FourDigitCredentialAttemptDecision.Unavailable
        val nowEpochMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val currentBootState = fourDigitCredentialStateForCurrentBoot(
            state = state,
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs,
            currentBootCount = bootCount,
        )
        if (currentBootState != state && !persistState(preferences, prefix, currentBootState)) {
            return FourDigitCredentialAttemptDecision.Unavailable
        }
        val remaining = fourDigitCredentialRemainingLockoutMs(
            state = currentBootState,
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs,
            currentBootCount = bootCount,
        )
        if (remaining > 0L) {
            return FourDigitCredentialAttemptDecision.Locked(remaining)
        }

        val stateForFailure = if (currentBootState.lockoutDurationMs > 0L) {
            currentBootState.copy(
                lockoutStartedEpochMs = 0L,
                lockoutStartedElapsedMs = 0L,
                lockoutDurationMs = 0L,
                lockoutBootCount = null,
            )
        } else {
            currentBootState
        }
        val updated = fourDigitCredentialStateAfterFailure(
            state = stateForFailure,
            nowEpochMs = nowEpochMs,
            nowElapsedMs = nowElapsedMs,
            currentBootCount = bootCount,
        )
        if (!persistState(preferences, prefix, updated)) {
            return FourDigitCredentialAttemptDecision.Unavailable
        }
        return if (updated.lockoutDurationMs > 0L) {
            FourDigitCredentialAttemptDecision.Locked(updated.lockoutDurationMs)
        } else {
            FourDigitCredentialAttemptDecision.Allowed
        }
    }

    fun reset(
        preferences: SharedPreferences,
        prefix: String,
    ): Boolean = runCatching {
        preferences.edit()
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_FAILED_ATTEMPTS))
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_LEVEL))
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS))
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS))
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_DURATION_MS))
            .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))
            .commit()
    }.getOrDefault(false)

    fun removeFromEditor(
        editor: SharedPreferences.Editor,
        prefix: String,
    ): SharedPreferences.Editor = editor
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_FAILED_ATTEMPTS))
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_LEVEL))
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS))
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS))
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_DURATION_MS))
        .remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))

    private fun readState(
        preferences: SharedPreferences,
        prefix: String,
    ): FourDigitCredentialAttemptState? = runCatching {
        val state = FourDigitCredentialAttemptState(
            failedAttempts = preferences.getInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_FAILED_ATTEMPTS),
                0,
            ),
            lockoutLevel = preferences.getInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_LEVEL),
                0,
            ),
            lockoutStartedEpochMs = preferences.getLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS),
                0L,
            ),
            lockoutStartedElapsedMs = preferences.getLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
                0L,
            ),
            lockoutDurationMs = preferences.getLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_DURATION_MS),
                0L,
            ),
            lockoutBootCount = if (
                preferences.contains(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))
            ) {
                preferences.getInt(
                    credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT),
                    0,
                )
            } else {
                null
            },
        )
        require(state.failedAttempts in 0 until FOUR_DIGIT_FAILURES_BEFORE_LOCKOUT)
        require(state.lockoutLevel in FOUR_DIGIT_LOCKOUT_DURATIONS_MS.indices)
        require(state.lockoutDurationMs >= 0L)
        if (state.lockoutDurationMs > 0L) {
            require(state.lockoutStartedEpochMs > 0L)
            require(state.lockoutStartedElapsedMs >= 0L)
            require(state.lockoutDurationMs in FOUR_DIGIT_LOCKOUT_DURATIONS_MS)
        }
        state
    }.getOrNull()

    private fun persistState(
        preferences: SharedPreferences,
        prefix: String,
        state: FourDigitCredentialAttemptState,
    ): Boolean = runCatching {
        val editor = preferences.edit()
            .putInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_FAILED_ATTEMPTS),
                state.failedAttempts,
            )
            .putInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_LEVEL),
                state.lockoutLevel,
            )
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_EPOCH_MS),
                state.lockoutStartedEpochMs,
            )
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_STARTED_ELAPSED_MS),
                state.lockoutStartedElapsedMs,
            )
            .putLong(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_DURATION_MS),
                state.lockoutDurationMs,
            )
        if (state.lockoutBootCount != null) {
            editor.putInt(
                credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT),
                state.lockoutBootCount,
            )
        } else {
            editor.remove(credentialAttemptKey(prefix, ATTEMPT_KEY_LOCKOUT_BOOT_COUNT))
        }
        editor.commit()
    }.getOrDefault(false)

    private fun currentBootCount(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()
    }
}

private val credentialVerificationMutexes = ConcurrentHashMap<String, Mutex>()

internal fun credentialVerificationMutex(ownerKey: String): Mutex =
    credentialVerificationMutexes.getOrPut(ownerKey) { Mutex() }

/**
 * Local credential store for profile PIN protection.
 *
 * Raw PIN values are never persisted. Each profile receives an independent random salt and a
 * PBKDF2-HMAC-SHA256 verifier. AccountScopeStore additionally isolates credentials belonging to
 * profiles that share the same local profile id across different accounts.
 *
 * Failed verification attempts are persisted beside the credential under the same account/profile
 * owner. Five failures enter an escalating bounded cooldown (30s, 2m, 10m, then 30m maximum).
 * Elapsed realtime is authoritative. When a reboot is detected, the current bounded cooldown is
 * conservatively restarted against elapsed realtime in the new boot; wall clock is never used to
 * end it early. Successful verification clears the attempt state durably.
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
    private val attemptProtection = FourDigitCredentialAttemptProtection(appContext)

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
        val id = normalizeProfileId(profileId) ?: return false
        if (!isValidProfilePin(pin)) return false
        val accountId = try {
            withContext(ioDispatcher) { accountScope.activeAccountId() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return false
        val targetPreferences = preferencesForAccount(accountId)
        val attemptPrefix = attemptPrefix(id)
        val ownerKey = "profile-pin:$accountId:$id"

        return credentialVerificationMutex(ownerKey).withLock {
            when (
                val decision = withContext(ioDispatcher) {
                    attemptProtection.check(targetPreferences, attemptPrefix)
                }
            ) {
                FourDigitCredentialAttemptDecision.Allowed -> Unit
                is FourDigitCredentialAttemptDecision.Locked ->
                    throw FourDigitCredentialLockedException(decision.retryAfterMs)
                FourDigitCredentialAttemptDecision.Unavailable ->
                    throw FourDigitCredentialProtectionUnavailableException()
            }

            val credential = try {
                withContext(ioDispatcher) { load(id, targetPreferences) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@withLock false

            val matches = try {
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

            if (matches) {
                val stillCurrentOwner = try {
                    withContext(ioDispatcher) { accountScope.activeAccountId() == accountId }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!stillCurrentOwner) return@withLock false
                val reset = withContext(ioDispatcher) {
                    attemptProtection.reset(targetPreferences, attemptPrefix)
                }
                if (!reset) throw FourDigitCredentialProtectionUnavailableException()
                true
            } else {
                when (
                    val decision = withContext(ioDispatcher) {
                        attemptProtection.recordFailure(targetPreferences, attemptPrefix)
                    }
                ) {
                    FourDigitCredentialAttemptDecision.Allowed -> false
                    is FourDigitCredentialAttemptDecision.Locked ->
                        throw FourDigitCredentialLockedException(decision.retryAfterMs)
                    FourDigitCredentialAttemptDecision.Unavailable ->
                        throw FourDigitCredentialProtectionUnavailableException()
                }
            }
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
        val scopedPreferences = preferencesForAccount(normalizedAccountId)
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

    /**
     * Reads an opaque salted verifier for the one-time parental-code compatibility migration.
     * The profile credential remains untouched and the explicit account id prevents an account
     * switch during asynchronous migration from redirecting the read into another scope.
     */
    internal suspend fun credentialSnapshotForMigration(
        accountId: String,
        profileId: String,
    ): SecureFourDigitCredentialSnapshot? {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank) ?: return null
        val id = normalizeProfileId(profileId) ?: return null
        return withContext(ioDispatcher) {
            val scopedPreferences = if (accountScope.activeAccountId() == normalizedAccountId) {
                accountScope.preferences(PREFERENCES_NAME)
            } else {
                preferencesForAccount(normalizedAccountId)
            }
            load(id, scopedPreferences)?.let { credential ->
                SecureFourDigitCredentialSnapshot(
                    salt = credential.salt.copyOf(),
                    verifier = credential.verifier.copyOf(),
                    iterations = credential.iterations,
                )
            }
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
    ): Boolean {
        val editor = targetPreferences.edit()
            .remove(key(profileId, KEY_VERSION))
            .remove(key(profileId, KEY_ITERATIONS))
            .remove(key(profileId, KEY_SALT))
            .remove(key(profileId, KEY_VERIFIER))
        return attemptProtection
            .removeFromEditor(editor, attemptPrefix(profileId))
            .commit()
    }

    private fun load(
        profileId: String,
        sourcePreferences: SharedPreferences = preferences,
    ): StoredPinCredential? {
        val id = normalizeProfileId(profileId) ?: return null
        val version = sourcePreferences.getInt(key(id, KEY_VERSION), 0)
        if (version != CURRENT_CREDENTIAL_VERSION) return null

        val iterations = sourcePreferences.getInt(key(id, KEY_ITERATIONS), 0)
        if (iterations <= 0) return null

        val salt = sourcePreferences.getString(key(id, KEY_SALT), null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val verifier = sourcePreferences.getString(key(id, KEY_VERIFIER), null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return StoredPinCredential(
            salt = salt,
            verifier = verifier,
            iterations = iterations,
        )
    }

    private fun preferencesForAccount(accountId: String): SharedPreferences =
        appContext.getSharedPreferences(
            accountScopedPreferencesName(PREFERENCES_NAME, accountId),
            Context.MODE_PRIVATE,
        )

    private fun normalizeProfileId(profileId: String): String? =
        profileId.trim().takeIf(String::isNotBlank)

    private fun key(profileId: String, suffix: String): String = "profile:$profileId:$suffix"

    private fun attemptPrefix(profileId: String): String = "profile:$profileId:attempt_protection"

    private data class StoredPinCredential(
        val salt: ByteArray,
        val verifier: ByteArray,
        val iterations: Int,
    )

    companion object {
        const val CURRENT_CREDENTIAL_VERSION = 1
        const val DEFAULT_ITERATIONS = 120_000

        internal const val PREFERENCES_NAME = "hulk_profile_pin_credentials_v1"
        private const val KEY_VERSION = "credential_version"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
    }
}

private const val ALGORITHM = "PBKDF2WithHmacSHA256"
private const val KEY_LENGTH_BITS = 256
private const val SALT_BYTES = 16
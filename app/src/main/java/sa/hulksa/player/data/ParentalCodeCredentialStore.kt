package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PARENTAL_CODE_LENGTH = FOUR_DIGIT_CREDENTIAL_LENGTH

internal fun isValidParentalCode(code: String): Boolean =
    isValidFourDigitCredential(code)

internal fun deriveParentalCodeVerifier(
    code: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray = deriveFourDigitCredentialVerifier(code, salt, iterations)

internal enum class LegacyParentalCodeMigrationDecision {
    COPY_EXISTING_PROFILE_PIN,
    COMPLETE_WITHOUT_COPY,
}

internal fun legacyParentalCodeMigrationDecision(
    hadKidsProfiles: Boolean,
    legacyPrimaryProfilePinAvailable: Boolean,
): LegacyParentalCodeMigrationDecision =
    if (hadKidsProfiles && legacyPrimaryProfilePinAvailable) {
        LegacyParentalCodeMigrationDecision.COPY_EXISTING_PROFILE_PIN
    } else {
        LegacyParentalCodeMigrationDecision.COMPLETE_WITHOUT_COPY
    }

internal enum class LegacyParentalCodeMigrationResult {
    MIGRATED_LEGACY_PROFILE_PIN,
    COMPLETED_WITHOUT_LEGACY_CREDENTIAL,
    ALREADY_COMPLETED,
    FAILED,
}

/**
 * Account-owned credential for leaving Kids mode and managing profiles.
 *
 * This store deliberately has no profile-id key. A single salted PBKDF2 verifier belongs to the
 * active canonical account scope, so every Kids profile in that account shares the same parental
 * code while another account receives a completely different SharedPreferences namespace.
 *
 * PBKDF2 work runs on [cpuDispatcher]. SharedPreferences reads and commits used by suspend APIs run
 * on [ioDispatcher]. Raw parental codes are never persisted.
 */
class ParentalCodeCredentialStore(
    context: Context,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val secureRandom = SecureRandom()

    fun hasCode(): Boolean {
        val accountId = accountScope.activeAccountId() ?: return false
        return runCatching { load(preferencesForAccount(accountId)) != null }.getOrDefault(false)
    }

    suspend fun setCode(code: String): Boolean {
        if (!isValidParentalCode(code)) return false
        val accountId = accountScope.activeAccountId() ?: return false
        val targetPreferences = preferencesForAccount(accountId)

        val credential = try {
            withContext(cpuDispatcher) {
                val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
                StoredParentalCodeCredential(
                    salt = salt,
                    verifier = deriveParentalCodeVerifier(code, salt, DEFAULT_ITERATIONS),
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
                if (accountScope.activeAccountId() != accountId) {
                    false
                } else {
                    val stored = persistCredential(targetPreferences, credential)
                    stored && accountScope.activeAccountId() == accountId
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun verifyCode(code: String): Boolean {
        if (!isValidParentalCode(code)) return false
        val accountId = accountScope.activeAccountId() ?: return false
        val targetPreferences = preferencesForAccount(accountId)

        val credential = try {
            withContext(ioDispatcher) { load(targetPreferences) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        } ?: return false

        return try {
            withContext(cpuDispatcher) {
                val candidate = deriveParentalCodeVerifier(
                    code = code,
                    salt = credential.salt,
                    iterations = credential.iterations,
                )
                val matches = MessageDigest.isEqual(credential.verifier, candidate)
                accountScope.activeAccountId() == accountId && matches
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One-time compatibility migration for accounts created by the previous architecture.
     *
     * Existing Kids accounts used the Primary Adult profile verifier as their parental
     * credential. Because no trustworthy metadata distinguishes a bootstrap-created PIN from a
     * manually enabled Profile PIN, the verifier is copied into this dedicated namespace and the
     * original Profile PIN is preserved. Accounts without Kids at first migration are marked
     * complete without copying so a future manually-created Profile PIN can never be mistaken for
     * a parental code.
     */
    internal suspend fun ensureLegacyMigration(
        accountId: String,
        hadKidsProfiles: Boolean,
        legacyPrimaryProfileId: String?,
        profilePinCredentialStore: ProfilePinCredentialStore,
    ): LegacyParentalCodeMigrationResult {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank)
            ?: return LegacyParentalCodeMigrationResult.FAILED
        if (accountScope.activeAccountId() != normalizedAccountId) {
            return LegacyParentalCodeMigrationResult.FAILED
        }
        val targetPreferences = preferencesForAccount(normalizedAccountId)

        val migrationComplete = try {
            withContext(ioDispatcher) {
                targetPreferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LegacyParentalCodeMigrationResult.FAILED
        }
        if (migrationComplete) return LegacyParentalCodeMigrationResult.ALREADY_COMPLETED

        val existingCredential = try {
            withContext(ioDispatcher) { load(targetPreferences) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LegacyParentalCodeMigrationResult.FAILED
        }
        if (existingCredential != null) {
            return try {
                withContext(ioDispatcher) {
                    if (markLegacyMigrationComplete(targetPreferences)) {
                        LegacyParentalCodeMigrationResult.ALREADY_COMPLETED
                    } else {
                        LegacyParentalCodeMigrationResult.FAILED
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LegacyParentalCodeMigrationResult.FAILED
            }
        }

        val legacyCredential = if (hadKidsProfiles && legacyPrimaryProfileId != null) {
            try {
                profilePinCredentialStore.credentialSnapshotForMigration(
                    accountId = normalizedAccountId,
                    profileId = legacyPrimaryProfileId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return LegacyParentalCodeMigrationResult.FAILED
            }
        } else {
            null
        }
        val decision = legacyParentalCodeMigrationDecision(
            hadKidsProfiles = hadKidsProfiles,
            legacyPrimaryProfilePinAvailable = legacyCredential != null,
        )

        return try {
            withContext(ioDispatcher) {
                synchronized(this@ParentalCodeCredentialStore) {
                    if (targetPreferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false)) {
                        return@synchronized LegacyParentalCodeMigrationResult.ALREADY_COMPLETED
                    }
                    if (load(targetPreferences) != null) {
                        return@synchronized if (markLegacyMigrationComplete(targetPreferences)) {
                            LegacyParentalCodeMigrationResult.ALREADY_COMPLETED
                        } else {
                            LegacyParentalCodeMigrationResult.FAILED
                        }
                    }

                    when (decision) {
                        LegacyParentalCodeMigrationDecision.COPY_EXISTING_PROFILE_PIN -> {
                            val source = legacyCredential
                                ?: return@synchronized LegacyParentalCodeMigrationResult.FAILED
                            val migrated = persistCredential(
                                targetPreferences = targetPreferences,
                                credential = StoredParentalCodeCredential(
                                    salt = source.salt.copyOf(),
                                    verifier = source.verifier.copyOf(),
                                    iterations = source.iterations,
                                ),
                            )
                            if (migrated) {
                                LegacyParentalCodeMigrationResult.MIGRATED_LEGACY_PROFILE_PIN
                            } else {
                                LegacyParentalCodeMigrationResult.FAILED
                            }
                        }

                        LegacyParentalCodeMigrationDecision.COMPLETE_WITHOUT_COPY -> {
                            if (markLegacyMigrationComplete(targetPreferences)) {
                                LegacyParentalCodeMigrationResult.COMPLETED_WITHOUT_LEGACY_CREDENTIAL
                            } else {
                                LegacyParentalCodeMigrationResult.FAILED
                            }
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LegacyParentalCodeMigrationResult.FAILED
        }
    }

    @Synchronized
    private fun persistCredential(
        targetPreferences: SharedPreferences,
        credential: StoredParentalCodeCredential,
    ): Boolean = targetPreferences.edit()
        .putInt(KEY_VERSION, CURRENT_CREDENTIAL_VERSION)
        .putInt(KEY_ITERATIONS, credential.iterations)
        .putString(KEY_SALT, Base64.encodeToString(credential.salt, Base64.NO_WRAP))
        .putString(KEY_VERIFIER, Base64.encodeToString(credential.verifier, Base64.NO_WRAP))
        .putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true)
        .commit()

    @Synchronized
    private fun markLegacyMigrationComplete(targetPreferences: SharedPreferences): Boolean =
        targetPreferences.edit()
            .putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true)
            .commit()

    private fun load(targetPreferences: SharedPreferences): StoredParentalCodeCredential? {
        val version = targetPreferences.getInt(KEY_VERSION, 0)
        if (version != CURRENT_CREDENTIAL_VERSION) return null

        val iterations = targetPreferences.getInt(KEY_ITERATIONS, 0)
        if (iterations <= 0) return null

        val salt = targetPreferences.getString(KEY_SALT, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val verifier = targetPreferences.getString(KEY_VERIFIER, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return StoredParentalCodeCredential(
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

    private data class StoredParentalCodeCredential(
        val salt: ByteArray,
        val verifier: ByteArray,
        val iterations: Int,
    )

    companion object {
        const val CURRENT_CREDENTIAL_VERSION = 1
        const val DEFAULT_ITERATIONS = ProfilePinCredentialStore.DEFAULT_ITERATIONS
        internal const val PREFERENCES_NAME = "hulk_parental_code_credentials_v1"

        private const val KEY_VERSION = "credential_version"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_profile_pin_migration_complete_v1"
        private const val SALT_BYTES = 16
    }
}

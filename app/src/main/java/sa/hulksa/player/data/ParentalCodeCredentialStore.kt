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
    REQUIRE_LEGACY_PARENT_PROOF_THEN_EXPLICIT_SETUP,
    FAIL_CLOSED_NO_USABLE_PARENT_PROOF,
    COMPLETE_WITHOUT_PARENTAL_CODE,
}

internal fun legacyParentalCodeMigrationDecision(
    hadKidsProfiles: Boolean,
    legacyPrimaryProfilePinAvailable: Boolean,
): LegacyParentalCodeMigrationDecision = when {
    hadKidsProfiles && legacyPrimaryProfilePinAvailable ->
        LegacyParentalCodeMigrationDecision.REQUIRE_LEGACY_PARENT_PROOF_THEN_EXPLICIT_SETUP
    hadKidsProfiles -> LegacyParentalCodeMigrationDecision.FAIL_CLOSED_NO_USABLE_PARENT_PROOF
    else -> LegacyParentalCodeMigrationDecision.COMPLETE_WITHOUT_PARENTAL_CODE
}

internal enum class LegacyParentalCodeMigrationResult {
    LEGACY_PARENT_PROOF_REQUIRED,
    FAIL_CLOSED_NO_USABLE_PARENT_PROOF,
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
 * Only [setCode] writes a credential and every persisted credential carries explicit user-created
 * provenance. A Profile PIN verifier is never copied, promoted, or interpreted as a parental code.
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
                    val stored = persistExplicitCredential(targetPreferences, credential)
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
     * Classifies one-time legacy state without migrating any Profile PIN material.
     *
     * PR #240 previously wrote copied Profile PIN verifiers into a v1 parental namespace during
     * signed qualification. That build was never merged or released, and it did not record
     * trustworthy provenance. This corrected implementation therefore uses a new v2 namespace and
     * leaves the pre-release v1 data untouched and inert instead of guessing whether it was copied
     * or explicitly created.
     *
     * Legacy Kids accounts with a usable Primary Adult Profile PIN must prove that PIN once in the
     * UI and then explicitly create a new parental code. Accounts without usable proof stay
     * fail-closed. No credential bytes are read from ProfilePinCredentialStore here.
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
                targetPreferences.getBoolean(KEY_LEGACY_CLASSIFICATION_COMPLETE, false)
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
                    if (markLegacyClassificationComplete(targetPreferences)) {
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

        val legacyPrimaryProfilePinAvailable = if (
            hadKidsProfiles && legacyPrimaryProfileId != null
        ) {
            try {
                withContext(ioDispatcher) {
                    accountScope.activeAccountId() == normalizedAccountId &&
                        profilePinCredentialStore.hasPin(legacyPrimaryProfileId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return LegacyParentalCodeMigrationResult.FAILED
            }
        } else {
            false
        }

        val decision = legacyParentalCodeMigrationDecision(
            hadKidsProfiles = hadKidsProfiles,
            legacyPrimaryProfilePinAvailable = legacyPrimaryProfilePinAvailable,
        )

        return try {
            withContext(ioDispatcher) {
                synchronized(this@ParentalCodeCredentialStore) {
                    if (targetPreferences.getBoolean(KEY_LEGACY_CLASSIFICATION_COMPLETE, false)) {
                        return@synchronized LegacyParentalCodeMigrationResult.ALREADY_COMPLETED
                    }
                    if (load(targetPreferences) != null) {
                        return@synchronized if (markLegacyClassificationComplete(targetPreferences)) {
                            LegacyParentalCodeMigrationResult.ALREADY_COMPLETED
                        } else {
                            LegacyParentalCodeMigrationResult.FAILED
                        }
                    }
                    if (!markLegacyClassificationComplete(targetPreferences)) {
                        return@synchronized LegacyParentalCodeMigrationResult.FAILED
                    }

                    when (decision) {
                        LegacyParentalCodeMigrationDecision.REQUIRE_LEGACY_PARENT_PROOF_THEN_EXPLICIT_SETUP ->
                            LegacyParentalCodeMigrationResult.LEGACY_PARENT_PROOF_REQUIRED
                        LegacyParentalCodeMigrationDecision.FAIL_CLOSED_NO_USABLE_PARENT_PROOF ->
                            LegacyParentalCodeMigrationResult.FAIL_CLOSED_NO_USABLE_PARENT_PROOF
                        LegacyParentalCodeMigrationDecision.COMPLETE_WITHOUT_PARENTAL_CODE ->
                            LegacyParentalCodeMigrationResult.COMPLETED_WITHOUT_LEGACY_CREDENTIAL
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
    private fun persistExplicitCredential(
        targetPreferences: SharedPreferences,
        credential: StoredParentalCodeCredential,
    ): Boolean = targetPreferences.edit()
        .putInt(KEY_VERSION, CURRENT_CREDENTIAL_VERSION)
        .putInt(KEY_ITERATIONS, credential.iterations)
        .putString(KEY_SALT, Base64.encodeToString(credential.salt, Base64.NO_WRAP))
        .putString(KEY_VERIFIER, Base64.encodeToString(credential.verifier, Base64.NO_WRAP))
        .putString(KEY_PROVENANCE, EXPLICIT_USER_CREATED_PROVENANCE)
        .putBoolean(KEY_LEGACY_CLASSIFICATION_COMPLETE, true)
        .commit()

    @Synchronized
    private fun markLegacyClassificationComplete(targetPreferences: SharedPreferences): Boolean =
        targetPreferences.edit()
            .putBoolean(KEY_LEGACY_CLASSIFICATION_COMPLETE, true)
            .commit()

    private fun load(targetPreferences: SharedPreferences): StoredParentalCodeCredential? {
        val version = targetPreferences.getInt(KEY_VERSION, 0)
        if (version != CURRENT_CREDENTIAL_VERSION) return null
        if (targetPreferences.getString(KEY_PROVENANCE, null) != EXPLICIT_USER_CREATED_PROVENANCE) {
            return null
        }

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
        const val CURRENT_CREDENTIAL_VERSION = 2
        const val DEFAULT_ITERATIONS = ProfilePinCredentialStore.DEFAULT_ITERATIONS
        internal const val PREFERENCES_NAME = "hulk_parental_code_credentials_v2"
        internal const val EXPLICIT_USER_CREATED_PROVENANCE = "EXPLICIT_USER_CREATED"

        private const val KEY_VERSION = "credential_version"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_SALT = "salt"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_PROVENANCE = "credential_provenance"
        private const val KEY_LEGACY_CLASSIFICATION_COMPLETE =
            "legacy_parental_setup_classification_complete_v2"
        private const val SALT_BYTES = 16
    }
}

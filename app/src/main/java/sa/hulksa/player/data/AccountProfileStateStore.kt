package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences

internal data class AccountProfileStateScope(
    val accountId: String,
    val profileId: String,
)

internal enum class LegacyProfileStatePolicy {
    MIGRATE_TO_PROVEN_ACCOUNT,
    CLEAR,
}

internal enum class LegacyProfileStateResult {
    ALREADY_HANDLED,
    MIGRATED,
    CLEARED,
    RETRY_REQUIRED,
}

internal interface AccountProfileStateBackend {
    fun read(accountId: String, key: String): String?

    fun write(accountId: String, key: String, value: String)

    fun remove(accountId: String, key: String)

    fun isLegacyHandled(): Boolean

    fun hasLegacyState(): Boolean

    fun copyLegacyState(accountId: String): Boolean

    fun clearLegacyState(): Boolean

    fun markLegacyHandled(): Boolean
}

/**
 * Owns the account/profile boundary for small persisted UI state.
 *
 * Reads fail closed and writes are ignored until both an authenticated account and an active
 * profile are available. The backend is deliberately injectable so the ownership behavior can be
 * exercised as a real state transition in local JVM tests without Android storage substitutes.
 */
internal class AccountProfileStateCore(
    private val activeAccountId: () -> String?,
    private val activeProfileId: () -> String?,
    private val backend: AccountProfileStateBackend,
) {
    fun activeScope(): AccountProfileStateScope? {
        val accountId = activeAccountId().normalizedStateIdentity() ?: return null
        val profileId = activeProfileId().normalizedStateIdentity() ?: return null
        return AccountProfileStateScope(accountId, profileId)
    }

    fun read(key: String): String? = activeScope()?.let { scope -> read(scope, key) }

    fun read(scope: AccountProfileStateScope, key: String): String? {
        val normalized = scope.normalized() ?: return null
        val stateKey = key.normalizedStateIdentity() ?: return null
        return backend.read(
            accountId = normalized.accountId,
            key = profileStateKey(normalized.profileId, stateKey),
        )
    }

    fun write(key: String, value: String) {
        activeScope()?.let { scope -> write(scope, key, value) }
    }

    fun write(scope: AccountProfileStateScope, key: String, value: String) {
        val normalized = scope.normalized() ?: return
        val stateKey = key.normalizedStateIdentity() ?: return
        backend.write(
            accountId = normalized.accountId,
            key = profileStateKey(normalized.profileId, stateKey),
            value = value,
        )
    }

    fun remove(key: String) {
        activeScope()?.let { scope -> remove(scope.accountId, scope.profileId, key) }
    }

    fun remove(accountId: String, profileId: String, vararg keys: String) {
        val normalizedAccountId = accountId.normalizedStateIdentity() ?: return
        val normalizedProfileId = profileId.normalizedStateIdentity() ?: return
        keys.asSequence()
            .mapNotNull { key -> key.normalizedStateIdentity() }
            .distinct()
            .forEach { key ->
                backend.remove(
                    accountId = normalizedAccountId,
                    key = profileStateKey(normalizedProfileId, key),
                )
            }
    }

    @Synchronized
    fun handleLegacy(
        policy: LegacyProfileStatePolicy,
        provenOwnerAccountId: String?,
    ): LegacyProfileStateResult {
        if (backend.isLegacyHandled()) return LegacyProfileStateResult.ALREADY_HANDLED

        if (!backend.hasLegacyState()) {
            return if (backend.markLegacyHandled()) {
                LegacyProfileStateResult.CLEARED
            } else {
                LegacyProfileStateResult.RETRY_REQUIRED
            }
        }

        val provenOwner = provenOwnerAccountId.normalizedStateIdentity()
        val migrated = policy == LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT &&
            provenOwner != null &&
            backend.copyLegacyState(provenOwner)
        if (
            policy == LegacyProfileStatePolicy.MIGRATE_TO_PROVEN_ACCOUNT &&
            provenOwner != null &&
            !migrated
        ) {
            return LegacyProfileStateResult.RETRY_REQUIRED
        }

        if (!backend.clearLegacyState()) return LegacyProfileStateResult.RETRY_REQUIRED
        if (!backend.markLegacyHandled()) return LegacyProfileStateResult.RETRY_REQUIRED
        return if (migrated) {
            LegacyProfileStateResult.MIGRATED
        } else {
            LegacyProfileStateResult.CLEARED
        }
    }
}

/** Android SharedPreferences adapter used by Search and Live profile state. */
internal class AccountProfileStateStore(
    context: Context,
    basePreferencesName: String,
    legacyPolicy: LegacyProfileStatePolicy,
) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val profileStore by lazy { ProfileStore(appContext) }
    private val core = AccountProfileStateCore(
        activeAccountId = { accountScope.activeAccountId() },
        activeProfileId = { profileStore.activeProfileId() },
        backend = SharedPreferencesAccountProfileStateBackend(
            context = appContext,
            basePreferencesName = basePreferencesName,
        ),
    )

    init {
        core.handleLegacy(
            policy = legacyPolicy,
            provenOwnerAccountId = accountScope.legacyOwnerAccountId(),
        )
    }

    fun activeScope(): AccountProfileStateScope? = core.activeScope()

    fun read(key: String): String? = core.read(key)

    fun read(scope: AccountProfileStateScope, key: String): String? = core.read(scope, key)

    fun write(key: String, value: String) = core.write(key, value)

    fun write(scope: AccountProfileStateScope, key: String, value: String) =
        core.write(scope, key, value)

    fun remove(key: String) = core.remove(key)

    fun remove(accountId: String, profileId: String, vararg keys: String) =
        core.remove(accountId, profileId, *keys)

    companion object {
        fun clearLegacyOnce(context: Context, basePreferencesName: String) {
            val appContext = context.applicationContext
            AccountProfileStateCore(
                activeAccountId = { null },
                activeProfileId = { null },
                backend = SharedPreferencesAccountProfileStateBackend(
                    context = appContext,
                    basePreferencesName = basePreferencesName,
                ),
            ).handleLegacy(
                policy = LegacyProfileStatePolicy.CLEAR,
                provenOwnerAccountId = null,
            )
        }
    }
}

private class SharedPreferencesAccountProfileStateBackend(
    context: Context,
    private val basePreferencesName: String,
) : AccountProfileStateBackend {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(
        basePreferencesName,
        Context.MODE_PRIVATE,
    )
    private val migrationState = appContext.getSharedPreferences(
        MIGRATION_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val migrationKey = "handled:$basePreferencesName"

    override fun read(accountId: String, key: String): String? =
        accountPreferences(accountId).getString(key, null)

    override fun write(accountId: String, key: String, value: String) {
        accountPreferences(accountId).edit().putString(key, value).apply()
    }

    override fun remove(accountId: String, key: String) {
        accountPreferences(accountId).edit().remove(key).apply()
    }

    override fun isLegacyHandled(): Boolean = migrationState.getBoolean(migrationKey, false)

    override fun hasLegacyState(): Boolean = legacyPreferences.all.isNotEmpty()

    override fun copyLegacyState(accountId: String): Boolean {
        val target = accountPreferences(accountId)
        val editor = target.edit()
        legacyPreferences.all.forEach { (key, value) ->
            if (!target.contains(key) && value is String) {
                editor.putString(key, value)
            }
        }
        return editor.commit()
    }

    override fun clearLegacyState(): Boolean = legacyPreferences.edit().clear().commit()

    override fun markLegacyHandled(): Boolean =
        migrationState.edit().putBoolean(migrationKey, true).commit()

    private fun accountPreferences(accountId: String): SharedPreferences =
        appContext.getSharedPreferences(
            accountScopedPreferencesName(basePreferencesName, accountId),
            Context.MODE_PRIVATE,
        )

    private companion object {
        const val MIGRATION_PREFERENCES_NAME = "hulk_account_profile_state_migration_v1"
    }
}

internal fun profileStateKey(profileId: String, stateKey: String): String =
    "profile:$profileId:$stateKey"

private fun AccountProfileStateScope.normalized(): AccountProfileStateScope? {
    val accountId = accountId.normalizedStateIdentity() ?: return null
    val profileId = profileId.normalizedStateIdentity() ?: return null
    return AccountProfileStateScope(accountId, profileId)
}

private fun String?.normalizedStateIdentity(): String? =
    this?.trim()?.takeIf(String::isNotBlank)

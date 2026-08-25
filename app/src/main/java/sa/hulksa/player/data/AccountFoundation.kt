package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.AuthenticatedSession
import java.security.MessageDigest
import java.util.UUID

/**
 * Stable non-secret identity for an IPTV account. The password is deliberately
 * excluded so changing a password does not orphan profile-owned local data.
 */
internal fun stableAccountId(portalBaseUrl: String, username: String): String {
    val canonical = portalBaseUrl.trim().trimEnd('/') + "\u0000" + username.trim()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

/**
 * Persistent local alias for a subscriber username. Access codes and reseller
 * hosts may rotate, so they must not redefine the account that owns profiles,
 * favorites, history, downloads or other account-scoped preferences.
 */
internal fun accountIdentityAliasKey(username: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(username.trim().toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "account_alias_$digest"
}

internal fun resolveAccountIdForAuthentication(
    portalBaseUrl: String,
    username: String,
    aliasedAccountId: String?,
    currentAccountId: String?,
    currentUsername: String?,
    lastAccountId: String?,
    lastUsername: String?,
): String {
    val normalizedUsername = username.trim()
    fun validAccountId(value: String?): String? = value
        ?.trim()
        ?.takeIf(String::isNotBlank)

    validAccountId(aliasedAccountId)?.let { return it }
    if (currentUsername?.trim() == normalizedUsername) {
        validAccountId(currentAccountId)?.let { return it }
    }
    if (lastUsername?.trim() == normalizedUsername) {
        validAccountId(lastAccountId)?.let { return it }
    }
    return stableAccountId(portalBaseUrl, normalizedUsername)
}

internal fun accountScopedPreferencesName(baseName: String, accountId: String): String =
    "$baseName.account.${accountId.trim()}"

internal fun isAccountSessionExpired(
    expiresAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
): Boolean = expiresAtEpochSeconds != null && expiresAtEpochSeconds > 0L &&
    nowEpochSeconds >= expiresAtEpochSeconds

internal const val SUBSCRIPTION_RESUME_REVALIDATION_INTERVAL_MS = 10 * 60_000L

/**
 * Foreground entitlement checks intentionally share the app's existing
 * ten-minute resume network cadence. A recent failed attempt is also throttled,
 * while clock rollback or missing authentication metadata forces revalidation.
 */
internal fun shouldRevalidateAccountOnResume(
    authenticatedAtEpochMs: Long,
    lastAttemptElapsedMs: Long,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    minimumAgeMs: Long = SUBSCRIPTION_RESUME_REVALIDATION_INTERVAL_MS,
): Boolean {
    if (minimumAgeMs <= 0L) return true

    if (lastAttemptElapsedMs > 0L) {
        if (nowElapsedMs < lastAttemptElapsedMs) return true
        if (nowElapsedMs - lastAttemptElapsedMs < minimumAgeMs) return false
    }

    if (authenticatedAtEpochMs <= 0L) return true
    if (nowEpochMs < authenticatedAtEpochMs) return true
    return nowEpochMs - authenticatedAtEpochMs >= minimumAgeMs
}

data class AccountSessionMetadata(
    val accountId: String,
    val username: String,
    val portalBaseUrl: String,
    val authenticatedAtEpochMs: Long,
    val expiresAtEpochSeconds: Long?,
    val status: String,
    val installationId: String,
    val sessionId: String,
) {
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        isAccountSessionExpired(expiresAtEpochSeconds, nowEpochSeconds)
}

/**
 * Supplies account-scoped SharedPreferences while preserving the v1.1 data as
 * rollback-safe legacy storage. The first successfully authenticated account
 * claims the old unscoped data exactly once; later accounts start isolated.
 */
class AccountScopeStore(context: Context) {
    private val appContext = context.applicationContext
    private val state = appContext.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)

    fun activeAccountId(): String? = state.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    internal fun legacyOwnerAccountId(): String? = state.getString(KEY_LEGACY_OWNER_ACCOUNT_ID, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    internal fun registerActiveAccountListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        state.registerOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    fun bind(accountId: String): Boolean {
        val normalized = accountId.trim().takeIf(String::isNotBlank) ?: return false
        val legacyOwner = state.getString(KEY_LEGACY_OWNER_ACCOUNT_ID, null)
        return state.edit().apply {
            putString(KEY_ACTIVE_ACCOUNT_ID, normalized)
            if (legacyOwner.isNullOrBlank()) {
                putString(KEY_LEGACY_OWNER_ACCOUNT_ID, normalized)
            }
        }.commit()
    }

    @Synchronized
    fun clearActive() {
        state.edit().remove(KEY_ACTIVE_ACCOUNT_ID).commit()
    }

    fun preferences(baseName: String): SharedPreferences {
        val accountId = activeAccountId()
            ?: return appContext.getSharedPreferences(baseName, Context.MODE_PRIVATE)
        val scopedName = accountScopedPreferencesName(baseName, accountId)
        val scoped = appContext.getSharedPreferences(scopedName, Context.MODE_PRIVATE)
        migrateLegacyPreferencesIfNeeded(
            legacyName = baseName,
            scoped = scoped,
            accountId = accountId,
        )
        return scoped
    }

    @Synchronized
    private fun migrateLegacyPreferencesIfNeeded(
        legacyName: String,
        scoped: SharedPreferences,
        accountId: String,
    ) {
        if (scoped.getBoolean(KEY_SCOPE_MIGRATED, false)) return

        val editor = scoped.edit()
        val ownsLegacy = state.getString(KEY_LEGACY_OWNER_ACCOUNT_ID, null) == accountId
        if (ownsLegacy) {
            val legacy = appContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
            copyPreferences(legacy, editor)
        }
        editor.putBoolean(KEY_SCOPE_MIGRATED, true).commit()
    }

    private fun copyPreferences(
        source: SharedPreferences,
        target: SharedPreferences.Editor,
    ) {
        source.all.forEach { (key, value) ->
            when (value) {
                is String -> target.putString(key, value)
                is Boolean -> target.putBoolean(key, value)
                is Int -> target.putInt(key, value)
                is Long -> target.putLong(key, value)
                is Float -> target.putFloat(key, value)
                is Set<*> -> target.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private companion object {
        const val STATE_PREFERENCES = "hulk_account_scope_v1"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val KEY_LEGACY_OWNER_ACCOUNT_ID = "legacy_owner_account_id"
        const val KEY_SCOPE_MIGRATED = "__account_scope_migrated_v1"
    }
}

/**
 * Persists non-secret session metadata and owns the active account binding.
 * Xtream does not issue an opaque access token, so the protocol credentials
 * remain exclusively in CredentialVault; this store never persists a password.
 */
class AccountSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val accountScope = AccountScopeStore(appContext)

    @Synchronized
    fun recordAuthenticated(session: AuthenticatedSession): AccountSessionMetadata {
        val username = session.credentials.username.trim()
        val aliasKey = accountIdentityAliasKey(username)
        val accountId = resolveAccountIdForAuthentication(
            portalBaseUrl = session.portal.baseUrl,
            username = username,
            aliasedAccountId = preferences.getString(aliasKey, null),
            currentAccountId = preferences.getString(KEY_ACCOUNT_ID, null),
            currentUsername = preferences.getString(KEY_USERNAME, null),
            lastAccountId = preferences.getString(KEY_LAST_ACCOUNT_ID, null),
            lastUsername = preferences.getString(KEY_LAST_USERNAME, null),
        )
        check(accountScope.bind(accountId)) { "Unable to bind authenticated account scope" }

        val installationId = preferences.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString()
        val metadata = AccountSessionMetadata(
            accountId = accountId,
            username = username,
            portalBaseUrl = session.portal.baseUrl.trim().trimEnd('/'),
            authenticatedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochSeconds = session.account.expiresAtEpochSeconds,
            status = session.account.status,
            installationId = installationId,
            sessionId = UUID.randomUUID().toString(),
        )

        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putString(aliasKey, metadata.accountId)
            .putString(KEY_ACCOUNT_ID, metadata.accountId)
            .putString(KEY_USERNAME, metadata.username)
            .putString(KEY_PORTAL_BASE_URL, metadata.portalBaseUrl)
            .putLong(KEY_AUTHENTICATED_AT, metadata.authenticatedAtEpochMs)
            .putLong(KEY_EXPIRES_AT, metadata.expiresAtEpochSeconds ?: NO_EXPIRY)
            .putString(KEY_STATUS, metadata.status)
            .putString(KEY_INSTALLATION_ID, metadata.installationId)
            .putString(KEY_SESSION_ID, metadata.sessionId)
            .putString(KEY_LAST_ACCOUNT_ID, metadata.accountId)
            .putString(KEY_LAST_USERNAME, metadata.username)
            .putString(KEY_LAST_ACCESS_CODE, session.credentials.accessCode)
            .commit()
        return metadata
    }

    fun metadata(): AccountSessionMetadata? {
        val accountId = preferences.getString(KEY_ACCOUNT_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (accountScope.activeAccountId() != accountId) return null
        val username = preferences.getString(KEY_USERNAME, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val portal = preferences.getString(KEY_PORTAL_BASE_URL, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val installationId = preferences.getString(KEY_INSTALLATION_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val sessionId = preferences.getString(KEY_SESSION_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val authenticatedAt = preferences.getLong(KEY_AUTHENTICATED_AT, 0L)
        if (authenticatedAt <= 0L) return null
        val expires = preferences.getLong(KEY_EXPIRES_AT, NO_EXPIRY)
            .takeUnless { it == NO_EXPIRY }
        return AccountSessionMetadata(
            accountId = accountId,
            username = username,
            portalBaseUrl = portal,
            authenticatedAtEpochMs = authenticatedAt,
            expiresAtEpochSeconds = expires,
            status = preferences.getString(KEY_STATUS, "Active").orEmpty().ifBlank { "Active" },
            installationId = installationId,
            sessionId = sessionId,
        )
    }

    fun activeAccountId(): String? = accountScope.activeAccountId()

    fun lastAccessCode(): String? = preferences.getString(KEY_LAST_ACCESS_CODE, null)
        ?.takeIf { it.isNotBlank() }

    @Synchronized
    fun clearActiveSession() {
        val currentAccountId = preferences.getString(KEY_ACCOUNT_ID, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val currentUsername = preferences.getString(KEY_USERNAME, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val editor = preferences.edit()
        if (currentAccountId != null && currentUsername != null) {
            editor
                .putString(KEY_LAST_ACCOUNT_ID, currentAccountId)
                .putString(KEY_LAST_USERNAME, currentUsername)
                .putString(accountIdentityAliasKey(currentUsername), currentAccountId)
        }
        editor
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_PORTAL_BASE_URL)
            .remove(KEY_AUTHENTICATED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_STATUS)
            .remove(KEY_SESSION_ID)
            .commit()
        accountScope.clearActive()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val NO_EXPIRY = -1L
        private const val PREFERENCES_NAME = "hulk_account_session_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PORTAL_BASE_URL = "portal_base_url"
        private const val KEY_AUTHENTICATED_AT = "authenticated_at_epoch_ms"
        private const val KEY_EXPIRES_AT = "expires_at_epoch_seconds"
        private const val KEY_STATUS = "status"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LAST_ACCOUNT_ID = "last_account_id"
        private const val KEY_LAST_USERNAME = "last_username"
        private const val KEY_LAST_ACCESS_CODE = "last_access_code"
    }
}

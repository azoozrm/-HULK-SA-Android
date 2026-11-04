package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.security.containsCredentialBearingIptvMaterial
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
 * Persistent local alias for a subscriber username. Kept for rollback compatibility with
 * earlier releases and as durable same-customer continuation evidence when a host or code changes.
 * The existing accountId always remains the local data owner; no access code is a data namespace.
 */
internal fun accountIdentityAliasKey(username: String): String =
    "account_alias_${accountIdentityUsernameKey(username)}"

/** Stable non-secret username index key used by the trusted-host ownership state. */
internal fun accountIdentityUsernameKey(username: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(username.trim().toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return digest
}

/** Canonical trusted-host identity; matches the host component used by [stableAccountId]. */
internal fun accountTrustedHostKey(portalBaseUrl: String): String =
    portalBaseUrl.trim().trimEnd('/')

internal sealed interface AccountIdentityResolution {
    data class Known(val accountId: String) : AccountIdentityResolution
    data class New(val accountId: String) : AccountIdentityResolution
}

internal data class AccountIdentitySnapshot(
    val trustedAccountIds: Set<String>,
    val trustedHostsByAccountId: Map<String, Set<String>>,
    val legacyCandidateAccountIds: Set<String>,
    val activeAccountId: String?,
    val activeUsername: String?,
    val activePortalBaseUrl: String?,
    val aliasAccountId: String? = null,
    val lastAccountId: String? = null,
    val lastUsername: String? = null,
)

/**
 * Owner decision 2026-11-02 supersedes the old O2/O4 customer-confirmation rule.
 * Exact established host associations preserve distinct historical scopes. On a new host,
 * continue this username's current owner, per-username alias, matching last owner, or sole
 * historical owner, in that order. No decision, host notice, scope merge or data copy is needed.
 * Evidence-free multiple/conflicting scopes remain intact; a deterministic separate owner is
 * used instead of choosing an arbitrary candidate or exposing another customer's local data.
 */
internal fun resolveAccountIdentity(
    username: String,
    portalBaseUrl: String,
    snapshot: AccountIdentitySnapshot,
): AccountIdentityResolution {
    val normalizedUsername = username.trim()
    val hostKey = accountTrustedHostKey(portalBaseUrl)
    fun String?.ownerId(): String? = this?.trim()?.takeIf(String::isNotBlank)
    val current = snapshot.activeAccountId.ownerId()
        .takeIf { snapshot.activeUsername?.trim() == normalizedUsername }
    val alias = snapshot.aliasAccountId.ownerId()
    val last = snapshot.lastAccountId.ownerId()
        .takeIf { snapshot.lastUsername?.trim() == normalizedUsername }
    val candidates = (snapshot.trustedAccountIds + snapshot.legacyCandidateAccountIds +
        listOfNotNull(current, alias, last)).mapNotNull { it.ownerId() }.toSet()
    val hostOwners = candidates.filter { hostKey in snapshot.trustedHostsByAccountId[it].orEmpty() }
    val continuation = listOfNotNull(current, alias, last).firstOrNull()
    if (hostOwners.size == 1) return AccountIdentityResolution.Known(hostOwners.single())
    if (hostOwners.size > 1) {
        if (continuation in hostOwners) return AccountIdentityResolution.Known(requireNotNull(continuation))
        return AccountIdentityResolution.New(isolatedUnresolvedAccountId(normalizedUsername, hostKey, candidates))
    }
    if (continuation != null) return AccountIdentityResolution.Known(continuation)
    val legacyHostOwner = stableAccountId(hostKey, normalizedUsername)
    if (legacyHostOwner in candidates) return AccountIdentityResolution.Known(legacyHostOwner)
    if (candidates.size == 1) return AccountIdentityResolution.Known(candidates.single())
    return AccountIdentityResolution.New(
        if (candidates.isEmpty()) legacyHostOwner
        else isolatedUnresolvedAccountId(normalizedUsername, hostKey, candidates),
    )
}

private fun isolatedUnresolvedAccountId(username: String, host: String, candidates: Set<String>): String =
    "local-restore-" + stableAccountId(host, username + "\u0000" + candidates.sorted().joinToString("\u0000"))

internal fun accountScopedPreferencesName(baseName: String, accountId: String): String =
    "$baseName.account.${accountId.trim()}"

internal fun isAccountSessionExpired(
    expiresAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
): Boolean = expiresAtEpochSeconds != null && expiresAtEpochSeconds > 0L &&
    nowEpochSeconds >= expiresAtEpochSeconds

/**
 * Single expiry gate for fresh authentication and authenticated refresh/restore.
 * Missing, zero and non-positive expiry values keep the provider's unlimited semantics.
 */
internal fun requireUnexpiredAccountSession(
    expiresAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
) {
    if (isAccountSessionExpired(expiresAtEpochSeconds, nowEpochSeconds)) {
        throw XtreamException.SubscriptionExpired
    }
}

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
class AccountScopeStore internal constructor(
    private val state: SharedPreferences,
    private val scopedPreferences: (String) -> SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE),
        { name -> context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) },
    )

    fun activeAccountId(): String? = state.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    internal fun legacyOwnerAccountId(): String? = state.getString(KEY_LEGACY_OWNER_ACCOUNT_ID, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    internal data class RollbackSnapshot(
        val activeAccountId: String?,
        val legacyOwnerAccountId: String?,
    )

    internal fun rollbackSnapshot(): RollbackSnapshot = RollbackSnapshot(
        activeAccountId = activeAccountId(),
        legacyOwnerAccountId = legacyOwnerAccountId(),
    )

    internal fun restore(snapshot: RollbackSnapshot): Boolean =
        state.edit().apply {
            if (snapshot.activeAccountId == null) {
                remove(KEY_ACTIVE_ACCOUNT_ID)
            } else {
                putString(KEY_ACTIVE_ACCOUNT_ID, snapshot.activeAccountId)
            }
            if (snapshot.legacyOwnerAccountId == null) {
                remove(KEY_LEGACY_OWNER_ACCOUNT_ID)
            } else {
                putString(KEY_LEGACY_OWNER_ACCOUNT_ID, snapshot.legacyOwnerAccountId)
            }
        }.commit()

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
            ?: return scopedPreferences(baseName)
        return preferences(baseName, accountId)
    }

    internal fun preferences(baseName: String, accountId: String): SharedPreferences {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank)
            ?: return scopedPreferences(baseName)
        val scopedName = accountScopedPreferencesName(baseName, normalizedAccountId)
        val scoped = scopedPreferences(scopedName)
        migrateLegacyPreferencesIfNeeded(
            legacyName = baseName,
            scoped = scoped,
            accountId = normalizedAccountId,
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
        val establishedCredentialState = legacyName == ProfilePinCredentialStore.PREFERENCES_NAME &&
            hasEstablishedPinCredentialState(scoped)
        if (
            ownsLegacy &&
            !establishedCredentialState &&
            !hasEstablishedScopedProfileState(legacyName, scoped)
        ) {
            val legacy = scopedPreferences(legacyName)
            copyPreferences(legacy, scoped, editor, legacyName)
        }
        editor.putBoolean(KEY_SCOPE_MIGRATED, true).commit()
    }

    private fun hasEstablishedScopedProfileState(
        baseName: String,
        scoped: SharedPreferences,
    ): Boolean = baseName == PROFILE_PREFERENCES_NAME &&
        profilePreferencesHaveEstablishedState(scoped)

    private fun copyPreferences(
        source: SharedPreferences,
        target: SharedPreferences,
        targetEditor: SharedPreferences.Editor,
        legacyName: String,
    ) {
        var unsafePayloadSkipped = false
        source.all.forEach { (key, value) ->
            if (target.contains(key)) return@forEach
            if (value is String && containsCredentialBearingIptvMaterial(value)) {
                unsafePayloadSkipped = true
                return@forEach
            }
            when (value) {
                is String -> targetEditor.putString(key, value)
                is Boolean -> targetEditor.putBoolean(key, value)
                is Int -> targetEditor.putInt(key, value)
                is Long -> targetEditor.putLong(key, value)
                is Float -> targetEditor.putFloat(key, value)
                is Set<*> -> targetEditor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (unsafePayloadSkipped) {
            targetEditor.putBoolean(unsafeLegacyMigrationMarkerKey(legacyName), true)
        }
    }

    private companion object {
        const val STATE_PREFERENCES = "hulk_account_scope_v1"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val KEY_LEGACY_OWNER_ACCOUNT_ID = "legacy_owner_account_id"
        const val KEY_SCOPE_MIGRATED = "__account_scope_migrated_v1"
        const val PROFILE_PREFERENCES_NAME = "hulk_profiles_v1"
        const val KEY_PROFILE_DOCUMENT = "profiles"
        const val KEY_PROFILE_ACTIVE_ID = "active_profile_id"
        const val KEY_PROFILE_SCHEMA_VERSION = "schema_version"
    }
}

/**
 * Persists non-secret session metadata and owns the active account binding.
 * Xtream does not issue an opaque access token, so the protocol credentials
 * remain exclusively in CredentialVault; this store never persists a password.
 */
class AccountSessionStore internal constructor(
    private val preferences: SharedPreferences,
    private val accountScope: AccountScopeStore,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        AccountScopeStore(context),
    )

    @Synchronized
    fun recordAuthenticated(session: AuthenticatedSession): AccountSessionMetadata {
        val username = session.credentials.username.trim()
        return when (val resolution = resolveAuthenticationIdentity(username, session.portal.baseUrl)) {
            is AccountIdentityResolution.Known -> recordAuthenticated(session, resolution.accountId)
            is AccountIdentityResolution.New -> recordAuthenticated(session, resolution.accountId)
        }
    }

    /**
     * Commits resolved local ownership. The account scope binding, session
     * metadata and trusted-host association are persisted in one checked commit so a failed
     * commit cannot leave a partial ownership state behind.
     */
    @Synchronized
    fun recordAuthenticated(
        session: AuthenticatedSession,
        accountId: String,
        trustHost: Boolean = true,
    ): AccountSessionMetadata {
        requireUnexpiredAccountSession(
            expiresAtEpochSeconds = session.account.expiresAtEpochSeconds,
            nowEpochSeconds = System.currentTimeMillis() / 1_000L,
        )
        val username = session.credentials.username.trim()
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Authenticated accountId must not be blank")
        val previous = rollbackSnapshot()
        try {
            check(accountScope.bind(normalizedAccountId)) {
                "Unable to bind authenticated account scope"
            }

            val installationId = preferences.getString(KEY_INSTALLATION_ID, null)
                ?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString()
            val metadata = AccountSessionMetadata(
                accountId = normalizedAccountId,
                username = username,
                portalBaseUrl = session.portal.baseUrl.trim().trimEnd('/'),
                authenticatedAtEpochMs = System.currentTimeMillis(),
                expiresAtEpochSeconds = session.account.expiresAtEpochSeconds,
                status = session.account.status,
                installationId = installationId,
                sessionId = UUID.randomUUID().toString(),
            )

            val editor = preferences.edit()
                .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                .putString(accountIdentityAliasKey(username), metadata.accountId)
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
            if (trustHost) {
                applyTrustedHost(
                    editor = editor,
                    username = username,
                    portalBaseUrl = session.portal.baseUrl,
                    accountId = normalizedAccountId,
                )
            }
            check(editor.commit()) { "Unable to persist authenticated account session metadata" }
            return metadata
        } catch (error: Throwable) {
            runCatching { restore(previous) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    /**
     * Resolves authenticated ownership using exact historical associations and same-username
     * continuation evidence. Legacy aliases/current/last owners restore their original scopes
     * automatically; neither an access code nor a changed host creates a replacement namespace.
     */
    @Synchronized
    internal fun resolveAuthenticationIdentity(
        username: String,
        portalBaseUrl: String,
    ): AccountIdentityResolution {
        val normalizedUsername = username.trim()
        val usernameKey = accountIdentityUsernameKey(normalizedUsername)
        val trustedAccountIds = preferences.getStringSet(trustedAccountsKey(usernameKey), null)
            .orEmpty()
            .filter(String::isNotBlank)
            .toSet()
        val trustedHostsByAccountId = trustedAccountIds.associateWith { accountId ->
            preferences.getStringSet(trustedHostsKey(accountId), null)
                .orEmpty()
                .filter(String::isNotBlank)
                .toSet()
        }
        val legacyCandidateAccountIds = linkedSetOf<String>()
        preferences.getString(accountIdentityAliasKey(normalizedUsername), null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(legacyCandidateAccountIds::add)
        if (preferences.getString(KEY_USERNAME, null)?.trim() == normalizedUsername) {
            preferences.getString(KEY_ACCOUNT_ID, null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(legacyCandidateAccountIds::add)
        }
        if (preferences.getString(KEY_LAST_USERNAME, null)?.trim() == normalizedUsername) {
            preferences.getString(KEY_LAST_ACCOUNT_ID, null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(legacyCandidateAccountIds::add)
        }
        val activeMetadata = metadata()
        return resolveAccountIdentity(
            username = normalizedUsername,
            portalBaseUrl = portalBaseUrl,
            snapshot = AccountIdentitySnapshot(
                trustedAccountIds = trustedAccountIds,
                trustedHostsByAccountId = trustedHostsByAccountId,
                legacyCandidateAccountIds = legacyCandidateAccountIds,
                activeAccountId = activeMetadata?.accountId,
                activeUsername = activeMetadata?.username,
                activePortalBaseUrl = activeMetadata?.portalBaseUrl,
                aliasAccountId = preferences.getString(accountIdentityAliasKey(normalizedUsername), null),
                lastAccountId = preferences.getString(KEY_LAST_ACCOUNT_ID, null),
                lastUsername = preferences.getString(KEY_LAST_USERNAME, null),
            ),
        )
    }

    /**
     * Reauthentication and restore continue an already explicit local owner. An already-trusted
     * host must belong to that owner; an untrusted host is accepted as that owner's rotation.
     * A host trusted to a different same-username account fails closed and never rebinds.
     */
    @Synchronized
    internal fun continuationAccountId(
        username: String,
        portalBaseUrl: String,
        currentAccountId: String,
    ): String? {
        val normalizedAccountId = currentAccountId.trim().takeIf(String::isNotBlank) ?: return null
        val current = metadata() ?: return null
        if (current.accountId != normalizedAccountId || current.username.trim() != username.trim()) return null
        val hostKey = accountTrustedHostKey(portalBaseUrl)
        if (hostKey.isEmpty()) return null
        val usernameKey = accountIdentityUsernameKey(username.trim())
        val trustedAccountIds = preferences.getStringSet(trustedAccountsKey(usernameKey), null)
            .orEmpty()
            .filter(String::isNotBlank)
        val hostOwners = trustedAccountIds.filter { accountId ->
            hostKey in preferences.getStringSet(trustedHostsKey(accountId), null).orEmpty()
        }
        return when {
            hostOwners.isEmpty() -> normalizedAccountId
            hostOwners.size == 1 && hostOwners.single() == normalizedAccountId -> normalizedAccountId
            else -> null
        }
    }

    private fun applyTrustedHost(
        editor: SharedPreferences.Editor,
        username: String,
        portalBaseUrl: String,
        accountId: String,
    ) {
        val normalizedUsername = username.trim()
        val hostKey = accountTrustedHostKey(portalBaseUrl)
        if (normalizedUsername.isEmpty() || hostKey.isEmpty() || accountId.isBlank()) return
        val usernameKey = accountIdentityUsernameKey(normalizedUsername)
        val accounts = preferences.getStringSet(trustedAccountsKey(usernameKey), null)
            .orEmpty()
            .filter(String::isNotBlank)
            .toMutableSet()
        accounts += accountId
        editor.putStringSet(trustedAccountsKey(usernameKey), accounts)
        val hosts = preferences.getStringSet(trustedHostsKey(accountId), null)
            .orEmpty()
            .filter(String::isNotBlank)
            .toMutableSet()
        hosts += hostKey
        editor.putStringSet(trustedHostsKey(accountId), hosts)
    }

    private fun trustedAccountsKey(usernameKey: String): String = "trusted_accounts_$usernameKey"

    private fun trustedHostsKey(accountId: String): String = "trusted_hosts_$accountId"

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

    internal data class RollbackSnapshot(
        val sessionPreferences: Map<String, *>,
        val accountScope: AccountScopeStore.RollbackSnapshot,
    )

    @Synchronized
    internal fun rollbackSnapshot(): RollbackSnapshot =
        RollbackSnapshot(preferences.all.toMap(), accountScope.rollbackSnapshot())

    /** Attempts both durable restorations even if one commit reports failure. */
    @Synchronized
    internal fun restore(snapshot: RollbackSnapshot) {
        val restoredSession = restoreSessionPreferencesSnapshot(snapshot.sessionPreferences)
        val restoredScope = accountScope.restore(snapshot.accountScope)
        check(restoredSession && restoredScope) { "Unable to restore previous account ownership" }
    }

    private fun restoreSessionPreferencesSnapshot(snapshot: Map<String, *>): Boolean {
        val editor = preferences.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        return editor.commit()
    }

    /**
     * Confirms the existing non-secret installation key with a checked commit.
     * Different store owners share process preferences, including failed-write memory.
     * Account/session state is unchanged.
     */
    @Synchronized
    fun installationIdForPairing(): String? {
        val candidate = preferences.getString(KEY_INSTALLATION_ID, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: UUID.randomUUID().toString()
        val persisted = preferences.edit()
            .putString(KEY_INSTALLATION_ID, candidate)
            .commit()
        return candidate.takeIf { persisted }
    }

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

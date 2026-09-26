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
 * Persistent local alias for a subscriber username. Kept for rollback compatibility with
 * earlier releases: the trusted-host ownership model below supersedes it for resolution, but the
 * alias is still maintained so an older build reading the same data follows the same owner.
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

/**
 * User-readable, privacy-safe host label: host and optional port only. Scheme, path, query,
 * fragment and any user-info are deliberately removed.
 */
internal fun trustedHostDisplayLabel(portalBaseUrl: String): String? {
    val trimmed = portalBaseUrl.trim()
    if (trimmed.isEmpty()) return null
    val afterScheme = trimmed.substringAfter("://", trimmed)
    val authority = afterScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val hostPort = authority.substringAfterLast('@')
    return hostPort.takeIf { it.isNotBlank() }
}

/** Host label including the scheme; used only to disambiguate otherwise identical candidates. */
internal fun trustedHostFullDisplayLabel(portalBaseUrl: String): String? {
    val hostPort = trustedHostDisplayLabel(portalBaseUrl) ?: return null
    val trimmed = portalBaseUrl.trim()
    val schemeEnd = trimmed.indexOf("://")
    val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() else null
    return if (scheme != null) "$scheme://$hostPort" else hostPort
}

internal data class TrustedAccountIdentity(
    val accountId: String,
    val trustedHosts: Set<String>,
)

internal sealed interface AccountIdentityResolution {
    data class Known(val accountId: String) : AccountIdentityResolution
    data class New(val accountId: String) : AccountIdentityResolution
    data class Ambiguous(val candidates: List<TrustedAccountIdentity>) : AccountIdentityResolution
}

internal data class AccountIdentitySnapshot(
    val trustedAccountIds: Set<String>,
    val trustedHostsByAccountId: Map<String, Set<String>>,
    val legacyCandidateAccountIds: Set<String>,
    val activeAccountId: String?,
    val activeUsername: String?,
    val activePortalBaseUrl: String?,
)

/**
 * O2 + O4 subscriber identity precedence:
 * 1. an exact trusted username+host association resolves directly to its account;
 * 2. an active session whose persisted username+host match is trustworthy saved evidence;
 * 3. a username with no local account at all starts a new isolated scope;
 * 4. any unknown host with existing same-username candidates stays ambiguous until the user
 *    explicitly chooses one existing account or a different subscription.
 */
internal fun resolveAccountIdentity(
    username: String,
    portalBaseUrl: String,
    snapshot: AccountIdentitySnapshot,
): AccountIdentityResolution {
    val normalizedUsername = username.trim()
    val hostKey = accountTrustedHostKey(portalBaseUrl)
    val candidates = (snapshot.trustedAccountIds + snapshot.legacyCandidateAccountIds)
        .map { accountId ->
            TrustedAccountIdentity(
                accountId = accountId,
                trustedHosts = snapshot.trustedHostsByAccountId[accountId].orEmpty(),
            )
        }
        .sortedBy(TrustedAccountIdentity::accountId)
    val hostOwners = candidates.filter { hostKey in it.trustedHosts }
    if (hostOwners.size == 1) return AccountIdentityResolution.Known(hostOwners.single().accountId)
    if (hostOwners.size > 1) return AccountIdentityResolution.Ambiguous(candidates)
    val activeAccountId = snapshot.activeAccountId?.trim()?.takeIf(String::isNotBlank)
    if (
        activeAccountId != null &&
        snapshot.activeUsername?.trim() == normalizedUsername &&
        snapshot.activePortalBaseUrl != null &&
        accountTrustedHostKey(snapshot.activePortalBaseUrl) == hostKey
    ) {
        return AccountIdentityResolution.Known(activeAccountId)
    }
    if (candidates.isEmpty()) {
        return AccountIdentityResolution.New(stableAccountId(portalBaseUrl, normalizedUsername))
    }
    return AccountIdentityResolution.Ambiguous(candidates)
}

internal data class AccountIdentityCandidateLabel(
    val accountId: String,
    val label: String,
)

internal const val ACCOUNT_DECISION_LEGACY_ACCOUNT_LABEL = "حساب محفوظ سابقاً"

/**
 * Deterministic non-secret labels for ambiguity choices. Host-only labels are preferred; scheme
 * is included only when host:port alone would collide. No accountId or credential is exposed.
 */
internal fun accountIdentityCandidateLabels(
    candidates: List<TrustedAccountIdentity>,
): List<AccountIdentityCandidateLabel> {
    val hostLabels = candidates.associate { candidate ->
        candidate.accountId to candidate.trustedHosts
            .mapNotNull(::trustedHostDisplayLabel)
            .distinct()
            .sorted()
    }
    val preferred = candidates.associate { candidate ->
        candidate.accountId to (
            hostLabels[candidate.accountId]?.firstOrNull()
                ?: ACCOUNT_DECISION_LEGACY_ACCOUNT_LABEL
            )
    }
    val preferredCounts = preferred.values.groupingBy { it }.eachCount()
    return candidates.map { candidate ->
        val labels = hostLabels[candidate.accountId].orEmpty()
        val label = when {
            labels.isEmpty() -> ACCOUNT_DECISION_LEGACY_ACCOUNT_LABEL
            preferredCounts[labels.first()] == 1 -> labels.first()
            else -> candidate.trustedHosts
                .mapNotNull(::trustedHostFullDisplayLabel)
                .distinct()
                .sorted()
                .joinToString("، ")
        }
        AccountIdentityCandidateLabel(accountId = candidate.accountId, label = label)
    }.sortedWith(compareBy({ it.label }, { it.accountId }))
}

/**
 * Raised when ownership cannot be resolved without an explicit user decision. The pending
 * authenticated session must not be committed or bound before that decision completes.
 */
internal class AccountIdentityDecisionRequiredException(
    val candidates: List<TrustedAccountIdentity>,
) : IllegalStateException("Account identity decision required")

/** A stale or late decision can never rebind ownership after trusted state changed. */
internal class StaleAccountIdentityDecisionException :
    IllegalStateException("Account identity decision no longer applies")

/**
 * Validates an explicit decision against freshly resolved ownership. Returns the accountId to
 * commit, or throws when the decision no longer applies: a currently known host cannot be rebound
 * to another account, and a selected candidate must still be a current same-username account.
 */
internal fun resolveDecisionAccountId(
    resolution: AccountIdentityResolution,
    selectedAccountId: String?,
    fallbackAccountId: String,
): String = when (resolution) {
    is AccountIdentityResolution.Known -> {
        if (resolution.accountId != selectedAccountId) throw StaleAccountIdentityDecisionException()
        resolution.accountId
    }
    is AccountIdentityResolution.Ambiguous -> {
        if (selectedAccountId != null) {
            resolution.candidates
                .firstOrNull { it.accountId == selectedAccountId }
                ?.accountId
                ?: throw StaleAccountIdentityDecisionException()
        } else {
            fallbackAccountId
        }
    }
    is AccountIdentityResolution.New -> {
        if (selectedAccountId != null) throw StaleAccountIdentityDecisionException()
        fallbackAccountId
    }
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
        if (ownsLegacy) {
            val legacy = scopedPreferences(legacyName)
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
            is AccountIdentityResolution.Ambiguous ->
                throw AccountIdentityDecisionRequiredException(resolution.candidates)
        }
    }

    /**
     * Commits an explicitly resolved ownership decision. The account scope binding, session
     * metadata and trusted-host association are persisted in one checked commit so a failed
     * commit cannot leave a partial ownership state behind.
     */
    @Synchronized
    fun recordAuthenticated(
        session: AuthenticatedSession,
        accountId: String,
        trustHost: Boolean = true,
    ): AccountSessionMetadata {
        val username = session.credentials.username.trim()
        val normalizedAccountId = accountId.trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Authenticated accountId must not be blank")
        check(accountScope.bind(normalizedAccountId)) { "Unable to bind authenticated account scope" }

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
    }

    /**
     * Resolves the O2 + O4 ownership decision for an authenticated username+host. Trusted state is
     * authoritative; the legacy alias/current/last accountIds remain candidates only so an
     * upgrade install can still offer a one-time same/different decision instead of orphaning data.
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

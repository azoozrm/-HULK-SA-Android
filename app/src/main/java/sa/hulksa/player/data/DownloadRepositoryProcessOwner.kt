package sa.hulksa.player.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.DownloadSettings
import sa.hulksa.player.model.AuthenticatedSession
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.PlaybackRequest
import java.security.MessageDigest

internal enum class LegacyDownloadMigrationPolicy {
    MIGRATE,
    QUARANTINE,
}

internal fun legacyDownloadMigrationPolicy(
    capturedLegacyOwnerAccountId: String?,
    targetAccountId: String,
): LegacyDownloadMigrationPolicy = if (
    capturedLegacyOwnerAccountId?.trim()?.takeIf(String::isNotEmpty) ==
    targetAccountId.trim().takeIf(String::isNotEmpty)
) {
    LegacyDownloadMigrationPolicy.MIGRATE
} else {
    LegacyDownloadMigrationPolicy.QUARANTINE
}

internal fun downloadAccountStorageKey(accountId: String): String {
    val normalized = accountId.trim().takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("accountId must not be blank")
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        .take(24)
}

internal fun downloadAccountDirectoryName(accountId: String): String =
    "hulk-account-${downloadAccountStorageKey(accountId)}"

internal fun authenticatedDownloadAccountId(
    session: AuthenticatedSession?,
    metadata: AccountSessionMetadata?,
): String? {
    if (session == null || metadata == null) return null
    if (metadata.username != session.credentials.username.trim()) return null
    if (
        metadata.portalBaseUrl.trim().trimEnd('/') !=
        session.portal.baseUrl.trim().trimEnd('/')
    ) {
        return null
    }
    return metadata.accountId.trim().takeIf(String::isNotEmpty)
}

internal fun accountDownloadAccessAllowed(
    recordAccountId: String,
    activeAccountId: String?,
    profileOwnsRecord: Boolean,
): Boolean =
    profileOwnsRecord &&
        recordAccountId.isNotBlank() &&
        recordAccountId == activeAccountId

internal fun downloadOwnerContextMatches(
    expectedAccountId: String,
    expectedProfileId: String,
    activeAccountId: String?,
    activeProfileId: String,
): Boolean =
    expectedAccountId.isNotBlank() &&
        expectedProfileId.isNotBlank() &&
        expectedAccountId == activeAccountId &&
        expectedProfileId == activeProfileId

internal fun downloadRemovalContextMatches(
    expectedAccountId: String,
    expectedProfileId: String,
    activeAccountId: String?,
    activeProfileId: String,
): Boolean = downloadOwnerContextMatches(
    expectedAccountId = expectedAccountId,
    expectedProfileId = expectedProfileId,
    activeAccountId = activeAccountId,
    activeProfileId = activeProfileId,
)

/**
 * Captures the pre-existing account owner before a new login can claim account
 * scope, then exposes rollback-safe account namespaces for download metadata.
 * Unknown legacy data stays in the original preferences and is never loaded.
 */
internal class DownloadAccountStorage(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val migrationState = appContext.getSharedPreferences(
        MIGRATION_STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun captureLegacyOwner(): Boolean {
        synchronized(MIGRATION_LOCK) {
            if (migrationState.getBoolean(KEY_LEGACY_OWNER_CAPTURED, false)) return true
            val legacyOwner = accountScope.legacyOwnerAccountId()
            return migrationState.edit()
                .putBoolean(KEY_LEGACY_OWNER_CAPTURED, true)
                .apply {
                    if (legacyOwner == null) {
                        remove(KEY_CAPTURED_LEGACY_OWNER)
                    } else {
                        putString(KEY_CAPTURED_LEGACY_OWNER, legacyOwner)
                    }
                }
                .commit()
        }
    }

    fun preferences(baseName: String, accountId: String): SharedPreferences {
        synchronized(MIGRATION_LOCK) {
            check(captureLegacyOwner()) { "Unable to capture legacy download owner" }
            val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("accountId must not be blank")
            val scoped = appContext.getSharedPreferences(
                accountScopedPreferencesName(baseName, normalizedAccountId),
                Context.MODE_PRIVATE,
            )
            if (scoped.getBoolean(KEY_ACCOUNT_DOWNLOAD_SCOPE_MIGRATED, false)) return scoped

            val capturedLegacyOwner = migrationState.getString(KEY_CAPTURED_LEGACY_OWNER, null)
            val editor = scoped.edit()
            if (
                legacyDownloadMigrationPolicy(capturedLegacyOwner, normalizedAccountId) ==
                LegacyDownloadMigrationPolicy.MIGRATE
            ) {
                copyPreferences(
                    source = appContext.getSharedPreferences(baseName, Context.MODE_PRIVATE),
                    target = editor,
                )
            }
            check(editor.putBoolean(KEY_ACCOUNT_DOWNLOAD_SCOPE_MIGRATED, true).commit()) {
                "Unable to persist download account migration"
            }
            return scoped
        }
    }

    private fun copyPreferences(
        source: SharedPreferences,
        target: SharedPreferences.Editor,
    ) {
        source.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> target.putBoolean(key, value)
                is Float -> target.putFloat(key, value)
                is Int -> target.putInt(key, value)
                is Long -> target.putLong(key, value)
                is String -> target.putString(key, value)
                is Set<*> -> target.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private companion object {
        val MIGRATION_LOCK = Any()
        const val MIGRATION_STATE_PREFERENCES = "hulk_download_account_scope_v1"
        const val KEY_LEGACY_OWNER_CAPTURED = "legacy_owner_captured"
        const val KEY_CAPTURED_LEGACY_OWNER = "captured_legacy_owner_account_id"
        const val KEY_ACCOUNT_DOWNLOAD_SCOPE_MIGRATED = "__download_account_scope_migrated_v1"
    }
}

/**
 * Process-local owner for stateful account repositories. WorkManager runs in the
 * app process by default, so one instance per account prevents parallel OkHttp
 * writers from touching the same partial download file.
 */
internal object DownloadRepositoryProcessOwner {
    private val instances = mutableMapOf<String, DownloadRepository>()

    fun captureLegacyOwner(context: Context): Boolean =
        DownloadAccountStorage(context.applicationContext).captureLegacyOwner()

    @Synchronized
    fun get(context: Context, accountId: String): DownloadRepository {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("accountId must not be blank")
        return instances.getOrPut(normalizedAccountId) {
            val appContext = context.applicationContext
            val accountStorage = DownloadAccountStorage(appContext)
            val downloadPreferences = accountStorage.preferences(
                DurableDownloadPreferenceStore.PREFERENCES_NAME,
                normalizedAccountId,
            )
            val legacyOwnershipStore = if (
                downloadPreferences.getInt(DurableDownloadPreferenceStore.KEY_STORAGE_VERSION, 0) <
                DurableDownloadPreferenceStore.STORAGE_VERSION
            ) {
                LegacyProfileDownloadOwnershipStore(appContext, normalizedAccountId)
            } else {
                null
            }
            DownloadRepository(
                context = appContext,
                accountId = normalizedAccountId,
                preferences = downloadPreferences,
                legacyOwners = { historyKey -> legacyOwnershipStore?.explicitOwners(historyKey) },
            )
        }
    }

    fun getActive(context: Context): DownloadRepository? {
        val appContext = context.applicationContext
        val accountId = authenticatedDownloadAccountId(
            session = AuthenticatedSessionRegistry.current(),
            metadata = AccountSessionStore(appContext).metadata(),
        ) ?: return null
        return activate(appContext, accountId)
    }

    @Synchronized
    fun activate(context: Context, accountId: String): DownloadRepository {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("accountId must not be blank")
        instances.keys.filterNot { it == normalizedAccountId }.toList().forEach { inactiveAccountId ->
            instances.remove(inactiveAccountId)?.suspendForAccountBoundary()
            DurableDownloadScheduler(context.applicationContext).cancelAccount(inactiveAccountId)
        }
        return get(context, normalizedAccountId)
    }

    @Synchronized
    fun suspendAccount(context: Context, accountId: String) {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty) ?: return
        instances.remove(normalizedAccountId)?.suspendForAccountBoundary()
        DurableDownloadScheduler(context.applicationContext).cancelAccount(normalizedAccountId)
    }
}

/**
 * UI-facing facade over the active account's physical download engine.
 *
 * Each account has one physical transfer queue, while this facade scopes
 * visibility and references to the active profile. A physical file can be owned
 * by more than one profile in that account, avoiding duplicate files while
 * keeping each profile's Downloads screen isolated.
 */
internal data class ProfileDownloadEnqueueOutcome(
    val result: DownloadRepository.EnqueueResult,
    val downloads: List<OfflineDownload>,
)

internal data class ProfileDownloadPauseOutcome(
    val downloads: List<OfflineDownload>,
    val applied: Boolean,
    val persisted: Boolean,
)

internal data class ProfileDownloadMutationOutcome(
    val settings: DownloadSettings,
    val downloads: List<OfflineDownload>,
    val applied: Boolean,
)

internal class ProfileScopedDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val accountSessionStore = AccountSessionStore(appContext)
    private val profileStore = ProfileStore(appContext)

    fun downloads(): List<OfflineDownload> = snapshot()

    fun snapshot(): List<OfflineDownload> {
        val binding = activeSnapshotBinding() ?: return emptyList()
        return ProfileDownloadSnapshotList(
            accountId = binding.accountId,
            allDownloads = binding.delegate.snapshot(),
            activeAccountId = ::activeAccountId,
            activeProfileId = profileStore::activeProfileId,
        )
    }

    fun settings(): DownloadSettings = activeBinding()?.delegate?.settings() ?: DownloadSettings()

    fun setSettings(
        settings: DownloadSettings,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadMutationOutcome = mutateForOwner(expectedAccountId, expectedProfileId) { binding ->
        binding.delegate.setSettings(settings) to true
    }

    fun cyclePriority(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadMutationOutcome = mutateForOwner(expectedAccountId, expectedProfileId) { binding ->
        val ownsDownload = owns(binding, downloadId)
        if (ownsDownload) binding.delegate.cyclePriority(downloadId)
        binding.delegate.settings() to ownsDownload
    }

    fun enqueue(
        request: PlaybackRequest,
        expectedAccountId: String,
        expectedProfileId: String,
        seriesTitle: String? = null,
        season: Int? = null,
        episodeNumber: Int? = null,
    ): ProfileDownloadEnqueueOutcome {
        if (
            !downloadOwnerContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) {
            return ProfileDownloadEnqueueOutcome(
                result = DownloadRepository.EnqueueResult.Failed("تغير المستخدم قبل بدء التحميل."),
                downloads = emptyList(),
            )
        }
        val binding = activeBinding()
            ?: return ProfileDownloadEnqueueOutcome(
                result = DownloadRepository.EnqueueResult.Failed("سجل الدخول قبل بدء التحميل."),
                downloads = emptyList(),
            )
        if (binding.accountId != expectedAccountId) {
            return ProfileDownloadEnqueueOutcome(
                result = DownloadRepository.EnqueueResult.Failed("تغير الحساب قبل بدء التحميل."),
                downloads = emptyList(),
            )
        }
        val result = binding.delegate.enqueue(
            request = request,
            ownerProfileId = expectedProfileId,
            seriesTitle = seriesTitle,
            season = season,
            episodeNumber = episodeNumber,
        )
        return ProfileDownloadEnqueueOutcome(
            result = result,
            downloads = snapshotForOwner(binding, expectedAccountId, expectedProfileId),
        )
    }

    fun pause(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadPauseOutcome {
        if (
            !downloadOwnerContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) {
            return ProfileDownloadPauseOutcome(
                downloads = snapshot(),
                applied = false,
                persisted = true,
            )
        }
        val binding = activeBinding()
            ?: return ProfileDownloadPauseOutcome(
                downloads = emptyList(),
                applied = false,
                persisted = true,
            )
        if (binding.accountId != expectedAccountId || !owns(binding, downloadId)) {
            return ProfileDownloadPauseOutcome(
                downloads = snapshot(),
                applied = false,
                persisted = true,
            )
        }
        val result = binding.delegate.pause(downloadId)
        return ProfileDownloadPauseOutcome(
            downloads = snapshot(),
            applied = result.persisted,
            persisted = result.persisted,
        )
    }

    fun resume(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): ProfileDownloadMutationOutcome = mutateForOwner(expectedAccountId, expectedProfileId) { binding ->
        val resumed = owns(binding, downloadId) && binding.delegate.resume(downloadId)
        binding.delegate.settings() to resumed
    }

    fun remove(downloadId: Long): List<OfflineDownload> {
        val expectedAccountId = activeAccountId() ?: return emptyList()
        val expectedProfileId = profileStore.activeProfileId()
        return remove(downloadId, expectedAccountId, expectedProfileId)
    }

    fun remove(
        downloadId: Long,
        expectedAccountId: String,
        expectedProfileId: String,
    ): List<OfflineDownload> {
        if (
            !downloadRemovalContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) return snapshot()

        val binding = activeBinding() ?: return emptyList()
        if (binding.accountId != expectedAccountId) return snapshot()
        val item = binding.delegate.snapshot().firstOrNull { it.downloadId == downloadId }
            ?: return snapshot()
        if (expectedProfileId !in item.ownerProfileIds) return snapshot()

        if (
            !downloadRemovalContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) return snapshot()

        binding.delegate.removeOwner(downloadId, expectedProfileId)
        return snapshot()
    }

    fun playableLocalUri(downloadId: Long, historyKey: String): String? {
        val binding = activeBinding() ?: return null
        val item = binding.delegate.record(downloadId) ?: return null
        if (item.historyKey != historyKey) return null
        if (!owns(binding, downloadId)) return null
        return item.localUri?.takeIf(String::isNotBlank)
    }

    fun activeAccountIdForCleanup(): String? = activeAccountId()

    fun removeProfile(accountId: String, profileId: String) {
        val normalizedAccountId = accountId.trim().takeIf(String::isNotEmpty) ?: return
        val normalizedProfileId = profileId.trim().takeIf(String::isNotEmpty) ?: return
        val delegate = DownloadRepositoryProcessOwner.get(appContext, normalizedAccountId)
        val downloads = delegate.downloads()
        downloads.forEach { item ->
            if (normalizedProfileId in item.ownerProfileIds) {
                delegate.removeOwner(item.downloadId, normalizedProfileId)
            }
        }
    }

    fun suspendActiveAccountForLogout() {
        val accountId = accountSessionStore.metadata()?.accountId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return
        DownloadRepositoryProcessOwner.suspendAccount(appContext, accountId)
    }

    private fun snapshotForOwner(
        binding: Binding,
        accountId: String,
        profileId: String,
    ): List<OfflineDownload> = ProfileDownloadSnapshotList(
        accountId = binding.accountId,
        allDownloads = binding.delegate.snapshot(),
        activeAccountId = { accountId },
        activeProfileId = { profileId },
    )

    private fun mutateForOwner(
        expectedAccountId: String,
        expectedProfileId: String,
        mutation: (Binding) -> Pair<DownloadSettings, Boolean>,
    ): ProfileDownloadMutationOutcome {
        if (
            !downloadOwnerContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) {
            return ProfileDownloadMutationOutcome(DownloadSettings(), emptyList(), applied = false)
        }
        val binding = activeBinding()
            ?: return ProfileDownloadMutationOutcome(DownloadSettings(), emptyList(), applied = false)
        if (binding.accountId != expectedAccountId) {
            return ProfileDownloadMutationOutcome(DownloadSettings(), emptyList(), applied = false)
        }
        val (settings, applied) = mutation(binding)
        return ProfileDownloadMutationOutcome(
            settings = settings,
            downloads = snapshotForOwner(binding, expectedAccountId, expectedProfileId),
            applied = applied,
        )
    }

    private fun owns(binding: Binding, downloadId: Long): Boolean {
        val item = binding.delegate.record(downloadId) ?: return false
        return accountDownloadAccessAllowed(
            recordAccountId = binding.accountId,
            activeAccountId = activeAccountId(),
            profileOwnsRecord = profileStore.activeProfileId() in item.ownerProfileIds,
        )
    }

    private fun activeBinding(): Binding? = activeSnapshotBinding()

    private fun activeSnapshotBinding(): Binding? {
        val accountId = activeAccountId() ?: return null
        val delegate = DownloadRepositoryProcessOwner.activate(appContext, accountId)
        return Binding(accountId, delegate)
    }

    private fun activeAccountId(): String? = authenticatedDownloadAccountId(
        session = AuthenticatedSessionRegistry.current(),
        metadata = accountSessionStore.metadata(),
    )

    private data class Binding(
        val accountId: String,
        val delegate: DownloadRepository,
    )
}

/**
 * Immutable profile-scoped view of one physical download snapshot.
 *
 * Account/profile resolution and ownership filtering happen exactly once when
 * the snapshot is built. List accessors only read the already-filtered list.
 */
internal class ProfileDownloadSnapshotList(
    accountId: String,
    allDownloads: List<OfflineDownload>,
    activeAccountId: () -> String?,
    activeProfileId: () -> String,
) : AbstractList<OfflineDownload>() {
    private val snapshot: List<OfflineDownload> = run {
        val resolvedAccountId = activeAccountId()
        if (
            !accountDownloadAccessAllowed(
                recordAccountId = accountId,
                activeAccountId = resolvedAccountId,
                profileOwnsRecord = true,
            )
        ) {
            emptyList()
        } else {
            val profileId = activeProfileId()
            allDownloads.filter { item ->
                profileId in item.ownerProfileIds
            }
        }
    }

    override val size: Int
        get() = snapshot.size

    override fun get(index: Int): OfflineDownload = snapshot[index]

    override fun iterator(): Iterator<OfflineDownload> = snapshot.iterator()

    override fun listIterator(index: Int): ListIterator<OfflineDownload> = snapshot.listIterator(index)
}

/** One record owns both the physical download state and its profile references. */
internal data class ProfileReferenceRemoval(
    val remainingOwners: Set<String>,
    val deletePhysicalDownload: Boolean,
)

internal fun profileReferenceRemoval(
    owners: Set<String>,
    profileId: String,
): ProfileReferenceRemoval {
    val normalizedProfileId = profileId.trim().takeIf(String::isNotEmpty)
        ?: return ProfileReferenceRemoval(owners, deletePhysicalDownload = false)
    val remaining = owners - normalizedProfileId
    return ProfileReferenceRemoval(
        remainingOwners = remaining,
        deletePhysicalDownload = remaining.isEmpty(),
    )
}

/** Read-only migration source for ownership written by releases before storage v2. */
private class LegacyProfileDownloadOwnershipStore(
    context: Context,
    accountId: String,
) {
    private val preferences = DownloadAccountStorage(context.applicationContext).preferences(
        PREFERENCES_NAME,
        accountId,
    )

    fun explicitOwners(historyKey: String): Set<String>? {
        val key = ownersKey(historyKey)
        if (!preferences.contains(key)) return null
        return preferences.getStringSet(key, emptySet())
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    private fun ownersKey(historyKey: String): String = "$KEY_OWNER_PREFIX$historyKey"

    companion object {
        const val PREFERENCES_NAME = "hulk_profile_download_ownership_v1"
        const val KEY_OWNER_PREFIX = "owners:"
    }
}

/**
 * More-specific overload used by AndroidViewModel callers. Each account keeps
 * one physical repository while the ViewModel sees only the active profile's
 * owned references.
 */
@Suppress("FunctionName")
internal fun DownloadRepository(application: Application): ProfileScopedDownloadRepository =
    ProfileScopedDownloadRepository(application)

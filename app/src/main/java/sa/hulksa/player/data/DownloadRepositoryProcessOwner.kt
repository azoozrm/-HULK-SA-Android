package sa.hulksa.player.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import sa.hulksa.player.model.DownloadScheduleMode
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
            DownloadRepository(
                context = appContext,
                accountId = normalizedAccountId,
                preferences = DownloadAccountStorage(appContext).preferences(
                    DurableDownloadPreferenceStore.PREFERENCES_NAME,
                    normalizedAccountId,
                ),
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

internal class ProfileScopedDownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val accountSessionStore = AccountSessionStore(appContext)
    private val profileStore = ProfileStore(appContext)
    private val ownershipStores = mutableMapOf<String, ProfileDownloadOwnershipStore>()

    fun downloads(): List<OfflineDownload> = snapshot()

    fun snapshot(): List<OfflineDownload> {
        val binding = activeSnapshotBinding() ?: return emptyList()
        return ProfileDownloadSnapshotList(
            accountId = binding.accountId,
            allDownloads = binding.delegate.snapshot(),
            activeAccountId = ::activeAccountId,
            activeProfileId = profileStore::activeProfileId,
            ownersForExistingDownload = binding.ownershipStore::ownersForExistingDownload,
        )
    }

    fun settings(): DownloadSettings = activeBinding()?.delegate?.settings() ?: DownloadSettings()

    fun setWifiOnly(enabled: Boolean): DownloadSettings =
        activeBinding()?.delegate?.setWifiOnly(enabled) ?: DownloadSettings()

    fun setScheduleMode(mode: DownloadScheduleMode): DownloadSettings =
        activeBinding()?.delegate?.setScheduleMode(mode) ?: DownloadSettings()

    fun setConcurrentDownloads(count: Int): DownloadSettings =
        activeBinding()?.delegate?.setConcurrentDownloads(count) ?: DownloadSettings()

    fun cyclePriority(downloadId: Long): List<OfflineDownload> {
        val binding = activeBinding() ?: return emptyList()
        if (owns(binding, downloadId)) binding.delegate.cyclePriority(downloadId)
        return snapshot()
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
        val existing = binding.delegate.snapshot().firstOrNull { it.historyKey == request.historyKey }
        val ownersBefore = if (existing != null) {
            binding.ownershipStore.ownersForExistingDownload(request.historyKey)
        } else {
            binding.ownershipStore.explicitOwners(request.historyKey).orEmpty()
        }
        val alreadyOwned = expectedProfileId in ownersBefore

        if (
            !binding.ownershipStore.addOwner(
                historyKey = request.historyKey,
                profileId = expectedProfileId,
                includeLegacyPrimary = existing != null,
            )
        ) {
            return ProfileDownloadEnqueueOutcome(
                result = DownloadRepository.EnqueueResult.Failed("تعذر حفظ ملكية التحميل."),
                downloads = snapshotForOwner(binding, expectedAccountId, expectedProfileId),
            )
        }

        return try {
            val result = binding.delegate.enqueue(
                request = request,
                seriesTitle = seriesTitle,
                season = season,
                episodeNumber = episodeNumber,
            )
            val persistedResult = if (result is DownloadRepository.EnqueueResult.Failed && !alreadyOwned) {
                if (binding.ownershipStore.removeExplicitOwner(request.historyKey, expectedProfileId)) {
                    result
                } else {
                    DownloadRepository.EnqueueResult.Failed("تعذر حفظ حالة التحميل.")
                }
            } else {
                result
            }
            ProfileDownloadEnqueueOutcome(
                result = persistedResult,
                downloads = snapshotForOwner(binding, expectedAccountId, expectedProfileId),
            )
        } catch (error: Throwable) {
            if (!alreadyOwned) {
                binding.ownershipStore.removeExplicitOwner(request.historyKey, expectedProfileId)
            }
            throw error
        }
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

    fun resume(downloadId: Long): Boolean {
        val binding = activeBinding() ?: return false
        return owns(binding, downloadId) && binding.delegate.resume(downloadId)
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
        val owners = binding.ownershipStore.ownersForExistingDownload(item.historyKey)
        if (expectedProfileId !in owners) return snapshot()

        if (
            !downloadRemovalContextMatches(
                expectedAccountId = expectedAccountId,
                expectedProfileId = expectedProfileId,
                activeAccountId = activeAccountId(),
                activeProfileId = profileStore.activeProfileId(),
            )
        ) return snapshot()

        val removal = profileReferenceRemoval(owners, expectedProfileId)
        if (removal.deletePhysicalDownload) {
            binding.delegate.remove(downloadId)
            if (binding.delegate.record(downloadId) == null) {
                binding.ownershipStore.clearOwners(item.historyKey)
            }
        } else {
            binding.ownershipStore.removeExistingOwner(item.historyKey, expectedProfileId)
        }
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
        val ownershipStore = ownershipStore(normalizedAccountId)
        val downloads = delegate.downloads()
        ownershipStore.migrateLegacy(downloads)
        downloads.forEach { item ->
            val owners = ownershipStore.ownersForExistingDownload(item.historyKey)
            if (normalizedProfileId !in owners) return@forEach
            val removal = profileReferenceRemoval(owners, normalizedProfileId)
            if (removal.deletePhysicalDownload) {
                delegate.remove(item.downloadId)
                if (delegate.record(item.downloadId) == null) {
                    ownershipStore.clearOwners(item.historyKey)
                }
            } else {
                ownershipStore.removeExistingOwner(item.historyKey, normalizedProfileId)
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
        ownersForExistingDownload = binding.ownershipStore::ownersForExistingDownload,
    )

    private fun owns(binding: Binding, downloadId: Long): Boolean {
        val item = binding.delegate.record(downloadId) ?: return false
        return accountDownloadAccessAllowed(
            recordAccountId = binding.accountId,
            activeAccountId = activeAccountId(),
            profileOwnsRecord = profileStore.activeProfileId() in
                binding.ownershipStore.ownersForExistingDownload(item.historyKey),
        )
    }

    private fun activeBinding(): Binding? {
        val binding = activeSnapshotBinding() ?: return null
        if (!binding.ownershipStore.migrateLegacy(binding.delegate.snapshot())) return null
        return binding
    }

    private fun activeSnapshotBinding(): Binding? {
        val accountId = activeAccountId() ?: return null
        val delegate = DownloadRepositoryProcessOwner.activate(appContext, accountId)
        return Binding(accountId, delegate, ownershipStore(accountId))
    }

    private fun activeAccountId(): String? = authenticatedDownloadAccountId(
        session = AuthenticatedSessionRegistry.current(),
        metadata = accountSessionStore.metadata(),
    )

    private fun ownershipStore(accountId: String): ProfileDownloadOwnershipStore =
        synchronized(ownershipStores) {
            ownershipStores.getOrPut(accountId) {
                ProfileDownloadOwnershipStore(appContext, accountId)
            }
        }

    private data class Binding(
        val accountId: String,
        val delegate: DownloadRepository,
        val ownershipStore: ProfileDownloadOwnershipStore,
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
    ownersForExistingDownload: (String) -> Set<String>,
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
                profileId in ownersForExistingDownload(item.historyKey)
            }
        }
    }

    override val size: Int
        get() = snapshot.size

    override fun get(index: Int): OfflineDownload = snapshot[index]

    override fun iterator(): Iterator<OfflineDownload> = snapshot.iterator()

    override fun listIterator(index: Int): ListIterator<OfflineDownload> = snapshot.listIterator(index)
}

/**
 * Profile ownership metadata for physical device downloads.
 *
 * Ownership is stored by historyKey because DownloadRepository already enforces
 * one physical download per historyKey. This lets two profiles reference the
 * same on-device file without duplicating storage. Downloads created before
 * Multi Profile are migrated once to the primary profile.
 */
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

private class ProfileDownloadOwnershipStore(
    context: Context,
    accountId: String,
) {
    private val preferences = DownloadAccountStorage(context.applicationContext).preferences(
        PREFERENCES_NAME,
        accountId,
    )

    @Synchronized
    fun migrateLegacy(downloads: List<OfflineDownload>): Boolean {
        if (preferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETE, false)) return true
        val editor = preferences.edit()
        downloads.forEach { item ->
            val key = ownersKey(item.historyKey)
            if (!preferences.contains(key)) {
                editor.putStringSet(key, setOf(ProfileStore.PRIMARY_PROFILE_ID))
            }
        }
        return editor.putBoolean(KEY_LEGACY_MIGRATION_COMPLETE, true).commit()
    }

    @Synchronized
    fun explicitOwners(historyKey: String): Set<String>? {
        val key = ownersKey(historyKey)
        if (!preferences.contains(key)) return null
        return preferences.getStringSet(key, emptySet())
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    @Synchronized
    fun ownersForExistingDownload(historyKey: String): Set<String> =
        explicitOwners(historyKey)
            ?.takeIf(Set<String>::isNotEmpty)
            ?: setOf(ProfileStore.PRIMARY_PROFILE_ID)

    @Synchronized
    fun addOwner(
        historyKey: String,
        profileId: String,
        includeLegacyPrimary: Boolean,
    ): Boolean {
        val current = explicitOwners(historyKey)
            ?: if (includeLegacyPrimary) setOf(ProfileStore.PRIMARY_PROFILE_ID) else emptySet()
        val updated = current + profileId
        if (updated == current && explicitOwners(historyKey) != null) return true
        return preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
    }

    @Synchronized
    fun removeExistingOwner(historyKey: String, profileId: String): Set<String> {
        val updated = profileReferenceRemoval(
            owners = ownersForExistingDownload(historyKey),
            profileId = profileId,
        ).remainingOwners
        if (updated.isEmpty()) {
            preferences.edit().remove(ownersKey(historyKey)).commit()
        } else {
            preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
        }
        return updated
    }

    @Synchronized
    fun removeExplicitOwner(historyKey: String, profileId: String): Boolean {
        val current = explicitOwners(historyKey) ?: return true
        val updated = current - profileId
        return if (updated.isEmpty()) {
            preferences.edit().remove(ownersKey(historyKey)).commit()
        } else {
            preferences.edit().putStringSet(ownersKey(historyKey), updated).commit()
        }
    }

    @Synchronized
    fun clearOwners(historyKey: String) {
        preferences.edit().remove(ownersKey(historyKey)).commit()
    }

    private fun ownersKey(historyKey: String): String = "$KEY_OWNER_PREFIX$historyKey"

    companion object {
        const val PREFERENCES_NAME = "hulk_profile_download_ownership_v1"
        const val KEY_OWNER_PREFIX = "owners:"
        const val KEY_LEGACY_MIGRATION_COMPLETE = "legacy_primary_migration_complete"
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

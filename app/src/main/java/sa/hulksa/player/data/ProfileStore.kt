package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile
import java.util.UUID

internal fun normalizeProfileName(raw: String): String? = raw
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(ProfileStore.MAX_DISPLAY_NAME_LENGTH)
    .takeIf(String::isNotBlank)

internal fun canDeleteProfile(isPrimary: Boolean, profileCount: Int): Boolean =
    !isPrimary && profileCount > 1

private const val PROFILE_BASE_NAME = "hulk_profiles_v1"
private const val PROFILE_DOCUMENT_KEY = "profiles"
private const val PROFILE_ACTIVE_KEY = "active_profile_id"
private const val PROFILE_SCHEMA_KEY = "schema_version"

/**
 * One profile established-state definition used by ProfileStore and account migration.
 * Recovery evidence counts, and a schema value never short-circuits other evidence.
 */
internal fun profilePreferencesHaveEstablishedState(preferences: SharedPreferences): Boolean =
    runCatching {
        val keys = preferences.all.keys
        keys.contains(PROFILE_DOCUMENT_KEY) ||
            keys.contains(PROFILE_ACTIVE_KEY) ||
            (keys.contains(PROFILE_SCHEMA_KEY) &&
                preferences.getInt(PROFILE_SCHEMA_KEY, 0) > 0) ||
            keys.any { it.startsWith(persistedRecoveryKeyPrefix(PROFILE_DOCUMENT_KEY)) } ||
            hasUnsafeLegacyMigrationMarker(preferences, PROFILE_BASE_NAME)
    }.getOrDefault(true)

internal suspend fun <T> runProfilePersistenceOffMain(operation: () -> T): T =
    withContext(Dispatchers.IO) { operation() }

internal data class ProfileStoreSnapshot(
    val profiles: List<UserProfile>,
    val activeProfileId: String,
)

internal data class ProfileStoreMutation<T>(
    val value: T,
    val snapshot: ProfileStoreSnapshot,
)

private sealed interface ProfileDocumentRead {
    data object Missing : ProfileDocumentRead

    data class Healthy(
        val profiles: List<UserProfile>,
        val activeProfileId: String,
        val damagedRecordCount: Int,
        val activeKeyPresent: Boolean,
    ) : ProfileDocumentRead

    data object Damaged : ProfileDocumentRead
}

class ProfileStore internal constructor(
    private val accountScope: AccountScopeStore,
    private val preferencesForName: (String) -> SharedPreferences,
) {
    constructor(context: Context) : this(
        AccountScopeStore(context.applicationContext),
        { name -> context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) },
    )

    @Synchronized
    fun profiles(): List<UserProfile> = readSnapshot(readPreferences())?.profiles.orEmpty()

    fun activeProfileOrNull(): UserProfile? {
        val snapshot = readSnapshot(readPreferences()) ?: return null
        return snapshot.profiles.firstOrNull { it.id == snapshot.activeProfileId }
    }

    @Synchronized
    fun activeProfile(): UserProfile = activeProfileOrNull() ?: integrityBlockedProfile()

    fun activeProfileId(): String = activeProfile().id

    @Synchronized
    fun setActiveProfile(profileId: String): Boolean {
        val preferences = writablePreferences()
        if (!ensureInitialized(preferences)) return false
        return setActiveProfile(preferences, profileId) != null
    }

    @Synchronized
    fun createProfile(
        displayName: String,
        avatarKey: String = UserProfile.DEFAULT_AVATAR_KEY,
        kind: ProfileKind = ProfileKind.STANDARD,
    ): UserProfile? {
        val preferences = writablePreferences()
        if (!ensureInitialized(preferences)) return null
        return createProfile(preferences, displayName, avatarKey, kind)
    }

    @Synchronized
    fun updateProfile(
        profileId: String,
        displayName: String,
        avatarKey: String,
    ): UserProfile? {
        val preferences = writablePreferences()
        if (!ensureInitialized(preferences)) return null
        return updateProfile(preferences, profileId, displayName, avatarKey)
    }

    @Synchronized
    fun deleteProfile(profileId: String): Boolean {
        val preferences = writablePreferences()
        if (!ensureInitialized(preferences)) return false
        return deleteProfile(preferences, profileId)
    }

    fun schemaVersion(): Int {
        return runCatching { readPreferences().getInt(KEY_SCHEMA_VERSION, 0) }
            .getOrDefault(CURRENT_SCHEMA_VERSION)
    }

    internal suspend fun load(expectedAccountId: String): ProfileStoreSnapshot? =
        runProfilePersistenceOffMain { loadForAccount(expectedAccountId) }

    internal suspend fun setActiveProfileForAccount(
        expectedAccountId: String,
        profileId: String,
    ): ProfileStoreSnapshot? = runProfilePersistenceOffMain {
        setActiveProfileForAccountBlocking(expectedAccountId, profileId)
    }

    internal suspend fun createProfileForAccount(
        expectedAccountId: String,
        displayName: String,
        avatarKey: String = UserProfile.DEFAULT_AVATAR_KEY,
        kind: ProfileKind = ProfileKind.STANDARD,
    ): ProfileStoreMutation<UserProfile>? = runProfilePersistenceOffMain {
        createProfileForAccountBlocking(expectedAccountId, displayName, avatarKey, kind)
    }

    internal suspend fun updateProfileForAccount(
        expectedAccountId: String,
        profileId: String,
        displayName: String,
        avatarKey: String,
    ): ProfileStoreMutation<UserProfile>? = runProfilePersistenceOffMain {
        updateProfileForAccountBlocking(expectedAccountId, profileId, displayName, avatarKey)
    }

    internal suspend fun deleteProfileForAccount(
        expectedAccountId: String,
        profileId: String,
    ): ProfileStoreMutation<Boolean>? = runProfilePersistenceOffMain {
        deleteProfileForAccountBlocking(expectedAccountId, profileId)
    }

    @Synchronized
    private fun setActiveProfileForAccountBlocking(
        expectedAccountId: String,
        profileId: String,
    ): ProfileStoreSnapshot? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val snapshot = setActiveProfile(preferences, profileId) ?: return null
        return snapshot.takeIf { accountScope.activeAccountId() == expectedAccountId }
    }

    @Synchronized
    private fun createProfileForAccountBlocking(
        expectedAccountId: String,
        displayName: String,
        avatarKey: String,
        kind: ProfileKind,
    ): ProfileStoreMutation<UserProfile>? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val profile = createProfile(preferences, displayName, avatarKey, kind)
            ?: return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        return ProfileStoreMutation(profile, readSnapshot(preferences) ?: return null)
    }

    @Synchronized
    private fun updateProfileForAccountBlocking(
        expectedAccountId: String,
        profileId: String,
        displayName: String,
        avatarKey: String,
    ): ProfileStoreMutation<UserProfile>? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val profile = updateProfile(preferences, profileId, displayName, avatarKey)
            ?: return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        return ProfileStoreMutation(profile, readSnapshot(preferences) ?: return null)
    }

    @Synchronized
    private fun deleteProfileForAccountBlocking(
        expectedAccountId: String,
        profileId: String,
    ): ProfileStoreMutation<Boolean>? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        if (!deleteProfile(preferences, profileId)) return null
        if (accountScope.activeAccountId() != expectedAccountId) return null
        return ProfileStoreMutation(true, readSnapshot(preferences) ?: return null)
    }

    @Synchronized
    private fun loadForAccount(expectedAccountId: String): ProfileStoreSnapshot? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        return readSnapshot(preferences)
            ?.takeIf { accountScope.activeAccountId() == expectedAccountId }
    }

    @Synchronized
    private fun ensureInitialized(scopedPreferences: SharedPreferences): Boolean {
        when (val document = readDocument(scopedPreferences)) {
            ProfileDocumentRead.Missing -> {
                val primary = createPrimaryProfile()
                val editor = scopedPreferences.edit()
                    .putString(KEY_PROFILES, encodeProfiles(listOf(primary)))
                    .putString(KEY_ACTIVE_PROFILE_ID, primary.id)
                    .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                return editor.commit()
            }
            ProfileDocumentRead.Damaged -> return false
            is ProfileDocumentRead.Healthy -> {
                val editor = scopedPreferences.edit()
                if (!document.activeKeyPresent) {
                    editor.putString(KEY_ACTIVE_PROFILE_ID, document.activeProfileId)
                }
                val currentSchemaVersion = runCatching {
                    scopedPreferences.getInt(KEY_SCHEMA_VERSION, 0)
                }.getOrDefault(CURRENT_SCHEMA_VERSION)
                if (currentSchemaVersion < CURRENT_SCHEMA_VERSION) {
                    editor.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                }
                return editor.commit()
            }
        }
    }

    @Synchronized
    private fun setActiveProfile(
        preferences: SharedPreferences,
        profileId: String,
    ): ProfileStoreSnapshot? {
        val current = writableSnapshot(preferences) ?: return null
        val target = current.profiles.firstOrNull { it.id == profileId } ?: return null
        val before = readRawProfileDocument(preferences)
        if (!preferences.edit().putString(KEY_ACTIVE_PROFILE_ID, target.id).commit()) {
            restoreRawProfileDocument(preferences, before)
            return null
        }
        return current.copy(activeProfileId = target.id)
    }

    @Synchronized
    private fun createProfile(
        preferences: SharedPreferences,
        displayName: String,
        avatarKey: String,
        kind: ProfileKind,
    ): UserProfile? {
        val normalizedName = normalizeProfileName(displayName) ?: return null
        val current = writableSnapshot(preferences)?.profiles ?: return null
        if (current.size >= MAX_PROFILES) return null
        val profile = UserProfile(
            id = "profile_${UUID.randomUUID()}",
            displayName = normalizedName,
            kind = kind,
            avatarKey = avatarKey.trim().ifBlank { UserProfile.DEFAULT_AVATAR_KEY },
            createdAtEpochMs = System.currentTimeMillis(),
            isPrimary = false,
        )
        val before = readRawProfileDocument(preferences)
        val persisted = preferences.edit()
            .putString(KEY_PROFILES, encodeProfiles(current + profile))
            .commit()
        if (!persisted) restoreRawProfileDocument(preferences, before)
        return profile.takeIf { persisted }
    }

    @Synchronized
    private fun updateProfile(
        preferences: SharedPreferences,
        profileId: String,
        displayName: String,
        avatarKey: String,
    ): UserProfile? {
        val normalizedName = normalizeProfileName(displayName) ?: return null
        val current = writableSnapshot(preferences)?.profiles ?: return null
        val index = current.indexOfFirst { it.id == profileId }
        if (index < 0) return null
        val updatedProfile = current[index].copy(
            displayName = normalizedName,
            avatarKey = avatarKey.trim().ifBlank { UserProfile.DEFAULT_AVATAR_KEY },
        )
        val updated = current.toMutableList().apply { this[index] = updatedProfile }
        val before = readRawProfileDocument(preferences)
        val persisted = preferences.edit()
            .putString(KEY_PROFILES, encodeProfiles(updated))
            .commit()
        if (!persisted) restoreRawProfileDocument(preferences, before)
        return updatedProfile.takeIf { persisted }
    }

    @Synchronized
    private fun deleteProfile(preferences: SharedPreferences, profileId: String): Boolean {
        val snapshot = writableSnapshot(preferences) ?: return false
        val current = snapshot.profiles
        val target = current.firstOrNull { it.id == profileId } ?: return false
        if (!canDeleteProfile(target.isPrimary, current.size)) return false
        val updated = current.filterNot { it.id == profileId }
        val before = readRawProfileDocument(preferences)
        val editor = preferences.edit().putString(KEY_PROFILES, encodeProfiles(updated))
        if (snapshot.activeProfileId == profileId) {
            val fallback = updated.firstOrNull(UserProfile::isPrimary) ?: updated.first()
            editor.putString(KEY_ACTIVE_PROFILE_ID, fallback.id)
        }
        val persisted = editor.commit()
        if (!persisted) restoreRawProfileDocument(preferences, before)
        return persisted
    }

    private fun readSnapshot(preferences: SharedPreferences): ProfileStoreSnapshot? =
        when (val document = readDocument(preferences)) {
            ProfileDocumentRead.Missing -> {
                val primary = createPrimaryProfile()
                ProfileStoreSnapshot(profiles = listOf(primary), activeProfileId = primary.id)
            }
            ProfileDocumentRead.Damaged -> null
            is ProfileDocumentRead.Healthy -> ProfileStoreSnapshot(
                profiles = document.profiles,
                activeProfileId = document.activeProfileId,
            )
        }

    private fun writableSnapshot(preferences: SharedPreferences): ProfileStoreSnapshot? {
        return when (val document = readDocument(preferences)) {
            is ProfileDocumentRead.Healthy -> {
                if (document.damagedRecordCount > 0) {
                    val raw = runCatching { preferences.getString(KEY_PROFILES, null) }
                        .getOrNull()
                        ?: return null
                    if (!preserveDamagedPersistedDocument(preferences, KEY_PROFILES, raw)) {
                        return null
                    }
                }
                ProfileStoreSnapshot(
                    profiles = document.profiles,
                    activeProfileId = document.activeProfileId,
                )
            }
            ProfileDocumentRead.Missing,
            ProfileDocumentRead.Damaged,
            -> null
        }
    }

    private data class RawProfileDocument(
        val profilesRaw: String?,
        val activeProfileIdRaw: String?,
    )

    private fun readRawProfileDocument(preferences: SharedPreferences): RawProfileDocument =
        RawProfileDocument(
            profilesRaw = runCatching { preferences.getString(KEY_PROFILES, null) }.getOrNull(),
            activeProfileIdRaw = runCatching {
                preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
            }.getOrNull(),
        )

    /**
     * Failed commits still update in-process SharedPreferences memory. Restoring the exact
     * pre-mutation document rights in memory prevents a later successful write from finalizing a
     * repair that was never durably committed. Recovery copies remain untouched.
     */
    private fun restoreRawProfileDocument(
        preferences: SharedPreferences,
        document: RawProfileDocument,
    ) {
        val editor = preferences.edit()
        if (document.profilesRaw == null) {
            editor.remove(KEY_PROFILES)
        } else {
            editor.putString(KEY_PROFILES, document.profilesRaw)
        }
        if (document.activeProfileIdRaw == null) {
            editor.remove(KEY_ACTIVE_PROFILE_ID)
        } else {
            editor.putString(KEY_ACTIVE_PROFILE_ID, document.activeProfileIdRaw)
        }
        editor.commit()
    }

    private fun readDocument(preferences: SharedPreferences): ProfileDocumentRead {
        val containsProfiles = runCatching { preferences.contains(KEY_PROFILES) }
            .getOrDefault(false)
        if (!containsProfiles) {
            return if (hasResidualEstablishedState(preferences)) {
                ProfileDocumentRead.Damaged
            } else {
                ProfileDocumentRead.Missing
            }
        }
        val raw = runCatching { preferences.getString(KEY_PROFILES, null) }.getOrNull()
            ?: return ProfileDocumentRead.Damaged
        if (raw.isBlank()) return ProfileDocumentRead.Damaged
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return ProfileDocumentRead.Damaged
        if (array.length() == 0) return ProfileDocumentRead.Damaged

        val activeKeyPresent = runCatching { preferences.contains(KEY_ACTIVE_PROFILE_ID) }
            .getOrDefault(false)
        val storedActiveId = if (activeKeyPresent) {
            runCatching { preferences.getString(KEY_ACTIVE_PROFILE_ID, null) }
                .getOrNull()
                ?.trim()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }

        val decoded = linkedMapOf<String, UserProfile>()
        val seenIds = linkedSetOf<String>()
        val conflictingIds = linkedSetOf<String>()
        var damagedRecords = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                damagedRecords += 1
                continue
            }
            val itemId = item.optString("id").trim().takeIf(String::isNotBlank)
            if (itemId != null && !seenIds.add(itemId)) {
                conflictingIds += itemId
                damagedRecords += 1
            }
            val profile = decodeProfile(item)
            if (profile == null) {
                damagedRecords += 1
                continue
            }
            if (profile.id in conflictingIds) continue
            if (decoded.put(profile.id, profile) != null) {
                conflictingIds += profile.id
                damagedRecords += 1
            }
        }
        conflictingIds.forEach(decoded::remove)
        if (decoded.isEmpty()) return ProfileDocumentRead.Damaged
        val profiles = decoded.values.toList()

        if (storedActiveId != null && storedActiveId in conflictingIds) {
            return ProfileDocumentRead.Damaged
        }
        val resolvedActiveId = profiles.firstOrNull { it.id == storedActiveId }?.id
            ?: if (!activeKeyPresent && profiles.size == 1 && damagedRecords == 0) {
                profiles.single().id
            } else {
                return ProfileDocumentRead.Damaged
            }
        return ProfileDocumentRead.Healthy(
            profiles = profiles,
            activeProfileId = resolvedActiveId,
            damagedRecordCount = damagedRecords,
            activeKeyPresent = activeKeyPresent || storedActiveId != null,
        )
    }

    private fun hasResidualEstablishedState(preferences: SharedPreferences): Boolean =
        profilePreferencesHaveEstablishedState(preferences)

    private fun readPreferences(): SharedPreferences {
        val accountId = accountScope.activeAccountId()
            ?: return preferencesForName(PREFERENCES_NAME)
        val scoped = preferencesForName(accountScopedPreferencesName(PREFERENCES_NAME, accountId))
        if (scoped.contains(KEY_PROFILES) || hasResidualEstablishedState(scoped)) return scoped
        return if (accountScope.legacyOwnerAccountId() == accountId) {
            preferencesForName(PREFERENCES_NAME)
        } else {
            scoped
        }
    }

    private fun writablePreferences(): SharedPreferences = accountScope.preferences(PREFERENCES_NAME)

    private fun createPrimaryProfile(): UserProfile = UserProfile(
        id = PRIMARY_PROFILE_ID,
        displayName = PRIMARY_PROFILE_NAME,
        kind = ProfileKind.STANDARD,
        createdAtEpochMs = System.currentTimeMillis(),
        isPrimary = true,
    )

    private fun integrityBlockedProfile(): UserProfile = UserProfile(
        id = INTEGRITY_BLOCKED_PROFILE_ID,
        displayName = PRIMARY_PROFILE_NAME,
        kind = ProfileKind.KIDS,
        createdAtEpochMs = 0L,
        isPrimary = true,
    )

    private fun decodeProfile(item: JSONObject): UserProfile? {
        val id = item.optString("id").trim().takeIf(String::isNotBlank) ?: return null
        val name = item.optString("displayName").trim().takeIf(String::isNotBlank) ?: return null
        val kindRaw = item.optString("kind").trim().takeIf(String::isNotBlank) ?: return null
        val kind = runCatching { ProfileKind.valueOf(kindRaw) }.getOrNull() ?: return null
        return UserProfile(
            id = id,
            displayName = name,
            kind = kind,
            avatarKey = item.optString("avatarKey", UserProfile.DEFAULT_AVATAR_KEY)
                .trim()
                .ifBlank { UserProfile.DEFAULT_AVATAR_KEY },
            createdAtEpochMs = item.optLong("createdAtEpochMs", 0L)
                .takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            isPrimary = item.optBoolean("isPrimary", id == PRIMARY_PROFILE_ID),
        )
    }

    private fun encodeProfiles(profiles: List<UserProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("displayName", profile.displayName)
                    .put("kind", profile.kind.name)
                    .put("avatarKey", profile.avatarKey)
                    .put("createdAtEpochMs", profile.createdAtEpochMs)
                    .put("isPrimary", profile.isPrimary),
            )
        }
        return array.toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val PRIMARY_PROFILE_ID = "primary"
        const val MAX_PROFILES = 5
        const val MAX_DISPLAY_NAME_LENGTH = 24
        internal const val INTEGRITY_BLOCKED_PROFILE_ID = "integrity-blocked"

        private const val PRIMARY_PROFILE_NAME = "الرئيسي"
        internal const val PREFERENCES_NAME = "hulk_profiles_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }
}

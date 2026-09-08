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

class ProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)

    @Synchronized
    fun profiles(): List<UserProfile> = readSnapshot(readPreferences()).profiles

    @Synchronized
    fun activeProfile(): UserProfile {
        val snapshot = readSnapshot(readPreferences())
        return snapshot.profiles.first { it.id == snapshot.activeProfileId }
    }

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
        return readPreferences().getInt(KEY_SCHEMA_VERSION, 0)
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
        return ProfileStoreMutation(profile, readSnapshot(preferences))
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
        return ProfileStoreMutation(profile, readSnapshot(preferences))
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
        return ProfileStoreMutation(true, readSnapshot(preferences))
    }

    @Synchronized
    private fun loadForAccount(expectedAccountId: String): ProfileStoreSnapshot? {
        if (accountScope.activeAccountId() != expectedAccountId) return null
        val preferences = accountScope.preferences(PREFERENCES_NAME, expectedAccountId)
        if (!ensureInitialized(preferences)) return null
        return readSnapshot(preferences)
            .takeIf { accountScope.activeAccountId() == expectedAccountId }
    }

    @Synchronized
    private fun ensureInitialized(scopedPreferences: SharedPreferences): Boolean {
        val storedProfiles = decodeProfiles(scopedPreferences.getString(KEY_PROFILES, null))
        val profiles = if (storedProfiles.isEmpty()) {
            listOf(createPrimaryProfile())
        } else {
            storedProfiles
        }
        val activeId = scopedPreferences.getString(KEY_ACTIVE_PROFILE_ID, null)
        val resolvedActive = profiles.firstOrNull { it.id == activeId }
            ?: profiles.firstOrNull(UserProfile::isPrimary)
            ?: profiles.first()

        val editor = scopedPreferences.edit()
        if (storedProfiles.isEmpty()) {
            editor.putString(KEY_PROFILES, encodeProfiles(profiles))
        }
        if (activeId != resolvedActive.id) {
            editor.putString(KEY_ACTIVE_PROFILE_ID, resolvedActive.id)
        }
        if (scopedPreferences.getInt(KEY_SCHEMA_VERSION, 0) < CURRENT_SCHEMA_VERSION) {
            editor.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        }
        return editor.commit()
    }

    @Synchronized
    private fun setActiveProfile(
        preferences: SharedPreferences,
        profileId: String,
    ): ProfileStoreSnapshot? {
        val current = readSnapshot(preferences)
        val target = current.profiles.firstOrNull { it.id == profileId } ?: return null
        if (!preferences.edit().putString(KEY_ACTIVE_PROFILE_ID, target.id).commit()) return null
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
        val current = readSnapshot(preferences).profiles
        if (current.size >= MAX_PROFILES) return null
        val profile = UserProfile(
            id = "profile_${UUID.randomUUID()}",
            displayName = normalizedName,
            kind = kind,
            avatarKey = avatarKey.trim().ifBlank { UserProfile.DEFAULT_AVATAR_KEY },
            createdAtEpochMs = System.currentTimeMillis(),
            isPrimary = false,
        )
        return profile.takeIf {
            preferences.edit().putString(KEY_PROFILES, encodeProfiles(current + profile)).commit()
        }
    }

    @Synchronized
    private fun updateProfile(
        preferences: SharedPreferences,
        profileId: String,
        displayName: String,
        avatarKey: String,
    ): UserProfile? {
        val normalizedName = normalizeProfileName(displayName) ?: return null
        val current = readSnapshot(preferences).profiles
        val index = current.indexOfFirst { it.id == profileId }
        if (index < 0) return null
        val updatedProfile = current[index].copy(
            displayName = normalizedName,
            avatarKey = avatarKey.trim().ifBlank { UserProfile.DEFAULT_AVATAR_KEY },
        )
        val updated = current.toMutableList().apply { this[index] = updatedProfile }
        return updatedProfile.takeIf {
            preferences.edit().putString(KEY_PROFILES, encodeProfiles(updated)).commit()
        }
    }

    @Synchronized
    private fun deleteProfile(preferences: SharedPreferences, profileId: String): Boolean {
        val current = readSnapshot(preferences).profiles
        val target = current.firstOrNull { it.id == profileId } ?: return false
        if (!canDeleteProfile(target.isPrimary, current.size)) return false
        val updated = current.filterNot { it.id == profileId }
        val snapshot = readSnapshot(preferences)
        val editor = preferences.edit().putString(KEY_PROFILES, encodeProfiles(updated))
        if (snapshot.activeProfileId == profileId) {
            val fallback = updated.firstOrNull(UserProfile::isPrimary) ?: updated.first()
            editor.putString(KEY_ACTIVE_PROFILE_ID, fallback.id)
        }
        return editor.commit()
    }

    private fun readSnapshot(preferences: SharedPreferences): ProfileStoreSnapshot {
        val decoded = decodeProfiles(preferences.getString(KEY_PROFILES, null))
        val profiles = if (decoded.isNotEmpty()) decoded else listOf(createPrimaryProfile())
        val storedActiveId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
        val activeProfileId = profiles.firstOrNull { it.id == storedActiveId }?.id
            ?: profiles.firstOrNull(UserProfile::isPrimary)?.id
            ?: profiles.first().id
        return ProfileStoreSnapshot(profiles = profiles, activeProfileId = activeProfileId)
    }

    private fun readPreferences(): SharedPreferences {
        val accountId = accountScope.activeAccountId()
            ?: return appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val scoped = appContext.getSharedPreferences(
            accountScopedPreferencesName(PREFERENCES_NAME, accountId),
            Context.MODE_PRIVATE,
        )
        if (scoped.contains(KEY_PROFILES)) return scoped
        return if (accountScope.legacyOwnerAccountId() == accountId) {
            appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
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

    private fun decodeProfiles(raw: String?): List<UserProfile> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim().takeIf(String::isNotBlank) ?: continue
                val name = item.optString("displayName").trim().takeIf(String::isNotBlank)
                    ?: PRIMARY_PROFILE_NAME
                val kind = runCatching {
                    ProfileKind.valueOf(item.optString("kind", ProfileKind.STANDARD.name))
                }.getOrDefault(ProfileKind.STANDARD)
                add(
                    UserProfile(
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
                    ),
                )
            }
        }.distinctBy(UserProfile::id)
    }.getOrDefault(emptyList())

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

        private const val PRIMARY_PROFILE_NAME = "الرئيسي"
        internal const val PREFERENCES_NAME = "hulk_profiles_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }
}

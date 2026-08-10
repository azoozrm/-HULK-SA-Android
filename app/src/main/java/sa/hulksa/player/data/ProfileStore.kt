package sa.hulksa.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import sa.hulksa.player.model.ProfileKind
import sa.hulksa.player.model.UserProfile

class ProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    init {
        ensureInitialized()
    }

    @Synchronized
    fun profiles(): List<UserProfile> {
        val decoded = decodeProfiles(preferences.getString(KEY_PROFILES, null))
        return if (decoded.isNotEmpty()) decoded else listOf(createPrimaryProfile())
    }

    @Synchronized
    fun activeProfile(): UserProfile {
        val profiles = profiles()
        val activeId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
        val active = profiles.firstOrNull { it.id == activeId }
        if (active != null) return active

        val fallback = profiles.firstOrNull(UserProfile::isPrimary) ?: profiles.first()
        preferences.edit().putString(KEY_ACTIVE_PROFILE_ID, fallback.id).commit()
        return fallback
    }

    fun activeProfileId(): String = activeProfile().id

    @Synchronized
    fun setActiveProfile(profileId: String): Boolean {
        val target = profiles().firstOrNull { it.id == profileId } ?: return false
        return preferences.edit().putString(KEY_ACTIVE_PROFILE_ID, target.id).commit()
    }

    fun schemaVersion(): Int = preferences.getInt(KEY_SCHEMA_VERSION, 0)

    @Synchronized
    private fun ensureInitialized() {
        val storedProfiles = decodeProfiles(preferences.getString(KEY_PROFILES, null))
        val profiles = if (storedProfiles.isEmpty()) {
            listOf(createPrimaryProfile())
        } else {
            storedProfiles
        }
        val activeId = preferences.getString(KEY_ACTIVE_PROFILE_ID, null)
        val resolvedActive = profiles.firstOrNull { it.id == activeId }
            ?: profiles.firstOrNull(UserProfile::isPrimary)
            ?: profiles.first()

        val editor = preferences.edit()
        if (storedProfiles.isEmpty()) {
            editor.putString(KEY_PROFILES, encodeProfiles(profiles))
        }
        if (activeId != resolvedActive.id) {
            editor.putString(KEY_ACTIVE_PROFILE_ID, resolvedActive.id)
        }
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) < CURRENT_SCHEMA_VERSION) {
            editor.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        }
        editor.commit()
    }

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

        private const val PRIMARY_PROFILE_NAME = "الرئيسي"
        private const val PREFERENCES_NAME = "hulk_profiles_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }
}

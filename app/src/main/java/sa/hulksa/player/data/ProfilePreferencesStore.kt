package sa.hulksa.player.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Account/profile routing preferences used by the Multi Profile entry flow.
 *
 * `last used profile` is intentionally NOT duplicated here; ProfileStore's
 * activeProfileId remains the single source of truth for that value.
 */
data class ProfileRoutingPreferences(
    val directEntryEnabled: Boolean = false,
    val defaultProfileId: String? = null,
)

/**
 * Profile-owned viewing preferences prepared in v1.1 so later playback and
 * language features can be added without another ownership migration.
 *
 * Null means "inherit the application/account default".
 */
data class ProfileViewingPreferences(
    val preferredContentLanguage: String? = null,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val autoplayNextEpisode: Boolean? = null,
    val subtitlesEnabledByDefault: Boolean? = null,
)

/**
 * Structural PIN metadata only.
 *
 * v1.1 does not enable Kids restrictions or store a raw PIN. A future secure
 * credential store can supply the verifier referenced by credentialVersion.
 */
data class ProfilePinFoundation(
    val enabled: Boolean = false,
    val credentialVersion: Int = 0,
)

/**
 * Persistence foundation for profile routing, future viewing preferences and
 * PIN capability metadata.
 *
 * AccountScopeStore keeps each account's profile metadata isolated while the
 * existing profile-level keys remain unchanged inside that account scope.
 */
class ProfilePreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val preferences: SharedPreferences
        get() = accountScope.preferences(PREFERENCES_NAME).also(::ensureSchema)
    private val profileStore = ProfileStore(appContext)

    fun routing(): ProfileRoutingPreferences {
        val storedDefault = preferences.getString(KEY_DEFAULT_PROFILE_ID, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val validDefault = storedDefault?.takeIf(::profileExists)
        if (storedDefault != null && validDefault == null) {
            preferences.edit().remove(KEY_DEFAULT_PROFILE_ID).apply()
        }
        return ProfileRoutingPreferences(
            directEntryEnabled = preferences.getBoolean(KEY_DIRECT_ENTRY_ENABLED, false),
            defaultProfileId = validDefault,
        )
    }

    @Synchronized
    fun setRouting(
        directEntryEnabled: Boolean,
        defaultProfileId: String?,
    ): ProfileRoutingPreferences {
        val normalizedDefault = defaultProfileId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.takeIf(::profileExists)
        preferences.edit().apply {
            putBoolean(KEY_DIRECT_ENTRY_ENABLED, directEntryEnabled)
            if (normalizedDefault == null) {
                remove(KEY_DEFAULT_PROFILE_ID)
            } else {
                putString(KEY_DEFAULT_PROFILE_ID, normalizedDefault)
            }
        }.commit()
        return routing()
    }

    fun viewing(profileId: String): ProfileViewingPreferences {
        val id = normalizedExistingProfileId(profileId) ?: return ProfileViewingPreferences()
        return ProfileViewingPreferences(
            preferredContentLanguage = stringOrNull(profileKey(id, KEY_CONTENT_LANGUAGE)),
            preferredAudioLanguage = stringOrNull(profileKey(id, KEY_AUDIO_LANGUAGE)),
            preferredSubtitleLanguage = stringOrNull(profileKey(id, KEY_SUBTITLE_LANGUAGE)),
            autoplayNextEpisode = nullableBoolean(profileKey(id, KEY_AUTOPLAY_NEXT)),
            subtitlesEnabledByDefault = nullableBoolean(profileKey(id, KEY_SUBTITLES_DEFAULT)),
        )
    }

    @Synchronized
    fun setViewing(
        profileId: String,
        value: ProfileViewingPreferences,
    ): ProfileViewingPreferences? {
        val id = normalizedExistingProfileId(profileId) ?: return null
        preferences.edit().apply {
            putNullableString(profileKey(id, KEY_CONTENT_LANGUAGE), value.preferredContentLanguage)
            putNullableString(profileKey(id, KEY_AUDIO_LANGUAGE), value.preferredAudioLanguage)
            putNullableString(profileKey(id, KEY_SUBTITLE_LANGUAGE), value.preferredSubtitleLanguage)
            putNullableBoolean(profileKey(id, KEY_AUTOPLAY_NEXT), value.autoplayNextEpisode)
            putNullableBoolean(profileKey(id, KEY_SUBTITLES_DEFAULT), value.subtitlesEnabledByDefault)
        }.commit()
        return viewing(id)
    }

    fun pinFoundation(profileId: String): ProfilePinFoundation {
        val id = normalizedExistingProfileId(profileId) ?: return ProfilePinFoundation()
        return ProfilePinFoundation(
            enabled = preferences.getBoolean(profileKey(id, KEY_PIN_ENABLED), false),
            credentialVersion = preferences
                .getInt(profileKey(id, KEY_PIN_CREDENTIAL_VERSION), 0)
                .coerceAtLeast(0),
        )
    }

    @Synchronized
    fun setPinFoundation(
        profileId: String,
        enabled: Boolean,
        credentialVersion: Int,
    ): ProfilePinFoundation? {
        val id = normalizedExistingProfileId(profileId) ?: return null
        preferences.edit()
            .putBoolean(profileKey(id, KEY_PIN_ENABLED), enabled)
            .putInt(profileKey(id, KEY_PIN_CREDENTIAL_VERSION), credentialVersion.coerceAtLeast(0))
            .commit()
        return pinFoundation(id)
    }

    /**
     * Removes only profile-owned preference metadata. Physical downloads,
     * credentials, sessions and account/device settings are intentionally out of
     * scope and must not be touched here.
     */
    @Synchronized
    fun removeProfilePreferences(profileId: String) {
        val id = profileId.trim().takeIf(String::isNotBlank) ?: return
        val prefix = "profile:$id:"
        val keys = preferences.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        preferences.edit().apply {
            keys.forEach(::remove)
        }.commit()
    }

    fun schemaVersion(): Int = preferences.getInt(KEY_SCHEMA_VERSION, 0)

    private fun normalizedExistingProfileId(profileId: String): String? = profileId
        .trim()
        .takeIf(String::isNotBlank)
        ?.takeIf(::profileExists)

    private fun profileExists(profileId: String): Boolean =
        profileStore.profiles().any { it.id == profileId }

    private fun profileKey(profileId: String, key: String): String =
        "profile:$profileId:$key"

    private fun stringOrNull(key: String): String? = preferences
        .getString(key, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun nullableBoolean(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    private fun SharedPreferences.Editor.putNullableString(
        key: String,
        value: String?,
    ) {
        val normalized = value?.trim()?.takeIf(String::isNotBlank)
        if (normalized == null) remove(key) else putString(key, normalized)
    }

    private fun SharedPreferences.Editor.putNullableBoolean(
        key: String,
        value: Boolean?,
    ) {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    @Synchronized
    private fun ensureSchema(scopedPreferences: SharedPreferences) {
        if (scopedPreferences.getInt(KEY_SCHEMA_VERSION, 0) >= CURRENT_SCHEMA_VERSION) return
        scopedPreferences.edit().putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).commit()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private const val PREFERENCES_NAME = "hulk_profile_preferences_v1"
        private const val KEY_SCHEMA_VERSION = "schema_version"
        private const val KEY_DIRECT_ENTRY_ENABLED = "direct_entry_enabled"
        private const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"
        private const val KEY_CONTENT_LANGUAGE = "preferred_content_language"
        private const val KEY_AUDIO_LANGUAGE = "preferred_audio_language"
        private const val KEY_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        private const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        private const val KEY_SUBTITLES_DEFAULT = "subtitles_enabled_by_default"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PIN_CREDENTIAL_VERSION = "pin_credential_version"
    }
}

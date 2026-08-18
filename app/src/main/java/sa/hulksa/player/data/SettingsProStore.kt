package sa.hulksa.player.data

import android.content.Context
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

data class SettingsProPlaybackSettings(
    val autoplayNextEpisode: Boolean = true,
    val resumePlayback: Boolean = true,
    val seekStepSeconds: Int = 10,
    val keepScreenOn: Boolean = true,
    val autoHideControls: Boolean = true,
)

internal val SETTINGS_PRO_SEEK_STEPS = listOf(10, 15, 30)

internal fun normalizedSeekStepSeconds(value: Int): Int =
    value.takeIf { it in SETTINGS_PRO_SEEK_STEPS } ?: SETTINGS_PRO_SEEK_STEPS.first()

internal fun nextSeekStepSeconds(current: Int): Int {
    val normalized = normalizedSeekStepSeconds(current)
    val index = SETTINGS_PRO_SEEK_STEPS.indexOf(normalized)
    return SETTINGS_PRO_SEEK_STEPS[(index + 1) % SETTINGS_PRO_SEEK_STEPS.size]
}

internal fun normalizedUserRating(value: Int?): Int? = value?.takeIf { it in 1..5 }

internal fun userRatingPreferenceKey(
    profileId: String,
    type: ContentType,
    contentId: Int,
): String = "rating:${profileId.trim()}:${type.name}:$contentId"

/**
 * v1.9 preferences that HULK SA can enforce locally without pretending to
 * control provider-side stream quality, audio or subtitle availability.
 */
class SettingsProStore(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
    private val profileStore = ProfileStore(appContext)
    private val preferences
        get() = accountScope.preferences(PREFERENCES_NAME)

    fun playbackSettings(): SettingsProPlaybackSettings = SettingsProPlaybackSettings(
        autoplayNextEpisode = preferences.getBoolean(KEY_AUTOPLAY_NEXT, true),
        resumePlayback = preferences.getBoolean(KEY_RESUME_PLAYBACK, true),
        seekStepSeconds = normalizedSeekStepSeconds(
            preferences.getInt(KEY_SEEK_STEP_SECONDS, SETTINGS_PRO_SEEK_STEPS.first()),
        ),
        keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true),
        autoHideControls = preferences.getBoolean(KEY_AUTO_HIDE_CONTROLS, true),
    )

    @Synchronized
    fun setAutoplayNextEpisode(enabled: Boolean): SettingsProPlaybackSettings {
        preferences.edit().putBoolean(KEY_AUTOPLAY_NEXT, enabled).commit()
        return playbackSettings()
    }

    @Synchronized
    fun setResumePlayback(enabled: Boolean): SettingsProPlaybackSettings {
        preferences.edit().putBoolean(KEY_RESUME_PLAYBACK, enabled).commit()
        return playbackSettings()
    }

    @Synchronized
    fun cycleSeekStep(): SettingsProPlaybackSettings {
        val next = nextSeekStepSeconds(playbackSettings().seekStepSeconds)
        preferences.edit().putInt(KEY_SEEK_STEP_SECONDS, next).commit()
        return playbackSettings()
    }

    @Synchronized
    fun setKeepScreenOn(enabled: Boolean): SettingsProPlaybackSettings {
        preferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).commit()
        return playbackSettings()
    }

    @Synchronized
    fun setAutoHideControls(enabled: Boolean): SettingsProPlaybackSettings {
        preferences.edit().putBoolean(KEY_AUTO_HIDE_CONTROLS, enabled).commit()
        return playbackSettings()
    }

    fun userRating(item: ContentItem): Int? {
        if (item.type == ContentType.LIVE) return null
        val key = ratingKey(item)
        if (!preferences.contains(key)) return null
        return normalizedUserRating(preferences.getInt(key, 0))
    }

    /** Selecting the active score again clears it. */
    @Synchronized
    fun toggleUserRating(item: ContentItem, score: Int): Int? {
        if (item.type == ContentType.LIVE) return null
        val normalized = normalizedUserRating(score) ?: return userRating(item)
        val key = ratingKey(item)
        val current = userRating(item)
        if (current == normalized) {
            preferences.edit().remove(key).commit()
            return null
        }
        preferences.edit().putInt(key, normalized).commit()
        return normalized
    }

    private fun ratingKey(item: ContentItem): String = userRatingPreferenceKey(
        profileId = profileStore.activeProfileId(),
        type = item.type,
        contentId = item.id,
    )

    private companion object {
        const val PREFERENCES_NAME = "hulk_settings_pro_v1"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_HIDE_CONTROLS = "auto_hide_controls"
    }
}

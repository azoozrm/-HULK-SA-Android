package sa.hulksa.player.data

import android.content.Context

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

/**
 * v1.9 preferences that HULK SA can enforce locally without pretending to
 * control provider-side stream quality, audio or subtitle availability.
 */
class SettingsProStore(context: Context) {
    private val appContext = context.applicationContext
    private val accountScope = AccountScopeStore(appContext)
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

    @Synchronized
    fun resetPlaybackSettings(): SettingsProPlaybackSettings {
        preferences.edit()
            .remove(KEY_AUTOPLAY_NEXT)
            .remove(KEY_RESUME_PLAYBACK)
            .remove(KEY_SEEK_STEP_SECONDS)
            .remove(KEY_KEEP_SCREEN_ON)
            .remove(KEY_AUTO_HIDE_CONTROLS)
            .commit()
        return playbackSettings()
    }

    private companion object {
        const val PREFERENCES_NAME = "hulk_settings_pro_v1"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val KEY_SEEK_STEP_SECONDS = "seek_step_seconds"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_HIDE_CONTROLS = "auto_hide_controls"
    }
}

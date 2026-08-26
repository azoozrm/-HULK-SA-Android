@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.content.Context
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

internal enum class AudioOutputCompatibilityMode {
    NORMAL,
    PCM_COMPATIBILITY,
}

internal enum class AudioRecoveryExecutionKind {
    SAME_PLAYER_REPREPARE,
    RECREATE_PLAYER_PCM_COMPATIBILITY,
}

internal data class AudioRecoveryExecutionPlan(
    val kind: AudioRecoveryExecutionKind,
    val outputMode: AudioOutputCompatibilityMode,
)

internal fun audioRecoveryExecutionPlan(action: AudioRecoveryAction): AudioRecoveryExecutionPlan = when (action) {
    AudioRecoveryAction.REPREPARE_CURRENT -> AudioRecoveryExecutionPlan(
        kind = AudioRecoveryExecutionKind.SAME_PLAYER_REPREPARE,
        outputMode = AudioOutputCompatibilityMode.NORMAL,
    )
    AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY -> AudioRecoveryExecutionPlan(
        kind = AudioRecoveryExecutionKind.RECREATE_PLAYER_PCM_COMPATIBILITY,
        outputMode = AudioOutputCompatibilityMode.PCM_COMPATIBILITY,
    )
    else -> error("No execution plan for $action")
}

internal fun initialAudioOutputCompatibilityMode(): AudioOutputCompatibilityMode =
    AudioOutputCompatibilityMode.NORMAL

internal fun TrackSelectionParameters.withCompatibilityAudioOutput(): TrackSelectionParameters =
    buildUpon()
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                )
                .build(),
        )
        .build()

/**
 * Normal playback keeps Media3's route-aware audio capabilities. The compatibility instance is
 * created only for the second bounded recovery attempt and deliberately removes encoded passthrough
 * capabilities so the platform decoder feeds PCM to AudioTrack instead of retrying the same direct
 * encoded output path.
 */
internal class HulkAudioRenderersFactory(
    context: Context,
    private val outputMode: AudioOutputCompatibilityMode,
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        if (outputMode == AudioOutputCompatibilityMode.NORMAL) {
            return super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
        }

        // Media3 1.10.1: pin the recovery-only sink to minimum PCM capabilities so route-reported
        // encoded passthrough support cannot select the same silent direct-output path again.
        @Suppress("DEPRECATION")
        return DefaultAudioSink.Builder()
            .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            .build()
    }
}

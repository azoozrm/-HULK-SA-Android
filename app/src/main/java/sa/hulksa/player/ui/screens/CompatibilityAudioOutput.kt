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

internal enum class CompatibilityAudioOffloadMode {
    DISABLED,
}

/**
 * Pure compatibility policy: selection intent is carried through unchanged, while the
 * compatibility player must disable audio offload. Keeping the selection value generic makes the
 * contract deterministic in local JVM tests without constructing Android-backed Media3 language
 * normalization objects.
 */
internal data class CompatibilityAudioTrackPolicy<T>(
    val preservedSelectionIntent: T,
    val offloadMode: CompatibilityAudioOffloadMode,
)

internal fun <T> compatibilityAudioTrackPolicy(
    selectionIntent: T,
): CompatibilityAudioTrackPolicy<T> = CompatibilityAudioTrackPolicy(
    preservedSelectionIntent = selectionIntent,
    offloadMode = CompatibilityAudioOffloadMode.DISABLED,
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

/**
 * Media3 adapter for the pure policy above. buildUpon() preserves the complete existing selection
 * parameters (languages, overrides, subtitle choices and other selection intent); only offload is
 * changed for the bounded compatibility recreation.
 */
internal fun TrackSelectionParameters.withCompatibilityAudioOutput(): TrackSelectionParameters {
    val policy = compatibilityAudioTrackPolicy(this)
    val builder = policy.preservedSelectionIntent.buildUpon()
    when (policy.offloadMode) {
        CompatibilityAudioOffloadMode.DISABLED -> builder.setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                )
                .build(),
        )
    }
    return builder.build()
}

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

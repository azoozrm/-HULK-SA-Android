@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer

internal const val MAX_AUDIO_RECOVERY_ATTEMPTS = 2

internal enum class AudioFailureClassification {
    RECOVERABLE_AUDIO,
    NON_AUDIO,
    FATAL,
}

internal enum class AudioRecoveryAction {
    NONE,
    REPREPARE_CURRENT,
    RESET_CURRENT_PIPELINE,
    EXHAUSTED,
}

internal class AudioRecoveryStateMachine(
    private val maxAttempts: Int = MAX_AUDIO_RECOVERY_ATTEMPTS,
) {
    init {
        require(maxAttempts > 0)
    }

    private var generationKey: String? = null
    private var commandInFlight = false
    private var awaitingOutcome = false
    private var invalidated = false

    internal var attemptsUsed: Int = 0
        private set

    internal val recoveryPending: Boolean
        get() = commandInFlight || awaitingOutcome

    fun beginGeneration(key: String) {
        generationKey = key
        attemptsUsed = 0
        commandInFlight = false
        awaitingOutcome = false
        invalidated = false
    }

    fun requestRecovery(
        key: String,
        classification: AudioFailureClassification,
        hasAudioTrack: Boolean,
    ): AudioRecoveryAction {
        if (
            invalidated ||
            key != generationKey ||
            classification != AudioFailureClassification.RECOVERABLE_AUDIO ||
            !hasAudioTrack
        ) {
            return AudioRecoveryAction.NONE
        }
        if (commandInFlight) return AudioRecoveryAction.NONE

        // A new audio failure after a dispatched attempt is the outcome of that attempt.
        if (awaitingOutcome) awaitingOutcome = false
        if (attemptsUsed >= maxAttempts) return AudioRecoveryAction.EXHAUSTED

        attemptsUsed += 1
        commandInFlight = true
        return if (attemptsUsed == 1) {
            AudioRecoveryAction.REPREPARE_CURRENT
        } else {
            AudioRecoveryAction.RESET_CURRENT_PIPELINE
        }
    }

    fun markRecoveryCommandIssued(key: String) {
        if (invalidated || key != generationKey || !commandInFlight) return
        commandInFlight = false
        awaitingOutcome = true
    }

    fun markPlaybackReady(key: String) {
        if (invalidated || key != generationKey) return
        commandInFlight = false
        awaitingOutcome = false
    }

    fun invalidate() {
        invalidated = true
        commandInFlight = false
        awaitingOutcome = false
    }
}

internal fun classifyAudioFailure(error: PlaybackException): AudioFailureClassification {
    val exoError = error as? ExoPlaybackException
    return classifyAudioFailure(
        errorCode = error.errorCode,
        rendererError = exoError?.type == ExoPlaybackException.TYPE_RENDERER,
        rendererMimeType = exoError?.rendererFormat?.sampleMimeType,
    )
}

internal fun classifyAudioFailure(
    errorCode: Int,
    rendererError: Boolean,
    rendererMimeType: String?,
): AudioFailureClassification {
    val isAudioRenderer =
        rendererError && rendererMimeType?.startsWith("audio/", ignoreCase = true) == true

    return when (errorCode) {
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
        -> AudioFailureClassification.RECOVERABLE_AUDIO

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> if (isAudioRenderer) {
            AudioFailureClassification.RECOVERABLE_AUDIO
        } else {
            AudioFailureClassification.NON_AUDIO
        }

        else -> if (isAudioRenderer) {
            AudioFailureClassification.FATAL
        } else {
            AudioFailureClassification.NON_AUDIO
        }
    }
}

internal fun audioRecoveryPositionMs(isLive: Boolean, currentPositionMs: Long): Long? =
    if (isLive) null else currentPositionMs.coerceAtLeast(0L)

internal fun ExoPlayer.hasAudioTrackForRecovery(): Boolean =
    currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_AUDIO }

internal fun ExoPlayer.isCurrentAudioRecoverySource(expectedSource: String): Boolean =
    currentMediaItem?.localConfiguration?.uri?.toString() == expectedSource

internal fun ExoPlayer.executeAudioRecovery(
    action: AudioRecoveryAction,
    isLive: Boolean,
    expectedSource: String,
) {
    require(action == AudioRecoveryAction.REPREPARE_CURRENT || action == AudioRecoveryAction.RESET_CURRENT_PIPELINE)
    check(isCurrentAudioRecoverySource(expectedSource))

    val resumePositionMs = audioRecoveryPositionMs(isLive, currentPosition)
    val preservedPlayWhenReady = playWhenReady
    val preservedSpeed = playbackParameters.speed
    val preservedVolume = volume
    val preservedTrackSelectionParameters = trackSelectionParameters

    when (action) {
        AudioRecoveryAction.REPREPARE_CURRENT -> {
            if (resumePositionMs != null) seekTo(resumePositionMs)
        }
        AudioRecoveryAction.RESET_CURRENT_PIPELINE -> {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(expectedSource))
            trackSelectionParameters = preservedTrackSelectionParameters
            if (resumePositionMs != null) seekTo(resumePositionMs)
            setPlaybackSpeed(preservedSpeed)
            volume = preservedVolume
        }
        else -> error("Unsupported audio recovery action")
    }

    prepare()
    playWhenReady = preservedPlayWhenReady
}

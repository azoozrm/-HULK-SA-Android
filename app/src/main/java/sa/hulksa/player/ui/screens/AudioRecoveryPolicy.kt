@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import java.lang.ref.WeakReference

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

internal object AudioRecoveryRegistry {
    private val states = mutableMapOf<String, WeakReference<AudioRecoveryStateMachine>>()

    @Synchronized
    fun register(key: String, stateMachine: AudioRecoveryStateMachine) {
        states[key] = WeakReference(stateMachine)
    }

    @Synchronized
    fun unregister(key: String, stateMachine: AudioRecoveryStateMachine) {
        val registered = states[key]?.get()
        if (registered == null || registered === stateMachine) states.remove(key)
    }

    @Synchronized
    fun current(key: String): AudioRecoveryStateMachine? {
        val registered = states[key]?.get()
        if (registered == null) states.remove(key)
        return registered
    }
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
        generationKey?.let { oldKey -> AudioRecoveryRegistry.unregister(oldKey, this) }
        generationKey = key
        attemptsUsed = 0
        commandInFlight = false
        awaitingOutcome = false
        invalidated = false
        AudioRecoveryRegistry.register(key, this)
    }

    fun requestRecovery(
        key: String,
        classification: AudioFailureClassification,
        hasAudioTrack: Boolean,
        explicitAudioEvidence: Boolean = false,
    ): AudioRecoveryAction {
        if (
            invalidated ||
            key != generationKey ||
            classification != AudioFailureClassification.RECOVERABLE_AUDIO ||
            (!hasAudioTrack && !explicitAudioEvidence)
        ) {
            return AudioRecoveryAction.NONE
        }
        if (commandInFlight) return AudioRecoveryAction.NONE

        // A confirmed new failure after a dispatched attempt is that attempt's unsuccessful outcome.
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
        // READY proves player readiness, not audio-output health. A pending recovery remains pending
        // until Media3 reports audio-output progression or a new confirmed failure arrives.
        if (invalidated || key != generationKey) return
    }

    fun markAudioHealthy(key: String) {
        if (invalidated || key != generationKey) return
        commandInFlight = false
        awaitingOutcome = false
    }

    fun invalidate() {
        generationKey?.let { key -> AudioRecoveryRegistry.unregister(key, this) }
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

internal fun explicitAudioRecoveryGate(
    classification: AudioFailureClassification,
    hasAudioTrack: Boolean,
): Boolean = hasAudioTrack || classification == AudioFailureClassification.RECOVERABLE_AUDIO

internal fun audioRecoveryPositionMs(isLive: Boolean, currentPositionMs: Long): Long? =
    if (isLive) null else currentPositionMs.coerceAtLeast(0L)

internal data class AudioRecoveryPreservedState<T>(
    val positionMs: Long?,
    val playWhenReady: Boolean,
    val speed: Float,
    val volume: Float,
    val trackSelectionParameters: T,
)

internal fun <T> preservedAudioRecoveryState(
    isLive: Boolean,
    currentPositionMs: Long,
    playWhenReady: Boolean,
    speed: Float,
    volume: Float,
    trackSelectionParameters: T,
): AudioRecoveryPreservedState<T> = AudioRecoveryPreservedState(
    positionMs = audioRecoveryPositionMs(isLive, currentPositionMs),
    playWhenReady = playWhenReady,
    speed = speed,
    volume = volume,
    trackSelectionParameters = trackSelectionParameters,
)

internal fun ExoPlayer.hasAudioTrackForRecovery(): Boolean {
    val hasPopulatedAudioTrack = currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_AUDIO }
    val explicitClassification = playerError?.let(::classifyAudioFailure) ?: AudioFailureClassification.NON_AUDIO
    return explicitAudioRecoveryGate(explicitClassification, hasPopulatedAudioTrack)
}

internal fun ExoPlayer.isCurrentAudioRecoverySource(expectedSource: String): Boolean =
    currentMediaItem?.localConfiguration?.uri?.toString() == expectedSource

internal fun ExoPlayer.executeAudioRecovery(
    action: AudioRecoveryAction,
    isLive: Boolean,
    expectedSource: String,
) {
    require(action == AudioRecoveryAction.REPREPARE_CURRENT || action == AudioRecoveryAction.RESET_CURRENT_PIPELINE)
    check(isCurrentAudioRecoverySource(expectedSource))

    val preserved = preservedAudioRecoveryState(
        isLive = isLive,
        currentPositionMs = currentPosition,
        playWhenReady = playWhenReady,
        speed = playbackParameters.speed,
        volume = volume,
        trackSelectionParameters = trackSelectionParameters,
    )

    when (action) {
        AudioRecoveryAction.REPREPARE_CURRENT -> {
            // Lightest deterministic same-source renderer/sink restart.
            stop()
            trackSelectionParameters = preserved.trackSelectionParameters
            if (preserved.positionMs != null) {
                seekTo(preserved.positionMs)
            } else {
                seekToDefaultPosition()
            }
            setPlaybackSpeed(preserved.speed)
            volume = preserved.volume
        }
        AudioRecoveryAction.RESET_CURRENT_PIPELINE -> {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(expectedSource))
            trackSelectionParameters = preserved.trackSelectionParameters
            if (preserved.positionMs != null) seekTo(preserved.positionMs)
            setPlaybackSpeed(preserved.speed)
            volume = preserved.volume
        }
        else -> error("Unsupported audio recovery action")
    }

    prepare()
    playWhenReady = preserved.playWhenReady
}

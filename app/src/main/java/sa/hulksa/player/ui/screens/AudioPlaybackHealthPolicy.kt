@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.model.PlaybackRequest

internal const val SILENT_AUDIO_STARTUP_GRACE_MS = 4_000L
internal const val SILENT_AUDIO_CONFIRMATION_MS = 1_500L
private const val AUDIO_HEALTH_DISCOVERY_POLL_MS = 100L
private const val AUDIO_TRANSITION_GUARD_MS = 1_000L
private const val POSITION_PROGRESS_MIN_DELTA_MS = 100L
private const val AUDIO_RECOVERY_LOG_TAG = "HulkAudioRecovery"

internal enum class AudioPlaybackHealthDecision {
    HEALTHY,
    WAITING,
    SUSPECTED_SILENT_AUDIO,
    RECOVER_AUDIO,
    NO_ACTION,
}

internal data class AudioPlaybackHealthSnapshot(
    val playbackReady: Boolean,
    val isPlaying: Boolean,
    val playbackProgressing: Boolean,
    val audioTrackPresent: Boolean,
    val audioTrackSelected: Boolean,
    val audioFormatKnown: Boolean,
    val audioPositionAdvanced: Boolean,
    val volume: Float,
    val muted: Boolean,
    val buffering: Boolean,
    val seekInProgress: Boolean,
    val trackTransitionInProgress: Boolean,
    val appForeground: Boolean,
)

internal class AudioPlaybackHealthPolicy(
    private val startupGraceMs: Long = SILENT_AUDIO_STARTUP_GRACE_MS,
    private val confirmationMs: Long = SILENT_AUDIO_CONFIRMATION_MS,
) {
    init {
        require(startupGraceMs >= 0L)
        require(confirmationMs >= 0L)
    }

    private var generationKey: String? = null
    private var observationStartedAtMs: Long? = null
    private var invalidated = false

    fun beginGeneration(key: String) {
        generationKey = key
        observationStartedAtMs = null
        invalidated = false
    }

    fun resetObservation(key: String) {
        if (invalidated || generationKey != key) return
        observationStartedAtMs = null
    }

    fun invalidate() {
        invalidated = true
        observationStartedAtMs = null
    }

    fun evaluate(
        key: String,
        nowMs: Long,
        snapshot: AudioPlaybackHealthSnapshot,
    ): AudioPlaybackHealthDecision {
        if (invalidated || key != generationKey) return AudioPlaybackHealthDecision.NO_ACTION

        if (snapshot.audioPositionAdvanced) {
            observationStartedAtMs = null
            return AudioPlaybackHealthDecision.HEALTHY
        }

        val eligible =
            snapshot.playbackReady &&
                snapshot.isPlaying &&
                snapshot.playbackProgressing &&
                snapshot.audioTrackPresent &&
                snapshot.audioTrackSelected &&
                snapshot.audioFormatKnown &&
                snapshot.volume > 0f &&
                !snapshot.muted &&
                !snapshot.buffering &&
                !snapshot.seekInProgress &&
                !snapshot.trackTransitionInProgress &&
                snapshot.appForeground

        if (!eligible) {
            observationStartedAtMs = null
            return AudioPlaybackHealthDecision.NO_ACTION
        }

        val startedAt = observationStartedAtMs ?: nowMs.also { observationStartedAtMs = it }
        val elapsedMs = (nowMs - startedAt).coerceAtLeast(0L)
        return when {
            elapsedMs < startupGraceMs -> AudioPlaybackHealthDecision.WAITING
            elapsedMs < startupGraceMs + confirmationMs -> AudioPlaybackHealthDecision.SUSPECTED_SILENT_AUDIO
            else -> AudioPlaybackHealthDecision.RECOVER_AUDIO
        }
    }
}

internal data class SafeAudioDiagnostics(
    val streamKind: String,
    val streamId: Int,
    val isLive: Boolean,
    val candidateIndex: Int,
    val audioMime: String?,
    val codecs: String?,
    val channelCount: Int,
    val sampleRate: Int,
    val decoderName: String?,
    val audioRendererInitialized: Boolean,
    val audioTrackInitialized: Boolean,
    val audioPositionAdvanced: Boolean,
    val audioSessionId: Int,
    val selectedAudioTrack: Boolean,
    val renderedOutputBuffers: Int,
    val skippedInputBuffers: Int,
    val audioTrackEncoding: Int?,
    val audioTrackChannelConfig: Int?,
    val audioTrackOffload: Boolean?,
    val audioTrackTunneling: Boolean?,
    val outputMode: AudioOutputCompatibilityMode,
)

/**
 * Activity-owned discovery coordinator. It does not own playback or source selection. Recovery
 * execution is delegated back to PlayerScreen through AudioRecoveryExecutorRegistry so a stronger
 * attempt can safely replace the ExoPlayer instance while retaining Compose ownership.
 */
internal class AudioPlaybackHealthCoordinator(
    private val activity: ComponentActivity,
    private val stateProvider: () -> HulkUiState,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private var coordinatorJob: Job? = null
    private var monitor: LiveAudioPlaybackHealthMonitor? = null
    private var monitorIdentity: String? = null

    fun start() {
        if (coordinatorJob != null) return
        coordinatorJob = activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    while (isActive) {
                        syncMonitor()
                        monitor?.setAppForeground(true)
                        monitor?.observe()
                        delay(AUDIO_HEALTH_DISCOVERY_POLL_MS)
                    }
                } finally {
                    monitor?.setAppForeground(false)
                }
            }
        }
    }

    fun stop() {
        coordinatorJob?.cancel()
        coordinatorJob = null
        detachMonitor()
    }

    private fun syncMonitor() {
        val state = stateProvider()
        val request = state.playback
        if (state.screen != HulkScreen.PLAYER || request == null) {
            detachMonitor()
            return
        }

        val player = findExoPlayer(activity.window.decorView) ?: return
        val currentSource = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val currentCandidateIndex = request.candidates.indexOf(currentSource)
        if (currentCandidateIndex < 0) {
            detachMonitor()
            return
        }

        val generationKey = request.audioRecoveryGenerationKey()
        val recovery = AudioRecoveryRegistry.current(generationKey) ?: return
        val identity = "$generationKey:$currentCandidateIndex:${System.identityHashCode(player)}"
        if (identity == monitorIdentity) return

        detachMonitor()
        monitor = LiveAudioPlaybackHealthMonitor(
            player = player,
            request = request,
            generationKey = generationKey,
            candidateIndex = currentCandidateIndex,
            expectedSource = currentSource,
            audioRecovery = recovery,
            clockMs = clockMs,
        ).also {
            it.start()
            it.setAppForeground(true)
        }
        monitorIdentity = identity
    }

    private fun detachMonitor() {
        monitor?.invalidate()
        monitor = null
        monitorIdentity = null
    }
}

private class LiveAudioPlaybackHealthMonitor(
    private val player: ExoPlayer,
    private val request: PlaybackRequest,
    private val generationKey: String,
    private val candidateIndex: Int,
    private val expectedSource: String,
    private val audioRecovery: AudioRecoveryStateMachine,
    private val clockMs: () -> Long,
) {
    private val healthPolicy = AudioPlaybackHealthPolicy().apply { beginGeneration(generationKey) }

    private var started = false
    private var appForeground = true
    private var armedByObservedAudioPipeline = false
    private var audioRendererInitialized = false
    private var audioTrackInitialized = false
    private var audioPositionAdvanced = false
    private var audioDecoderCounters: DecoderCounters? = null
    private var lastRenderedOutputBufferCount: Int? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var decoderName: String? = null
    private var observedAudioFormat: Format? = null
    private var audioTrackConfig: AudioSink.AudioTrackConfig? = null
    private var transitionUntilMs = 0L
    private var seekInProgressUntilMs = 0L
    private var lastObservedPositionMs: Long? = null
    private var playbackProgressing = false
    private var lastLoggedRecoveryAttempt = 0

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY) resetObservation("playback_state")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            resetObservation(if (isPlaying) "resume" else "pause")
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) armedByObservedAudioPipeline = true
            transitionUntilMs = clockMs() + AUDIO_TRANSITION_GUARD_MS
            resetObservation("tracks_changed")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            seekInProgressUntilMs = clockMs() + AUDIO_TRANSITION_GUARD_MS
            lastObservedPositionMs = newPosition.positionMs
            playbackProgressing = false
            resetObservation("position_discontinuity")
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
            armedByObservedAudioPipeline = true
            audioRendererInitialized = true
            audioDecoderCounters = decoderCounters
            lastRenderedOutputBufferCount = decoderCounters.renderedOutputBufferCount
        }

        override fun onAudioDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
            audioRendererInitialized = false
            audioDecoderCounters = null
            lastRenderedOutputBufferCount = null
            resetObservation("audio_disabled")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            armedByObservedAudioPipeline = true
            this@LiveAudioPlaybackHealthMonitor.decoderName = decoderName
            audioRendererInitialized = true
        }

        override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
            if (this@LiveAudioPlaybackHealthMonitor.decoderName == decoderName) {
                this@LiveAudioPlaybackHealthMonitor.decoderName = null
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            armedByObservedAudioPipeline = true
            observedAudioFormat = format
            transitionUntilMs = clockMs() + AUDIO_TRANSITION_GUARD_MS
            resetObservation("audio_format_changed")
        }

        override fun onAudioPositionAdvancing(
            eventTime: AnalyticsListener.EventTime,
            playoutStartSystemTimeMs: Long,
        ) {
            armedByObservedAudioPipeline = true
            markAudioOutputProgress("audio_position_advancing")
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            this@LiveAudioPlaybackHealthMonitor.audioSessionId = audioSessionId
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            armedByObservedAudioPipeline = true
            audioTrackInitialized = true
            this@LiveAudioPlaybackHealthMonitor.audioTrackConfig = audioTrackConfig
            logEvent("audio_track_initialized", AudioRecoveryAction.NONE, null)
        }

        override fun onAudioTrackReleased(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            audioTrackInitialized = false
            if (this@LiveAudioPlaybackHealthMonitor.audioTrackConfig == audioTrackConfig) {
                this@LiveAudioPlaybackHealthMonitor.audioTrackConfig = null
            }
            resetObservation("audio_track_released")
        }

        override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
            logEvent("audio_codec_error", AudioRecoveryAction.NONE, player.playerError?.errorCode)
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            logEvent("audio_sink_error", AudioRecoveryAction.NONE, player.playerError?.errorCode)
        }
    }

    fun start() {
        if (started) return
        started = true
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
    }

    fun setAppForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        resetObservation(if (foreground) "foreground" else "background")
    }

    fun observe() {
        if (!started || !request.isLive || !appForeground) return
        if (AudioRecoveryRegistry.current(generationKey) !== audioRecovery) return
        if (!player.isCurrentAudioRecoverySource(expectedSource)) return

        sampleDecoderDiagnostics()
        samplePlaybackProgress()
        logExplicitRecoveryIfNeeded()

        val tracks = player.currentTracks
        val audioTrackPresent = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
        val audioTrackSelected = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                (0 until group.length).any { trackIndex -> group.isTrackSelected(trackIndex) }
        }
        val resolvedFormat = player.audioFormat ?: observedAudioFormat

        // If the observer attached after initial renderer callbacks, current selected tracks and
        // format are sufficient to arm observation. Missing historical callbacks alone never cause
        // a recovery and no timer is started until all normal health guards are satisfied.
        if (!armedByObservedAudioPipeline) {
            if (audioTrackPresent && audioTrackSelected && resolvedFormat != null) {
                armedByObservedAudioPipeline = true
            } else {
                return
            }
        }

        val nowMs = clockMs()
        val decision = healthPolicy.evaluate(
            key = generationKey,
            nowMs = nowMs,
            snapshot = AudioPlaybackHealthSnapshot(
                playbackReady = player.playbackState == Player.STATE_READY,
                isPlaying = player.isPlaying,
                playbackProgressing = playbackProgressing,
                audioTrackPresent = audioTrackPresent,
                audioTrackSelected = audioTrackSelected,
                audioFormatKnown = resolvedFormat != null,
                audioPositionAdvanced = audioPositionAdvanced,
                volume = player.volume,
                muted = player.volume <= 0f,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                seekInProgress = nowMs < seekInProgressUntilMs,
                trackTransitionInProgress = nowMs < transitionUntilMs,
                appForeground = appForeground,
            ),
        )

        if (decision != AudioPlaybackHealthDecision.RECOVER_AUDIO) return

        val action = audioRecovery.requestRecovery(
            key = generationKey,
            classification = AudioFailureClassification.RECOVERABLE_AUDIO,
            hasAudioTrack = audioTrackPresent && audioTrackSelected && resolvedFormat != null,
            explicitAudioEvidence = false,
        )
        when (action) {
            AudioRecoveryAction.REPREPARE_CURRENT,
            AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY,
            -> {
                lastLoggedRecoveryAttempt = audioRecovery.attemptsUsed
                logEvent("silent_audio", action, null)
                val executed = AudioRecoveryExecutorRegistry.execute(
                    key = generationKey,
                    stateMachine = audioRecovery,
                    player = player,
                    action = action,
                    expectedSource = expectedSource,
                )
                if (executed) {
                    armedByObservedAudioPipeline = false
                    audioRecovery.markRecoveryCommandIssued(generationKey)
                    transitionUntilMs = clockMs() + AUDIO_TRANSITION_GUARD_MS
                    resetObservation("silent_audio_recovery")
                } else {
                    audioRecovery.markRecoveryCommandAborted(generationKey)
                    logEvent("recovery_executor_stale", AudioRecoveryAction.NONE, null)
                }
            }
            AudioRecoveryAction.EXHAUSTED -> {
                logEvent("silent_audio_exhausted", action, null)
                healthPolicy.invalidate()
            }
            AudioRecoveryAction.NONE -> Unit
        }
    }

    fun invalidate() {
        if (started) {
            player.removeAnalyticsListener(analyticsListener)
            player.removeListener(playerListener)
        }
        started = false
        healthPolicy.invalidate()
    }

    private fun samplePlaybackProgress() {
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val previousPositionMs = lastObservedPositionMs
        playbackProgressing = when {
            previousPositionMs == null -> false
            currentPositionMs + POSITION_PROGRESS_MIN_DELTA_MS < previousPositionMs -> false
            else -> currentPositionMs - previousPositionMs >= POSITION_PROGRESS_MIN_DELTA_MS
        }
        if (previousPositionMs != null && currentPositionMs + POSITION_PROGRESS_MIN_DELTA_MS < previousPositionMs) {
            resetObservation("position_reset")
        }
        lastObservedPositionMs = currentPositionMs
    }

    private fun sampleDecoderDiagnostics() {
        val counters = audioDecoderCounters ?: return
        lastRenderedOutputBufferCount = counters.renderedOutputBufferCount
    }

    private fun resetObservation(@Suppress("UNUSED_PARAMETER") reason: String) {
        healthPolicy.resetObservation(generationKey)
        audioPositionAdvanced = false
        lastRenderedOutputBufferCount = audioDecoderCounters?.renderedOutputBufferCount
    }

    private fun markAudioOutputProgress(reason: String) {
        val wasPending = audioRecovery.recoveryPending
        audioPositionAdvanced = true
        healthPolicy.resetObservation(generationKey)
        audioRecovery.markAudioHealthy(generationKey)
        if (wasPending || reason == "audio_position_advancing") {
            logEvent(if (wasPending) "recovery_healthy" else reason, AudioRecoveryAction.NONE, null)
        }
    }

    private fun logExplicitRecoveryIfNeeded() {
        if (!audioRecovery.recoveryPending || audioRecovery.attemptsUsed <= lastLoggedRecoveryAttempt) return
        val error = player.playerError ?: return
        if (classifyAudioFailure(error) != AudioFailureClassification.RECOVERABLE_AUDIO) return
        val action = if (audioRecovery.attemptsUsed == 1) {
            AudioRecoveryAction.REPREPARE_CURRENT
        } else {
            AudioRecoveryAction.RECREATE_PLAYER_COMPATIBILITY
        }
        lastLoggedRecoveryAttempt = audioRecovery.attemptsUsed
        logEvent("explicit_audio_error", action, error.errorCode)
    }

    private fun diagnostics(): SafeAudioDiagnostics {
        val format = player.audioFormat ?: observedAudioFormat
        val counters = audioDecoderCounters
        val selected = player.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                (0 until group.length).any { index -> group.isTrackSelected(index) }
        }
        val trackConfig = audioTrackConfig
        return SafeAudioDiagnostics(
            streamKind = request.streamKind,
            streamId = request.streamId,
            isLive = request.isLive,
            candidateIndex = candidateIndex,
            audioMime = format?.sampleMimeType,
            codecs = format?.codecs,
            channelCount = format?.channelCount ?: Format.NO_VALUE,
            sampleRate = format?.sampleRate ?: Format.NO_VALUE,
            decoderName = decoderName,
            audioRendererInitialized = audioRendererInitialized,
            audioTrackInitialized = audioTrackInitialized,
            audioPositionAdvanced = audioPositionAdvanced,
            audioSessionId = audioSessionId,
            selectedAudioTrack = selected,
            renderedOutputBuffers = counters?.renderedOutputBufferCount ?: 0,
            skippedInputBuffers = counters?.skippedInputBufferCount ?: 0,
            audioTrackEncoding = trackConfig?.encoding,
            audioTrackChannelConfig = trackConfig?.channelConfig,
            audioTrackOffload = trackConfig?.offload,
            audioTrackTunneling = trackConfig?.tunneling,
            outputMode = AudioRecoveryExecutorRegistry.outputMode(generationKey, audioRecovery, player),
        )
    }

    private fun logEvent(reason: String, action: AudioRecoveryAction, errorCode: Int?) {
        val d = diagnostics()
        Log.i(
            AUDIO_RECOVERY_LOG_TAG,
            buildString {
                append("kind=").append(d.streamKind)
                append(" streamId=").append(d.streamId)
                append(" mode=").append(if (d.isLive) "LIVE" else "VOD")
                append(" candidate=").append(d.candidateIndex)
                append(" mime=").append(d.audioMime ?: "unknown")
                append(" codecs=").append(d.codecs ?: "unknown")
                append(" channels=").append(d.channelCount)
                append(" sampleRate=").append(d.sampleRate)
                append(" decoder=").append(d.decoderName ?: "unknown")
                append(" rendererInit=").append(d.audioRendererInitialized)
                append(" audioTrackInit=").append(d.audioTrackInitialized)
                append(" audioAdvanced=").append(d.audioPositionAdvanced)
                append(" sessionId=").append(d.audioSessionId)
                append(" selectedAudio=").append(d.selectedAudioTrack)
                append(" renderedBuffers=").append(d.renderedOutputBuffers)
                append(" skippedInput=").append(d.skippedInputBuffers)
                append(" trackEncoding=").append(d.audioTrackEncoding ?: "unknown")
                append(" trackChannelConfig=").append(d.audioTrackChannelConfig ?: "unknown")
                append(" offload=").append(d.audioTrackOffload ?: "unknown")
                append(" tunneling=").append(d.audioTrackTunneling ?: "unknown")
                append(" outputMode=").append(d.outputMode.name)
                append(" reason=").append(reason)
                append(" attempt=").append(audioRecovery.attemptsUsed)
                append(" action=").append(action.name)
                if (errorCode != null) append(" errorCode=").append(errorCode)
            },
        )
    }
}

private fun PlaybackRequest.audioRecoveryGenerationKey(): String =
    "$streamKind:$streamId:$historyKey"

private fun findExoPlayer(root: View): ExoPlayer? {
    if (root is PlayerView) return root.player as? ExoPlayer
    if (root is ViewGroup) {
        for (index in 0 until root.childCount) {
            findExoPlayer(root.getChildAt(index))?.let { return it }
        }
    }
    return null
}

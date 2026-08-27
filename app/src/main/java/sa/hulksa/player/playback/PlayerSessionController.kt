@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import sa.hulksa.player.model.PlaybackRequest

private const val HULK_PLAYER_LOG_TAG = "HulkPlayer"
private const val AUDIO_TRACK_TRANSITION_GUARD_MS = 1_200L

private data class DeferredRecovery(
    val failureClass: RecoveryFailureClass,
    val trigger: RecoveryTrigger,
)

internal data class PlayerSessionCallbacks(
    val isNetworkAvailable: () -> Boolean,
    val isRecoveryCommandCurrent: (RecoveryCommand) -> Boolean,
    val onRecoveryCommand: (RecoveryCommand) -> Boolean,
    val onFinalError: (RecoveryFailureClass) -> Unit,
    val onOffline: (positionMs: Long) -> Unit,
)

/**
 * One owner for a single playback generation. Engine callbacks are accepted only from the bound
 * player and expected source; stale players can therefore never change source, tracks, or errors.
 */
internal class PlayerSessionController(
    private val request: PlaybackRequest,
    val generation: PlaybackGeneration = newPlaybackGeneration(request),
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    val sourcePlan: MediaSourcePlan = planMediaSources(request)
    val firstCandidateIndex: Int? = sourcePlan.firstCandidateIndex

    private val recovery = PlayerRecoveryCoordinator(
        generation = generation,
        sourcePlan = sourcePlan,
        maxSameSourceRetries = maxSameSourceRetriesFor(request.isLive),
    )
    private var healthMonitor = PlaybackHealthMonitor(isLive = request.isLive)
    private var callbacks: PlayerSessionCallbacks? = null
    private var boundPlayer: ExoPlayer? = null
    private var boundCandidateIndex = -1
    private var preparedCandidateIndex = -1
    private var expectedSource: PlannedMediaSource? = null
    private var boundOutputMode = PlayerAudioOutputMode.NORMAL
    private var invalidated = false
    private var monitorSuppressed = true
    private var networkSuspended = false
    private var appForeground = true
    private var deferredRecovery: DeferredRecovery? = null
    private var finalErrorReported = false

    private var firstFrameRendered = false
    private var audioPositionAdvanced = false
    private var audioDecoderInitialized = false
    private var audioDecoderName: String? = null
    private var audioFormat: Format? = null
    private var audioDecoderCounters: DecoderCounters? = null
    private var audioTrackConfig: AudioSink.AudioTrackConfig? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var audioTrackTransitionUntilMs = 0L
    private var lastAudioPipelineError: String? = null

    private var playerListener: Player.Listener? = null
    private var analyticsListener: AnalyticsListener? = null

    init {
        Log.i(
            HULK_PLAYER_LOG_TAG,
            "playback generation=${generation.safeLogId} type=${request.streamKind} streamId=${request.streamId} " +
                "candidates=${sourcePlan.candidates.size} rejected=${sourcePlan.rejectedCandidateIndices.size}",
        )
    }

    fun attach(
        player: ExoPlayer,
        candidateIndex: Int,
        outputMode: PlayerAudioOutputMode,
        callbacks: PlayerSessionCallbacks,
    ) {
        if (invalidated) return
        detachBoundPlayer()
        this.callbacks = callbacks
        boundPlayer = player
        boundCandidateIndex = candidateIndex
        boundOutputMode = outputMode
        expectedSource = null
        monitorSuppressed = true

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (!accepts(player) || monitorSuppressed) return
                audioTrackTransitionUntilMs = clockMs() + AUDIO_TRACK_TRANSITION_GUARD_MS
                logAudioTracks(tracks)
                val audioTracks = audioTrackCandidates(tracks)
                val correction = chooseSupportedAudioTrack(audioTracks)
                when {
                    correction != null -> issueAudioRecovery(
                        player = player,
                        trigger = RecoveryTrigger.UNSUPPORTED_SELECTED_AUDIO_TRACK,
                        alternateTrack = correction,
                    )
                    requiresCompatibilityAudioRecovery(audioTracks, boundOutputMode) -> issueAudioRecovery(
                        player = player,
                        trigger = RecoveryTrigger.UNSUPPORTED_AUDIO_CAPABILITY,
                        alternateTrack = null,
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!acceptsCurrentSource(player)) return
                if (monitorSuppressed) return
                if (!request.usesOnlyLocalMedia() && callbacks.isNetworkAvailable().not()) {
                    monitorSuppressed = true
                    networkSuspended = true
                    callbacks.onOffline(if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0L))
                    return
                }

                val classification = classify(player, error)
                logRecoveryEvidence(player, error, classification)
                if (classification == RecoveryFailureClass.AUDIO) {
                    issueAudioRecovery(
                        player = player,
                        trigger = audioErrorTrigger(error),
                        alternateTrack = chooseAlternateSupportedAudioTrack(
                            audioTrackCandidates(player.currentTracks),
                        ),
                    )
                } else {
                    issueSourceRecovery(player, sourceErrorTrigger(error))
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long,
            ) {
                if (accepts(player)) firstFrameRendered = true
            }

            override fun onAudioEnabled(
                eventTime: AnalyticsListener.EventTime,
                decoderCounters: DecoderCounters,
            ) {
                if (accepts(player)) audioDecoderCounters = decoderCounters
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                if (!accepts(player)) return
                audioDecoderInitialized = true
                audioDecoderName = decoderName
                Log.i(
                    HULK_PLAYER_LOG_TAG,
                    "audio decoder generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "name=$decoderName initMs=$initializationDurationMs mode=$boundOutputMode",
                )
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                if (!accepts(player)) return
                audioFormat = format
                logAudioFormat(format)
            }

            override fun onAudioPositionAdvancing(
                eventTime: AnalyticsListener.EventTime,
                playoutStartSystemTimeMs: Long,
            ) {
                if (!acceptsCurrentSource(player) || monitorSuppressed) return
                audioPositionAdvanced = true
                if (recovery.markAudioHealthy(generation.id)) {
                    Log.i(
                        HULK_PLAYER_LOG_TAG,
                        "recovery result=audio_healthy generation=${generation.safeLogId} " +
                            "candidate=$boundCandidateIndex decoder=${audioDecoderName.orEmpty()} " +
                            "rendered=${audioDecoderCounters?.renderedOutputBufferCount ?: 0} " +
                            "encoding=${audioTrackConfig?.encoding ?: C.ENCODING_INVALID} " +
                            "sessionId=$audioSessionId",
                    )
                    resumeDeferredRecovery(player)
                }
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception,
            ) {
                if (!accepts(player)) return
                lastAudioPipelineError = "sink:${audioSinkError.javaClass.simpleName}"
                Log.w(
                    HULK_PLAYER_LOG_TAG,
                    "audio sink evidence generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "error=${audioSinkError.javaClass.simpleName}",
                )
            }

            override fun onAudioCodecError(
                eventTime: AnalyticsListener.EventTime,
                audioCodecError: Exception,
            ) {
                if (!accepts(player)) return
                lastAudioPipelineError = "codec:${audioCodecError.javaClass.simpleName}"
                Log.w(
                    HULK_PLAYER_LOG_TAG,
                    "audio codec evidence generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "decoder=${audioDecoderName.orEmpty()} error=${audioCodecError.javaClass.simpleName}",
                )
            }

            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig,
            ) {
                if (!accepts(player)) return
                this@PlayerSessionController.audioTrackConfig = audioTrackConfig
                Log.i(
                    HULK_PLAYER_LOG_TAG,
                    "audio output generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "encoding=${audioTrackConfig.encoding} channels=${audioTrackConfig.channelConfig} " +
                        "sampleRate=${audioTrackConfig.sampleRate} offload=${audioTrackConfig.offload} " +
                        "tunneling=${audioTrackConfig.tunneling}",
                )
            }

            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                if (!accepts(player)) return
                this@PlayerSessionController.audioSessionId = audioSessionId
                Log.i(
                    HULK_PLAYER_LOG_TAG,
                    "audio session generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "sessionId=$audioSessionId",
                )
            }
        }

        playerListener = listener
        analyticsListener = analytics
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    fun prepareMediaItem(player: ExoPlayer, candidateIndex: Int): MediaItem? {
        if (!accepts(player)) return null
        val plannedSource = sourcePlan.candidate(candidateIndex) ?: return null
        val sourceChanged = preparedCandidateIndex >= 0 && preparedCandidateIndex != candidateIndex
        if (!recovery.beginCandidate(generation.id, candidateIndex, sourceChanged)) return null

        boundCandidateIndex = candidateIndex
        preparedCandidateIndex = candidateIndex
        expectedSource = plannedSource
        resetPipelineEvidenceForPrepare()
        healthMonitor = PlaybackHealthMonitor(isLive = request.isLive).also { it.onPrepare(clockMs()) }
        monitorSuppressed = false
        networkSuspended = false
        deferredRecovery = null
        finalErrorReported = false

        Log.i(
            HULK_PLAYER_LOG_TAG,
            "source prepare generation=${generation.safeLogId} candidate=$candidateIndex " +
                "type=${plannedSource.type} container=${plannedSource.containerMimeType.orEmpty()} " +
                "mode=$boundOutputMode",
        )
        return plannedSource.toMediaItem(generation)
    }

    fun observeHealth(
        player: ExoPlayer,
        appForeground: Boolean,
        muted: Boolean,
    ) {
        if (!acceptsCurrentSource(player) || monitorSuppressed || networkSuspended) return
        val tracks = player.currentTracks
        val audioCandidates = audioTrackCandidates(tracks)
        val signal = healthMonitor.evaluate(
            nowMs = clockMs(),
            snapshot = PlaybackHealthSnapshot(
                playbackState = player.playbackState.toEnginePlaybackState(),
                playWhenReady = player.playWhenReady,
                playbackSuppressed =
                    player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                isLoading = player.isLoading,
                firstFrameRendered = firstFrameRendered,
                audioTrackPresent = audioCandidates.isNotEmpty(),
                audioTrackSelected = audioCandidates.any(AudioTrackCandidate::selected),
                audioFormatKnown = player.audioFormat != null || audioFormat != null,
                audioDecoderInitialized = audioDecoderInitialized,
                audioPositionAdvanced = audioPositionAdvanced,
                volume = player.volume,
                muted = muted,
                trackTransitionInProgress = clockMs() < audioTrackTransitionUntilMs,
                appForeground = appForeground,
            ),
        )

        when (signal) {
            PlaybackHealthSignal.NONE -> Unit
            PlaybackHealthSignal.SOURCE_HEALTHY -> {
                if (recovery.markSourceHealthy(generation.id)) {
                    Log.i(
                        HULK_PLAYER_LOG_TAG,
                        "recovery result=source_healthy generation=${generation.safeLogId} " +
                            "candidate=$boundCandidateIndex",
                    )
                    resumeDeferredRecovery(player)
                }
            }
            PlaybackHealthSignal.AUDIO_HEALTHY -> {
                recovery.markAudioHealthy(generation.id)
                resumeDeferredRecovery(player)
            }
            PlaybackHealthSignal.STARTUP_TIMEOUT -> {
                logWatchdog("startup", player)
                issueSourceRecovery(player, RecoveryTrigger.STARTUP_TIMEOUT)
            }
            PlaybackHealthSignal.REBUFFER_STALL -> {
                logWatchdog("stall", player)
                issueSourceRecovery(player, RecoveryTrigger.REBUFFER_STALL)
            }
            PlaybackHealthSignal.SILENT_AUDIO -> {
                logWatchdog("silent_audio", player)
                issueAudioRecovery(
                    player = player,
                    trigger = RecoveryTrigger.SILENT_AUDIO,
                    alternateTrack = chooseAlternateSupportedAudioTrack(audioCandidates),
                )
            }
        }
    }

    fun onNetworkUnavailable() {
        networkSuspended = true
        monitorSuppressed = true
    }

    fun onNetworkRestored(player: ExoPlayer) {
        if (!accepts(player) || request.usesOnlyLocalMedia()) return
        networkSuspended = false
        issueSourceRecovery(player, RecoveryTrigger.NETWORK_RESTORED)
    }

    fun onAppForegroundChanged(player: ExoPlayer, foreground: Boolean) {
        appForeground = foreground
        if (!foreground || !acceptsCurrentSource(player)) return
        val pending = deferredRecovery ?: return
        deferredRecovery = null
        if (pending.failureClass == RecoveryFailureClass.AUDIO) {
            issueAudioRecovery(
                player = player,
                trigger = pending.trigger,
                alternateTrack = chooseAlternateSupportedAudioTrack(
                    audioTrackCandidates(player.currentTracks),
                ),
            )
        } else {
            issueSourceRecovery(player, pending.trigger)
        }
    }

    fun resetForManualRetry(candidateIndex: Int): Boolean {
        val reset = recovery.resetForManualRetry(generation.id, candidateIndex)
        if (reset) {
            monitorSuppressed = true
            networkSuspended = false
            deferredRecovery = null
            finalErrorReported = false
        }
        return reset
    }

    fun detach(player: ExoPlayer) {
        if (boundPlayer === player) detachBoundPlayer()
    }

    fun invalidate() {
        if (invalidated) return
        invalidated = true
        healthMonitor.invalidate()
        recovery.invalidate()
        detachBoundPlayer()
        callbacks = null
        Log.i(HULK_PLAYER_LOG_TAG, "playback invalidated generation=${generation.safeLogId}")
    }

    private fun issueAudioRecovery(
        player: ExoPlayer,
        trigger: RecoveryTrigger,
        alternateTrack: AudioTrackRef?,
    ) {
        if (!acceptsCurrentSource(player)) return
        if (!appForeground) {
            deferredRecovery = DeferredRecovery(RecoveryFailureClass.AUDIO, trigger)
            return
        }
        if (monitorSuppressed) return
        val command = recovery.requestAudioRecovery(generation.id, trigger, alternateTrack)
        if (command == null) {
            if (
                recovery.phase == RecoveryPhase.STABILIZING &&
                recovery.activeFailure == RecoveryFailureClass.SOURCE
            ) {
                deferredRecovery = DeferredRecovery(RecoveryFailureClass.AUDIO, trigger)
            }
            return
        }
        dispatch(command, player)
    }

    private fun issueSourceRecovery(player: ExoPlayer, trigger: RecoveryTrigger) {
        if (!accepts(player)) return
        if (!appForeground) {
            deferredRecovery = DeferredRecovery(RecoveryFailureClass.SOURCE, trigger)
            return
        }
        if (monitorSuppressed && trigger != RecoveryTrigger.NETWORK_RESTORED) return
        val command = recovery.requestSourceRecovery(generation.id, trigger)
        if (command == null) {
            if (
                recovery.phase == RecoveryPhase.STABILIZING &&
                recovery.activeFailure == RecoveryFailureClass.AUDIO
            ) {
                deferredRecovery = DeferredRecovery(RecoveryFailureClass.SOURCE, trigger)
            }
            return
        }
        dispatch(command, player)
    }

    private fun resumeDeferredRecovery(player: ExoPlayer) {
        if (recovery.activeFailure != null || monitorSuppressed || !appForeground) return
        val pending = deferredRecovery ?: return
        deferredRecovery = null
        if (pending.failureClass == RecoveryFailureClass.AUDIO) {
            issueAudioRecovery(
                player = player,
                trigger = pending.trigger,
                alternateTrack = chooseAlternateSupportedAudioTrack(
                    audioTrackCandidates(player.currentTracks),
                ),
            )
        } else {
            issueSourceRecovery(player, pending.trigger)
        }
    }

    private fun dispatch(command: RecoveryCommand, player: ExoPlayer) {
        logRecoveryCommand(command, player)
        val currentCallbacks = callbacks
        if (
            !accepts(player) ||
            currentCallbacks == null ||
            !currentCallbacks.isRecoveryCommandCurrent(command)
        ) {
            val budgetRestored = if (command.type == RecoveryCommandType.SHOW_FINAL_ERROR) {
                false
            } else {
                recovery.markCommandStale(generation.id, command.id)
            }
            Log.i(
                HULK_PLAYER_LOG_TAG,
                "recovery result=stale commandGeneration=${command.generationId} " +
                    "sessionGeneration=${generation.id} candidate=${command.candidateIndex} " +
                    "attempt=${command.id} strategy=${command.type} budgetRestored=$budgetRestored",
            )
            return
        }

        if (command.type == RecoveryCommandType.SHOW_FINAL_ERROR) {
            monitorSuppressed = true
            deferredRecovery = null
            Log.e(
                HULK_PLAYER_LOG_TAG,
                "recovery result=exhausted generation=${generation.safeLogId} " +
                    "attempt=${command.id} candidate=${command.candidateIndex} classification=${command.failureClass}",
            )
            if (!finalErrorReported) {
                finalErrorReported = true
                currentCallbacks.onFinalError(command.failureClass)
            }
            return
        }

        monitorSuppressed = true
        val accepted = currentCallbacks.onRecoveryCommand(command)
        if (accepted) {
            if (!recovery.markCommandApplied(generation.id, command.id)) return
            Log.i(
                HULK_PLAYER_LOG_TAG,
                "recovery result=accepted generation=${generation.safeLogId} " +
                    "attempt=${command.id} candidate=${command.candidateIndex} strategy=${command.type}",
            )
            if (
                command.type == RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK &&
                !shouldReprepareAfterAudioTrackOverride(command.trigger)
            ) {
                audioPositionAdvanced = false
                audioTrackTransitionUntilMs = clockMs() + AUDIO_TRACK_TRANSITION_GUARD_MS
                healthMonitor = PlaybackHealthMonitor(isLive = request.isLive).also { it.onPrepare(clockMs()) }
                monitorSuppressed = false
            }
        } else {
            if (!recovery.markCommandRejected(generation.id, command.id)) return
            Log.e(
                HULK_PLAYER_LOG_TAG,
                "recovery result=rejected generation=${generation.safeLogId} " +
                    "attempt=${command.id} candidate=${command.candidateIndex} strategy=${command.type}",
            )
            if (!accepts(player) || finalErrorReported) return
            monitorSuppressed = false
            when (command.type) {
                RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK,
                RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO,
                -> issueAudioRecovery(player, command.trigger, alternateTrack = null)
                RecoveryCommandType.RETRY_CURRENT_SOURCE -> issueSourceRecovery(player, command.trigger)
                RecoveryCommandType.MOVE_TO_NEXT_SOURCE -> dispatchTerminalAfterRejectedMove(command)
                RecoveryCommandType.SHOW_FINAL_ERROR -> Unit
            }
        }
    }

    private fun dispatchTerminalAfterRejectedMove(command: RecoveryCommand) {
        monitorSuppressed = true
        if (finalErrorReported) return
        finalErrorReported = true
        Log.e(
            HULK_PLAYER_LOG_TAG,
            "recovery result=unapplied generation=${generation.safeLogId} " +
                "attempt=${command.id} candidate=${command.candidateIndex} strategy=${command.type}",
        )
        callbacks?.onFinalError(command.failureClass)
    }

    private fun accepts(player: ExoPlayer): Boolean =
        !invalidated && boundPlayer === player

    private fun acceptsCurrentSource(player: ExoPlayer): Boolean {
        if (!accepts(player)) return false
        val expectedUri = expectedSource?.uri ?: return false
        return player.currentMediaItem?.localConfiguration?.uri?.toString() == expectedUri
    }

    private fun detachBoundPlayer() {
        val player = boundPlayer
        playerListener?.let { listener -> player?.removeListener(listener) }
        analyticsListener?.let { listener -> player?.removeAnalyticsListener(listener) }
        playerListener = null
        analyticsListener = null
        boundPlayer = null
    }

    private fun resetPipelineEvidenceForPrepare() {
        firstFrameRendered = false
        audioPositionAdvanced = false
        audioDecoderInitialized = false
        audioDecoderName = null
        audioFormat = null
        audioDecoderCounters = null
        audioTrackConfig = null
        audioSessionId = C.AUDIO_SESSION_ID_UNSET
        audioTrackTransitionUntilMs = 0L
        lastAudioPipelineError = null
    }

    private fun logAudioTracks(tracks: Tracks) {
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                Log.i(
                    HULK_PLAYER_LOG_TAG,
                    "audio track generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                        "group=$groupIndex track=$trackIndex selected=${group.isTrackSelected(trackIndex)} " +
                        "support=${group.getTrackSupport(trackIndex).toSafeSupportLabel()} " +
                        "selectionFlags=${format.selectionFlags} roleFlags=${format.roleFlags} " +
                        "mime=${format.sampleMimeType.orEmpty()} container=${format.containerMimeType.orEmpty()} " +
                        "codecs=${format.codecs.orEmpty()} channels=${format.channelCount} " +
                        "sampleRate=${format.sampleRate} bitrate=${format.bitrate} language=${format.language.orEmpty()}",
                )
            }
        }
    }

    private fun logAudioFormat(format: Format) {
        val decoderInventory = runCatching {
            format.sampleMimeType
                ?.let { mime -> MediaCodecSelector.DEFAULT.getDecoderInfos(mime, false, false) }
                .orEmpty()
                .joinToString(separator = ",") { info ->
                    "${info.name}:${if (info.softwareOnly) "sw" else if (info.hardwareAccelerated) "hw" else "platform"}"
                }
        }.getOrDefault("")
        Log.i(
            HULK_PLAYER_LOG_TAG,
            "audio format generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                "mime=${format.sampleMimeType.orEmpty()} container=${format.containerMimeType.orEmpty()} " +
                "codecs=${format.codecs.orEmpty()} channels=${format.channelCount} sampleRate=${format.sampleRate} " +
                "bitrate=${format.bitrate} decoders=$decoderInventory",
        )
    }

    private fun logRecoveryEvidence(
        player: ExoPlayer,
        error: PlaybackException,
        classification: RecoveryFailureClass,
    ) {
        val counters = audioDecoderCounters
        val output = audioTrackConfig
        Log.w(
            HULK_PLAYER_LOG_TAG,
            "recovery evidence generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                "classification=$classification code=${error.errorCode} decoder=${audioDecoderName.orEmpty()} " +
                "rendered=${counters?.renderedOutputBufferCount ?: 0} skipped=${counters?.skippedInputBufferCount ?: 0} " +
                "encoding=${output?.encoding ?: C.ENCODING_INVALID} sessionId=$audioSessionId " +
                "audioEvidence=${lastAudioPipelineError.orEmpty()} " +
                "state=${player.playbackState} loading=${player.isLoading}",
        )
    }

    private fun logRecoveryCommand(command: RecoveryCommand, player: ExoPlayer) {
        Log.w(
            HULK_PLAYER_LOG_TAG,
            "recovery trigger=${command.trigger} classification=${command.failureClass} " +
                "generation=${generation.safeLogId} candidate=${command.candidateIndex} " +
                "attempt=${command.id} strategy=${command.type} delayMs=${command.delayMs} " +
                "state=${player.playbackState}",
        )
    }

    private fun logWatchdog(kind: String, player: ExoPlayer) {
        Log.w(
            HULK_PLAYER_LOG_TAG,
            "watchdog=$kind generation=${generation.safeLogId} candidate=$boundCandidateIndex " +
                "state=${player.playbackState} loading=${player.isLoading} position=${player.currentPosition.coerceAtLeast(0L)} " +
                "firstFrame=$firstFrameRendered audioAdvanced=$audioPositionAdvanced",
        )
    }

    private fun classify(player: ExoPlayer, error: PlaybackException): RecoveryFailureClass {
        val exoError = error as? ExoPlaybackException
        val rendererIsAudio = exoError
            ?.rendererIndex
            ?.takeIf { rendererIndex -> rendererIndex in 0 until player.rendererCount }
            ?.let(player::getRendererType) == C.TRACK_TYPE_AUDIO
        return classifyPlaybackFailure(
            audioOutputFailure = error.errorCode in AUDIO_OUTPUT_ERROR_CODES,
            decoderFailure = error.errorCode in DECODER_ERROR_CODES,
            rendererMimeType = exoError?.rendererFormat?.sampleMimeType,
            rendererIsAudio = rendererIsAudio,
        )
    }

    private fun audioErrorTrigger(error: PlaybackException): RecoveryTrigger =
        if (error.errorCode in AUDIO_OUTPUT_ERROR_CODES) {
            RecoveryTrigger.AUDIO_SINK_ERROR
        } else {
            RecoveryTrigger.AUDIO_DECODER_ERROR
        }

    private fun sourceErrorTrigger(error: PlaybackException): RecoveryTrigger {
        val httpStatus = generateSequence(error.cause) { cause -> cause.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
        if (httpStatus != null) {
            Log.w(
                HULK_PLAYER_LOG_TAG,
                "source http evidence generation=${generation.safeLogId} " +
                    "candidate=$boundCandidateIndex status=$httpStatus",
            )
        }
        return if (
            error.errorCode in TRANSIENT_SOURCE_ERROR_CODES ||
            (httpStatus != null && isTransientHttpStatus(httpStatus))
        ) {
            RecoveryTrigger.TRANSIENT_SOURCE_ERROR
        } else {
            RecoveryTrigger.NON_RETRIABLE_SOURCE_ERROR
        }
    }
}

private fun PlaybackRequest.usesOnlyLocalMedia(): Boolean =
    candidates.isNotEmpty() && candidates.all { candidate ->
        val source = candidate.trim()
        source.startsWith("file:", ignoreCase = true) ||
            source.startsWith("content:", ignoreCase = true)
    }

private fun audioTrackCandidates(tracks: Tracks): List<AudioTrackCandidate> = buildList {
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val selectionFlags = group.getTrackFormat(trackIndex).selectionFlags
            add(
                AudioTrackCandidate(
                    ref = AudioTrackRef(groupIndex, trackIndex),
                    support = group.getTrackSupport(trackIndex).toAudioFormatSupport(),
                    selected = group.isTrackSelected(trackIndex),
                    defaultSelection = selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
                    autoSelection = selectionFlags and C.SELECTION_FLAG_AUTOSELECT != 0,
                ),
            )
        }
    }
}

private fun Int.toAudioFormatSupport(): AudioFormatSupport = when (this) {
    C.FORMAT_HANDLED -> AudioFormatSupport.SUPPORTED
    C.FORMAT_EXCEEDS_CAPABILITIES -> AudioFormatSupport.EXCEEDS_CAPABILITIES
    else -> AudioFormatSupport.UNSUPPORTED
}

private fun Int.toSafeSupportLabel(): String = when (toAudioFormatSupport()) {
    AudioFormatSupport.SUPPORTED -> "SUPPORTED"
    AudioFormatSupport.EXCEEDS_CAPABILITIES -> "EXCEEDS_CAPABILITIES"
    AudioFormatSupport.UNSUPPORTED -> "UNSUPPORTED"
}

private fun Int.toEnginePlaybackState(): EnginePlaybackState = when (this) {
    Player.STATE_BUFFERING -> EnginePlaybackState.BUFFERING
    Player.STATE_READY -> EnginePlaybackState.READY
    Player.STATE_ENDED -> EnginePlaybackState.ENDED
    else -> EnginePlaybackState.IDLE
}

private val AUDIO_OUTPUT_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
)

private val DECODER_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
)

private val TRANSIENT_SOURCE_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_UNSPECIFIED,
    PlaybackException.ERROR_CODE_REMOTE_ERROR,
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
)

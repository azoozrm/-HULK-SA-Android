package sa.hulksa.player.playback

import sa.hulksa.player.model.PlaybackRequest
import java.util.concurrent.atomic.AtomicLong

internal const val LIVE_STARTUP_TIMEOUT_MS = 8_000L
internal const val LIVE_STALL_TIMEOUT_MS = 8_000L
internal const val SILENT_AUDIO_TIMEOUT_MS = 6_000L
internal const val SOURCE_RETRY_BACKOFF_MS = 300L
internal const val SOURCE_SWITCH_BACKOFF_MS = 200L
internal const val MAX_SAME_SOURCE_RETRIES = 1

internal fun maxSameSourceRetriesFor(isLive: Boolean): Int =
    if (isLive) MAX_SAME_SOURCE_RETRIES else 0

internal data class PlaybackGeneration(
    val id: Long,
    val streamKind: String,
    val streamId: Int,
    val historyIdentity: Int,
) {
    val safeLogId: String
        get() = "g$id:$streamKind:$streamId:$historyIdentity"
}

private object PlaybackGenerationSequence {
    private val nextId = AtomicLong(0L)

    fun next(request: PlaybackRequest): PlaybackGeneration = PlaybackGeneration(
        id = nextId.incrementAndGet(),
        streamKind = request.streamKind,
        streamId = request.streamId,
        historyIdentity = request.historyKey.hashCode(),
    )
}

internal fun newPlaybackGeneration(request: PlaybackRequest): PlaybackGeneration =
    PlaybackGenerationSequence.next(request)

internal enum class RecoveryPhase {
    NORMAL,
    AUDIO_RECOVERY,
    SOURCE_RECOVERY,
    STABILIZING,
    HEALTHY,
    EXHAUSTED,
    INVALIDATED,
}

internal enum class RecoveryFailureClass {
    AUDIO,
    SOURCE,
}

internal enum class RecoveryTrigger {
    UNSUPPORTED_SELECTED_AUDIO_TRACK,
    UNSUPPORTED_AUDIO_CAPABILITY,
    AUDIO_DECODER_ERROR,
    AUDIO_SINK_ERROR,
    SILENT_AUDIO,
    TRANSIENT_SOURCE_ERROR,
    NON_RETRIABLE_SOURCE_ERROR,
    STARTUP_TIMEOUT,
    REBUFFER_STALL,
    NETWORK_RESTORED,
}

internal enum class RecoveryCommandType {
    SELECT_ALTERNATE_AUDIO_TRACK,
    RECREATE_WITH_SOFTWARE_AUDIO,
    RETRY_CURRENT_SOURCE,
    MOVE_TO_NEXT_SOURCE,
    SHOW_FINAL_ERROR,
}

internal enum class PlayerAudioOutputMode {
    NORMAL,
    PLATFORM_SOFTWARE_PCM,
}

internal fun outputModeFor(command: RecoveryCommandType): PlayerAudioOutputMode =
    if (command == RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO) {
        PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM
    } else {
        PlayerAudioOutputMode.NORMAL
    }

internal fun shouldReprepareAfterAudioTrackOverride(trigger: RecoveryTrigger): Boolean =
    trigger == RecoveryTrigger.AUDIO_DECODER_ERROR ||
        trigger == RecoveryTrigger.AUDIO_SINK_ERROR

internal data class AudioTrackRef(
    val groupIndex: Int,
    val trackIndex: Int,
)

internal data class RecoveryCommand(
    val id: Long,
    val generationId: Long,
    val type: RecoveryCommandType,
    val trigger: RecoveryTrigger,
    val failureClass: RecoveryFailureClass,
    val candidateIndex: Int,
    val audioTrack: AudioTrackRef? = null,
    val delayMs: Long = 0L,
)

internal data class RecoveryDispatchOwner(
    val generationId: Long,
    val playerInstanceId: Int,
)

internal fun ownsRecoveryCommand(
    command: RecoveryCommand,
    attachedOwner: RecoveryDispatchOwner,
    currentOwner: RecoveryDispatchOwner,
): Boolean =
    command.generationId == attachedOwner.generationId && attachedOwner == currentOwner

internal enum class AudioFormatSupport {
    SUPPORTED,
    EXCEEDS_CAPABILITIES,
    UNSUPPORTED,
}

internal data class AudioTrackCandidate(
    val ref: AudioTrackRef,
    val support: AudioFormatSupport,
    val selected: Boolean,
    val defaultSelection: Boolean,
    val autoSelection: Boolean,
)

internal data class PlayerReplacementPolicy(
    val positionMs: Long?,
    val sourceChanged: Boolean,
)

internal fun playerReplacementPolicy(
    isLive: Boolean,
    currentPositionMs: Long,
    currentCandidateIndex: Int,
    targetCandidateIndex: Int,
): PlayerReplacementPolicy = PlayerReplacementPolicy(
    positionMs = if (isLive) null else currentPositionMs.coerceAtLeast(0L),
    sourceChanged = currentCandidateIndex != targetCandidateIndex,
)

/** Returns a correction only when the current automatic selection is not actually supported. */
internal fun chooseSupportedAudioTrack(tracks: List<AudioTrackCandidate>): AudioTrackRef? {
    if (tracks.isEmpty()) return null
    if (tracks.any { it.selected && it.support == AudioFormatSupport.SUPPORTED }) return null

    return tracks
        .asSequence()
        .filter { it.support == AudioFormatSupport.SUPPORTED }
        .sortedWith(
            compareByDescending<AudioTrackCandidate> { it.defaultSelection }
                .thenByDescending { it.autoSelection }
                .thenBy { it.ref.groupIndex }
                .thenBy { it.ref.trackIndex },
        )
        .firstOrNull()
        ?.ref
}

/** On a proven audio failure, a different fully supported track is safer than restarting the one that failed. */
internal fun chooseAlternateSupportedAudioTrack(tracks: List<AudioTrackCandidate>): AudioTrackRef? =
    tracks
        .asSequence()
        .filter { it.support == AudioFormatSupport.SUPPORTED && !it.selected }
        .sortedWith(
            compareByDescending<AudioTrackCandidate> { it.defaultSelection }
                .thenByDescending { it.autoSelection }
                .thenBy { it.ref.groupIndex }
                .thenBy { it.ref.trackIndex },
        )
        .firstOrNull()
        ?.ref

internal fun requiresCompatibilityAudioRecovery(
    tracks: List<AudioTrackCandidate>,
    outputMode: PlayerAudioOutputMode,
): Boolean {
    if (tracks.isEmpty() || tracks.any { it.support == AudioFormatSupport.SUPPORTED }) return false
    return outputMode == PlayerAudioOutputMode.NORMAL ||
        tracks.all { it.support == AudioFormatSupport.UNSUPPORTED } ||
        tracks.none(AudioTrackCandidate::selected)
}

internal class PlayerRecoveryCoordinator(
    val generation: PlaybackGeneration,
    private val sourcePlan: MediaSourcePlan,
    private val maxSameSourceRetries: Int = MAX_SAME_SOURCE_RETRIES,
) {
    init {
        require(maxSameSourceRetries >= 0)
    }

    private var currentCandidateIndex: Int = sourcePlan.firstCandidateIndex ?: -1
    private val sourceRetries = mutableMapOf<Int, Int>()
    private var alternateTrackAttempted = false
    private var softwareAudioAttempted = false
    private var commandInFlight = false
    private var inFlightCommand: RecoveryCommand? = null
    private var nextCommandId = 0L
    private var invalidated = false
    private var activeFailureClass: RecoveryFailureClass? = null

    var phase: RecoveryPhase = RecoveryPhase.NORMAL
        private set

    val candidateIndex: Int
        get() = currentCandidateIndex

    val recoveryInFlight: Boolean
        get() = commandInFlight

    val activeFailure: RecoveryFailureClass?
        get() = activeFailureClass

    fun beginCandidate(
        generationId: Long,
        candidateIndex: Int,
        sourceChanged: Boolean,
    ): Boolean {
        if (!owns(generationId) || sourcePlan.candidate(candidateIndex) == null) return false
        currentCandidateIndex = candidateIndex
        commandInFlight = false
        inFlightCommand = null
        phase = RecoveryPhase.STABILIZING
        if (sourceChanged) {
            alternateTrackAttempted = false
            softwareAudioAttempted = false
            if (activeFailureClass != RecoveryFailureClass.SOURCE) {
                activeFailureClass = null
            }
        }
        return true
    }

    fun requestAudioRecovery(
        generationId: Long,
        trigger: RecoveryTrigger,
        alternateTrack: AudioTrackRef?,
    ): RecoveryCommand? {
        if (!canStart(generationId)) return null
        if (activeFailureClass == RecoveryFailureClass.SOURCE && phase == RecoveryPhase.STABILIZING) {
            return null
        }

        val command = when {
            alternateTrack != null && !alternateTrackAttempted -> {
                alternateTrackAttempted = true
                command(
                    type = RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK,
                    trigger = trigger,
                    failureClass = RecoveryFailureClass.AUDIO,
                    audioTrack = alternateTrack,
                )
            }
            !softwareAudioAttempted -> {
                softwareAudioAttempted = true
                command(
                    type = RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO,
                    trigger = trigger,
                    failureClass = RecoveryFailureClass.AUDIO,
                )
            }
            else -> terminalCommand(trigger, RecoveryFailureClass.AUDIO)
        }
        activeFailureClass = RecoveryFailureClass.AUDIO
        phase = if (command.type == RecoveryCommandType.SHOW_FINAL_ERROR) {
            RecoveryPhase.EXHAUSTED
        } else {
            RecoveryPhase.AUDIO_RECOVERY
        }
        commandInFlight = command.type != RecoveryCommandType.SHOW_FINAL_ERROR
        inFlightCommand = command.takeIf { commandInFlight }
        return command
    }

    fun requestSourceRecovery(
        generationId: Long,
        trigger: RecoveryTrigger,
    ): RecoveryCommand? {
        if (!canStart(generationId)) return null
        if (
            activeFailureClass == RecoveryFailureClass.AUDIO &&
            phase == RecoveryPhase.STABILIZING &&
            trigger !in SOURCE_RECOVERY_TAKEOVER_TRIGGERS
        ) {
            return null
        }

        val retriesUsed = sourceRetries[currentCandidateIndex] ?: 0
        val sameSourceRetryIsUseful = trigger in SAME_SOURCE_RETRY_TRIGGERS
        val command = when {
            sameSourceRetryIsUseful && retriesUsed < maxSameSourceRetries -> {
                sourceRetries[currentCandidateIndex] = retriesUsed + 1
                command(
                    type = RecoveryCommandType.RETRY_CURRENT_SOURCE,
                    trigger = trigger,
                    failureClass = RecoveryFailureClass.SOURCE,
                    delayMs = SOURCE_RETRY_BACKOFF_MS,
                )
            }
            sourcePlan.nextCandidateIndex(currentCandidateIndex) != null -> {
                command(
                    type = RecoveryCommandType.MOVE_TO_NEXT_SOURCE,
                    trigger = trigger,
                    failureClass = RecoveryFailureClass.SOURCE,
                    candidateIndex = checkNotNull(sourcePlan.nextCandidateIndex(currentCandidateIndex)),
                    delayMs = SOURCE_SWITCH_BACKOFF_MS,
                )
            }
            else -> terminalCommand(trigger, RecoveryFailureClass.SOURCE)
        }
        activeFailureClass = RecoveryFailureClass.SOURCE
        phase = if (command.type == RecoveryCommandType.SHOW_FINAL_ERROR) {
            RecoveryPhase.EXHAUSTED
        } else {
            RecoveryPhase.SOURCE_RECOVERY
        }
        commandInFlight = command.type != RecoveryCommandType.SHOW_FINAL_ERROR
        inFlightCommand = command.takeIf { commandInFlight }
        return command
    }

    fun markCommandApplied(generationId: Long, commandId: Long): Boolean {
        if (!ownsInFlight(generationId, commandId)) return false
        commandInFlight = false
        inFlightCommand = null
        phase = RecoveryPhase.STABILIZING
        return true
    }

    fun markCommandRejected(generationId: Long, commandId: Long): Boolean {
        if (!ownsInFlight(generationId, commandId)) return false
        commandInFlight = false
        inFlightCommand = null
        phase = RecoveryPhase.STABILIZING
        return true
    }

    fun markCommandStale(generationId: Long, commandId: Long): Boolean {
        if (!ownsInFlight(generationId, commandId)) return false
        val command = checkNotNull(inFlightCommand)
        when (command.type) {
            RecoveryCommandType.SELECT_ALTERNATE_AUDIO_TRACK -> alternateTrackAttempted = false
            RecoveryCommandType.RECREATE_WITH_SOFTWARE_AUDIO -> softwareAudioAttempted = false
            RecoveryCommandType.RETRY_CURRENT_SOURCE -> {
                val retriesUsed = sourceRetries[command.candidateIndex] ?: 0
                when {
                    retriesUsed <= 1 -> sourceRetries.remove(command.candidateIndex)
                    else -> sourceRetries[command.candidateIndex] = retriesUsed - 1
                }
            }
            RecoveryCommandType.MOVE_TO_NEXT_SOURCE -> Unit
            RecoveryCommandType.SHOW_FINAL_ERROR -> return false
        }
        commandInFlight = false
        inFlightCommand = null
        activeFailureClass = null
        phase = RecoveryPhase.STABILIZING
        return true
    }

    fun markSourceHealthy(generationId: Long): Boolean {
        if (!owns(generationId)) return false
        if (activeFailureClass != RecoveryFailureClass.AUDIO) {
            activeFailureClass = null
            phase = RecoveryPhase.HEALTHY
        }
        return true
    }

    fun markAudioHealthy(generationId: Long): Boolean {
        if (!owns(generationId)) return false
        if (activeFailureClass != RecoveryFailureClass.SOURCE) {
            activeFailureClass = null
            commandInFlight = false
            inFlightCommand = null
            phase = RecoveryPhase.HEALTHY
        }
        return true
    }

    fun resetForManualRetry(generationId: Long, candidateIndex: Int): Boolean {
        if (!owns(generationId) || sourcePlan.candidate(candidateIndex) == null) return false
        currentCandidateIndex = candidateIndex
        sourceRetries.clear()
        alternateTrackAttempted = false
        softwareAudioAttempted = false
        commandInFlight = false
        inFlightCommand = null
        activeFailureClass = null
        phase = RecoveryPhase.NORMAL
        return true
    }

    fun invalidate() {
        invalidated = true
        commandInFlight = false
        inFlightCommand = null
        activeFailureClass = null
        phase = RecoveryPhase.INVALIDATED
    }

    private fun canStart(generationId: Long): Boolean =
        owns(generationId) && !commandInFlight && phase != RecoveryPhase.EXHAUSTED

    private fun owns(generationId: Long): Boolean =
        !invalidated && generation.id == generationId

    private fun ownsInFlight(generationId: Long, commandId: Long): Boolean =
        owns(generationId) && commandInFlight && inFlightCommand?.id == commandId

    private fun command(
        type: RecoveryCommandType,
        trigger: RecoveryTrigger,
        failureClass: RecoveryFailureClass,
        candidateIndex: Int = currentCandidateIndex,
        audioTrack: AudioTrackRef? = null,
        delayMs: Long = 0L,
    ): RecoveryCommand {
        nextCommandId += 1L
        return RecoveryCommand(
            id = nextCommandId,
            generationId = generation.id,
            type = type,
            trigger = trigger,
            failureClass = failureClass,
            candidateIndex = candidateIndex,
            audioTrack = audioTrack,
            delayMs = delayMs,
        )
    }

    private fun terminalCommand(
        trigger: RecoveryTrigger,
        failureClass: RecoveryFailureClass,
    ): RecoveryCommand = command(
        type = RecoveryCommandType.SHOW_FINAL_ERROR,
        trigger = trigger,
        failureClass = failureClass,
    )
}

private val SAME_SOURCE_RETRY_TRIGGERS = setOf(
    RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
    RecoveryTrigger.STARTUP_TIMEOUT,
    RecoveryTrigger.REBUFFER_STALL,
    RecoveryTrigger.NETWORK_RESTORED,
)

/** Direct source evidence may serially replace an applied audio recovery, never an in-flight one. */
private val SOURCE_RECOVERY_TAKEOVER_TRIGGERS = setOf(
    RecoveryTrigger.TRANSIENT_SOURCE_ERROR,
    RecoveryTrigger.NON_RETRIABLE_SOURCE_ERROR,
    RecoveryTrigger.STARTUP_TIMEOUT,
    RecoveryTrigger.REBUFFER_STALL,
)

internal enum class EnginePlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

internal data class PlaybackHealthSnapshot(
    val playbackState: EnginePlaybackState,
    val playWhenReady: Boolean,
    val playbackSuppressed: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val isLoading: Boolean,
    val firstFrameRendered: Boolean,
    val audioTrackPresent: Boolean,
    val audioTrackSelected: Boolean,
    val audioFormatKnown: Boolean,
    val audioDecoderInitialized: Boolean,
    val audioPositionAdvanced: Boolean,
    val volume: Float,
    val muted: Boolean,
    val trackTransitionInProgress: Boolean,
    val appForeground: Boolean,
)

internal enum class PlaybackHealthSignal {
    NONE,
    SOURCE_HEALTHY,
    AUDIO_HEALTHY,
    STARTUP_TIMEOUT,
    REBUFFER_STALL,
    SILENT_AUDIO,
}

internal class PlaybackHealthMonitor(
    private val isLive: Boolean,
    private val startupTimeoutMs: Long = LIVE_STARTUP_TIMEOUT_MS,
    private val stallTimeoutMs: Long = LIVE_STALL_TIMEOUT_MS,
    private val silentAudioTimeoutMs: Long = SILENT_AUDIO_TIMEOUT_MS,
) {
    init {
        require(startupTimeoutMs >= 0L)
        require(stallTimeoutMs >= 0L)
        require(silentAudioTimeoutMs >= 0L)
    }

    private var preparedAtMs = 0L
    private var lastPositionMs: Long? = null
    private var lastProgressAtMs = 0L
    private var bufferingSinceMs: Long? = null
    private var readyStallSinceMs: Long? = null
    private var silentSinceMs: Long? = null
    private var sourceHealthy = false
    private var sourceHealthyReported = false
    private var audioHealthyReported = false
    private var invalidated = false

    fun onPrepare(nowMs: Long) {
        preparedAtMs = nowMs
        lastPositionMs = null
        lastProgressAtMs = nowMs
        bufferingSinceMs = null
        readyStallSinceMs = null
        silentSinceMs = null
        sourceHealthy = false
        sourceHealthyReported = false
        audioHealthyReported = false
    }

    fun invalidate() {
        invalidated = true
        bufferingSinceMs = null
        readyStallSinceMs = null
        silentSinceMs = null
    }

    fun evaluate(nowMs: Long, snapshot: PlaybackHealthSnapshot): PlaybackHealthSignal {
        if (invalidated) return PlaybackHealthSignal.NONE
        if (!snapshot.appForeground || !snapshot.playWhenReady || snapshot.playbackSuppressed) {
            preparedAtMs = nowMs
            lastProgressAtMs = nowMs
            bufferingSinceMs = null
            readyStallSinceMs = null
            silentSinceMs = null
            lastPositionMs = snapshot.positionMs
            return PlaybackHealthSignal.NONE
        }

        val previousPosition = lastPositionMs
        val progressed = previousPosition != null && snapshot.positionMs >= previousPosition + 100L
        if (progressed) lastProgressAtMs = nowMs
        lastPositionMs = snapshot.positionMs

        if (isLive && !sourceHealthy) {
            val usefulOutput = snapshot.firstFrameRendered || snapshot.audioPositionAdvanced
            if (usefulOutput && progressed && snapshot.playbackState == EnginePlaybackState.READY) {
                sourceHealthy = true
                bufferingSinceMs = null
                if (!sourceHealthyReported) {
                    sourceHealthyReported = true
                    return PlaybackHealthSignal.SOURCE_HEALTHY
                }
            } else if (nowMs - preparedAtMs >= startupTimeoutMs) {
                return PlaybackHealthSignal.STARTUP_TIMEOUT
            }
        }

        if (isLive && sourceHealthy && snapshot.playbackState == EnginePlaybackState.BUFFERING) {
            readyStallSinceMs = null
            if (progressed) bufferingSinceMs = nowMs
            val bufferingSince = bufferingSinceMs ?: nowMs.also { bufferingSinceMs = it }
            if (nowMs - bufferingSince >= stallTimeoutMs && lastProgressAtMs <= bufferingSince) {
                return PlaybackHealthSignal.REBUFFER_STALL
            }
        } else {
            bufferingSinceMs = null
        }

        if (
            isLive &&
            sourceHealthy &&
            snapshot.playbackState == EnginePlaybackState.READY &&
            snapshot.isPlaying
        ) {
            if (progressed) readyStallSinceMs = null
            val stalledSince = readyStallSinceMs ?: nowMs.also { readyStallSinceMs = it }
            if (!progressed && nowMs - stalledSince >= stallTimeoutMs) {
                return PlaybackHealthSignal.REBUFFER_STALL
            }
        } else {
            readyStallSinceMs = null
        }

        if (snapshot.audioPositionAdvanced) {
            silentSinceMs = null
            if (!audioHealthyReported) {
                audioHealthyReported = true
                return PlaybackHealthSignal.AUDIO_HEALTHY
            }
            return PlaybackHealthSignal.NONE
        }

        val silentAudioEligible =
            snapshot.playbackState == EnginePlaybackState.READY &&
                snapshot.isPlaying &&
                progressed &&
                snapshot.firstFrameRendered &&
                snapshot.audioTrackPresent &&
                snapshot.audioTrackSelected &&
                snapshot.audioFormatKnown &&
                snapshot.audioDecoderInitialized &&
                snapshot.volume > 0f &&
                !snapshot.muted &&
                !snapshot.trackTransitionInProgress

        if (!silentAudioEligible) {
            silentSinceMs = null
            return PlaybackHealthSignal.NONE
        }

        val silentSince = silentSinceMs ?: nowMs.also { silentSinceMs = it }
        return if (nowMs - silentSince >= silentAudioTimeoutMs) {
            PlaybackHealthSignal.SILENT_AUDIO
        } else {
            PlaybackHealthSignal.NONE
        }
    }
}

internal fun classifyPlaybackFailure(
    audioOutputFailure: Boolean,
    decoderFailure: Boolean,
    rendererMimeType: String?,
    rendererIsAudio: Boolean = false,
): RecoveryFailureClass {
    val audioRenderer =
        rendererIsAudio ||
            rendererMimeType?.startsWith("audio/", ignoreCase = true) == true
    return if (audioOutputFailure || (decoderFailure && audioRenderer)) {
        RecoveryFailureClass.AUDIO
    } else {
        RecoveryFailureClass.SOURCE
    }
}

internal fun isTransientHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 425 ||
        statusCode == 429 ||
        statusCode in 500..599

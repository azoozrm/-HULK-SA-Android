@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.resolveArabicCommentarySource
import sa.hulksa.player.playback.HulkPlayerFactory
import sa.hulksa.player.playback.LIVE_STALL_TIMEOUT_MS
import sa.hulksa.player.playback.MediaSourcePlan
import sa.hulksa.player.playback.PlannedMediaSource
import sa.hulksa.player.playback.PlayerAudioOutputMode
import sa.hulksa.player.playback.SOURCE_SWITCH_BACKOFF_MS
import sa.hulksa.player.playback.planMediaSources

private const val TAG = "HulkPlayer"
private const val PREPARATION_TIMEOUT_MS = 20_000L
private const val PAUSE_CONFIRM_TIMEOUT_MS = 900L

internal enum class ArabicCommentaryPhase { INACTIVE, PREPARING, ACTIVE, INTERRUPTED, ERROR }

internal data class ArabicCommentaryState(
    val phase: ArabicCommentaryPhase = ArabicCommentaryPhase.INACTIVE,
    val feed: ArabicCommentaryFeed? = null,
    val source: ArabicCommentaryCatalogSource? = null,
    val alignment: ArabicCommentaryAlignment = ArabicCommentaryAlignment(),
    val stepMs: Long = ARABIC_COMMENTARY_FINE_STEP_MS,
    val commentaryAudible: Boolean = false,
    val outputMuted: Boolean = false,
    val adjusting: Boolean = false,
    val buffering: Boolean = false,
    val recovering: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sessionAvailable: Boolean = true,
) {
    val canRetry: Boolean get() = sessionAvailable && feed != null && source != null

    fun afterPictureReplacement(): ArabicCommentaryState? {
        if (!sessionAvailable || feed == null) return null
        return ArabicCommentaryState(
            phase = ArabicCommentaryPhase.INTERRUPTED,
            feed = feed,
            source = source,
            stepMs = stepMs,
            alignment = ArabicCommentaryAlignment(needsReadjustment = true),
            message = "اعيد تحميل القناة؛ اعد اختيار التعليق او المحاولة بعد استقرار التشغيل",
        )
    }
}

/** Owns one audio-only player at a time and the manual holds for the two active streams. */
internal class ArabicCommentaryOwner(
    private val factory: HulkPlayerFactory,
    private val videoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
    private val expectedOwner: AuthenticatedSessionOwner?,
    initialState: ArabicCommentaryState,
    private val onVideoHeld: () -> Unit,
    private val onAudioRouteChanged: (Boolean) -> Unit,
) {
    var state by mutableStateOf(initialState)
        private set

    private class Hold(val target: ExoPlayer, val other: ExoPlayer, val source: ArabicCommentaryDelayedSource) {
        var pauseApplied = false
        val canContinue: Boolean
            get() = target.playbackState == Player.STATE_READY && other.isPlaying &&
                (!pauseApplied || !target.playWhenReady)
    }

    private var fallback: ArabicCommentaryFallback? = null
    private var sourcePlan: MediaSourcePlan? = null
    private var candidateIndex = -1
    private var preparationDeadlineMs = 0L
    private var commentaryPlayer: ExoPlayer? = null
    private var commentaryListener: Player.Listener? = null
    private var watchdogJob: Job? = null
    private var switchJob: Job? = null
    private var hold: Hold? = null
    private var adjustmentJob: Job? = null
    private var released = false
    private val workingSourceKeys = mutableMapOf<ArabicCommentaryFeedKey, String>()

    val videoPlaybackIntended: Boolean
        get() = videoPlayer.playWhenReady || videoPlayer.isPlaying ||
            hold?.let { it.target === videoPlayer && it.pauseApplied } == true

    private val videoListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                )
            ) return
            if (commentaryPlayer == null || !ensureAuthenticatedSession()) return
            followVideo()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (oldPosition.positionMs == newPosition.positionMs ||
                !state.alignment.hasPictureAdjustment && state.phase != ArabicCommentaryPhase.ACTIVE
            ) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
                reason == Player.DISCONTINUITY_REASON_REMOVE
            ) {
                cancelAdjustment("تغير موضع الصورة اثناء الضبط")
                state = state.copy(alignment = state.alignment.pictureRepositioned())
            }
        }
    }

    init {
        videoPlayer.addListener(videoListener)
    }

    fun selectChannel(feed: ArabicCommentaryFeed) {
        val preferred = feed.sources.firstOrNull { it.key == workingSourceKeys[feed.key] }
            ?: feed.sources.firstOrNull()
            ?: return
        start(feed, preferred)
    }

    fun selectSource(source: ArabicCommentaryCatalogSource) {
        val feed = state.feed ?: return
        if (feed.sources.none { it.key == source.key }) return
        if (source.key == state.source?.key && state.phase == ArabicCommentaryPhase.ACTIVE) return
        start(feed, source)
    }

    fun retry() {
        val feed = state.feed ?: return
        val source = state.source ?: return
        start(feed, source)
    }

    private fun start(
        feed: ArabicCommentaryFeed,
        source: ArabicCommentaryCatalogSource,
        message: String? = null,
    ) {
        if (!ensureAuthenticatedSession()) return
        stopRecovery(restorePicture = true)
        val recovery = ArabicCommentaryFallback(feed, source)
        fallback = recovery
        state = state.copy(
            phase = ArabicCommentaryPhase.PREPARING,
            feed = feed,
            source = null,
            alignment = state.alignment.sourceReplaced(
                recheck = state.feed != null || state.alignment.hasPictureAdjustment,
            ),
            recovering = false,
            error = null,
            message = message,
        )
        prepareNextSource(recovery)
    }

    private fun prepareNextSource(recovery: ArabicCommentaryFallback) {
        if (fallback !== recovery || !ensureAuthenticatedSession()) return
        val source = recovery.nextSource()
        if (source == null) {
            stopRecovery(restorePicture = true)
            state = state.copy(
                phase = ArabicCommentaryPhase.ERROR,
                recovering = false,
                message = null,
                error = "تعذر تشغيل التعليق. عاد الصوت الاصلي",
            )
            return
        }
        state = state.copy(source = source)
        val request = expectedOwner?.let { resolveArabicCommentarySource(it, source.item) }
        val plan = request?.let(::planMediaSources)
        sourcePlan = plan
        val first = plan?.candidates?.firstOrNull()
        if (first == null) {
            scheduleFallback(recovery, nextCandidate = null)
            return
        }
        preparationDeadlineMs = SystemClock.elapsedRealtime() + PREPARATION_TIMEOUT_MS
        prepare(recovery, first)
    }

    private fun prepare(recovery: ArabicCommentaryFallback, candidate: PlannedMediaSource) {
        if (fallback !== recovery || !ensureAuthenticatedSession()) return
        candidateIndex = candidate.candidateIndex
        val token = recovery.beginAttempt()
        try {
            val player = factory.create(
                outputMode = PlayerAudioOutputMode.NORMAL,
                handleAudioFocus = false,
            )
            commentaryPlayer = player
            player.volume = 0f
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setPreferredAudioLanguage("ar")
                .build()
            val listener = createCommentaryListener(token)
            commentaryListener = listener
            player.addListener(listener)
            player.setMediaItem(
                MediaItem.Builder().setUri(candidate.uri).setMimeType(candidate.containerMimeType).build(),
            )
            player.playWhenReady = videoPlayer.isPlaying
            player.prepare()
        } catch (failure: Exception) {
            recover(token, failure.javaClass.simpleName)
            return
        }
        if (recovery.accepts(token) && state.phase == ArabicCommentaryPhase.PREPARING) {
            val timeLeft = (preparationDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            watchdogJob = scope.launch {
                delay(timeLeft)
                recover(token, "prepare_timeout", sourceTimedOut = true)
            }
        }
        Log.i(TAG, "commentary preparing channelId=${state.source?.item?.id} candidate=$candidateIndex")
    }

    private fun createCommentaryListener(token: Any): Player.Listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                )
            ) return
            if (!accepts(token)) return
            val commentary = commentaryPlayer ?: return
            if (player.playbackState == Player.STATE_ENDED) {
                recover(token, "ended")
                return
            }
            if (state.phase == ArabicCommentaryPhase.PREPARING && player.playbackState == Player.STATE_READY &&
                (commentary.audioFormat != null || player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
            ) {
                cancelWatchdog()
                state = state.copy(phase = ArabicCommentaryPhase.ACTIVE, recovering = false)
                Log.i(TAG, "commentary ready channelId=${state.source?.item?.id} candidate=$candidateIndex")
            }
            if (state.phase == ArabicCommentaryPhase.ACTIVE) {
                followVideo()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            recover(token, error.errorCodeName)
        }
    }

    private fun accepts(token: Any): Boolean =
        fallback?.accepts(token) == true && ensureAuthenticatedSession()

    private fun recover(token: Any, reason: String, sourceTimedOut: Boolean = false) {
        if (!accepts(token)) return
        val recovery = fallback ?: return
        val wasActive = state.phase == ArabicCommentaryPhase.ACTIVE
        if (wasActive) preparationDeadlineMs = SystemClock.elapsedRealtime() + PREPARATION_TIMEOUT_MS
        val plan = sourcePlan
        val next = if (plan != null && !sourceTimedOut && SystemClock.elapsedRealtime() < preparationDeadlineMs) {
            plan.nextCandidateIndex(candidateIndex)?.let(plan::candidate)
        } else {
            null
        }
        Log.i(TAG, "commentary attempt failed channelId=${state.source?.item?.id} candidate=$candidateIndex reason=$reason")
        scheduleFallback(recovery, next)
    }

    private fun scheduleFallback(recovery: ArabicCommentaryFallback, nextCandidate: PlannedMediaSource?) {
        val wasActive = state.phase == ArabicCommentaryPhase.ACTIVE
        releaseAttempt(restorePicture = true)
        state = state.copy(
            phase = ArabicCommentaryPhase.PREPARING,
            alignment = state.alignment.sourceReplaced(recheck = wasActive || nextCandidate == null),
            recovering = true,
        )
        switchJob?.cancel()
        switchJob = scope.launch {
            delay(SOURCE_SWITCH_BACKOFF_MS)
            if (fallback !== recovery || !ensureAuthenticatedSession()) return@launch
            if (nextCandidate != null && SystemClock.elapsedRealtime() < preparationDeadlineMs) {
                prepare(recovery, nextCandidate)
            } else {
                prepareNextSource(recovery)
            }
        }
    }

    private fun observeCommentaryBuffering(token: Any, commentary: ExoPlayer) {
        when (arabicCommentaryBufferingTransition(
            wasBuffering = state.buffering,
            videoPlaying = videoPlayer.isPlaying,
            commentaryBuffering = commentary.playbackState == Player.STATE_BUFFERING,
            adjusting = hold != null,
        )) {
            ArabicCommentaryBufferingTransition.START -> {
                state = state.copy(buffering = true)
                watchdogJob = scope.launch {
                    delay(LIVE_STALL_TIMEOUT_MS)
                    if (state.buffering) recover(token, "buffering_timeout")
                }
            }
            ArabicCommentaryBufferingTransition.CANCEL -> {
                state = state.copy(buffering = false)
                cancelWatchdog()
            }
            ArabicCommentaryBufferingTransition.KEEP -> Unit
        }
    }

    private fun followVideo() {
        val token = fallback?.currentAttempt ?: return
        val commentary = commentaryPlayer ?: return
        if (hold?.canContinue != true) {
            if (hold != null) cancelAdjustment("انقطع التشغيل اثناء الضبط ولم تكتمل الخطوة")
            if (commentaryPlayer !== commentary) return
            commentary.playWhenReady = videoPlayer.isPlaying
            if (commentaryPlayer !== commentary) return
            updateAudibleSource(commentary)
        }
        // A picture-only resume can leave the secondary in the same buffering state.
        if (state.phase == ArabicCommentaryPhase.ACTIVE && commentaryPlayer === commentary && accepts(token)) {
            observeCommentaryBuffering(token, commentary)
        }
    }

    /** Mute the departing route before restoring or raising the other volume. */
    private fun updateAudibleSource(commentary: ExoPlayer) {
        val desired = arabicCommentaryDesiredAudibleSource(
            commentaryActive = state.phase == ArabicCommentaryPhase.ACTIVE,
            adjusting = hold != null,
            videoPlaying = videoPlayer.isPlaying,
            commentaryPlaying = commentary.isPlaying,
        ) ?: return
        val useCommentary = desired == ArabicCommentaryAudibleSource.COMMENTARY
        if (useCommentary == state.commentaryAudible) return
        if (useCommentary) {
            val feed = state.feed
            val source = state.source
            if (feed != null && source != null) workingSourceKeys[feed.key] = source.key
            onAudioRouteChanged(true)
            commentary.volume = if (state.outputMuted) 0f else 1f
        } else {
            commentary.volume = 0f
            onAudioRouteChanged(false)
        }
        state = state.copy(commentaryAudible = useCommentary)
        Log.i(TAG, "commentary audio route=$desired channelId=${state.source?.item?.id}")
    }

    fun setOutputMuted(muted: Boolean) {
        if (released || state.outputMuted == muted) return
        commentaryPlayer?.volume = if (state.commentaryAudible && !muted) 1f else 0f
        state = state.copy(outputMuted = muted)
    }

    fun setStep(stepMs: Long) {
        if (stepMs == ARABIC_COMMENTARY_FINE_STEP_MS || stepMs == ARABIC_COMMENTARY_COARSE_STEP_MS) {
            state = state.copy(stepMs = stepMs)
        }
    }

    fun adjust(symptom: ArabicCommentarySymptom) {
        if (!ensureAuthenticatedSession() || state.adjusting || state.phase != ArabicCommentaryPhase.ACTIVE) return
        val commentary = commentaryPlayer ?: return
        if (!videoPlayer.isPlaying || !commentary.isPlaying) {
            state = state.copy(message = "شغل الصورة والتعليق قبل ضبط التزامن")
            return
        }
        val result = applyArabicCommentaryStep(state.alignment.delays, symptom, state.stepMs)
        if (result.appliedMs == 0L) {
            state = state.copy(message = "بلغت حد التأخير البالغ 10 ثوان لهذا المصدر")
            return
        }
        val target = if (result.delayedSource == ArabicCommentaryDelayedSource.VIDEO) videoPlayer else commentary
        val other = if (target === videoPlayer) commentary else videoPlayer
        val bufferedAheadMs = (target.bufferedPosition - target.currentPosition).coerceAtLeast(0L)
        val liveTarget = target.isCurrentMediaItemLive
        if (arabicCommentaryStepBlocked(
                ready = target.playbackState == Player.STATE_READY,
                isLive = liveTarget,
                isLoading = target.isLoading,
                bufferedAheadMs = bufferedAheadMs,
                appliedMs = result.appliedMs,
            )
        ) {
            state = state.copy(message = "انتظر تجهيز البث قبل تطبيق هذه الخطوة")
            Log.i(
                TAG,
                "commentary adjust blocked source=${result.delayedSource} step=${result.appliedMs} " +
                    "live=$liveTarget bufferedAheadMs=$bufferedAheadMs targetState=${target.playbackState} " +
                    "targetLoading=${target.isLoading}",
            )
            return
        }

        val operation = Hold(target, other, result.delayedSource)
        hold = operation
        state = state.copy(adjusting = true, message = null)
        adjustmentJob = scope.launch {
            try {
                Log.i(
                    TAG,
                    "commentary adjust start error=${state.error} symptom=$symptom stepMs=${state.stepMs} " +
                        "source=${result.delayedSource} appliedMs=${result.appliedMs} bufferedAheadMs=$bufferedAheadMs " +
                        "videoState=${videoPlayer.playbackState} videoPlaying=${videoPlayer.isPlaying} " +
                        "commentaryState=${commentary.playbackState} commentaryPlaying=${commentary.isPlaying}",
                )
                if (!pauseAndConfirm(operation)) {
                    Log.i(TAG, "commentary adjust pause failed source=${result.delayedSource}")
                    if (hold === operation) cancelAdjustment("لم تكتمل خطوة ضبط التزامن")
                    return@launch
                }
                delay(result.appliedMs)
                if (!ensureAuthenticatedSession()) return@launch
                val shouldResume = arabicCommentaryShouldResumeAfterAdjustment(
                    interrupted = !operation.canContinue,
                    generationCurrent = hold === operation,
                    phaseActive = state.phase == ArabicCommentaryPhase.ACTIVE,
                    released = released,
                )
                if (!shouldResume) {
                    Log.i(
                        TAG,
                        "commentary adjust interrupted source=${result.delayedSource} " +
                            "videoState=${videoPlayer.playbackState} videoPlaying=${videoPlayer.isPlaying} " +
                            "commentaryState=${commentary.playbackState} commentaryPlaying=${commentary.isPlaying}",
                    )
                    if (hold === operation) cancelAdjustment("لم تكتمل خطوة ضبط التزامن")
                    return@launch
                }
                hold = null
                restoreHeldPlayer(operation)
                if (target.playerError != null || !target.playWhenReady) {
                    state = state.copy(
                        adjusting = false,
                        message = "تعذر استئناف التشغيل بعد الخطوة. لم يتم تثبيت التأخير",
                    )
                    Log.w(
                        TAG,
                        "commentary adjust resume failed source=${result.delayedSource} " +
                            "targetError=${target.playerError?.errorCodeName} playWhenReady=${target.playWhenReady}",
                    )
                    return@launch
                }
                state = state.copy(
                    adjusting = false,
                    alignment = state.alignment.copy(delays = result.delays, needsReadjustment = false),
                    message = if (result.limitReached) "بلغت حد التأخير البالغ 10 ثوان لهذا المصدر" else null,
                )
                followVideo()
                Log.i(
                    TAG,
                    "commentary adjusted source=${result.delayedSource} appliedMs=${result.appliedMs} " +
                        "commentaryMs=${result.delays.commentaryMs} videoMs=${result.delays.videoMs} " +
                        "videoState=${videoPlayer.playbackState} videoPlaying=${videoPlayer.isPlaying} " +
                        "videoPlayWhenReady=${videoPlayer.playWhenReady}",
                )
            } finally {
                if (hold === operation) detachAdjustment()
            }
        }
    }

    private suspend fun pauseAndConfirm(operation: Hold): Boolean {
        val target = operation.target
        if (!target.isPlaying || !operation.canContinue) return false
        val paused = CompletableDeferred<Unit>()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) paused.complete(Unit)
            }
        }
        target.addListener(listener)
        try {
            operation.pauseApplied = true
            target.playWhenReady = false
            if (target === videoPlayer && hold === operation) onVideoHeld()
            return !target.isPlaying || withTimeoutOrNull(PAUSE_CONFIRM_TIMEOUT_MS) { paused.await() } != null
        } finally {
            target.removeListener(listener)
        }
    }

    private fun detachAdjustment(): Hold? {
        val operation = hold
        hold = null
        adjustmentJob?.cancel()
        adjustmentJob = null
        if (operation != null) {
            state = state.copy(
                adjusting = false,
                alignment = if (operation.pauseApplied) {
                    state.alignment.holdInterrupted(operation.source)
                } else {
                    state.alignment
                },
            )
        }
        return operation
    }

    private fun cancelAdjustment(message: String) {
        val operation = detachAdjustment() ?: return
        state = state.copy(message = message)
        restoreHeldPlayer(operation)
    }

    private fun restoreHeldPlayer(operation: Hold) {
        val target = operation.target
        if (operation.pauseApplied && target.playerError == null && !target.playWhenReady &&
            (target === videoPlayer || target === commentaryPlayer && videoPlayer.isPlaying)
        ) {
            target.playWhenReady = true
        }
    }

    fun reset() {
        if (!ensureAuthenticatedSession() || state.adjusting || state.phase != ArabicCommentaryPhase.ACTIVE) return
        val feed = state.feed ?: return
        val source = state.source ?: return
        val alignment = state.alignment
        if (!alignment.delays.hasDelay && !alignment.hasPictureAdjustment && !alignment.needsReadjustment) {
            state = state.copy(message = "لا توجد خطوات تأخير لاعادة ضبطها")
            return
        }
        val canResetPicture = alignment.canResetPicture(
            isLive = videoPlayer.isCurrentMediaItemLive,
            seekable = videoPlayer.isCurrentMediaItemSeekable,
            defaultSeekAvailable = videoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION),
        )
        if (canResetPicture) {
            // The primary discontinuity listener clears picture steps when the position moves.
            videoPlayer.seekToDefaultPosition()
        }
        val message = if (alignment.hasPictureAdjustment && !canResetPicture) {
            "البث الحالي لا يتيح اعادة ضبط موضع الصورة"
        } else null
        start(feed, source, message)
    }

    fun returnToOriginalAudio() {
        if (released) return
        stopRecovery(restorePicture = true)
        state = state.copy(
            phase = ArabicCommentaryPhase.INACTIVE,
            feed = null,
            source = null,
            alignment = state.alignment.sourceReplaced(recheck = false),
            recovering = false,
            message = null,
            error = null,
        )
        Log.i(TAG, "commentary stopped reason=original_audio")
    }

    fun onAppForegroundChanged(foreground: Boolean) {
        if (foreground || released) return
        // Restore only an owned primary hold before the screen applies its normal background stop.
        interrupt("توقف التعليق عند مغادرة التطبيق", restorePicture = true)
    }

    fun onPlaybackInterrupted(message: String, pictureReloaded: Boolean = false) {
        val hadPictureAdjustment = state.alignment.hasPictureAdjustment
        interrupt(message, restorePicture = false)
        if (pictureReloaded && state.alignment.hasPictureAdjustment) {
            state = state.copy(alignment = state.alignment.pictureRepositioned())
        }
        Log.i(
            TAG,
            "commentary interrupted pictureReloaded=$pictureReloaded " +
                "hadPictureAdjustment=$hadPictureAdjustment " +
                "commentaryMs=${state.alignment.delays.commentaryMs} videoMs=${state.alignment.delays.videoMs}",
        )
    }

    fun ensureAuthenticatedSession(): Boolean {
        if (released) return false
        if (expectedOwner != null && AuthenticatedSessionRegistry.isCurrent(expectedOwner)) return true
        if (state.sessionAvailable) {
            stopRecovery(restorePicture = true)
            workingSourceKeys.clear()
            state = state.copy(
                phase = ArabicCommentaryPhase.INTERRUPTED,
                feed = null,
                source = null,
                alignment = state.alignment.sourceReplaced(recheck = false),
                recovering = false,
                message = null,
                error = null,
                sessionAvailable = false,
            )
            Log.i(TAG, "commentary interrupted reason=session_changed")
        }
        return false
    }

    private fun interrupt(message: String, restorePicture: Boolean) {
        if (released || state.phase == ArabicCommentaryPhase.INACTIVE ||
            state.phase == ArabicCommentaryPhase.INTERRUPTED
        ) return
        stopRecovery(restorePicture)
        state = state.copy(
            phase = ArabicCommentaryPhase.INTERRUPTED,
            alignment = state.alignment.sourceReplaced(recheck = true),
            recovering = false,
            message = message,
            error = null,
        )
    }

    fun release() {
        if (released) return
        released = true
        videoPlayer.removeListener(videoListener)
        stopRecovery(restorePicture = false)
    }

    private fun stopRecovery(restorePicture: Boolean) {
        switchJob?.cancel()
        switchJob = null
        releaseAttempt(restorePicture)
        fallback = null
        sourcePlan = null
    }

    private fun releaseAttempt(restorePicture: Boolean) {
        fallback?.endAttempt()
        cancelWatchdog()
        val operation = detachAdjustment()
        val player = commentaryPlayer
        commentaryPlayer = null
        val listener = commentaryListener
        commentaryListener = null
        if (player != null) {
            if (listener != null) player.removeListener(listener)
            player.volume = 0f
            player.release()
        }
        if (state.commentaryAudible) {
            runCatching { onAudioRouteChanged(false) }
        }
        state = state.copy(commentaryAudible = false, buffering = false)
        if (restorePicture && operation?.target === videoPlayer) restoreHeldPlayer(operation)
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}

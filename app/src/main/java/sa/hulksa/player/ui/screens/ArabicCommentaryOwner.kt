@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.data.AuthenticatedSessionOwner
import sa.hulksa.player.data.AuthenticatedSessionRegistry
import sa.hulksa.player.data.ArabicCommentarySource
import sa.hulksa.player.data.resolveArabicCommentarySource
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.playback.HulkPlayerFactory
import sa.hulksa.player.playback.MediaSourcePlan
import sa.hulksa.player.playback.PlayerAudioOutputMode
import sa.hulksa.player.playback.planMediaSources

private const val TAG = "HulkPlayer"
private const val PREPARATION_TIMEOUT_MS = 20_000L
private const val PAUSE_CONFIRM_TIMEOUT_MS = 900L

internal enum class ArabicCommentaryPhase {
    INACTIVE,
    PREPARING,
    ACTIVE,
    INTERRUPTED,
    ERROR,
}

internal data class ArabicCommentaryState(
    val phase: ArabicCommentaryPhase = ArabicCommentaryPhase.INACTIVE,
    val selectedChannel: ContentItem? = null,
    val delays: ArabicCommentaryDelays = ArabicCommentaryDelays(),
    val stepMs: Long = ARABIC_COMMENTARY_FINE_STEP_MS,
    val commentaryAudible: Boolean = false,
    val adjusting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val canRetry: Boolean = false,
    val connectionNotice: String? = null,
    val sessionAvailable: Boolean = true,
) {
    val busy: Boolean get() = adjusting || phase == ArabicCommentaryPhase.PREPARING
}

/**
 * Owns the optional second Live playback used as the Arabic commentary source.
 *
 * The foreign video player stays the visible/lifecycle player. This owner only creates an
 * audio-focused ExoPlayer for the commentary channel, disables its video/text tracks, and routes
 * one audible source at a time. A rejected/stale callback never mutates a newer activation because
 * every listener and coroutine is bound to a generation token.
 *
 * Synchronization mechanism:
 * - A source is delayed by holding that source's `playWhenReady` for exactly the requested step
 *   while the other source continues. The held player keeps filling its bounded forward buffer.
 * - Reset/return uses the stream's live/default position where it is seekable. On a stream that
 *   cannot seek live, the UI reports the limitation instead of claiming the delay was removed.
 * - The total applied delay is manually tracked per source; the feature never assumes a shared
 *   content clock and never uses a displayed-only offset.
 */
internal class ArabicCommentaryOwner(
    private val factory: HulkPlayerFactory,
    private val videoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
    private val expectedOwner: AuthenticatedSessionOwner?,
    private val onAudioRouteChanged: (Boolean) -> Unit,
) {
    var state by mutableStateOf(ArabicCommentaryState())
        private set

    private var commentaryPlayer: ExoPlayer? = null
    private var source: ArabicCommentarySource? = null
    private var sourcePlan: MediaSourcePlan? = null
    private var generation = 0
    private var candidateIndex = 0
    private var muted = false
    private var holdTarget: ExoPlayer? = null
    private var adjustmentJob: Job? = null
    private var watchdogJob: Job? = null
    private var commentaryListener: Player.Listener? = null
    private var released = false

    private val videoListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (state.adjusting) return
            val commentary = commentaryPlayer ?: return
            if (state.phase != ArabicCommentaryPhase.PREPARING &&
                state.phase != ArabicCommentaryPhase.ACTIVE
            ) {
                return
            }
            commentary.playWhenReady = isPlaying && !state.adjusting
            updateAudibleSource()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (state.adjusting) return
            val commentary = commentaryPlayer ?: return
            if (state.phase != ArabicCommentaryPhase.PREPARING &&
                state.phase != ArabicCommentaryPhase.ACTIVE
            ) {
                return
            }
            when (playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_IDLE,
                -> commentary.playWhenReady = false
                Player.STATE_READY -> if (videoPlayer.playWhenReady) {
                    commentary.playWhenReady = true
                }
            }
            updateAudibleSource()
        }
    }

    init {
        videoPlayer.addListener(videoListener)
    }

    fun start(channel: ContentItem, notice: String? = null) {
        if (released || channel.type != ContentType.LIVE) return
        val keptStep = state.stepMs
        val previousNotice = state.connectionNotice
        val expected = expectedOwner
        val resolved = resolveArabicCommentarySource(channel)
        stopInternal(removeVideoDelay = true)
        if (expected == null) {
            state = ArabicCommentaryState(
                phase = ArabicCommentaryPhase.ERROR,
                selectedChannel = channel,
                stepMs = keptStep,
                connectionNotice = previousNotice,
                error = "تعذر التحقق من جلسة الحساب.",
                canRetry = false,
                sessionAvailable = false,
            )
            Log.i(TAG, "commentary rejected reason=owner_unavailable channelId=${channel.id}")
            return
        }
        if (resolved == null) {
            val sessionAvailable = AuthenticatedSessionRegistry.currentOwner() != null
            state = ArabicCommentaryState(
                phase = ArabicCommentaryPhase.ERROR,
                selectedChannel = channel,
                stepMs = keptStep,
                connectionNotice = previousNotice,
                error = "تعذر تجهيز قناة التعليق من الحساب الحالي.",
                canRetry = sessionAvailable,
                sessionAvailable = sessionAvailable,
            )
            Log.i(TAG, "commentary rejected reason=source_unavailable channelId=${channel.id}")
            return
        }
        if (resolved.owner !== expected) {
            state = ArabicCommentaryState(
                phase = ArabicCommentaryPhase.ERROR,
                selectedChannel = channel,
                stepMs = keptStep,
                connectionNotice = previousNotice,
                error = "تغيرت جلسة الحساب؛ لم يتم تشغيل التعليق.",
                canRetry = false,
                sessionAvailable = false,
            )
            Log.i(TAG, "commentary rejected reason=owner_mismatch channelId=${channel.id}")
            return
        }

        source = resolved
        val plan = planMediaSources(resolved.request)
        sourcePlan = plan
        val gen = generation
        val firstCandidateIndex = plan.firstCandidateIndex
        if (firstCandidateIndex == null) {
            fail(gen, "لا يوجد مصدر تشغيل صالح لقناة التعليق.", null)
            return
        }
        candidateIndex = firstCandidateIndex
        val connectionNoticeText = arabicCommentaryConnectionNotice(
            activeConnections = resolved.activeConnections,
            maxConnections = resolved.maxConnections,
        )
        state = ArabicCommentaryState(
            phase = ArabicCommentaryPhase.PREPARING,
            selectedChannel = channel,
            stepMs = keptStep,
            connectionNotice = connectionNoticeText,
            message = notice,
        )
        Log.i(
            TAG,
            "commentary preparing channelId=${channel.id} active=${resolved.activeConnections} " +
                "max=${resolved.maxConnections} candidates=${resolved.request.candidates.size}",
        )

        val player = runCatching {
            factory.create(
                outputMode = PlayerAudioOutputMode.NORMAL,
                handleAudioFocus = false,
            )
        }.getOrElse { error ->
            fail(gen, "تعذر انشاء مشغل التعليق.", error)
            return
        }
        commentaryPlayer = player
        player.volume = 0f
        // The commentary component is audio-only. A multiplexed transport may still download
        // video bytes; this only prevents a video decoder from being selected/opened.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        val listener = createCommentaryListener(gen)
        commentaryListener = listener
        player.addListener(listener)
        prepareCandidate(gen, firstCandidateIndex)

        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(PREPARATION_TIMEOUT_MS)
            if (gen == generation && state.phase == ArabicCommentaryPhase.PREPARING) {
                fail(gen, "تعذر تشغيل قناة التعليق خلال 20 ثانية.", null)
            }
        }
    }

    fun retry() {
        val channel = state.selectedChannel ?: return
        start(channel)
    }

    fun setStep(stepMs: Long) {
        if (stepMs != ARABIC_COMMENTARY_FINE_STEP_MS && stepMs != ARABIC_COMMENTARY_COARSE_STEP_MS) {
            return
        }
        state = state.copy(stepMs = stepMs)
    }

    fun adjust(symptom: ArabicCommentarySymptom) {
        if (released || state.adjusting) return
        if (state.phase != ArabicCommentaryPhase.ACTIVE) return
        val result = applyArabicCommentaryStep(
            delays = state.delays,
            symptom = symptom,
            stepMs = state.stepMs,
        )
        if (result.appliedMs <= 0L) {
            state = state.copy(
                message = "وصلت الى الحد الاقصى المدعوم (10 ث لكل مصدر).",
            )
            return
        }
        val target = when (result.delayedSource) {
            ArabicCommentaryDelayedSource.COMMENTARY -> commentaryPlayer
            ArabicCommentaryDelayedSource.VIDEO -> videoPlayer
        }
        if (target == null) {
            state = state.copy(message = "مصدر التعليق غير جاهز للضبط.")
            return
        }
        if (!videoPlayer.isPlaying) {
            state = state.copy(message = "شغل القناة اولا ثم اضبط التزامن.")
            return
        }
        if (result.delayedSource == ArabicCommentaryDelayedSource.COMMENTARY && !target.isPlaying) {
            state = state.copy(message = "التعليق غير جاهز للضبط الان.")
            return
        }
        val bufferedAheadMs = (target.bufferedPosition - target.currentPosition).coerceAtLeast(0L)
        if (target.playbackState != Player.STATE_READY || bufferedAheadMs < result.appliedMs) {
            state = state.copy(
                message = "البيانات المتوفرة لا تكفي لهذه الخطوة. انتظر تجهيز المخزن المؤقت.",
            )
            return
        }

        val gen = generation
        state = state.copy(
            adjusting = true,
            message = "جاري ضبط التزامن…",
            error = null,
        )
        adjustmentJob = scope.launch {
            var resumed = false
            try {
                val paused = pauseAndConfirm(target)
                if (!paused) {
                    runCatching { if (!target.playWhenReady) target.playWhenReady = true }
                    if (gen == generation) {
                        state = state.copy(
                            adjusting = false,
                            message = "تعذر ايقاف المصدر مؤقتا لضبط التزامن.",
                        )
                    }
                    return@launch
                }
                holdTarget = target
                if (gen != generation || state.phase != ArabicCommentaryPhase.ACTIVE) {
                    return@launch
                }
                delay(result.appliedMs)
                if (gen != generation || state.phase != ArabicCommentaryPhase.ACTIVE) {
                    return@launch
                }
                target.playWhenReady = true
                resumed = true
                holdTarget = null
                state = state.copy(
                    delays = result.delays,
                    adjusting = false,
                    message = null,
                )
                Log.i(
                    TAG,
                    "commentary adjusted source=${result.delayedSource} " +
                        "appliedMs=${result.appliedMs} commentaryMs=${result.delays.commentaryMs} " +
                        "videoMs=${result.delays.videoMs}",
                )
            } finally {
                if (holdTarget === target) {
                    holdTarget = null
                    if (!resumed) runCatching { target.playWhenReady = true }
                }
                if (gen == generation && state.adjusting) {
                    state = state.copy(adjusting = false)
                }
            }
        }
    }

    fun reset() {
        if (released || state.adjusting) return
        if (state.phase != ArabicCommentaryPhase.ACTIVE) return
        val current = state.delays
        if (!current.hasDelay) {
            state = state.copy(message = "لا يوجد تأخير لاعادة ضبطه.")
            return
        }
        val channel = state.selectedChannel ?: return
        if (current.videoMs > 0L && !videoPlayer.isCurrentMediaItemSeekable) {
            state = state.copy(
                message = "هذه القناة لا تدعم الرجوع للبث المباشر؛ لم يتم ازالة تأخير الصورة.",
            )
            return
        }
        // Rebuild the commentary at its live/default source instead of seeking the current HLS
        // reader. A live-edge seek can fail as an HTTP error on providers with a short window;
        // a bounded fresh start is the same baseline and still falls back to the next candidate.
        Log.i(TAG, "commentary reset restart channelId=${channel.id}")
        start(channel, notice = "جاري اعادة الضبط من البث المباشر…")
    }

    fun returnToOriginalAudio() {
        if (released) return
        val step = state.stepMs
        stopInternal(removeVideoDelay = true)
        state = ArabicCommentaryState(
            phase = ArabicCommentaryPhase.INACTIVE,
            stepMs = step,
            message = "تمت العودة للصوت الاصلي.",
        )
        Log.i(TAG, "commentary stopped reason=original_audio")
    }

    fun setOutputMuted(muted: Boolean) {
        this.muted = muted
        commentaryPlayer?.volume = if (state.commentaryAudible && !muted) 1f else 0f
    }

    fun onAppForegroundChanged(foreground: Boolean) {
        if (foreground || released) return
        interrupt(
            message = "تم ايقاف التعليق عند مغادرة التطبيق. يمكنك اعادة تشغيله.",
            reason = "background",
        )
    }

    fun onPlaybackInterrupted(message: String) {
        interrupt(message = message, reason = "playback_interrupted")
    }

    fun ensureAuthenticatedSession() {
        val owner = source?.owner ?: return
        if (!AuthenticatedSessionRegistry.isCurrent(owner)) {
            val step = state.stepMs
            val notice = state.connectionNotice
            stopInternal(removeVideoDelay = true)
            state = ArabicCommentaryState(
                phase = ArabicCommentaryPhase.INTERRUPTED,
                stepMs = step,
                connectionNotice = notice,
                message = "انتهت جلسة الحساب؛ تم ايقاف التعليق واخراج الصوت التعليقي.",
                canRetry = false,
                sessionAvailable = false,
            )
            Log.i(TAG, "commentary interrupted reason=session_changed")
        }
    }

    fun release() {
        if (released) return
        released = true
        videoPlayer.removeListener(videoListener)
        stopInternal(removeVideoDelay = true)
        Log.i(TAG, "commentary owner released")
    }

    private fun interrupt(message: String, reason: String) {
        if (released) return
        if (state.phase == ArabicCommentaryPhase.INACTIVE ||
            state.phase == ArabicCommentaryPhase.INTERRUPTED
        ) {
            return
        }
        val channel = state.selectedChannel
        val step = state.stepMs
        val notice = state.connectionNotice
        stopInternal(removeVideoDelay = true)
        state = ArabicCommentaryState(
            phase = ArabicCommentaryPhase.INTERRUPTED,
            selectedChannel = channel,
            stepMs = step,
            connectionNotice = notice,
            message = message,
            canRetry = channel != null,
        )
        Log.i(TAG, "commentary interrupted reason=$reason")
    }

    private fun createCommentaryListener(gen: Int): Player.Listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (gen != generation) return
            when (playbackState) {
                Player.STATE_READY -> markReady(gen)
                Player.STATE_BUFFERING -> if (state.phase == ArabicCommentaryPhase.ACTIVE) {
                    updateAudibleSource()
                    if (!state.adjusting && state.message == null) {
                        state = state.copy(
                            message = "توقف التعليق مؤقتا؛ قد تحتاج اعادة ضبط التزامن بعد الاستئناف.",
                        )
                    }
                }
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (gen != generation) return
            logAudioTrack(tracks)
            markReady(gen)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (gen != generation) return
            if (isPlaying) {
                markReady(gen)
            }
            if (state.phase == ArabicCommentaryPhase.ACTIVE) {
                updateAudibleSource()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (gen != generation) return
            handleCommentaryError(gen, error)
        }
    }

    private fun markReady(gen: Int) {
        if (gen != generation) return
        val player = commentaryPlayer ?: return
        if (state.phase != ArabicCommentaryPhase.PREPARING &&
            state.phase != ArabicCommentaryPhase.ACTIVE
        ) {
            return
        }
        val hasAudio = player.audioFormat != null ||
            player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        if (player.playbackState != Player.STATE_READY || !hasAudio) return
        if (state.phase == ArabicCommentaryPhase.PREPARING) {
            watchdogJob?.cancel()
            watchdogJob = null
            state = state.copy(
                phase = ArabicCommentaryPhase.ACTIVE,
                message = null,
                error = null,
            )
            Log.i(
                TAG,
                "commentary ready channelId=${state.selectedChannel?.id} " +
                    "seekable=${player.isCurrentMediaItemSeekable}",
            )
        }
        updateAudibleSource()
    }

    /**
     * Routes exactly one audible source. The video is muted before the commentary is raised and the
     * commentary is muted before the original audio returns.
     */
    private fun updateAudibleSource() {
        val commentary = commentaryPlayer ?: return
        if (state.phase != ArabicCommentaryPhase.ACTIVE) return
        val desired = arabicCommentaryDesiredAudibleSource(
            commentaryActive = state.phase == ArabicCommentaryPhase.ACTIVE,
            adjusting = state.adjusting,
            videoPlaying = videoPlayer.isPlaying,
            commentaryPlaying = commentary.isPlaying,
        ) ?: return
        val useCommentary = desired == ArabicCommentaryAudibleSource.COMMENTARY
        if (state.commentaryAudible == useCommentary) {
            commentary.volume = if (useCommentary && !muted) 1f else 0f
            return
        }
        if (useCommentary) {
            runCatching { onAudioRouteChanged(true) }
            commentary.volume = if (muted) 0f else 1f
        } else {
            commentary.volume = 0f
            runCatching { onAudioRouteChanged(false) }
        }
        state = state.copy(commentaryAudible = useCommentary)
        Log.i(
            TAG,
            "commentary audio route=${if (useCommentary) "COMMENTARY" else "ORIGINAL"} " +
                "channelId=${state.selectedChannel?.id} mainVolume=${videoPlayer.volume} " +
                "commentaryVolume=${commentary.volume}",
        )
    }

    private fun prepareCandidate(gen: Int, index: Int) {
        if (gen != generation) return
        val player = commentaryPlayer ?: return
        val resolved = source ?: return
        val planned = sourcePlan?.candidate(index)
        if (planned == null) {
            fail(gen, "لا يوجد مصدر تشغيل صالح لقناة التعليق.", null)
            return
        }
        val firstCandidate = sourcePlan?.firstCandidateIndex == index
        candidateIndex = index
        state = state.copy(
            phase = ArabicCommentaryPhase.PREPARING,
            message = if (firstCandidate) null else "جاري تجربة مصدر بديل…",
        )
        runCatching {
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(planned.uri)
                    .also { builder ->
                        planned.containerMimeType?.let(builder::setMimeType)
                    }
                    .build(),
            )
            player.prepare()
            player.playWhenReady = videoPlayer.playWhenReady
        }.onFailure { error ->
            fail(gen, "تعذر تحضير قناة التعليق.", error)
        }
        Log.i(
            TAG,
            "commentary prepared channelId=${resolved.request.streamId} candidate=$index " +
                "type=${planned.type} extension=${resolved.request.extension}",
        )
    }

    private fun handleCommentaryError(gen: Int, error: PlaybackException) {
        if (gen != generation) return
        val nextCandidate = sourcePlan?.nextCandidateIndex(afterIndex = candidateIndex)
        val hasNext = nextCandidate != null
        Log.i(
            TAG,
            "commentary error phase=${state.phase} candidate=$candidateIndex " +
                "next=$hasNext code=${error.errorCodeName}",
        )
        if (state.phase == ArabicCommentaryPhase.PREPARING && nextCandidate != null) {
            commentaryPlayer?.stop()
            prepareCandidate(gen, nextCandidate)
            return
        }
        fail(gen, "تعذر تشغيل قناة التعليق. تمت العودة للصوت الاصلي.", error)
    }

    private fun fail(gen: Int, message: String, error: Throwable?) {
        if (gen != generation) return
        val channel = state.selectedChannel
        val step = state.stepMs
        val notice = state.connectionNotice
        stopInternal(removeVideoDelay = true)
        state = ArabicCommentaryState(
            phase = ArabicCommentaryPhase.ERROR,
            selectedChannel = channel,
            stepMs = step,
            connectionNotice = notice,
            error = message,
            canRetry = channel != null,
        )
        Log.i(
            TAG,
            "commentary failed channelId=${channel?.id} " +
                "cause=${error?.javaClass?.simpleName.orEmpty()}",
        )
    }

    private suspend fun pauseAndConfirm(target: ExoPlayer): Boolean {
        if (!target.isPlaying) return false
        val paused = CompletableDeferred<Unit>()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && !paused.isCompleted) paused.complete(Unit)
            }
        }
        target.addListener(listener)
        target.playWhenReady = false
        val confirmed = withTimeoutOrNull(PAUSE_CONFIRM_TIMEOUT_MS) { paused.await() }
        target.removeListener(listener)
        return confirmed != null
    }

    private fun logAudioTrack(tracks: Tracks) {
        val audio = tracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            ?: return
        val format = audio.getTrackFormat(0)
        Log.i(
            TAG,
            "commentary audio channelId=${state.selectedChannel?.id} " +
                "mime=${format.sampleMimeType.orEmpty()} " +
                "container=${format.containerMimeType.orEmpty()} " +
                "codecs=${format.codecs.orEmpty()} channels=${format.channelCount} " +
                "sampleRate=${format.sampleRate}",
        )
    }

    private fun stopInternal(removeVideoDelay: Boolean) {
        generation += 1
        val wasCommentaryAudible = state.commentaryAudible
        val videoDelayMs = state.delays.videoMs
        watchdogJob?.cancel()
        watchdogJob = null
        adjustmentJob?.cancel()
        adjustmentJob = null
        val held = holdTarget
        holdTarget = null
        held?.let { runCatching { if (!it.playWhenReady) it.playWhenReady = true } }

        val player = commentaryPlayer
        commentaryPlayer = null
        commentaryListener?.let { listener -> player?.removeListener(listener) }
        commentaryListener = null
        if (player != null) {
            runCatching { player.volume = 0f }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        if (wasCommentaryAudible) {
            runCatching { onAudioRouteChanged(false) }
        }
        if (removeVideoDelay && videoDelayMs > 0L) {
            restoreVideoToLive(videoDelayMs)
        }
        source = null
        sourcePlan = null
    }

    private fun restoreVideoToLive(videoDelayMs: Long) {
        val seekable = runCatching { videoPlayer.isCurrentMediaItemSeekable }.getOrDefault(false)
        val restored = if (seekable) {
            moveToLiveEdge(videoPlayer)
        } else {
            // A live window that does not expose seeking cannot be proven to return to live. Make
            // the best-effort live request but do not report this path as a verified reset.
            runCatching { videoPlayer.seekToDefaultPosition() }.isSuccess
        }
        Log.i(
            TAG,
            "commentary video live restore delayMs=$videoDelayMs seekable=$seekable restored=$restored",
        )
    }

    /**
     * `Player.seekToDefaultPosition` is the Media3 contract for returning a live item to its live
     * default position (typically the live edge). It is only treated as verified when the window
     * exposes seeking, so a non-seekable provider never receives a false reset claim.
     */
    private fun moveToLiveEdge(player: ExoPlayer): Boolean {
        val seekable = runCatching { player.isCurrentMediaItemSeekable }.getOrDefault(false)
        if (!seekable) return false
        val beforeOffsetMs = runCatching { player.currentLiveOffset }.getOrDefault(C.TIME_UNSET)
        val applied = runCatching {
            player.seekToDefaultPosition()
            true
        }.getOrDefault(false)
        Log.i(
            TAG,
            "commentary live-edge seek applied=$applied beforeOffsetMs=$beforeOffsetMs",
        )
        return applied
    }
}

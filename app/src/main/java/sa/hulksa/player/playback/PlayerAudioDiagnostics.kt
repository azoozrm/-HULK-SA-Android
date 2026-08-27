@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.playback

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import sa.hulksa.player.model.PlaybackRequest

private const val HULK_AUDIO_DIAGNOSTICS_TAG = "HulkAudioDiag"

/**
 * Bounded, credential-safe diagnostics for proving audio codec/output failures on physical TVs.
 * It logs only stream identity, provider candidate index/type, formats, decoder capabilities and
 * runtime counters. It never logs media URLs, credentials, request headers or tokens.
 */
internal class PlayerAudioDiagnostics(
    context: Context,
    private val request: PlaybackRequest,
    private val generation: PlaybackGeneration,
    private val sourcePlan: MediaSourcePlan,
) {
    private val appContext = context.applicationContext
    private var boundPlayer: ExoPlayer? = null
    private var candidateIndex = -1
    private var outputMode = PlayerAudioOutputMode.NORMAL
    private var firstFrameRendered = false
    private var audioPositionAdvanced = false
    private var decoderName: String? = null
    private var audioFormat: Format? = null
    private var decoderCounters: DecoderCounters? = null
    private var audioTrackConfig: AudioSink.AudioTrackConfig? = null
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private var playerListener: Player.Listener? = null
    private var analyticsListener: AnalyticsListener? = null

    init {
        Log.i(
            HULK_AUDIO_DIAGNOSTICS_TAG,
            "session generation=${generation.safeLogId} streamKind=${request.streamKind} " +
                "device=${safeToken(Build.MANUFACTURER)}/${safeToken(Build.MODEL)} sdk=${Build.VERSION.SDK_INT}",
        )
    }

    fun attach(player: ExoPlayer) {
        if (boundPlayer === player) return
        detachBoundPlayer()
        boundPlayer = player

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (!accepts(player)) return
                logAudioTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!accepts(player)) return
                logPlayerError(player, error)
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
                if (accepts(player)) this@PlayerAudioDiagnostics.decoderCounters = decoderCounters
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                if (!accepts(player)) return
                this@PlayerAudioDiagnostics.decoderName = decoderName
                Log.i(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "decoder actual generation=${generation.safeLogId} candidate=$candidateIndex " +
                        "name=${safeToken(decoderName)} initMs=$initializationDurationMs " +
                        "mime=${audioFormat?.sampleMimeType.orEmpty()} mode=$outputMode",
                )
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                if (!accepts(player)) return
                audioFormat = format
                logAudioFormatAndDecoderInventory(format)
            }

            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig,
            ) {
                if (!accepts(player)) return
                this@PlayerAudioDiagnostics.audioTrackConfig = audioTrackConfig
                Log.i(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "output generation=${generation.safeLogId} candidate=$candidateIndex " +
                        "encoding=${audioTrackConfig.encoding} sampleRate=${audioTrackConfig.sampleRate} " +
                        "channelConfig=${audioTrackConfig.channelConfig} bufferSize=${audioTrackConfig.bufferSize} " +
                        "offload=${audioTrackConfig.offload} tunneling=${audioTrackConfig.tunneling} " +
                        "sessionId=$audioSessionId mode=$outputMode",
                )
            }

            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int,
            ) {
                if (!accepts(player)) return
                this@PlayerAudioDiagnostics.audioSessionId = audioSessionId
                Log.i(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "audio session generation=${generation.safeLogId} candidate=$candidateIndex sessionId=$audioSessionId",
                )
            }

            override fun onAudioPositionAdvancing(
                eventTime: AnalyticsListener.EventTime,
                playoutStartSystemTimeMs: Long,
            ) {
                if (!accepts(player)) return
                audioPositionAdvanced = true
                logRuntimeEvidence(player, event = "position_advancing")
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception,
            ) {
                if (!accepts(player)) return
                Log.w(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "sink error generation=${generation.safeLogId} candidate=$candidateIndex " +
                        "class=${audioSinkError.javaClass.simpleName} causes=${safeCauseClasses(audioSinkError)}",
                )
                logRuntimeEvidence(player, event = "sink_error")
            }

            override fun onAudioCodecError(
                eventTime: AnalyticsListener.EventTime,
                audioCodecError: Exception,
            ) {
                if (!accepts(player)) return
                Log.w(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "codec error generation=${generation.safeLogId} candidate=$candidateIndex " +
                        "decoder=${safeToken(decoderName.orEmpty())} class=${audioCodecError.javaClass.simpleName} " +
                        "causes=${safeCauseClasses(audioCodecError)}",
                )
                logRuntimeEvidence(player, event = "codec_error")
            }
        }

        playerListener = listener
        analyticsListener = analytics
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    fun onPrepare(candidateIndex: Int, outputMode: PlayerAudioOutputMode) {
        this.candidateIndex = candidateIndex
        this.outputMode = outputMode
        firstFrameRendered = false
        audioPositionAdvanced = false
        decoderName = null
        audioFormat = null
        decoderCounters = null
        audioTrackConfig = null
        audioSessionId = C.AUDIO_SESSION_ID_UNSET

        val source = sourcePlan.candidate(candidateIndex)
        Log.i(
            HULK_AUDIO_DIAGNOSTICS_TAG,
            "source generation=${generation.safeLogId} streamKind=${request.streamKind} candidate=$candidateIndex " +
                "sourceType=${source?.type ?: "UNKNOWN"} mimeHint=${source?.containerMimeType.orEmpty()} " +
                "mode=$outputMode",
        )
    }

    fun detach(player: ExoPlayer) {
        if (boundPlayer === player) detachBoundPlayer()
    }

    private fun accepts(player: ExoPlayer): Boolean = boundPlayer === player

    private fun detachBoundPlayer() {
        val player = boundPlayer
        playerListener?.let { listener -> player?.removeListener(listener) }
        analyticsListener?.let { listener -> player?.removeAnalyticsListener(listener) }
        playerListener = null
        analyticsListener = null
        boundPlayer = null
    }

    private fun logAudioTracks(tracks: Tracks) {
        var audioTrackCount = 0
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                audioTrackCount += 1
                val format = group.getTrackFormat(trackIndex)
                val selected = group.isTrackSelected(trackIndex)
                val support = group.getTrackSupport(trackIndex)
                Log.i(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "track generation=${generation.safeLogId} candidate=$candidateIndex group=$groupIndex track=$trackIndex " +
                        "selected=$selected sampleMimeType=${format.sampleMimeType.orEmpty()} " +
                        "containerMimeType=${format.containerMimeType.orEmpty()} codecs=${format.codecs.orEmpty()} " +
                        "channelCount=${format.channelCount} sampleRate=${format.sampleRate} bitrate=${format.bitrate} " +
                        "language=${safeToken(format.language.orEmpty())} selectionFlags=${format.selectionFlags} " +
                        "roleFlags=${format.roleFlags} support=$support supportLabel=${support.toDiagnosticSupportLabel()} " +
                        "selectedSupport=${if (selected) support.toDiagnosticSupportLabel() else "n/a"}",
                )
                if (selected) logDecoderInventories(format)
            }
        }
        if (audioTrackCount == 0) {
            Log.w(
                HULK_AUDIO_DIAGNOSTICS_TAG,
                "track generation=${generation.safeLogId} candidate=$candidateIndex audioTrackCount=0",
            )
        }
    }

    private fun logAudioFormatAndDecoderInventory(format: Format) {
        Log.i(
            HULK_AUDIO_DIAGNOSTICS_TAG,
            "format generation=${generation.safeLogId} candidate=$candidateIndex " +
                "sampleMimeType=${format.sampleMimeType.orEmpty()} containerMimeType=${format.containerMimeType.orEmpty()} " +
                "codecs=${format.codecs.orEmpty()} channelCount=${format.channelCount} " +
                "sampleRate=${format.sampleRate} bitrate=${format.bitrate}",
        )
        logDecoderInventories(format)
    }

    private fun logDecoderInventories(format: Format) {
        logDecoderInventory("default", MediaCodecSelector.DEFAULT, format)
        logDecoderInventory("prefer_software", MediaCodecSelector.PREFER_SOFTWARE, format)
    }

    private fun logDecoderInventory(
        selectorLabel: String,
        selector: MediaCodecSelector,
        format: Format,
    ) {
        val mime = format.sampleMimeType
        if (mime.isNullOrBlank()) {
            Log.w(
                HULK_AUDIO_DIAGNOSTICS_TAG,
                "decoder inventory generation=${generation.safeLogId} candidate=$candidateIndex " +
                    "selector=$selectorLabel mime=unknown count=0",
            )
            return
        }
        val decoders = runCatching { selector.getDecoderInfos(mime, false, false) }
            .getOrElse { error ->
                Log.w(
                    HULK_AUDIO_DIAGNOSTICS_TAG,
                    "decoder inventory generation=${generation.safeLogId} candidate=$candidateIndex " +
                        "selector=$selectorLabel mime=$mime queryError=${error.javaClass.simpleName}",
                )
                emptyList()
            }
        if (decoders.isEmpty()) {
            Log.w(
                HULK_AUDIO_DIAGNOSTICS_TAG,
                "decoder inventory generation=${generation.safeLogId} candidate=$candidateIndex " +
                    "selector=$selectorLabel mime=$mime count=0",
            )
            return
        }
        decoders.forEachIndexed { index, info ->
            val formatSupported = runCatching { info.isFormatSupported(appContext, format) }
                .fold(onSuccess = Boolean::toString, onFailure = { "unknown" })
            Log.i(
                HULK_AUDIO_DIAGNOSTICS_TAG,
                "decoder inventory generation=${generation.safeLogId} candidate=$candidateIndex " +
                    "selector=$selectorLabel index=$index name=${safeToken(info.name)} " +
                    "softwareOnly=${info.softwareOnly} hardwareAccelerated=${info.hardwareAccelerated} " +
                    "vendor=${info.vendor} formatSupported=$formatSupported",
            )
        }
    }

    private fun logPlayerError(player: ExoPlayer, error: PlaybackException) {
        val exoError = error as? ExoPlaybackException
        val rendererType = exoError
            ?.rendererIndex
            ?.takeIf { it in 0 until player.rendererCount }
            ?.let(player::getRendererType)
            ?: C.TRACK_TYPE_UNKNOWN
        Log.w(
            HULK_AUDIO_DIAGNOSTICS_TAG,
            "player error generation=${generation.safeLogId} candidate=$candidateIndex " +
                "errorCode=${error.errorCode} errorName=${PlaybackException.getErrorCodeName(error.errorCode)} " +
                "rendererIndex=${exoError?.rendererIndex ?: C.INDEX_UNSET} rendererType=$rendererType " +
                "rendererName=${safeToken(exoError?.rendererName.orEmpty())} " +
                "rendererMime=${exoError?.rendererFormat?.sampleMimeType.orEmpty()} " +
                "rendererFormatSupport=${exoError?.rendererFormatSupport ?: C.FORMAT_UNSUPPORTED_TYPE} " +
                "causes=${safeCauseClasses(error)}",
        )
        logRuntimeEvidence(player, event = "player_error")
    }

    private fun logRuntimeEvidence(player: ExoPlayer, event: String) {
        val counters = decoderCounters
        counters?.ensureUpdated()
        val output = audioTrackConfig
        Log.i(
            HULK_AUDIO_DIAGNOSTICS_TAG,
            "runtime generation=${generation.safeLogId} candidate=$candidateIndex event=$event " +
                "decoder=${safeToken(decoderName.orEmpty())} decoderInits=${counters?.decoderInitCount ?: 0} " +
                "decoderReleases=${counters?.decoderReleaseCount ?: 0} queuedInput=${counters?.queuedInputBufferCount ?: 0} " +
                "renderedOutput=${counters?.renderedOutputBufferCount ?: 0} " +
                "skippedInput=${counters?.skippedInputBufferCount ?: 0} skippedOutput=${counters?.skippedOutputBufferCount ?: 0} " +
                "dropped=${counters?.droppedBufferCount ?: 0} droppedInput=${counters?.droppedInputBufferCount ?: 0} " +
                "audioPositionAdvancing=$audioPositionAdvanced playerPositionMs=${player.currentPosition.coerceAtLeast(0L)} " +
                "playbackState=${player.playbackState} firstFrameRendered=$firstFrameRendered " +
                "isPlaying=${player.isPlaying} isLoading=${player.isLoading} " +
                "encoding=${output?.encoding ?: C.ENCODING_INVALID} sampleRate=${output?.sampleRate ?: -1} " +
                "channelConfig=${output?.channelConfig ?: -1} bufferSize=${output?.bufferSize ?: 0} " +
                "offload=${output?.offload ?: false} tunneling=${output?.tunneling ?: false} sessionId=$audioSessionId " +
                "mode=$outputMode",
        )
    }
}

private fun Int.toDiagnosticSupportLabel(): String = when (this) {
    C.FORMAT_HANDLED -> "HANDLED"
    C.FORMAT_EXCEEDS_CAPABILITIES -> "EXCEEDS_CAPABILITIES"
    C.FORMAT_UNSUPPORTED_DRM -> "UNSUPPORTED_DRM"
    C.FORMAT_UNSUPPORTED_SUBTYPE -> "UNSUPPORTED_SUBTYPE"
    C.FORMAT_UNSUPPORTED_TYPE -> "UNSUPPORTED_TYPE"
    else -> "UNKNOWN"
}

private fun safeCauseClasses(error: Throwable): String {
    val names = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && names.size < 4) {
        names += safeToken(current.javaClass.simpleName)
        current = current.cause
    }
    return names.joinToString(separator = ">").ifBlank { "none" }
}

private fun safeToken(value: String): String = value
    .replace(Regex("[^A-Za-z0-9._+:/-]"), "_")
    .take(160)

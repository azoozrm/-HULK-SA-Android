@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.playback

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.util.ArrayList

internal class HulkPlayerFactory(context: Context) {
    private val appContext = context.applicationContext

    fun create(outputMode: PlayerAudioOutputMode): ExoPlayer {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("HULK-SA-Player/0.7.2")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Accept" to "*/*", "Icy-MetaData" to "1"))
        val dataSource = DefaultDataSource.Factory(appContext, httpDataSource)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSource)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .build()
        val trackSelector = DefaultTrackSelector(appContext)
        val renderersFactory = HulkPlayerRenderersFactory(appContext, outputMode)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val createStartMs = android.os.SystemClock.uptimeMillis()
        val created = ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        android.util.Log.i(
            "HulkPlayer",
            "player created t=${android.os.SystemClock.elapsedRealtime()} mode=$outputMode " +
                "in ${android.os.SystemClock.uptimeMillis() - createStartMs}ms",
        )
        return created
            .also { player ->
                if (outputMode == PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(
                                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                                )
                                .build(),
                        )
                        .build()
                }
            }
    }
}

internal fun PlannedMediaSource.toMediaItem(generation: PlaybackGeneration): MediaItem =
    MediaItem.Builder()
        .setMediaId("${generation.safeLogId}:candidate:$candidateIndex")
        .setUri(uri)
        .also { builder -> containerMimeType?.let(builder::setMimeType) }
        .build()

internal fun TrackSelectionParameters.forPlayerReplacement(
    outputMode: PlayerAudioOutputMode,
    sourceChanged: Boolean,
): TrackSelectionParameters = buildUpon()
    .also { builder ->
        if (sourceChanged) builder.clearOverrides()
        if (outputMode == PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM) {
            builder.setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                    )
                    .build(),
            )
        }
    }
    .build()

private class HulkPlayerRenderersFactory(
    context: Context,
    private val outputMode: PlayerAudioOutputMode,
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
        if (outputMode == PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM) {
            setMediaCodecSelector(AudioOnlySoftwareFirstCodecSelector)
        }
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        // Keep the same platform video renderer/decoder selection, but normalize the one
        // ambiguous SDR color combination that the Amlogic decoder otherwise promotes to HDR.
        val videoIndex = out.indexOfFirst { it is MediaCodecVideoRenderer }
        if (videoIndex >= 0) {
            out[videoIndex] = HulkColorSafeVideoRenderer(
                context = context,
                mediaCodecSelector = mediaCodecSelector,
                allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs,
                enableDecoderFallback = enableDecoderFallback,
                eventHandler = eventHandler,
                eventListener = eventListener,
            )
        }
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )

        val bundledMp2Available = BundledMp2AudioRenderer.isAvailable()
        if (shouldInstallBundledMp2Renderer(outputMode, bundledMp2Available)) {
            // Keep MediaCodec first. Media3 selects this renderer only when the platform renderer
            // cannot handle audio/mpeg-L2. No other audio MIME is accepted by the bundled renderer.
            out += BundledMp2AudioRenderer(eventHandler, eventListener, audioSink)
            Log.i(
                "HulkPlayerFactory",
                "bundledMp2Available=true decoderPath=PLATFORM>BUNDLED_MP2 recoveryStage=PLATFORM_SOFTWARE_PCM",
            )
        }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        if (outputMode == PlayerAudioOutputMode.NORMAL) {
            return super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
        }

        // This recovery-only path decodes audio to PCM and avoids repeating a silent encoded
        // passthrough/offload route. Video decoder ordering remains untouched.
        @Suppress("DEPRECATION")
        return DefaultAudioSink.Builder()
            .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            .build()
    }
}

/**
 * Platform video renderer with one narrow color-metadata normalization. Some 8-bit H.264
 * providers publish BT.2020 primaries with an unspecified transfer and no HDR static metadata;
 * the Amlogic decoder promotes that combination to its HDR path and washes the video/OSD. Treat
 * only that ambiguous combination as the SDR BT.709 limited stream it actually is. Real HDR
 * (ST2084/HLG), explicit transfer, bitdepth > 8 and every non-H.264 stream are untouched.
 */
private class HulkColorSafeVideoRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    allowedVideoJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
) : MediaCodecVideoRenderer(
    context,
    mediaCodecSelector,
    allowedVideoJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
) {
    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        val current = formatHolder.format
        if (current != null) {
            val normalized = normalizeAmbiguousSdrVideoColor(current)
            if (normalized !== current) {
                Log.i(
                    "HulkPlayerFactory",
                    "video color normalized mime=${current.sampleMimeType.orEmpty()} " +
                        "codecs=${current.codecs.orEmpty()} size=${current.width}x${current.height} " +
                        "from=space:${current.colorInfo?.colorSpace} transfer:${current.colorInfo?.colorTransfer} " +
                        "to=space:${C.COLOR_SPACE_BT709} transfer:${C.COLOR_TRANSFER_SDR}",
                )
                formatHolder.format = normalized
            }
        }
        return super.onInputFormatChanged(formatHolder)
    }
}

internal fun normalizeAmbiguousSdrVideoColor(format: Format): Format {
    val info = format.colorInfo ?: return format
    if (format.sampleMimeType != MimeTypes.VIDEO_H264) return format
    if (info.colorSpace != C.COLOR_SPACE_BT2020) return format
    if (info.colorTransfer >= 0) return format
    if (info.hdrStaticInfo?.isNotEmpty() == true) return format
    if (ColorInfo.isTransferHdr(info)) return format
    if (info.lumaBitdepth > 8) return format
    return format.buildUpon()
        .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
        .build()
}

private object AudioOnlySoftwareFirstCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> = if (mimeType.startsWith("audio/", ignoreCase = true)) {
        MediaCodecSelector.PREFER_SOFTWARE.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
    } else {
        MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
    }
}

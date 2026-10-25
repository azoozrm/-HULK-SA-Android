@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.playback

import android.content.Context
import android.os.Build
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
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
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
    private var inputRequiresCompatibility = false
    private var activeCodecCompatibility = false

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        val current = formatHolder.format
        inputRequiresCompatibility =
            current != null && shouldApplyAmlogicColorCompatibility(current)
        if (inputRequiresCompatibility && current != null) {
            Log.i(
                "HulkPlayerFactory",
                "video color compatibility mime=${current.sampleMimeType.orEmpty()} " +
                    "codecs=${current.codecs.orEmpty()} size=${current.width}x${current.height} " +
                    "from=space:${current.colorInfo?.colorSpace} transfer:${current.colorInfo?.colorTransfer} " +
                    "range:${current.colorInfo?.colorRange} assumption=BT709_LIMITED",
            )
        }
        // Keep the original Format in the holder so capability queries and reuse evaluate the
        // actual incoming stream, not the normalized decoder configuration.
        return super.onInputFormatChanged(formatHolder)
    }

    override fun getDecoderInfos(
        selector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val decoders = super.getDecoderInfos(selector, format, requiresSecureDecoder)
        if (!shouldApplyAmlogicColorCompatibility(format)) return decoders
        if (decoders.none { it.name.contains("amlogic", ignoreCase = true) }) return decoders
        val software = decoders.filter { it.softwareOnly }
        if (software.isEmpty()) {
            Log.w(
                "HulkPlayerFactory",
                "video decoder path=SOFTWARE_COMPAT unavailable mime=${format.sampleMimeType.orEmpty()} " +
                    "size=${format.width}x${format.height} fallback=DEFAULT",
            )
            return decoders
        }
        Log.i(
            "HulkPlayerFactory",
            "video decoder path=SOFTWARE_COMPAT mime=${format.sampleMimeType.orEmpty()} " +
                "size=${format.width}x${format.height} candidates=${software.size}",
        )
        return software
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int,
    ): android.media.MediaFormat {
        // Compatibility assumption: the evidenced stream is limited-range 8-bit H.264 carrying
        // BT.2020 primaries without a transfer. Normalize only the decoder configuration; the
        // original Format remains available for reuse/capability classification.
        val configured = if (shouldApplyAmlogicColorCompatibility(format)) {
            normalizeAmbiguousSdrVideoColor(format)
        } else {
            format
        }
        return super.getMediaFormat(
            configured,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId,
        )
    }

    override fun onCodecInitialized(
        name: String,
        configuration: MediaCodecAdapter.Configuration,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        super.onCodecInitialized(
            name,
            configuration,
            initializedTimestampMs,
            initializationDurationMs,
        )
        activeCodecCompatibility = inputRequiresCompatibility
    }

    override fun canReuseCodec(
        codecInfo: MediaCodecInfo,
        oldFormat: Format,
        newFormat: Format,
        isAdaptiveFormatChange: Boolean,
    ): DecoderReuseEvaluation {
        val newCompatibility = shouldApplyAmlogicColorCompatibility(newFormat)
        if (shouldRejectVideoCodecReuse(activeCodecCompatibility, newCompatibility)) {
            // Force the supported Media3 reinitialization path when the current decoder does not
            // satisfy the new policy (hardware -> compatibility software, or the reverse).
            return DecoderReuseEvaluation(
                codecInfo.name,
                oldFormat,
                newFormat,
                DecoderReuseEvaluation.REUSE_RESULT_NO,
                DecoderReuseEvaluation.DISCARD_REASON_APP_OVERRIDE,
            )
        }
        return super.canReuseCodec(codecInfo, oldFormat, newFormat, isAdaptiveFormatChange)
    }
}

/** Platform bound evidenced by the Amlogic vendor video/CPQ path on the Xiaomi surface. */
internal fun isAffectedAmlogicPlatform(): Boolean =
    Build.HARDWARE.contains("amlogic", ignoreCase = true)

/**
 * Narrow compatibility assumption for the evidenced limited-range, known-8-bit H.264 stream with
 * BT.2020 primaries, unspecified transfer and no HDR metadata. Unknown bit depth alone is not
 * sufficient; the AVC profile must independently identify an 8-bit profile.
 */
internal fun isAmbiguousSdrVideoColor(format: Format): Boolean {
    val info = format.colorInfo ?: return false
    if (format.sampleMimeType != MimeTypes.VIDEO_H264) return false
    if (info.colorSpace != C.COLOR_SPACE_BT2020) return false
    if (info.colorTransfer >= 0) return false
    if (info.colorRange != C.COLOR_RANGE_LIMITED) return false
    if (info.hdrStaticInfo?.isNotEmpty() == true) return false
    if (ColorInfo.isTransferHdr(info)) return false
    if (info.lumaBitdepth > 8) return false
    if (info.lumaBitdepth < 1 && !isKnownEightBitAvcProfile(format)) return false
    return true
}

/** H.264 profile_idc values that identify 8-bit profiles; High10/422/444 are excluded. */
internal fun isKnownEightBitAvcProfile(format: Format): Boolean {
    val codecs = format.codecs?.trim()?.lowercase() ?: return false
    val prefix = when {
        codecs.startsWith("avc1.") -> "avc1."
        codecs.startsWith("avc3.") -> "avc3."
        else -> return false
    }
    val profile = codecs.removePrefix(prefix).take(2).toIntOrNull(16) ?: return false
    return profile in setOf(44, 66, 77, 88, 100, 118, 128)
}

internal fun shouldApplyAmlogicColorCompatibility(
    format: Format,
    isAmlogicPlatform: Boolean = isAffectedAmlogicPlatform(),
): Boolean = isAmlogicPlatform && isAmbiguousSdrVideoColor(format)

/** Reuse is allowed only when the current decoder and the incoming format share one policy. */
internal fun shouldRejectVideoCodecReuse(
    activeCodecCompatibility: Boolean,
    newFormatRequiresCompatibility: Boolean,
): Boolean = activeCodecCompatibility != newFormatRequiresCompatibility

internal fun normalizeAmbiguousSdrVideoColor(format: Format): Format =
    if (isAmbiguousSdrVideoColor(format)) {
        format.buildUpon()
            .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
            .build()
    } else {
        format
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

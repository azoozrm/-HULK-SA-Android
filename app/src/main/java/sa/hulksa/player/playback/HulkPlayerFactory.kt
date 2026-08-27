@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.playback

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Renderer
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

        return ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
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

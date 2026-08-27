package sa.hulksa.player.playback

internal const val BUNDLED_MP2_MIME_TYPE = "audio/mpeg-L2"

internal enum class BundledMp2DecoderPath {
    PLATFORM,
    BUNDLED_MP2,
    NONE,
}

internal fun isBundledMp2MimeType(sampleMimeType: String?): Boolean =
    sampleMimeType?.equals(BUNDLED_MP2_MIME_TYPE, ignoreCase = true) == true

internal fun shouldInstallBundledMp2Renderer(
    outputMode: PlayerAudioOutputMode,
    bundledMp2Available: Boolean,
): Boolean = outputMode == PlayerAudioOutputMode.PLATFORM_SOFTWARE_PCM && bundledMp2Available

/**
 * Mirrors Media3 renderer priority for the recovery-only MP2 path. The platform renderer is first;
 * the bundled renderer is eligible only when the platform format support is not fully handled.
 */
internal fun bundledMp2DecoderPath(
    sampleMimeType: String?,
    platformSupport: AudioFormatSupport,
    outputMode: PlayerAudioOutputMode,
    bundledMp2Available: Boolean,
    audioTrackPresent: Boolean = true,
): BundledMp2DecoderPath = when {
    !audioTrackPresent -> BundledMp2DecoderPath.NONE
    platformSupport == AudioFormatSupport.SUPPORTED -> BundledMp2DecoderPath.PLATFORM
    shouldInstallBundledMp2Renderer(outputMode, bundledMp2Available) &&
        isBundledMp2MimeType(sampleMimeType) -> BundledMp2DecoderPath.BUNDLED_MP2
    else -> BundledMp2DecoderPath.NONE
}

internal fun bundledMp2DiagnosticFields(
    bundledMp2Available: Boolean,
    decoderPath: BundledMp2DecoderPath,
): String =
    "sampleMimeType=$BUNDLED_MP2_MIME_TYPE " +
        "bundledMp2Available=$bundledMp2Available decoderPath=${decoderPath.name}"

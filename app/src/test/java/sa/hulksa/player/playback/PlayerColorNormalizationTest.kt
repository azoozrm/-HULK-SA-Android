package sa.hulksa.player.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerColorNormalizationTest {
    private fun videoFormat(
        mimeType: String = MimeTypes.VIDEO_H264,
        codecs: String = "avc1.64001F",
        colorInfo: ColorInfo?,
    ): Format = Format.Builder()
        .setSampleMimeType(mimeType)
        .setWidth(1280)
        .setHeight(720)
        .setCodecs(codecs)
        .apply { colorInfo?.let(::setColorInfo) }
        .build()

    private fun bt2020(
        transfer: Int,
        range: Int = C.COLOR_RANGE_LIMITED,
        lumaBitdepth: Int = -1,
        hdrStatic: ByteArray? = null,
    ): ColorInfo = ColorInfo.Builder()
        .setColorSpace(C.COLOR_SPACE_BT2020)
        .setColorRange(range)
        .setColorTransfer(transfer)
        .setLumaBitdepth(lumaBitdepth)
        .setHdrStaticInfo(hdrStatic)
        .build()

    @Test
    fun limitedRangeKnownEightBitBt2020H264WithoutTransferIsCompatibilityCandidate() {
        val format = videoFormat(colorInfo = bt2020(transfer = -1))

        assertTrue(isAmbiguousSdrVideoColor(format))
        assertEquals(ColorInfo.SDR_BT709_LIMITED, normalizeAmbiguousSdrVideoColor(format).colorInfo)
        assertTrue(shouldApplyAmlogicColorCompatibility(format, isAmlogicPlatform = true))
        assertFalse(shouldApplyAmlogicColorCompatibility(format, isAmlogicPlatform = false))
    }

    @Test
    fun fullRangeIsNeverRewritten() {
        val format = videoFormat(colorInfo = bt2020(transfer = -1, range = C.COLOR_RANGE_FULL))

        assertFalse(isAmbiguousSdrVideoColor(format))
        assertSame(format, normalizeAmbiguousSdrVideoColor(format))
    }

    @Test
    fun unknownBitDepthRequiresAnIndependentEightBitAvcProfile() {
        val highProfile = videoFormat(
            codecs = "avc1.64001F",
            colorInfo = bt2020(transfer = -1, lumaBitdepth = -1),
        )
        val high10Profile = videoFormat(
            codecs = "avc1.6E001F",
            colorInfo = bt2020(transfer = -1, lumaBitdepth = -1),
        )
        val high10BitDepth = videoFormat(
            codecs = "avc1.64001F",
            colorInfo = bt2020(transfer = -1, lumaBitdepth = 10),
        )
        val cavlc444 = videoFormat(
            codecs = "avc1.2C101F",
            colorInfo = bt2020(transfer = -1, lumaBitdepth = -1),
        )

        assertTrue(isAmbiguousSdrVideoColor(highProfile))
        assertFalse(isAmbiguousSdrVideoColor(high10Profile))
        assertFalse(isAmbiguousSdrVideoColor(high10BitDepth))
        assertFalse(isAmbiguousSdrVideoColor(cavlc444))
        assertSame(cavlc444, normalizeAmbiguousSdrVideoColor(cavlc444))
    }

    @Test
    fun explicitSdrBt2020IsNotRewritten() {
        val format = videoFormat(colorInfo = bt2020(transfer = C.COLOR_TRANSFER_SDR))

        assertFalse(isAmbiguousSdrVideoColor(format))
        assertSame(format, normalizeAmbiguousSdrVideoColor(format))
    }

    @Test
    fun realHdrAndTenBitAreNeverRewritten() {
        val pq = videoFormat(
            colorInfo = bt2020(transfer = C.COLOR_TRANSFER_ST2084, hdrStatic = byteArrayOf(1)),
        )
        val hlg = videoFormat(colorInfo = bt2020(transfer = C.COLOR_TRANSFER_HLG))
        val tenBit = videoFormat(colorInfo = bt2020(transfer = -1, lumaBitdepth = 10))

        assertSame(pq, normalizeAmbiguousSdrVideoColor(pq))
        assertSame(hlg, normalizeAmbiguousSdrVideoColor(hlg))
        assertSame(tenBit, normalizeAmbiguousSdrVideoColor(tenBit))
    }

    @Test
    fun decoderReuseIsRejectedOnlyWhenTheCompatibilityPolicyChanges() {
        assertTrue(shouldRejectVideoCodecReuse(false, true))
        assertTrue(shouldRejectVideoCodecReuse(true, false))
        assertFalse(shouldRejectVideoCodecReuse(false, false))
        assertFalse(shouldRejectVideoCodecReuse(true, true))
    }

    @Test
    fun nonH264AndMissingColorInfoAreUntouched() {
        val hevc = videoFormat(
            mimeType = MimeTypes.VIDEO_H265,
            codecs = "hvc1.2.4.L153.B0",
            colorInfo = bt2020(transfer = -1),
        )
        val noColor = videoFormat(colorInfo = null)

        assertSame(hevc, normalizeAmbiguousSdrVideoColor(hevc))
        assertSame(noColor, normalizeAmbiguousSdrVideoColor(noColor))
    }
}

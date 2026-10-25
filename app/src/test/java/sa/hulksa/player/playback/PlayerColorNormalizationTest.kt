package sa.hulksa.player.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerColorNormalizationTest {
    private fun videoFormat(
        mimeType: String = MimeTypes.VIDEO_H264,
        colorInfo: ColorInfo?,
    ): Format = Format.Builder()
        .setSampleMimeType(mimeType)
        .setWidth(1280)
        .setHeight(720)
        .setCodecs("avc1.64001F")
        .apply { colorInfo?.let(::setColorInfo) }
        .build()

    private fun bt2020(
        transfer: Int,
        lumaBitdepth: Int = -1,
        hdrStatic: ByteArray? = null,
    ): ColorInfo = ColorInfo.Builder()
        .setColorSpace(C.COLOR_SPACE_BT2020)
        .setColorRange(C.COLOR_RANGE_LIMITED)
        .setColorTransfer(transfer)
        .setLumaBitdepth(lumaBitdepth)
        .setHdrStaticInfo(hdrStatic)
        .build()

    @Test
    fun ambiguousBt2020H264WithoutTransferIsNormalizedToSdrBt709() {
        val format = videoFormat(colorInfo = bt2020(transfer = -1))

        val normalized = normalizeAmbiguousSdrVideoColor(format)

        assertEquals(ColorInfo.SDR_BT709_LIMITED, normalized.colorInfo)
        assertEquals(format.sampleMimeType, normalized.sampleMimeType)
        assertEquals(format.codecs, normalized.codecs)
        assertEquals(format.width, normalized.width)
    }

    @Test
    fun explicitSdrBt2020IsNotRewritten() {
        val format = videoFormat(colorInfo = bt2020(transfer = C.COLOR_TRANSFER_SDR))

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
    fun nonH264AndMissingColorInfoAreUntouched() {
        val hevc = videoFormat(
            mimeType = MimeTypes.VIDEO_H265,
            colorInfo = bt2020(transfer = -1),
        )
        val noColor = videoFormat(colorInfo = null)

        assertSame(hevc, normalizeAmbiguousSdrVideoColor(hevc))
        assertSame(noColor, normalizeAmbiguousSdrVideoColor(noColor))
    }
}

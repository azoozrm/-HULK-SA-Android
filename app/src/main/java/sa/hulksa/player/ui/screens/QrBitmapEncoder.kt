package sa.hulksa.player.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Shared ZXing QR encoder for the real Growth and pairing destinations. Local-only: no QR network
 * service, no mock/generated bitmap, and the caller supplies the already-validated public URL.
 */
internal fun encodeQrBitmap(content: String, sizePixels: Int): Bitmap? = runCatching {
    require(content.isNotBlank())
    val safeSize = sizePixels.coerceIn(256, 1_400)
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        safeSize,
        safeSize,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ),
    )
    val pixels = IntArray(safeSize * safeSize)
    for (y in 0 until safeSize) {
        val rowOffset = y * safeSize
        for (x in 0 until safeSize) {
            pixels[rowOffset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(pixels, safeSize, safeSize, Bitmap.Config.ARGB_8888)
}.getOrNull()

package sa.hulksa.player.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Scoped R07 quick-login glyph: one stroked smartphone with a QR block, matching the accepted
 * reference export. The preview-only icon library is not imported; this is the single native
 * semantic vector added for the pairing entry.
 */
internal val PairingQuickLoginIcon: ImageVector = ImageVector.Builder(
    name = "PairingQuickLogin",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(7.5f, 1.5f)
        lineTo(16.5f, 1.5f)
        quadTo(19f, 1.5f, 19f, 4f)
        lineTo(19f, 20f)
        quadTo(19f, 22.5f, 16.5f, 22.5f)
        lineTo(7.5f, 22.5f)
        quadTo(5f, 22.5f, 5f, 20f)
        lineTo(5f, 4f)
        quadTo(5f, 1.5f, 7.5f, 1.5f)
        close()
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(10f, 19.5f)
        lineTo(14f, 19.5f)
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 6f)
        horizontalLineTo(11f)
        verticalLineTo(9f)
        horizontalLineTo(8f)
        close()
        moveTo(13f, 6f)
        horizontalLineTo(16f)
        verticalLineTo(9f)
        horizontalLineTo(13f)
        close()
        moveTo(8f, 11f)
        horizontalLineTo(11f)
        verticalLineTo(14f)
        horizontalLineTo(8f)
        close()
        moveTo(13f, 11f)
        horizontalLineTo(14f)
        verticalLineTo(12f)
        horizontalLineTo(16f)
        verticalLineTo(14f)
        horizontalLineTo(13f)
        close()
    }
}.build()

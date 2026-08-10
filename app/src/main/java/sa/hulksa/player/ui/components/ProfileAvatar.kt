package sa.hulksa.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ProfileAvatar(
    avatarKey: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val normalizedKey = normalizeAvatarKey(avatarKey)
    val palette = when (normalizedKey) {
        "nova" -> AvatarPalette(Color(0xFF4F8DFF), Color(0xFF214C9E), Color(0xFFEAF3FF), Color(0xFF1D4A8A))
        "sage" -> AvatarPalette(Color(0xFF55D6B3), Color(0xFF167D67), Color(0xFFE7FFF8), Color(0xFF16725F))
        "orbit" -> AvatarPalette(Color(0xFF78B7E8), Color(0xFF315B7D), Color(0xFFF2FAFF), Color(0xFF31536D))
        "sunny" -> AvatarPalette(Color(0xFFFFD45A), Color(0xFFB97A12), Color(0xFFFFF6C9), Color(0xFFC17A10))
        else -> AvatarPalette(Color(0xFFFFA45A), Color(0xFFB55C17), Color(0xFFFFF0DF), Color(0xFFB45B17))
    }

    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(palette.backgroundTop, palette.backgroundBottom),
                ),
                CircleShape,
            )
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) Color(0xFFFFD54F) else Color.White.copy(alpha = .18f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawOval(
                color = palette.body,
                topLeft = Offset(w * .20f, h * .62f),
                size = Size(w * .60f, h * .32f),
            )

            drawCircle(
                color = palette.face,
                radius = w * .22f,
                center = Offset(w * .50f, h * .39f),
            )

            when (normalizedKey) {
                "nova" -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 192f,
                        sweepAngle = 156f,
                        useCenter = true,
                        topLeft = Offset(w * .27f, h * .16f),
                        size = Size(w * .46f, h * .38f),
                    )
                    drawCircle(Color.White.copy(alpha = .90f), w * .022f, Offset(w * .68f, h * .24f))
                }
                "sage" -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(w * .28f, h * .17f),
                        size = Size(w * .44f, h * .32f),
                    )
                }
                "orbit" -> {
                    drawRect(
                        color = palette.accent,
                        topLeft = Offset(w * .30f, h * .17f),
                        size = Size(w * .40f, h * .15f),
                    )
                }
                "sunny" -> {
                    drawCircle(palette.accent, w * .08f, Offset(w * .35f, h * .19f))
                    drawCircle(palette.accent, w * .08f, Offset(w * .65f, h * .19f))
                }
                else -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = true,
                        topLeft = Offset(w * .29f, h * .18f),
                        size = Size(w * .42f, h * .30f),
                    )
                }
            }

            val eyeY = h * .40f
            drawCircle(palette.eye, w * .022f, Offset(w * .43f, eyeY))
            drawCircle(palette.eye, w * .022f, Offset(w * .57f, eyeY))

            val mouthStart = if (normalizedKey == "orbit") 0f else 15f
            val mouthSweep = if (normalizedKey == "orbit") 180f else 150f
            drawArc(
                color = palette.eye,
                startAngle = mouthStart,
                sweepAngle = mouthSweep,
                useCenter = false,
                topLeft = Offset(w * .42f, h * .43f),
                size = Size(w * .16f, h * .10f),
                style = Stroke(width = w * .018f),
            )
        }
    }
}

private fun normalizeAvatarKey(key: String): String = when (key) {
    "ember", "default" -> "ember"
    "nova", "gold" -> "nova"
    "sage", "classic" -> "sage"
    "orbit", "dark" -> "orbit"
    "sunny", "kids" -> "sunny"
    else -> "ember"
}

private data class AvatarPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val face: Color,
    val body: Color,
) {
    val accent: Color get() = body.copy(alpha = .96f)
    val eye: Color get() = backgroundBottom
}

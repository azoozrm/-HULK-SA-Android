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
    val palette = when (avatarKey) {
        "gold" -> AvatarPalette(Color(0xFFF2C94C), Color(0xFF6B4A00), Color(0xFFFFF1A8), Color(0xFF2D1C00))
        "dark" -> AvatarPalette(Color(0xFF31343A), Color(0xFF0B0D10), Color(0xFFD8DEE9), Color(0xFF111318))
        "classic" -> AvatarPalette(Color(0xFF6F8F3D), Color(0xFF1F2B16), Color(0xFFDCE8B7), Color(0xFF18200F))
        "kids" -> AvatarPalette(Color(0xFF55C2FF), Color(0xFF124866), Color(0xFFFFE082), Color(0xFF0C2A3A))
        else -> AvatarPalette(Color(0xFFB8C74A), Color(0xFF263113), Color(0xFFE8F0A8), Color(0xFF16200B))
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
                color = if (highlighted) Color(0xFFFFD54F) else Color.White.copy(alpha = .14f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // shoulders
            drawOval(
                color = palette.body,
                topLeft = Offset(w * .20f, h * .62f),
                size = Size(w * .60f, h * .32f),
            )

            // head
            drawCircle(
                color = palette.face,
                radius = w * .22f,
                center = Offset(w * .50f, h * .39f),
            )

            // hair / mask shape varies by preset
            when (avatarKey) {
                "gold" -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 190f,
                        sweepAngle = 160f,
                        useCenter = true,
                        topLeft = Offset(w * .27f, h * .16f),
                        size = Size(w * .46f, h * .38f),
                    )
                }
                "dark" -> {
                    drawRect(
                        color = palette.accent,
                        topLeft = Offset(w * .30f, h * .17f),
                        size = Size(w * .40f, h * .15f),
                    )
                }
                "classic" -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(w * .28f, h * .17f),
                        size = Size(w * .44f, h * .32f),
                    )
                }
                "kids" -> {
                    drawCircle(palette.accent, w * .08f, Offset(w * .35f, h * .19f))
                    drawCircle(palette.accent, w * .08f, Offset(w * .65f, h * .19f))
                }
                else -> {
                    drawArc(
                        color = palette.accent,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = true,
                        topLeft = Offset(w * .29f, h * .18f),
                        size = Size(w * .42f, h * .30f),
                    )
                }
            }

            // eyes
            drawCircle(palette.eye, w * .022f, Offset(w * .43f, h * .40f))
            drawCircle(palette.eye, w * .022f, Offset(w * .57f, h * .40f))

            // smile / mouth
            drawArc(
                color = palette.eye,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(w * .42f, h * .43f),
                size = Size(w * .16f, h * .10f),
                style = Stroke(width = w * .018f),
            )
        }
    }
}

private data class AvatarPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val face: Color,
    val body: Color,
) {
    val accent: Color get() = body.copy(alpha = .95f)
    val eye: Color get() = backgroundBottom
}

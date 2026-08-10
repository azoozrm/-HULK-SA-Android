package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.ui.theme.LocalHulkColors

internal val PROFILE_AVATARS = listOf("ember", "nova", "sage", "orbit", "sunny")

@Composable
internal fun ProfileAvatarArtwork(
    avatarKey: String,
    displayName: String,
    size: Dp,
    highlighted: Boolean,
) {
    val colors = LocalHulkColors.current
    val palette = when (avatarKey) {
        "nova" -> listOf(Color(0xFF7D8BFF), Color(0xFF302D76))
        "sage" -> listOf(Color(0xFF83D6B2), Color(0xFF205E4B))
        "orbit" -> listOf(Color(0xFF9AA5B1), Color(0xFF29313A))
        "sunny" -> listOf(Color(0xFFFFD36A), Color(0xFFB96C14))
        else -> listOf(Color(0xFFFFA85C), Color(0xFF8E4C18))
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(palette))
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) colors.goldBright else Color.White.copy(alpha = .18f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (avatarKey) {
            "nova" -> AvatarFace(eyes = "•  •", mouth = "⌣", accent = "✦")
            "sage" -> AvatarFace(eyes = "◠  ◠", mouth = "ᴗ", accent = "")
            "orbit" -> AvatarFace(eyes = "•  •", mouth = "—", accent = "")
            "sunny" -> AvatarFace(eyes = "^  ^", mouth = "◡", accent = "")
            else -> AvatarFace(eyes = "•  •", mouth = "ᴗ", accent = "")
        }
    }
}

@Composable
private fun AvatarFace(
    eyes: String,
    mouth: String,
    accent: String,
) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = "$eyes\n$mouth",
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Black,
        )
        if (accent.isNotBlank()) {
            Text(
                text = accent,
                color = Color.White.copy(alpha = .9f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

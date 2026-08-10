package sa.hulksa.player.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import sa.hulksa.player.ui.components.ProfileAvatar

internal val PROFILE_AVATARS = listOf("ember", "nova", "sage", "orbit", "sunny")

@Composable
internal fun ProfileAvatarArtwork(
    avatarKey: String,
    displayName: String,
    size: Dp,
    highlighted: Boolean,
) {
    ProfileAvatar(
        avatarKey = avatarKey,
        modifier = Modifier,
        highlighted = highlighted,
    )
}

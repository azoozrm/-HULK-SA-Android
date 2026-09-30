package sa.hulksa.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

@Composable
fun UniversalPosterCard(
    item: ContentItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    if (item.type == ContentType.SERIES) {
        SeriesPosterCard(
            item = item,
            isFavorite = isFavorite,
            onClick = onClick,
            modifier = modifier,
            onLongClick = onLongClick,
            onFocused = onFocused,
        )
    } else {
        CompactPosterCard(
            item = item,
            isFavorite = isFavorite,
            onClick = onClick,
            modifier = modifier,
            onLongClick = onLongClick,
            onFocused = onFocused,
        )
    }
}

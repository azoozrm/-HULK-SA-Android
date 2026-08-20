package sa.hulksa.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.R
import sa.hulksa.player.data.SeriesCardMetadataStore
import sa.hulksa.player.data.SeriesCardTechnicalMetadata
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

@Composable
fun SeriesPosterCard(
    item: ContentItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    require(item.type == ContentType.SERIES)

    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    var metadata by remember(item.id) { mutableStateOf(SeriesCardTechnicalMetadata()) }

    LaunchedEffect(item.id, metadataStore) {
        metadata = metadataStore.metadata(item.id)
    }

    var focused by remember { mutableStateOf(false) }
    var artworkFailed by remember(item.posterUrl) { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.04f else 1f, label = "seriesPosterScale")
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (showFocused) 14.dp.toPx() else 0f
            }
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(
                if (showFocused) 3.dp else 0.dp,
                if (focused) colors.goldBright else Color.Transparent,
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (onLongClick == null || !event.nativeKeyEvent.isSeriesRemoteSelectKey()) {
                    false
                } else if (event.type == KeyEventType.KeyDown) {
                    if (
                        (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress) &&
                        !remoteLongPressHandled
                    ) {
                        remoteLongPressHandled = true
                        onLongClick()
                    }
                    true
                } else if (event.type == KeyEventType.KeyUp) {
                    if (!remoteLongPressHandled) onClick()
                    remoteLongPressHandled = false
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (!item.posterUrl.isNullOrBlank() && !artworkFailed) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.hulk_sa_logo),
                onError = { artworkFailed = true },
            )
        } else {
            BrandLogo(Modifier.fillMaxSize().padding(22.dp))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .42f to Color.Transparent,
                        .62f to Color.Black.copy(alpha = .28f),
                        .76f to Color.Black.copy(alpha = .64f),
                        .90f to Color.Black.copy(alpha = .90f),
                        1f to Color.Black.copy(alpha = .98f),
                    ),
                ),
        )

        metadata.quality
            ?.takeIf(String::isNotBlank)
            ?.let { quality ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SeriesMetadataBadge(
                        text = quality,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .padding(7.dp),
                        quality = true,
                    )
                }
            }

        if (isFavorite) {
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.TopRight)
                    .padding(7.dp)
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .78f))
                    .border(1.dp, Color.White.copy(alpha = .16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "★",
                    color = colors.goldBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = if (adaptiveUi.isTelevision) 13.sp else 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (adaptiveUi.isTelevision) 16.sp else 15.sp,
            )

            val rating = compactSeriesRating(item.rating)
            val seasonCount = metadata.seasonCount?.takeIf { it > 0 }
            if (rating != null || seasonCount != null) {
                Spacer(Modifier.height(5.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            rating?.let {
                                SeriesMetadataBadge(
                                    text = "★ $it",
                                    accentText = true,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            seasonCount?.let { SeriesSeasonBadge(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesMetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
    quality: Boolean = false,
    accentText: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .height(21.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = if (quality) .82f else .78f))
            .border(
                1.dp,
                if (quality) colors.goldBright.copy(alpha = .38f) else Color.White.copy(alpha = .22f),
                shape,
            )
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (accentText) colors.goldBright else Color.White,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun SeriesSeasonBadge(seasonCount: Int) {
    val shape = RoundedCornerShape(7.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .height(21.dp)
                .clip(shape)
                .background(Color.Black.copy(alpha = .78f))
                .border(1.dp, Color.White.copy(alpha = .22f), shape)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = String.format(Locale.US, "%d", seasonCount),
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = "موسم",
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

private fun compactSeriesRating(raw: String?): String? {
    val value = raw
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?: return null
    return String.format(Locale.US, "%.1f", value)
}

private fun AndroidKeyEvent.isSeriesRemoteSelectKey(): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_SPACE

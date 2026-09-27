package sa.hulksa.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.ViewModelProvider
import coil3.compose.AsyncImage
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.R
import sa.hulksa.player.data.HomeHeroMetadataStore
import sa.hulksa.player.data.SeriesCardMetadataStore
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

internal const val MEDIA_CARD_FOCUS_BORDER_WIDTH_DP = 3f

internal enum class MediaArtworkKind {
    POSTER,
    LIVE_LOGO,
}

internal data class MediaCardMetadata(
    val quality: String? = null,
    val durationMs: Long? = null,
    val seasonCount: Int? = null,
)

internal data class MediaCardFocusStyle(
    val borderWidthDp: Float,
    val scale: Float,
)

internal fun mediaArtworkKind(type: ContentType): MediaArtworkKind =
    if (type == ContentType.LIVE) MediaArtworkKind.LIVE_LOGO else MediaArtworkKind.POSTER

internal fun mediaCardFocusStyle(showFocused: Boolean): MediaCardFocusStyle =
    MediaCardFocusStyle(
        borderWidthDp = if (showFocused) MEDIA_CARD_FOCUS_BORDER_WIDTH_DP else 0f,
        scale = 1f,
    )

internal fun mediaQualityLabel(raw: String?): String? =
    raw?.trim()?.takeIf(String::isNotBlank)

internal fun mediaMetadataLine(item: ContentItem, metadata: MediaCardMetadata): String? {
    val parts = when (item.type) {
        ContentType.MOVIE -> listOfNotNull(
            formatMediaRating(item.rating),
            formatMediaDuration(metadata.durationMs),
        )
        ContentType.SERIES -> listOfNotNull(
            formatMediaRating(item.rating),
            formatMediaSeasonCount(metadata.seasonCount),
        )
        ContentType.LIVE -> listOfNotNull(
            formatMediaRating(item.rating),
            item.year?.trim()?.takeIf(String::isNotBlank),
        )
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun formatMediaRating(raw: String?): String? {
    val value = raw
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?: return null
    return String.format(Locale.US, "★ %.1f", value)
}

internal fun formatMediaDuration(durationMs: Long?): String? {
    val totalMinutes = durationMs
        ?.takeIf { it > 0L }
        ?.div(60_000L)
        ?: return null
    if (totalMinutes <= 0L) return null
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> String.format(Locale.US, "%dس %02dد", hours, minutes)
        hours > 0L -> String.format(Locale.US, "%dس", hours)
        else -> String.format(Locale.US, "%dد", minutes)
    }
}

internal fun formatMediaSeasonCount(count: Int?): String? {
    val seasons = count?.takeIf { it > 0 } ?: return null
    return when {
        seasons == 1 -> "موسم واحد"
        seasons == 2 -> "موسمان"
        seasons <= 10 -> String.format(Locale.US, "%d مواسم", seasons)
        else -> String.format(Locale.US, "%d موسم", seasons)
    }
}

@Composable
internal fun rememberMediaCardMetadata(item: ContentItem): MediaCardMetadata = when (item.type) {
    ContentType.MOVIE -> rememberMovieCardMetadata(item)
    ContentType.SERIES -> rememberSeriesCardMetadata(item)
    ContentType.LIVE -> MediaCardMetadata()
}

@Composable
private fun rememberMovieCardMetadata(item: ContentItem): MediaCardMetadata {
    val context = LocalContext.current
    val metadataStore = remember(context) { HomeHeroMetadataStore.get(context) }
    val metadataOwner = metadataStore.currentOwner()
    val viewModel = remember(context) {
        context.findViewModelStoreOwner()?.let { owner -> ViewModelProvider(owner)[HulkViewModel::class.java] }
    }
    var metadata by remember(item.type, item.id, metadataOwner) {
        val cached = metadataStore.cached(metadataOwner, item)
        mutableStateOf(MediaCardMetadata(quality = cached.quality, durationMs = cached.durationMs))
    }
    LaunchedEffect(item.type, item.id, metadataOwner, viewModel) {
        if (viewModel != null) {
            viewModel.prefetchMovieCardMetadata(item) { quality, durationMs ->
                metadata = MediaCardMetadata(quality = quality, durationMs = durationMs)
            }
        }
    }
    return metadata
}

@Composable
private fun rememberSeriesCardMetadata(item: ContentItem): MediaCardMetadata {
    val context = LocalContext.current
    val metadataStore = remember(context) { SeriesCardMetadataStore.get(context) }
    val metadataOwner = metadataStore.currentOwner()
    var metadata by remember(item.id, metadataOwner) {
        val cached = metadataStore.cached(metadataOwner, item.id)
        mutableStateOf(MediaCardMetadata(quality = cached.quality, seasonCount = cached.seasonCount))
    }
    LaunchedEffect(item.id, metadataOwner, metadataStore) {
        val owner = metadataOwner ?: return@LaunchedEffect
        val loaded = metadataStore.metadata(owner, item.id)
        metadataStore.publishIfCurrent(owner) {
            metadata = MediaCardMetadata(quality = loaded.quality, seasonCount = loaded.seasonCount)
        }
    }
    return metadata
}

@Composable
internal fun MediaCatalogCard(
    item: ContentItem,
    isFavorite: Boolean,
    metadata: MediaCardMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    var artworkFailed by remember(item.posterUrl) { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val focusStyle = mediaCardFocusStyle(showFocused)
    val artworkShape = RoundedCornerShape(12.dp)
    val quality = mediaQualityLabel(metadata.quality)
    val metadataText = mediaMetadataLine(item, metadata)
    val titleSize = if (adaptiveUi.isTelevision) 13.sp else 12.sp
    val titleLineHeight = if (adaptiveUi.isTelevision) 17.sp else 15.sp
    val metadataSize = if (adaptiveUi.isTelevision) 11.sp else 10.sp

    Column(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (onLongClick == null || !event.nativeKeyEvent.isMediaRemoteSelectKey()) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(artworkShape)
                .background(Color(0xFF15160F))
                .border(
                    focusStyle.borderWidthDp.dp,
                    if (focused) colors.goldBright else Color.Transparent,
                    artworkShape,
                ),
        ) {
            when (mediaArtworkKind(item.type)) {
                MediaArtworkKind.POSTER -> MediaPosterArtwork(item, artworkFailed) { artworkFailed = true }
                MediaArtworkKind.LIVE_LOGO -> MediaLiveLogoArtwork(item, artworkFailed) { artworkFailed = true }
            }
            quality?.let {
                MediaQualityLabel(
                    text = it,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .padding(7.dp),
                )
            }
            if (isFavorite) {
                MediaFavoriteMark(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .padding(7.dp),
                )
            }
        }
        Text(
            text = item.name,
            color = colors.text,
            fontWeight = FontWeight.Bold,
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, top = 7.dp, end = 2.dp),
        )
        Text(
            text = metadataText.orEmpty(),
            color = colors.textMuted,
            fontSize = metadataSize,
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, top = 3.dp, end = 2.dp),
        )
    }
}

@Composable
private fun MediaPosterArtwork(
    item: ContentItem,
    artworkFailed: Boolean,
    onArtworkError: () -> Unit,
) {
    if (!item.posterUrl.isNullOrBlank() && !artworkFailed) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_launcher_foreground),
            onError = { onArtworkError() },
        )
    } else {
        HulkFallbackArtwork(Modifier.fillMaxSize(), HulkArtworkSurface.POSTER)
    }
}

@Composable
private fun BoxScope.MediaLiveLogoArtwork(
    item: ContentItem,
    artworkFailed: Boolean,
    onArtworkError: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.posterUrl.isNullOrBlank() && !artworkFailed) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
                onError = { onArtworkError() },
            )
        } else {
            BrandLogo(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun MediaQualityLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = .62f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = colors.goldBright,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MediaFavoriteMark(
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
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

private fun AndroidKeyEvent.isMediaRemoteSelectKey(): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_SPACE

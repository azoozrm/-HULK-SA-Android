package sa.hulksa.player.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import coil3.compose.AsyncImage
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.R
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(R.drawable.hulk_sa_logo),
        contentDescription = "HULK SA",
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
fun BrandBadge(
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    listOf(colors.gold.copy(alpha = .18f), Color(0xFF050604)),
                ),
            )
            .border(1.dp, colors.gold.copy(alpha = .28f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BrandLogo(Modifier.fillMaxSize().padding(5.dp))
    }
}

@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
    outlined: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.035f else 1f, label = "buttonScale")
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        !enabled -> colors.surfaceRaised.copy(alpha = .5f)
        primary && showFocused -> colors.goldBright
        primary -> colors.gold
        showFocused -> Color(0xFF2A281B)
        outlined -> Color(0xFF151711)
        else -> Color(0xFF181914)
    }
    val textColor = if (primary) Color.Black else colors.text

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                width = when {
                    showFocused -> 2.dp
                    outlined -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    showFocused -> colors.goldBright
                    outlined -> colors.gold.copy(alpha = .42f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { e -> if(enabled && onLongClick!=null && e.type==KeyEventType.KeyDown && (e.key==Key.Enter||e.key==Key.DirectionCenter)){onLongClick();true}else false }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (compact) 12.dp else 21.dp,
                vertical = if (compact) 9.dp else 12.dp,
            ),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun NavRailButton(
    glyph: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(13.dp)
    val active = showFocused || selected
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.gold.copy(alpha = if (showFocused) .25f else .13f) else Color.Transparent)
            .border(
                if (showFocused) 2.dp else 0.dp,
                if (showFocused) colors.goldBright else Color.Transparent,
                shape,
            )
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (active) colors.gold else colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = if (active) Color.Black else colors.textMuted, fontSize = 13.sp)
        }
        Text(
            text = text,
            color = if (active) colors.text else colors.textMuted,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun HulkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(Color(0xFF12130F))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.gold else colors.line,
                shape = shape,
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, textAlign = TextAlign.Start),
        cursorBrush = Brush.verticalGradient(listOf(colors.gold, colors.gold)),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(label, color = colors.textMuted, fontSize = 14.sp)
                innerField()
            }
        },
    )
}

@Composable
fun ErrorNotice(message: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.danger.copy(alpha = .12f))
            .border(1.dp, colors.danger.copy(alpha = .45f), RoundedCornerShape(12.dp))
            .padding(13.dp),
    ) {
        Text(text = message, color = Color(0xFFFFB5B0), fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
fun LoadingRing(modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalHulkColors.current
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "loadingRotation",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            drawArc(
                color = colors.gold,
                startAngle = rotation,
                sweepAngle = 265f,
                useCenter = false,
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(label, color = colors.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun CompactPosterCard(
    item: ContentItem,
    isFavorite: Boolean,
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
    val scale by animateFloatAsState(if (showFocused) 1.04f else 1f, label = "posterScale")
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
                if (onLongClick == null || !event.nativeKeyEvent.isRemoteSelectKey()) {
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
                        .56f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .96f),
                    ),
                ),
        )
        if (isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(25.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .76f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("★", color = colors.goldBright, fontSize = 14.sp)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(9.dp),
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
            val meta = listOfNotNull(item.year, item.rating?.let { "★ $it" }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(meta, color = Color(0xFFE0D7B8), fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun PosterCard(
    item: ContentItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) = CompactPosterCard(item, isFavorite, onClick, modifier, onLongClick)

@Composable
fun HistoryCard(
    entry: HistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val layoutDirection = LocalLayoutDirection.current
    val viewModel = remember(context) {
        context.findViewModelStoreOwner()?.let { owner -> ViewModelProvider(owner)[HulkViewModel::class.java] }
    }
    val canDismiss = !entry.isLive
    var focused by remember(entry.key) { mutableStateOf(false) }
    var remoteLongPressHandled by remember(entry.key) { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val scale by animateFloatAsState(if (showFocused) 1.045f else 1f, label = "historyScale")
    val shape = RoundedCornerShape(12.dp)
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val primaryTitle = if (entry.streamKind.equals("series", ignoreCase = true)) {
        historyPrimaryTitle(entry)
    } else {
        entry.title
    }
    val metadata = historyMetadata(entry)
    val episodeTitle = if (adaptiveUi.isTelevision) null else usefulEpisodeTitle(entry)
    val dismissFromContinueWatching: (Boolean) -> Unit = { moveFocusFirst ->
        if (canDismiss && viewModel != null) {
            if (moveFocusFirst) {
                val forward = if (layoutDirection == LayoutDirection.Rtl) FocusDirection.Left else FocusDirection.Right
                val backward = if (layoutDirection == LayoutDirection.Rtl) FocusDirection.Right else FocusDirection.Left
                val movedToNeighbor = focusManager.moveFocus(forward) || focusManager.moveFocus(backward)
                if (!movedToNeighbor) {
                    focusManager.moveFocus(FocusDirection.Up)
                }
            }
            viewModel.removeHistoryEntry(entry.key)
        }
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(if (showFocused) 3.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (!canDismiss || !event.nativeKeyEvent.isRemoteSelectKey()) {
                    false
                } else if (event.type == KeyEventType.KeyDown) {
                    if (
                        (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress) &&
                        !remoteLongPressHandled
                    ) {
                        // Own the whole select-key gesture, but do not mutate the list while
                        // the key is still held. Removing on KEY_UP prevents repeats or the
                        // trailing release from activating the card that replaces this one.
                        remoteLongPressHandled = true
                    }
                    true
                } else if (event.type == KeyEventType.KeyUp) {
                    if (remoteLongPressHandled) {
                        dismissFromContinueWatching(true)
                    } else {
                        onClick()
                    }
                    remoteLongPressHandled = false
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = if (canDismiss) ({ dismissFromContinueWatching(false) }) else null,
            ),
    ) {
        if (!entry.posterUrl.isNullOrBlank()) {
            AsyncImage(entry.posterUrl, primaryTitle, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(76.dp))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .48f to Color.Black.copy(alpha = .08f),
                        1f to Color.Black.copy(alpha = .97f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(
                primaryTitle,
                color = Color.White,
                fontSize = if (adaptiveUi.isTelevision) 12.sp else 12.sp,
                lineHeight = if (adaptiveUi.isTelevision) 14.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (adaptiveUi.isTelevision) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                metadata,
                color = colors.goldBright,
                fontSize = if (adaptiveUi.isTelevision) 11.sp else 9.sp,
                lineHeight = if (adaptiveUi.isTelevision) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeTitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    episodeTitle,
                    color = Color.White.copy(alpha = .82f),
                    fontSize = if (adaptiveUi.isTelevision) 9.sp else 8.sp,
                    lineHeight = if (adaptiveUi.isTelevision) 11.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(if (adaptiveUi.isTelevision) 6.dp else 5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (adaptiveUi.isTelevision) 5.dp else 3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .28f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.goldBright),
                )
            }
        }
    }
}

@Composable
fun ChannelListItem(
    item: ContentItem,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    var remoteLongPressHandled by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val active = showFocused || selected
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (active) colors.gold.copy(alpha = .14f) else Color.Transparent)
            .border(if (showFocused) 2.dp else 0.dp, if (showFocused) colors.goldBright else Color.Transparent, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (onLongClick == null || !event.nativeKeyEvent.isRemoteSelectKey()) {
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
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ChannelLogo(item, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("● بث مباشر", color = if (active) colors.goldBright else colors.textMuted, fontSize = 10.sp)
        }
        if (isFavorite) {
            Text("★", color = colors.goldBright, fontSize = 16.sp)
        }
        Text("▶", color = if (active) colors.goldBright else colors.textMuted, fontSize = 14.sp)
    }
}

@Composable
fun ChannelLogo(
    item: ContentItem,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var imageFailed by remember(item.posterUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF0EEE7))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.posterUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                placeholder = painterResource(R.drawable.hulk_sa_logo),
                onError = { imageFailed = true },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF171912), Color(0xFF262315)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channelMonogram(item.name),
                    color = colors.goldBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun channelMonogram(name: String): String {
    val words = name
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    return when {
        words.size >= 2 -> words.take(2).joinToString("") { it.take(1) }.uppercase()
        words.isNotEmpty() -> words.first().take(2).uppercase()
        else -> "TV"
    }
}

private fun AndroidKeyEvent.isRemoteSelectKey(): Boolean =
    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_SPACE

private tailrec fun Context.findViewModelStoreOwner(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.findViewModelStoreOwner()
    else -> null
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = .46f))
            .border(1.dp, colors.line.copy(alpha = .72f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(text, color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

private fun historyPrimaryTitle(entry: HistoryEntry): String {
    if (!entry.streamKind.equals("series", ignoreCase = true)) return entry.title
    return entry.seriesTitle
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: entry.title
            .substringBefore(" · ")
            .trim()
            .takeIf(String::isNotBlank)
        ?: entry.title
}

private fun historyMetadata(entry: HistoryEntry): String {
    if (entry.isLive) return "بث مباشر"

    val elapsed = formatHistoryTime(entry.positionMs)
    val time = if (entry.durationMs > 0L) {
        "$elapsed / ${formatHistoryTime(entry.durationMs)}"
    } else {
        elapsed
    }

    return if (entry.streamKind.equals("series", ignoreCase = true)) {
        val (season, episodeNumber) = historyEpisodeNumbers(entry)
        val episode = listOfNotNull(
            season?.let { "S${latinInt(it)}" },
            episodeNumber?.let { "E${latinInt(it)}" },
        ).joinToString(" · ")
        listOf(episode.ifBlank { "مسلسل" }, time).joinToString(" · ")
    } else {
        "فيلم · $time"
    }
}

private fun historyEpisodeNumbers(entry: HistoryEntry): Pair<Int?, Int?> {
    var season = entry.season
    var episodeNumber = entry.episodeNumber
    if (!entry.streamKind.equals("series", ignoreCase = true) || (season != null && episodeNumber != null)) {
        return season to episodeNumber
    }

    val source = listOfNotNull(entry.episodeTitle, entry.title)
        .joinToString(" ")
        .toLatinDigits()
    val patterns = listOf(
        Regex("""\bS\s*(\d{1,3})\s*[-._· ]*E(?:P)?\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSeason\s*(\d{1,3})\s*[-._· ]*(?:Episode|Ep|E)\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE),
        Regex("""الموسم\s*(\d{1,3}).{0,12}الحلقة\s*(\d{1,4})""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,3})\s*[xX]\s*(\d{1,4})\b"""),
    )
    for (pattern in patterns) {
        val match = pattern.find(source) ?: continue
        if (season == null) season = match.groupValues.getOrNull(1)?.toIntOrNull()
        if (episodeNumber == null) episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull()
        if (season != null || episodeNumber != null) break
    }
    return season to episodeNumber
}

private fun usefulEpisodeTitle(entry: HistoryEntry): String? {
    if (!entry.streamKind.equals("series", ignoreCase = true)) return null
    val title = entry.episodeTitle
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: entry.title.substringAfter(" · ", "").trim().takeIf(String::isNotBlank)
        ?: return null
    val seriesTitle = historyPrimaryTitle(entry)
    if (title.equals(seriesTitle, ignoreCase = true)) return null
    val normalized = title.toLatinDigits()
    if (Regex("""^(?:episode|ep|الحلقة)\s*\d+$""", RegexOption.IGNORE_CASE).matches(normalized)) return null
    return title
}

private fun String.toLatinDigits(): String = buildString(length) {
    for (character in this@toLatinDigits) {
        append(
            when (character) {
                in '٠'..'٩' -> ('0'.code + character.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + character.code - '۰'.code).toChar()
                else -> character
            },
        )
    }
}

private fun latinInt(value: Int): String = String.format(Locale.US, "%d", value)

private fun formatHistoryTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return if (seconds >= 3600L) {
        String.format(
            Locale.US,
            "%d:%02d:%02d",
            seconds / 3600L,
            (seconds % 3600L) / 60L,
            seconds % 60L,
        )
    } else {
        String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }
}

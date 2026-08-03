package sa.hulksa.player.ui.components

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.R
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.HistoryEntry
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors

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
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.035f else 1f, label = "buttonScale")
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        !enabled -> colors.surfaceRaised.copy(alpha = .5f)
        primary && televisionFocused -> colors.goldBright
        primary -> colors.gold
        televisionFocused -> Color(0xFF2A281B)
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
                    televisionFocused || keyboardFocused -> 2.dp
                    outlined -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    televisionFocused || keyboardFocused -> colors.goldBright
                    outlined -> colors.gold.copy(alpha = .42f)
                    else -> Color.Transparent
                },
                shape = shape,
            )
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
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val shape = RoundedCornerShape(13.dp)
    val active = televisionFocused || selected
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) colors.gold.copy(alpha = if (televisionFocused) .25f else .13f) else Color.Transparent)
            .border(
                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
                shape,
            )
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
    compact: Boolean = false,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)

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
            .padding(horizontal = if (compact) 12.dp else 15.dp, vertical = if (compact) 8.dp else 13.dp),
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = if (compact) 13.sp else 15.sp, textAlign = TextAlign.Start),
        cursorBrush = Brush.verticalGradient(listOf(colors.gold, colors.gold)),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(label, color = colors.textMuted, fontSize = if (compact) 12.sp else 14.sp)
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
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.04f else 1f, label = "posterScale")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (televisionFocused) 14.dp.toPx() else 0f
            }
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(
                when {
                    televisionFocused -> 3.dp
                    keyboardFocused -> 2.dp
                    else -> 0.dp
                },
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
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
    var focused by remember { mutableStateOf(false) }
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val scale by animateFloatAsState(if (televisionFocused) 1.035f else 1f, label = "historyScale")
    val shape = RoundedCornerShape(12.dp)
    val progress = if (entry.durationMs > 0L) {
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Color(0xFF15160F))
            .border(
                when {
                    televisionFocused -> 3.dp
                    keyboardFocused -> 2.dp
                    else -> 0.dp
                },
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (!entry.posterUrl.isNullOrBlank()) {
            AsyncImage(entry.posterUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BrandLogo(Modifier.align(Alignment.Center).size(76.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.94f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(entry.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "استكمال المشاهدة  •  ${formatHistoryTime(entry.positionMs)}",
                color = colors.goldBright,
                fontSize = if (adaptiveUi.isTelevision) 12.sp else 9.sp,
                lineHeight = if (adaptiveUi.isTelevision) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(.25f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.goldBright))
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
    val televisionFocused = focused && adaptiveUi.showFocusHighlights
    val keyboardFocused = focused && adaptiveUi.showKeyboardFocusIndicator
    val active = televisionFocused || selected
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (active) colors.gold.copy(alpha = .14f) else Color.Transparent)
            .border(
                if (televisionFocused || keyboardFocused) 2.dp else 0.dp,
                if (televisionFocused || keyboardFocused) colors.goldBright else Color.Transparent,
                shape,
            )
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

private fun formatHistoryTime(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return if(s>=3600)"%d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) else "%02d:%02d".format(s/60,s%60)}

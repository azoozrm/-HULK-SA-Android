package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import sa.hulksa.player.data.EpgNowNext
import sa.hulksa.player.data.EpgProgramme
import sa.hulksa.player.data.LiveEpgUiState
import sa.hulksa.player.data.epgChannelId
import sa.hulksa.player.data.formatEpgLocalTime
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.ChannelLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.HulkColors
import sa.hulksa.player.ui.theme.LocalHulkColors

internal const val EPG_NOW_LABEL = "الآن"
internal const val EPG_NEXT_LABEL = "التالي"
internal const val EPG_GUIDE_ACTION_LABEL = "دليل البرامج"
internal const val EPG_GUIDE_TITLE = "دليل البرامج"
internal const val EPG_EMPTY_GUIDE_MESSAGE = "لا يوجد دليل متاح لهذه القناة"
internal const val EPG_GUIDE_SCOPE = "التوقيت المحلي • حتى 24 ساعة"

private val EPG_GUIDE_SURFACE = Color(0xF20A0B08)
private val EPG_GUIDE_ROW_SURFACE = Color(0xFF151711)

/**
 * Keeps «الآن»/«التالي» and the guide's current marker fresh while visible without a foreground
 * polling loop outside the composed EPG surface. A bounded 30-second tick only updates the clock.
 */
@Composable
internal fun rememberEpgNowEpochSeconds(): Long {
    var nowEpochSeconds by remember {
        mutableLongStateOf(System.currentTimeMillis() / 1_000L)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochSeconds = System.currentTimeMillis() / 1_000L
        }
    }
    return nowEpochSeconds
}

/**
 * The selected channel's two fixed Live information rows. Programme text stays on the physical
 * right and the local time range on the physical left, matching the accepted RTL Live anatomy.
 * The caller reserves this block's height so loading or changing EPG data never moves the artwork.
 */
@Composable
internal fun LiveEpgNowNextRows(
    nowNext: EpgNowNext,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EpgNowNextLine(
            label = EPG_NOW_LABEL,
            programme = nowNext.current,
            colors = colors,
        )
        EpgNowNextLine(
            label = EPG_NEXT_LABEL,
            programme = nowNext.next,
            colors = colors,
        )
    }
}

@Composable
private fun EpgNowNextLine(
    label: String,
    programme: EpgProgramme?,
    colors: HulkColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (programme == null) {
            Spacer(Modifier.weight(1f))
            return@Row
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = programme.title,
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = epgTimeRange(programme),
            color = colors.textMuted,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

/**
 * Shared left-side programme guide used by the live catalogue and the live player.
 *
 * On TV it is the accepted More-panel surface on the physical left, with the same dark surface,
 * theme colours and restrained 1.5dp gold focus edge. Compact windows use the same panel policy at
 * their available width. The header and close action stay fixed; only the programme list scrolls.
 */
@Composable
internal fun LiveEpgGuideOverlay(
    channel: ContentItem,
    state: LiveEpgUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    val layoutDirection = LocalLayoutDirection.current
    val panelAlignment = if (layoutDirection == LayoutDirection.Rtl) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (adaptiveUi.isTelevision) .42f else .55f)),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(
                        horizontal = if (adaptiveUi.isTelevision) 30.dp else 12.dp,
                        vertical = if (adaptiveUi.isTelevision) 18.dp else 10.dp,
                    ),
            ) {
                val metrics = livePlayerControlsMetrics(
                    screenWidthDp = adaptiveUi.screenWidthDp,
                    screenHeightDp = adaptiveUi.screenHeightDp,
                    remoteLayout = adaptiveUi.isTelevision,
                )
                val panelWidth = minOf(metrics.morePanelWidthDp.dp, maxWidth)
                LiveEpgGuidePanel(
                    channel = channel,
                    state = state,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .align(panelAlignment),
                )
            }
        }
    }
}

@Composable
private fun LiveEpgGuidePanel(
    channel: ContentItem,
    state: LiveEpgUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val nowEpochSeconds = rememberEpgNowEpochSeconds()
    val channelId = channel.epgChannelId()
    val programmes = state.programmesFor(channelId, nowEpochSeconds)
    val nowNext = state.nowNext(channelId, nowEpochSeconds)
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var expandedStart by remember(channel.id) { mutableStateOf<Long?>(null) }
    val focusStart = nowNext.current?.startEpochSeconds
        ?: programmes.firstOrNull()?.startEpochSeconds

    LaunchedEffect(channel.id) {
        val index = programmes.indexOfFirst { it.startEpochSeconds == focusStart }
        if (index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { initialFocus.requestFocus() }
        } else {
            withFrameNanos { }
            runCatching { closeFocus.requestFocus() }
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(EPG_GUIDE_SURFACE)
            .border(1.dp, colors.gold.copy(alpha = .45f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChannelLogo(channel, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = EPG_GUIDE_TITLE,
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = channel.name,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = EPG_GUIDE_SCOPE,
                    color = colors.textMuted,
                    fontSize = 9.sp,
                )
            }
            FocusButton(
                text = "اغلاق",
                onClick = onDismiss,
                modifier = Modifier
                    .width(88.dp)
                    .height(40.dp)
                    .focusRequester(closeFocus),
                primary = false,
                outlined = true,
                compact = true,
                trailingIcon = Icons.Rounded.Close,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line),
        )
        if (programmes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = EPG_EMPTY_GUIDE_MESSAGE,
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = programmes,
                    key = { programme -> programme.startEpochSeconds },
                ) { programme ->
                    EpgGuideRow(
                        programme = programme,
                        colors = colors,
                        isCurrent = programme == nowNext.current,
                        progress = epgProgressFraction(
                            startEpochSeconds = programme.startEpochSeconds,
                            stopEpochSeconds = programme.stopEpochSeconds,
                            nowEpochSeconds = nowEpochSeconds,
                        ),
                        expanded = expandedStart == programme.startEpochSeconds,
                        onToggle = {
                            expandedStart = if (expandedStart == programme.startEpochSeconds) {
                                null
                            } else {
                                programme.startEpochSeconds
                            }
                        },
                        modifier = if (programme.startEpochSeconds == focusStart) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgGuideRow(
    programme: EpgProgramme,
    colors: HulkColors,
    isCurrent: Boolean,
    progress: Float?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember(programme.startEpochSeconds) { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(11.dp)
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoViewRequester.bringIntoView() }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    isCurrent -> colors.gold.copy(alpha = .08f)
                    else -> EPG_GUIDE_ROW_SURFACE
                },
            )
            .border(
                width = if (showFocused) 1.5.dp else 1.dp,
                color = if (showFocused) colors.goldBright else Color.White.copy(alpha = .06f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.gold)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = EPG_NOW_LABEL,
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = programme.title,
                        color = colors.text,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (expanded) {
                        epgTimeRange(programme)
                    } else {
                        formatEpgLocalTime(programme.startEpochSeconds)
                    },
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                if (expanded) {
                    Text(
                        text = epgDurationLabel(
                            programme.startEpochSeconds,
                            programme.stopEpochSeconds,
                        ),
                        color = colors.gold,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (isCurrent && progress != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.gold.copy(alpha = .20f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(colors.gold),
                )
            }
        }
    }
}

/** Valid progress fraction only while now is inside a positive programme range. */
internal fun epgProgressFraction(
    startEpochSeconds: Long,
    stopEpochSeconds: Long,
    nowEpochSeconds: Long,
): Float? {
    if (stopEpochSeconds <= startEpochSeconds) return null
    if (nowEpochSeconds < startEpochSeconds || nowEpochSeconds >= stopEpochSeconds) return null
    return (
        (nowEpochSeconds - startEpochSeconds).toDouble() /
            (stopEpochSeconds - startEpochSeconds).toDouble()
        ).toFloat().coerceIn(0f, 1f)
}

/** Compact Arabic duration for the expanded guide row, using the real programme range only. */
internal fun epgDurationLabel(startEpochSeconds: Long, stopEpochSeconds: Long): String {
    val totalMinutes = ((stopEpochSeconds - startEpochSeconds).coerceAtLeast(0L) / 60L).toInt()
    if (totalMinutes <= 0) return "أقل من دقيقة"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes د"
        minutes == 0 -> "$hours س"
        else -> "$hours س $minutes د"
    }
}

private fun epgTimeRange(programme: EpgProgramme): String =
    "${formatEpgLocalTime(programme.startEpochSeconds)} - " +
        formatEpgLocalTime(programme.stopEpochSeconds)

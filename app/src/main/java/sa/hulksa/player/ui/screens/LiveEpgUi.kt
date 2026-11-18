package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import sa.hulksa.player.data.EpgNowNext
import sa.hulksa.player.data.EpgProgramme
import sa.hulksa.player.data.LiveEpgUiState
import sa.hulksa.player.data.formatEpgLocalTime
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors

internal const val EPG_NOW_LABEL = "الآن"
internal const val EPG_NEXT_LABEL = "التالي"
internal const val EPG_GUIDE_ACTION_LABEL = "دليل البرامج"
internal const val EPG_GUIDE_TITLE = "دليل البرامج"
internal const val EPG_EMPTY_GUIDE_MESSAGE = "لا يوجد دليل متاح لهذه القناة"
internal const val EPG_GUIDE_SCOPE = "التوقيت المحلي • حتى 24 ساعة"

/**
 * Keeps «الآن»/«التالي» and the guide's current marker fresh while visible without a foreground
 * polling loop outside the composed EPG surface. A bounded 30-second tick only updates the clock.
 */
@Composable
internal fun rememberEpgNowEpochSeconds(): Long {
    var nowEpochSeconds by remember {
        mutableStateOf(System.currentTimeMillis() / 1_000L)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochSeconds = System.currentTimeMillis() / 1_000L
        }
    }
    return nowEpochSeconds
}

/** The selected channel's real Now/Next values; absent when EPG is disabled or the channel has none. */
@Composable
internal fun LiveEpgNowNextBlock(
    nowNext: EpgNowNext,
    modifier: Modifier = Modifier,
) {
    if (nowNext.isEmpty) return
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        nowNext.current?.let { programme ->
            EpgNowNextRow(label = EPG_NOW_LABEL, programme = programme, colors = colors)
        }
        nowNext.next?.let { programme ->
            EpgNowNextRow(label = EPG_NEXT_LABEL, programme = programme, colors = colors)
        }
    }
}

@Composable
private fun EpgNowNextRow(
    label: String,
    programme: EpgProgramme,
    colors: sa.hulksa.player.ui.theme.HulkColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = colors.gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = epgTimeRange(programme),
                color = colors.textMuted,
                fontSize = 9.sp,
            )
        }
        Text(
            text = programme.title,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Centered bounded 24-hour guide.
 *
 * Uses the shared dark/bounded dialog family, keeps the physical RTL anatomy (title and item text
 * at the right, time/current badge at the left), exposes one TV focus target per programme row and
 * leaves Back to the dialog's single dismiss path. No 7-day claim and no programme fields the
 * provider does not supply (category/icon/episode) are shown.
 */
@Composable
internal fun LiveEpgGuideDialog(
    channelName: String,
    channelId: String?,
    state: LiveEpgUiState,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val nowEpochSeconds = rememberEpgNowEpochSeconds()
    val programmes = state.programmesFor(channelId, nowEpochSeconds)
    val nowNext = state.nowNext(channelId, nowEpochSeconds)
    val listState = rememberLazyListState()
    val firstRowFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching {
            if (programmes.isEmpty()) closeFocus.requestFocus() else firstRowFocus.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = if (adaptiveUi.isTelevision) {
                (maxWidth * 0.52f).coerceIn(380.dp, 640.dp)
            } else {
                (maxWidth * 0.92f).coerceIn(280.dp, 620.dp)
            }
            val dialogHeight = (maxHeight * 0.86f).coerceAtLeast(220.dp)
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = dialogHeight)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.line, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = EPG_GUIDE_TITLE,
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = channelName,
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
                Spacer(Modifier.height(10.dp))
                if (programmes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .padding(vertical = 18.dp),
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
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = programmes,
                            key = { programme -> programme.startEpochSeconds },
                        ) { programme ->
                            EpgGuideRow(
                                programme = programme,
                                isCurrent = programme == nowNext.current,
                                modifier = if (programme == programmes.first()) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FocusButton(
                    text = "تم",
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(min = 120.dp)
                        .then(
                            if (programmes.isEmpty()) Modifier.focusRequester(closeFocus) else Modifier,
                        ),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun EpgGuideRow(
    programme: EpgProgramme,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember(programme.startEpochSeconds) { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(11.dp)
    LaunchedEffect(focused) {
        if (focused) runCatching { bringIntoViewRequester.bringIntoView() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    isCurrent -> colors.gold.copy(alpha = .10f)
                    else -> Color(0xFF151711)
                },
            )
            .border(
                width = if (showFocused) 1.5.dp else 1.dp,
                color = if (showFocused) colors.goldBright else Color.White.copy(alpha = .06f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = programme.title,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = epgTimeRange(programme),
                color = colors.textMuted,
                fontSize = 10.sp,
            )
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.gold)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = EPG_NOW_LABEL,
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun epgTimeRange(programme: EpgProgramme): String =
    "${formatEpgLocalTime(programme.startEpochSeconds)} - " +
        formatEpgLocalTime(programme.stopEpochSeconds)

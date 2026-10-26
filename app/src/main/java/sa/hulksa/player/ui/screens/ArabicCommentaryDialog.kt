package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.ErrorNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Owner-facing opt-in surface for Arabic commentary over the current Live channel.
 *
 * The dialog is a real modal window, so D-pad focus cannot leak to the player behind it. Closing
 * always returns to the More trigger through the owning player surface.
 */
@Composable
internal fun ArabicCommentaryDialog(
    catalog: Catalog?,
    currentStreamId: Int,
    state: ArabicCommentaryState,
    accountNotice: String?,
    onSelectChannel: (ContentItem) -> Unit,
    onStepSelected: (Long) -> Unit,
    onAdjust: (ArabicCommentarySymptom) -> Unit,
    onReset: () -> Unit,
    onReturnToOriginal: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val expanded = adaptiveUi.isTelevision || adaptiveUi.screenWidthDp >= 720
    val connectionNotice = state.connectionNotice ?: accountNotice
    var searchQuery by remember { mutableStateOf("") }
    var pickerVisible by remember(state.selectedChannel?.id) {
        mutableStateOf(state.selectedChannel == null)
    }

    val channels = remember(catalog, searchQuery, currentStreamId) {
        val query = searchQuery.trim()
        catalog?.items.orEmpty()
            .filter { it.type == ContentType.LIVE && it.id != currentStreamId }
            .filter { channel ->
                query.isBlank() ||
                    channel.name.contains(query, ignoreCase = true) ||
                    channel.id.toString().contains(query)
            }
    }

    val closeFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val earlyFocus = remember { FocusRequester() }
    val lateFocus = remember { FocusRequester() }
    val resetFocus = remember { FocusRequester() }
    val returnFocus = remember { FocusRequester() }
    val changeChannelFocus = remember { FocusRequester() }
    val stepFineFocus = remember { FocusRequester() }
    val stepCoarseFocus = remember { FocusRequester() }

    LaunchedEffect(
        pickerVisible,
        state.phase,
        state.selectedChannel?.id,
        state.sessionAvailable,
        channels.size,
        channels.firstOrNull()?.id,
    ) {
        withFrameNanos { }
        val target = when {
            !state.sessionAvailable -> closeFocus
            pickerVisible && channels.isNotEmpty() -> firstChannelFocus
            pickerVisible -> searchFocus
            state.phase == ArabicCommentaryPhase.ERROR ||
                state.phase == ArabicCommentaryPhase.INTERRUPTED -> retryFocus
            state.phase == ArabicCommentaryPhase.ACTIVE -> earlyFocus
            state.phase == ArabicCommentaryPhase.PREPARING -> returnFocus
            else -> closeFocus
        }
        runCatching { target.requestFocus() }
    }

    Dialog(
        onDismissRequest = {
            if (pickerVisible && state.selectedChannel != null) {
                pickerVisible = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = if (expanded) 28.dp else 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = if (expanded) {
                maxWidth.coerceAtMost(620.dp)
            } else {
                maxWidth
            }
            val dialogHeight = (maxHeight * if (expanded) .88f else .92f).coerceAtLeast(240.dp)
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .widthIn(max = 620.dp)
                    .height(dialogHeight)
                    .focusGroup()
                    .clip(shape)
                    .background(Color(0xF2141510))
                    .border(1.dp, colors.gold.copy(alpha = .55f), shape)
                    .padding(horizontal = if (expanded) 20.dp else 14.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "التعليق العربي",
                            color = colors.goldBright,
                            fontSize = if (expanded) 21.sp else 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "صوت عربي من قناة مباشرة اخرى فوق صورة القناة الحالية",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FocusButton(
                        text = "اغلاق",
                        onClick = onDismiss,
                        primary = false,
                        outlined = true,
                        compact = true,
                        scaleOnFocus = false,
                        modifier = Modifier
                            .focusRequester(closeFocus)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (state.sessionAvailable) {
                    connectionNotice?.let { notice ->
                        Text(
                            text = notice,
                            color = if (notice.startsWith("تنبيه")) colors.gold else colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (state.sessionAvailable) {
                    state.message?.let { message ->
                        Text(
                            text = message,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (!state.sessionAvailable) {
                    Column(modifier = Modifier.weight(1f)) {
                        ErrorNotice(
                            message = "انتهت جلسة الحساب. اخرج من المشغل ثم سجل الدخول من جديد.",
                        )
                    }
                } else if (pickerVisible) {
                    ChannelPickerContent(
                        channels = channels,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        searchFocus = searchFocus,
                        firstChannelFocus = firstChannelFocus,
                        onSelectChannel = { channel ->
                            pickerVisible = false
                            onSelectChannel(channel)
                        },
                        expanded = expanded,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.selectedChannel != null) {
                        Spacer(Modifier.height(8.dp))
                        CommentaryDirectRow(
                            text = "رجوع للتحكم",
                            icon = Icons.Rounded.RecordVoiceOver,
                            onClick = { pickerVisible = false },
                            focusRequester = changeChannelFocus,
                        )
                    }
                } else {
                    CommentaryControlContent(
                        state = state,
                        onStepSelected = onStepSelected,
                        onAdjust = onAdjust,
                        onReset = onReset,
                        onReturnToOriginal = onReturnToOriginal,
                        onRetry = onRetry,
                        onChangeChannel = { pickerVisible = true },
                        retryFocus = retryFocus,
                        earlyFocus = earlyFocus,
                        lateFocus = lateFocus,
                        resetFocus = resetFocus,
                        returnFocus = returnFocus,
                        changeChannelFocus = changeChannelFocus,
                        stepFineFocus = stepFineFocus,
                        stepCoarseFocus = stepCoarseFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentaryControlContent(
    state: ArabicCommentaryState,
    onStepSelected: (Long) -> Unit,
    onAdjust: (ArabicCommentarySymptom) -> Unit,
    onReset: () -> Unit,
    onReturnToOriginal: () -> Unit,
    onRetry: () -> Unit,
    onChangeChannel: () -> Unit,
    retryFocus: FocusRequester,
    earlyFocus: FocusRequester,
    lateFocus: FocusRequester,
    resetFocus: FocusRequester,
    returnFocus: FocusRequester,
    changeChannelFocus: FocusRequester,
    stepFineFocus: FocusRequester,
    stepCoarseFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val channelName = state.selectedChannel?.name ?: "قناة التعليق"
    val status = when (state.phase) {
        ArabicCommentaryPhase.PREPARING -> "جاري التحضير…"
        ArabicCommentaryPhase.ACTIVE -> if (state.commentaryAudible) {
            "التعليق مسموع"
        } else {
            "جاهز"
        }
        ArabicCommentaryPhase.INTERRUPTED -> "متوقف"
        ArabicCommentaryPhase.ERROR -> "تعذر التشغيل"
        ArabicCommentaryPhase.INACTIVE -> "غير مفعل"
    }
    val stepFineSelected = state.stepMs == ARABIC_COMMENTARY_FINE_STEP_MS
    val stepCoarseSelected = state.stepMs == ARABIC_COMMENTARY_COARSE_STEP_MS
    val remainingEarly = arabicCommentaryRemainingMs(
        state.delays,
        ArabicCommentarySymptom.COMMENTARY_EARLY,
    )
    val remainingLate = arabicCommentaryRemainingMs(
        state.delays,
        ArabicCommentarySymptom.COMMENTARY_LATE,
    )
    val active = state.phase == ArabicCommentaryPhase.ACTIVE
    val canAdjustEarly = active && state.commentaryAudible && !state.busy &&
        remainingEarly >= state.stepMs
    val canAdjustLate = active && state.commentaryAudible && !state.busy &&
        remainingLate >= state.stepMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1B1A0E))
                .border(1.dp, colors.gold.copy(alpha = .30f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "قناة التعليق",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
                Text(
                    text = channelName,
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = status,
                color = if (state.phase == ArabicCommentaryPhase.ACTIVE) colors.goldBright else colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        if (state.phase == ArabicCommentaryPhase.PREPARING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LoadingRing(
                    label = "الصوت الاصلي يعمل حتى يجهز التعليق",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.error?.let { error ->
            ErrorNotice(message = error)
        }
        if (state.canRetry && (state.phase == ArabicCommentaryPhase.ERROR ||
                state.phase == ArabicCommentaryPhase.INTERRUPTED)
        ) {
            FocusButton(
                text = "اعادة المحاولة",
                onClick = onRetry,
                leadingIcon = Icons.Rounded.Refresh,
                scaleOnFocus = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .focusRequester(retryFocus),
            )
        }

        if (active) {
            Text(
                text = "التأخير الحالي",
                color = colors.gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = arabicCommentaryDelaySummary(state.delays),
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
            Text(
                text = "المدى المدعوم حتى 10 ث لكل مصدر حسب دعم القناة. يتم تأخير احد المصدرين " +
                    "مؤقتا ثم متابعة التشغيل. ستلاحظ توقفا قصيرا بمقدار الخطوة، وقد يختلف قليلا " +
                    "حسب المخزن المؤقت.",
                color = colors.textMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp,
            )

            Text(
                text = "الخطوة",
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepChip(
                    label = "0.1 ث",
                    selected = stepFineSelected,
                    enabled = !state.adjusting,
                    onClick = { onStepSelected(ARABIC_COMMENTARY_FINE_STEP_MS) },
                    focusRequester = stepFineFocus,
                    modifier = Modifier.weight(1f),
                )
                StepChip(
                    label = "1 ث",
                    selected = stepCoarseSelected,
                    enabled = !state.adjusting,
                    onClick = { onStepSelected(ARABIC_COMMENTARY_COARSE_STEP_MS) },
                    focusRequester = stepCoarseFocus,
                    modifier = Modifier.weight(1f),
                )
            }

            CommentaryActionRow(
                title = "التعليق يسبق الصورة",
                subtitle = if (remainingEarly < state.stepMs) {
                    "وصل تأخير التعليق الى الحد الاقصى"
                } else {
                    "زيادة تأخير صوت التعليق"
                },
                icon = Icons.Rounded.RecordVoiceOver,
                enabled = canAdjustEarly,
                onClick = { onAdjust(ArabicCommentarySymptom.COMMENTARY_EARLY) },
                focusRequester = earlyFocus,
            )
            CommentaryActionRow(
                title = "التعليق متأخر عن الصورة",
                subtitle = if (remainingLate < state.stepMs) {
                    "وصل تأخير الصورة الى الحد الاقصى"
                } else {
                    "زيادة تأخير صورة القناة الاجنبية"
                },
                icon = Icons.Rounded.Tv,
                enabled = canAdjustLate,
                onClick = { onAdjust(ArabicCommentarySymptom.COMMENTARY_LATE) },
                focusRequester = lateFocus,
            )
        }

        Spacer(Modifier.height(2.dp))
        CommentaryDirectRow(
            text = "اعادة الضبط",
            icon = Icons.Rounded.Replay,
            enabled = active && !state.busy && state.delays.hasDelay,
            onClick = onReset,
            focusRequester = resetFocus,
        )
        CommentaryDirectRow(
            text = "العودة للصوت الاصلي",
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            enabled = state.phase != ArabicCommentaryPhase.INACTIVE && !state.adjusting,
            onClick = onReturnToOriginal,
            focusRequester = returnFocus,
        )
        CommentaryDirectRow(
            text = "تغيير قناة التعليق",
            icon = Icons.Rounded.Search,
            enabled = !state.adjusting,
            onClick = onChangeChannel,
            focusRequester = changeChannelFocus,
        )
    }
}

@Composable
private fun ChannelPickerContent(
    channels: List<ContentItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocus: FocusRequester,
    firstChannelFocus: FocusRequester,
    onSelectChannel: (ContentItem) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "اختر قناة التعليق من قنوات الحساب",
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(7.dp))
        HulkTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = "بحث عن قناة",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus),
            leadingIcon = Icons.Rounded.Search,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${channels.size} قناة",
            color = colors.textMuted,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(5.dp))
        when {
            channels.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "لا توجد قناة مطابقة",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(channels, key = ContentItem::id) { channel ->
                    CommentaryChannelRow(
                        channel = channel,
                        onClick = { onSelectChannel(channel) },
                        focusRequester = if (channel == channels.first()) firstChannelFocus else null,
                        expanded = expanded,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentaryChannelRow(
    channel: ContentItem,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    expanded: Boolean,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember(channel.id) { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (expanded) 48.dp else 46.dp)
            .clip(shape)
            .background(if (showFocused) colors.gold.copy(alpha = .14f) else Color(0xFF14150F))
            .border(
                width = if (showFocused) 1.5.dp else 1.dp,
                color = if (showFocused) colors.gold else Color.White.copy(alpha = .08f),
                shape = shape,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = channel.name }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.LiveTv,
            contentDescription = null,
            tint = if (showFocused) colors.goldBright else colors.gold,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = channel.name,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (showFocused) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.goldBright,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    FocusButton(
        text = label,
        onClick = onClick,
        enabled = enabled,
        primary = selected,
        outlined = !selected,
        compact = true,
        scaleOnFocus = false,
        trailingIcon = if (selected) Icons.Rounded.Check else null,
        modifier = modifier.focusRequester(focusRequester),
    )
}

@Composable
private fun CommentaryActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    showFocused -> colors.gold.copy(alpha = .14f)
                    else -> Color(0xFF181914)
                },
            )
            .border(
                width = if (showFocused) 1.5.dp else 1.dp,
                color = if (showFocused) colors.gold else Color.White.copy(alpha = .09f),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = enabled
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) colors.text else colors.textMuted.copy(alpha = .55f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = colors.textMuted.copy(alpha = if (enabled) 1f else .55f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                if (showFocused) colors.goldBright else colors.gold
            } else {
                colors.textMuted.copy(alpha = .45f)
            },
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun CommentaryDirectRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    enabled: Boolean = true,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (showFocused) colors.gold.copy(alpha = .12f) else Color.Transparent)
            .border(
                width = if (showFocused) 1.5.dp else 0.dp,
                color = if (showFocused) colors.gold else Color.Transparent,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = enabled
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.textMuted.copy(alpha = .5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.gold else colors.textMuted.copy(alpha = .45f),
            modifier = Modifier.size(18.dp),
        )
    }
}

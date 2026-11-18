package sa.hulksa.player.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sa.hulksa.player.model.Catalog
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.EntryNotice
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.theme.LocalHulkColors

private enum class CommentaryPage { CONTROL, CHANNELS, SOURCES }

@Composable
internal fun ArabicCommentaryDialog(
    catalog: Catalog?,
    currentStreamId: Int,
    state: ArabicCommentaryState,
    onSelectChannel: (ArabicCommentaryFeed) -> Unit,
    onSelectSource: (ArabicCommentaryCatalogSource) -> Unit,
    onStepSelected: (Long) -> Unit,
    onAdjust: (ArabicCommentarySymptom) -> Unit,
    onReset: () -> Unit,
    onReturnToOriginal: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val television = LocalAdaptiveUi.current.isTelevision
    var page by remember { mutableStateOf(if (state.feed == null) CommentaryPage.CHANNELS else CommentaryPage.CONTROL) }
    var returnFrom by remember { mutableStateOf<CommentaryPage?>(null) }
    val visiblePage = if (state.sessionAvailable) page else CommentaryPage.CONTROL
    val phase = state.phase.takeIf { visiblePage == CommentaryPage.CONTROL }
    var focusPending by remember(visiblePage, phase, state.sessionAvailable) { mutableStateOf(true) }
    val focusPlaced = {
        focusPending = false
        returnFrom = null
    }

    fun goBack() {
        if (visiblePage == CommentaryPage.CONTROL || state.feed == null) {
            onDismiss()
        } else {
            returnFrom = visiblePage
            page = CommentaryPage.CONTROL
        }
    }

    Dialog(
        onDismissRequest = ::goBack,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = if (television) 24.dp else 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(12.dp)
                val controlFocus = arabicCommentaryFocusTarget(
                    sessionAvailable = state.sessionAvailable,
                    pickerVisible = false,
                    channelsAvailable = false,
                    phase = state.phase,
                    deliberatePickerEntry = false,
                    canRetry = state.canRetry,
                )
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            when (key.keyCode) {
                                AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                    if (event.type == KeyEventType.KeyDown && key.repeatCount == 0) goBack()
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                                AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                                AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                                AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                                AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                AndroidKeyEvent.KEYCODE_MEDIA_STOP,
                                AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                                -> true
                                else -> false
                            }
                        }
                        .clip(shape)
                        .background(Color(0xF2141510))
                        .border(1.dp, colors.gold.copy(alpha = .42f), shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "التعليق العربي",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.goldBright,
                        )
                        FocusButton(
                            text = "اغلاق",
                            onClick = onDismiss,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .commentaryEntryFocus(
                                    focusPending && visiblePage == CommentaryPage.CONTROL &&
                                        (controlFocus == ArabicCommentaryFocusTarget.CLOSE || state.feed == null),
                                    focusPlaced,
                                ),
                            primary = false,
                            compact = true,
                            outlined = true,
                            scaleOnFocus = false,
                        )
                    }

                    when (visiblePage) {
                        CommentaryPage.CONTROL -> {
                            val feed = state.feed
                            val canChangeSource = feed != null && feed.sources.size > 1
                            CommentaryControls(
                                state = state,
                                modifier = Modifier.weight(1f, fill = false),
                                earlyFocus = focusPending && returnFrom == null &&
                                    controlFocus == ArabicCommentaryFocusTarget.EARLY,
                                retryFocus = focusPending && returnFrom == null &&
                                    controlFocus == ArabicCommentaryFocusTarget.RETRY,
                                sourceFocus = focusPending && returnFrom == CommentaryPage.SOURCES && canChangeSource,
                                onFocusPlaced = focusPlaced,
                                onChangeSource = { returnFrom = null; page = CommentaryPage.SOURCES },
                                onStepSelected = onStepSelected,
                                onAdjust = onAdjust,
                                onReset = onReset,
                                onRetry = { returnFrom = null; onRetry() },
                            )
                            if (state.sessionAvailable && feed != null) {
                                CommentaryDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CommentaryFooterButton(
                                        text = "تغيير قناة التعليق",
                                        onClick = { returnFrom = null; page = CommentaryPage.CHANNELS },
                                        modifier = Modifier.weight(1f).commentaryEntryFocus(
                                            focusPending && (returnFrom == CommentaryPage.CHANNELS ||
                                                returnFrom == CommentaryPage.SOURCES && !canChangeSource),
                                            focusPlaced,
                                        ),
                                    )
                                    CommentaryFooterButton(
                                        text = "العودة للصوت الاصلي",
                                        onClick = onReturnToOriginal,
                                        modifier = Modifier.weight(1f).commentaryEntryFocus(
                                            focusPending && returnFrom == null &&
                                                controlFocus == ArabicCommentaryFocusTarget.RETURN,
                                            focusPlaced,
                                        ),
                                    )
                                }
                            }
                        }
                        CommentaryPage.CHANNELS -> {
                            CommentaryChannelPicker(
                                catalog = catalog,
                                currentStreamId = currentStreamId,
                                state = state,
                                focusPending = focusPending,
                                onFocusPlaced = focusPlaced,
                                modifier = Modifier.weight(1f, fill = false),
                                onSelect = { feed ->
                                    returnFrom = null
                                    page = CommentaryPage.CONTROL
                                    onSelectChannel(feed)
                                },
                            )
                        }
                        CommentaryPage.SOURCES -> {
                            state.feed?.let { feed ->
                                CommentarySourcePicker(
                                    feed = feed,
                                    selected = state.source,
                                    focusPending = focusPending,
                                    onFocusPlaced = focusPlaced,
                                    modifier = Modifier.weight(1f, fill = false),
                                    onSelect = { source ->
                                        returnFrom = CommentaryPage.SOURCES
                                        page = CommentaryPage.CONTROL
                                        onSelectSource(source)
                                    },
                                )
                            }
                        }
                    }

                    if (visiblePage != CommentaryPage.CONTROL && state.feed != null) {
                        CommentaryDivider()
                        CommentaryFooterButton(
                            text = "رجوع للتحكم",
                            onClick = ::goBack,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentaryControls(
    state: ArabicCommentaryState,
    modifier: Modifier,
    earlyFocus: Boolean,
    retryFocus: Boolean,
    sourceFocus: Boolean,
    onFocusPlaced: () -> Unit,
    onChangeSource: () -> Unit,
    onStepSelected: (Long) -> Unit,
    onAdjust: (ArabicCommentarySymptom) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!state.sessionAvailable) {
            EntryNotice(message = "انتهت جلسة الحساب", compact = true)
            return@Column
        }

        state.feed?.let { feed ->
            Text(
                text = feed.key.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            state.source?.let { source ->
                CommentarySourceName(source)
            }
        }

        val status = when {
            state.phase == ArabicCommentaryPhase.ERROR -> null
            state.phase == ArabicCommentaryPhase.INTERRUPTED -> state.message ?: "التعليق متوقف"
            state.phase == ArabicCommentaryPhase.PREPARING && state.recovering -> "جاري تجربة مصدر بديل"
            state.phase == ArabicCommentaryPhase.PREPARING -> "جاري تجهيز التعليق"
            state.adjusting -> "جاري ضبط التزامن"
            state.buffering -> "جاري استئناف التعليق"
            state.commentaryAudible && state.outputMuted -> "التعليق مفعل · الصوت مكتوم"
            state.commentaryAudible -> "التعليق مفعل"
            state.phase == ArabicCommentaryPhase.ACTIVE -> "التعليق جاهز"
            else -> "الصوت الاصلي"
        }
        if (status != null) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.phase == ArabicCommentaryPhase.PREPARING || state.buffering || state.adjusting) {
                    LoadingRing(Modifier.size(16.dp))
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.goldBright)
            }
        }
        state.error?.let { EntryNotice(message = it, compact = true) }
        if (state.phase != ArabicCommentaryPhase.INTERRUPTED) {
            state.message?.let { CommentarySecondaryText(it) }
        }
        if ((state.feed?.sources?.size ?: 0) > 1) {
            CommentaryRow(
                title = "تغيير المصدر",
                icon = Icons.Rounded.LiveTv,
                openChild = true,
                modifier = Modifier.commentaryEntryFocus(sourceFocus, onFocusPlaced),
                onClick = onChangeSource,
            )
        }

        if (state.phase == ArabicCommentaryPhase.ACTIVE) {
            CommentaryDivider()
            Text(
                text = "ضبط التزامن",
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            CommentaryAlignmentStatus(state.alignment)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (step in listOf(ARABIC_COMMENTARY_FINE_STEP_MS, ARABIC_COMMENTARY_COARSE_STEP_MS)) {
                    CommentaryRow(
                        title = arabicCommentaryStepLabel(step),
                        selectedOption = state.stepMs == step,
                        numeric = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onStepSelected(step) },
                    )
                }
            }
            CommentaryRow(
                title = "التعليق يسبق الصورة",
                icon = Icons.Rounded.RecordVoiceOver,
                modifier = Modifier.commentaryEntryFocus(earlyFocus, onFocusPlaced),
                onClick = { onAdjust(ArabicCommentarySymptom.COMMENTARY_EARLY) },
            )
            CommentaryRow(
                title = "التعليق متاخر عن الصورة",
                icon = Icons.Rounded.LiveTv,
                onClick = { onAdjust(ArabicCommentarySymptom.COMMENTARY_LATE) },
            )
            CommentaryRow(
                title = "اعادة الضبط",
                icon = Icons.Rounded.Replay,
                onClick = onReset,
            )
        } else {
            if (state.alignment.hasPictureAdjustment) CommentaryAlignmentStatus(state.alignment)
            if (state.canRetry && (state.phase == ArabicCommentaryPhase.ERROR ||
                    state.phase == ArabicCommentaryPhase.INTERRUPTED)
            ) {
                CommentaryRow(
                    title = "اعادة المحاولة",
                    icon = Icons.Rounded.Refresh,
                    modifier = Modifier.commentaryEntryFocus(retryFocus, onFocusPlaced),
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun CommentaryChannelPicker(
    catalog: Catalog?,
    currentStreamId: Int,
    state: ArabicCommentaryState,
    focusPending: Boolean,
    onFocusPlaced: () -> Unit,
    modifier: Modifier,
    onSelect: (ArabicCommentaryFeed) -> Unit,
) {
    val projection by produceState<List<ArabicCommentaryFeed>?>(null, catalog, currentStreamId) {
        value = null
        value = withContext(Dispatchers.Default) { arabicCommentaryFeeds(catalog, currentStreamId) }
    }
    var query by remember { mutableStateOf("") }
    val feeds = remember(projection, query) {
        val search = arabicCommentarySearchWords(query)
        projection.orEmpty().filter { it.matches(search) }
    }
    val focus = arabicCommentaryFocusTarget(
        sessionAvailable = true,
        pickerVisible = true,
        channelsAvailable = feeds.isNotEmpty(),
        phase = state.phase,
        deliberatePickerEntry = query.isEmpty(),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HulkTextField(
            value = query,
            onValueChange = {
                query = it
                onFocusPlaced()
            },
            label = "بحث عن قناة",
            modifier = Modifier.fillMaxWidth().commentaryEntryFocus(
                focusPending && projection != null && focus == ArabicCommentaryFocusTarget.SEARCH,
                onFocusPlaced,
            ),
            leadingIcon = Icons.Rounded.Search,
        )
        if (state.feed == null && state.alignment.hasPictureAdjustment) {
            CommentaryAlignmentStatus(state.alignment)
        }
        when {
            projection == null -> CommentarySecondaryText("جاري تجهيز القنوات")
            feeds.isEmpty() -> CommentarySecondaryText(
                if (query.isBlank()) "لا توجد قنوات beIN SPORTS عربية" else "لا توجد قناة مطابقة",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(feeds, key = { it.key.rowKey }) { feed ->
                    CommentaryRow(
                        title = feed.key.label,
                        icon = Icons.Rounded.LiveTv,
                        selectedOption = feed.key == state.feed?.key,
                        modifier = Modifier.commentaryEntryFocus(
                            focusPending && focus == ArabicCommentaryFocusTarget.FIRST_CHANNEL &&
                                feed.key == feeds.first().key,
                            onFocusPlaced,
                        ),
                        onClick = { onSelect(feed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentarySourcePicker(
    feed: ArabicCommentaryFeed,
    selected: ArabicCommentaryCatalogSource?,
    focusPending: Boolean,
    onFocusPlaced: () -> Unit,
    modifier: Modifier,
    onSelect: (ArabicCommentaryCatalogSource) -> Unit,
) {
    val colors = LocalHulkColors.current
    val initialIndex = remember(feed.key) { feed.sources.indexOfFirst { it.key == selected?.key }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(feed.key.label, style = MaterialTheme.typography.titleSmall, color = colors.text)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(feed.sources, key = { it.key }) { source ->
                CommentaryRow(
                    title = source.item.name,
                    subtitle = source.categoryName,
                    selectedOption = source.key == selected?.key,
                    modifier = Modifier.commentaryEntryFocus(
                        focusPending && source.key == feed.sources[initialIndex].key,
                        onFocusPlaced,
                    ),
                    onClick = { onSelect(source) },
                )
            }
        }
    }
}

@Composable
private fun CommentaryRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    openChild: Boolean = false,
    selectedOption: Boolean? = null,
    numeric: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val showFocus = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(11.dp)
    val selectable = selectedOption != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (adaptiveUi.isTelevision) 44.dp else 48.dp)
            .clip(shape)
            .background(
                when {
                    showFocus -> colors.gold.copy(alpha = .14f)
                    selectedOption == true -> colors.gold.copy(alpha = .16f)
                    else -> Color.Transparent
                },
            )
            .then(if (showFocus) Modifier.border(1.5.dp, colors.gold, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selectable) Modifier.semantics { selected = selectedOption == true } else Modifier)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val glyphColor = if (showFocus) colors.goldBright else colors.gold
        if (icon != null && (openChild || selectable)) {
            Icon(icon, contentDescription = null, tint = glyphColor, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = title,
                color = colors.text,
                style = if (numeric) textStyle.copy(textDirection = TextDirection.Ltr) else textStyle,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let { CommentarySecondaryText(it) }
        }
        when {
            openChild -> Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(18.dp), tint = colors.textMuted)
            selectable -> Box(Modifier.size(18.dp)) {
                if (selectedOption == true) Icon(Icons.Rounded.Check, null, tint = glyphColor)
            }
            icon != null -> Icon(icon, contentDescription = null, tint = glyphColor, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Modifier.commentaryEntryFocus(request: Boolean, onPlaced: () -> Unit): Modifier {
    val requester = remember { FocusRequester() }
    var attached by remember { mutableStateOf(false) }
    LaunchedEffect(request, attached) {
        if (request && attached && requester.requestFocus()) onPlaced()
    }
    return focusRequester(requester).onGloballyPositioned { attached = it.isAttached }
}

@Composable
private fun CommentaryFooterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusButton(
        text = text,
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        primary = false,
        outlined = true,
        compact = true,
        scaleOnFocus = false,
        textMaxLines = Int.MAX_VALUE,
    )
}

@Composable
private fun CommentarySourceName(source: ArabicCommentaryCatalogSource) {
    CommentarySecondaryText(source.item.name)
    source.categoryName?.let { CommentarySecondaryText(it) }
}

private val delayNumber = Regex("[0-9]+(?:\\.[0-9]+)?")

@Composable
private fun CommentaryAlignmentStatus(alignment: ArabicCommentaryAlignment) {
    val summary = arabicCommentaryDelaySummary(
        delays = alignment.delays,
        pictureRetained = alignment.needsReadjustment,
    )
    if (alignment.delays.hasDelay || !alignment.pictureHoldIncomplete) {
        CommentarySecondaryText(delayNumber.replace(summary) { match -> "\u2066${match.value}\u2069" })
    }
    if (alignment.pictureHoldIncomplete) {
        CommentarySecondaryText("قد يبقى جزء من تأخير الصورة بعد قطع الضبط")
    }
    if (alignment.needsReadjustment) {
        CommentarySecondaryText("اعد ضبط التزامن عند الحاجة")
    }
}

@Composable
private fun CommentarySecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalHulkColors.current.textMuted)
}

@Composable
private fun CommentaryDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalHulkColors.current.line))
}

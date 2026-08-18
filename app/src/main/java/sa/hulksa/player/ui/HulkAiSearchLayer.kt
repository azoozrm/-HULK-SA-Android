package sa.hulksa.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.InfoPill
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.components.UniversalPosterCard
import sa.hulksa.player.ui.screens.TvSearchFocusAction
import sa.hulksa.player.ui.screens.tvSearchFocusAction
import sa.hulksa.player.ui.theme.LocalHulkColors

private const val HULK_AI_QUERY_DEBOUNCE_MS = 100L

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HulkAiSearchLayer(
    state: HulkUiState,
    isTv: Boolean,
    isFavorite: (ContentItem) -> Boolean,
    onSelectDestination: (MainDestination) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val query = state.searchQuery
    val trimmedQuery = query.trim()
    val movieItems = state.catalogs[ContentType.MOVIE]?.items.orEmpty()
    val seriesItems = state.catalogs[ContentType.SERIES]?.items.orEmpty()
    val inputRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val voiceRequester = remember { FocusRequester() }
    var tvEditing by remember { mutableStateOf(false) }

    val aiResult by produceState<HulkAiQueryResult?>(
        initialValue = null,
        key1 = trimmedQuery,
        key2 = movieItems,
        key3 = Triple(seriesItems, state.history, state.favorites),
    ) {
        if (!isHulkAiRequest(trimmedQuery)) {
            value = null
            return@produceState
        }
        delay(HULK_AI_QUERY_DEBOUNCE_MS)
        value = withContext(Dispatchers.Default) {
            buildResponsiveHulkAiQuerySuggestions(
                rawQuery = trimmedQuery,
                movies = movieItems,
                series = seriesItems,
                history = state.history,
                favorites = state.favorites,
                limit = if (isTv) 30 else 24,
            )
        }
    }

    val result = aiResult
    val suggestions = result?.suggestions.orEmpty()
    val catalogLoading =
        ContentType.MOVIE in state.loadingTypes || ContentType.SERIES in state.loadingTypes
    val waitingForInitialCatalog =
        catalogLoading && movieItems.isEmpty() && seriesItems.isEmpty()

    BackHandler(enabled = true) {
        if (imeVisible) {
            keyboardController?.hide()
            tvEditing = false
        } else {
            onSearch("")
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            delay(120L)
            runCatching { inputRequester.requestFocus() }
        }
    }

    val moveToResults: () -> Boolean = {
        if (suggestions.isEmpty()) {
            false
        } else {
            tvEditing = false
            keyboardController?.hide()
            runCatching { firstResultRequester.requestFocus() }.isSuccess
        }
    }

    val inputFocusModifier = if (isTv) {
        Modifier
            .focusRequester(inputRequester)
            .onFocusChanged { focus ->
                if (!focus.isFocused) {
                    tvEditing = false
                    keyboardController?.hide()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (!tvEditing && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    tvEditing = true
                    keyboardController?.show()
                    true
                } else {
                    when (
                        tvSearchFocusAction(
                            isTv = true,
                            eventType = event.type,
                            key = event.key,
                            hasResults = suggestions.isNotEmpty(),
                            imeVisible = imeVisible,
                        )
                    ) {
                        TvSearchFocusAction.MOVE_TO_RESULTS -> moveToResults()
                        TvSearchFocusAction.DISMISS_KEYBOARD -> {
                            tvEditing = false
                            keyboardController?.hide()
                            true
                        }
                        TvSearchFocusAction.NONE -> false
                    }
                }
            }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(
                start = if (isTv) 24.dp else 14.dp,
                end = if (isTv) 24.dp else 14.dp,
                top = if (isTv) 18.dp else 12.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "البحث",
                    color = colors.text,
                    fontSize = if (isTv) 28.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "بحث ذكي في القنوات والافلام والمسلسلات",
                    color = colors.textMuted,
                    fontSize = if (isTv) 11.sp else 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isTv) {
                FocusButton(
                    "الرئيسية",
                    { onSelectDestination(MainDestination.HOME) },
                    primary = false,
                    compact = true,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (isTv) 900.dp else 760.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InlineVoiceSearchAction(
                query = query,
                isTv = isTv,
                requester = voiceRequester,
                searchFieldRequester = inputRequester,
                downRequester = if (suggestions.isNotEmpty()) firstResultRequester else null,
            )
            HulkTextField(
                value = query,
                onValueChange = onSearch,
                label = "قل اسم فيلم او مسلسل او اطلب ترشيحا",
                modifier = Modifier
                    .weight(1f)
                    .then(inputFocusModifier),
                readOnly = isTv && !tvEditing,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { moveToResults() }),
            )
        }

        Spacer(Modifier.height(11.dp))
        if (result != null && result.understoodLabels.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("فهمت طلبك:", color = colors.textMuted, fontSize = 10.sp)
                result.understoodLabels.take(if (isTv) 6 else 4).forEach { label ->
                    InfoPill(label)
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        if (result != null && !result.exactConstraintMatch && suggestions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.gold.copy(alpha = .30f), RoundedCornerShape(12.dp))
                    .background(colors.gold.copy(alpha = .07f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    "ما لقيت تطابقا كاملا لكل تفاصيل الطلب؛ هذه اقرب نتائج حقيقية من مكتبتك.",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(9.dp))
        }

        when {
            waitingForInitialCatalog -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري تجهيز مكتبتك…")
                }
            }
            result == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingRing(label = "جاري تجهيز النتائج…")
                }
            }
            suggestions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "لا توجد نتائج حقيقية مطابقة في مكتبتك",
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "جرب صياغة ابسط او امسح الطلب للبحث بالاسم",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "النتائج المقترحة",
                        color = colors.text,
                        fontSize = if (isTv) 19.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${suggestions.size} نتيجة",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(7.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTv) 142.dp else 108.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 9.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp),
                    contentPadding = PaddingValues(bottom = if (isTv) 30.dp else 86.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = suggestions,
                        key = { _, suggestion -> "${suggestion.item.type}:${suggestion.item.id}" },
                    ) { index, suggestion ->
                        Column {
                            UniversalPosterCard(
                                item = suggestion.item,
                                isFavorite = isFavorite(suggestion.item),
                                onClick = { onOpen(suggestion.item) },
                                onLongClick = { onToggleFavorite(suggestion.item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (index == 0 && isTv) {
                                            Modifier
                                                .focusRequester(firstResultRequester)
                                                .focusProperties { up = inputRequester }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                            val reason = hulkAiReason(suggestion)
                            if (reason.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    reason,
                                    color = colors.textMuted,
                                    fontSize = if (isTv) 9.sp else 8.sp,
                                    lineHeight = if (isTv) 12.sp else 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hulkAiReason(suggestion: HulkAiQuerySuggestion): String {
    val labels = suggestion.signals
        .map(HulkAiQuerySignal::label)
        .filter(String::isNotBlank)
        .distinct()
        .take(3)
    return labels.joinToString("  •  ")
}

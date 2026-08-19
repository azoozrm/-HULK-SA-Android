package sa.hulksa.player.ui.screens

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.CompactPosterCard
import sa.hulksa.player.ui.components.SeriesPosterCard

internal data class TvCatalogMetrics(
    val minCellWidthDp: Float,
    val horizontalSpacingDp: Float,
    val verticalSpacingDp: Float,
    val horizontalContentPaddingDp: Float,
    val bottomContentPaddingDp: Float,
    val focusViewportInsetDp: Float,
)

internal fun tvCatalogMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
): TvCatalogMetrics {
    val width = screenWidthDp.coerceAtLeast(1)
    val height = screenHeightDp.coerceAtLeast(1)
    val policy = tvPremiumWindowPolicy(width, height)
    val compact = width <= 960 || height <= 540
    val large = width >= 1600 && height >= 900

    val densityScale = when {
        compact -> .94f
        large -> 1.10f
        else -> 1f
    }

    return TvCatalogMetrics(
        minCellWidthDp = (132f * densityScale).coerceIn(124f, 146f),
        horizontalSpacingDp = (14f * densityScale).coerceIn(12f, 16f),
        verticalSpacingDp = (15f * densityScale).coerceIn(13f, 17f),
        horizontalContentPaddingDp = when {
            compact -> 12f
            large -> 12f
            else -> 10f
        },
        bottomContentPaddingDp = maxOf(44f, policy.verticalSafeInsetDp + 30f),
        focusViewportInsetDp = when {
            compact -> 9f
            large -> 12f
            else -> 10f
        },
    )
}

@Composable
internal fun TvCatalogGrid(
    content: List<ContentItem>,
    destination: MainDestination,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    restoreFocusedCard: Boolean,
) {
    require(destination == MainDestination.MOVIES || destination == MainDestination.SERIES)

    val adaptiveUi = LocalAdaptiveUi.current
    val metrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp) {
        tvCatalogMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
        )
    }
    val minCellWidth = metrics.minCellWidthDp.dp
    val horizontalSpacing = metrics.horizontalSpacingDp.dp
    val verticalSpacing = metrics.verticalSpacingDp.dp
    val horizontalContentPadding = metrics.horizontalContentPaddingDp.dp
    val bottomContentPadding = metrics.bottomContentPaddingDp.dp
    val focusViewportInset = metrics.focusViewportInsetDp.dp

    val contentKeys = remember(content) { content.map { "${it.type}:${it.id}" } }
    val remembered = navigationMemory.position(destination)
    val rememberedKeyIndex = contentKeys.indexOf(remembered.itemKey)
    val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
        .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    val targetKey = contentKeys.getOrNull(targetIndex)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    val focusRequesters = remember(contentKeys) {
        contentKeys.associateWith { FocusRequester() }
    }
    val focusScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusViewportInsetPx = with(density) { focusViewportInset.roundToPx() }
    var focusMoveJob by remember { mutableStateOf<Job?>(null) }
    var pendingTargetIndex by remember(contentKeys, destination) { mutableStateOf<Int?>(null) }

    suspend fun ensureIndexFullyVisible(index: Int) {
        val layoutInfo = gridState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val viewportStart = layoutInfo.viewportStartOffset + focusViewportInsetPx
        val viewportEnd = layoutInfo.viewportEndOffset - focusViewportInsetPx
        val itemTop = itemInfo.offset.y
        val itemBottom = itemTop + itemInfo.size.height
        val correction = when {
            itemBottom > viewportEnd -> itemBottom - viewportEnd
            itemTop < viewportStart -> itemTop - viewportStart
            else -> 0
        }
        if (correction != 0) {
            gridState.scrollBy(correction.toFloat())
        }
    }

    suspend fun focusIndex(
        index: Int,
        columnCount: Int,
        ensureFullyVisible: Boolean,
    ) {
        val requester = contentKeys.getOrNull(index)?.let(focusRequesters::get) ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == index }) {
            val firstVisible = visible.minOfOrNull { it.index } ?: index
            val lastVisible = visible.maxOfOrNull { it.index } ?: index
            val visibleRowCount = if (lastVisible >= firstVisible) {
                ((lastVisible - firstVisible) / columnCount) + 1
            } else {
                1
            }
            val anchor = when {
                index < firstVisible -> index
                index > lastVisible -> (index - (visibleRowCount - 1) * columnCount).coerceAtLeast(0)
                else -> firstVisible
            }
            gridState.scrollToItem(anchor)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == index } }
                .first { it }
        }
        if (ensureFullyVisible) {
            ensureIndexFullyVisible(index)
        }
        runCatching { requester.requestFocus() }
    }

    LaunchedEffect(contentKeys, remembered.itemKey, destination, restoreFocusedCard) {
        if (restoreFocusedCard && content.isNotEmpty() && targetKey != null) {
            gridState.scrollToItem(targetIndex)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }
                .first { it }
            ensureIndexFullyVisible(targetIndex)
            runCatching { focusRequesters.getValue(targetKey).requestFocus() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableGridWidth = (maxWidth - (horizontalContentPadding * 2)).coerceAtLeast(minCellWidth)
        val columnCount = (((availableGridWidth + horizontalSpacing).value) /
            (minCellWidth + horizontalSpacing).value)
            .toInt()
            .coerceAtLeast(1)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minCellWidth),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            contentPadding = PaddingValues(
                start = horizontalContentPadding,
                top = horizontalContentPadding,
                end = horizontalContentPadding,
                bottom = bottomContentPadding,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(content, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                val key = contentKeys[index]
                val cardModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters.getValue(key))
                    .onPreviewKeyEvent { event ->
                        val move = when (event.key) {
                            Key.DirectionLeft -> TvGridFocusMove.LEFT
                            Key.DirectionRight -> TvGridFocusMove.RIGHT
                            Key.DirectionUp -> TvGridFocusMove.UP
                            Key.DirectionDown -> TvGridFocusMove.DOWN
                            else -> null
                        } ?: return@onPreviewKeyEvent false

                        if (event.type == KeyEventType.KeyUp) {
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }

                        val baseIndex = pendingTargetIndex ?: index
                        val nextIndex = nextTvGridFocusIndex(
                            currentIndex = baseIndex,
                            itemCount = content.size,
                            columnCount = columnCount,
                            move = move,
                        )

                        if (nextIndex == null) {
                            // RTL catalog policy:
                            // - Physical RIGHT from the row's right-most card is allowed to
                            //   escape to the navigation rail.
                            // - Physical LEFT from the row's left-most card is consumed so
                            //   Compose cannot spatially fall back to an unrelated control.
                            return@onPreviewKeyEvent move == TvGridFocusMove.LEFT
                        }

                        pendingTargetIndex = nextIndex
                        navigationMemory.save(destination, contentKeys[nextIndex], nextIndex)
                        focusMoveJob?.cancel()
                        focusMoveJob = focusScope.launch {
                            focusIndex(
                                index = nextIndex,
                                columnCount = columnCount,
                                ensureFullyVisible = move == TvGridFocusMove.UP || move == TvGridFocusMove.DOWN,
                            )
                            if (pendingTargetIndex == nextIndex) {
                                pendingTargetIndex = null
                            }
                        }
                        true
                    }
                val onFocusedCard = {
                    if (pendingTargetIndex == index) {
                        pendingTargetIndex = null
                    }
                    navigationMemory.save(destination, key, index)
                }

                if (destination == MainDestination.SERIES) {
                    SeriesPosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = cardModifier,
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = onFocusedCard,
                    )
                } else {
                    CompactPosterCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = cardModifier,
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = onFocusedCard,
                    )
                }
            }
        }
    }
}

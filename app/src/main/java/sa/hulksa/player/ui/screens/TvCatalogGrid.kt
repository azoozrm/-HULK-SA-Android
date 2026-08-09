package sa.hulksa.player.ui.screens

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.components.CompactPosterCard

private val TV_GRID_MIN_CELL_WIDTH = 132.dp
private val TV_GRID_HORIZONTAL_SPACING = 14.dp
private val TV_GRID_VERTICAL_SPACING = 15.dp
private val TV_GRID_HORIZONTAL_CONTENT_PADDING = 5.dp

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
    var focusMoveJob by remember { mutableStateOf<Job?>(null) }
    var pendingTargetIndex by remember(contentKeys, destination) { mutableStateOf<Int?>(null) }

    suspend fun focusIndex(index: Int, columnCount: Int) {
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
        runCatching { requester.requestFocus() }
    }

    LaunchedEffect(contentKeys, remembered.itemKey, destination, restoreFocusedCard) {
        if (restoreFocusedCard && content.isNotEmpty() && targetKey != null) {
            gridState.scrollToItem(targetIndex)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }
                .first { it }
            runCatching { focusRequesters.getValue(targetKey).requestFocus() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableGridWidth = (maxWidth - (TV_GRID_HORIZONTAL_CONTENT_PADDING * 2)).coerceAtLeast(TV_GRID_MIN_CELL_WIDTH)
        val columnCount = (((availableGridWidth + TV_GRID_HORIZONTAL_SPACING).value) /
            (TV_GRID_MIN_CELL_WIDTH + TV_GRID_HORIZONTAL_SPACING).value)
            .toInt()
            .coerceAtLeast(1)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(TV_GRID_MIN_CELL_WIDTH),
            horizontalArrangement = Arrangement.spacedBy(TV_GRID_HORIZONTAL_SPACING),
            verticalArrangement = Arrangement.spacedBy(TV_GRID_VERTICAL_SPACING),
            contentPadding = PaddingValues(
                start = TV_GRID_HORIZONTAL_CONTENT_PADDING,
                top = 5.dp,
                end = TV_GRID_HORIZONTAL_CONTENT_PADDING,
                bottom = 28.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(content, key = { _, item -> "${item.type}:${item.id}" }) { index, item ->
                val key = contentKeys[index]
                CompactPosterCard(
                    item = item,
                    isFavorite = isFavorite(item),
                    onClick = { onOpen(item) },
                    modifier = Modifier
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
                            ) ?: return@onPreviewKeyEvent false

                            pendingTargetIndex = nextIndex
                            navigationMemory.save(destination, contentKeys[nextIndex], nextIndex)
                            focusMoveJob?.cancel()
                            focusMoveJob = focusScope.launch {
                                focusIndex(nextIndex, columnCount)
                                if (pendingTargetIndex == nextIndex) {
                                    pendingTargetIndex = null
                                }
                            }
                            true
                        },
                    onLongClick = { onToggleFavorite(item) },
                    onFocused = {
                        pendingTargetIndex = null
                        navigationMemory.save(destination, key, index)
                    },
                )
            }
        }
    }
}

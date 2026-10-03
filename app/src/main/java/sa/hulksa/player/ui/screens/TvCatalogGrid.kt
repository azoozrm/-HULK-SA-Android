package sa.hulksa.player.ui.screens

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sa.hulksa.player.MainDestination
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy
import sa.hulksa.player.ui.components.CompactPosterCard
import sa.hulksa.player.ui.components.MoviesCatalogBoxedCard
import sa.hulksa.player.ui.components.SeriesPosterCard

// Legibility floor for the Movie-only compact fallback on unusually short usable windows.
private const val MOVIE_COMPACT_ARTWORK_MIN_HEIGHT_DP = 96

internal data class TvCatalogMetrics(
    val minCellWidthDp: Float,
    val horizontalSpacingDp: Float,
    val verticalSpacingDp: Float,
    val horizontalContentPaddingDp: Float,
    val endContentPaddingDp: Float,
    val bottomContentPaddingDp: Float,
    val focusViewportInsetDp: Float,
    val focusSafeBottomInsetDp: Float,
)

/**
 * Movie-only minimum cell width for the owner-observed wide TV five-column composition.
 *
 * Wider cells intentionally yield fewer, shorter cards on wide windows while narrower windows
 * fall back toward the existing adaptive cell so five columns are never forced on phones,
 * tablets, split windows or small TVs.
 */
internal fun movieCatalogMinCellWidthDp(screenWidthDp: Int): Float {
    val width = screenWidthDp.coerceAtLeast(1)
    return when {
        width >= 1500 -> 205f
        width >= 1100 -> 190f
        width >= 900 -> 170f
        width >= 720 -> 150f
        width >= 480 -> 132f
        else -> 120f
    }
}

/**
 * One column policy shared by the actual LazyVerticalGrid layout and the D-pad column arithmetic.
 */
internal fun movieCatalogColumnCount(
    availableWidthDp: Float,
    spacingDp: Float,
    minCellWidthDp: Float,
): Int {
    if (availableWidthDp <= 0f || minCellWidthDp <= 0f || spacingDp < 0f) return 1
    return (((availableWidthDp + spacingDp) / (minCellWidthDp + spacingDp)).toInt()).coerceAtLeast(1)
}

internal fun tvCatalogMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int,
    movieCards: Boolean = false,
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
        minCellWidthDp = if (movieCards) {
            movieCatalogMinCellWidthDp(width)
        } else {
            (132f * densityScale).coerceIn(124f, 146f)
        },
        horizontalSpacingDp = (14f * densityScale).coerceIn(12f, 16f),
        verticalSpacingDp = (15f * densityScale).coerceIn(13f, 17f),
        horizontalContentPaddingDp = when {
            compact -> 12f
            large -> 12f
            else -> 10f
        },
        // Movie-only: the physical LEFT end (RTL) uses the real TV safe inset so the focused
        // card border is not cropped by overscan. Other catalogs keep the historical 6dp.
        endContentPaddingDp = if (movieCards) policy.horizontalSafeInsetDp else 6f,
        bottomContentPaddingDp = maxOf(44f, policy.verticalSafeInsetDp + 30f),
        focusViewportInsetDp = when {
            compact -> 9f
            large -> 12f
            else -> 10f
        },
        // Movie-only: the settled focused-card reveal also protects the physical bottom safe
        // area so the footer and its in-bounds focus edge are never cropped by TV overscan.
        // Other catalogs keep the historical viewport bounds.
        focusSafeBottomInsetDp = if (movieCards) policy.verticalSafeInsetDp else 0f,
    )
}

// Symmetric horizontal spacing plus a top-only inset is used by the TV
// catalog headers so controls stay inside the content viewport without
// reintroducing the large side gutters removed from the catalog body.
internal fun Modifier.padding(horizontal: Dp, top: Dp): Modifier =
    padding(start = horizontal, top = top, end = horizontal, bottom = 0.dp)

internal enum class TvCatalogFocusPath {
    DIRECT,
    SCROLL_ASSISTED,
    INVALID,
}

internal fun tvCatalogFocusPath(
    targetIndex: Int,
    itemCount: Int,
    itemTop: Int?,
    itemBottom: Int?,
    viewportStart: Int,
    viewportEnd: Int,
): TvCatalogFocusPath {
    if (targetIndex !in 0 until itemCount) return TvCatalogFocusPath.INVALID
    if (itemTop == null || itemBottom == null || itemBottom <= itemTop) {
        return TvCatalogFocusPath.SCROLL_ASSISTED
    }
    return if (itemTop >= viewportStart && itemBottom <= viewportEnd) {
        TvCatalogFocusPath.DIRECT
    } else {
        TvCatalogFocusPath.SCROLL_ASSISTED
    }
}

/**
 * Single authoritative full-visibility math for a focused card and its in-bounds focus edge.
 *
 * The margin is kept when the card and both margins fit inside the usable window (which already
 * excludes the physical safe area); otherwise the margin falls back to zero so a card that
 * exactly fits is still fully revealed instead of being scrolled to a clipped half position.
 */
internal fun focusedCardScrollCorrection(
    itemTop: Int,
    itemBottom: Int,
    usableStart: Int,
    usableEnd: Int,
    marginPx: Int,
): Int {
    val itemHeight = itemBottom - itemTop
    val window = usableEnd - usableStart
    val safeMargin = if (itemHeight + 2 * marginPx <= window) marginPx else 0
    val revealStart = usableStart + safeMargin
    val revealEnd = usableEnd - safeMargin
    return when {
        itemBottom > revealEnd -> itemBottom - revealEnd
        itemTop < revealStart -> itemTop - revealStart
        else -> 0
    }
}

/**
 * One-pass authoritative reveal of a focused grid item. Reads the real settled item rectangle and
 * applies a single correction inside the usable viewport (safe area plus focus inset already
 * excluded). Used after focus actually moved and for restoration, never as a polling loop.
 */
internal suspend fun revealFocusedGridItem(
    gridState: LazyGridState,
    index: Int,
    focusInsetPx: Int,
    safeBottomInsetPx: Int,
    extraMarginPx: Int = 0,
) {
    val layoutInfo = gridState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val correction = focusedCardScrollCorrection(
        itemTop = itemInfo.offset.y,
        itemBottom = itemInfo.offset.y + itemInfo.size.height,
        usableStart = layoutInfo.viewportStartOffset + focusInsetPx,
        usableEnd = layoutInfo.viewportEndOffset - safeBottomInsetPx - focusInsetPx,
        marginPx = extraMarginPx,
    )
    if (correction != 0) {
        gridState.scrollBy(correction.toFloat())
    }
}

/**
 * Constraint-aware compact fallback for Movie cards on unusually short usable windows.
 *
 * Returns the artwork height that keeps the complete card (footer included) inside the usable
 * window, or null when the accepted square geometry already fits. Never enlarges the accepted
 * normal-TV geometry and never returns less than the legibility floor.
 */
internal fun movieCompactArtworkHeightPx(
    cellWidthPx: Int,
    footerHeightPx: Int,
    usableHeightPx: Int,
    minArtworkHeightPx: Int,
): Int? {
    if (cellWidthPx <= 0 || footerHeightPx <= 0 || usableHeightPx <= 0) return null
    if (cellWidthPx + footerHeightPx <= usableHeightPx) return null
    return (usableHeightPx - footerHeightPx).coerceAtLeast(minArtworkHeightPx).takeIf { it < cellWidthPx }
}

internal class TvCatalogFocusMoveState {
    var job: Job? = null
    var revealJob: Job? = null
    private var pendingTargetIndex: Int? = null

    fun baseIndex(currentIndex: Int): Int = pendingTargetIndex ?: currentIndex

    fun begin(targetIndex: Int) {
        pendingTargetIndex = targetIndex
    }

    fun complete(targetIndex: Int) {
        if (pendingTargetIndex == targetIndex) pendingTargetIndex = null
    }

    fun pendingTargetIndex(): Int? = pendingTargetIndex
}

@Composable
internal fun TvCatalogGrid(
    content: List<ContentItem>,
    contentKeys: List<String>,
    contentKeyIndex: Map<String, Int>,
    destination: MainDestination,
    navigationMemory: NavigationMemoryStore,
    isFavorite: (ContentItem) -> Boolean,
    onOpen: (ContentItem) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    restoreFocusedCard: Boolean,
    onMoveToCategories: (() -> Boolean)? = null,
) {
    require(destination == MainDestination.MOVIES || destination == MainDestination.SERIES)
    require(contentKeys.size == content.size)

    val adaptiveUi = LocalAdaptiveUi.current
    val movieCards = destination == MainDestination.MOVIES
    val metrics = remember(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp, movieCards) {
        tvCatalogMetrics(
            screenWidthDp = adaptiveUi.screenWidthDp,
            screenHeightDp = adaptiveUi.screenHeightDp,
            movieCards = movieCards,
        )
    }
    val minCellWidth = metrics.minCellWidthDp.dp
    val horizontalSpacing = metrics.horizontalSpacingDp.dp
    val verticalSpacing = metrics.verticalSpacingDp.dp
    val horizontalContentPadding = metrics.horizontalContentPaddingDp.dp
    val focusSafeEndPadding = metrics.endContentPaddingDp.dp
    val bottomContentPadding = metrics.bottomContentPaddingDp.dp
    val focusViewportInset = metrics.focusViewportInsetDp.dp
    val focusSafeBottomInset = metrics.focusSafeBottomInsetDp.dp

    val remembered = navigationMemory.position(destination)
    val rememberedKeyIndex = contentKeyIndex[remembered.itemKey] ?: -1
    val targetIndex = (if (rememberedKeyIndex >= 0) rememberedKeyIndex else remembered.itemIndex)
        .coerceIn(0, content.lastIndex.coerceAtLeast(0))
    val targetKey = contentKeys.getOrNull(targetIndex)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    val focusRequesters = remember(contentKeys) { List(contentKeys.size) { FocusRequester() } }
    val focusScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusViewportInsetPx = with(density) { focusViewportInset.roundToPx() }
    val focusSafeBottomInsetPx = with(density) { focusSafeBottomInset.roundToPx() }
    val focusMoveState = remember(contentKeys, destination) { TvCatalogFocusMoveState() }
    DisposableEffect(focusMoveState) {
        onDispose {
            focusMoveState.job?.cancel()
            focusMoveState.revealJob?.cancel()
        }
    }

    suspend fun ensureIndexFullyVisible(index: Int) {
        revealFocusedGridItem(
            gridState = gridState,
            index = index,
            focusInsetPx = focusViewportInsetPx,
            safeBottomInsetPx = focusSafeBottomInsetPx,
            // Movies add the extra focus margin; Series keeps its historical single-inset bound.
            extraMarginPx = if (movieCards) focusViewportInsetPx else 0,
        )
    }

    // Movie-only settled reveal: after focus has actually moved, real layout frames decide the
    // final focused rectangle so implicit focus relocation, restoration or a row transition can
    // never leave the footer/border half-cropped. Bounded to two real frames (no sleep/polling)
    // and cancelled for obsolete targets; Series keeps the pre-focus path unchanged.
    suspend fun settleFocusedIndexVisibility(index: Int) {
        repeat(2) {
            withFrameNanos { }
            ensureIndexFullyVisible(index)
        }
    }

    suspend fun focusIndex(
        index: Int,
        columnCount: Int,
        ensureFullyVisible: Boolean,
    ) {
        val requester = focusRequesters.getOrNull(index) ?: return
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
            runCatching { focusRequesters[targetIndex].requestFocus() }
            if (movieCards) {
                settleFocusedIndexVisibility(targetIndex)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableGridWidth = (
            maxWidth - horizontalContentPadding - focusSafeEndPadding
        ).coerceAtLeast(minCellWidth)
        val columnCount = if (movieCards) {
            movieCatalogColumnCount(
                availableWidthDp = availableGridWidth.value,
                spacingDp = horizontalSpacing.value,
                minCellWidthDp = minCellWidth.value,
            )
        } else {
            (((availableGridWidth + horizontalSpacing).value) /
                (minCellWidth + horizontalSpacing).value)
                .toInt()
                .coerceAtLeast(1)
        }
        // Movie-only compact fallback: measure the real footer, then cap only when the accepted
        // square card cannot fit the usable grid window. Normal TVs keep the accepted geometry.
        val cellWidth = ((availableGridWidth - horizontalSpacing * (columnCount - 1)) / columnCount)
            .coerceAtLeast(1.dp)
        val usableGridHeightPx = with(density) {
            (maxHeight - horizontalContentPadding - focusSafeBottomInset).coerceAtLeast(1.dp).roundToPx()
        }
        var movieFooterHeightPx by remember(contentKeys) { mutableIntStateOf(0) }
        val compactArtworkHeightPx = if (movieCards && movieFooterHeightPx > 0) {
            movieCompactArtworkHeightPx(
                cellWidthPx = with(density) { cellWidth.roundToPx() },
                footerHeightPx = movieFooterHeightPx,
                usableHeightPx = usableGridHeightPx,
                minArtworkHeightPx = with(density) { MOVIE_COMPACT_ARTWORK_MIN_HEIGHT_DP.dp.roundToPx() },
            )
        } else {
            null
        }

        LazyVerticalGrid(
            state = gridState,
            columns = if (movieCards) GridCells.Fixed(columnCount) else GridCells.Adaptive(minCellWidth),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            contentPadding = PaddingValues(
                start = horizontalContentPadding,
                top = horizontalContentPadding,
                end = focusSafeEndPadding,
                bottom = bottomContentPadding,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(content, key = { index, _ -> contentKeys[index] }) { index, item ->
                val key = contentKeys[index]
                val cardModifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
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

                        val baseIndex = focusMoveState.baseIndex(index)
                        val nextIndex = nextTvGridFocusIndex(
                            currentIndex = baseIndex,
                            itemCount = content.size,
                            columnCount = columnCount,
                            move = move,
                        )

                        if (nextIndex == null) {
                            if (move == TvGridFocusMove.UP && onMoveToCategories != null) {
                                return@onPreviewKeyEvent onMoveToCategories()
                            }
                            // RTL catalog policy:
                            // - Physical RIGHT from the row's right-most card is allowed to
                            //   escape to the navigation rail.
                            // - Physical LEFT from the row's left-most card is consumed so
                            //   Compose cannot spatially fall back to an unrelated control.
                            return@onPreviewKeyEvent move == TvGridFocusMove.LEFT
                        }

                        val requester = focusRequesters.getOrNull(nextIndex)
                            ?: return@onPreviewKeyEvent false
                        val layoutInfo = gridState.layoutInfo
                        val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == nextIndex }
                        val focusPath = tvCatalogFocusPath(
                            targetIndex = nextIndex,
                            itemCount = content.size,
                            itemTop = targetInfo?.offset?.y,
                            itemBottom = targetInfo?.let { it.offset.y + it.size.height },
                            viewportStart = layoutInfo.viewportStartOffset + focusViewportInsetPx,
                            viewportEnd = layoutInfo.viewportEndOffset - focusViewportInsetPx,
                        )
                        if (focusPath == TvCatalogFocusPath.INVALID) {
                            return@onPreviewKeyEvent false
                        }

                        focusMoveState.begin(nextIndex)
                        navigationMemory.save(destination, contentKeys[nextIndex], nextIndex)

                        focusMoveState.job?.cancel()
                        focusMoveState.job = null
                        val focusedDirectly = if (focusPath == TvCatalogFocusPath.DIRECT) {
                            runCatching { requester.requestFocus() }.getOrDefault(false)
                        } else {
                            false
                        }
                        if (focusedDirectly) {
                            focusMoveState.complete(nextIndex)
                        } else {
                            focusMoveState.job = focusScope.launch {
                                focusIndex(
                                    index = nextIndex,
                                    columnCount = columnCount,
                                    ensureFullyVisible = true,
                                )
                                focusMoveState.complete(nextIndex)
                            }
                        }
                        true
                    }
                val onFocusedCard = {
                    focusMoveState.complete(index)
                    navigationMemory.save(destination, key, index)
                    if (movieCards) {
                        focusMoveState.revealJob?.cancel()
                        focusMoveState.revealJob = focusScope.launch {
                            settleFocusedIndexVisibility(index)
                        }
                    }
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
                } else if (destination == MainDestination.MOVIES) {
                    MoviesCatalogBoxedCard(
                        item = item,
                        isFavorite = isFavorite(item),
                        onClick = { onOpen(item) },
                        modifier = cardModifier,
                        onLongClick = { onToggleFavorite(item) },
                        onFocused = onFocusedCard,
                        artworkHeightDp = compactArtworkHeightPx?.let { heightPx ->
                            with(density) { heightPx.toDp() }
                        },
                        onFooterHeightMeasured = { movieFooterHeightPx = it },
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

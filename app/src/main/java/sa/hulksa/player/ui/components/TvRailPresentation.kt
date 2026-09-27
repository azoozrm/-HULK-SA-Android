package sa.hulksa.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.screens.TvRailMetrics
import sa.hulksa.player.ui.screens.tvRailOverlayWidthDp
import sa.hulksa.player.ui.theme.LocalHulkColors

/** Bounded expansion transition. It is presentation only and never gates focus or navigation. */
internal const val TV_RAIL_EXPANSION_DURATION_MILLIS = 160

private val TvRailSurfaceStart = Color(0xFF090A07)
private val TvRailSurfaceEnd = Color(0xFF0A0B08)
private const val TV_RAIL_FOCUSED_BACKGROUND_ALPHA = 0.16f
private const val TV_RAIL_SELECTED_BACKGROUND_ALPHA = 0.10f
private const val TV_RAIL_SELECTED_BORDER_ALPHA = 0.30f
private val TvRailSelectionMarkerWidth = 3.dp
private val TvRailSelectionMarkerInset = 5.dp
private const val TV_RAIL_SELECTION_MARKER_HEIGHT_FRACTION = 0.52f
private val TvRailEdgeScrimWidth = 28.dp

/**
 * Visual meaning of a rail destination. Selection and D-pad focus stay separate meanings; when a
 * destination is both, the combined state draws the selection treatment and the focus outline
 * together instead of inventing a third decoration.
 */
internal enum class TvRailVisualState {
    IDLE,
    SELECTED,
    FOCUSED,
    SELECTED_FOCUSED,
}

internal fun tvRailVisualState(selected: Boolean, highlighted: Boolean): TvRailVisualState = when {
    selected && highlighted -> TvRailVisualState.SELECTED_FOCUSED
    highlighted -> TvRailVisualState.FOCUSED
    selected -> TvRailVisualState.SELECTED
    else -> TvRailVisualState.IDLE
}

/**
 * Horizontal offset for the oversized rail surface inside its collapsed footprint.
 *
 * The surface must stay anchored to the layout's start edge (the right edge in RTL television
 * layouts), so expansion can only grow toward the content. A positive non-start offset would push
 * the surface's start edge past the screen's safe edge, which is the reported clipping defect.
 */
internal fun tvRailOverlayAnchorOffsetPx(
    reportedWidthPx: Int,
    surfaceWidthPx: Int,
    isRtl: Boolean,
): Int = if (isRtl) reportedWidthPx - surfaceWidthPx else 0

/**
 * Opacity of the local content-edge scrim attached to the expanded rail.
 *
 * It is driven by the expansion fraction so it fades in as the rail grows and is exactly zero when
 * the rail is collapsed, leaving no residual mask over the page. The scrim softens only the rail's
 * content-facing edge; it never dims the page.
 */
internal fun tvRailEdgeScrimAlpha(expansionFraction: Float): Float =
    expansionFraction.coerceIn(0f, 1f)

private fun DrawScope.drawTvRailEdgeScrim(alpha: Float) {
    if (alpha <= 0f) return
    val scrimWidth = TvRailEdgeScrimWidth.toPx()
    if (scrimWidth <= 0f) return
    val surfaceColor = TvRailSurfaceStart.copy(alpha = alpha)
    val transparent = TvRailSurfaceStart.copy(alpha = 0f)
    if (layoutDirection == LayoutDirection.Rtl) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(transparent, surfaceColor),
                startX = -scrimWidth,
                endX = 0f,
            ),
            topLeft = Offset(-scrimWidth, 0f),
            size = Size(scrimWidth, size.height),
        )
    } else {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(surfaceColor, transparent),
                startX = size.width,
                endX = size.width + scrimWidth,
            ),
            topLeft = Offset(size.width, 0f),
            size = Size(scrimWidth, size.height),
        )
    }
}

/**
 * Measures the rail surface at [width] while reporting only the parent's collapsed footprint.
 *
 * Unlike `requiredWidth`, which centers oversized content within the coerced space and therefore
 * bleeds past both edges, this anchors the surface to the parent's start edge: in RTL the right
 * edge stays fixed on screen and the rail expands leftward over the content.
 */
private fun Modifier.tvRailOverlayWidth(width: Dp): Modifier = layout { measurable, constraints ->
    val requestedWidth = width.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minWidth = requestedWidth, maxWidth = requestedWidth),
    )
    if (!constraints.hasBoundedWidth) {
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    } else {
        val reportedWidth = constraints.maxWidth
        layout(reportedWidth, placeable.height) {
            placeable.place(
                tvRailOverlayAnchorOffsetPx(
                    reportedWidthPx = reportedWidth,
                    surfaceWidthPx = placeable.width,
                    isRtl = layoutDirection == LayoutDirection.Rtl,
                ),
                0,
            )
        }
    }
}

/**
 * Shared presentation surface for the Main and Search TV rails.
 *
 * The two screens keep their own focus controllers, destination state and handoff ownership; this
 * surface only receives their presentation state. On television the shell reserves the collapsed
 * footprint and the surface is allowed to draw past it toward the content, so expansion can never
 * remeasure or translate the page. Non-TV rail layouts keep their existing measured width and are
 * never converted into an overlay.
 */
@Composable
internal fun TvRailSurface(
    metrics: TvRailMetrics,
    expanded: Boolean,
    overlayExpansion: Boolean,
    onRailFocusChanged: (Boolean) -> Unit,
    onRailEnter: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val targetWidth = tvRailOverlayWidthDp(metrics, expanded = expanded).dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(
            durationMillis = TV_RAIL_EXPANSION_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
        label = "tvRailOverlayWidth",
    )
    val surfaceWidth = if (overlayExpansion) animatedWidth else targetWidth
    val expansionFraction = if (overlayExpansion && metrics.expandedWidthDp > metrics.collapsedWidthDp) {
        (
            (surfaceWidth.value - metrics.collapsedWidthDp) /
                (metrics.expandedWidthDp - metrics.collapsedWidthDp)
            ).coerceIn(0f, 1f)
    } else {
        0f
    }
    val edgeScrimAlpha = tvRailEdgeScrimAlpha(expansionFraction)

    Box(
        modifier = Modifier
            .zIndex(1f)
            .then(
                if (overlayExpansion) Modifier.width(metrics.collapsedWidthDp.dp) else Modifier,
            )
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (overlayExpansion) {
                        Modifier.tvRailOverlayWidth(surfaceWidth)
                    } else {
                        Modifier.width(surfaceWidth)
                    },
                )
                .then(
                    if (overlayExpansion) {
                        Modifier.drawBehind { drawTvRailEdgeScrim(edgeScrimAlpha) }
                    } else {
                        Modifier
                    },
                )
                .fillMaxHeight()
                .focusProperties {
                    onRailEnter?.let { enter -> onEnter = { enter() } }
                }
                .focusGroup()
                .onFocusChanged { onRailFocusChanged(it.hasFocus) }
                .background(Brush.horizontalGradient(listOf(TvRailSurfaceStart, TvRailSurfaceEnd)))
                .padding(
                    start = metrics.outerHorizontalPaddingDp.dp,
                    end = metrics.outerHorizontalPaddingDp.dp,
                    top = metrics.topPaddingDp.dp,
                    bottom = metrics.bottomPaddingDp.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * Shared rail destination decoration.
 *
 * The item owns its own focus observation but no navigation: callers keep supplying their own
 * [androidx.compose.ui.focus.FocusRequester] map and direction contract through [modifier].
 */
@Composable
internal fun TvRailDestinationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    metrics: TvRailMetrics,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var itemFocused by remember { mutableStateOf(false) }
    val focusHighlighted = itemFocused && adaptiveUi.showFocusHighlights
    val visualState = tvRailVisualState(selected = selected, highlighted = focusHighlighted)
    val active = visualState != TvRailVisualState.IDLE
    val shape = RoundedCornerShape(metrics.cornerRadiusDp.dp)
    val background = when (visualState) {
        TvRailVisualState.FOCUSED,
        TvRailVisualState.SELECTED_FOCUSED,
        -> colors.gold.copy(alpha = TV_RAIL_FOCUSED_BACKGROUND_ALPHA)

        TvRailVisualState.SELECTED -> colors.gold.copy(alpha = TV_RAIL_SELECTED_BACKGROUND_ALPHA)
        TvRailVisualState.IDLE -> Color.Transparent
    }
    val borderWidth = when (visualState) {
        TvRailVisualState.FOCUSED,
        TvRailVisualState.SELECTED_FOCUSED,
        -> adaptiveUi.tvPremiumPolicy.focusBorderWidthDp.dp

        TvRailVisualState.SELECTED -> 1.dp
        TvRailVisualState.IDLE -> 0.dp
    }
    val borderColor = when (visualState) {
        TvRailVisualState.FOCUSED,
        TvRailVisualState.SELECTED_FOCUSED,
        -> colors.goldBright

        TvRailVisualState.SELECTED -> colors.goldBright.copy(alpha = TV_RAIL_SELECTED_BORDER_ALPHA)
        TvRailVisualState.IDLE -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.itemHeightDp.dp)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { itemFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TvRailSelectionMarkerInset)
                    .width(TvRailSelectionMarkerWidth)
                    .fillMaxHeight(TV_RAIL_SELECTION_MARKER_HEIGHT_FRACTION)
                    .background(colors.goldBright, RoundedCornerShape(percent = 50)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.itemHorizontalPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) colors.goldBright else colors.textMuted,
                modifier = Modifier.size(metrics.iconSizeDp.dp),
            )
            if (expanded) {
                Spacer(Modifier.width(metrics.iconLabelGapDp.dp))
                Text(
                    text = label,
                    color = if (active) colors.text else colors.textMuted,
                    fontSize = metrics.labelSizeSp.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

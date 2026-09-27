package sa.hulksa.player.ui.screens

import sa.hulksa.player.ui.adaptive.tvPremiumWindowPolicy

internal data class TvRailMetrics(
    val collapsedWidthDp: Float,
    val expandedWidthDp: Float,
    val logoSizeDp: Float,
    val itemHeightDp: Float,
    val iconSizeDp: Float,
    val labelSizeSp: Float,
    val outerHorizontalPaddingDp: Float,
    val itemHorizontalPaddingDp: Float,
    val iconLabelGapDp: Float,
    val logoItemGapDp: Float,
    val itemGapDp: Float,
    val topPaddingDp: Float,
    val bottomPaddingDp: Float,
    val cornerRadiusDp: Float,
)

/**
 * Width the TV shell reserves for the rail in the measured page layout.
 *
 * Only the collapsed footprint participates in shell measurement, so expanding the rail can never
 * remeasure or translate the content beside it.
 */
internal fun tvRailShellReservedWidthDp(metrics: TvRailMetrics): Float =
    metrics.collapsedWidthDp

/**
 * Width of the rail's visual surface. When expanded on television it exceeds the reserved
 * footprint because it is drawn over the content-facing side instead of being measured.
 */
internal fun tvRailOverlayWidthDp(metrics: TvRailMetrics, expanded: Boolean): Float =
    if (expanded) metrics.expandedWidthDp else metrics.collapsedWidthDp

internal fun tvRailMetrics(screenWidthDp: Int, screenHeightDp: Int): TvRailMetrics {
    val policy = tvPremiumWindowPolicy(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
    )

    return TvRailMetrics(
        collapsedWidthDp = policy.railCollapsedWidthDp,
        expandedWidthDp = policy.railExpandedWidthDp,
        logoSizeDp = policy.railLogoSizeDp,
        itemHeightDp = policy.railItemHeightDp,
        iconSizeDp = policy.railIconSizeDp,
        labelSizeSp = policy.railLabelSizeSp,
        outerHorizontalPaddingDp = policy.railOuterHorizontalPaddingDp,
        itemHorizontalPaddingDp = policy.railItemHorizontalPaddingDp,
        iconLabelGapDp = policy.railIconLabelGapDp,
        logoItemGapDp = policy.railLogoItemGapDp,
        itemGapDp = policy.railItemGapDp,
        topPaddingDp = policy.railTopPaddingDp,
        bottomPaddingDp = policy.railBottomPaddingDp,
        cornerRadiusDp = policy.railCornerRadiusDp,
    )
}

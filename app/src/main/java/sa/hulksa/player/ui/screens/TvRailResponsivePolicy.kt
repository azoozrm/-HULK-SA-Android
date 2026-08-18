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

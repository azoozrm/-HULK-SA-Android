package sa.hulksa.player.ui.screens

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
    val width = screenWidthDp.coerceAtLeast(1).toFloat()
    val height = screenHeightDp.coerceAtLeast(1).toFloat()
    val shortSide = minOf(width, height)

    val collapsedWidth = (width / 14f).coerceIn(88f, 102f)
    val expandedWidth = (width / 6.2f).coerceIn(194f, 236f)
    val logoSize = (shortSide / 10f).coerceIn(54f, 78f)
    val itemHeight = (height / 14.5f).coerceIn(46f, 56f)
    val iconSize = (height / 30f).coerceIn(23f, 28f)
    val labelSize = (height / 50f).coerceIn(14f, 17f)
    val outerHorizontalPadding = (collapsedWidth * .11f).coerceIn(9f, 12f)
    val itemHorizontalPadding = (expandedWidth * .064f).coerceIn(12f, 15f)
    val iconLabelGap = (expandedWidth * .052f).coerceIn(10f, 13f)
    val logoItemGap = (height / 72f).coerceIn(9f, 15f)
    val itemGap = (height / 300f).coerceIn(2f, 4f)
    val topPadding = (height / 28f).coerceIn(20f, 30f)
    val bottomPadding = (height / 36f).coerceIn(16f, 24f)
    val cornerRadius = (itemHeight * .25f).coerceIn(11f, 14f)

    return TvRailMetrics(
        collapsedWidthDp = collapsedWidth,
        expandedWidthDp = expandedWidth,
        logoSizeDp = logoSize,
        itemHeightDp = itemHeight,
        iconSizeDp = iconSize,
        labelSizeSp = labelSize,
        outerHorizontalPaddingDp = outerHorizontalPadding,
        itemHorizontalPaddingDp = itemHorizontalPadding,
        iconLabelGapDp = iconLabelGap,
        logoItemGapDp = logoItemGap,
        itemGapDp = itemGap,
        topPaddingDp = topPadding,
        bottomPaddingDp = bottomPadding,
        cornerRadiusDp = cornerRadius,
    )
}

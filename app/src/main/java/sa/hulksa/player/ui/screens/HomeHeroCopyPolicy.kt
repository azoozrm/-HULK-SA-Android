package sa.hulksa.player.ui.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val HOME_HERO_TITLE_MAX_LINES = 2
internal const val HOME_HERO_GENRE_MAX_LABELS = 2
internal const val HOME_HERO_FACT_MAX_ROWS = 2

/**
 * Home-hero-only typography and reservation values. The same instance is used by the renderer,
 * the copy placement slot budget and the minimum-height calculation so the title, genre and facts
 * band cannot disagree with the height that reserves it.
 *
 * `sp` values intentionally stay in sp; the derived dp slots include the current font scale. The
 * technical-facts row floor also accounts for the quality-chip minimum, which does not scale.
 */
internal data class HomeHeroCopyMetrics(
    val isTv: Boolean,
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val titleSlotDp: Dp,
    val genreFontSize: TextUnit,
    val genreLineHeight: TextUnit,
    val genreSlotDp: Dp,
    val factsFontSize: TextUnit,
    val factsLineHeight: TextUnit,
    val factsFontSizeSp: Int,
    val factsLineHeightSp: Int,
    val factsRowHeightDp: Dp,
    val factsRowGapDp: Dp,
    val factsSlotDp: Dp,
    val maxFactsRows: Int,
    val topGapDp: Dp,
    val titleGenreGapDp: Dp,
    val genreFactsGapDp: Dp,
    val factsActionsGapDp: Dp,
    val bottomPaddingDp: Dp,
    val factsGroupHorizontalPaddingDp: Dp,
    val factsSeparatorHeightDp: Dp,
    val factsIconSizeDp: Dp,
    val qualityChipMinHeightDp: Dp,
    val genreSeparatorHorizontalPaddingDp: Dp,
)

internal fun homeHeroCopyMetrics(isTv: Boolean, fontScale: Float = 1f): HomeHeroCopyMetrics {    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val titleLineHeightSp = 34
    val genreLineHeightSp = 18
    val factsFontSizeSp = if (isTv) 13 else 12
    val factsLineHeightSp = if (isTv) 20 else 18
    val factsRowHeightDp = maxOf(
        if (isTv) 24f else 20f,
        factsLineHeightSp * scale,
    )
    val factsRowGapDp = if (isTv) 8f else 6f
    val factsSlotDp = factsRowHeightDp * HOME_HERO_FACT_MAX_ROWS +
        factsRowGapDp * (HOME_HERO_FACT_MAX_ROWS - 1)
    return HomeHeroCopyMetrics(
        isTv = isTv,
        titleFontSize = 28.sp,
        titleLineHeight = titleLineHeightSp.sp,
        titleSlotDp = (titleLineHeightSp * scale * HOME_HERO_TITLE_MAX_LINES).dp,
        genreFontSize = 12.sp,
        genreLineHeight = genreLineHeightSp.sp,
        genreSlotDp = (genreLineHeightSp * scale).dp,
        factsFontSize = factsFontSizeSp.sp,
        factsLineHeight = factsLineHeightSp.sp,
        factsFontSizeSp = factsFontSizeSp,
        factsLineHeightSp = factsLineHeightSp,
        factsRowHeightDp = factsRowHeightDp.dp,
        factsRowGapDp = factsRowGapDp.dp,
        factsSlotDp = factsSlotDp.dp,
        maxFactsRows = HOME_HERO_FACT_MAX_ROWS,
        topGapDp = if (isTv) 16.dp else 12.dp,
        titleGenreGapDp = 6.dp,
        genreFactsGapDp = 4.dp,
        factsActionsGapDp = if (isTv) 8.dp else 10.dp,
        bottomPaddingDp = if (isTv) 40.dp else 24.dp,
        factsGroupHorizontalPaddingDp = if (isTv) 10.dp else 8.dp,
        factsSeparatorHeightDp = if (isTv) 18.dp else 14.dp,
        factsIconSizeDp = if (isTv) 17.dp else 14.dp,
        qualityChipMinHeightDp = if (isTv) 24.dp else 20.dp,
        genreSeparatorHorizontalPaddingDp = 5.dp,
    )
}

/**
 * Home-only genre style derived from the inherited application style. Only the approved size,
 * line height and regular weight are overridden; the IBM Plex family and any other inherited
 * style properties remain.
 */
internal fun homeHeroGenreTextStyle(
    base: TextStyle,
    metrics: HomeHeroCopyMetrics,
): TextStyle = base.copy(
    fontSize = metrics.genreFontSize,
    lineHeight = metrics.genreLineHeight,
    fontWeight = FontWeight.Normal,
)

/**
 * Home-only technical-fact style derived from the same inherited application style. Only the
 * approved size, line height and medium weight are overridden.
 */
internal fun homeHeroFactTextStyle(
    base: TextStyle,
    metrics: HomeHeroCopyMetrics,
): TextStyle = base.copy(
    fontSize = metrics.factsFontSize,
    lineHeight = metrics.factsLineHeight,
    fontWeight = FontWeight.Medium,
)

/**
 * Home-hero floor that can carry the normal copy band: top gap, two-line title slot, one genre
 * line, at most two measured technical-fact rows, action gap, bottom padding and the measured
 * actions. It is a viewport/font-scale value, never item- or metadata-dependent; the measured
 * compact/minimal fallback remains for the rare case where the supplied band is still shorter.
 */
internal fun heroRequiredHeightDp(
    headerMinHeightDp: Dp,
    actionsHeightDp: Dp,
    metrics: HomeHeroCopyMetrics,
): Dp = headerMinHeightDp + metrics.topGapDp + metrics.titleSlotDp +
    metrics.titleGenreGapDp + metrics.genreSlotDp + metrics.genreFactsGapDp +
    metrics.factsSlotDp + metrics.factsActionsGapDp + metrics.bottomPaddingDp +
    actionsHeightDp

/** Preserve the accepted policy height whenever it can carry the real worst-case copy band. */
internal fun effectiveHeroHeightDp(
    policyHeightDp: Dp,
    headerMinHeightDp: Dp,
    actionsHeightDp: Dp,
    metrics: HomeHeroCopyMetrics,
): Dp = maxOf(
    policyHeightDp,
    heroRequiredHeightDp(
        headerMinHeightDp = headerMinHeightDp,
        actionsHeightDp = actionsHeightDp,
        metrics = metrics,
    ),
)

/**
 * Provider genre delimiters verified in the existing catalog/recommendation paths. Ampersands are
 * deliberately excluded so compound labels such as `Action & Adventure` remain one label.
 */
private val HOME_HERO_GENRE_DELIMITERS = charArrayOf(',', '،', '/', '|', ';')

/** Complete unique genre labels in provider order. Blank entries are ignored. */
internal fun homeHeroGenreLabels(raw: String?): List<String> = raw.orEmpty()
    .split(*HOME_HERO_GENRE_DELIMITERS)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal data class HomeHeroGenreLineSelection(
    val labelIndices: List<Int> = emptyList(),
    val omittedCount: Int = 0,
) {
    val countOnly: Boolean get() = labelIndices.isEmpty() && omittedCount > 0
}

/**
 * Chooses the longest provider-order prefix (at most two labels) that fits together with the
 * centered-dot separators and the `+N` remainder. A zero-label result intentionally renders the
 * count only rather than truncating a label.
 */
internal fun homeHeroGenreLineSelection(
    labelWidthsPx: List<Int>,
    availableWidthPx: Int,
    separatorWidthPx: Int,
    countWidthPx: (Int) -> Int,
    maxLabels: Int = HOME_HERO_GENRE_MAX_LABELS,
): HomeHeroGenreLineSelection {
    val total = labelWidthsPx.size
    if (total == 0) return HomeHeroGenreLineSelection()
    val limit = maxLabels.coerceAtLeast(0).coerceAtMost(total)
    val width = availableWidthPx.coerceAtLeast(0)
    val separator = separatorWidthPx.coerceAtLeast(0)
    for (count in limit downTo 0) {
        val omitted = total - count
        var used = labelWidthsPx.take(count).sumOf { it.coerceAtLeast(0) }
        if (count > 1) used += separator * (count - 1)
        if (omitted > 0) {
            if (count > 0) used += separator
            used += countWidthPx(omitted).coerceAtLeast(0)
        }
        if (used <= width || count == 0) {
            return HomeHeroGenreLineSelection(
                labelIndices = (0 until count).toList(),
                omittedCount = omitted,
            )
        }
    }
    return HomeHeroGenreLineSelection(omittedCount = total)
}

internal fun homeHeroGenreRemainderLabel(count: Int): String = "+$count"

internal data class HomeHeroFactRow(
    val groupIndices: List<Int>,
    val widthPx: Int,
)

/**
 * Truthful measured layout for the technical-facts block. [rows] is empty when [fits] is false;
 * in that case no child may be placed, and [requiredWidthPx] is the smallest possible maximum-row
 * width for the approved row limit. The renderer must consume this outcome before placement.
 */
internal data class HomeHeroFactRowLayout(
    val rows: List<HomeHeroFactRow>,
    val fits: Boolean,
    val requiredWidthPx: Int,
    val overWideGroupIndices: List<Int> = emptyList(),
) {
    val placedGroupIndices: List<Int> get() = rows.flatMap { it.groupIndices }
    val maxRowWidthPx: Int get() = rows.maxOfOrNull { it.widthPx } ?: 0
}

/**
 * Caller-resolved facts presentation. [widthPx] is the exact measured width the block may use:
 * the normal copy width when it fits, otherwise the minimum required width within the caller's
 * existing maximum capacity. When even [maxWidthPx] cannot carry all complete groups, [fits] is
 * false and [layout.requiredWidthPx] records the capacity that would be needed.
 */
internal data class HomeHeroFactPresentation(
    val layout: HomeHeroFactRowLayout,
    val widthPx: Int,
)

/**
 * Resolves the facts block inside the caller's existing capacity. The normal copy width is
 * preferred so the accepted composition is unchanged; if it is insufficient, the block may grow
 * toward the caller's maximum available width (the same hero content bounds) just enough to fit.
 * It never grows beyond [maxWidthPx].
 */
internal fun homeHeroFactPresentation(
    groupWidthsPx: List<Int>,
    preferredWidthPx: Int,
    maxWidthPx: Int,
    separatorWidthPx: Int,
    maxRows: Int = HOME_HERO_FACT_MAX_ROWS,
): HomeHeroFactPresentation {
    val maximum = maxWidthPx.coerceAtLeast(0)
    val preferred = preferredWidthPx.coerceAtMost(maximum).coerceAtLeast(0)
    val preferredLayout = homeHeroFactRowLayout(
        groupWidthsPx = groupWidthsPx,
        availableWidthPx = preferred,
        separatorWidthPx = separatorWidthPx,
        maxRows = maxRows,
    )
    if (preferredLayout.fits) {
        return HomeHeroFactPresentation(layout = preferredLayout, widthPx = preferred)
    }
    val required = preferredLayout.requiredWidthPx.coerceAtMost(maximum)
    if (preferredLayout.requiredWidthPx <= maximum) {
        val requiredLayout = homeHeroFactRowLayout(
            groupWidthsPx = groupWidthsPx,
            availableWidthPx = required,
            separatorWidthPx = separatorWidthPx,
            maxRows = maxRows,
        )
        if (requiredLayout.fits) {
            return HomeHeroFactPresentation(layout = requiredLayout, widthPx = required)
        }
    }
    val maximumLayout = homeHeroFactRowLayout(
        groupWidthsPx = groupWidthsPx,
        availableWidthPx = maximum,
        separatorWidthPx = separatorWidthPx,
        maxRows = maxRows,
    )
    return HomeHeroFactPresentation(
        layout = if (maximumLayout.fits) maximumLayout else preferredLayout,
        widthPx = maximum,
    )
}

/**
 * Exhaustive whole-group row partition for the small technical-facts set. Rows preserve provider
 * field order, each row is non-empty, and every row width includes the separators that will be
 * placed between its groups. A layout is returned only when all groups fit within [maxRows]
 * measured rows and every row is inside [availableWidthPx]. Otherwise [fits] is false, [rows] is
 * empty, and the measured minimum required width plus any over-wide groups are surfaced.
 */
internal fun homeHeroFactRowLayout(
    groupWidthsPx: List<Int>,
    availableWidthPx: Int,
    separatorWidthPx: Int,
    maxRows: Int = HOME_HERO_FACT_MAX_ROWS,
): HomeHeroFactRowLayout {
    val widths = groupWidthsPx.map { it.coerceAtLeast(0) }
    if (widths.isEmpty()) {
        return HomeHeroFactRowLayout(rows = emptyList(), fits = true, requiredWidthPx = 0)
    }
    val available = availableWidthPx.coerceAtLeast(0)
    val separator = separatorWidthPx.coerceAtLeast(0)
    val rowLimit = maxRows.coerceAtLeast(1).coerceAtMost(widths.size)
    val count = widths.size
    val unreachable = Int.MAX_VALUE / 4

    fun rowWidth(start: Int, endInclusive: Int): Int {
        var total = 0
        for (index in start..endInclusive) {
            if (index > start) total += separator
            total += widths[index]
        }
        return total
    }

    fun partitionFits(
        start: Int,
        rowsLeft: Int,
        rows: MutableList<HomeHeroFactRow>,
    ): Boolean {
        if (start == count) return rowsLeft == 0
        if (rowsLeft <= 0 || count - start < rowsLeft) return false
        var end = start
        while (end < count) {
            val width = rowWidth(start, end)
            if (width > available) break
            rows += HomeHeroFactRow(groupIndices = (start..end).toList(), widthPx = width)
            if (partitionFits(end + 1, rowsLeft - 1, rows)) return true
            rows.removeAt(rows.lastIndex)
            end++
        }
        return false
    }

    fun minimumMaxRowWidth(start: Int, rowsLeft: Int): Int {
        if (start == count) return if (rowsLeft == 0) 0 else unreachable
        if (rowsLeft <= 0 || count - start < rowsLeft) return unreachable
        var best = unreachable
        var end = start
        while (end < count) {
            val width = rowWidth(start, end)
            val tail = minimumMaxRowWidth(end + 1, rowsLeft - 1)
            if (tail != unreachable) best = minOf(best, maxOf(width, tail))
            end++
        }
        return best
    }

    for (rowCount in 1..rowLimit) {
        val rows = mutableListOf<HomeHeroFactRow>()
        if (partitionFits(start = 0, rowsLeft = rowCount, rows = rows)) {
            return HomeHeroFactRowLayout(
                rows = rows,
                fits = true,
                requiredWidthPx = rows.maxOf { it.widthPx },
            )
        }
    }
    val requiredWidth = (1..rowLimit)
        .map { rowCount -> minimumMaxRowWidth(0, rowCount) }
        .minOrNull()
        ?: unreachable
    return HomeHeroFactRowLayout(
        rows = emptyList(),
        fits = false,
        requiredWidthPx = requiredWidth,
        overWideGroupIndices = widths.indices.filter { widths[it] > available },
    )
}


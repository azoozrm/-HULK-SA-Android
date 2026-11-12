package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import sa.hulksa.player.data.HomeHeroTechnicalMetadata
import sa.hulksa.player.hulkPerfLog
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.ui.components.SeriesEpisodeCountInline
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Home-only hero metadata. Genres get their own single measured line; technical facts get a
 * separate line (normally one row on TV, at most two measured rows) with whole field groups.
 * The shared Details metadata renderers keep their accepted defaults and are not modified.
 */
@Composable
internal fun HomeHeroFacts(
    item: ContentItem,
    isTv: Boolean,
    metadata: HomeHeroTechnicalMetadata,
    metrics: HomeHeroCopyMetrics,
    heroIdentity: String,
    preferredWidthPx: Int,
    maxWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val entries = when (item.type) {
        ContentType.MOVIE -> movieHomeHeroFactEntries(item, isTv, metadata)
        ContentType.SERIES -> seriesHomeHeroFactEntries(item, isTv, metadata)
        ContentType.LIVE -> emptyList()
    }
    if (entries.isEmpty()) return
    HomeHeroFactRows(
        entries = entries,
        metrics = metrics,
        heroIdentity = heroIdentity,
        preferredWidthPx = preferredWidthPx,
        maxWidthPx = maxWidthPx,
        modifier = modifier,
    )
}

@Composable
internal fun HomeHeroGenreLine(
    genre: String?,
    metrics: HomeHeroCopyMetrics,
    modifier: Modifier = Modifier,
) {
    val labels = remember(genre) { homeHeroGenreLabels(genre) }
    if (labels.isEmpty()) {
        Spacer(modifier.height(metrics.genreSlotDp))
        return
    }

    val colors = LocalHulkColors.current
    val density = LocalDensity.current
    val genreSlotPx = with(density) { metrics.genreSlotDp.roundToPx() }
    val genreRightInsetPx = with(density) { metrics.factsGroupHorizontalPaddingDp.roundToPx() }
    // R19 TV alignment: the accepted width budget keeps the inset, while the visible content edge
    // moves onto the shared Home header axis. Non-TV compensation is zero, so it is unchanged.
    val anchorShiftPx = with(density) { homeHeroMetadataEdgeCompensationDp(metrics).roundToPx() }
    val labelStyle = homeHeroGenreTextStyle(LocalTextStyle.current, metrics)
    val labelColor = colors.text
    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth.coerceAtLeast(0)
        val contentWidth = (width - genreRightInsetPx).coerceAtLeast(0)
        val labelPlaceables = labels.mapIndexed { index, label ->
            subcompose("hero-genre-label-$index") {
                Text(
                    text = label,
                    style = labelStyle,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Right,
                )
            }.first().measure(Constraints())
        }
        val dotPlaceables = (0..HOME_HERO_GENRE_MAX_LABELS).map { index ->
            subcompose("hero-genre-dot-$index") {
                Text(
                    text = "·",
                    style = labelStyle,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        horizontal = metrics.genreSeparatorHorizontalPaddingDp,
                    ),
                )
            }.first().measure(Constraints())
        }
        val countPlaceables = (1..labels.size).associateWith { count ->
            subcompose("hero-genre-count-$count") {
                Text(
                    text = homeHeroGenreRemainderLabel(count),
                    style = labelStyle.copy(textDirection = TextDirection.Ltr),
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }.first().measure(Constraints())
        }
        val selection = homeHeroGenreLineSelection(
            labelWidthsPx = labelPlaceables.map { it.width },
            availableWidthPx = contentWidth,
            separatorWidthPx = dotPlaceables.first().width,
            countWidthPx = { count -> countPlaceables.getValue(count).width },
        )
        layout(width, genreSlotPx) {
            var x = width - genreRightInsetPx + anchorShiftPx
            selection.labelIndices.forEachIndexed { order, labelIndex ->
                val label = labelPlaceables[labelIndex]
                x -= label.width
                label.place(x, (genreSlotPx - label.height) / 2)
                if (order < selection.labelIndices.lastIndex) {
                    val dot = dotPlaceables[order]
                    x -= dot.width
                    dot.place(x, (genreSlotPx - dot.height) / 2)
                }
            }
            if (selection.omittedCount > 0) {
                if (selection.labelIndices.isNotEmpty()) {
                    val dot = dotPlaceables[selection.labelIndices.lastIndex]
                    x -= dot.width
                    dot.place(x, (genreSlotPx - dot.height) / 2)
                }
                val count = countPlaceables.getValue(selection.omittedCount)
                x -= count.width
                count.place(x, (genreSlotPx - count.height) / 2)
            }
        }
    }
}

internal data class HomeHeroFactEntry(
    val text: String = "",
    val quality: Boolean = false,
    val rating: Boolean = false,
    val clock: Boolean = false,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
)

internal fun movieHomeHeroFactEntries(
    item: ContentItem,
    isTv: Boolean,
    metadata: HomeHeroTechnicalMetadata,
): List<HomeHeroFactEntry> = listOfNotNull(
    item.year?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it) },
    (if (isTv) detailsTvDuration(metadata.durationMs) else detailsProDurationLabel(metadata.durationMs))
        ?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it, clock = true) },
    metadata.quality?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it, quality = true) },
    (if (isTv) detailsTvRating(item.rating) else detailsProRatingLabel(item.rating))
        ?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it, rating = true) },
)

internal fun seriesHomeHeroFactEntries(
    item: ContentItem,
    isTv: Boolean,
    metadata: HomeHeroTechnicalMetadata,
): List<HomeHeroFactEntry> = listOfNotNull(
    metadata.quality?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it, quality = true) },
    metadata.seasonCount?.takeIf { it > 0 }?.let { HomeHeroFactEntry(seasonCount = it) },
    metadata.episodeCount?.takeIf { it > 0 }?.let { HomeHeroFactEntry(episodeCount = it) },
    (if (isTv) detailsTvRating(item.rating) else detailsProRatingLabel(item.rating))
        ?.trim()?.takeIf(String::isNotBlank)?.let { HomeHeroFactEntry(it, rating = true) },
)

@Composable
private fun HomeHeroFactRows(
    entries: List<HomeHeroFactEntry>,
    metrics: HomeHeroCopyMetrics,
    heroIdentity: String,
    preferredWidthPx: Int,
    maxWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val factsRowGapPx = with(density) { metrics.factsRowGapDp.roundToPx() }
    val separatorWidthPx = with(density) { 1.dp.roundToPx() }
    // R19/R20 TV alignment: the visible content edge of every row lands on the accepted Home header
    // axis. R20 removes the leading group's unused physical-right outer gutter from its rendered
    // measurement instead of placing that padded box outside the row's reported bounds; the logical
    // widths used for the accepted fit/partition decision are unchanged. Non-TV compensation is 0.
    val compensatesLeadingEdge = homeHeroMetadataEdgeCompensationDp(metrics) > 0.dp
    SubcomposeLayout(modifier = modifier) { constraints ->
        val fullWidth = constraints.maxWidth.coerceAtLeast(0)
        val groupPlaceables = entries.mapIndexed { index, entry ->
            subcompose("hero-fact-$index") {
                HomeHeroFactEntryContent(entry = entry, metrics = metrics)
            }.first().measure(Constraints())
        }
        val presentation = homeHeroFactPresentation(
            groupWidthsPx = groupPlaceables.map { it.width },
            preferredWidthPx = preferredWidthPx,
            maxWidthPx = maxWidthPx.coerceAtMost(fullWidth),
            separatorWidthPx = separatorWidthPx,
            maxRows = metrics.maxFactsRows,
        )
        val layoutResult = presentation.layout
        if (!layoutResult.fits) {
            hulkPerfLog {
                "homeHeroFactsUnfit identity=$heroIdentity preferredWidth=$preferredWidthPx " +
                    "maxWidth=${maxWidthPx.coerceAtMost(fullWidth)} " +
                    "requiredWidth=${layoutResult.requiredWidthPx} " +
                    "overWide=${layoutResult.overWideGroupIndices}"
            }
            layout(presentation.widthPx, 0) { }
        } else {
            val rows = layoutResult.rows
            val rowHeights = rows.map { row ->
                row.groupIndices.maxOf { groupPlaceables[it].height }
            }
            val totalHeight = rowHeights.sum() +
                factsRowGapPx * (rows.size - 1).coerceAtLeast(0)
            val separators = rows.mapIndexed { rowIndex, row ->
                (0 until (row.groupIndices.size - 1).coerceAtLeast(0)).map { gapIndex ->
                    subcompose("hero-fact-sep-$rowIndex-$gapIndex") {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(metrics.factsSeparatorHeightDp)
                                .background(Color.White.copy(alpha = .22f)),
                        )
                    }.first().measure(Constraints())
                }
            }
            // The leading group of each row is measured again without its unused outer-right
            // gutter, so its rendered box stays inside the row while its visible content still
            // ends exactly on the shared axis.
            val leadingPlaceables = if (compensatesLeadingEdge) {
                rows.map { row -> row.groupIndices.first() }.distinct().associateWith { index ->
                    subcompose("hero-fact-leading-$index") {
                        HomeHeroFactEntryContent(
                            entry = entries[index],
                            metrics = metrics,
                            omitLeadingEdgeGutter = true,
                        )
                    }.first().measure(Constraints())
                }
            } else {
                emptyMap()
            }
            val rowRects = rows.map { row ->
                val renderWidths = row.groupIndices.mapIndexed { order, index ->
                    if (order == 0) {
                        leadingPlaceables[index]?.width ?: groupPlaceables[index].width
                    } else {
                        groupPlaceables[index].width
                    }
                }
                homeHeroFactRowRects(
                    parentWidthPx = presentation.widthPx,
                    renderWidthsPx = renderWidths,
                    separatorWidthPx = separatorWidthPx,
                )
            }
            if (rowRects.any { it == null }) {
                hulkPerfLog {
                    "homeHeroFactsOutOfBounds identity=$heroIdentity " +
                        "width=${presentation.widthPx} rows=${layoutResult.rows.size}"
                }
                layout(presentation.widthPx, 0) { }
            } else {
                layout(presentation.widthPx, totalHeight) {
                    var y = 0
                    rows.forEachIndexed { rowIndex, row ->
                        val rowHeight = rowHeights[rowIndex]
                        val rects = rowRects[rowIndex].orEmpty()
                        var groupOrder = 0
                        rects.forEach { rect ->
                            if (rect.separator) {
                                val separator = separators[rowIndex][groupOrder - 1]
                                separator.place(
                                    rect.leftPx,
                                    y + (rowHeight - separator.height) / 2,
                                )
                            } else {
                                val index = row.groupIndices[groupOrder]
                                val placeable = if (groupOrder == 0) {
                                    leadingPlaceables[index] ?: groupPlaceables[index]
                                } else {
                                    groupPlaceables[index]
                                }
                                placeable.place(
                                    rect.leftPx,
                                    y + (rowHeight - placeable.height) / 2,
                                )
                                groupOrder++
                            }
                        }
                        y += rowHeight + factsRowGapPx
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroFactEntryContent(
    entry: HomeHeroFactEntry,
    metrics: HomeHeroCopyMetrics,
    omitLeadingEdgeGutter: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val textStyle = homeHeroFactTextStyle(LocalTextStyle.current, metrics)
    // R20: the row-leading group drops only its unused physical-right outer gutter; every interior
    // gutter, separator gap and the chip's own 7dp padding/border stay exactly as accepted.
    val groupPadding = if (omitLeadingEdgeGutter) {
        Modifier.absolutePadding(
            left = metrics.factsGroupHorizontalPaddingDp,
            right = 0.dp,
        )
    } else {
        Modifier.padding(horizontal = metrics.factsGroupHorizontalPaddingDp)
    }
    when {
        entry.seasonCount != null -> Box(
            modifier = groupPadding,
            contentAlignment = Alignment.Center,
        ) {
            SeriesEpisodeCountInline(
                count = entry.seasonCount,
                word = "موسم",
                fontSizeSp = metrics.factsFontSizeSp,
                lineHeightSp = metrics.factsLineHeightSp,
                textColor = colors.text,
                fontWeight = FontWeight.Medium,
            )
        }
        entry.episodeCount != null -> Box(
            modifier = groupPadding,
            contentAlignment = Alignment.Center,
        ) {
            SeriesEpisodeCountInline(
                count = entry.episodeCount,
                fontSizeSp = metrics.factsFontSizeSp,
                lineHeightSp = metrics.factsLineHeightSp,
                textColor = colors.text,
                fontWeight = FontWeight.Medium,
            )
        }
        entry.quality -> Box(
            modifier = groupPadding
                .heightIn(min = metrics.qualityChipMinHeightDp)
                .clip(RoundedCornerShape(5.dp))
                .border(1.dp, colors.gold.copy(alpha = .80f), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.text,
                color = colors.gold,
                style = textStyle,
                maxLines = 1,
            )
        }
        entry.rating || entry.clock -> Row(
            modifier = groupPadding,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = entry.text,
                color = colors.text,
                style = textStyle,
                maxLines = 1,
            )
            // The icon is the last child, so in RTL it renders physically LEFT of the value.
            Icon(
                imageVector = if (entry.rating) Icons.Rounded.Star else Icons.Rounded.Schedule,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(metrics.factsIconSizeDp),
            )
        }
        else -> Text(
            text = entry.text,
            color = colors.text,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = groupPadding,
        )
    }
}

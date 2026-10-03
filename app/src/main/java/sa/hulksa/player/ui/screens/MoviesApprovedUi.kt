package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Truthful app-authored download action label for the approved movie actions. State glyphs are
 * replaced by the surrounding real icon component and the percentage stays separate.
 */
internal fun movieDownloadActionLabel(download: OfflineDownload?): String = when (download?.status) {
    OfflineStatus.COMPLETED -> "تم التحميل"
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> "ايقاف التحميل ${(download.progress * 100).toInt().coerceIn(0, 100)}%"
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> "استئناف التحميل"
    OfflineStatus.FAILED -> "اعادة التحميل"
    null -> "تحميل الفلم"
}

/**
 * Owner-approved movie details content. Three persistent tabs with exclusive content: the
 * specification overrides the related-works draft that omitted the middle tab.
 */
internal enum class MovieDetailsTab(val label: String) {
    STORY("القصة"),
    INFORMATION("معلومات الفلم"),
    RELATED("افلام مشابهة"),
}

@Composable
internal fun MovieDetailsTabRow(
    selected: MovieDetailsTab,
    onSelect: (MovieDetailsTab) -> Unit,
    requesters: Map<MovieDetailsTab, FocusRequester>,
    upTarget: FocusRequester?,
    downTargets: Map<MovieDetailsTab, FocusRequester?>,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onSelectedTabScrollKey: ((KeyEvent) -> Boolean)? = null,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovieDetailsTab.entries.forEach { tab ->
            var focused by remember(tab) { mutableStateOf(false) }
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .goldFocusEdge(shape = RoundedCornerShape(10.dp), visible = focused)
                    .background(if (focused) colors.gold.copy(alpha = .10f) else Color.Transparent)
                    .then(
                        requesters[tab]?.let { requester ->
                            Modifier
                                .focusRequester(requester)
                                .focusProperties {
                                    up = upTarget ?: FocusRequester.Cancel
                                    down = downTargets[tab] ?: FocusRequester.Cancel
                                }
                        } ?: Modifier,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        // Only the selected tab can drive the parent page read-scroll. Moving
                        // focus across tabs alone neither activates nor scrolls anything.
                        if (isSelected && onSelectedTabScrollKey != null) {
                            Modifier.onPreviewKeyEvent(onSelectedTabScrollKey)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = if (isTv) 20.dp else 13.dp, vertical = if (isTv) 11.dp else 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tab.label,
                    // Round 3 owner override: all three tab labels are ivory, selected or not.
                    // Selection is shown by the heavier weight and the ivory underline below.
                    color = colors.text,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(if (isTv) 58.dp else 44.dp)
                        .height(2.dp)
                        .background(if (isSelected) colors.text else Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun MovieDetailsStoryContent(
    plot: String?,
    isTv: Boolean,
    horizontalPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    if (plot.isNullOrBlank()) {
        MovieDetailsEmptyTabMessage("لا يوجد وصف متاح لهذا الفلم", isTv, modifier)
        return
    }
    Text(
        text = plot,
        color = colors.text,
        fontSize = if (isTv) 15.sp else 13.sp,
        lineHeight = if (isTv) 24.sp else 21.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPaddingDp.dp, vertical = if (isTv) 10.dp else 14.dp),
    )
}

@Composable
internal fun MovieDetailsEmptyTabMessage(
    message: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = if (isTv) 28.dp else 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = colors.textMuted, fontSize = if (isTv) 14.sp else 12.sp)
    }
}

private data class MovieInfoCell(val label: String, val value: String)

/**
 * Truthful information table built only from available movie fields. Missing fields are omitted
 * rather than invented; the layout uses the existing details model.
 */
@Composable
internal fun MovieDetailsInfoGrid(
    item: ContentItem,
    details: ContentDetails?,
    durationLabel: String?,
    qualityLabel: String?,
    ratingLabel: String?,
    releaseYearLabel: String?,
    horizontalPaddingDp: Int,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val year = item.year?.trim()?.takeIf(String::isNotBlank) ?: releaseYearLabel?.trim()?.takeIf(String::isNotBlank)
    val cells = listOfNotNull(
        item.name.trim().takeIf(String::isNotBlank)?.let { MovieInfoCell("اسم الفلم", it) },
        (details?.genre ?: item.genre)?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("التصنيف", it) },
        durationLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("المدة", it) },
        qualityLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("الجودة", it) },
        ratingLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("التقييم", it) },
        year?.let { MovieInfoCell("سنة العرض", it) },
        details?.director?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("الاخراج", it) },
        details?.cast?.trim()?.takeIf(String::isNotBlank)?.let { MovieInfoCell("البطولة", it) },
    )
    if (cells.isEmpty()) {
        MovieDetailsEmptyTabMessage("لا توجد معلومات متاحة لهذا الفلم", isTv, modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPaddingDp.dp)
            .padding(top = if (isTv) 8.dp else 12.dp, bottom = if (isTv) 18.dp else 22.dp),
    ) {
        cells.chunked(3).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = .08f)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { cellIndex, cell ->
                    if (cellIndex > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(if (isTv) 52.dp else 46.dp)
                                .background(Color.White.copy(alpha = .08f)),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = if (isTv) 13.dp else 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = cell.label,
                            // Information field labels are warm gold; real values stay ivory.
                            color = colors.gold,
                            fontSize = if (isTv) 12.sp else 11.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = cell.value,
                            color = colors.text,
                            fontSize = if (isTv) 15.sp else 13.sp,
                            lineHeight = if (isTv) 20.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(3 - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class MovieHeroMetadata(
    val text: String,
    val quality: Boolean = false,
    val rating: Boolean = false,
    val clock: Boolean = false,
)

/**
 * Centered hero metadata line. Warm-gold star/clock icons sit physically LEFT of their ivory
 * values, the known quality value keeps its small framed chip, and only available fields render.
 */
@Composable
internal fun MovieDetailsHeroMetadataRow(
    item: ContentItem,
    genre: String?,
    durationLabel: String?,
    qualityLabel: String?,
    ratingLabel: String?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val entries = listOfNotNull(
        item.year?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it) },
        genre?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it) },
        durationLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it, clock = true) },
        qualityLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it, quality = true) },
        ratingLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it, rating = true) },
    )
    if (entries.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(if (isTv) 18.dp else 14.dp)
                        .background(Color.White.copy(alpha = .22f)),
                )
            }
            when {
                entry.quality -> Box(
                    modifier = Modifier
                        .padding(horizontal = if (isTv) 10.dp else 8.dp)
                        .heightIn(min = if (isTv) 24.dp else 20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, colors.gold.copy(alpha = .80f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.text,
                        color = colors.gold,
                        fontSize = if (isTv) 13.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                entry.rating || entry.clock -> Row(
                    modifier = Modifier.padding(horizontal = if (isTv) 10.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = entry.text,
                        color = colors.text,
                        fontSize = if (isTv) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    // The icon is the last child, so in RTL it renders physically LEFT of the value.
                    Icon(
                        imageVector = if (entry.rating) Icons.Rounded.Star else Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.gold,
                        modifier = Modifier.size(if (isTv) 17.dp else 14.dp),
                    )
                }
                else -> Text(
                    text = entry.text,
                    color = colors.text,
                    fontSize = if (isTv) 14.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = if (isTv) 10.dp else 8.dp),
                )
            }
        }
    }
}

/**
 * Movie-only compact hero height. Constraint-aware instead of the shared Series floors, so a short
 * window or large font scale cannot be dominated by fixed Movie hero height.
 */
internal fun movieCompactHeroHeightDp(screenHeightDp: Int): Int {
    val height = screenHeightDp.coerceAtLeast(1)
    val contentAware = (height * .56f).toInt()
    val available = (height - 96).coerceAtLeast(200)
    return contentAware.coerceIn(220, 460).coerceAtMost(available)
}

/**
 * Authoritative inline resume gating for the Movie details strip. Mirrors the existing history
 * eligibility: a positive saved position on a known duration below the completed threshold.
 */
internal fun movieResumeProgress(positionMs: Long, durationMs: Long): Float? {
    if (positionMs <= 0L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f).takeIf { it < .95f }
}

/**
 * Index of the Movie Details tab row inside the parent page list (hero, optional error, tabs).
 */
internal fun movieDetailsTabsItemIndex(hasError: Boolean): Int = if (hasError) 2 else 1

/**
 * Stable keyed-layout identity of the selected Movie Details section item.
 */
internal fun movieDetailsSectionItemKey(tab: MovieDetailsTab, tvPolished: Boolean): String = when (tab) {
    MovieDetailsTab.STORY -> if (tvPolished) "movie_tv_polished_story" else "movie_story"
    MovieDetailsTab.INFORMATION -> if (tvPolished) "movie_tv_polished_information" else "movie_information"
    MovieDetailsTab.RELATED -> if (tvPolished) "movie_tv_polished_related_tab" else "movie_related"
}

/**
 * True when the selected Movie Details section is not completely inside the page viewport and the
 * parent list should reveal it. A not-yet-composed section counts as needing reveal.
 */
internal fun movieDetailsSectionNeedsReveal(
    sectionPresent: Boolean,
    sectionTop: Int,
    sectionBottom: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Boolean = !sectionPresent || sectionTop < viewportStart || sectionBottom > viewportEnd

/**
 * True when the selected Movie Details reading panel still extends below the viewport and the tab
 * row should keep scrolling the parent page on DOWN.
 */
internal fun movieDetailsPanelNeedsMoreScroll(
    sectionPresent: Boolean,
    sectionBottom: Int,
    viewportEnd: Int,
): Boolean = !sectionPresent || sectionBottom > viewportEnd

internal fun movieFormatPosition(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Truthful download icon mapping for the single Movie download action. The label mapping lives in
 * [movieDownloadActionLabel]; both read the existing download state owner only.
 */
internal fun movieDownloadActionIcon(download: OfflineDownload?): ImageVector = when (download?.status) {
    OfflineStatus.COMPLETED -> Icons.Rounded.Check
    OfflineStatus.QUEUED,
    OfflineStatus.CHECKING,
    OfflineStatus.DOWNLOADING,
    -> Icons.Rounded.Pause
    OfflineStatus.PAUSED,
    OfflineStatus.WAITING_SCHEDULE,
    OfflineStatus.WAITING_NETWORK,
    OfflineStatus.WAITING_STORAGE,
    -> Icons.Rounded.Download
    OfflineStatus.FAILED -> Icons.Rounded.Refresh
    null -> Icons.Rounded.Download
}

/**
 * Shallow Movie-only inline continue-watching panel. Progress is the clamped authoritative saved
 * position; the track starts at the RTL page start (physical right) and the clock trails the
 * wording on the physical left. Missing or invalid durations render nothing.
 */
@Composable
internal fun MovieInlineResumeStrip(
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(if (isTv) 12.dp else 10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xE612130E))
            .border(1.dp, colors.gold.copy(alpha = .38f), shape)
            .padding(horizontal = if (isTv) 14.dp else 11.dp, vertical = if (isTv) 9.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "اكمل المشاهدة من ${movieFormatPosition(positionMs)}",
                color = colors.text,
                fontSize = if (isTv) 13.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(7.dp))
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(if (isTv) 16.dp else 14.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 4.dp else 3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = .16f)),
        ) {
            // RTL layout start is the physical right, matching the details page direction.
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(if (isTv) 4.dp else 3.dp)
                    .background(colors.gold),
            )
        }
    }
}



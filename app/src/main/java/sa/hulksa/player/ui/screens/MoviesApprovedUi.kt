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
    RELATED("اعمال مشابهة"),
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
            val contentColor = when {
                focused || isSelected -> colors.goldBright
                else -> colors.textMuted
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) colors.gold.copy(alpha = .12f) else Color.Transparent)
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
                    .clickable(role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = if (isTv) 20.dp else 13.dp, vertical = if (isTv) 11.dp else 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tab.label,
                    color = contentColor,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(if (isTv) 58.dp else 44.dp)
                        .height(2.dp)
                        .background(if (isSelected) colors.goldBright else Color.Transparent),
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
        color = Color(0xFFE3DFD5),
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
                            color = colors.textMuted,
                            fontSize = if (isTv) 12.sp else 11.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = cell.value,
                            color = Color.White,
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
)

/**
 * Centered hero metadata line: year/genre/duration separated by thin dividers, the known quality
 * value in its small framed chip, and the rating with a real gold star. Only available fields
 * render, so absent metadata never becomes an invented value.
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
        durationLabel?.trim()?.takeIf(String::isNotBlank)?.let { MovieHeroMetadata(it) },
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
                        .border(1.dp, colors.goldBright.copy(alpha = .80f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.text,
                        color = colors.goldBright,
                        fontSize = if (isTv) 13.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                entry.rating -> Row(
                    modifier = Modifier.padding(horizontal = if (isTv) 10.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = colors.goldBright,
                        modifier = Modifier.size(if (isTv) 17.dp else 14.dp),
                    )
                    Text(
                        text = entry.text,
                        color = Color.White,
                        fontSize = if (isTv) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                else -> Text(
                    text = entry.text,
                    color = Color.White.copy(alpha = .92f),
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



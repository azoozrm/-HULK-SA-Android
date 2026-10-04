package sa.hulksa.player.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

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

/**
 * Validated Movie offline failure: the Movies catalog refresh failed while the device has no
 * usable validated network. Connectivity classification is authoritative; localized error strings
 * are never parsed and non-network server errors keep the existing error presentation.
 */
internal fun moviesOfflineFailureVisible(
    isMovies: Boolean,
    hasErrorMessage: Boolean,
    networkUsable: Boolean,
): Boolean = isMovies && hasErrorMessage && !networkUsable

/**
 * In-content offline empty state: the validated offline failure with no cached content to keep and
 * no active search (a search with no matches stays a truthful "no results" state).
 */
internal fun moviesOfflineEmptyVisible(
    isMovies: Boolean,
    hasErrorMessage: Boolean,
    networkUsable: Boolean,
    hasCachedContent: Boolean,
    searchActive: Boolean,
): Boolean = moviesOfflineFailureVisible(isMovies, hasErrorMessage, networkUsable) &&
    !hasCachedContent &&
    !searchActive

internal data class MoviesErrorCopy(val title: String, val body: String)

/**
 * Catalog error copy: validated connectivity selects the offline wording, otherwise a truthful
 * load failure keeps the real server message in the same visual component.
 */
internal fun moviesCatalogErrorCopy(offline: Boolean, serverMessage: String?): MoviesErrorCopy =
    if (offline) {
        MoviesErrorCopy(
            title = "لا يوجد اتصال بالانترنت",
            body = "تعذر تحديث المحتوى ، حاول مرة اخرى",
        )
    } else {
        MoviesErrorCopy(
            title = "تعذر تحديث المحتوى",
            body = serverMessage?.trim()?.takeIf { it.isNotEmpty() } ?: "حاول مرة اخرى",
        )
    }

/** Movie Details error copy with the same classification rules. */
internal fun moviesDetailsErrorCopy(offline: Boolean, serverMessage: String?): MoviesErrorCopy =
    if (offline) {
        MoviesErrorCopy(
            title = "لا يوجد اتصال بالانترنت",
            body = "تعذر تحميل بيانات الفلم ، تحقق من الاتصال وحاول مرة اخرى",
        )
    } else {
        MoviesErrorCopy(
            title = "تعذر تحميل بيانات الفلم",
            body = serverMessage?.trim()?.takeIf { it.isNotEmpty() } ?: "حاول مرة اخرى",
        )
    }

/**
 * One bounded Movies error surface for catalog and Details: accepted dark surface, warm-gold
 * status/action icons and ivory wording. The status icon is grouped with its wording and the
 * Retry action is compact and explicitly routable in the TV focus graph.
 */
@Composable
internal fun MoviesErrorNotice(
    title: String,
    body: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryRequester: FocusRequester? = null,
    onRetryFocusChanged: ((Boolean) -> Unit)? = null,
    onRetryUp: (() -> Boolean)? = null,
    onRetryDown: (() -> Boolean)? = null,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF11120D))
            .border(1.dp, colors.gold.copy(alpha = .35f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.wrapContentWidth()) {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = body,
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(10.dp))
        FocusButton(
            text = "اعادة المحاولة",
            onClick = onRetry,
            primary = false,
            outlined = true,
            compact = true,
            trailingIcon = Icons.Rounded.Refresh,
            trailingIconTint = colors.gold,
            scaleOnFocus = false,
            textMaxLines = 1,
            modifier = Modifier
                .then(if (retryRequester != null) Modifier.focusRequester(retryRequester) else Modifier)
                .onFocusChanged { onRetryFocusChanged?.invoke(it.isFocused) }
                .then(
                    if (onRetryUp != null || onRetryDown != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> onRetryUp?.invoke() ?: false
                                Key.DirectionDown -> onRetryDown?.invoke() ?: false
                                Key.DirectionLeft, Key.DirectionRight -> true
                                else -> false
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** Consistent in-content Movie empty state with the same classified wording and retry owner. */
@Composable
internal fun MoviesOfflineEmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "لا يوجد اتصال بالانترنت",
    body: String = "تعذر تحميل المحتوى ، تحقق من الاتصال وحاول مرة اخرى",
    retryRequester: FocusRequester? = null,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            color = colors.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(14.dp))
        FocusButton(
            text = "اعادة المحاولة",
            onClick = onRetry,
            compact = true,
            trailingIcon = Icons.Rounded.Refresh,
            scaleOnFocus = false,
            textMaxLines = 1,
            modifier = if (retryRequester != null) Modifier.focusRequester(retryRequester) else Modifier,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Round 4: truthful per-state download presentation, one permanent control and on-demand panel.
// ---------------------------------------------------------------------------------------------

/** Truthful permanent-control caption per observed job state (null = no job). */
internal fun movieDownloadStatusCaption(status: OfflineStatus?): String = when (status) {
    null -> "تحميل الفلم"
    OfflineStatus.QUEUED -> "في انتظار التحميل"
    OfflineStatus.CHECKING -> "جاري تجهيز التحميل"
    OfflineStatus.DOWNLOADING -> "جاري التحميل"
    OfflineStatus.PAUSED -> "استئناف التحميل"
    OfflineStatus.WAITING_SCHEDULE -> "في انتظار الموعد"
    OfflineStatus.WAITING_NETWORK -> "في انتظار الشبكة"
    OfflineStatus.WAITING_STORAGE -> "في انتظار المساحة"
    OfflineStatus.FAILED -> "اعادة التحميل"
    OfflineStatus.COMPLETED -> "تم التحميل"
}

/** Stable download glyph for the single control; completed keeps the gold check. */
internal fun movieDownloadControlIcon(download: OfflineDownload?): ImageVector =
    if (download?.status == OfflineStatus.COMPLETED) Icons.Rounded.Check else Icons.Rounded.Download

/**
 * Permanent-control caption: the truthful state wording plus the real percentage inline when the
 * total is known. Unknown totals never append a fabricated 0%.
 */
internal fun movieDownloadControlCaption(download: OfflineDownload?): String =
    listOfNotNull(
        movieDownloadStatusCaption(download?.status),
        movieDownloadPercentLabel(download),
    ).joinToString(" ")

/** Known-total percentage only; unknown totals never fabricate a 0%. */
internal fun movieDownloadPercentLabel(download: OfflineDownload?): String? {
    if (download == null || download.totalBytes <= 0L) return null
    val percent = (download.bytesDownloaded.toDouble() / download.totalBytes.toDouble() * 100.0)
        .toInt()
        .coerceIn(0, 100)
    return "$percent%"
}

/** Live speed is shown only while actively downloading. */
internal fun movieDownloadShowsSpeed(status: OfflineStatus?): Boolean =
    status == OfflineStatus.DOWNLOADING

internal fun movieDownloadSpeedLabel(bytesPerSecond: Long): String? {
    if (bytesPerSecond <= 0L) return null
    val megabytes = bytesPerSecond / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", megabytes)
    } else {
        String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
    }
}

internal fun movieDownloadSizeLabel(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val gigabytes = safe / (1024.0 * 1024.0 * 1024.0)
    return if (gigabytes >= 1.0) {
        String.format(Locale.US, "%.1f GB", gigabytes)
    } else {
        String.format(Locale.US, "%.0f MB", safe / (1024.0 * 1024.0))
    }
}

/** Downloaded / total when the total is known; otherwise only the known downloaded size. */
internal fun movieDownloadSizePairLabel(download: OfflineDownload?): String? {
    if (download == null) return null
    return if (download.totalBytes > 0L) {
        "${movieDownloadSizeLabel(download.bytesDownloaded)} / ${movieDownloadSizeLabel(download.totalBytes)}"
    } else {
        movieDownloadSizeLabel(download.bytesDownloaded)
    }
}

internal fun movieDownloadEtaLabel(etaSeconds: Long): String? {
    if (etaSeconds <= 0L) return null
    val minutes = ((etaSeconds + 59L) / 60L).coerceAtLeast(1L)
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours <= 0L -> "متبقي $minutes د"
        remainder == 0L -> "متبقي $hours س"
        else -> "متبقي $hours س $remainder د"
    }
}

internal enum class MovieDownloadPanelActionKind { PAUSE, RESUME, RETRY, CANCEL }

/** Only actions the existing status-sensitive dispatchers actually support. */
internal fun movieDownloadPanelActionKinds(status: OfflineStatus): List<MovieDownloadPanelActionKind> =
    when (status) {
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        -> listOf(MovieDownloadPanelActionKind.PAUSE, MovieDownloadPanelActionKind.CANCEL)
        OfflineStatus.PAUSED,
        OfflineStatus.WAITING_SCHEDULE,
        OfflineStatus.WAITING_NETWORK,
        OfflineStatus.WAITING_STORAGE,
        -> listOf(MovieDownloadPanelActionKind.RESUME, MovieDownloadPanelActionKind.CANCEL)
        OfflineStatus.FAILED ->
            listOf(MovieDownloadPanelActionKind.RETRY, MovieDownloadPanelActionKind.CANCEL)
        OfflineStatus.COMPLETED -> emptyList()
    }

internal fun movieDownloadPanelActionLabel(kind: MovieDownloadPanelActionKind): String = when (kind) {
    MovieDownloadPanelActionKind.PAUSE -> "ايقاف التحميل"
    MovieDownloadPanelActionKind.RESUME -> "استئناف التحميل"
    MovieDownloadPanelActionKind.RETRY -> "اعادة التحميل"
    MovieDownloadPanelActionKind.CANCEL -> "الغاء التحميل"
}

internal fun movieDownloadPanelActionIcon(kind: MovieDownloadPanelActionKind): ImageVector = when (kind) {
    MovieDownloadPanelActionKind.PAUSE -> Icons.Rounded.Pause
    MovieDownloadPanelActionKind.RESUME -> Icons.Rounded.PlayArrow
    MovieDownloadPanelActionKind.RETRY -> Icons.Rounded.Refresh
    MovieDownloadPanelActionKind.CANCEL -> Icons.Rounded.Close
}

internal enum class MovieActionLayout { SINGLE_ROW, WATCH_THEN_PAIR, STACKED }

/**
 * Compact action height policy shared by both Movie Details implementations: TV and normal-height
 * Details use 46dp, compact-height Details uses 42dp. Content taller than the floor (large font
 * scales or longer captions) still grows the row naturally.
 */
internal fun movieActionHeightDp(isTv: Boolean, compactHeight: Boolean): Int =
    if (!isTv && compactHeight) 42 else 46

internal fun movieActionRequiredWidthPx(
    captionWidthPx: Int,
    iconSizePx: Int,
    horizontalPaddingPx: Int,
    gapPx: Int,
): Int = captionWidthPx.coerceAtLeast(0) +
    iconSizePx.coerceAtLeast(0) +
    gapPx.coerceAtLeast(0) +
    2 * horizontalPaddingPx.coerceAtLeast(0)

/**
 * Measured action allocation: three equal columns when every full caption fits, otherwise a
 * full-width Watch with an equal secondary pair, otherwise full-width stacked controls.
 */
internal fun movieActionLayoutMode(
    availableWidthPx: Int,
    requiredWidthsPx: List<Int>,
    gapPx: Int,
): MovieActionLayout {
    if (availableWidthPx <= 0 || requiredWidthsPx.size < 3) return MovieActionLayout.STACKED
    val gap = gapPx.coerceAtLeast(0)
    val maxRequired = requiredWidthsPx.maxOrNull() ?: return MovieActionLayout.STACKED
    if (maxRequired <= 0) return MovieActionLayout.STACKED
    val favorite = requiredWidthsPx[1]
    val download = requiredWidthsPx[2]
    return when {
        3 * maxRequired + 2 * gap <= availableWidthPx -> MovieActionLayout.SINGLE_ROW
        2 * maxOf(favorite, download) + gap <= availableWidthPx -> MovieActionLayout.WATCH_THEN_PAIR
        else -> MovieActionLayout.STACKED
    }
}

private val MovieActionCaptionStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val MovieActionIconSize = 17.dp
private val MovieActionHorizontalPadding = 12.dp
private val MovieActionIconGap = 6.dp
private val MovieActionRowGap = 9.dp
private val MovieActionCaptionBuffer = 6.dp

/**
 * Responsive Movie Details action bar. Every caption is measured at the current font scale so the
 * complete wording is always allocated: three centered equal columns on wide/TV layouts, a
 * full-width Watch with an equal secondary pair on narrow widths, and full-width stacked
 * controls when even the pair cannot fit. The single download control reserves a stable telemetry
 * area so state changes never reflow the hero.
 */
@Composable
internal fun MovieDetailsActionsBar(
    isTv: Boolean,
    rowFraction: Float,
    minimumActionHeightDp: Int,
    resumePositionMs: Long?,
    isFavorite: Boolean,
    download: OfflineDownload?,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    downloadRequester: FocusRequester,
    upRequester: FocusRequester?,
    tabsDownRequester: FocusRequester,
    onActionFocused: (FocusRequester) -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenDownloadPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val watchCaption = if (resumePositionMs != null) "اكمل المشاهدة" else "ابدا المشاهدة"
    val downloadCaption = movieDownloadStatusCaption(download?.status)
    val iconPx = with(density) { MovieActionIconSize.roundToPx() }
    val horizontalPaddingPx = with(density) { MovieActionHorizontalPadding.roundToPx() }
    val iconGapPx = with(density) { MovieActionIconGap.roundToPx() }
    val bufferPx = with(density) { MovieActionCaptionBuffer.roundToPx() }
    // The download caption changes with the job state and can carry an inline percentage.
    // Measuring the longest status with a full "100%" suffix keeps the chosen arrangement stable
    // across every state and guarantees the complete inline caption always fits.
    val reservedDownloadCaptionWidthPx = remember(textMeasurer) {
        OfflineStatus.entries.maxOf { status ->
            val stateLabel = movieDownloadStatusCaption(status)
            maxOf(
                textMeasurer.measure(
                    text = AnnotatedString(stateLabel),
                    style = MovieActionCaptionStyle,
                    maxLines = 1,
                ).size.width,
                textMeasurer.measure(
                    text = AnnotatedString("$stateLabel 100%"),
                    style = MovieActionCaptionStyle,
                    maxLines = 1,
                ).size.width,
            )
        }
    }
    val downloadRequiredWidthPx = movieActionRequiredWidthPx(
        captionWidthPx = reservedDownloadCaptionWidthPx,
        iconSizePx = iconPx,
        horizontalPaddingPx = horizontalPaddingPx,
        gapPx = iconGapPx,
    ) + bufferPx

    BoxWithConstraints(modifier.fillMaxWidth(rowFraction)) {
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val requiredWidths = listOf(
            movieActionRequiredWidthPx(
                captionWidthPx = textMeasurer.measure(
                    text = AnnotatedString(watchCaption),
                    style = MovieActionCaptionStyle,
                    maxLines = 1,
                ).size.width,
                iconSizePx = iconPx,
                horizontalPaddingPx = horizontalPaddingPx,
                gapPx = iconGapPx,
            ) + bufferPx,
            movieActionRequiredWidthPx(
                captionWidthPx = textMeasurer.measure(
                    text = AnnotatedString("المفضلة"),
                    style = MovieActionCaptionStyle,
                    maxLines = 1,
                ).size.width,
                iconSizePx = iconPx,
                horizontalPaddingPx = horizontalPaddingPx,
                gapPx = iconGapPx,
            ) + bufferPx,
            downloadRequiredWidthPx,
        )
        val layoutMode = movieActionLayoutMode(
            availableWidthPx = availableWidthPx,
            requiredWidthsPx = requiredWidths,
            gapPx = with(density) { MovieActionRowGap.roundToPx() },
        )
        val focusWiring: (Int) -> Modifier = { position ->
            Modifier.focusProperties {
                down = tabsDownRequester
                if (isTv) {
                    up = upRequester ?: FocusRequester.Cancel
                    if (layoutMode == MovieActionLayout.SINGLE_ROW) {
                        when (position) {
                            0 -> {
                                left = favoriteRequester
                                right = FocusRequester.Cancel
                            }
                            1 -> {
                                left = downloadRequester
                                right = playRequester
                            }
                            else -> {
                                left = FocusRequester.Cancel
                                right = favoriteRequester
                            }
                        }
                    }
                }
            }
        }
        val watchAction: @Composable (Modifier) -> Unit = { actionModifier ->
            FocusButton(
                text = watchCaption,
                onClick = onPlay,
                trailingIcon = Icons.Rounded.PlayArrow,
                compact = true,
                scaleOnFocus = false,
                textMaxLines = 1,
                onFocused = { onActionFocused(playRequester) },
                modifier = actionModifier
                    .focusRequester(playRequester)
                    .then(focusWiring(0)),
            )
        }
        val favoriteAction: @Composable (Modifier) -> Unit = { actionModifier ->
            FocusButton(
                text = "المفضلة",
                onClick = onToggleFavorite,
                primary = false,
                outlined = true,
                compact = true,
                trailingIcon = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                trailingIconTint = colors.gold,
                scaleOnFocus = false,
                textMaxLines = 1,
                onFocused = { onActionFocused(favoriteRequester) },
                modifier = actionModifier
                    .focusRequester(favoriteRequester)
                    .then(focusWiring(1)),
            )
        }
        val downloadAction: @Composable (Modifier) -> Unit = { actionModifier ->
            MovieDownloadActionControl(
                download = download,
                onClick = if (download == null) onDownload else onOpenDownloadPanel,
                onFocused = { onActionFocused(downloadRequester) },
                modifier = actionModifier
                    .focusRequester(downloadRequester)
                    .then(focusWiring(2)),
            )
        }
        val actionMinHeight = Modifier.heightIn(min = minimumActionHeightDp.dp)
        when (layoutMode) {
            MovieActionLayout.SINGLE_ROW -> Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MovieActionRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                watchAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                favoriteAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                downloadAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
            }
            MovieActionLayout.WATCH_THEN_PAIR -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MovieActionRowGap),
            ) {
                watchAction(Modifier.fillMaxWidth().then(actionMinHeight))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(MovieActionRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    favoriteAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                    downloadAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                }
            }
            MovieActionLayout.STACKED -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MovieActionRowGap),
            ) {
                watchAction(Modifier.fillMaxWidth().then(actionMinHeight))
                favoriteAction(Modifier.fillMaxWidth().then(actionMinHeight))
                downloadAction(Modifier.fillMaxWidth().then(actionMinHeight))
            }
        }
    }
}

/**
 * The single permanent Movie download control. Shows the truthful state caption and the gold
 * status glyph as one vertically centered row; while a known total exists the real percentage is
 * inline with that caption. A thin progress indicator is overlaid on the button's bottom padding
 * so it never adds layout height or moves the centered caption. Speed, byte counts and remaining
 * time live only in the on-demand management dialog. A normal click starts a new download or
 * opens that dialog for an existing job; opening never mutates the job.
 */
@Composable
private fun MovieDownloadActionControl(
    download: OfflineDownload?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    var focused by remember { mutableStateOf(false) }
    val enabled = download?.status != OfflineStatus.COMPLETED
    val showFocused = focused && adaptiveUi.showFocusHighlights
    val shape = RoundedCornerShape(12.dp)
    val caption = movieDownloadControlCaption(download)
    val active = download?.status in setOf(
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
    )
    val progress = if (download != null && download.totalBytes > 0L) download.progress else null
    val indeterminate = download != null && download.totalBytes <= 0L && active
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    !enabled -> colors.surfaceRaised.copy(alpha = .5f)
                    showFocused -> Color(0xFF2A281B)
                    else -> Color(0xFF151711)
                },
            )
            .border(
                width = if (showFocused) 2.dp else 1.dp,
                color = when {
                    showFocused -> colors.goldBright
                    enabled -> colors.gold.copy(alpha = .42f)
                    else -> Color.White.copy(alpha = .10f)
                },
                shape = shape,
            )
            .semantics(mergeDescendants = true) { contentDescription = caption }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovieActionIconGap),
        ) {
            Text(
                text = caption,
                color = colors.text,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // The icon is the last child, so in RTL it renders physically LEFT of the wording.
            Icon(
                imageVector = movieDownloadControlIcon(download),
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(MovieActionIconSize),
            )
        }
        if (progress != null || indeterminate) {
            MovieDownloadProgressTrack(
                progress = progress,
                indeterminate = indeterminate,
                height = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, bottom = 5.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MovieNumericText(
    text: String,
    color: Color,
    fontSizeSp: Int,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            color = color,
            fontSize = fontSizeSp.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            minLines = 1,
        )
    }
}

/**
 * Thin informational track. Known progress fills from the physical right; unknown active totals
 * use a looping indeterminate sliver instead of a fabricated value; inactive unknown totals show
 * only the muted remainder.
 */
@Composable
private fun MovieDownloadProgressTrack(
    progress: Float?,
    indeterminate: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    showRemainder: Boolean = true,
) {
    val colors = LocalHulkColors.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(
                    if (showRemainder || progress != null || indeterminate) {
                        Color.White.copy(alpha = .18f)
                    } else {
                        Color.Transparent
                    },
                ),
        ) {
            when {
                progress != null -> Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(height)
                        .background(colors.gold),
                )
                indeterminate -> IndeterminateDownloadSliver(height)
            }
        }
    }
}

@Composable
private fun IndeterminateDownloadSliver(height: Dp) {
    val colors = LocalHulkColors.current
    val transition = rememberInfiniteTransition(label = "movieDownloadSliver")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "movieDownloadSliverPhase",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(.32f)
                .height(height)
                .graphicsLayer { translationX = -phase * widthPx * .68f }
                .background(colors.gold),
        )
    }
}

/**
 * Bounded on-demand host for [MovieDownloadPanel]. Uses the established app dialog pattern: Back
 * and outside tap dismiss through Dialog, content stays inside the safe bounds, and the compact
 * panel is centered both horizontally and vertically on TV and mobile alike.
 */
@Composable
internal fun MovieDownloadPanelDialog(
    download: OfflineDownload,
    isTv: Boolean,
    onPauseResumeRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelWidth = if (isTv) {
                (maxWidth * .40f).coerceIn(320.dp, 430.dp)
            } else {
                (maxWidth * .92f).coerceIn(280.dp, 520.dp)
            }
            val panelHeight = (maxHeight * if (isTv) .78f else .84f).coerceAtLeast(180.dp)
            MovieDownloadPanel(
                download = download,
                isTv = isTv,
                onPauseResumeRetry = onPauseResumeRetry,
                onCancel = onCancel,
                onClose = onDismiss,
                modifier = Modifier
                    .width(panelWidth)
                    .heightIn(max = panelHeight),
            )
        }
    }
}

/**
 * Compact on-demand download management panel. It only dispatches the existing status-sensitive
 * callbacks; opening it performs no mutation. The caller owns dismissal/Back and focus return.
 */
@Composable
internal fun MovieDownloadPanel(
    download: OfflineDownload,
    isTv: Boolean,
    onPauseResumeRetry: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(14.dp)
    var closeFocused by remember { mutableStateOf(false) }
    val closeRequester = remember(download.downloadId) { FocusRequester() }
    val actionKinds = movieDownloadPanelActionKinds(download.status)
    val actionRequesters = remember(download.downloadId, actionKinds) {
        actionKinds.map { FocusRequester() }
    }
    val firstActionRequester = actionRequesters.firstOrNull()
    LaunchedEffect(download.downloadId, actionKinds) {
        withFrameNanos { }
        firstActionRequester?.let { runCatching { it.requestFocus() } }
    }
    val percent = movieDownloadPercentLabel(download)
    val speed = if (movieDownloadShowsSpeed(download.status)) {
        movieDownloadSpeedLabel(download.bytesPerSecond)
    } else {
        null
    }
    val eta = if (movieDownloadShowsSpeed(download.status)) {
        movieDownloadEtaLabel(download.etaSeconds)
    } else {
        null
    }
    val sizePair = movieDownloadSizePairLabel(download)
    val indeterminate = download.totalBytes <= 0L && movieDownloadShowsSpeed(download.status)
    val progress = if (download.totalBytes > 0L) download.progress else null
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xF711120D))
            .border(1.dp, colors.gold.copy(alpha = .45f), shape)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "التحميل",
                color = colors.text,
                fontSize = if (isTv) 15.sp else 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "اغلاق",
                color = colors.gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .focusRequester(closeRequester)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = firstActionRequester ?: FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    }
                    .onFocusChanged { closeFocused = it.isFocused }
                    .goldFocusEdge(RoundedCornerShape(8.dp), visible = closeFocused)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(9.dp))
        // Telemetry keeps stable LTR numeric ordering (percent left, speed right) inside the RTL
        // panel, matching the reference panel content without reversing digits or units.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MovieNumericText(
                        text = percent ?: movieDownloadSizeLabel(download.bytesDownloaded),
                        color = colors.text,
                        fontSizeSp = if (percent != null) 20 else 13,
                    )
                    if (sizePair != null && percent != null) {
                        Spacer(Modifier.height(3.dp))
                        MovieNumericText(text = sizePair, color = colors.textMuted, fontSizeSp = 11)
                    }
                }
                if (speed != null) {
                    MovieNumericText(text = speed, color = colors.text, fontSizeSp = 12)
                }
            }
        }
        if (eta != null) {
            // Arabic wording stays in the ambient RTL order; only numeric telemetry is LTR-isolated.
            Text(text = eta, color = colors.textMuted, fontSize = 10.sp, maxLines = 1)
        }
        Spacer(Modifier.height(9.dp))
        MovieDownloadProgressTrack(
            progress = progress,
            indeterminate = indeterminate,
            height = if (isTv) 4.dp else 3.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionKinds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actionKinds.forEachIndexed { index, kind ->
                    MovieDownloadPanelAction(
                        kind = kind,
                        requester = actionRequesters[index],
                        upRequester = closeRequester,
                        leftRequester = actionRequesters.getOrNull(index + 1),
                        rightRequester = actionRequesters.getOrNull(index - 1),
                        onClick = when (kind) {
                            MovieDownloadPanelActionKind.CANCEL -> onCancel
                            else -> onPauseResumeRetry
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieDownloadPanelAction(
    kind: MovieDownloadPanelActionKind,
    requester: FocusRequester,
    upRequester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color(0xFF2A281B) else Color(0xFF151711))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.goldBright else colors.gold.copy(alpha = .42f),
                shape = shape,
            )
            .focusRequester(requester)
            .focusProperties {
                up = upRequester
                down = FocusRequester.Cancel
                left = leftRequester ?: FocusRequester.Cancel
                right = rightRequester ?: FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = movieDownloadPanelActionLabel(kind),
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        // Icon last: physically LEFT of the wording in RTL.
        Icon(
            imageVector = movieDownloadPanelActionIcon(kind),
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(16.dp),
        )
    }
}



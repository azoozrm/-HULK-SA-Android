package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sa.hulksa.player.model.ContentDetails
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus
import sa.hulksa.player.ui.components.BrandLogo
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.RecentTimeRow
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Owner-directed Series details content. Four persistent tabs with exclusive content; the season
 * selector and episode cards remain the Series-specific functions inside the Episodes tab.
 */
internal enum class SeriesDetailsTab(val label: String) {
    STORY("القصة"),
    EPISODES("الحلقات"),
    INFORMATION("معلومات المسلسل"),
    RELATED("مسلسلات مشابهة"),
}

/**
 * Shared details tab row visual. Movies and Series keep their own typed wrappers; the rendering,
 * selected underline and focus treatment stay in one place so the accepted Movies appearance is
 * not silently revised.
 */
@Composable
internal fun HulkDetailsTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    requesters: List<FocusRequester?>,
    upTarget: FocusRequester?,
    downTargets: List<FocusRequester?>,
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
        labels.forEachIndexed { index, label ->
            var focused by remember(index) { mutableStateOf(false) }
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .goldFocusEdge(shape = RoundedCornerShape(10.dp), visible = focused)
                    .background(if (focused) colors.gold.copy(alpha = .10f) else Color.Transparent)
                    .then(
                        requesters.getOrNull(index)?.let { requester ->
                            Modifier
                                .focusRequester(requester)
                                .focusProperties {
                                    up = upTarget ?: FocusRequester.Cancel
                                    down = downTargets.getOrNull(index) ?: FocusRequester.Cancel
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
                    .clickable(role = Role.Tab, onClick = { onSelectIndex(index) })
                    .padding(horizontal = if (isTv) 20.dp else 13.dp, vertical = if (isTv) 11.dp else 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    // All tab labels are ivory, selected or not. Selection is shown by the heavier
                    // weight and the ivory underline below.
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
internal fun SeriesDetailsTabRow(
    selected: SeriesDetailsTab,
    onSelect: (SeriesDetailsTab) -> Unit,
    requesters: Map<SeriesDetailsTab, FocusRequester>,
    upTarget: FocusRequester?,
    downTargets: Map<SeriesDetailsTab, FocusRequester?>,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onSelectedTabScrollKey: ((KeyEvent) -> Boolean)? = null,
) {
    HulkDetailsTabRow(
        labels = SeriesDetailsTab.entries.map(SeriesDetailsTab::label),
        selectedIndex = selected.ordinal,
        onSelectIndex = { onSelect(SeriesDetailsTab.entries[it]) },
        requesters = SeriesDetailsTab.entries.map { requesters[it] },
        upTarget = upTarget,
        downTargets = SeriesDetailsTab.entries.map { downTargets[it] },
        isTv = isTv,
        modifier = modifier,
        onSelectedTabScrollKey = onSelectedTabScrollKey,
    )
}

/**
 * Index of the Series details tab row inside the parent page list: hero, optional error, tabs.
 */
internal fun seriesDetailsTabsItemIndex(hasError: Boolean): Int = if (hasError) 2 else 1

/**
 * Stable keyed-layout identity of the selected Series details section item.
 */
internal fun seriesDetailsSectionItemKey(tab: SeriesDetailsTab, tvPolished: Boolean): String = when (tab) {
    SeriesDetailsTab.STORY -> if (tvPolished) "series_tv_polished_story" else "series_story"
    SeriesDetailsTab.EPISODES -> if (tvPolished) "series_tv_polished_header" else "series_episode_header"
    SeriesDetailsTab.INFORMATION -> if (tvPolished) "series_tv_polished_info" else "series_info"
    SeriesDetailsTab.RELATED -> if (tvPolished) "series_tv_polished_related" else "series_related"
}

/**
 * True when the focused selected Series details tab hands its first UP press to the primary watch
 * action in one reveal/focus transition. Episodes and the two informational tabs hand off; Story
 * keeps its existing read-scroll behavior.
 */
internal fun seriesDetailsTabUpHandsOffToWatchAction(tab: SeriesDetailsTab): Boolean =
    tab == SeriesDetailsTab.EPISODES ||
        tab == SeriesDetailsTab.INFORMATION ||
        tab == SeriesDetailsTab.RELATED

/**
 * True when selecting a Series details tab needs the page reveal up to the tab row. Only the
 * informational tabs require it; Episodes and Story keep their existing selection behavior, so the
 * UP handoff above does not add a competing selection scroll.
 */
internal fun seriesDetailsTabSelectionNeedsReveal(tab: SeriesDetailsTab): Boolean =
    tab == SeriesDetailsTab.INFORMATION || tab == SeriesDetailsTab.RELATED

/**
 * Accepts the first focus request that is actually granted, in the supplied priority order. A
 * `false` result or an unavailable (not attached) target continues to the next request instead of
 * being treated as restored, and the first granted request stops the chain so no competing focus
 * owner runs afterwards.
 */
internal fun seriesDetailsFirstGrantedFocus(requests: List<() -> Boolean>): Boolean =
    requests.firstOrNull { request -> runCatching(request).getOrDefault(false) } != null

/**
 * Generation token for one Series focus-restoration/handoff request. `begin()` issues a token and
 * invalidates every older one; `invalidate()` (new error, tab selection, new user key, Retry
 * regaining focus, route change) makes all outstanding requests stale. A stale request must never
 * focus or fall through to another target.
 */
internal class SeriesFocusRestorationOwner {
    private var generation = 0

    fun begin(): Int {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(token: Int): Boolean = token == generation
}

/**
 * A newer user focus/navigation owner (any hero action gaining focus through the shared actions
 * bar) transfers ownership and invalidates every pending Series restoration/handoff request, so a
 * suspended job cannot pull focus back after the transfer. The restoration's own grant happens on
 * the already-decided synchronous chain and cannot self-cancel it.
 */
internal fun seriesDetailsNoteNewFocusOwner(owner: SeriesFocusRestorationOwner) {
    owner.invalidate()
}

/**
 * Real restoration/handoff flow used by the Series Details routes. The reveal runs through the
 * existing page owner; after every suspension the request re-checks its token immediately before
 * requesting focus, so a newer error/selection/navigation or a canceled job cannot focus a stale
 * target. Returns true when one request is granted, false when the reveal completed but no request
 * succeeded, and null when the request was invalidated/cancelled (never an unavailable-target
 * fallback).
 */
internal suspend fun seriesDetailsRestoreFocus(
    isCurrent: () -> Boolean,
    reveal: suspend () -> Unit,
    requests: List<() -> Boolean>,
): Boolean? {
    reveal()
    if (!isCurrent()) return null
    return seriesDetailsFirstGrantedFocus(requests)
}

/**
 * Series information tab: the accepted shared field-grid layout with real series fields only.
 * Missing fields are omitted; labels are gold and values ivory.
 */
@Composable
internal fun SeriesDetailsInfoGrid(
    series: ContentItem,
    details: ContentDetails?,
    seasonCount: Int?,
    episodeCount: Int?,
    qualityLabel: String?,
    ratingLabel: String?,
    horizontalPaddingDp: Int,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val cells = listOfNotNull(
        series.name.trim().takeIf(String::isNotBlank)?.let { DetailsInfoCell("اسم المسلسل", it) },
        (details?.genre ?: series.genre)?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("التصنيف", it) },
        seasonCount?.takeIf { it > 0 }?.let { DetailsInfoCell(label = "المواسم", seasonCount = it) },
        episodeCount?.takeIf { it > 0 }?.let { DetailsInfoCell(label = "الحلقات", episodeCount = it) },
        qualityLabel?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("الجودة", it) },
        ratingLabel?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("التقييم", it) },
        details?.releaseDate?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("تاريخ العرض", it) },
        details?.director?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("الاخراج", it) },
        details?.cast?.trim()?.takeIf(String::isNotBlank)?.let { DetailsInfoCell("البطولة", it) },
    )
    DetailsInfoGrid(
        cells = cells,
        emptyMessage = "لا توجد معلومات متاحة لهذا المسلسل",
        horizontalPaddingDp = horizontalPaddingDp,
        isTv = isTv,
        modifier = modifier,
    )
}

/**
 * Series hero metadata composition, ordered per the approved Series model (rating, genre, quality,
 * episodes, seasons) while reusing the accepted shared row styling. Missing fields stay omitted.
 */
@Composable
internal fun SeriesDetailsHeroMetadataRow(
    series: ContentItem,
    genre: String?,
    qualityLabel: String?,
    ratingLabel: String?,
    episodeCount: Int?,
    seasonCount: Int?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    // Physical RIGHT-to-LEFT: Classification, Quality, Season count, Episode count, Rating. The
    // count groups are explicit icon-less ordered elements (number first) so bidi cannot reverse
    // the order; the hero keeps no season/Layers icon.
    val entries = listOfNotNull(
        genre?.trim()?.takeIf(String::isNotBlank)?.let { DetailsHeroMetadataEntry(it) },
        qualityLabel?.trim()?.takeIf(String::isNotBlank)?.let { DetailsHeroMetadataEntry(it, quality = true) },
        seasonCount?.takeIf { it > 0 }?.let { DetailsHeroMetadataEntry(seasonCount = it, seasonIcon = false) },
        episodeCount?.takeIf { it > 0 }?.let { DetailsHeroMetadataEntry(episodeCount = it) },
        ratingLabel?.trim()?.takeIf(String::isNotBlank)?.let { DetailsHeroMetadataEntry(it, rating = true) },
    )
    DetailsHeroMetadataRow(entries = entries, isTv = isTv, modifier = modifier, wrap = wrap)
}

/**
 * Responsive Series Details action bar. Keeps the accepted Movies measured allocation and visual
 * atoms, with the third slot carrying the existing episode-notification action instead of the
 * Movie download control. Every caption is measured at the current font scale so complete wording
 * is allocated: three equal centered columns, a full-width Watch over an equal secondary pair, or
 * full-width stacked controls.
 */
@Composable
internal fun SeriesDetailsActionsBar(
    isTv: Boolean,
    rowFraction: Float,
    minimumActionHeightDp: Int,
    resumePositionMs: Long?,
    isFavorite: Boolean,
    notificationsEnabled: Boolean,
    notificationToggleAvailable: Boolean,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    notificationRequester: FocusRequester,
    upRequester: FocusRequester?,
    tabsDownRequester: FocusRequester,
    onActionFocused: (FocusRequester) -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val watchCaption = if (resumePositionMs != null) "اكمل المشاهدة" else "ابدا المشاهدة"
    val notificationCaption = seriesNotificationButtonLabel(notificationsEnabled, isTv)
    val iconPx = with(density) { 17.dp.roundToPx() }
    val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
    val iconGapPx = with(density) { 6.dp.roundToPx() }
    val bufferPx = with(density) { 6.dp.roundToPx() }
    val captionStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val notificationCaptionStyle = TextStyle(
        fontSize = (seriesNotificationButtonTextSizeSp(isTv) ?: 13).sp,
        fontWeight = FontWeight.Bold,
    )
    // Reserve the longest notification wording so the chosen arrangement never reflows between
    // "التنبيهات مفعلة" and the longer disabled caption.
    val reservedNotificationWidthPx = remember(textMeasurer, notificationCaptionStyle, isTv) {
        listOf(
            seriesNotificationButtonLabel(enabled = true, isTv = isTv),
            seriesNotificationButtonLabel(enabled = false, isTv = isTv),
        ).maxOf { caption ->
            textMeasurer.measure(
                text = AnnotatedString(caption),
                style = notificationCaptionStyle,
                maxLines = 1,
            ).size.width
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth(rowFraction)) {
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val requiredWidths = listOf(
            movieActionRequiredWidthPx(
                captionWidthPx = textMeasurer.measure(
                    text = AnnotatedString(watchCaption),
                    style = captionStyle,
                    maxLines = 1,
                ).size.width,
                iconSizePx = iconPx,
                horizontalPaddingPx = horizontalPaddingPx,
                gapPx = iconGapPx,
            ) + bufferPx,
            movieActionRequiredWidthPx(
                captionWidthPx = textMeasurer.measure(
                    text = AnnotatedString("المفضلة"),
                    style = captionStyle,
                    maxLines = 1,
                ).size.width,
                iconSizePx = iconPx,
                horizontalPaddingPx = horizontalPaddingPx,
                gapPx = iconGapPx,
            ) + bufferPx,
            movieActionRequiredWidthPx(
                captionWidthPx = reservedNotificationWidthPx,
                iconSizePx = iconPx,
                horizontalPaddingPx = horizontalPaddingPx,
                gapPx = iconGapPx,
            ) + bufferPx,
        )
        val layoutMode = movieActionLayoutMode(
            availableWidthPx = availableWidthPx,
            requiredWidthsPx = requiredWidths,
            gapPx = with(density) { 9.dp.roundToPx() },
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
                                left = notificationRequester
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
        val notificationAction: @Composable (Modifier) -> Unit = { actionModifier ->
            FocusButton(
                text = notificationCaption,
                onClick = onToggleNotifications,
                enabled = notificationsEnabled || notificationToggleAvailable,
                primary = false,
                outlined = true,
                compact = true,
                accent = notificationsEnabled,
                trailingIcon = Icons.Rounded.Notifications,
                scaleOnFocus = false,
                textMaxLines = 1,
                textSizeSp = seriesNotificationButtonTextSizeSp(isTv),
                onFocused = { onActionFocused(notificationRequester) },
                modifier = actionModifier
                    .focusRequester(notificationRequester)
                    .then(focusWiring(2)),
            )
        }
        val actionMinHeight = Modifier.heightIn(min = minimumActionHeightDp.dp)
        val actionGap = 9.dp
        when (layoutMode) {
            MovieActionLayout.SINGLE_ROW -> Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(actionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                watchAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                favoriteAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                notificationAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
            }
            MovieActionLayout.WATCH_THEN_PAIR -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(actionGap),
            ) {
                watchAction(Modifier.fillMaxWidth().then(actionMinHeight))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(actionGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    favoriteAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                    notificationAction(Modifier.weight(1f).then(actionMinHeight).fillMaxHeight())
                }
            }
            MovieActionLayout.STACKED -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(actionGap),
            ) {
                watchAction(Modifier.fillMaxWidth().then(actionMinHeight))
                favoriteAction(Modifier.fillMaxWidth().then(actionMinHeight))
                notificationAction(Modifier.fillMaxWidth().then(actionMinHeight))
            }
        }
    }
}


/** Optional explicit focus links for one episode card target (card, download or cancel action). */

/**
 * Truthful permanent Series episode download caption: the accepted full state wording with the
 * episode noun (`تحميل الحلقة`) plus the real percentage only when the total is known.
 */
internal fun episodeDownloadControlCaption(download: OfflineDownload?): String =
    listOfNotNull(
        when (download?.status) {
            null -> "تحميل الحلقة"
            OfflineStatus.QUEUED -> "في انتظار التحميل"
            OfflineStatus.CHECKING -> "جاري تجهيز التحميل"
            OfflineStatus.DOWNLOADING -> "جاري التحميل"
            OfflineStatus.PAUSED -> "استئناف التحميل"
            OfflineStatus.WAITING_SCHEDULE -> "في انتظار الموعد"
            OfflineStatus.WAITING_NETWORK -> "في انتظار الشبكة"
            OfflineStatus.WAITING_STORAGE -> "في انتظار المساحة"
            OfflineStatus.FAILED -> "اعادة التحميل"
            OfflineStatus.COMPLETED -> "تم التحميل"
        },
        movieDownloadPercentLabel(download),
    ).joinToString(" ")

internal data class EpisodeFocusLinks(
    val up: FocusRequester? = null,
    val down: FocusRequester? = null,
    val left: FocusRequester? = null,
    val right: FocusRequester? = null,
)

/**
 * Shared Series episode boxed card: real artwork with the episode identity/duration badges and the
 * real saved progress, plus a stable title/metadata footer holding the full-width permanent download
 * control. The card itself is the playback focus target; the download control and its optional
 * cancel/remove control are separate focus targets. Focus stays inside the card bounds (no scale).
 */
@Composable
internal fun EpisodeBoxedCard(
    episodeId: Int,
    identityLabel: String,
    title: String,
    subtitle: String?,
    statusLabel: String?,
    statusElapsedText: String?,
    statusTotalText: String?,
    durationLabel: String?,
    progress: Float?,
    completed: Boolean,
    artworkUrl: String?,
    download: OfflineDownload?,
    downloadCaption: String,
    downloadEnabled: Boolean,
    cancelCaption: String?,
    isTv: Boolean,
    highlighted: Boolean,
    cardRequester: FocusRequester,
    actionRequester: FocusRequester,
    cardLinks: EpisodeFocusLinks = EpisodeFocusLinks(),
    actionLinks: EpisodeFocusLinks = EpisodeFocusLinks(),
    cancelRequester: FocusRequester? = null,
    cancelLinks: EpisodeFocusLinks = EpisodeFocusLinks(),
    onActionDown: () -> Boolean = { false },
    onMoveDown: (() -> Unit)? = null,
    onCancelPendingMove: () -> Unit = {},
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var cardFocused by remember(episodeId) { mutableStateOf(false) }
    var cancelRestorePending by remember(episodeId) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isTv) 12.dp else 10.dp)
    val downloadPercent = download
        ?.takeIf { it.totalBytes > 0L }
        ?.let { (it.progress * 100).toInt().coerceIn(0, 100) }
    val downloadTracked = download?.status in setOf(
        OfflineStatus.QUEUED,
        OfflineStatus.CHECKING,
        OfflineStatus.DOWNLOADING,
        OfflineStatus.PAUSED,
    )
    val cancelVisible = cancelCaption != null && cancelRequester != null &&
        onCancelDownload != null
    // When the cancel/remove control is activated and the job disappears, focus returns to the
    // surviving download action instead of being lost with the removed node. The pending flag is
    // set on activation (not on focus loss) so node removal cannot clear it first.
    LaunchedEffect(cancelVisible) {
        if (!cancelVisible && cancelRestorePending) {
            withFrameNanos { }
            runCatching { actionRequester.requestFocus() }
            cancelRestorePending = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF10110C))
            .goldFocusEdge(shape = shape, visible = cardFocused && isTv)
            .border(
                width = if (cardFocused) 2.dp else 1.dp,
                color = if (cardFocused) colors.goldBright else Color.White.copy(alpha = .10f),
                shape = shape,
            )
            .focusRequester(cardRequester)
            .focusProperties {
                up = cardLinks.up ?: FocusRequester.Cancel
                down = cardLinks.down ?: actionRequester
                left = cardLinks.left ?: FocusRequester.Cancel
                right = cardLinks.right ?: FocusRequester.Cancel
            }
            .onFocusChanged { cardFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onPlay),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BrandLogo(
                    Modifier
                        .align(Alignment.Center)
                        .size(58.dp)
                        .graphicsLayer { alpha = .28f },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            .55f to Color.Transparent,
                            .80f to Color.Black.copy(alpha = .62f),
                            1f to Color.Black.copy(alpha = .94f),
                        ),
                    ),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                durationLabel?.takeIf(String::isNotBlank)?.let { value ->
                    EpisodeArtworkBadge(
                        text = value,
                        modifier = Modifier.align(AbsoluteAlignment.TopLeft).padding(7.dp),
                    )
                }
                EpisodeArtworkBadge(
                    text = identityLabel,
                    modifier = Modifier.align(AbsoluteAlignment.TopRight).padding(7.dp),
                    accent = highlighted,
                )
            }
            if (progress != null || completed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = .16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (completed) 1f else progress ?: 0f)
                            .fillMaxHeight()
                            .background(colors.goldBright),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12130E))
                .padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = colors.text,
                fontWeight = FontWeight.Black,
                fontSize = if (isTv) 13.sp else 12.sp,
                lineHeight = if (isTv) 16.sp else 15.sp,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle.orEmpty(),
                color = colors.textMuted,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // Reserved status area matching the approved Recent-card treatment: the status word on
            // its own line, then the real saved/known time with a gold clock and ivory values.
            Text(
                text = statusLabel.orEmpty(),
                color = if (statusLabel.isNullOrBlank()) Color.Transparent else colors.goldBright,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                minLines = 1,
                textAlign = TextAlign.Center,
            )
            RecentTimeRow(
                elapsedText = statusElapsedText.orEmpty(),
                totalText = statusTotalText,
                iconSize = 11.dp,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                valueColor = if (statusElapsedText.isNullOrBlank()) Color.Transparent else colors.text,
            )
            if (downloadTracked && downloadPercent != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$downloadPercent%",
                        color = colors.goldBright,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = .16f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(downloadPercent / 100f)
                                .fillMaxHeight()
                                .background(colors.gold),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            FocusButton(
                text = downloadCaption,
                onClick = onDownload,
                enabled = downloadEnabled,
                primary = false,
                outlined = true,
                compact = true,
                scaleOnFocus = false,
                textMaxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 38.dp else 42.dp)
                    .focusRequester(actionRequester)
                    .focusProperties {
                        up = actionLinks.up ?: cardRequester
                        // The visible cancel/remove control sits directly below the download action,
                        // so DOWN must reach it before leaving the card.
                        down = if (cancelVisible) {
                            cancelRequester ?: FocusRequester.Cancel
                        } else {
                            actionLinks.down ?: FocusRequester.Cancel
                        }
                        left = actionLinks.left ?: FocusRequester.Cancel
                        right = actionLinks.right ?: FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
                            false
                        } else if (cancelVisible) {
                            // Let the focus graph move DOWN into the cancel control.
                            false
                        } else if (onMoveDown != null) {
                            onCancelPendingMove()
                            onMoveDown()
                            true
                        } else {
                            onActionDown()
                        }
                    },
            )
            if (cancelVisible) {
                Spacer(Modifier.height(5.dp))
                val cancelRequesterValue = cancelRequester
                FocusButton(
                    text = cancelCaption.orEmpty(),
                    onClick = {
                        cancelRestorePending = true
                        onCancelDownload?.invoke()
                    },
                    primary = false,
                    outlined = true,
                    compact = true,
                    scaleOnFocus = false,
                    textMaxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isTv) 36.dp else 38.dp)
                        .focusRequester(cancelRequesterValue!!)
                        .focusProperties {
                            // UP returns to the download action; DOWN leaves the card normally.
                            up = cancelLinks.up ?: actionRequester
                            down = cancelLinks.down ?: FocusRequester.Cancel
                            left = cancelLinks.left ?: FocusRequester.Cancel
                            right = cancelLinks.right ?: FocusRequester.Cancel
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) {
                                false
                            } else if (onMoveDown != null) {
                                onCancelPendingMove()
                                onMoveDown()
                                true
                            } else {
                                onActionDown()
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun EpisodeArtworkBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val colors = LocalHulkColors.current
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = text,
        color = if (accent) colors.goldBright else Color.White,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = .76f))
            .border(
                width = 1.dp,
                color = if (accent) colors.goldBright.copy(alpha = .55f) else Color.White.copy(alpha = .18f),
                shape = shape,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

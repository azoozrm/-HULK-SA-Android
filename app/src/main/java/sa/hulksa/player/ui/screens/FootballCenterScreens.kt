package sa.hulksa.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import sa.hulksa.player.data.FootballAlertType
import sa.hulksa.player.data.FootballCompetition
import sa.hulksa.player.data.FootballDayFailure
import sa.hulksa.player.data.FootballFavoriteTeam
import sa.hulksa.player.data.FootballFeedDiscovery
import sa.hulksa.player.data.FootballFeedError
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.FootballFeedUiState
import sa.hulksa.player.data.FootballMatch
import sa.hulksa.player.data.FootballMatchAlertPreferences
import sa.hulksa.player.data.FootballMatchStatus
import sa.hulksa.player.data.FootballTeam
import sa.hulksa.player.data.filterFootballTeams
import sa.hulksa.player.data.footballFavoritesFirstSnapshot
import sa.hulksa.player.data.footballRiyadhDayKey
import sa.hulksa.player.data.footballRiyadhDayStartEpochMs
import sa.hulksa.player.data.footballRiyadhDayLabel
import sa.hulksa.player.data.footballRiyadhTimeLabel
import sa.hulksa.player.data.footballSnapshotCacheFresh
import sa.hulksa.player.data.footballDisplaySnapshotForDay
import sa.hulksa.player.data.footballDerivedDaySnapshot
import sa.hulksa.player.data.footballEffectiveDayFailure
import sa.hulksa.player.data.footballTeamsFromMatches
import sa.hulksa.player.data.isSaudiCompetition
import sa.hulksa.player.data.mergeFootballTeamLists
import sa.hulksa.player.data.resolveFootballAlertPreferences
import sa.hulksa.player.data.resolveFootballMatch
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.theme.LocalHulkColors
import sa.hulksa.player.ui.theme.NeoNeutralSurfaces

internal enum class FootballCenterFilter(val label: String) {
    ALL("الكل"),
    MY_TEAMS("مباريات فرقي"),
    LIVE("مباشر"),
}

internal enum class FootballTeamRegion(val label: String) {
    ALL("الكل"),
    SAUDI("السعودية"),
    GULF("الخليج"),
    ARAB("العربية"),
    EUROPE("اوروبا"),
    NATIONAL("المنتخبات"),
    OTHER("اخرى"),
}

internal val footballVisibleTeamRegions: List<FootballTeamRegion> =
    FootballTeamRegion.entries.filter { it != FootballTeamRegion.ALL && it != FootballTeamRegion.OTHER }

internal fun footballTeamRegion(team: FootballTeam): FootballTeamRegion {
    if (team.nationalTeam) return FootballTeamRegion.NATIONAL
    val country = team.country?.lowercase().orEmpty()
    return when {
        team.competitionIds.contains("17") || country in setOf("saudi arabia", "sa") ->
            FootballTeamRegion.SAUDI
        team.competitionIds.any { it in setOf("53", "47") } ||
            country in setOf("morocco", "tunisia", "egypt", "algeria", "jordan", "iraq") ->
            FootballTeamRegion.ARAB
        team.competitionIds.any { it in setOf("1", "3", "4", "5", "6", "7", "8", "83") } ->
            FootballTeamRegion.EUROPE
        country in setOf("uae", "qatar", "kuwait", "bahrain", "oman", "united arab emirates") ->
            FootballTeamRegion.GULF
        else -> FootballTeamRegion.OTHER
    }
}

internal fun footballMatchesForFilter(
    matches: List<FootballMatch>,
    filter: FootballCenterFilter,
    favoriteIds: Set<String>,
): List<FootballMatch> = when (filter) {
    FootballCenterFilter.ALL -> matches
    FootballCenterFilter.MY_TEAMS -> matches.filter { match -> favoriteIds.any(match::involvesTeam) }
    FootballCenterFilter.LIVE -> matches.filter { it.status == FootballMatchStatus.LIVE }
}

internal fun footballCenterGroups(matches: List<FootballMatch>): List<Pair<FootballCompetition, List<FootballMatch>>> =
    matches.groupBy { it.competition.id }
        .values
        .map { group -> group.first().competition to group }
        .sortedWith(
            compareBy<Pair<FootballCompetition, List<FootballMatch>>> { it.first.priority }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.displayName },
        )

/**
 * The selected day's own snapshot owns the center. A Home snapshot is only a fallback for today,
 * and a non-stale flag is not enough: an expired cache must not hide a newer stale/unavailable day.
 */


internal data class FootballCenterFailureCopy(
    val title: String,
    val detail: String,
    val retryEnabled: Boolean,
)

internal fun footballRetryWaitLabel(waitSeconds: Long): String = when {
    waitSeconds <= 0L -> ""
    waitSeconds < 60L -> "$waitSeconds ثانية"
    else -> "${(waitSeconds + 59L) / 60L} دقيقة"
}

private fun footballFailureBaseCopy(error: FootballFeedError?): String = when (error) {
    FootballFeedError.RATE_LIMITED -> "المزود طلب الانتظار قبل التحديث التالي"
    FootballFeedError.INVALID_RESPONSE -> "وصلت بيانات غير مكتملة من الخادم"
    FootballFeedError.NOT_CONFIGURED -> "خدمة المباريات غير مفعلة"
    FootballFeedError.UNAVAILABLE, null -> "تعذر الوصول إلى خدمة المباريات"
}

internal fun footballCenterFailureCopy(
    failure: FootballDayFailure?,
    nowEpochMs: Long,
): FootballCenterFailureCopy {
    val retryAt = failure?.retryAtEpochMs ?: 0L
    val waitSeconds = ((retryAt - nowEpochMs + 999L) / 1_000L).coerceAtLeast(0L)
    val base = footballFailureBaseCopy(failure?.error)
    return FootballCenterFailureCopy(
        title = "تعذر تحميل مباريات هذا اليوم",
        detail = if (waitSeconds > 0L) {
            "$base • بعد ${footballRetryWaitLabel(waitSeconds)}"
        } else {
            base
        },
        retryEnabled = waitSeconds == 0L,
    )
}

internal data class FootballCenterEmptyCopy(
    val title: String,
    val detail: String?,
)

internal fun footballCenterEmptyCopy(
    snapshot: FootballFeedSnapshot,
    filter: FootballCenterFilter,
): FootballCenterEmptyCopy = when {
    snapshot.stale && snapshot.matches.isEmpty() -> FootballCenterEmptyCopy(
        title = "تعذر تحديث مباريات اليوم",
        detail = "لا توجد بيانات محفوظة لهذا اليوم؛ ستتم إعادة المحاولة تلقائياً",
    )
    snapshot.stale -> FootballCenterEmptyCopy(
        title = "لا توجد مباريات مطابقة في آخر بيانات محفوظة",
        detail = "البيانات قديمة؛ ستُحدَّث تلقائياً",
    )
    snapshot.matches.isEmpty() -> FootballCenterEmptyCopy(
        title = "لا توجد مباريات لهذا اليوم",
        detail = null,
    )
    else -> FootballCenterEmptyCopy(
        title = "لا توجد مباريات مطابقة",
        detail = if (filter == FootballCenterFilter.ALL) {
            null
        } else {
            "فلتر «${filter.label}» لا يطابق أي مباراة اليوم"
        },
    )
}

/**
 * Minimal extra scroll that keeps a focused row a real margin inside the list viewport. Positive
 * means scroll forward. Returns zero when the row already has the requested top and bottom margin.
 */
internal fun footballRetryTickDelayMs(nowEpochMs: Long, retryAtEpochMs: Long): Long =
    (retryAtEpochMs - nowEpochMs).coerceIn(0L, 1_000L)

@Composable
internal fun rememberFootballClock(deadlineEpochMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(deadlineEpochMs, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                delay(footballCenterClockTickMs(current, deadlineEpochMs))
            }
        }
    }
    return now
}

internal fun footballCenterClockTickMs(nowEpochMs: Long, retryAtEpochMs: Long): Long =
    if (retryAtEpochMs > nowEpochMs) {
        footballRetryTickDelayMs(nowEpochMs, retryAtEpochMs)
    } else {
        15_000L
    }

/**
 * Automatic retry wait for a selected Center day. A provider Retry-After deadline is authoritative;
 * a plain transport failure without a deadline backs off for one refresh interval instead of
 * hammering the route or waiting for a manual button.
 */
internal fun footballAutomaticRetryDelayMs(nowEpochMs: Long, retryAtEpochMs: Long): Long =
    (retryAtEpochMs - nowEpochMs).takeIf { it > 0L } ?: 60_000L

internal fun footballFocusedIndex(
    focusedKey: String?,
    keyToIndex: Map<String, Int>,
): Int? = focusedKey?.let(keyToIndex::get)

/**
 * Physical D-pad movement inside the Home football rail. The rail is RTL, so physical left
 * advances to the next stable card (higher index) and physical right goes back. Returning null at
 * either end lets the normal focus search leave the rail for the header/adjacent sections.
 */
internal fun footballRailAdjacentIndex(
    currentIndex: Int,
    itemCount: Int,
    forward: Boolean,
): Int? {
    if (currentIndex !in 0 until itemCount) return null
    val target = if (forward) currentIndex + 1 else currentIndex - 1
    return target.takeIf { it in 0 until itemCount }
}

/**
 * Re-resolves the transition target by stable identity after a refresh/reorder. A target that
 * disappeared is not replaced by whatever card now occupies its old index.
 */
internal fun footballRailTargetIndex(keys: List<String>, targetKey: String): Int? =
    keys.indexOf(targetKey).takeIf { it >= 0 }

/**
 * Route for a physical key when the adjacent card is already fully visible. A refused focus is
 * not consumed and falls back to the normal focus graph; only a partially visible/offscreen target
 * enters the bounded reveal transition.
 */
internal enum class FootballRailVisibleRoute { SLOW_PATH, CONSUMED, NORMAL_ROUTE }

internal fun footballRailVisibleRoute(
    fullyVisible: Boolean,
    requestFocus: () -> Boolean,
): FootballRailVisibleRoute = when {
    !fullyVisible -> FootballRailVisibleRoute.SLOW_PATH
    requestFocus() -> FootballRailVisibleRoute.CONSUMED
    else -> FootballRailVisibleRoute.NORMAL_ROUTE
}

/**
 * Home section title discloses the actual accepted day when the previous-day fallback is shown.
 * It never restores the removed refresh/freshness/timezone wording.
 */
internal fun footballHomeSectionTitle(
    displayDayKey: String?,
    todayKey: String,
    nowEpochMs: Long,
): String = if (displayDayKey == null || displayDayKey == todayKey) {
    "مباريات اليوم"
} else {
    "مباريات ${footballRiyadhDayLabel(displayDayKey, nowEpochMs)}"
}

/**
 * Full-visibility decision for one rail card. The requested focus margin normally applies, but at
 * a real scroll bound the rail cannot add it; a card that is fully inside the viewport is already
 * revealed and must not be rejected for a one-pixel margin deficit (row padding 5 dp vs margin).
 */
internal fun footballRailFullyVisible(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    marginPx: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Boolean {
    val delta = footballFocusScrollDelta(itemOffset, itemSize, viewportStart, viewportEnd, marginPx)
    if (delta == 0) return true
    val fullyInside = itemOffset >= viewportStart && itemOffset + itemSize <= viewportEnd
    if (!fullyInside) return false
    return (delta < 0 && !canScrollBackward) || (delta > 0 && !canScrollForward)
}

internal fun footballFocusScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    marginPx: Int,
): Int {
    val margin = marginPx.coerceAtLeast(0)
    val top = viewportStart + margin
    val bottom = viewportEnd - margin
    return when {
        itemOffset < top -> itemOffset - top
        itemOffset + itemSize > bottom -> itemOffset + itemSize - bottom
        else -> 0
    }
}

internal fun footballCompetitionMarkSizeDp(
    surface: FootballCompetitionMarkSurface,
    isTv: Boolean,
): Dp = when (surface) {
    FootballCompetitionMarkSurface.HOME -> if (isTv) 24.dp else 20.dp
    FootballCompetitionMarkSurface.CENTER -> if (isTv) 30.dp else 24.dp
    FootballCompetitionMarkSurface.DETAILS -> if (isTv) 32.dp else 28.dp
}

internal enum class FootballCompetitionMarkSurface { HOME, CENTER, DETAILS }

/** Verified provider names only, trimmed and de-duplicated; never a league-derived channel. */
internal fun footballBroadcasterSummary(names: List<String>): String? =
    names.map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" • ")

@Composable
internal fun FootballCrest(
    team: FootballTeam?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(NeoNeutralSurfaces.card)
            .border(1.dp, NeoNeutralSurfaces.stroke, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val logo = team?.logoUrl
        var failed by remember(logo) { mutableStateOf(false) }
        if (!logo.isNullOrBlank() && !failed) {
            AsyncImage(
                model = logo,
                contentDescription = team?.displayName,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = team?.displayName,
                tint = colors.gold,
                modifier = Modifier.size(size * .55f),
            )
        }
    }
}

@Composable
internal fun FootballCompetitionMark(
    competition: FootballCompetition?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(NeoNeutralSurfaces.card)
            .border(1.dp, NeoNeutralSurfaces.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val logo = competition?.logoUrl
        var failed by remember(logo) { mutableStateOf(false) }
        if (!logo.isNullOrBlank() && !failed) {
            AsyncImage(
                model = logo,
                contentDescription = competition?.displayName,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = competition?.displayName,
                tint = colors.gold,
                modifier = Modifier.size(size * .55f),
            )
        }
    }
}

private fun footballScoresMissing(match: FootballMatch): Boolean =
    match.status.isStarted && (match.homeScore == null || match.awayScore == null)

internal fun footballStatusText(
    match: FootballMatch,
    hideScores: Boolean,
    nowEpochMs: Long,
    stale: Boolean,
): String = when (match.status) {
    FootballMatchStatus.LIVE -> {
        val live = match.minute?.let { "مباشر $it'" } ?: "مباشر"
        if (footballScoresMissing(match)) "$live • النتيجة غير متوفرة" else live
    }
    FootballMatchStatus.FINISHED -> when {
        hideScores -> "انتهت • النتيجة مخفية"
        footballScoresMissing(match) -> "انتهت • النتيجة غير متوفرة"
        else -> "انتهت"
    }
    FootballMatchStatus.POSTPONED -> "مؤجلة"
    FootballMatchStatus.CANCELLED -> "ملغاة"
    FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
    FootballMatchStatus.UPCOMING -> when {
        match.kickoffEpochMs > nowEpochMs -> "لم تبدا"
        stale -> "الموعد مضى • بيانات قديمة"
        else -> "الموعد مضى • بانتظار التحديث"
    }
}

internal fun footballDetailStatusText(
    match: FootballMatch,
    nowEpochMs: Long,
    stale: Boolean,
): String = when (match.status) {
    FootballMatchStatus.UPCOMING -> when {
        match.kickoffEpochMs > nowEpochMs -> "مؤكدة • لم تبدا"
        stale -> "الموعد مضى • بيانات قديمة"
        else -> "الموعد مضى • بانتظار التحديث"
    }
    FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
    else -> footballStatusText(match, false, nowEpochMs, stale)
}

@Composable
private fun FootballScoreDisplay(
    match: FootballMatch,
    hideScores: Boolean,
    fontSizeSp: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    if (!match.status.isStarted) {
        Text(
            text = footballRiyadhTimeLabel(match.kickoffEpochMs),
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }
    if (hideScores) {
        Text(
            text = "—",
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Home is physically first in RTL, so each side keeps its own score next to its team.
        Text(
            text = match.homeScore?.toString() ?: "—",
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = " - ",
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = match.awayScore?.toString() ?: "—",
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private fun footballSecondaryText(
    match: FootballMatch,
    hideScores: Boolean,
    nowEpochMs: Long,
    stale: Boolean,
): String = footballStatusText(match, hideScores, nowEpochMs, stale)

@Composable
internal fun FootballHomeCard(
    match: FootballMatch,
    favoriteIds: Set<String>,
    hideScores: Boolean,
    isTv: Boolean,
    nowEpochMs: Long,
    stale: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    onFocusLost: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val isFavorite = favoriteIds.any(match::involvesTeam)
    val isSaudi = match.competition.isSaudiCompetition()
    val shape = RoundedCornerShape(16.dp)
    // One fixed geometry for every Home card: a two-line team name must never grow the card or
    // shift its neighbours in the rail.
    Column(
        modifier = modifier
            .width(if (isTv) 302.dp else 274.dp)
            .height(if (isTv) 184.dp else 176.dp)
            .clip(shape)
            .background(NeoNeutralSurfaces.card)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                width = if (focused) 0.dp else 1.dp,
                color = NeoNeutralSurfaces.stroke,
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else onFocusLost()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(if (isTv) 32.dp else 28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FootballCompetitionMark(
                    match.competition,
                    if (isTv) 32.dp else 28.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = match.competition.displayName,
                    color = if (isSaudi) colors.goldBright else colors.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = if (isTv) 214.dp else 184.dp),
                )
            }
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "فريق متابع",
                    tint = colors.gold,
                    modifier = Modifier.align(Alignment.CenterEnd).size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FootballHomeTeam(
                match.homeTeam,
                if (isTv) 48.dp else 46.dp,
                Modifier.weight(1f),
                TextAlign.Start,
                nameAreaHeight = 30.dp,
            )
            Column(
                modifier = Modifier.widthIn(min = 86.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FootballScoreDisplay(match, hideScores, 22)
            }
            FootballHomeTeam(
                match.awayTeam,
                if (isTv) 48.dp else 46.dp,
                Modifier.weight(1f),
                TextAlign.End,
                nameAreaHeight = 30.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(NeoNeutralSurfaces.stroke.copy(alpha = .72f)),
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (match.status == FootballMatchStatus.LIVE) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = footballSecondaryText(match, hideScores, nowEpochMs, stale),
                    color = if (match.status == FootballMatchStatus.LIVE) colors.goldBright else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = if (match.status == FootballMatchStatus.LIVE) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            val broadcasterSummary = footballBroadcasterSummary(match.broadcasters)
            if (broadcasterSummary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "القناة الناقلة: $broadcasterSummary",
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FootballHomeTeam(
    team: FootballTeam,
    crestSize: Dp,
    modifier: Modifier,
    textAlign: TextAlign,
    nameAreaHeight: Dp? = null,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FootballCrest(team, crestSize)
        Spacer(Modifier.height(4.dp))
        val name: @Composable () -> Unit = {
            Text(
                text = team.displayName,
                color = colors.text,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (nameAreaHeight != null) {
            Box(
                modifier = Modifier.height(nameAreaHeight).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                name()
            }
        } else {
            name()
        }
    }
}

@Composable
internal fun FootballCenterScreen(
    feed: FootballFeedUiState,
    isTv: Boolean,
    onBack: () -> Unit,
    onToggleHideScores: () -> Unit,
    onSelectDay: (String) -> Unit,
    onRetryDay: (String) -> Unit,
    discovery: FootballFeedDiscovery?,
    onSaveMatchAlerts: (FootballMatch, FootballMatchAlertPreferences) -> Unit,
    onSetTeams: (List<FootballTeam>) -> Unit,
    onLoadTeams: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val wideToolbar = adaptiveUi.screenWidthDp >= 900
    val safeInsets = tvPageSafeInsets(adaptiveUi.screenWidthDp, adaptiveUi.screenHeightDp)
    // The board's top control group sits below the measured TV overscan band, not on the edge.
    val topGutter = if (isTv) (safeInsets.verticalDp + 12f).dp else 10.dp
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        currentTimeMs = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    val today = footballRiyadhDayKey(currentTimeMs)
    LaunchedEffect(today) {
        val start = footballRiyadhDayStartEpochMs(today) ?: return@LaunchedEffect
        val remaining = start + 24L * 60L * 60L * 1_000L - System.currentTimeMillis()
        if (remaining > 0L) {
            delay(remaining)
            currentTimeMs = System.currentTimeMillis()
        }
    }
    val days = listOf(
        footballRiyadhDayKey(currentTimeMs - 24 * 60 * 60 * 1_000L) to "الامس",
        today to "اليوم",
        footballRiyadhDayKey(currentTimeMs + 24 * 60 * 60 * 1_000L) to "غدا",
    )
    var selectedDay by rememberSaveable(today) { mutableStateOf(today) }
    val retryDeadline = maxOf(
        feed.dayFailures[selectedDay]?.retryAtEpochMs ?: 0L,
        feed.nextRetryAtEpochMs,
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(retryDeadline, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = System.currentTimeMillis()
                currentTimeMs = now
                delay(footballCenterClockTickMs(now, retryDeadline))
            }
        }
    }
    var managerVisible by remember { mutableStateOf(false) }
    var managerWasVisible by remember { mutableStateOf(false) }
    val manageRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backRequester.requestFocus() }
    }
    LaunchedEffect(managerVisible) {
        if (managerVisible) {
            managerWasVisible = true
        } else if (managerWasVisible) {
            managerWasVisible = false
            withFrameNanos { runCatching { manageRequester.requestFocus() } }
        }
    }
    var filter by rememberSaveable { mutableStateOf(FootballCenterFilter.ALL.name) }
    val activeFilter = FootballCenterFilter.entries.firstOrNull { it.name == filter } ?: FootballCenterFilter.ALL
    val dayFailure = footballEffectiveDayFailure(
        daySnapshots = feed.daySnapshots,
        dayFailures = feed.dayFailures,
        dayKey = selectedDay,
    )
    val effectiveRetryAt = maxOf(dayFailure?.retryAtEpochMs ?: 0L, feed.nextRetryAtEpochMs)
    val effectiveFailure = dayFailure?.copy(retryAtEpochMs = effectiveRetryAt)
    val favoriteIds = feed.favoriteTeamIds
    val exactSnapshot = footballFavoritesFirstSnapshot(
        footballDisplaySnapshotForDay(
            daySnapshots = feed.daySnapshots,
            dayFailures = feed.dayFailures,
            dayKey = selectedDay,
            nowEpochMs = currentTimeMs,
        ),
        favoriteIds,
    )
    // Today's accepted payload already carries the provider's prior-day history and next-day
    // reminder rows. Show only rows whose kickoff belongs to the selected Riyadh day; this is a
    // provisional projection, never a saved record for that day, and the exact-day fetch replaces
    // it when available.
    val derivedSnapshot = remember(feed.snapshot, selectedDay, favoriteIds) {
        footballDerivedDaySnapshot(feed.snapshot, selectedDay, favoriteIds)
    }
    val snapshot = exactSnapshot ?: derivedSnapshot
    val provisionalDay = exactSnapshot == null && derivedSnapshot != null
    val currentSelectDay by rememberUpdatedState(onSelectDay)
    val currentRetryDay by rememberUpdatedState(onRetryDay)
    val refreshDay = selectedDay
    val dayLoading = refreshDay in feed.loadingDays
    // Preserve Today's explicit selection and failure recovery; routine Today refresh belongs
    // to the foreground scheduler.
    LaunchedEffect(refreshDay) {
        if (refreshDay == today) currentSelectDay(refreshDay)
    }
    LaunchedEffect(refreshDay, today, effectiveFailure, dayLoading, lifecycleOwner) {
        if (refreshDay != today || effectiveFailure == null || dayLoading) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(footballAutomaticRetryDelayMs(System.currentTimeMillis(), effectiveRetryAt) + 100L)
            if (selectedDay == refreshDay) currentRetryDay(refreshDay)
        }
    }
    val currentFeed by rememberUpdatedState(feed)
    val currentDiscovery by rememberUpdatedState(discovery)
    val adjacentScheduler = remember(refreshDay, discovery?.feedUrl) {
        FootballAdjacentDayScheduler(refreshDay)
    }
    LaunchedEffect(adjacentScheduler, today, lifecycleOwner) {
        if (refreshDay == today) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val readInputs = {
                val state = currentFeed
                val enabled = state.available && selectedDay == refreshDay &&
                    currentDiscovery?.feedUrl == discovery?.feedUrl
                FootballAdjacentDayInputs(
                    todayKey = footballRiyadhDayKey(System.currentTimeMillis()),
                    exactSnapshot = state.daySnapshots[refreshDay],
                    failure = footballEffectiveDayFailure(state.daySnapshots, state.dayFailures, refreshDay),
                    loading = refreshDay in state.loadingDays,
                    retryAtEpochMs = state.nextRetryAtEpochMs,
                    discoveryRefreshSeconds = currentDiscovery?.refreshSeconds?.takeIf { enabled },
                )
            }
            val changes = snapshotFlow(readInputs)
            adjacentScheduler.run(
                readInputs = readInputs,
                awaitChangeOrTimeout = { previous, waitMs ->
                    if (waitMs == null) {
                        changes.first { it != previous }
                    } else {
                        withTimeoutOrNull(waitMs) { changes.first { it != previous } }
                    }
                },
                requestRefresh = { force ->
                    if (force) currentRetryDay(refreshDay) else currentSelectDay(refreshDay)
                },
            )
        }
    }
    val matches = remember(snapshot, activeFilter, favoriteIds) {
        footballMatchesForFilter(snapshot?.matches.orEmpty(), activeFilter, favoriteIds)
    }
    val groups = remember(matches) { footballCenterGroups(matches) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val focusMarginPx = with(density) { 6.dp.roundToPx() }
    var focusedMatchKey by remember { mutableStateOf<String?>(null) }
    val matchLazyIndices = remember(groups) {
        buildMap<String, Int> {
            var index = 0
            groups.forEach { (_, group) ->
                index += 1 // group header
                group.forEach { match ->
                    put(match.stableIdentity, index)
                    index += 1
                }
            }
        }
    }
    LaunchedEffect(focusedMatchKey, groups) {
        val key = focusedMatchKey ?: return@LaunchedEffect
        val index = footballFocusedIndex(key, matchLazyIndices) ?: return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }.collect { info ->
            if (info != null) {
                val delta = footballFocusScrollDelta(
                    itemOffset = info.offset,
                    itemSize = info.size,
                    viewportStart = listState.layoutInfo.viewportStartOffset,
                    viewportEnd = listState.layoutInfo.viewportEndOffset,
                    marginPx = focusMarginPx,
                )
                if (delta != 0) listState.scrollBy(delta.toFloat())
            }
        }
    }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var detailsMatch by remember { mutableStateOf<FootballMatch?>(null) }
    var returnFocusKey by remember { mutableStateOf<String?>(null) }
    var returnFocusEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detailsReturnRevision by remember { mutableLongStateOf(0L) }

    fun requesterFor(match: FootballMatch): FocusRequester =
        rowRequesters.getOrPut(match.stableIdentity) { FocusRequester() }

    LaunchedEffect(detailsReturnRevision) {
        if (returnFocusKey == null && returnFocusEventIds.isEmpty()) return@LaunchedEffect
        var lazyIndex = 0
        var matchIndex = -1
        var restoredMatch: FootballMatch? = null
        groups.forEach { (_, group) ->
            lazyIndex++ // group header
            group.forEach { match ->
                val matchesIdentity = match.stableIdentity == returnFocusKey ||
                    match.eventIds.any { it in returnFocusEventIds }
                if (matchIndex < 0 && matchesIdentity) {
                    matchIndex = lazyIndex
                    restoredMatch = match
                }
                lazyIndex++
            }
        }
        val match = restoredMatch ?: return@LaunchedEffect
        if (matchIndex >= 0) {
            listState.scrollToItem(matchIndex)
            withFrameNanos { }
            runCatching { requesterFor(match).requestFocus() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = topGutter, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "المباريات",
                color = colors.text,
                fontSize = if (isTv) 26.sp else 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            if (isTv) {
                FocusButton(
                    "ادارة فرقي",
                    { onLoadTeams(); managerVisible = true },
                    modifier = Modifier.focusRequester(manageRequester),
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = Icons.Rounded.Favorite,
                    neutralV5 = true,
                )
                FocusButton(
                    if (feed.hideScores) "اظهار النتائج" else "اخفاء النتائج",
                    onToggleHideScores,
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = if (feed.hideScores) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    neutralV5 = true,
                )
            }
            FocusButton(
                "رجوع",
                onBack,
                modifier = Modifier.focusRequester(backRequester),
                compact = true,
                primary = false,
                scaleOnFocus = false,
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                neutralV5 = true,
            )
        }
        if (!isTv) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusButton(
                    "ادارة فرقي",
                    { onLoadTeams(); managerVisible = true },
                    modifier = Modifier.focusRequester(manageRequester),
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = Icons.Rounded.Favorite,
                    neutralV5 = true,
                )
                FocusButton(
                    if (feed.hideScores) "اظهار النتائج" else "اخفاء النتائج",
                    onToggleHideScores,
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = if (feed.hideScores) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    neutralV5 = true,
                )
            }
        }
        if (wideToolbar) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FootballDaySelector(days, selectedDay) { selectedDay = it }
                Box(
                    Modifier.width(1.dp).height(24.dp)
                        .background(NeoNeutralSurfaces.stroke),
                )
                FootballFilterSelector(activeFilter) { filter = it.name }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FootballDaySelector(days, selectedDay) { selectedDay = it }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FootballFilterSelector(activeFilter) { filter = it.name }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (snapshot == null) {
            if (selectedDay in feed.loadingDays) {
                FootballCenterState("جاري تحميل المباريات", true)
            } else {
                val failureCopy = footballCenterFailureCopy(effectiveFailure, currentTimeMs)
                FootballCenterState(
                    failureCopy.title,
                    false,
                    detail = failureCopy.detail,
                )
            }
        } else if (matches.isEmpty()) {
            val emptyCopy = footballCenterEmptyCopy(snapshot, activeFilter)
            val failureCopy = effectiveFailure?.let { footballCenterFailureCopy(it, currentTimeMs) }
            val detail = listOfNotNull(
                emptyCopy.detail,
                failureCopy?.detail,
            ).joinToString(" • ")
            FootballCenterState(
                emptyCopy.title,
                false,
                detail = detail.takeIf { it.isNotBlank() },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                if (snapshot.stale) {
                    val failureCopy = effectiveFailure?.let { footballCenterFailureCopy(it, currentTimeMs) }
                    FootballStaleDayRow(
                        detail = listOfNotNull(
                            "بيانات أولية من تحديث اليوم".takeIf { provisionalDay },
                            failureCopy?.detail,
                        ).joinToString(" • ").ifBlank { "بيانات محفوظة" },
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    groups.forEach { (competition, group) ->
                        item(key = "group-${competition.id}") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                FootballCompetitionMark(
                                    competition,
                                    footballCompetitionMarkSizeDp(
                                        FootballCompetitionMarkSurface.CENTER,
                                        isTv,
                                    ),
                                )
                                Text(
                                    competition.displayName,
                                    color = colors.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        itemsIndexed(group, key = { _, match -> match.stableIdentity }) { _, match ->
                            FootballCenterRow(
                                match = match,
                                hideScores = feed.hideScores,
                                favoriteIds = favoriteIds,
                                nowEpochMs = currentTimeMs,
                                stale = snapshot.stale,
                                modifier = Modifier.focusRequester(requesterFor(match)),
                                onFocused = {
                                    focusedMatchKey = match.stableIdentity
                                },
                                onFocusLost = {
                                    if (focusedMatchKey == match.stableIdentity) {
                                        focusedMatchKey = null
                                    }
                                },
                                onClick = {
                                    returnFocusKey = match.stableIdentity
                                    returnFocusEventIds = match.eventIds
                                    detailsMatch = match
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (managerVisible) {
        FootballTeamManagerDialog(
            allTeams = mergeFootballTeamLists(
                feed.knownTeams,
                footballTeamsFromMatches(feed.allMatches, feed.favoriteTeams),
            ),
            favorites = feed.favoriteTeams,
            isTv = isTv,
            onSave = { teams ->
                onSetTeams(teams)
                managerVisible = false
            },
            onDismiss = { managerVisible = false },
        )
    }
    detailsMatch?.let { opened ->
        val match = resolveFootballMatch(opened, feed.allMatches)
        FootballMatchDetailsDialog(
            match = match,
            favorites = feed.favoriteTeams,
            preferences = resolveFootballAlertPreferences(feed.matchAlerts, match),
            hideScores = feed.hideScores,
            nowEpochMs = currentTimeMs,
            stale = snapshot?.stale == true,
            isTv = isTv,
            onSave = { preferences -> onSaveMatchAlerts(match, preferences) },
            onDismiss = {
                detailsMatch = null
                detailsReturnRevision += 1L
            },
        )
    }
}

@Composable
private fun FootballDaySelector(
    days: List<Pair<String, String>>,
    selectedDay: String,
    onSelect: (String) -> Unit,
) {
    days.forEach { (day, label) ->
        FocusButton(
            label,
            onClick = { onSelect(day) },
            compact = true,
            primary = selectedDay == day,
            scaleOnFocus = false,
            neutralV5 = true,
        )
    }
}

@Composable
private fun FootballFilterSelector(
    activeFilter: FootballCenterFilter,
    onSelect: (FootballCenterFilter) -> Unit,
) {
    FootballCenterFilter.entries.forEach { value ->
        FocusButton(
            value.label,
            onClick = { onSelect(value) },
            compact = true,
            primary = activeFilter == value,
            scaleOnFocus = false,
            neutralV5 = true,
        )
    }
}

@Composable
private fun FootballCenterRow(
    match: FootballMatch,
    hideScores: Boolean,
    favoriteIds: Set<String>,
    nowEpochMs: Long,
    stale: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val isFavorite = favoriteIds.any(match::involvesTeam)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(NeoNeutralSurfaces.card)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                if (focused) 0.dp else 1.dp,
                NeoNeutralSurfaces.stroke,
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else onFocusLost()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FootballTeamCenter(match.homeTeam, Modifier.weight(1f), TextAlign.Start)
        Column(
            Modifier.widthIn(min = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FootballScoreDisplay(match, hideScores, 20)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (match.status == FootballMatchStatus.LIVE) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    footballSecondaryText(match, hideScores, nowEpochMs, stale),
                    color = if (match.status == FootballMatchStatus.LIVE) colors.goldBright else colors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FootballTeamCenter(match.awayTeam, Modifier.weight(1f), TextAlign.End)
        if (isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "فريق متابع",
                tint = colors.gold,
                modifier = Modifier.size(13.dp),
            )
        }
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FootballTeamCenter(
    team: FootballTeam,
    modifier: Modifier,
    textAlign: TextAlign,
) {
    val colors = LocalHulkColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (textAlign == TextAlign.End) Arrangement.End else Arrangement.Start,
    ) {
        if (textAlign == TextAlign.End) {
            FootballCrest(team, 28.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            team.displayName,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (textAlign == TextAlign.Start) {
            Spacer(Modifier.width(7.dp))
            FootballCrest(team, 28.dp)
        }
    }
}

@Composable
private fun FootballStaleDayRow(detail: String) {
    val colors = LocalHulkColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(15.dp),
        )
        Text(
            detail,
            color = colors.textMuted,
            fontSize = 11.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun FootballCenterState(
    text: String,
    loading: Boolean,
    detail: String? = null,
) {
    val colors = LocalHulkColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) LoadingRing(Modifier.size(22.dp)) else Icon(
                Icons.Rounded.SportsSoccer,
                contentDescription = null,
                tint = colors.gold,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f, fill = false)) {
                Text(text, color = colors.textMuted, fontSize = 13.sp)
                if (detail != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(detail, color = colors.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun FootballTeamManagerDialog(
    allTeams: List<FootballTeam>,
    favorites: List<FootballFavoriteTeam>,
    isTv: Boolean,
    onSave: (List<FootballTeam>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var selectedIds by remember(favorites) {
        mutableStateOf<Set<String>>(favorites.mapTo(linkedSetOf(), FootballFavoriteTeam::teamId))
    }
    var query by remember { mutableStateOf("") }
    var regionName by remember { mutableStateOf(FootballTeamRegion.ALL.name) }
    var selectedOnly by remember { mutableStateOf(false) }
    val region = FootballTeamRegion.entries.firstOrNull { it.name == regionName } ?: FootballTeamRegion.ALL
    val catalog = remember(allTeams, favorites) {
        mergeFootballTeamLists(
            allTeams,
            favorites.map { favorite ->
                favorite.team ?: FootballTeam(favorite.teamId, favorite.teamName)
            },
        )
    }
    val filtered = remember(catalog, query, region, selectedOnly, selectedIds) {
        filterFootballTeams(catalog, query)
            .filter { team ->
                (region == FootballTeamRegion.ALL || footballTeamRegion(team) == region) &&
                    (!selectedOnly || team.id in selectedIds)
            }
    }
    val searchRequester = remember { FocusRequester() }
    val allFilterRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val focusMarginPx = with(density) { 6.dp.roundToPx() }
    var focusedTeamKey by remember { mutableStateOf<String?>(null) }
    val teamIndices = remember(filtered) {
        filtered.mapIndexed { index, team -> team.id to index }.toMap()
    }
    LaunchedEffect(focusedTeamKey, filtered) {
        val key = focusedTeamKey ?: return@LaunchedEffect
        val index = footballFocusedIndex(key, teamIndices) ?: return@LaunchedEffect
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }.collect { info ->
            if (info != null) {
                val delta = footballFocusScrollDelta(
                    itemOffset = info.offset.y,
                    itemSize = info.size.height,
                    viewportStart = gridState.layoutInfo.viewportStartOffset,
                    viewportEnd = gridState.layoutInfo.viewportEndOffset,
                    marginPx = focusMarginPx,
                )
                if (delta != 0) gridState.scrollBy(delta.toFloat())
            }
        }
    }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching {
            if (isTv) allFilterRequester.requestFocus() else searchRequester.requestFocus()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            val width = if (isTv) (maxWidth * .76f).coerceIn(620.dp, 980.dp) else (maxWidth * .96f).coerceIn(300.dp, 720.dp)
            val height = (maxHeight * .88f).coerceAtLeast(300.dp)
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .width(width)
                    .heightIn(max = height)
                    .clip(shape)
                    .background(NeoNeutralSurfaces.modal)
                    .border(1.dp, NeoNeutralSurfaces.stroke, shape)
                    .padding(15.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "فرقي المفضلة",
                        color = colors.text,
                        fontSize = if (isTv) 21.sp else 19.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (isTv) {
                        FocusButton(
                            "الكل",
                            { selectedOnly = false },
                            modifier = Modifier.focusRequester(allFilterRequester),
                            compact = true,
                            primary = !selectedOnly,
                            scaleOnFocus = false,
                            neutralV5 = true,
                        )
                        FocusButton(
                            "المختارة (${selectedIds.size})",
                            { selectedOnly = true },
                            compact = true,
                            primary = selectedOnly,
                            scaleOnFocus = false,
                            neutralV5 = true,
                        )
                    }
                }
                if (!isTv) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusButton(
                            "الكل",
                            { selectedOnly = false },
                            modifier = Modifier.focusRequester(allFilterRequester),
                            compact = true,
                            primary = !selectedOnly,
                            scaleOnFocus = false,
                            neutralV5 = true,
                        )
                        FocusButton(
                            "المختارة (${selectedIds.size})",
                            { selectedOnly = true },
                            compact = true,
                            primary = selectedOnly,
                            scaleOnFocus = false,
                            neutralV5 = true,
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                HulkTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "ابحث عن فريق",
                    leadingIcon = Icons.Rounded.Search,
                    leadingIconTint = colors.textMuted,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchRequester),
                    neutralV5 = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        footballVisibleTeamRegions,
                        key = FootballTeamRegion::name,
                    ) { value ->
                        FocusButton(
                            value.label,
                            {
                                regionName = if (region == value) {
                                    FootballTeamRegion.ALL.name
                                } else {
                                    value.name
                                }
                            },
                            compact = true,
                            primary = region == value,
                            scaleOnFocus = false,
                            neutralV5 = true,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    val emptyCopy = when {
                        query.isNotBlank() -> "لا توجد فرق مطابقة"
                        region == FootballTeamRegion.GULF ->
                            "مسابقات الخليج غير متوفرة من المزود الحالي"
                        selectedOnly -> "لم تختر اي فريق بعد"
                        else -> "لا توجد فرق مطابقة"
                    }
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            emptyCopy,
                            color = colors.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isTv) 4 else 2),
                        state = gridState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, team -> team.id }) { _, team ->
                            val selected = team.id in selectedIds
                            FootballTeamTile(
                                team = team,
                                selected = selected,
                                onFocused = { focusedTeamKey = team.id },
                                onFocusLost = {
                                    if (focusedTeamKey == team.id) focusedTeamKey = null
                                },
                                onClick = {
                                    selectedIds = if (selected) {
                                        selectedIds - team.id
                                    } else {
                                        selectedIds + team.id
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FocusButton(
                        "حفظ",
                        {
                            onSave(catalog.filter { it.id in selectedIds })
                            onDismiss()
                        },
                        compact = true,
                        primary = true,
                        scaleOnFocus = false,
                    )
                    FocusButton(
                        "الغاء",
                        onDismiss,
                        compact = true,
                        primary = false,
                        scaleOnFocus = false,
                        neutralV5 = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FootballTeamTile(
    team: FootballTeam,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(NeoNeutralSurfaces.card)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                if (focused) 0.dp else 1.dp,
                NeoNeutralSurfaces.stroke,
                shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else onFocusLost()
            }
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FootballCrest(team, 56.dp)
            Spacer(Modifier.height(5.dp))
            Text(
                team.displayName,
                color = colors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).size(22.dp).clip(CircleShape).background(colors.gold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "مختار", tint = Color.Black, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun FootballMatchDetailsDialog(
    match: FootballMatch,
    favorites: List<FootballFavoriteTeam>,
    preferences: FootballMatchAlertPreferences?,
    hideScores: Boolean,
    nowEpochMs: Long,
    stale: Boolean,
    isTv: Boolean,
    onSave: (FootballMatchAlertPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val followed = favorites.any { match.involvesTeam(it.teamId) }
    var kickoff by remember(match.stableIdentity) { mutableStateOf(preferences?.kickoff ?: followed) }
    var reminder by remember(match.stableIdentity) { mutableStateOf(preferences?.reminder15 == true) }
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            val width = if (isTv) (maxWidth * .66f).coerceIn(520.dp, 760.dp) else (maxWidth * .94f).coerceIn(300.dp, 520.dp)
            val height = (maxHeight * .9f).coerceAtLeast(260.dp)
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .width(width)
                    .heightIn(max = height)
                    .verticalScroll(rememberScrollState())
                    .clip(shape)
                    .background(NeoNeutralSurfaces.modal)
                    .border(1.dp, NeoNeutralSurfaces.stroke, shape)
                    .padding(16.dp),
            ) {
                Text("تفاصيل المباراة", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(NeoNeutralSurfaces.stroke.copy(alpha = .72f)),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FootballHomeTeam(match.homeTeam, 54.dp, Modifier.weight(1f), TextAlign.Start)
                    Column(Modifier.widthIn(min = 118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FootballCompetitionMark(
                            match.competition,
                            footballCompetitionMarkSizeDp(FootballCompetitionMarkSurface.DETAILS, isTv),
                        )
                        Text(
                            match.competition.displayName,
                            color = colors.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        FootballScoreDisplay(match, hideScores, 28)
                        Text(
                            footballRiyadhDayLabel(footballRiyadhDayKey(match.kickoffEpochMs), System.currentTimeMillis()),
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            footballDetailStatusText(match, nowEpochMs, stale),
                            color = colors.textMuted,
                            fontSize = 11.sp,
                        )
                        if (hideScores && match.status.isStarted) {
                            Text("النتيجة مخفية", color = colors.goldBright, fontSize = 10.sp)
                        } else if (
                            match.status.isStarted &&
                            (match.homeScore == null || match.awayScore == null)
                        ) {
                            Text("النتيجة غير متوفرة", color = colors.goldBright, fontSize = 10.sp)
                        }
                    }
                    FootballHomeTeam(match.awayTeam, 54.dp, Modifier.weight(1f), TextAlign.End)
                }
                if (match.broadcasters.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("القناة الناقلة", color = colors.gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        footballBroadcasterSummary(match.broadcasters).orEmpty(),
                        color = colors.text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(NeoNeutralSurfaces.stroke.copy(alpha = .72f)),
                )
                Spacer(Modifier.height(10.dp))
                Text("تنبيهات المباراة", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                FootballAlertToggle("عند بداية المباراة", kickoff) { kickoff = !kickoff }
                Spacer(Modifier.height(6.dp))
                FootballAlertToggle("قبل المباراة بـ 15 دقيقة", reminder) { reminder = !reminder }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FocusButton(
                        "حفظ",
                        {
                            onSave(
                                FootballMatchAlertPreferences(
                                    kickoff = kickoff,
                                    reminder15 = reminder,
                                    manuallyTracked = kickoff || reminder,
                                    trackedAtEpochMs = 0L,
                                ),
                            )
                            onDismiss()
                        },
                        compact = true,
                        primary = true,
                        scaleOnFocus = false,
                    )
                    FocusButton(
                        "رجوع",
                        onDismiss,
                        compact = true,
                        primary = false,
                        scaleOnFocus = false,
                        neutralV5 = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FootballAlertToggle(text: String, checked: Boolean, onClick: () -> Unit) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(NeoNeutralSurfaces.card)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                if (focused) 0.dp else 1.dp,
                NeoNeutralSurfaces.stroke,
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(text, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(21.dp).clip(RoundedCornerShape(5.dp))
                .background(if (checked) colors.goldBright else Color.Transparent)
                .border(1.dp, if (checked) colors.goldBright else colors.textMuted, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp))
        }
    }
}

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import sa.hulksa.player.data.FootballAlertType
import sa.hulksa.player.data.FootballCompetition
import sa.hulksa.player.data.FootballFavoriteTeam
import sa.hulksa.player.data.FootballFeedSnapshot
import sa.hulksa.player.data.FootballFeedUiState
import sa.hulksa.player.data.FootballMatch
import sa.hulksa.player.data.FootballMatchAlertPreferences
import sa.hulksa.player.data.FootballMatchStatus
import sa.hulksa.player.data.FootballTeam
import sa.hulksa.player.data.filterFootballTeams
import sa.hulksa.player.data.footballRiyadhDayKey
import sa.hulksa.player.data.footballRiyadhDayStartEpochMs
import sa.hulksa.player.data.footballRiyadhDayLabel
import sa.hulksa.player.data.footballRiyadhTimeLabel
import sa.hulksa.player.data.footballTeamsFromMatches
import sa.hulksa.player.data.mergeFootballTeamLists
import sa.hulksa.player.data.resolveFootballAlertPreferences
import sa.hulksa.player.data.resolveFootballMatch
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.components.HulkTextField
import sa.hulksa.player.ui.components.LoadingRing
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.components.goldFocusEdge
import sa.hulksa.player.ui.theme.LocalHulkColors

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
            .background(colors.surface),
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
        modifier = modifier.size(size).clip(CircleShape).background(colors.surface),
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

private fun footballStatusText(match: FootballMatch, hideScores: Boolean): String = when (match.status) {
    FootballMatchStatus.LIVE -> match.minute?.let { "مباشر $it'" } ?: "مباشر"
    FootballMatchStatus.FINISHED -> "انتهت"
    FootballMatchStatus.POSTPONED -> "مؤجلة"
    FootballMatchStatus.CANCELLED -> "ملغاة"
    FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
    FootballMatchStatus.UPCOMING -> "لم تبدا"
}

private fun footballDetailStatusText(match: FootballMatch): String = when (match.status) {
    FootballMatchStatus.UPCOMING -> "مؤكدة • لم تبدا"
    FootballMatchStatus.UNRESOLVED -> "غير مؤكدة"
    else -> footballStatusText(match, false)
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
            text = match.homeScore?.toString() ?: "-",
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
            text = match.awayScore?.toString() ?: "-",
            color = colors.text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

private fun footballSecondaryText(match: FootballMatch, hideScores: Boolean): String = when {
    match.status == FootballMatchStatus.LIVE ->
        match.minute?.let { "مباشر $it'" } ?: "مباشر"
    match.status == FootballMatchStatus.FINISHED && hideScores -> "انتهت • النتيجة مخفية"
    match.status == FootballMatchStatus.UPCOMING -> "لم تبدا"
    else -> footballStatusText(match, hideScores)
}

@Composable
internal fun FootballHomeCard(
    match: FootballMatch,
    favoriteIds: Set<String>,
    hideScores: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val isFavorite = favoriteIds.any(match::involvesTeam)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(if (isTv) 302.dp else 274.dp)
            .heightIn(min = 142.dp)
            .clip(shape)
            .background(if (focused) colors.surfaceRaised else colors.surface)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                width = if (focused) 0.dp else 1.dp,
                color = if (isFavorite) colors.gold.copy(alpha = .55f) else colors.line.copy(alpha = .38f),
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = match.competition.displayName,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FootballHomeTeam(match.homeTeam, 38.dp, Modifier.weight(1f), TextAlign.Start)
            Column(
                modifier = Modifier.widthIn(min = 82.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FootballScoreDisplay(match, hideScores, 21)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (match.status == FootballMatchStatus.LIVE) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE5484D)))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = footballSecondaryText(match, hideScores),
                        color = if (match.status == FootballMatchStatus.LIVE) colors.goldBright else colors.textMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            FootballHomeTeam(match.awayTeam, 38.dp, Modifier.weight(1f), TextAlign.End)
        }
    }
}

@Composable
private fun FootballHomeTeam(
    team: FootballTeam,
    crestSize: Dp,
    modifier: Modifier,
    textAlign: TextAlign,
) {
    val colors = LocalHulkColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FootballCrest(team, crestSize)
        Spacer(Modifier.height(4.dp))
        Text(
            text = team.displayName,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun FootballCenterScreen(
    feed: FootballFeedUiState,
    isTv: Boolean,
    onBack: () -> Unit,
    onToggleHideScores: () -> Unit,
    onSelectDay: (String) -> Unit,
    onRefresh: () -> Unit,
    onSaveMatchAlerts: (FootballMatch, FootballMatchAlertPreferences) -> Unit,
    onSetTeams: (List<FootballTeam>) -> Unit,
    onLoadTeams: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val wideToolbar = adaptiveUi.screenWidthDp >= 900
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
    val snapshot = listOfNotNull(
        feed.daySnapshots[selectedDay],
        if (selectedDay == today) feed.snapshot else null,
    ).maxWithOrNull(
        compareBy<FootballFeedSnapshot> { if (it.stale) 0 else 1 }
            .thenBy { it.cachedAtEpochMs },
    )
    val favoriteIds = feed.favoriteTeamIds
    val matches = remember(snapshot, activeFilter, favoriteIds) {
        footballMatchesForFilter(snapshot?.matches.orEmpty(), activeFilter, favoriteIds)
    }
    val groups = remember(matches) { footballCenterGroups(matches) }
    val listState = rememberLazyListState()
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var detailsMatch by remember { mutableStateOf<FootballMatch?>(null) }
    var returnFocusKey by remember { mutableStateOf<String?>(null) }
    var returnFocusEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detailsReturnRevision by remember { mutableLongStateOf(0L) }

    fun requesterFor(match: FootballMatch): FocusRequester =
        rowRequesters.getOrPut(match.stableIdentity) { FocusRequester() }

    LaunchedEffect(selectedDay) {
        onSelectDay(selectedDay)
    }
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
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
                )
                FocusButton(
                    if (feed.hideScores) "اظهار النتائج" else "اخفاء النتائج",
                    onToggleHideScores,
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = if (feed.hideScores) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
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
                )
                FocusButton(
                    if (feed.hideScores) "اظهار النتائج" else "اخفاء النتائج",
                    onToggleHideScores,
                    compact = true,
                    primary = false,
                    scaleOnFocus = false,
                    trailingIcon = if (feed.hideScores) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
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
                        .background(colors.line.copy(alpha = .55f)),
                )
                FootballFilterSelector(activeFilter) { filter = it.name }
                Spacer(Modifier.weight(1f))
                Text("توقيت السعودية", color = colors.textMuted, fontSize = 10.sp)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FootballDaySelector(days, selectedDay) { selectedDay = it }
                Spacer(Modifier.weight(1f))
                Text("توقيت السعودية", color = colors.textMuted, fontSize = 10.sp)
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
        when {
            snapshot == null && selectedDay in feed.loadingDays ->
                FootballCenterState("جاري تحميل المباريات", true)
            snapshot == null -> FootballCenterState(
                "تعذر تحميل مباريات هذا اليوم",
                false,
                onRetry = { onSelectDay(selectedDay) },
            )
            matches.isEmpty() -> FootballCenterState(
                if (snapshot.stale) "لا توجد مباريات مطابقة • بيانات قديمة" else "لا توجد مباريات مطابقة",
                false,
                onRetry = { onSelectDay(selectedDay) },
            )
            else -> Column(Modifier.fillMaxSize()) {
                if (snapshot.stale) {
                    FootballStaleDayRow { onSelectDay(selectedDay) }
                }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                groups.forEach { (competition, group) ->
                    item(key = "group-${competition.id}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            FootballCompetitionMark(competition, 24.dp)
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
                            modifier = Modifier.focusRequester(requesterFor(match)),
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
        )
    }
}

@Composable
private fun FootballCenterRow(
    match: FootballMatch,
    hideScores: Boolean,
    favoriteIds: Set<String>,
    modifier: Modifier = Modifier,
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
            .background(if (focused) colors.surfaceRaised else colors.surface)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                1.dp,
                if (isFavorite) colors.gold.copy(alpha = .55f) else colors.line.copy(alpha = .32f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
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
                    footballSecondaryText(match, hideScores),
                    color = if (match.status == FootballMatchStatus.LIVE) colors.goldBright else colors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FootballTeamCenter(match.awayTeam, Modifier.weight(1f), TextAlign.End)
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
private fun FootballStaleDayRow(onRetry: () -> Unit) {
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
            "بيانات قديمة من اخر تحديث ناجح",
            color = colors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        FocusButton("تحديث", onRetry, compact = true, primary = false, scaleOnFocus = false)
    }
}

@Composable
private fun FootballCenterState(
    text: String,
    loading: Boolean,
    onRetry: (() -> Unit)? = null,
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
            Text(text, color = colors.textMuted, fontSize = 13.sp)
            if (onRetry != null) {
                FocusButton("تحديث", onRetry, compact = true, primary = false, scaleOnFocus = false)
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
                    .background(colors.surface)
                    .border(1.dp, colors.line, shape)
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
                        )
                        FocusButton(
                            "المختارة (${selectedIds.size})",
                            { selectedOnly = true },
                            compact = true,
                            primary = selectedOnly,
                            scaleOnFocus = false,
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
                        )
                        FocusButton(
                            "المختارة (${selectedIds.size})",
                            { selectedOnly = true },
                            compact = true,
                            primary = selectedOnly,
                            scaleOnFocus = false,
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
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = FootballTeam::id) { team ->
                            val selected = team.id in selectedIds
                            FootballTeamTile(
                                team = team,
                                selected = selected,
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
                    FocusButton("الغاء", onDismiss, compact = true, primary = false, scaleOnFocus = false)
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
            .background(if (focused) colors.surfaceRaised else colors.surface)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(
                1.dp,
                if (selected) colors.gold.copy(alpha = .62f) else colors.line.copy(alpha = .32f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
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
                    .background(colors.surface)
                    .border(1.dp, colors.line, shape)
                    .padding(16.dp),
            ) {
                Text("تفاصيل المباراة", color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = .45f)))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FootballHomeTeam(match.homeTeam, 54.dp, Modifier.weight(1f), TextAlign.Start)
                    Column(Modifier.widthIn(min = 118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FootballCompetitionMark(match.competition, 28.dp)
                        Text(
                            match.competition.displayName,
                            color = colors.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        FootballScoreDisplay(match, hideScores, 28)
                        Text(
                            "${footballRiyadhDayLabel(footballRiyadhDayKey(match.kickoffEpochMs), System.currentTimeMillis())} بتوقيت السعودية",
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(footballDetailStatusText(match), color = colors.textMuted, fontSize = 11.sp)
                        if (hideScores && match.status.isStarted) {
                            Text("النتيجة مخفية", color = colors.goldBright, fontSize = 10.sp)
                        }
                    }
                    FootballHomeTeam(match.awayTeam, 54.dp, Modifier.weight(1f), TextAlign.End)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = .45f)))
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
                    FocusButton("رجوع", onDismiss, compact = true, primary = false, scaleOnFocus = false)
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
            .background(if (focused) colors.surfaceRaised else colors.surface)
            .goldFocusEdge(shape = shape, visible = focused)
            .border(1.dp, if (focused) Color.Transparent else colors.line.copy(alpha = .40f), shape)
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

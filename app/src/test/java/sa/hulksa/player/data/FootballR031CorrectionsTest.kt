package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.ui.screens.FootballTeamRegion
import sa.hulksa.player.ui.screens.footballTeamRegion

class FootballR031CorrectionsTest {
    private val now = 1_795_040_000_000L

    @Test
    fun `deleting a reminder never suppresses the independent future kickoff`() {
        val upcoming = match(
            id = "fixture",
            kickoff = now + 10 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val reminderPreferences = mapOf(
            "fixture" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = now - 60_000L,
            ),
        )
        val first = generate(
            current = snapshot(matches = listOf(upcoming)),
            inbox = FootballInboxState(),
            preferences = reminderPreferences,
            nowEpochMs = now,
        )
        val reminder = first.inbox.notifications.single()
        assertEquals(FootballAlertType.REMINDER_15, reminder.alertType)

        val deleted = deleteFootballKickoff(first.inbox, reminder.id)
        val liveAt = now + 20 * 60_000L
        val second = generate(
            current = snapshot(
                matches = listOf(
                    upcoming.copy(
                        status = FootballMatchStatus.LIVE,
                        providerUpdatedAtEpochMs = liveAt - 60_000L,
                    ),
                ),
            ).copy(cachedAtEpochMs = liveAt, generatedAtEpochMs = liveAt),
            inbox = deleted,
            preferences = emptyMap(),
            nowEpochMs = liveAt,
        )

        assertEquals(1, second.added.size)
        assertEquals(FootballAlertType.KICKOFF, second.added.single().alertType)
    }

    @Test
    fun `deleting a kickoff never suppresses a reminder`() {
        val live = match(
            id = "fixture",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
            providerUpdatedAtEpochMs = now - 60_000L,
        )
        val first = generate(
            current = snapshot(matches = listOf(live)),
            inbox = FootballInboxState(),
            nowEpochMs = now,
        )
        val deleted = deleteFootballKickoff(first.inbox, first.inbox.notifications.single().id)
        val upcomingAt = now + 30 * 60_000L
        val second = generate(
            current = snapshot(
                matches = listOf(
                    live.copy(
                        kickoffEpochMs = upcomingAt + 5 * 60_000L,
                        status = FootballMatchStatus.UPCOMING,
                    ),
                ),
            ).copy(cachedAtEpochMs = upcomingAt, generatedAtEpochMs = upcomingAt),
            inbox = deleted,
            preferences = mapOf(
                "fixture" to FootballMatchAlertPreferences(
                    reminder15 = true,
                    manuallyTracked = true,
                    trackedAtEpochMs = upcomingAt,
                ),
            ),
            nowEpochMs = upcomingAt,
        )

        assertEquals(1, second.added.size)
        assertEquals(FootballAlertType.REMINDER_15, second.added.single().alertType)
    }

    @Test
    fun `legacy kickoff and new alert tombstones are scoped by type and profile`() {
        val live = match(
            id = "fixture",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
            providerUpdatedAtEpochMs = now - 60_000L,
        )
        val legacy = FootballInboxState(suppressedEventIds = setOf("fixture"))
        val notSuppressedKickoff = generate(
            current = snapshot(matches = listOf(live)),
            inbox = legacy.copy(suppressedEventIds = emptySet()),
            nowEpochMs = now,
        )
        val suppressedKickoff = generate(
            current = snapshot(matches = listOf(live)),
            inbox = legacy,
            nowEpochMs = now,
        )
        assertEquals(1, notSuppressedKickoff.added.size)
        assertTrue(suppressedKickoff.added.isEmpty())

        val reminderAt = now + 5 * 60_000L
        val reminderMatch = live.copy(
            kickoffEpochMs = reminderAt + 10 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
        )
        val reminderPrefs = mapOf(
            "fixture" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = reminderAt - 60_000L,
            ),
        )
        val legacyReminder = generate(
            current = snapshot(matches = listOf(reminderMatch)).copy(
                cachedAtEpochMs = reminderAt,
                generatedAtEpochMs = reminderAt,
            ),
            inbox = legacy,
            preferences = reminderPrefs,
            nowEpochMs = reminderAt,
        )
        assertEquals(1, legacyReminder.added.size)
        assertEquals(FootballAlertType.REMINDER_15, legacyReminder.added.single().alertType)

        val profileTombstone = FootballInboxState(
            suppressedAlerts = setOf(
                footballAlertSuppressionKey("account-1", "profile-1", "fixture", FootballAlertType.KICKOFF),
            ),
        )
        val otherProfile = generate(
            accountId = "account-1",
            profileId = "profile-2",
            current = snapshot(matches = listOf(live)),
            inbox = profileTombstone,
            nowEpochMs = now,
        )
        val sameProfile = generate(
            accountId = "account-1",
            profileId = "profile-1",
            current = snapshot(matches = listOf(live)),
            inbox = profileTombstone,
            nowEpochMs = now,
        )
        assertEquals(1, otherProfile.added.size)
        assertTrue(sameProfile.added.isEmpty())
    }

    @Test
    fun `a later opposing selection does not invalidate the earlier subscription`() {
        val previous = snapshot(
            matches = listOf(
                match(
                    id = "fixture",
                    kickoff = now - 30 * 60_000L,
                    status = FootballMatchStatus.UPCOMING,
                ),
            ),
        ).copy(cachedAtEpochMs = now - 5 * 60_000L, generatedAtEpochMs = now - 5 * 60_000L)
        val current = snapshot(
            matches = listOf(
                match(
                    id = "fixture",
                    kickoff = now - 30 * 60_000L,
                    status = FootballMatchStatus.LIVE,
                    providerUpdatedAtEpochMs = now - 20 * 60_000L,
                ),
            ),
        )
        val result = generateFootballMatchAlerts(
            accountId = "account-1",
            profileId = "profile-1",
            favoriteTeams = listOf(
                FootballFavoriteTeam("home", "Home", now - 10 * 60_000L),
                FootballFavoriteTeam("away", "Away", now - 60_000L),
            ),
            preferences = emptyMap(),
            previous = previous,
            current = current,
            inbox = FootballInboxState(),
            nowEpochMs = now,
            previousObservedInSession = true,
            previousObservedAtEpochMs = now - 5 * 60_000L,
        )

        assertEquals(1, result.added.size)
    }

    @Test
    fun `unchanged preferences preserve eligibility and changed preferences reset it`() {
        val upcoming = match(
            id = "new",
            kickoff = now + 60 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
            aliases = listOf("old"),
        )
        val existing = mapOf(
            "old" to FootballMatchAlertPreferences(
                kickoff = true,
                reminder15 = false,
                manuallyTracked = true,
                trackedAtEpochMs = 100L,
            ),
        )
        val unchanged = upsertFootballAlertPreferences(
            current = existing,
            match = upcoming,
            preferences = FootballMatchAlertPreferences(
                kickoff = true,
                reminder15 = false,
                manuallyTracked = true,
                trackedAtEpochMs = 999L,
            ),
            nowEpochMs = 500L,
        )
        assertEquals(100L, unchanged.getValue("new").trackedAtEpochMs)
        assertFalse("old" in unchanged)

        val changed = upsertFootballAlertPreferences(
            current = unchanged,
            match = upcoming,
            preferences = FootballMatchAlertPreferences(
                kickoff = false,
                reminder15 = false,
                manuallyTracked = false,
            ),
            nowEpochMs = 700L,
        )
        assertEquals(700L, changed.getValue("new").trackedAtEpochMs)
        assertFalse(changed.getValue("new").kickoff)
    }

    @Test
    fun `new notifications use Arabic display names`() {
        val match = match(
            id = "arabic",
            kickoff = now + 10 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
        ).copy(
            competition = FootballCompetition("17", "Saudi Pro League", arabicName = "دوري روشن السعودي"),
            homeTeam = FootballTeam("262", "Al Hilal", arabicName = "الهلال"),
            awayTeam = FootballTeam("267", "Al Nassr", arabicName = "النصر"),
        )
        val notification = footballKickoffNotificationFor(
            accountId = "account-1",
            profileId = "profile-1",
            match = match,
            detectedAtEpochMs = now,
            alertType = FootballAlertType.REMINDER_15,
        )

        assertEquals("الهلال", notification.homeTeamName)
        assertEquals("النصر", notification.awayTeamName)
        assertEquals("دوري روشن السعودي", notification.competitionName)
    }

    @Test
    fun `suppression codec keeps legacy and typed tombstones`() {
        val inbox = FootballInboxState(
            suppressedEventIds = setOf("legacy"),
            suppressedAlerts = setOf(
                footballAlertSuppressionKey("account-1", "profile-1", "fixture", FootballAlertType.REMINDER_15),
            ),
        )

        val decoded = decodeFootballInboxState(encodeFootballInboxState(inbox))

        assertEquals(inbox.suppressedEventIds, decoded.suppressedEventIds)
        assertEquals(inbox.suppressedAlerts, decoded.suppressedAlerts)
    }

    @Test
    fun `latest explicit alias preference wins over an obsolete fixture id`() {
        val match = match(
            id = "new",
            kickoff = now + 60 * 60_000L,
            status = FootballMatchStatus.UPCOMING,
            aliases = listOf("old"),
        )
        val preferences = mapOf(
            "old" to FootballMatchAlertPreferences(kickoff = true, trackedAtEpochMs = 100L),
            "new" to FootballMatchAlertPreferences(kickoff = false, trackedAtEpochMs = 200L),
        )

        val resolved = resolveFootballAlertPreferences(preferences, match)
        assertNotNull(resolved)
        assertFalse(checkNotNull(resolved).kickoff)
    }

    @Test
    fun `reschedule updates one reminder and keeps its read state`() {
        val kickoff = now + 30 * 60_000L
        val upcoming = match(id = "fixture", kickoff = kickoff, status = FootballMatchStatus.UPCOMING)
        val preferences = mapOf(
            "fixture" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = now,
            ),
        )
        val first = generate(
            current = snapshot(matches = listOf(upcoming)).copy(
                cachedAtEpochMs = kickoff - 15 * 60_000L,
                generatedAtEpochMs = kickoff - 15 * 60_000L,
            ),
            inbox = FootballInboxState(),
            preferences = preferences,
            nowEpochMs = kickoff - 15 * 60_000L,
        )
        val reminder = first.inbox.notifications.single()
        val read = markFootballKickoffRead(first.inbox, reminder.id)
        val laterKickoff = kickoff + 60 * 60_000L
        val rescheduled = generate(
            current = snapshot(
                matches = listOf(upcoming.copy(kickoffEpochMs = laterKickoff)),
            ).copy(cachedAtEpochMs = now + 60_000L, generatedAtEpochMs = now + 60_000L),
            inbox = read,
            preferences = preferences,
            nowEpochMs = now + 60_000L,
        )

        val retained = rescheduled.inbox.notifications.single()
        assertEquals(laterKickoff, retained.kickoffEpochMs)
        assertTrue(retained.read)
        assertTrue(rescheduled.added.isEmpty())
    }

    @Test
    fun `cancelled and reappearing fixtures keep one delivered item and its read state`() {
        val live = match(
            id = "fixture",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
            homeScore = 2,
            awayScore = 1,
            providerUpdatedAtEpochMs = now - 60_000L,
        )
        val first = generate(current = snapshot(matches = listOf(live)), nowEpochMs = now)
        val read = markFootballKickoffRead(first.inbox, first.inbox.notifications.single().id)

        val cancelledAt = now + 60_000L
        val cancelled = generate(
            current = snapshot(
                matches = listOf(live.copy(status = FootballMatchStatus.CANCELLED, homeScore = null, awayScore = null)),
            ).copy(cachedAtEpochMs = cancelledAt, generatedAtEpochMs = cancelledAt),
            inbox = read,
            nowEpochMs = cancelledAt,
        )
        val cancelledItem = cancelled.inbox.notifications.single()
        assertEquals(FootballMatchStatus.CANCELLED, cancelledItem.status)
        assertEquals(2, cancelledItem.homeScore)
        assertEquals(1, cancelledItem.awayScore)
        assertTrue(cancelledItem.read)

        val disappearedAt = cancelledAt + 60_000L
        val disappeared = generate(
            current = snapshot(matches = emptyList()).copy(
                cachedAtEpochMs = disappearedAt,
                generatedAtEpochMs = disappearedAt,
            ),
            inbox = cancelled.inbox,
            nowEpochMs = disappearedAt,
        )
        assertEquals(1, disappeared.inbox.notifications.size)

        val reappearedAt = disappearedAt + 60_000L
        val reappeared = generate(
            current = snapshot(matches = listOf(live)).copy(
                cachedAtEpochMs = reappearedAt,
                generatedAtEpochMs = reappearedAt,
            ),
            inbox = disappeared.inbox,
            nowEpochMs = reappearedAt,
        )
        assertEquals(1, reappeared.inbox.notifications.size)
        assertEquals(FootballMatchStatus.LIVE, reappeared.inbox.notifications.single().status)
        assertTrue(reappeared.added.isEmpty())
    }

    @Test
    fun `reminder text never claims fifteen minutes before a rescheduled kickoff`() {
        val notification = footballKickoffNotificationFor(
            accountId = "account-1",
            profileId = "profile-1",
            match = match(
                id = "fixture",
                kickoff = now + 45 * 60_000L,
                status = FootballMatchStatus.UPCOMING,
            ),
            detectedAtEpochMs = now,
            alertType = FootballAlertType.REMINDER_15,
        )
        val detail = footballKickoffDetail(notification, nowEpochMs = now)
        assertFalse(detail.contains("بعد 15"))
    }

    @Test
    fun `a tomorrow fixture whose reminder is due before Riyadh midnight is generated today`() {
        val today = footballRiyadhDayKey(now)
        val tomorrowStart = checkNotNull(footballRiyadhDayStartEpochMs(
            footballRiyadhDayKey(now + 24L * 60L * 60L * 1_000L),
        ))
        val beforeMidnight = tomorrowStart - 10 * 60_000L
        val kickoff = tomorrowStart + 5 * 60_000L
        val match = match(
            id = "midnight",
            kickoff = kickoff,
            status = FootballMatchStatus.UPCOMING,
        )
        val preferences = mapOf(
            "midnight" to FootballMatchAlertPreferences(
                reminder15 = true,
                manuallyTracked = true,
                trackedAtEpochMs = beforeMidnight - 60_000L,
            ),
        )
        val result = generate(
            current = FootballFeedSnapshot(
                dayKey = today,
                generatedAtEpochMs = beforeMidnight,
                cachedAtEpochMs = beforeMidnight,
                stale = false,
                retryAfterSeconds = null,
                refreshSeconds = 60,
                matches = emptyList(),
                teams = emptyList(),
                reminderCandidates = listOf(match),
            ),
            inbox = FootballInboxState(),
            preferences = preferences,
            nowEpochMs = beforeMidnight,
        )

        assertEquals(1, result.added.size)
        assertEquals(FootballAlertType.REMINDER_15, result.added.single().alertType)
        assertEquals(kickoff, result.added.single().kickoffEpochMs)
    }

    @Test
    fun `an in-flight refresh cannot restore an older hide-scores value`() {
        assertTrue(footballEffectiveHideScores(false, stateAvailable = true, stateHideScores = true))
        assertFalse(footballEffectiveHideScores(true, stateAvailable = true, stateHideScores = false))
        assertTrue(footballEffectiveHideScores(true, stateAvailable = false, stateHideScores = false))
    }

    @Test
    fun `selected-day cache TTL expires instead of using a non-stale shortcut`() {
        val snapshot = snapshot(matches = emptyList())
        assertTrue(footballSnapshotCacheFresh(snapshot, now + 59_000L, 60))
        assertFalse(footballSnapshotCacheFresh(snapshot, now + 61_000L, 60))
        assertFalse(footballSnapshotCacheFresh(snapshot.copy(stale = true), now, 60))
        assertFalse(footballSnapshotCacheFresh(null, now, 60))
        assertTrue(footballSnapshotObservationFresh(snapshot, now + 60_000L))
        assertFalse(footballSnapshotObservationFresh(snapshot, now + 11 * 60_000L))
    }

    @Test
    fun `expired cached observations cannot create new starts or reminders`() {
        val live = match(
            id = "expired",
            kickoff = now - 30 * 60_000L,
            status = FootballMatchStatus.LIVE,
            providerUpdatedAtEpochMs = now - 60_000L,
        )
        val staleObservation = snapshot(matches = listOf(live)).copy(
            cachedAtEpochMs = now - 60 * 60_000L,
            generatedAtEpochMs = now - 60 * 60_000L,
        )
        val result = generate(
            current = staleObservation,
            inbox = FootballInboxState(),
            preferences = mapOf(
                "expired" to FootballMatchAlertPreferences(
                    reminder15 = true,
                    manuallyTracked = true,
                    trackedAtEpochMs = now - 2 * 60 * 60_000L,
                ),
            ),
            nowEpochMs = now,
        )
        assertTrue(result.added.isEmpty())
        assertTrue(result.inbox.notifications.isEmpty())
    }

    @Test
    fun `provider observation time gates an unobserved provider start`() {
        val tooOld = match(
            id = "provider-start",
            kickoff = now - 10 * 60_000L,
            status = FootballMatchStatus.LIVE,
            providerUpdatedAtEpochMs = now - 20 * 60_000L,
        )
        val confirmed = tooOld.copy(providerUpdatedAtEpochMs = now - 5 * 60_000L)

        val rejected = generate(current = snapshot(matches = listOf(tooOld)), nowEpochMs = now)
        val accepted = generate(current = snapshot(matches = listOf(confirmed)), nowEpochMs = now)

        assertTrue(rejected.added.isEmpty())
        assertEquals(1, accepted.added.size)
    }

    @Test
    fun `team merge enriches metadata by stable id instead of discarding it`() {
        val base = FootballTeam(id = "262", name = "Al Hilal", arabicName = "الهلال")
        val enriched = FootballTeam(
            id = "262",
            name = "Al Hilal",
            arabicName = "الهلال",
            logoUrl = "https://sports.bzzoiro.com/img/team/262/",
            country = "Saudi Arabia",
            competitionIds = setOf("17", "7"),
            nationalTeam = false,
            aliases = listOf("Hilal"),
        )
        val merged = mergeFootballTeamLists(listOf(base), listOf(enriched)).single()

        assertEquals("الهلال", merged.arabicName)
        assertEquals(enriched.logoUrl, merged.logoUrl)
        assertEquals("Saudi Arabia", merged.country)
        assertEquals(setOf("17", "7"), merged.competitionIds)
        assertTrue("Hilal" in merged.aliases)
        assertEquals("Al Hilal", merged.name)
    }

    @Test
    fun `newer cancellation updates status but keeps the confirmed final score`() {
        val final = match(
            id = "final",
            kickoff = now - 3 * 60 * 60_000L,
            status = FootballMatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1,
            providerUpdatedAtEpochMs = now - 2 * 60 * 60_000L,
        )
        val cancelled = final.copy(
            status = FootballMatchStatus.CANCELLED,
            homeScore = null,
            awayScore = null,
            providerUpdatedAtEpochMs = now - 60 * 60_000L,
        )

        val merged = mergeFootballMatchRows(listOf(final, cancelled)).single()
        val protectedFinal = mergeFootballMatchRows(listOf(cancelled, final)).single()

        assertEquals(FootballMatchStatus.CANCELLED, merged.status)
        assertEquals(2, merged.homeScore)
        assertEquals(1, merged.awayScore)
        assertEquals(FootballMatchStatus.CANCELLED, protectedFinal.status)
        assertEquals(2, protectedFinal.homeScore)
    }

    @Test
    fun `feed parser keeps provider freshness and reminder candidates`() {
        val raw = """
            {
              "schemaVersion":1,
              "day":"2026-11-19",
              "generatedAt":1795030200,
              "cachedAt":1795030200,
              "stale":false,
              "refreshSeconds":60,
              "matches":[],
              "reminderCandidates":[{
                "providerEventId":"tomorrow",
                "competition":{"id":"17","name":"Saudi Pro League","priority":10},
                "homeTeam":{"id":"262","name":"Al Hilal"},
                "awayTeam":{"id":"267","name":"Al Nassr"},
                "kickoffAt":1795040700,
                "status":"upcoming",
                "providerUpdatedAt":1795030000
              }],
              "teams":[]
            }
        """.trimIndent()

        val parsed = checkNotNull(parseFootballFeed(raw, "2026-11-19"))
        val reminder = parsed.reminderCandidates.single()
        assertEquals("tomorrow", reminder.providerEventId)
        assertEquals(1_795_030_000_000L, reminder.providerUpdatedAtEpochMs)
        assertTrue(parsed.matches.isEmpty())
    }

    @Test
    fun `unknown teams are not classified as Arab`() {
        assertEquals(
            FootballTeamRegion.OTHER,
            footballTeamRegion(FootballTeam(id = "x", name = "Unknown FC")),
        )
        assertEquals(
            FootballTeamRegion.SAUDI,
            footballTeamRegion(FootballTeam(id = "262", name = "Al Hilal", competitionIds = setOf("17"))),
        )
    }

    @Test
    fun `details resolution follows aliases and the newest score`() {
        val opened = match(
            id = "old",
            kickoff = now,
            status = FootballMatchStatus.LIVE,
            homeScore = 0,
            awayScore = 0,
        )
        val latest = match(
            id = "new",
            kickoff = now,
            status = FootballMatchStatus.LIVE,
            homeScore = 2,
            awayScore = 1,
            aliases = listOf("old"),
        )

        val resolved = resolveFootballMatch(opened, listOf(latest))
        assertEquals("new", resolved.providerEventId)
        assertEquals(2, resolved.homeScore)
        assertEquals(1, resolved.awayScore)
    }

    private fun generate(
        accountId: String = "account-1",
        profileId: String = "profile-1",
        current: FootballFeedSnapshot,
        inbox: FootballInboxState = FootballInboxState(),
        preferences: Map<String, FootballMatchAlertPreferences> = emptyMap(),
        nowEpochMs: Long,
    ): FootballKickoffGeneration = generateFootballMatchAlerts(
        accountId = accountId,
        profileId = profileId,
        favoriteTeams = listOf(FootballFavoriteTeam("home", "Home", nowEpochMs - 60 * 60_000L)),
        preferences = preferences,
        previous = null,
        current = current,
        inbox = inbox,
        nowEpochMs = nowEpochMs,
    )

    private fun snapshot(matches: List<FootballMatch>): FootballFeedSnapshot = FootballFeedSnapshot(
        dayKey = footballRiyadhDayKey(now),
        generatedAtEpochMs = now,
        cachedAtEpochMs = now,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches,
        teams = emptyList(),
    )

    private fun match(
        id: String,
        kickoff: Long,
        status: FootballMatchStatus,
        homeId: String = "home",
        awayId: String = "away",
        aliases: List<String> = emptyList(),
        homeScore: Int? = null,
        awayScore: Int? = null,
        providerUpdatedAtEpochMs: Long? = null,
        minute: Int? = null,
    ): FootballMatch = FootballMatch(
        providerEventId = id,
        aliases = aliases,
        competition = FootballCompetition("85", "Saudi Pro League", priority = 10),
        homeTeam = FootballTeam(homeId, homeId),
        awayTeam = FootballTeam(awayId, awayId),
        kickoffEpochMs = kickoff,
        status = status,
        homeScore = homeScore,
        awayScore = awayScore,
        minute = minute,
        providerUpdatedAtEpochMs = providerUpdatedAtEpochMs,
    )
}

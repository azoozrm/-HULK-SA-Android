package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballKickoffInboxTest {
    private val now = 1_795_040_000_000L

    @Test
    fun `a fresh provider transition confirms a start`() {
        val previous = snapshot(
            match(id = "1", kickoff = now - 10 * 60_000L, status = FootballMatchStatus.UPCOMING),
        )
        val current = snapshot(
            match(id = "1", kickoff = now - 10 * 60_000L, status = FootballMatchStatus.LIVE),
        )

        val result = generate(
            previous = previous,
            current = current,
        )

        assertEquals(1, result.added.size)
        assertEquals("kickoff|account-1|profile-1|1", result.added.single().id)
        assertEquals(1, result.inbox.unreadCount)
    }

    @Test
    fun `clock passage without a started provider status never creates an inbox item`() {
        val upcoming = snapshot(
            match(id = "1", kickoff = now - 60 * 60_000L, status = FootballMatchStatus.UPCOMING),
        )

        assertTrue(generate(current = upcoming).added.isEmpty())
    }

    @Test
    fun `a live response with a trustworthy actual kickoff timestamp confirms a start`() {
        val tooFresh = snapshot(
            match(id = "1", kickoff = now - 30_000L, status = FootballMatchStatus.LIVE),
        )
        val confirmed = snapshot(
            match(id = "1", kickoff = now - 2 * 60_000L, status = FootballMatchStatus.LIVE),
        )

        assertTrue(generate(current = tooFresh).added.isEmpty())
        assertEquals(1, generate(current = confirmed).added.size)
    }

    @Test
    fun `only the selected team is reported and other fixtures remain silent`() {
        val current = snapshot(
            match(id = "1", kickoff = now - 5 * 60_000L, status = FootballMatchStatus.LIVE),
            match(
                id = "2",
                kickoff = now - 60_000L,
                status = FootballMatchStatus.LIVE,
                homeId = "other-home",
                awayId = "other-away",
            ),
        )

        val result = generate(current = current)

        assertEquals(listOf("1"), result.added.map(FootballKickoffNotification::eventId))
    }

    @Test
    fun `catch-up only includes confirmed starts after selection and inside 24 hours`() {
        val selectedAt = now - 3 * 60 * 60_000L
        val current = snapshot(
            match(id = "after", kickoff = now - 2 * 60 * 60_000L, status = FootballMatchStatus.FINISHED),
            match(id = "before", kickoff = now - 4 * 60 * 60_000L, status = FootballMatchStatus.FINISHED),
            match(id = "old", kickoff = now - 25 * 60 * 60_000L, status = FootballMatchStatus.FINISHED),
        )

        val result = generate(
            current = current,
            favorite = FootballFavoriteTeam("home", "Home", selectedAt),
            catchUpOnly = true,
        )

        assertEquals(listOf("after"), result.added.map(FootballKickoffNotification::eventId))
    }

    @Test
    fun `dedupe keeps one stable event per account profile and preserves read state`() {
        val current = snapshot(
            match(id = "1", kickoff = now - 5 * 60_000L, status = FootballMatchStatus.LIVE),
        )
        val first = generate(current = current)
        val read = markFootballKickoffRead(first.inbox, first.inbox.notifications.single().id)
        val second = generate(current = current, inbox = read)

        assertTrue(second.added.isEmpty())
        assertEquals(1, second.inbox.notifications.size)
        assertTrue(second.inbox.notifications.single().read)
    }

    @Test
    fun `replaced_by aliases dedupe the old and replacement event under one notification`() {
        val oldMatch = match(id = "101", kickoff = now - 5 * 60_000L, status = FootballMatchStatus.LIVE)
        val first = generate(current = snapshot(oldMatch))
        val replacement = match(
            id = "202",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.FINISHED,
            aliases = listOf("101"),
            homeScore = 2,
            awayScore = 1,
        )
        val second = generate(
            previous = snapshot(oldMatch),
            current = snapshot(replacement),
            inbox = first.inbox,
        )

        assertTrue(second.added.isEmpty())
        val notification = second.inbox.notifications.single()
        assertTrue("101" in notification.eventIds)
        assertTrue("202" in notification.eventIds)
        assertEquals(FootballMatchStatus.FINISHED, notification.status)
        assertEquals(2, notification.homeScore)
    }

    @Test
    fun `deleting a kickoff suppresses the same stable event on resync`() {
        val current = snapshot(
            match(id = "1", kickoff = now - 5 * 60_000L, status = FootballMatchStatus.LIVE),
        )
        val first = generate(current = current)
        val deleted = deleteFootballKickoff(first.inbox, first.inbox.notifications.single().id)
        val resynced = generate(current = current, inbox = deleted)

        assertTrue(deleted.notifications.isEmpty())
        assertTrue(resynced.added.isEmpty())
        assertTrue(resynced.inbox.notifications.isEmpty())
    }

    @Test
    fun `clear-all watermark blocks old fixtures while a later kickoff can still arrive`() {
        val first = generate(
            current = snapshot(
                match(id = "1", kickoff = now - 6 * 60_000L, status = FootballMatchStatus.FINISHED),
                match(id = "2", kickoff = now - 4 * 60_000L, status = FootballMatchStatus.FINISHED),
            ),
        )
        val cleared = clearFootballKickoffs(first.inbox)
        val oldResync = generate(
            current = snapshot(
                match(id = "1", kickoff = now - 6 * 60_000L, status = FootballMatchStatus.FINISHED),
                match(id = "2", kickoff = now - 4 * 60_000L, status = FootballMatchStatus.FINISHED),
            ),
            inbox = cleared,
        )
        val later = generate(
            current = snapshot(
                match(id = "3", kickoff = now + 60_000L, status = FootballMatchStatus.LIVE),
            ),
            inbox = oldResync.inbox,
            now = now + 3 * 60_000L,
        )

        assertTrue(oldResync.added.isEmpty())
        assertEquals(1, later.added.size)
        assertEquals("3", later.added.single().eventId)
    }

    @Test
    fun `inbox codec round-trips read delete clear and nullable scores`() {
        val inbox = FootballInboxState(
            notifications = listOf(
                FootballKickoffNotification(
                    id = "kickoff|account-1|profile-1|9",
                    accountId = "account-1",
                    profileId = "profile-1",
                    eventId = "9",
                    eventIds = setOf("9", "8"),
                    competitionName = "Saudi Pro League",
                    homeTeamName = "Al Hilal",
                    awayTeamName = "Al Nassr",
                    kickoffEpochMs = now,
                    detectedAtEpochMs = now + 1_000L,
                    read = true,
                    homeScore = null,
                    awayScore = 3,
                    status = FootballMatchStatus.FINISHED,
                ),
            ),
            suppressedEventIds = setOf("7"),
            clearedThroughEpochMs = now - 1_000L,
        )

        val decoded = decodeFootballInboxState(encodeFootballInboxState(inbox))

        assertEquals(inbox, decoded)
    }

    @Test
    fun `favorite codec preserves one selected team`() {
        val favorite = FootballFavoriteTeam("10", "Al Hilal", now)
        assertEquals(favorite, decodeFootballFavoriteTeam(encodeFootballFavoriteTeam(favorite)))
    }

    @Test
    fun `a stale last-good snapshot cannot regress a fresher live score`() {
        val live = match(
            id = "1",
            kickoff = now - 5 * 60_000L,
            status = FootballMatchStatus.LIVE,
            homeScore = 2,
            awayScore = 1,
        )
        val first = generate(current = snapshot(live))
        val staleOldScore = live.copy(homeScore = 0, awayScore = 0)
        val stale = snapshot(staleOldScore).copy(stale = true)
        val second = generate(current = stale, previous = snapshot(live), inbox = first.inbox)

        assertEquals(2, second.inbox.notifications.single().homeScore)
        assertEquals(1, second.inbox.notifications.single().awayScore)
    }

    @Test
    fun `kickoff notification merges into the existing center and bell count`() {
        val inbox = FootballInboxState(
            notifications = listOf(
                footballKickoffNotificationFor(
                    accountId = "account-1",
                    profileId = "profile-1",
                    match = match(id = "1", kickoff = now, status = FootballMatchStatus.LIVE),
                    detectedAtEpochMs = now,
                ),
            ),
        )
        val merged = mergeNotificationCenterItems(
            episodeNotifications = emptyList(),
            systemNotifications = emptyList(),
            kickoffNotifications = inbox.notifications,
        )

        assertEquals(1, merged.size)
        assertEquals(LocalNotificationKind.FOOTBALL_KICKOFF, merged.single().kind)
        assertEquals(1, inbox.unreadCount)
    }

    @Test
    fun `known team codec keeps stable ids for empty-day search`() {
        val teams = listOf(FootballTeam("10", "Al Hilal"), FootballTeam("20", "Al Nassr"))
        assertEquals(teams, decodeFootballTeams(encodeFootballTeams(teams)))
    }

    private fun generate(
        current: FootballFeedSnapshot,
        previous: FootballFeedSnapshot? = null,
        inbox: FootballInboxState = FootballInboxState(),
        favorite: FootballFavoriteTeam = FootballFavoriteTeam("home", "Home", 0L),
        now: Long = this.now,
        catchUpOnly: Boolean = false,
    ): FootballKickoffGeneration = generateFootballKickoffNotifications(
        accountId = "account-1",
        profileId = "profile-1",
        favoriteTeam = favorite,
        previous = previous,
        current = current,
        inbox = inbox,
        nowEpochMs = now,
        catchUpOnly = catchUpOnly,
    )

    private fun snapshot(vararg matches: FootballMatch): FootballFeedSnapshot = FootballFeedSnapshot(
        dayKey = footballRiyadhDayKey(now),
        generatedAtEpochMs = now,
        cachedAtEpochMs = now,
        stale = false,
        retryAfterSeconds = null,
        refreshSeconds = 60,
        matches = matches.toList(),
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
    ): FootballMatch = FootballMatch(
        providerEventId = id,
        aliases = aliases,
        competition = FootballCompetition("85", "Saudi Pro League", "Saudi Arabia"),
        homeTeam = FootballTeam(homeId, "Home"),
        awayTeam = FootballTeam(awayId, "Away"),
        kickoffEpochMs = kickoff,
        status = status,
        homeScore = homeScore,
        awayScore = awayScore,
    )
}

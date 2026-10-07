package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType

class LivePlayerControlsPolicyTest {
    @Test
    fun `more panel back closes from menu and returns children to the originating row`() {
        assertNull(playerLiveMorePanelBack(PlayerLiveMorePanelView.MENU))
        assertEquals(
            PlayerLiveMorePanelView.MENU,
            playerLiveMorePanelBack(PlayerLiveMorePanelView.SOURCE),
        )
        assertEquals(
            PlayerLiveMorePanelView.MENU,
            playerLiveMorePanelBack(PlayerLiveMorePanelView.RESIZE),
        )

        assertNull(playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.MENU))
        assertEquals(
            PlayerLiveMoreRow.SOURCE,
            playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.SOURCE),
        )
        assertEquals(
            PlayerLiveMoreRow.RESIZE,
            playerLiveMorePanelOriginRow(PlayerLiveMorePanelView.RESIZE),
        )
    }

    @Test
    fun `more panel participates in child panel input ownership`() {
        assertFalse(playerChildPanelInputActive(hasActivePanel = false, hasLiveMorePanel = false))
        assertTrue(playerChildPanelInputActive(hasActivePanel = true, hasLiveMorePanel = false))
        assertTrue(playerChildPanelInputActive(hasActivePanel = false, hasLiveMorePanel = true))
        assertTrue(playerChildPanelInputActive(hasActivePanel = true, hasLiveMorePanel = true))
    }

    @Test
    fun `favorite control is disabled without the authoritative catalog channel`() {
        val channel = channel(id = 7)

        val enabled = livePlayerFavoriteControl(channel, isFavorite = true)
        assertTrue(enabled.enabled)
        assertTrue(enabled.favorite)

        val missing = livePlayerFavoriteControl(null, isFavorite = true)
        assertFalse(missing.enabled)
        assertFalse(missing.favorite)
    }

    @Test
    fun `last channel action stays inside the live pro gate and requires a target`() {
        assertTrue(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = true,
                hasLastChannel = true,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = true,
                hasLastChannel = false,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = false,
                liveTvProEnabled = true,
                hasLastChannel = true,
            ),
        )
        assertFalse(
            playerProLiveLastChannelActionEnabled(
                isLive = true,
                liveTvProEnabled = false,
                hasLastChannel = true,
            ),
        )
    }

    @Test
    fun `offline live entry never promises an automatic return`() {
        val entry = livePlayerErrorPresentation(
            offlineFailure = true,
            playbackStarted = false,
            autoResumeIntent = false,
            failureMessage = "لا يوجد اتصال بالانترنت. سيتم استئناف التشغيل تلقائيا عند عودة الاتصال.",
        )

        assertTrue(entry.offline)
        assertEquals("لا يوجد اتصال بالانترنت", entry.title)
        assertFalse(entry.body.contains("تلقائيا"))
    }

    @Test
    fun `live interruption keeps automatic return only for the captured playing intent`() {
        val playing = livePlayerErrorPresentation(
            offlineFailure = true,
            playbackStarted = true,
            autoResumeIntent = true,
            failureMessage = null,
        )
        assertEquals("انقطع اتصال الانترنت", playing.title)
        assertTrue(playing.body.contains("تلقائيا"))

        val paused = livePlayerErrorPresentation(
            offlineFailure = true,
            playbackStarted = true,
            autoResumeIntent = false,
            failureMessage = null,
        )
        assertEquals("انقطع اتصال الانترنت", paused.title)
        assertFalse(paused.body.contains("تلقائيا"))
    }

    @Test
    fun `online live failure keeps the real media message and the error glyph`() {
        val online = livePlayerErrorPresentation(
            offlineFailure = false,
            playbackStarted = true,
            autoResumeIntent = true,
            failureMessage = "البث غير متاح حاليا ، جرب اعادة المحاولة او اختر قناة اخرى",
        )

        assertFalse(online.offline)
        assertEquals("تعذر تشغيل القناة", online.title)
        assertEquals("البث غير متاح حاليا ، جرب اعادة المحاولة او اختر قناة اخرى", online.body)
    }

    @Test
    fun `live request starts hidden unless a remote switch requested the reveal`() {
        assertFalse(liveControlsVisibleOnRequestStart(isLive = true, revealRequested = false))
        assertTrue(liveControlsVisibleOnRequestStart(isLive = true, revealRequested = true))
        assertTrue(liveControlsVisibleOnRequestStart(isLive = false, revealRequested = false))
    }

    @Test
    fun `live controls reveal state follows rapid switches, replacement and cancellation`() {
        var state = LiveControlsInteractionState()
        assertFalse(state.revealRequested)
        assertEquals(0, state.interactionTick)

        // Rapid burst, including native repeats: every accepted step is a fresh interaction that
        // keeps the reveal requested and advances the interaction nonce.
        state = state.onAcceptedSwitchInteraction()
        state = state.onAcceptedSwitchInteraction()
        state = state.onAcceptedSwitchInteraction()
        assertTrue(state.revealRequested)
        assertEquals(3, state.interactionTick)

        // The committed replacement consumes the intent when no newer switch is queued.
        val consumed = state.onRequestReplacement(pendingSwitchPresent = false)
        assertFalse(consumed.revealRequested)
        assertEquals(3, consumed.interactionTick)

        // A newer accepted switch in the replacement window retains the intent for its own request.
        val retained = state.onRequestReplacement(pendingSwitchPresent = true)
        assertTrue(retained.revealRequested)
        assertEquals(3, retained.interactionTick)

        // Cancellation drops the intent; the committed interaction history is untouched.
        val cancelled = state.onCancel()
        assertFalse(cancelled.revealRequested)
        assertEquals(3, cancelled.interactionTick)

        // The next accepted step after cancellation is fresh again.
        val afterCancel = cancelled.onAcceptedSwitchInteraction()
        assertTrue(afterCancel.revealRequested)
        assertEquals(4, afterCancel.interactionTick)
    }

    @Test
    fun `normal press dispatches immediately at the leading edge`() {
        val plan = planLiveZapDispatch(
            state = LiveZapSchedulerState(pendingTargetId = 2),
            currentStreamId = 1,
            pendingTargetExists = true,
            nowMs = 1_000L,
        )

        assertEquals(2, plan.dispatchTargetId)
        assertEquals(0L, plan.waitMs)
        assertEquals(1_000L, plan.state.lastDispatchAtMs)
        assertEquals(2, plan.state.lastDispatchedTargetId)
        // The target stays queued until the request replacement confirms it.
        assertEquals(2, plan.state.pendingTargetId)
    }

    @Test
    fun `held burst commits the latest target before release at a bounded cadence`() {
        var state = LiveZapSchedulerState()
        var currentStreamId = 1
        val commits = mutableListOf<Pair<Long, Int>>()

        fun press(targetId: Int) {
            state = state.copy(pendingTargetId = targetId)
        }

        fun pump(nowMs: Long) {
            val plan = planLiveZapDispatch(
                state = state,
                currentStreamId = currentStreamId,
                pendingTargetExists = true,
                nowMs = nowMs,
            )
            state = plan.state
            plan.dispatchTargetId?.let { dispatched ->
                commits += nowMs to dispatched
                currentStreamId = dispatched
            }
        }

        // Leading press (the real clock is a large monotonic uptime; only deltas matter).
        press(2)
        pump(10_000L)
        assertEquals(listOf(10_000L to 2), commits)

        // Held/native-repeat burst: the target advances every 50 ms for one second.
        var now = 10_000L
        repeat(20) {
            now += 50L
            press(state.pendingTargetId!! + 1)
            pump(now)
        }

        // Commits happened while input was still arriving (never starved until release), and they
        // are bounded by the interval rather than one per repeat.
        assertTrue(commits.size >= 3)
        assertTrue(commits.size <= 1 + (1_000L / LIVE_ZAP_COMMIT_INTERVAL_MS).toInt())
        assertTrue(commits.last().second > 2)
        assertTrue(commits.zipWithNext().all { (a, b) -> b.first - a.first >= LIVE_ZAP_COMMIT_INTERVAL_MS })
    }

    @Test
    fun `newer queued input survives an earlier request replacement`() {
        var state = LiveZapSchedulerState(pendingTargetId = 2)
        var plan = planLiveZapDispatch(
            state = state,
            currentStreamId = 1,
            pendingTargetExists = true,
            nowMs = 10_000L,
        )
        state = plan.state
        assertEquals(2, plan.dispatchTargetId)

        // A newer accepted target arrives before the replacement request lands.
        state = state.copy(pendingTargetId = 3)

        // The earlier request replaces the player; the newer target is retained and waits only the
        // bounded remainder of the interval.
        plan = planLiveZapDispatch(
            state = state,
            currentStreamId = 2,
            pendingTargetExists = true,
            nowMs = 10_100L,
        )
        assertNull(plan.dispatchTargetId)
        assertEquals(LIVE_ZAP_COMMIT_INTERVAL_MS - 100L, plan.waitMs)
        assertEquals(3, plan.state.pendingTargetId)

        // After the bounded wait the latest target dispatches.
        plan = planLiveZapDispatch(
            state = plan.state,
            currentStreamId = 2,
            pendingTargetExists = true,
            nowMs = 10_000L + LIVE_ZAP_COMMIT_INTERVAL_MS,
        )
        assertEquals(3, plan.dispatchTargetId)
    }

    @Test
    fun `release settles the latest target unless it is committed or already in flight`() {
        assertEquals(
            4,
            liveZapReleaseDispatchTargetId(
                state = LiveZapSchedulerState(
                    pendingTargetId = 4,
                    lastDispatchedTargetId = 3,
                ),
                currentStreamId = 3,
            ),
        )
        assertNull(
            liveZapReleaseDispatchTargetId(
                state = LiveZapSchedulerState(
                    pendingTargetId = 3,
                    lastDispatchedTargetId = 3,
                ),
                currentStreamId = 2,
            ),
        )
        assertNull(
            liveZapReleaseDispatchTargetId(
                state = LiveZapSchedulerState(pendingTargetId = 3),
                currentStreamId = 3,
            ),
        )
        assertNull(
            liveZapReleaseDispatchTargetId(
                state = LiveZapSchedulerState(pendingTargetId = null),
                currentStreamId = 1,
            ),
        )
    }

    @Test
    fun `scheduler clears a stale or committed target and never dispatches it`() {
        val missing = planLiveZapDispatch(
            state = LiveZapSchedulerState(pendingTargetId = 9, lastDispatchedTargetId = 9),
            currentStreamId = 1,
            pendingTargetExists = false,
            nowMs = 10_000L,
        )
        assertNull(missing.dispatchTargetId)
        assertNull(missing.state.pendingTargetId)
        assertNull(missing.state.lastDispatchedTargetId)

        val committed = planLiveZapDispatch(
            state = LiveZapSchedulerState(pendingTargetId = 1),
            currentStreamId = 1,
            pendingTargetExists = true,
            nowMs = 10_000L,
        )
        assertNull(committed.dispatchTargetId)
        assertNull(committed.state.pendingTargetId)
    }

    @Test
    fun `foreground surfaces block queued live switching`() {
        assertTrue(
            liveZapDispatchBlocked(
                errorModalInputActive = true,
                browserVisible = false,
                panelInputActive = false,
            ),
        )
        assertTrue(
            liveZapDispatchBlocked(
                errorModalInputActive = false,
                browserVisible = true,
                panelInputActive = false,
            ),
        )
        assertTrue(
            liveZapDispatchBlocked(
                errorModalInputActive = false,
                browserVisible = false,
                panelInputActive = true,
            ),
        )
        assertFalse(
            liveZapDispatchBlocked(
                errorModalInputActive = false,
                browserVisible = false,
                panelInputActive = false,
            ),
        )
    }

    private fun channel(id: Int) = ContentItem(
        id = id,
        name = "Channel $id",
        categoryId = "news",
        type = ContentType.LIVE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "ts",
    )
}

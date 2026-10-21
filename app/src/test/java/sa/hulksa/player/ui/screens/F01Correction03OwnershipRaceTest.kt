package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F01 correction 03: retained media can reach READY during the restoration delay. READY must not
 * clear the confirmed outage, independent foreground preparation must not bypass the handoff, and
 * the captured playing intent/position must still be reapplied after the recovery command.
 */
class F01Correction03OwnershipRaceTest {
    private data class OwnershipCase(
        val label: String,
        val isLive: Boolean,
        val wasPlayingBeforeOffline: Boolean,
        val resumePromptPending: Boolean,
        val preservedPositionMs: Long,
    ) {
        val restoredPlayWhenReady: Boolean
            get() = movieOfflineRestoredPlayWhenReady(
                wasPlayingBeforeOffline = wasPlayingBeforeOffline,
                resumePromptPending = resumePromptPending,
            )

        val restorationSeekMs: Long
            get() = playerRetrySeekTarget(
                isLive = isLive,
                resumePromptPending = resumePromptPending,
                preservedPositionMs = preservedPositionMs,
                playerPositionMs = 0L,
            )
    }

    private val cases = listOf(
        OwnershipCase(
            label = "live playing",
            isLive = true,
            wasPlayingBeforeOffline = true,
            resumePromptPending = false,
            preservedPositionMs = 1_234_000L,
        ),
        OwnershipCase(
            label = "movie playing",
            isLive = false,
            wasPlayingBeforeOffline = true,
            resumePromptPending = false,
            preservedPositionMs = 1_234_000L,
        ),
        OwnershipCase(
            label = "episode playing",
            isLive = false,
            wasPlayingBeforeOffline = true,
            resumePromptPending = false,
            preservedPositionMs = 987_000L,
        ),
        OwnershipCase(
            label = "movie paused",
            isLive = false,
            wasPlayingBeforeOffline = false,
            resumePromptPending = false,
            preservedPositionMs = 1_234_000L,
        ),
        OwnershipCase(
            label = "episode paused",
            isLive = false,
            wasPlayingBeforeOffline = false,
            resumePromptPending = false,
            preservedPositionMs = 987_000L,
        ),
        OwnershipCase(
            label = "movie pending resume",
            isLive = false,
            wasPlayingBeforeOffline = true,
            resumePromptPending = true,
            preservedPositionMs = 1_234_000L,
        ),
        OwnershipCase(
            label = "episode pending resume",
            isLive = false,
            wasPlayingBeforeOffline = true,
            resumePromptPending = true,
            preservedPositionMs = 987_000L,
        ),
    )

    @Test
    fun `ready from retained media cannot clear a confirmed outage`() {
        assertFalse(shouldClearOfflineOnReady(confirmedOutage = true))
        assertTrue(shouldClearOfflineOnReady(confirmedOutage = false))
    }

    @Test
    fun `ready during the handoff preserves restoration intent for every mode`() {
        cases.forEach { case ->
            val capturedIntent = playerOfflinePlayIntentCapture(
                alreadyOffline = false,
                previousIntent = false,
                currentPlayingIntent = case.wasPlayingBeforeOffline,
            )
            assertEquals(case.label, case.wasPlayingBeforeOffline, capturedIntent)

            // Connectivity is back; the 700 ms handoff is still pending and READY arrives from
            // retained media. This callback must not clear the confirmed outage.
            assertFalse(case.label, shouldClearOfflineOnReady(confirmedOutage = true))
            assertTrue(
                case.label,
                shouldBeginOutageRestoration(
                    confirmedOutage = true,
                    networkAvailable = true,
                    localPlayback = false,
                ),
            )
            // The independent foreground preparation path is blocked while the handoff owns the
            // player.
            assertFalse(
                case.label,
                playerPreparationReady(
                    appForeground = true,
                    localPlayback = false,
                    networkAvailable = true,
                    confirmedOutage = true,
                ),
            )
            val expectedPlayWhenReady = case.wasPlayingBeforeOffline && !case.resumePromptPending
            assertEquals(case.label, expectedPlayWhenReady, case.restoredPlayWhenReady)
            val expectedSeekMs = if (case.isLive || case.resumePromptPending) {
                0L
            } else {
                case.preservedPositionMs
            }
            assertEquals(case.label, expectedSeekMs, case.restorationSeekMs)

            // Once the command has been applied and the handoff resolved, preparation is allowed.
            assertTrue(
                case.label,
                playerPreparationReady(
                    appForeground = true,
                    localPlayback = false,
                    networkAvailable = true,
                    confirmedOutage = false,
                ),
            )
        }
    }

    @Test
    fun `confirmed outage blocks preparation while restoration remains eligible`() {
        assertFalse(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = true,
            ),
        )
        assertTrue(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                networkAvailable = true,
                localPlayback = false,
            ),
        )
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = false,
                networkAvailable = true,
                localPlayback = false,
            ),
        )
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                networkAvailable = false,
                localPlayback = false,
            ),
        )
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = true,
                networkAvailable = true,
                localPlayback = true,
            ),
        )
    }

    @Test
    fun `independent foreground preparation cannot bypass the handoff`() {
        assertFalse(
            independentForegroundPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = true,
                recoveryPreparePending = false,
            ),
        )
        assertFalse(
            independentForegroundPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
                recoveryPreparePending = true,
            ),
        )
        assertTrue(
            independentForegroundPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
                recoveryPreparePending = false,
            ),
        )
    }

    @Test
    fun `background local and resolved-outage preparation guards`() {
        assertFalse(
            playerPreparationReady(
                appForeground = false,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = true,
            ),
        )
        assertTrue(
            playerPreparationReady(
                appForeground = true,
                localPlayback = false,
                networkAvailable = true,
                confirmedOutage = false,
            ),
        )
        assertTrue(
            playerPreparationReady(
                appForeground = true,
                localPlayback = true,
                networkAvailable = false,
                confirmedOutage = true,
            ),
        )
        assertTrue(shouldClearOfflineOnReady(confirmedOutage = false))
        assertFalse(
            shouldBeginOutageRestoration(
                confirmedOutage = false,
                networkAvailable = true,
                localPlayback = false,
            ),
        )
    }
}
